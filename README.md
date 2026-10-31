# FH6 CONTROLLER // FORZA HORIZON MOBILE HUD & PC COMPANION SUITE

Ultra-low latency virtual Xbox 360 controller emulation suite designed specifically for **Forza Horizon 6**. Turns your Android phone into an authentic, edge-to-edge motorsport HUD with gyroscope tilt steering, precision pedal modulation, and 1-click auto-driving assist.

---

## Architecture Overview

```
 +---------------------------------------------------------------+
 |                 ANDROID PHONE (fh6-controller-android)         |
 |  - 120Hz Touch HUD & Gyroscope Sensor (0.4x - 3.0x Sens)      |
 |  - Pure Vector SVGs (Steering Wheel, RT Accel, LT Brake, A)   |
 |  - 1-Click Auto-Drive Assist Button (C + Num 2 macro)         |
 |  - Dynamic Reposition Drag & Fine Pixel Resizing Engine       |
 +-------------------------------+-------------------------------+
                                 |
                 UDP FastStream  |  TCP Profile Sync & Media
                    (Port 4242)  |  (Port 4243)
                                 v
 +---------------------------------------------------------------+
 |              WINDOWS COMPANION (fh6-controller-windows.exe)   |
 |  - Native C# .NET 8 WPF Dashboard (Zero Electron / Zero CMD)  |
 |  - ViGEmBus Hardware Xbox 360 Controller Emulation Engine      |
 |  - Universal SMTC / Spotify / YouTube Music Media Bridge      |
 |  - Win32 SendInput Assist Sequencer (VK_C -> VK_NUMPAD2)      |
 +-------------------------------+-------------------------------+
                                 |
                                 v
 +---------------------------------------------------------------+
 |             STEAM & FORZA HORIZON 6 (WINDOWS PC)              |
 |  - Official Xbox 360 Controller Detected Plug-and-Play        |
 |  - 0-Input Lag Analog Steering & Progressive Triggers         |
 +---------------------------------------------------------------+
```

---

## Key Features

1. **0-Delay FastStream 120Hz UDP Protocol**
   - High-performance binary 24-byte packet stream with sequence numbering, CRC16 error checking, and a 500ms safety watchdog that zeroes inputs if a connection drop occurs.
2. **Native ViGEmBus Xbox 360 Controller Emulation**
   - Recognized natively by Windows, Steam, and Forza Horizon as an official Microsoft Xbox 360 Controller without requiring third-party remappers.
3. **Progressive Analog Reverse & Brake Modulation**
   - Smooth, linear braking and reverse pressure from 0% to 100%, perfectly matched with responsive visual feedback and mobile touch ergonomics.
4. **1-Click Auto-Drive Assist Sequencer**
   - A dedicated assist action on Android and Windows that dispatches `C` (`VK_C = 0x43`) followed by `NumPad 2` (`VK_NUMPAD2 = 0x62`) with precise 100ms hardware-level timing.
5. **Physical Gyroscope Tilt Steering**
   - Uses device hardware sensors to provide analog steering rotation with adjustable sensitivity (0.4x to 3.0x) and real-time visual feedback on both phone and PC.
6. **In-HUD Custom Layout & Repositioning Engine**
   - Interactive Edit Mode with live dragging and fine-grained pixel delta scaling (+/- 1.5% increments) for every steering wheel, pedal, and button.
7. **Instant Deep-Link QR Pairing & LAN Auto-Discovery**
   - Instant UDP discovery finds your PC automatically. Camera scanning opens a deep-link URL (`https://fh6-controller.github.io/?ip=...`) that automatically opens the app or provides the direct installer.
8. **Universal Media Hub Bridge**
   - Media controls synced with Spotify, YouTube Music, and Windows System Media Transport Controls (SMTC).
9. **Zero Popups / Zero Emojis / Native Lightweight Binaries**
   - Windows companion is a self-contained ~150 KB executable with no batch files (`.bat`), command prompt windows, or heavy Electron frameworks. Zero emojis across all UI, toast notifications, and source code.

---

## Connection Options

- **Automatic LAN Discovery (Recommended):** Ensure your phone and PC are connected to the same Wi-Fi router. Open the Windows companion and launch the Android app; pairing happens automatically in milliseconds.
- **Deep-Link QR Scanner:** Point your phone camera at the QR code displayed on the Windows companion dashboard. Your phone will open the deep link and pair automatically.
- **Zero-Jitter USB Tethering:** Enable USB Tethering on your Android phone and connect via USB cable for competitive esports gameplay with 0ms network jitter.

---

## Controls & Mapping Matrix

| Mobile HUD Element | Input Type | Windows / Xbox 360 Output | Function in FH6 |
|---|---|---|---|
| **Steering Wheel** | Analog Axis | Left Stick X (`-32768` to `+32767`) | Progressive Analog Steering |
| **Accelerator Pedal** | Analog Trigger | Right Trigger (`0` to `255`) | Throttle (RT) |
| **Brake / Reverse Pedal** | Analog Trigger | Left Trigger (`0` to `255`) | Brake & Reverse (LT) |
| **Handbrake Capsule** | Digital Button | Xbox Button `A` / Keyboard `Space` | E-Brake / Drift Handbrake |
| **Auto-Drive Assist** | Digital Macro | Win32 `VK_C` -> 100ms -> `VK_NUMPAD2` | 1-Click Autonomous Drive |
| **Rewind** | Digital Button | Xbox Button `Y` / Keyboard `R` | Horizon Rewind |
| **Camera Switch** | Digital Button | Xbox Button `RB` / Keyboard `C` | Change Camera Perspective |
| **Clutch** | Digital Button | Xbox Button `LB` | Manual Clutch |
| **Horn / LS** | Digital Button | Xbox Button `LS` (Thumb Click) | Horn |
| **Look Back / RS** | Digital Button | Xbox Button `RS` (Thumb Click) | Look Back |
| **View / Map** | Digital Button | Xbox Button `Back` (View) | World Map |
| **Menu / Pause** | Digital Button | Xbox Button `Start` (Menu) | Pause Menu |
| **D-Pad Up/Down/Left/Right**| Digital Buttons | Xbox D-Pad Matrix | Radio / Anna / Link / Telemetry |

---

## System Requirements

- **Windows PC:** Windows 10 or 11 (64-bit), .NET 8 Runtime. *(The required ViGEmBus virtual controller driver is automatically downloaded and installed by the companion executable on first launch if not already present — zero manual downloads needed).*
- **Mobile Phone:** Android 9.0 (Pie) or higher with Touchscreen and Gyroscope/Accelerometer (Optimized for 60Hz - 120Hz displays).

---

## Building from Source

### Windows Companion (`fh6-controller-windows.exe`)
```powershell
cd wheelpad-windows
dotnet restore
dotnet publish WheelPad.App\WheelPad.App.csproj -c Release -o ..\dist-windows
```

### Android App (`fh6-controller-android.apk`)
```powershell
cd wheelpad-android
.\gradlew.bat assembleDebug
```

---

## License

MIT License. Designed with authentic Forza Horizon festival styling for PC driving enthusiasts.

