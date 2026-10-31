# ZhongHong Integration Lab

A standalone Android 7.1.2/API-25 diagnostic app for the ZhongHong / NEXELL head unit used in the Saipa Quick.

## Capabilities
- Automatically probes vendor services, CAN, Logic, SystemState, read-only MCU data, providers, permissions, USB storage, and video-decoder inventory.
- Decodes the 159 CAN signal definitions extracted from the working CarCheck application.
- Prioritizes USB Music and Bluetooth media, including guided steering-wheel next/previous/play-pause checks.
- Includes a guided stationary Sport/manual-CVT test for P, selector mode, +, and −.
- Guides switch tests for lights, indicators, wipers, washers, window-switch inputs, and rear defrost.
- Exports Markdown reports with decoded CAN values, raw frame bytes, and event history.

## Compatibility
- Android 7.1.2 / API 25; 32-bit ARM; landscape 952 × 600.
- targetSdkVersion 22; separate package com.ismail.zhonghonglab.debug.
- No HOME intent, root, native libraries, or direct UART access.

## APK
GitHub Actions: https://github.com/IsmailAabedah/forDel4/actions
Download the artifact named zhonghong-integration-lab-debug from the latest successful Android build. The APK is inside the ZIP at app/build/outputs/apk/debug/app-debug.apk.

## References
- reference/CarCheck-v1.1.apk — the previously working app.
- reference/CarCheck-jadx-sources.zip — extracted source used to reproduce service bindings and CAN signal map.

Cloud compilation is not real-device testing. See docs/IMPLEMENTATION_STATUS.md for remaining validation. SRS data is informational only; no CAN/MCU writes are issued. The reverse-camera path remains under factory control.
