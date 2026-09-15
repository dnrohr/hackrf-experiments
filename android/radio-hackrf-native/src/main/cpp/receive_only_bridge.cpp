#include <jni.h>
#include <unistd.h>

#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>

extern "C" {
#include "hackrf.h"
int hackrf_android_open_by_fd(int fd, hackrf_device** device);
}

namespace {

constexpr std::int64_t kMinFrequencyHz = 1'000'000;
constexpr std::int64_t kMaxFrequencyHz = 6'000'000'000;
constexpr std::size_t kTransferBufferSize = 262'144;
// Keep several seconds of callback data available while the Android delivery
// coroutine is briefly descheduled, without allowing unbounded native memory.
constexpr std::size_t kNativeBufferCount = 32;

struct NativeSession {
    hackrf_device* device = nullptr;
    int owned_fd = -1;
    std::atomic<std::uint64_t> bytes{0};
    std::atomic<std::uint64_t> callback_errors{0};
    std::atomic<std::uint64_t> dropped_blocks{0};
    std::mutex lifecycle;
    std::mutex buffer_mutex;
    std::array<std::array<std::uint8_t, kTransferBufferSize>, kNativeBufferCount> buffers{};
    std::array<std::size_t, kNativeBufferCount> buffer_lengths{};
    std::size_t buffer_head = 0;
    std::size_t buffer_tail = 0;
    std::size_t buffer_count = 0;
    std::atomic<bool> streaming{false};
};

NativeSession* from_handle(jlong handle) noexcept {
    return reinterpret_cast<NativeSession*>(static_cast<std::intptr_t>(handle));
}

bool valid_rate(jint sample_rate_hz) noexcept {
    return sample_rate_hz == 2'000'000 || sample_rate_hz == 4'000'000 ||
           sample_rate_hz == 8'000'000;
}

bool valid_filter(jint sample_rate_hz, jint baseband_filter_hz) noexcept {
    return baseband_filter_hz == 0 ||
           (sample_rate_hz == 2'000'000 && baseband_filter_hz == 1'750'000) ||
           (sample_rate_hz == 4'000'000 && baseband_filter_hz == 3'500'000) ||
           (sample_rate_hz == 8'000'000 && baseband_filter_hz == 7'000'000);
}

bool valid_settings(jint sample_rate_hz, jint baseband_filter_hz, jint lna_gain_db, jint vga_gain_db) noexcept {
    return valid_filter(sample_rate_hz, baseband_filter_hz) &&
           lna_gain_db >= 0 && lna_gain_db <= 40 && lna_gain_db % 8 == 0 &&
           vga_gain_db >= 0 && vga_gain_db <= 62 && vga_gain_db % 2 == 0;
}

int apply_settings(NativeSession* session, jint sample_rate_hz, jint baseband_filter_hz,
                   jint lna_gain_db, jint vga_gain_db, jboolean rf_amp_enabled,
                   jboolean antenna_power_enabled) noexcept {
    int result = hackrf_set_amp_enable(session->device, 0);
    if (result == HACKRF_SUCCESS) result = hackrf_set_antenna_enable(session->device, 0);
    if (result == HACKRF_SUCCESS) result = hackrf_set_lna_gain(session->device, static_cast<std::uint32_t>(lna_gain_db));
    if (result == HACKRF_SUCCESS) result = hackrf_set_vga_gain(session->device, static_cast<std::uint32_t>(vga_gain_db));
    if (result == HACKRF_SUCCESS) result = hackrf_set_sample_rate(session->device, sample_rate_hz);
    if (result == HACKRF_SUCCESS) {
        const auto filter = baseband_filter_hz == 0
            ? hackrf_compute_baseband_filter_bw_round_down_lt(sample_rate_hz)
            : static_cast<std::uint32_t>(baseband_filter_hz);
        result = hackrf_set_baseband_filter_bandwidth(session->device, filter);
    }
    if (result == HACKRF_SUCCESS) result = hackrf_set_amp_enable(session->device, rf_amp_enabled == JNI_TRUE ? 1 : 0);
    if (result == HACKRF_SUCCESS) result = hackrf_set_antenna_enable(session->device, antenna_power_enabled == JNI_TRUE ? 1 : 0);
    return result;
}

int on_receive(hackrf_transfer* transfer) noexcept {
    if (transfer == nullptr || transfer->rx_ctx == nullptr) return -1;
    auto* session = static_cast<NativeSession*>(transfer->rx_ctx);
    if (transfer->buffer == nullptr || transfer->valid_length < 0) {
        session->callback_errors.fetch_add(1, std::memory_order_relaxed);
        return 0;
    }
    if (static_cast<std::size_t>(transfer->valid_length) > kTransferBufferSize) {
        session->callback_errors.fetch_add(1, std::memory_order_relaxed);
        return 0;
    }
    session->bytes.fetch_add(static_cast<std::uint64_t>(transfer->valid_length), std::memory_order_relaxed);
    std::unique_lock<std::mutex> guard(session->buffer_mutex, std::try_to_lock);
    if (!guard.owns_lock()) {
        session->dropped_blocks.fetch_add(1, std::memory_order_relaxed);
        return 0;
    }
    if (session->buffer_count == kNativeBufferCount) {
        session->dropped_blocks.fetch_add(1, std::memory_order_relaxed);
        return 0;
    }
    std::memcpy(session->buffers[session->buffer_tail].data(), transfer->buffer, transfer->valid_length);
    session->buffer_lengths[session->buffer_tail] = static_cast<std::size_t>(transfer->valid_length);
    session->buffer_tail = (session->buffer_tail + 1) % kNativeBufferCount;
    session->buffer_count++;
    return 0;
}

int safe_stop(NativeSession* session) noexcept {
    int result = HACKRF_SUCCESS;
    if (session->streaming.load(std::memory_order_acquire)) result = hackrf_stop_rx(session->device);
    session->streaming.store(false, std::memory_order_release);
    const int amp_result = hackrf_set_amp_enable(session->device, 0);
    const int antenna_result = hackrf_set_antenna_enable(session->device, 0);
    if (result == HACKRF_SUCCESS && amp_result != HACKRF_SUCCESS) result = amp_result;
    if (result == HACKRF_SUCCESS && antenna_result != HACKRF_SUCCESS) result = antenna_result;
    return result;
}

void reset_capture_state(NativeSession* session) {
    session->bytes.store(0, std::memory_order_relaxed);
    session->callback_errors.store(0, std::memory_order_relaxed);
    session->dropped_blocks.store(0, std::memory_order_relaxed);
    std::lock_guard<std::mutex> guard(session->buffer_mutex);
    session->buffer_head = 0;
    session->buffer_tail = 0;
    session->buffer_count = 0;
    session->buffer_lengths.fill(0);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeInitialize(JNIEnv*, jobject) {
    return hackrf_init();
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeOpen(JNIEnv*, jobject, jint fd) {
    if (fd < 0) return 0;
    auto* session = new NativeSession();
    session->owned_fd = dup(fd);
    if (session->owned_fd < 0 || hackrf_android_open_by_fd(session->owned_fd, &session->device) != HACKRF_SUCCESS) {
        if (session->owned_fd >= 0) close(session->owned_fd);
        delete session;
        return 0;
    }
    if (hackrf_set_amp_enable(session->device, 0) != HACKRF_SUCCESS ||
        hackrf_set_antenna_enable(session->device, 0) != HACKRF_SUCCESS ||
        hackrf_set_lna_gain(session->device, 0) != HACKRF_SUCCESS ||
        hackrf_set_vga_gain(session->device, 0) != HACKRF_SUCCESS) {
        hackrf_close(session->device);
        close(session->owned_fd);
        delete session;
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<std::intptr_t>(session));
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeDeviceInfo(JNIEnv* env, jobject, jlong handle) {
    auto* session = from_handle(handle);
    if (session == nullptr || session->device == nullptr) return nullptr;
    std::array<char, 256> firmware{};
    std::uint8_t board = BOARD_ID_UNDETECTED;
    std::uint8_t revision = BOARD_REV_UNDETECTED;
    std::uint16_t api = 0;
    if (hackrf_board_id_read(session->device, &board) != HACKRF_SUCCESS ||
        hackrf_version_string_read(session->device, firmware.data(), firmware.size() - 1) != HACKRF_SUCCESS ||
        hackrf_usb_api_version_read(session->device, &api) != HACKRF_SUCCESS ||
        hackrf_board_rev_read(session->device, &revision) != HACKRF_SUCCESS) return nullptr;
    std::array<char, 512> info{};
    std::snprintf(info.data(), info.size(), "%s\n%s\n%x.%02x\n%s",
                  hackrf_board_id_name(static_cast<hackrf_board_id>(board)), firmware.data(),
                  (api >> 8) & 0xff, api & 0xff,
                  hackrf_board_rev_name(static_cast<hackrf_board_rev>(revision)));
    return env->NewStringUTF(info.data());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeStartRx(
    JNIEnv*, jobject, jlong handle, jlong frequency_hz, jint sample_rate_hz,
    jint baseband_filter_hz, jint lna_gain_db, jint vga_gain_db,
    jboolean rf_amp_enabled, jboolean antenna_power_enabled) {
    auto* session = from_handle(handle);
    if (session == nullptr || session->device == nullptr || frequency_hz < kMinFrequencyHz ||
        frequency_hz > kMaxFrequencyHz || !valid_rate(sample_rate_hz) ||
        !valid_settings(sample_rate_hz, baseband_filter_hz, lna_gain_db, vga_gain_db)) return HACKRF_ERROR_INVALID_PARAM;
    std::lock_guard<std::mutex> guard(session->lifecycle);
    if (session->streaming.load(std::memory_order_acquire)) return HACKRF_ERROR_BUSY;
    reset_capture_state(session);
    int result = apply_settings(session, sample_rate_hz, baseband_filter_hz, lna_gain_db, vga_gain_db,
                                rf_amp_enabled, antenna_power_enabled);
    if (result == HACKRF_SUCCESS) result = hackrf_set_freq(session->device, static_cast<std::uint64_t>(frequency_hz));
    if (result == HACKRF_SUCCESS) result = hackrf_start_rx(session->device, on_receive, session);
    if (result != HACKRF_SUCCESS) {
        hackrf_set_amp_enable(session->device, 0);
        hackrf_set_antenna_enable(session->device, 0);
    }
    session->streaming.store(result == HACKRF_SUCCESS, std::memory_order_release);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeStartSweep(
    JNIEnv* env, jobject, jlong handle, jlongArray range_edges_hz,
    jint bin_width_hz, jint sample_rate_hz, jint baseband_filter_hz,
    jint lna_gain_db, jint vga_gain_db, jboolean rf_amp_enabled,
    jboolean antenna_power_enabled) {
    auto* session = from_handle(handle);
    if (session == nullptr || session->device == nullptr || range_edges_hz == nullptr ||
        bin_width_hz <= 0 || !valid_rate(sample_rate_hz) ||
        !valid_settings(sample_rate_hz, baseband_filter_hz, lna_gain_db, vga_gain_db)) {
        return HACKRF_ERROR_INVALID_PARAM;
    }
    const jsize edge_count = env->GetArrayLength(range_edges_hz);
    if (edge_count < 2 || edge_count % 2 != 0 || edge_count / 2 > MAX_SWEEP_RANGES) return HACKRF_ERROR_INVALID_PARAM;
    std::array<jlong, MAX_SWEEP_RANGES * 2> edges{};
    env->GetLongArrayRegion(range_edges_hz, 0, edge_count, edges.data());
    if (env->ExceptionCheck()) return HACKRF_ERROR_INVALID_PARAM;
    std::array<std::uint16_t, MAX_SWEEP_RANGES * 2> range_mhz{};
    int hardware_range_count = 0;
    for (jsize index = 0; index < edge_count; index += 2) {
        const auto start = edges[static_cast<std::size_t>(index)];
        const auto end = edges[static_cast<std::size_t>(index + 1)];
        if (start < kMinFrequencyHz || end > kMaxFrequencyHz || start >= end) return HACKRF_ERROR_INVALID_PARAM;
        if (index > 0 && start < edges[static_cast<std::size_t>(index - 1)]) return HACKRF_ERROR_INVALID_PARAM;
        const auto start_mhz = static_cast<std::uint16_t>(start / 1'000'000);
        const auto end_mhz = static_cast<std::uint16_t>((end + 999'999) / 1'000'000);
        if (hardware_range_count > 0 && start_mhz <= range_mhz[static_cast<std::size_t>(hardware_range_count * 2 - 1)]) {
            auto& previous_end = range_mhz[static_cast<std::size_t>(hardware_range_count * 2 - 1)];
            if (end_mhz > previous_end) previous_end = end_mhz;
        } else {
            range_mhz[static_cast<std::size_t>(hardware_range_count * 2)] = start_mhz;
            range_mhz[static_cast<std::size_t>(hardware_range_count * 2 + 1)] = end_mhz;
            hardware_range_count++;
        }
    }
    std::lock_guard<std::mutex> guard(session->lifecycle);
    if (session->streaming.load(std::memory_order_acquire)) return HACKRF_ERROR_BUSY;
    reset_capture_state(session);
    int result = apply_settings(session, sample_rate_hz, baseband_filter_hz, lna_gain_db, vga_gain_db,
                                rf_amp_enabled, antenna_power_enabled);
    if (result == HACKRF_SUCCESS) {
        const auto step_width = static_cast<std::uint32_t>(sample_rate_hz);
        const auto offset = static_cast<std::uint32_t>(sample_rate_hz / 2);
        result = hackrf_init_sweep(session->device, range_mhz.data(), hardware_range_count, BYTES_PER_BLOCK,
                                   step_width, offset, INTERLEAVED);
    }
    if (result == HACKRF_SUCCESS) result = hackrf_start_rx_sweep(session->device, on_receive, session);
    if (result != HACKRF_SUCCESS) {
        hackrf_set_amp_enable(session->device, 0);
        hackrf_set_antenna_enable(session->device, 0);
    }
    session->streaming.store(result == HACKRF_SUCCESS, std::memory_order_release);
    return result;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeStats(JNIEnv* env, jobject, jlong handle) {
    auto* session = from_handle(handle);
    std::array<jlong, 4> stats{};
    if (session != nullptr) {
        if (session->streaming.load(std::memory_order_acquire) &&
            hackrf_is_streaming(session->device) != HACKRF_TRUE) {
            session->callback_errors.fetch_add(1, std::memory_order_relaxed);
            session->streaming.store(false, std::memory_order_release);
        }
        stats[0] = static_cast<jlong>(session->bytes.load(std::memory_order_relaxed));
        stats[1] = static_cast<jlong>(session->callback_errors.load(std::memory_order_relaxed));
        stats[2] = static_cast<jlong>(session->dropped_blocks.load(std::memory_order_relaxed));
        stats[3] = session->streaming.load(std::memory_order_acquire) ? 1 : 0;
    }
    jlongArray result = env->NewLongArray(stats.size());
    if (result != nullptr) env->SetLongArrayRegion(result, 0, stats.size(), stats.data());
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativePollBuffer(JNIEnv* env, jobject, jlong handle) {
    auto* session = from_handle(handle);
    if (session == nullptr) return nullptr;
    std::lock_guard<std::mutex> guard(session->buffer_mutex);
    if (session->buffer_count == 0) return nullptr;
    const auto length = static_cast<jsize>(session->buffer_lengths[session->buffer_head]);
    jbyteArray result = env->NewByteArray(length);
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(
        result, 0, length,
        reinterpret_cast<const jbyte*>(session->buffers[session->buffer_head].data())
    );
    if (env->ExceptionCheck()) return nullptr;
    session->buffer_head = (session->buffer_head + 1) % kNativeBufferCount;
    session->buffer_count--;
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeStop(JNIEnv*, jobject, jlong handle) {
    auto* session = from_handle(handle);
    if (session == nullptr) return HACKRF_ERROR_INVALID_PARAM;
    std::lock_guard<std::mutex> guard(session->lifecycle);
    return safe_stop(session);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_rfnotebook_radio_hackrf_NativeHackrf_nativeClose(JNIEnv*, jobject, jlong handle) {
    auto* session = from_handle(handle);
    if (session == nullptr) return;
    {
        std::lock_guard<std::mutex> guard(session->lifecycle);
        safe_stop(session);
        if (session->device != nullptr) hackrf_close(session->device);
        session->device = nullptr;
    }
    if (session->owned_fd >= 0) close(session->owned_fd);
    delete session;
}
