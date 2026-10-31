# Implementation status — ZhongHong Integration Lab

This document describes code committed to main. It distinguishes code integration from proof on the vehicle itself.

## Device target
- Android 7.1.2 / API 25
- 32-bit ARM (armeabi-v7a / armeabi)
- 952 × 600 landscape display
- targetSdkVersion 22; separate package, not the factory launcher
- No HOME intent, root requirement, native code, direct UART, CAN writes, or SRS commands

## Implemented

### Automatic read-only diagnostics
- Detects device/ABI, vendor packages, Binder components, providers, storage, permissions, Bluetooth adapter, and advertised video decoders.
- Connects to CAN, registers the verified callback, reads modules 1–4, and decodes the 159 signal definitions extracted from CarCheck.
- Registers Logic callback domains 1 and 2, reads supported state/source getters, and records state, audio focus, source, and media-button events.
- Connects to SystemStateService and reads network, coarse Bluetooth connection, and Bluetooth-call state; registers its callback.
- Reads MCU domains 1–3 using the vendor non-standard parcel structure. Read-only probe.
- Reports gear/speed/ratio, TPMS pressure, ambient temperature, winter-mode raw value, window-switch values, and raw SRS flags with cautious interpretation.
- Exports decoded CAN fields, raw frame bytes, service checks, and timestamped events into Markdown.

### USB music
- Binds the exact CarCheck component: com.zhonghong.yzmusic/com.zhonghong.music.model.service.UsbMusicService.
- Reads play state/title/artist/album, registers the data listener, and calls vendor transport operations including setMusicSource, next, previous, play/pause, play, and pause.
- Guided USB test stays inside the app and correlates one wheel-button press with Logic event codes and actual track/play-state change.

### Bluetooth music
- Uses Android MediaSession when the head unit publishes an active session; Notification Listener access is a one-time Android special permission.
- Attempts Logic source selection ID 3 without opening the factory Bluetooth page.
- Guided test records Logic codes 87 next, 88 previous, and 85 toggle.
- Missing MediaSession metadata is reported partial/unavailable, never invented.
- Restores the previous Logic source when the guided test Activity exits.

### Manual Sport / CVT
- Checks speed=0 and P=9 before selector testing.
- Detects a raw shifter value outside P/R/N/D before calling Manual/Sport observed.
- Captures CAN gear changes, speed, and overall-ratio raw values during separate + and − observation windows.
- The display must not be touched while moving; capture ends only after timeout and the CAN speed reading returns to zero.

### Remaining vehicle switches
- Dedicated guided wizard covers position/low/high lamps, front/rear fog, left/right indicators, hazard, intermittent/low/high wipers, front/rear washer, rear wiper, rear defrost, and left/right window-switch input.
- A step is confirmed only if its target signal changes and returns to baseline; unexpected switch/lamp/wiper signals are separately noted.
- Requires stationary P and speed=0; aborts when speed becomes nonzero.
- Window signals are explicitly labeled switch inputs, not glass position.
- SRS/airbag bits are raw information only; no diagnosis is generated.

## Not yet proven on the user's head unit
A successful cloud build is not an on-device runtime test. Run the APK on the actual screen to confirm:
- USB Music binding and transactions from this package.
- Logic source switching and steering-wheel button delivery for USB and Bluetooth.
- Whether Bluetooth metadata and transport controls are published by this firmware.
- Each remaining switch signal's actual behavior and return-to-baseline.
- Sport/Manual raw shifter values and whether +/− changes Shifter_Lever_Signals, Overal_Ratio, or both.
- Video playback of actual USB MP4/H.264 files; only decoder inventory exists in this build.
- Custom contacts/call-log UI and phone companion bridge are not implemented.
- MCU read-all calls are not yet runtime-validated by this app.

## Safety
- CAN and MCU reads only; no write commands, raw UART, or direct SRS access.
- Never induce an SRS fault or disconnect SRS components.
- Never touch the diagnostic screen while driving. For a moving capture, use a passenger/technician only; the driver keeps hands off the screen.
- The factory reverse-camera path remains untouched.
