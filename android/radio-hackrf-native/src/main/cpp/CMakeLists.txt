cmake_minimum_required(VERSION 3.22.1)
project(rfnotebook_radio LANGUAGES C CXX)

include(FetchContent)

FetchContent_Declare(
    libusb_upstream
    URL https://codeload.github.com/libusb/libusb/zip/15a7ebb4d426c5ce196684347d2b7cafad862626
    URL_HASH SHA256=3415F390DF8D841F275C14F4B3B5CA5A4FCA893D36BA1D845538E4CAA54C2F30
)
FetchContent_Declare(
    hackrf_upstream
    URL https://codeload.github.com/greatscottgadgets/hackrf/zip/01f7c8b3509e308c5e17b77a8ed2dbb594a98860
    URL_HASH SHA256=33B90BCC62C5798F576AF64139C23553EB74FD65AEE60A467C63152B1982B976
)
FetchContent_GetProperties(libusb_upstream)
if(NOT libusb_upstream_POPULATED)
    FetchContent_Populate(libusb_upstream)
endif()
FetchContent_GetProperties(hackrf_upstream)
if(NOT hackrf_upstream_POPULATED)
    FetchContent_Populate(hackrf_upstream)
endif()

set(LIBUSB_DIR "${libusb_upstream_SOURCE_DIR}/libusb")
add_library(libusb_android STATIC
    "${LIBUSB_DIR}/core.c"
    "${LIBUSB_DIR}/descriptor.c"
    "${LIBUSB_DIR}/hotplug.c"
    "${LIBUSB_DIR}/io.c"
    "${LIBUSB_DIR}/sync.c"
    "${LIBUSB_DIR}/strerror.c"
    "${LIBUSB_DIR}/os/linux_usbfs.c"
    "${LIBUSB_DIR}/os/events_posix.c"
    "${LIBUSB_DIR}/os/threads_posix.c"
    "${LIBUSB_DIR}/os/linux_netlink.c"
)
target_include_directories(libusb_android PUBLIC "${LIBUSB_DIR}" PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/libusb-config" "${LIBUSB_DIR}/os")
target_compile_options(libusb_android PRIVATE -fvisibility=hidden -pthread)
target_link_libraries(libusb_android PRIVATE log)

set(HACKRF_DIR "${hackrf_upstream_SOURCE_DIR}/host/libhackrf/src")
file(READ "${HACKRF_DIR}/hackrf.c" HACKRF_SOURCE)
set(HACKRF_OPEN_MARKER "int ADDCALL hackrf_open(hackrf_device** device)")
set(HACKRF_ANDROID_OPEN [=[
int hackrf_android_open_by_fd(int fd, hackrf_device** device)
{
    libusb_device_handle* usb_device = NULL;
    int result;
    if (g_libusb_context == NULL || fd < 0 || device == NULL) {
        return HACKRF_ERROR_INVALID_PARAM;
    }
    result = libusb_wrap_sys_device(g_libusb_context, (intptr_t) fd, &usb_device);
    if (result != LIBUSB_SUCCESS) {
        last_libusb_error = result;
        return HACKRF_ERROR_LIBUSB;
    }
    return hackrf_open_setup(usb_device, device);
}

]=])
string(FIND "${HACKRF_SOURCE}" "${HACKRF_OPEN_MARKER}" HACKRF_OPEN_POSITION)
if(HACKRF_OPEN_POSITION EQUAL -1)
    message(FATAL_ERROR "Pinned libhackrf source no longer contains the Android open patch point")
endif()
string(REPLACE "${HACKRF_OPEN_MARKER}" "${HACKRF_ANDROID_OPEN}${HACKRF_OPEN_MARKER}" HACKRF_PATCHED_SOURCE "${HACKRF_SOURCE}")
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/hackrf_android.c" "${HACKRF_PATCHED_SOURCE}")

add_library(hackrf_android STATIC "${CMAKE_CURRENT_BINARY_DIR}/hackrf_android.c")
target_include_directories(hackrf_android PUBLIC "${HACKRF_DIR}" PRIVATE "${LIBUSB_DIR}")
target_compile_definitions(hackrf_android PRIVATE
    LIBRARY_VERSION="0.9.2"
    LIBRARY_RELEASE="2026.01.3"
    DISABLE_USB_DEVICE_DISCOVERY
)
target_compile_options(hackrf_android PRIVATE -fvisibility=hidden)
target_link_libraries(hackrf_android PRIVATE libusb_android)

add_library(rfnotebook_radio SHARED receive_only_bridge.cpp)
target_compile_features(rfnotebook_radio PRIVATE cxx_std_17)
target_compile_options(rfnotebook_radio PRIVATE -Wall -Wextra -Werror)
target_link_options(rfnotebook_radio PRIVATE
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/rfnotebook_radio.map"
)
target_include_directories(rfnotebook_radio PRIVATE "${HACKRF_DIR}")
target_link_libraries(rfnotebook_radio PRIVATE hackrf_android libusb_android log)
