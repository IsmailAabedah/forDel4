# ZhongHong Integration Lab

A standalone Android diagnostic and validation application for the ZhongHong / NEXELL head unit used in the Saipa Quick.

## Device compatibility target

- Android 7.1.2 / API 25
- 32-bit ARM (armeabi-v7a / armeabi)
- Landscape display: 952 × 600 (about 634 × 400 dp)
- Legacy target SDK 22 for API-25 compatibility
- No HOME launcher intent, root access, native libraries, or direct UART access

## Primary goals

1. Validate steering-wheel media controls while USB Music and Bluetooth A2DP are active.
2. Validate USB Music binder reads and transport commands.
3. Validate Logic audio-source/media-key callbacks and Bluetooth media metadata/control paths.
4. Confirm automatic CVT/manual Sport mode gear values (P/R/N/D and manual +/-) without issuing vehicle-control writes.
5. Automatically inspect CAN, Logic, MCU, phone-provider, codec, storage, and service availability.
6. Guide only the unresolved physical checks with explicit, guarded, one-action-at-a-time instructions.
7. Generate a concise, timestamped evidence report.

## Safety model

The app is read-first. It does not open factory UART devices, write CAN frames, issue SRS/airbag commands, or obscure/replace the reverse-camera path. Any future reversible MCU experiment must have a separately reviewed allowlist, a saved original value, a visible restore step, and a stationary-vehicle guard.

## Build

GitHub Actions builds the debug APK and publishes it as a workflow artifact. See .github/workflows/android.yml.

## Reference compatibility files

The reference/ directory contains the user's previously working CarCheck-v1.1 APK and its extracted source archive. They are compatibility references only; the new app is a separate package and does not replace the factory launcher.
