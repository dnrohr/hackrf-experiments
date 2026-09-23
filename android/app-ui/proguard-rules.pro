# Native methods are resolved by the project-owned JNI names exported from
# librfnotebook_radio.so. Keep this boundary stable under release obfuscation.
-keep class dev.rfnotebook.radio.hackrf.NativeHackrf {
    native <methods>;
}
