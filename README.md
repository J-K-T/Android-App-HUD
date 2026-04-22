# Ring HUD — Android AR App

BLE ring → gesture engine → Claude Vision AR overlay.  
Targets the **Moto G Play** (Snapdragon 460, 2–3 GB RAM, Adreno 619).

---

## Architecture at a glance

```
BLE Ring (GATT notify)
    └─▶ BleConnectionManager  (scan → connect → stream, exponential backoff)
            └─▶ FrameParser      (ByteArray → ImuFrame, reused ByteBuffer)
                    └─▶ SignalNormalizer  (gravity removal, EMA filter)
                            └─▶ WindowBuffer     (50-frame ring buffer @50 Hz)
                                    └─▶ PatternMatcher   (rule-based, <1 ms/window)
                                            └─▶ GestureRepository → SharedFlow
                                                        │
                                            HudViewModel (collects, debounced 150 ms)
                                                        │
                                         ┌──────────────┼──────────────┐
                                    VoiceManager   ClaudeApiClient   CameraX
                                    (SpeechRec)    (OkHttp, Vision)  (720p preview)
                                                        │
                                                   HudScreen (Compose)
```

---

## Quick start

### 1. Clone and open in Android Studio

```bash
git clone https://github.com/you/RingHUD.git
```

Open the root folder in Android Studio Hedgehog (2023.1.1) or later.

### 2. Add your Claude API key

Edit `local.properties` (never commit this file):

```
claudeApiKey=sk-ant-api03-YOUR_KEY_HERE
```

Get a key at https://console.anthropic.com.

### 3. Set your ring's GATT UUIDs

Open `app/src/main/java/com/ringhud/ble/BleConstants.kt` and update:

```kotlin
val RING_SERVICE_UUID = UUID.fromString("6E40FFF0-…")   // your ring's service
val IMU_CHAR_UUID     = UUID.fromString("6E40FFF1-…")   // notify characteristic
```

**How to find your UUIDs:**
1. Install **nRF Connect** (free, Play Store) on any Android phone
2. Scan for your ring → tap it → Connect
3. Under GATT Services, find the service that has a characteristic with **NOTIFY** property
4. Those UUIDs go in `BleConstants.kt`

**Common rings:**

| Ring | Service UUID | IMU Char UUID |
|------|-------------|---------------|
| Colmi R02/R06 | `0000180D-0000-1000-8000-00805F9B34FB` | `00002A37-0000-1000-8000-00805F9B34FB` |
| RingConn G1 | `6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E` | `6E40FFF1-B5A3-F393-E0A9-E50E24DCCA9E` |
| Amazfit Helio | Use nRF Connect to discover | — |
| Generic Nordic UART | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` |

### 4. Check the frame format

If your ring sends IMU data in a different byte layout, update `FrameParser.kt`.  
The default expects: `[seq:2][accelX:2][accelY:2][accelZ:2][gyroX:2][gyroY:2][gyroZ:2]` (little-endian int16).

### 5. Build and install

```bash
./gradlew installDebug
```

Or use **Run ▶** in Android Studio.

---

## Gestures → actions

| Ring gesture | HUD action |
|---|---|
| **Single tap** | Capture frame + Claude Vision scan |
| **Double tap** | Toggle voice input (Claude chat) |
| **Swipe up** | Increase HUD opacity |
| **Swipe down** | Decrease HUD opacity |
| **Swipe left/right** | Dismiss AI response |
| **Hold (600 ms)** | Show gesture hints |

Tune thresholds in `PatternMatcher.kt` (constants at the top of the file).

---

## Moto G Play optimisations baked in

| Concern | Solution |
|---|---|
| BLE stack hangs on `autoConnect=true` | `connectGatt(..., autoConnect=false)` |
| GATT handle limit (7 max) | Always `gatt.close()` before retry, not just `disconnect()` |
| 720p vs 1080p camera | `ResolutionSelector` capped at 720p — Adreno 619 composites cleanly |
| 50 Hz GC pressure | Pre-allocated `ByteBuffer` in `FrameParser`; `NormFrame` object pool via `ArrayDeque` |
| Claude API + BLE simultaneous I/O | API calls on `Dispatchers.IO`; BLE callbacks on dedicated handler thread |
| Battery drain from continuous scan | `SCAN_MODE_BALANCED` + 10 s scan timeout + UUID filter |

---

## File map

```
app/src/main/java/com/ringhud/
├── RingHudApplication.kt        Hilt entry point
├── MainActivity.kt              Permission gate → HudScreen
├── ble/
│   ├── BleConstants.kt          ✏️  Edit UUIDs here
│   ├── ImuFrame.kt              Raw sensor data + BleState sealed class
│   ├── FrameParser.kt           ByteArray → ImuFrame
│   ├── GattClient.kt            BluetoothGatt wrapper (Flow<ImuFrame>)
│   └── BleConnectionManager.kt  Scan → connect → retry state machine
├── gesture/
│   ├── GestureEvent.kt          Tap, DoubleTap, Swipe, Hold
│   ├── SignalNormalizer.kt      Gravity removal + scaling
│   ├── WindowBuffer.kt          50-frame sliding window
│   ├── PatternMatcher.kt        ✏️  Tune thresholds here
│   └── GestureRepository.kt     Wires everything → SharedFlow<GestureEvent>
├── data/
│   └── AppDatabase.kt           Room: GestureRecord + AppSetting
├── hud/
│   ├── HudUiState.kt            ArObject, AiStatus, HudUiState
│   ├── ClaudeApiClient.kt       Vision detect + chat (OkHttp)
│   ├── VoiceManager.kt          SpeechRecognizer → Flow<VoiceState>
│   ├── HudViewModel.kt          Connects all layers → single UiState
│   └── ui/
│       ├── HudScreen.kt         Camera + AR overlays (Compose)
│       └── PermissionScreen.kt  Permission request UI
└── platform/
    ├── RingHudService.kt        Foreground service (BLE alive in bg)
    └── di/AppModules.kt         Hilt @Provides bindings
```

---

## Troubleshooting

**Ring not found:**
- Confirm UUIDs in `BleConstants.kt` match nRF Connect output
- Check `BLUETOOTH_SCAN` permission is granted (Settings → Apps → Ring HUD → Permissions)
- On Android ≤ 11, `ACCESS_FINE_LOCATION` must also be granted for BLE scanning

**Camera black screen:**
- Moto G Play needs `PreviewView.ImplementationMode.COMPATIBLE` — already set
- Ensure `CAMERA` permission is granted

**Gestures not detecting:**
- Tap `PatternMatcher.TAP_MAGNITUDE_THRESHOLD` down from `12f` to `8f` if ring signal is weak
- Use the debug normFrame overlay (add a `LaunchedEffect` collecting `gestureRepo.normFrames`) to visualise raw signal

**Claude API errors:**
- Verify `claudeApiKey` in `local.properties` starts with `sk-ant-`
- Check logcat tag `ClaudeApiClient` for HTTP status codes
