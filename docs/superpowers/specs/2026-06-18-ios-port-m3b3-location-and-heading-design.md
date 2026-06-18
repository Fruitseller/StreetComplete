# iOS-Port M3b.3 — Live location dot + heading arrow + Android-parity GPS button (Design)

**Status:** Approved design (2026-06-18). Next step: implementation plan via writing-plans.

## Goal

Bring the first **live device data** onto the iOS map: a blue **position dot + accuracy circle**, a **heading arrow** that rotates with the compass, and a **GPS button** whose tap behaviour mirrors the Android app exactly (request permission → follow → toggle navigation mode). Map style, camera persistence and the layer stack already exist from M3b.2; this milestone feeds them real GPS/compass input.

## Scope & non-goals

In scope:
- Position dot + accuracy circle (`CurrentLocationLayers`, already ported), driven by `CLLocationManager`.
- Heading arrow driven by the device compass (`CLLocationManager` heading).
- GPS button (`LocationStateButton`, already ported) with the full Android state machine: permission request → follow mode → navigation mode, follow-off on user pan.
- Follow mode: camera recenters on each fix; first-fix zoom-to-18-if-below-17.
- Navigation mode: camera tilt 60° + map bearing = GPS course; exit keeps bearing, removes tilt.
- Follow/navigation-mode flags persisted via the existing `Preferences.mapIsFollowing` / `Preferences.mapIsNavigationMode`.

