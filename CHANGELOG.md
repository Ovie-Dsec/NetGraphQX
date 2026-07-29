# Changelog

All notable changes to NetGraph QX are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-07-29

### Added
- **Real WiFi telemetry**: TelemetryEngine now reads live WiFi connection data
  from the device using `WifiManager` (SSID, BSSID, RSSI, IP address, link speed,
  frequency band) instead of simulated values.
- **Real ICMP ping**: Each telemetry tick performs an actual `ping -c 1` to the
  default gateway to measure real latency and reachability. The UP/DOWN/UNSTABLE
  badge now reflects real connectivity.
- **Internet reachability check**: Uses `ConnectivityManager` to verify actual
  internet capability alongside gateway reachability.
- **Signal strength from RSSI**: Signal percentage is now derived from real RSSI
  readings (`-100 dBm` → 0%, `-40 dBm` → 100%) instead of simulation.
- **Permissions**: Added `NEARBY_WIFI_DEVICES` (API 33+, auto-granted) and
  `ACCESS_FINE_LOCATION` (API 32-, legacy) to `AndroidManifest.xml` for reading
  SSID/BSSID.
- **Graceful degradation**: When required permissions are missing, the engine
  returns `<no permission>` for SSID/BSSID and shows DOWN for affected fields
  instead of crashing.
- **Public API methods**: `getCurrentSsid()` and `getGatewayIp()` exposed for
  external use by future tools.

### Changed
- `TelemetryEngine` constructor now requires `Context` (passed from `MainActivity`
  via `LocalContext.current.applicationContext`).
- `reset()` and `switchAp()` are no-ops in the real engine (kept for API
  compatibility).
- Version bumped to **1.2.0** (build 5).

### Removed
- All simulated AP name lists, BSSID pools, gateway IP pools, and random
  wobble/dropout logic. The engine now measures truth instead of generating it.

## [1.1.0] - 2026-07-29

### Added
- **AP arrow visualization**: Telemetry mode now shows a visual arrow from a
  device icon (bottom centre) pointing upward to an access point icon (top centre),
  with concentric signal rings around the arrow shaft.
- **Live status badge**: A large UP/DOWN/UNSTABLE badge is displayed on the right
  side of the canvas, color-coded green/yellow/red.
- **Signal strength bars**: Five vertical bars on the left side of the canvas
  indicate signal quality (0-100%).
- **Info panel**: Text panel below the device shows IP, BSSID, latency, and status.
- **Status bar metrics**: Bottom overlay now shows SSID, SIGNAL%, LATENCY, and
  STATUS instead of PING/LOSS/CPU/MEM.
- **New function pad macros**: Telemetry pad replaced with PING GW, SCAN, SIGNAL,
  CHANNEL, BAND, and STATUS.
- **Version bump**: v1.1.0 (build 4).

### Changed
- **TelemetryEngine rewritten**: Emits `Flow<ApTelemetryTick>` at 1-second
  intervals. Simulates real AP connectivity data: SSID, BSSID, signal strength,
  gateway ping latency, and random reachable/unreachable transitions.
- **AppState model updated**: `TelemetryTick` replaced with `ApTelemetryTick`
  (fields: ssid, bssid, ipAddress, signalStrength, latencyMs, reachable, status,
  statusColor). New `ApStatus` enum (UP/DOWN/UNSTABLE).
- **GraphCanvas telemetry signature**: `telemetryTicks: List<TelemetryTick>`
  replaced with `apTick: ApTelemetryTick?`. Canvas now calls `drawApVisualization()`
  instead of `drawTelemetryGrid()`/`drawTelemetryWaveform()`.
- **MainActivity telemetry collection**: Uses a single `ApTelemetryTick?` state
  variable instead of a 300-entry list ring buffer.

### Removed
- `drawTelemetryGrid()` and `drawTelemetryWaveform()` from `GraphCanvas.kt`.
- Old PING/TRACERT/NSLOOKUP/CPU/MEM/NETSTAT telemetry macros.

## [1.0.2] - 2026-07-29

### Added
- **Version tracking**: `CHANGELOG.md` created to document all releases. Version
  now reads from `BuildConfig` (automatically set from `build.gradle.kts`) and
  displays as `v1.0.2 (build 3)` in the title bar.
- **APK artifact naming**: APKs are saved with versioned filenames
  (`NetGraphQX-v1.0.2.apk`) alongside the generic `NetGraphQX-debug.apk` for
  quick install.

### Fixed
- **Canvas rendering at zero height**: `GraphCanvas` used `.fillMaxWidth()` but had no height
  modifier. Compose `Canvas` has zero intrinsic size, so the graph area collapsed to 0px
  tall. Changed to `.fillMaxSize()` so the canvas fills its allocated `weight()` space.
- **Canvas now visible**: Mathematical curves and telemetry waveforms render properly
  rather than producing an invisible graph area.

## [1.0.1] - 2026-07-29

### Added
- **Calculation result display**: The expression bar now distinguishes between pure
  arithmetic (no `x` variable) and function expressions. For arithmetic like `2+2`,
  the result `= 4` is shown in large cyan text in the bottom bar.
- `CalculationResult` sealed class to model arithmetic values, functions, errors,
  and empty states.

### Changed
- `ExpressionBar` composable now accepts a `result` parameter and adjusts its
  display format accordingly.
- `TopBar` simplified (removed unused `expression` parameter).

## [1.0.0] - 2026-07-29

### Added
- **Full project scaffold**: Android Kotlin project with Jetpack Compose.
- **Dual-mode architecture** with top toggle:
  - `MATHEMATICS` mode: function graphing via exp4j expression parser.
  - `TELEMETRY` mode: real-time network/hardware data streaming via coroutines.
- **Three-section UI layout**:
  - Section 1: Custom Compose Canvas graph viewport (40% base, dynamically
    expands when sections 2/3 collapse).
  - Section 2: Function/macro pad with quick-access buttons (collapsible).
  - Section 3: Full in-app QWERTY keypad (collapsible, suppresses OS keyboard).
- **MathEngine.kt**: exp4j wrapper with `validate()`, `evaluate()`, `sample()`,
  `adaptiveSample()`, built-in function shortcuts.
- **TelemetryEngine.kt**: `Flow<TelemetryTick>` emitter at 250ms with simulated
  ping, packet loss, CPU, and memory data; 300-entry ring buffer.
- **GraphCanvas.kt**: 60 FPS Compose Canvas with Cartesian grid, curve rendering,
  waveform plots, color-coded thresholds, pinch-to-zoom, pan, and tap-to-trace
  crosshair with tooltip.
- **InAppKeypad.kt**: Full QWERTY key matrix (6 rows: numbers, letters, symbols,
  operators) with CLR/Space/Enter action keys.
- **Dark theme**: Monospace typography, Material3 dark color scheme, telemetry
  status colors (green/yellow/red).
- **Adaptive launcher icon**: Custom vector icon with graph curve and grid lines.
- **Build configuration**: Gradle 8.9, AGP 8.7.2, Kotlin 2.0.21, Compose BOM
  2024.12.01, exp4j 0.4.8, target SDK 36.
- **APK output**: Debug APK built and copied to `APKs/` directory.
