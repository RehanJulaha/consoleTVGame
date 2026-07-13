# RetroGameStick - Dual APK Project

Retro gaming on Android TV with phone controllers.

## Architecture

- **Console APK** (`com.retrogamestick.console`) - Runs on Android TV / Google TV
  - Libretro cores via JNI (fbalpha2012 for NeoGeo/CPS1/CPS2)
  - Bundled ROMs: KOF97, NeoGeo BIOS, PGM
  - WebRTC DataChannel for low-latency input (<20ms LAN)
  - mDNS discovery for zero-config pairing

- **Controller APK** (`com.retrogamestick.controller`) - Runs on Android Phone
  - Touch virtual gamepad (NeoGeo 6-button layout)
  - WebRTC sender for input frames at 60fps
  - Auto-discovers console via mDNS
  - BT LE fallback if WiFi fails

## Build

```bash
# Local build (requires Android SDK + NDK)
./gradlew :console:assembleDebug :controller:assembleDebug

# Or use GitHub Actions (push to main branch)
```

## CI/CD

GitHub Actions builds both APKs on every push:
- Console APK: `console/build/outputs/apk/debug/console-debug.apk`
- Controller APK: `controller/build/outputs/apk/debug/controller-debug.apk`

Artifacts uploaded as workflow artifacts for 7 days.

## ROMs

Bundled in `console/src/main/assets/roms/neogeo/`:
- `kof97.zip` - The King of Fighters '97
- `neogeo.zip` - NeoGeo BIOS (required)
- `pgm.zip` - PolyGame Master games

Add more ROMs by placing in `/storage/emulated/0/RetroGameStick/ROMS/` on TV.

## Pairing

1. Install Console APK on Android TV
2. Install Controller APK on phone(s)
3. Open Console app - shows QR code + 4-digit PIN
4. Open Controller app - auto-discovers TV
5. Enter PIN or scan QR
6. Up to 2 players supported

## Tech Stack

- Kotlin + Jetpack Compose (TV + Material3)
- Libretro cores via JNI (fbalpha2012, genesis_plus_gx, snes9x, mupen64plus_next, pcsx_rearmed, mgba)
- WebRTC DataChannels (unordered, unreliable for <20ms latency)
- mDNS (JmDNS) + UDP broadcast discovery
- Protobuf for input frame serialization
- AAudio / OpenSL ES for audio
- OpenGL ES 3.0 for video