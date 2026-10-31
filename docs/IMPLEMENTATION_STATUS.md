# Implementation status

## Implemented in the initial project

- Android app scaffold with API-25 / 32-bit ARM compatibility target and a legacy target SDK.
- Non-launcher app identity: the app has no HOME category and does not replace the factory launcher.
- Automatic inventory of vendor packages, selected Binder services, content providers, Android permissions, Bluetooth adapter, storage mounts, and advertised video decoders.
- Read-only binding checks for the known CAN, USB Music, and USB Video service components.
- USB Music Binder client using the transaction table recorded in the verified integration report:
  - 3: isPlaying
  - 4: getTitle
  - 5: getAlbum
  - 6: getArtist
  - 8: next
  - 9: previous
  - 10: play/pause
  - 11: play
  - 12: pause
- MediaSession inspection and transport controls, gated by the one-time Notification Listener permission.
- Guided USB/Bluetooth steering-wheel trials which compare the media state before and after one clearly named wheel-button press.
- Voice guidance, timestamped event journal, and exportable Markdown report.

## Explicitly not claimed as complete yet

- The CAN callback/AIDL data marshalling and signal decoder must be verified against the supplied CarCheck source and the full integration report before Sport gear values or all CAN signals can be declared tested.
- The Logic callback registration and media-key event mapping still require the exact callback parcel contract; the app currently probes Logic reachability but does not claim to observe all global steering-wheel events.
- Bluetooth metadata/control is confirmed only when the head unit publishes an active MediaSession. A missing session is not proof that A2DP itself is unavailable.
- A successful Binder bind proves reachability, not that every transaction works.
- No SRS/airbag diagnostic command or CAN write is included.

## Next validation gates

1. Compare the CarCheck APK manifest and exact probe implementation with this app.
2. Finish CAN read/callback integration and the manual/Sport gear capture.
3. Finish Logic domain 1 and 2 callback integration and correlate wheel keys with USB and Bluetooth responses.
4. Run GitHub Actions build and install the APK on the actual API-25 head unit.
5. Only after the on-device evidence is available, mark each feature PASS / CONDITIONAL / UNAVAILABLE.