Non-goals (deferred):
- GPS **track recording** for upload (the `osmtracks` subsystem). We do **not** port `getTrackBearing`/`Trackpoint`; navigation-mode bearing comes from `CLLocation.course` instead (the per-fix direction of travel — the faithful equivalent).
- Offline tiles (#6072), OSM data download, quest pins from real data (those are M4 / later M3b).
- Distinguishing the rare `ALLOWED` state (permission granted but global Location Services off) from `DENIED`. The button behaves identically in both (same disabled icon, same "request" action), so M3b.3 collapses both to `DENIED`. Noted as an accepted simplification.
- Compass tilt/pitch output (Android's `Compass` also emits tilt; the map2 `CurrentLocationLayers` only consumes `rotation`, so tilt is dropped).

## Why this is iOS-only / Android-safe

`mapModule` (which provides `MapViewModel`) is **not** loaded by Android's `StreetCompleteApplication`, and nothing in `androidMain` references `screens.main.map2`, `MapViewModel`, or `MapScreen`. The new map stack is therefore exercised only on iOS. New commonMain interfaces (`LocationSource`, `Compass`) compile on all targets but are **bound only on iOS**; Android never resolves them at runtime, so there is no missing-binding risk and no Android code change.

## Approach (chosen: B — two thin provider interfaces, orchestration in the ViewModel)

Mirrors the maintainers' existing Android architecture, where location and compass are **two separate dumb wrappers** (`util/location/FineLocationManager` + `screens/main/map/Compass`) and the `LocationState`/follow/navigation logic lives in `MainActivity`/`MainViewModel`. We replicate that split in commonMain:

- Two **thin** commonMain provider interfaces, each single-purpose.
- All orchestration (compute `LocationState`, follow/navigation state machine, camera control) lives in `MapViewModel` + `MapScreen` — not in the platform wrappers.
- The iOS implementation is **two separate classes**, one per interface, each owning its own `CLLocationManager`. Two classes (rather than one class behind both interfaces) keeps the Android-parity 1:1 and sidesteps the `start()`/`stop()` method-name collision that a single dual-interface class would hit. iOS permits multiple `CLLocationManager` instances; permission is app-global and shared.

### Data flow

```
CLLocationManager (location)        CLLocationManager (heading)
   │ delegate                          │ delegate
   ▼                                   ▼
IosLocationSource : LocationSource   IosCompass : Compass        (iosMain, Koin-bound: iosLocationModule)
   │ location: StateFlow<Location?>     │ rotation: StateFlow<Float?>
   │ hasPermission: StateFlow<Boolean>  │ (degrees, true north, declination applied)
   │ requestPermission()/start()/stop() │ start()/stop()
   └───────────────┬────────────────────┘
                   ▼
        MapViewModel (commonMain)   ← injected LocationSource + Compass + Preferences
          • location, rotation (re-exposed)
          • locationState: StateFlow<LocationState>   (derived: permission + tracking + first-fix)
          • isFollowing, isNavigationMode             (StateFlow; init from + persisted to Preferences)
          • onClickLocationButton(), setFollowing(v), setNavigationMode(v)
                   ▼
        MapScreen (commonMain)
          • LocationStateButton(state=locationState, isFollowing, isNavigationMode) → vm.onClickLocationButton()
          • follow: animate camera to location on each fix (≈600ms); first-fix zoom→18 if current <17
          • nav mode: camera tilt=60 + bearing=location.bearing(course); exit → tilt=0, keep bearing
          • pan-detect: cameraState.moveReason == GESTURE while following → vm.setFollowing(false)
          • arrowRotation = rotation − cameraState.position.bearing
                   ▼
        Map(location, rotation=arrowRotation)  →  CurrentLocationLayers(location, arrowRotation)   when location != null
```

## Component contracts

### `LocationSource` (new, commonMain `data/location/`)

```kotlin
/** Thin provider of device location updates. Platform impl is a dumb wrapper
 *  (Android: FineLocationManager; iOS: CLLocationManager). LocationState / follow logic
 *  lives in the consumer (MapViewModel), mirroring Android's MainActivity/MainViewModel. */
interface LocationSource {
    /** the most recent fix; null until the first one arrives after start() */
    val location: StateFlow<Location?>
    /** whether the app currently holds location permission (authorizedWhenInUse/Always) */
    val hasPermission: StateFlow<Boolean>
    /** ask the OS for permission; no-op if already granted */
    fun requestPermission()
    /** begin / stop receiving location updates (start requires permission) */
    fun start()
    fun stop()
}
```

### `Compass` (new, commonMain `data/location/`)

```kotlin
/** Thin provider of device heading. Mirrors androidMain screens/main/map/Compass.
 *  rotation = degrees clockwise from true north, magnetic declination already applied;
 *  null when no heading is available. */
interface Compass {
    val rotation: StateFlow<Float?>
    fun start()
    fun stop()
}
```

(Both declare `start()/stop()`, but they are separate interfaces implemented by separate classes, so there is no collision.)

### `Location` (edit, commonMain `data/location/Location.kt`)

Add an optional, backward-compatible field carrying the GPS direction of travel (used only for navigation-mode map bearing on iOS):

```kotlin
data class Location(
    val position: LatLon,
    val accuracy: Float,
    val elapsedDuration: Duration,
    val bearing: Float? = null,   // GPS course, degrees clockwise from north; null when unknown/stationary
)
```

The default keeps every existing 3-arg construction valid (Android `toLocation()`, commonTest). Android leaves it null.

### iOS implementations (new, iosMain)

- `IosLocationSource : LocationSource` — owns a `CLLocationManager` (`desiredAccuracy = kCLLocationAccuracyBest`) and an `NSObject`-subclass delegate (`CLLocationManagerDelegateProtocol`). `requestPermission()` → `requestWhenInUseAuthorization()`; `start()` → `startUpdatingLocation()`; `stop()` → `stopUpdatingLocation()`. Delegate maps:
  - `didUpdateLocations` → newest `CLLocation` → `Location` (position from `coordinate`; `accuracy` from `horizontalAccuracy`; `elapsedDuration` from a **monotonic** clock so it composes with `RecentLocations`/`SurveyChecker`; `bearing` from `course` when `courseAccuracy >= 0` and `speed > 0`, else null).
  - `didChangeAuthorization` → update `hasPermission`.
- `IosCompass : Compass` — owns a `CLLocationManager` + delegate. `start()` → `startUpdatingHeading()` (set a small `headingFilter`); `stop()` → `stopUpdatingHeading()`. Delegate `didUpdateHeading` → `trueHeading` (degrees, declination already applied by iOS); fall back to `magneticHeading` when `trueHeading < 0`.
- `iosLocationModule` (new): `single<LocationSource> { IosLocationSource() }`, `single<Compass> { IosCompass() }`; registered in `di/InitKoin.kt`.

### `MapViewModel` (edit, commonMain)

Constructor gains `LocationSource`, `Compass`, `Preferences`. (`mapModule`: `viewModel { MapViewModel(get(), get(), get(), get()) }` — all resolvable on iOS; never instantiated on Android.)

- `location = locationSource.location`; `rotation = compass.rotation` (raw heading; the arrow's camera-relative rotation is computed in MapScreen, which knows the camera bearing).
- `locationState` derived from `hasPermission` + an internal `trackingRequested` flag + `location`:
  - `!hasPermission` → `DENIED`
  - tracking not requested → `ENABLED`
  - requested, no fix yet → `SEARCHING`
  - fix received → `UPDATING`
- `isFollowing` / `isNavigationMode`: `MutableStateFlow` seeded from `prefs.mapIsFollowing` / `prefs.mapIsNavigationMode`; setters write back to prefs.
- Startup: if `hasPermission` already true → `locationSource.start()` + `compass.start()`, `trackingRequested = true` (follow restored from prefs, not forced).
- Permission grant via button: observing `hasPermission` flip false→true after a user request → `start()` + `compass.start()` + `setFollowing(true)` (mirrors Android `onLocationIsEnabled`, which auto-follows on enable).
- `onClickLocationButton()` (verbatim Android logic):
  ```
  if (!locationState.value.isEnabled)      requestPermission()        // → grant → start + follow(true)
  else if (!isFollowing.value)             setFollowing(true)
  else                                     setNavigationMode(!isNavigationMode.value)
  ```
- `onCleared()`: `locationSource.stop()` + `compass.stop()` (plus existing listener cleanup).

### `Map` (edit, commonMain `map2/Map.kt`)

Add `location: Location? = null` and `rotation: Float? = null` params; in `aboveLabelsContent`, after `PinsLayers`, render `location?.let { CurrentLocationLayers(it, rotation) }` (top-most).

### `MapScreen` (edit, commonMain)

- Collect `location`, `rotation`, `locationState`, `isFollowing`, `isNavigationMode`.
- `arrowRotation = rotation?.let { it - cameraState.position.bearing.toFloat() }`; pass `location` + `arrowRotation` to `Map`.
- `LocationStateButton` (bottom-end, safe-area padded) wired to `vm::onClickLocationButton`, with `state`, `isFollowing`, `isNavigationMode`.
- Follow effect: when `isFollowing && location != null`, `cameraState.animateTo(...)` to the fix; first fix (tracked by a local `zoomedYet`) sets zoom 18 if current < 17. In navigation mode also set `tilt = 60` and `bearing = location.bearing`; leaving navigation mode animates `tilt = 0` and keeps bearing.
- Pan-detect: a `snapshotFlow { cameraState.moveReason }` collector calls `vm.setFollowing(false)` when it sees `CameraMoveReason.GESTURE` while following (programmatic follow animations report `PROGRAMMATIC`, so they don't self-cancel).

### `Info.plist` (edit)

Add `NSLocationWhenInUseUsageDescription`, e.g.: *"StreetComplete shows your location on the map so you can survey places around you."* (WhenInUse also covers heading; no separate key needed.)

## Heading / rotation conventions (must match the ported layer)

`CurrentLocationLayers(location, rotation)` feeds `iconRotate = const(rotation)` (degrees). The value must be **camera-relative**, exactly as androidMain's `CurrentLocationMapComponent`: `arrowRotation = deviceHeadingDegrees − mapCameraBearingDegrees`. iOS `trueHeading` already has declination applied (no `GeomagneticField` math, unlike Android). In navigation mode the map bearing follows `CLLocation.course`, so the arrow shows the heading offset from the travel direction; in normal mode the map bearing is usually 0, so the arrow ≈ absolute heading.

## Files

New:
- `app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/LocationSource.kt`
- `app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/Compass.kt`
- `app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationSource.kt`
- `app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosCompass.kt`
- `app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationModule.kt`

Edit:
- `app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/Location.kt` (+`bearing`)
- `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt`
- `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt` (extra constructor args)
- `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt`
- `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/Map.kt`
- `app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt`
- `iosApp/iosApp/Info.plist`

## Sub-increments (each: link gate → simulator build/run → commit)

- **M3b.3a — Location dot + button + follow.** `LocationSource` + `IosLocationSource` + `Location.bearing` + Info.plist + Koin + `MapViewModel` location state machine + `Map`/`MapScreen` wiring of the dot + permission flow + follow/recenter + first-fix zoom + pan-to-unfollow. Verifiable on the **simulator** (set/replay a location via `xcrun simctl location` or the Simulator's Features ▸ Location menu).
- **M3b.3b — Heading arrow + navigation mode.** `Compass` + `IosCompass` + compass wiring in the VM + arrow-rotation math + navigation-mode camera (tilt + course bearing). Compass is **device-only** to verify (the simulator has no live magnetometer).

## Verification

- **Simulator:** permission prompt, dot + accuracy circle, follow/recenter, first-fix zoom, pan-to-unfollow — all drivable with a simulated location.
- **Device (iPhone 16 Pro):** the real test — live GPS dot, heading arrow rotating with the phone, follow + navigation mode while walking. Signing profile valid through 2026-06-21 (in-window).
- No unit-test gate (Android APK / iOS commonTest are upstream-WIP-red, not gates), consistent with M3a/M3b.

## Key decisions & deviations (for review)

1. **B over A:** two thin provider interfaces + VM orchestration, matching the maintainers' Android split; chosen over a single combined interface despite more ceremony, because it is the architecture the maintainers would accept and it lets Android later implement the same interfaces.
2. **Navigation bearing from `CLLocation.course`, not track recording** — avoids porting the `osmtracks` subsystem while staying faithful (both are "direction of travel"). Requires the additive `Location.bearing` field.
3. **`ALLOWED` collapsed into `DENIED`** — behaviourally identical for the button; the rare "permission granted, global Location Services off" case is not separately modelled.
4. **Two `CLLocationManager` instances** (one per iOS class) rather than one shared — keeps the 1:1 Android-parity class split and avoids the `start()/stop()` name clash.
5. **Battery/lifecycle:** updates start on permission/VM-init and stop on `onCleared()`. Finer-grained pause-on-background is deferred (acceptable for this increment; the screen is the whole app today).
