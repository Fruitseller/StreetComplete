# iOS-Port M3b.3 — Live location dot + heading arrow + Android-parity GPS button — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put a live GPS location dot + compass heading arrow on the iOS map, with a GPS button that behaves exactly like the Android app (request permission → follow → navigation mode).

**Architecture:** Two thin commonMain provider interfaces (`LocationSource`, `Compass`) — dumb wrappers, one iOS class each backed by its own `CLLocationManager`. All state-machine logic (`LocationState`, follow/navigation mode) lives in the existing commonMain `MapViewModel` + `MapScreen`, mirroring how Android keeps `FineLocationManager`/`Compass` dumb and orchestrates in `MainActivity`/`MainViewModel`. The new map stack is iOS-only (Android never loads `mapModule`), so the interfaces are Koin-bound only on iOS — no Android code changes.

**Tech Stack:** Kotlin Multiplatform 2.3.20, Compose Multiplatform 1.10.3, maplibre-compose 0.13.0, Koin, Kotlin/Native CoreLocation interop (`platform.CoreLocation.*`). iOS bundle id `de.westnordost.streetcomplete.ios`.

**Design doc:** `docs/superpowers/specs/2026-06-18-ios-port-m3b3-location-and-heading-design.md`

## Global Constraints

- **No unit-test gate.** Android APK and iOS commonTest are upstream-WIP-red (NOT our regressions; see backlog). Per-task gates are: **(1) link gate**, **(2) simulator build+run+screenshot+log**, **(3) device** (Task 5 only), **(4) commit**. This deliberately replaces the generic TDD red/green cycle — it is the established model used by the M3a/M3b/M3b.2 plans in this repo.
- **Link gate command:** `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64` → must print `BUILD SUCCESSFUL`.
- **`rm -rf build/ios` before every `xcodebuild`** (stale-relink gotcha: Xcode does not relink when only the static framework content changes).
- **No `NSLog`/`%@` from Kotlin/Native** (crashes K/N). Use `println`/`Log`.
- **Signing TEAM_ID `2DPUG448BC`**; free personal-team profile **expires 2026-06-21**.
- **No merge commits**; work directly on `master` (this fork's convention). Commit at the end of each task.
- **iOS targets:** `iosArm64` + `iosSimulatorArm64` only (no `iosX64`).
- Kotlin/Native CoreLocation calls that touch `CValue`/`useContents` need `@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)`. If the K/N compiler additionally demands `BetaInteropApi` for the `NSObject` delegate subclass, add `@OptIn(kotlinx.cinterop.BetaInteropApi::class)` to that class (apply only if the compiler reports it).

## Verification model (referenced by every task)

**Link gate:**
```
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL`.

**Simulator Smoke sequence** (`SIM_ID=5B7C16A4-2C11-4E0C-9549-42F7AC648438`):
```
rm -rf build/ios
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=$SIM_ID" -derivedDataPath build/ios build
xcrun simctl boot "$SIM_ID" 2>/dev/null || true
APP=$(find build/ios/Build/Products/Debug-iphonesimulator -maxdepth 1 -name 'StreetComplete.app')
xcrun simctl install booted "$APP"
xcrun simctl launch booted de.westnordost.streetcomplete.ios; sleep 8
xcrun simctl io booted screenshot /tmp/<shot>.png
xcrun simctl spawn booted log show --last 120s --predicate 'process == "StreetComplete"' 2>/dev/null \
  | grep -iE "exception|crash|Throwable|Fatal|EXC_|NoBeanDef|MapLibre|Metal|CoreLocation" | head -40
```
A benign `automaticallyAdjustsScrollViewInsets` deprecation warning is fine.

**Simulate a GPS location** (for follow/dot verification — the simulator has no real GPS):
```
xcrun simctl location booted set 52.516,13.378     # Berlin Brandenburg Gate
```
(If `simctl location` is unavailable on this Xcode, use the Simulator GUI: **Features ▸ Location ▸ Custom Location…**, set 52.516 / 13.378.) The compass/heading cannot be simulated — verify the arrow on the **device** (Task 5).

**To force the map on screen for a screenshot** (taps aren't scriptable): temporarily set `MainMenuNavHost`'s `startDestination = MainMenuDestination.Map`, screenshot, then revert before committing. (Same trick the M3b.1 plan used.)

---

## Task 1: Platform location layer — `LocationSource`, `Location.bearing`, `IosLocationSource`, Koin, Info.plist

Adds the location provider abstraction + its iOS implementation and the permission plumbing. No UI yet; `MapViewModel` is untouched, so this is runtime-invisible — the gate is link-green + the app still launching (no Koin/Info.plist regression).

**Files:**
- Create: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/LocationSource.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/Location.kt`
- Create: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationSource.kt`
- Create: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationModule.kt`
- Modify: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt`
- Modify: `iosApp/iosApp/Info.plist`

**Interfaces:**
- Produces:
  - `interface LocationSource { val location: StateFlow<Location?>; val hasPermission: StateFlow<Boolean>; fun requestPermission(); fun start(); fun stop() }`
  - `data class Location(position: LatLon, accuracy: Float, elapsedDuration: Duration, bearing: Float? = null)`
  - `class IosLocationSource : LocationSource`
  - `val iosLocationModule` (Koin module binding `LocationSource` → `IosLocationSource`)

- [ ] **Step 1: Create the `LocationSource` interface**

`app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/LocationSource.kt`:
```kotlin
package de.westnordost.streetcomplete.data.location

import kotlinx.coroutines.flow.StateFlow

/** Thin provider of device location updates. The platform implementation is a dumb wrapper
 *  (Android: FineLocationManager; iOS: CLLocationManager); the LocationState / follow logic
 *  lives in the consumer (MapViewModel), mirroring Android's MainActivity/MainViewModel. */
interface LocationSource {
    /** the most recent fix; null until the first one arrives after [start] */
    val location: StateFlow<Location?>
    /** whether the app currently holds location permission (authorizedWhenInUse/Always) */
    val hasPermission: StateFlow<Boolean>
    /** ask the OS for location permission; no-op if already granted */
    fun requestPermission()
    /** begin receiving location updates (requires permission) */
    fun start()
    /** stop receiving location updates */
    fun stop()
}
```

- [ ] **Step 2: Add the optional `bearing` field to `Location`**

Edit `app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/Location.kt` to:
```kotlin
package de.westnordost.streetcomplete.data.location

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlin.time.Duration

data class Location(
    val position: LatLon,
    val accuracy: Float,
    val elapsedDuration: Duration,
    /** GPS course (direction of travel), degrees clockwise from north; null when unknown/stationary */
    val bearing: Float? = null,
)
```
The default keeps every existing 3-arg construction valid (`android.location.Location.toLocation()`, `RecentLocationsTest`). Do not touch those call sites.

- [ ] **Step 3: Create `IosLocationSource`**

`app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationSource.kt`:
```kotlin
@file:OptIn(ExperimentalForeignApi::class)

package de.westnordost.streetcomplete.util.location

import de.westnordost.streetcomplete.data.location.Location
import de.westnordost.streetcomplete.data.location.LocationSource
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject

/** iOS [LocationSource] backed by CLLocationManager. Dumb wrapper: delivers fixes + permission
 *  state only; LocationState/follow logic lives in MapViewModel (parity with androidMain's
 *  FineLocationManager being a dumb wrapper used by MainActivity/MainViewModel). */
class IosLocationSource : LocationSource {
    private val _location = MutableStateFlow<Location?>(null)
    override val location: StateFlow<Location?> = _location.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    override val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val manager = CLLocationManager()
    private val delegate = Delegate()

    init {
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.delegate = delegate
        _hasPermission.value = manager.authorizationStatus.isAuthorized()
    }

    override fun requestPermission() { manager.requestWhenInUseAuthorization() }
    override fun start() { manager.startUpdatingLocation() }
    override fun stop() { manager.stopUpdatingLocation() }

    /** retained strongly by the (singleton) IosLocationSource, so the ObjC weak delegate stays alive */
    private inner class Delegate : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val clLocation = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            _location.value = clLocation.toLocation()
        }
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            _hasPermission.value = manager.authorizationStatus.isAuthorized()
        }
    }
}

private fun CLAuthorizationStatus.isAuthorized(): Boolean =
    this == kCLAuthorizationStatusAuthorizedWhenInUse || this == kCLAuthorizationStatusAuthorizedAlways

private fun CLLocation.toLocation(): Location {
    val (lat, lon) = coordinate.useContents { latitude to longitude }
    return Location(
        position = LatLon(latitude = lat, longitude = lon),
        accuracy = horizontalAccuracy.toFloat(),
        // monotonic (like Android's elapsedRealtimeNanos), NOT wall-clock, so RecentLocations dedup works
        elapsedDuration = NSProcessInfo.processInfo.systemUptime.seconds,
        bearing = if (course >= 0.0) course.toFloat() else null,
    )
}
```

- [ ] **Step 4: Create the iOS Koin module**

`app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationModule.kt`:
```kotlin
package de.westnordost.streetcomplete.util.location

import de.westnordost.streetcomplete.data.location.LocationSource
import org.koin.dsl.module

/** Binds the iOS device-sensor providers. Bound only on iOS — the new map stack (MapViewModel)
 *  is not loaded on Android, so these interfaces are never resolved there. */
val iosLocationModule = module {
    single<LocationSource> { IosLocationSource() }
}
```
(The `Compass` binding is added to this module in Task 4.)

- [ ] **Step 5: Register `iosLocationModule` in the iOS Koin graph**

Edit `app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt`:
- add the import (alphabetical with the other `de.westnordost.streetcomplete.*` imports):
```kotlin
import de.westnordost.streetcomplete.util.location.iosLocationModule
```
- add `iosLocationModule,` to the `modules(...)` list (next to `iosControllersModule,`).

- [ ] **Step 6: Add the location usage description to Info.plist**

Edit `iosApp/iosApp/Info.plist` — inside the top-level `<dict>`, add:
```xml
	<key>NSLocationWhenInUseUsageDescription</key>
	<string>StreetComplete shows your location on the map so you can survey places around you.</string>
```
(WhenInUse authorization also covers compass heading — no separate key needed.)

- [ ] **Step 7: Link gate**

Run the link gate (Verification model). Expected: `BUILD SUCCESSFUL`.
If K/N reports the `Delegate` needs `BetaInteropApi`, add `@OptIn(kotlinx.cinterop.BetaInteropApi::class)` to the `Delegate` class and re-run.

- [ ] **Step 8: Simulator regression check**

Run the Simulator Smoke sequence, screenshot `/tmp/m3b3-task1.png`. Expected: app launches to the **menu** as before (we changed Koin + Info.plist, so confirm no startup crash / no `NoBeanDef`). The map/dot is not wired yet — no visible change.

- [ ] **Step 9: Commit**
```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/LocationSource.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/Location.kt \
        app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationSource.kt \
        app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationModule.kt \
        app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt \
        iosApp/iosApp/Info.plist
git commit -m "M3b.3a: add LocationSource + IosLocationSource (CLLocationManager) + Koin + Info.plist"
```

---

## Task 2: `MapViewModel` location state machine (location + follow, no compass yet)

Wires `LocationSource` into the iOS `MapViewModel`: exposes the location flow, derives `LocationState`, owns the follow flag (persisted), and implements the button handler's request/follow branches. Navigation mode + compass come in Task 4. No UI change yet → gate is link-green + map still rendering.

**Files:**
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt`

**Interfaces:**
- Consumes: `LocationSource` (Task 1); `Preferences.mapIsFollowing` (existing, commonMain).
- Produces (on `MapViewModel`):
  - `val location: StateFlow<Location?>`
  - `val locationState: StateFlow<LocationState>`
  - `val isFollowing: StateFlow<Boolean>`
  - `fun onClickLocationButton()`
  - `fun setFollowing(value: Boolean)`

- [ ] **Step 1: Rewrite `MapViewModel` to inject `LocationSource` + `Preferences` and add the location state machine**

Replace `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt` with:
```kotlin
package de.westnordost.streetcomplete.screens.main.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.download.tiles.TilePos
import de.westnordost.streetcomplete.data.location.Location
import de.westnordost.streetcomplete.data.location.LocationSource
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.screens.main.controls.LocationState
import de.westnordost.streetcomplete.screens.main.map2.layers.Marker
import de.westnordost.streetcomplete.screens.main.map2.layers.Pin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(
    private val downloadedTilesSource: DownloadedTilesSource,
    private val locationSource: LocationSource,
    private val prefs: Preferences,
) : ViewModel() {

    private val _downloadedTiles = MutableStateFlow<List<TilePos>>(emptyList())
    val downloadedTiles: StateFlow<List<TilePos>> = _downloadedTiles.asStateFlow()

    private val _geometryMarkers = MutableStateFlow<List<Marker>>(emptyList())
    val geometryMarkers: StateFlow<List<Marker>> = _geometryMarkers.asStateFlow()

    private val _focusedGeometry = MutableStateFlow<ElementGeometry?>(null)
    val focusedGeometry: StateFlow<ElementGeometry?> = _focusedGeometry.asStateFlow()

    private val _pins = MutableStateFlow<List<Pin>>(emptyList())
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    // --- location ---
    val location: StateFlow<Location?> = locationSource.location

    private val _isFollowing = MutableStateFlow(prefs.mapIsFollowing)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _trackingRequested = MutableStateFlow(false)
    private var pendingFollowOnGrant = false

    /** DENIED until permission; ENABLED once permitted but not tracking; SEARCHING while waiting
     *  for the first fix; UPDATING once fixes arrive. (ALLOWED — permission granted but global
     *  Location Services off — is collapsed into DENIED: the button behaves identically.) */
    val locationState: StateFlow<LocationState> = combine(
        locationSource.hasPermission, _trackingRequested, locationSource.location
    ) { hasPermission, tracking, location ->
        when {
            !hasPermission -> LocationState.DENIED
            !tracking -> LocationState.ENABLED
            location == null -> LocationState.SEARCHING
            else -> LocationState.UPDATING
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocationState.DENIED)

    private val downloadedTilesListener = object : DownloadedTilesSource.Listener {
        override fun onUpdated() { reloadDownloadedTiles() }
    }

    init {
        downloadedTilesSource.addListener(downloadedTilesListener)
        reloadDownloadedTiles()
        // Start tracking as soon as permission is held. On launch with permission already granted,
        // follow stays as restored from prefs; a fresh grant via the button also turns follow on
        // (mirrors Android onLocationIsEnabled).
        viewModelScope.launch {
            locationSource.hasPermission.collect { granted ->
                if (granted && !_trackingRequested.value) {
                    locationSource.start()
                    _trackingRequested.value = true
                    if (pendingFollowOnGrant) { setFollowing(true); pendingFollowOnGrant = false }
                }
            }
        }
    }

    private fun reloadDownloadedTiles() {
        viewModelScope.launch {
            _downloadedTiles.value = withContext(Dispatchers.IO) {
                downloadedTilesSource.getAll(ApplicationConstants.DELETE_OLD_DATA_AFTER)
            }
        }
    }

    fun onClickLocationButton() {
        if (!locationState.value.isEnabled) {
            pendingFollowOnGrant = true
            locationSource.requestPermission()
        } else {
            // M3b.3b adds: if already following, toggle navigation mode instead.
            setFollowing(true)
        }
    }

    fun setFollowing(value: Boolean) {
        _isFollowing.value = value
        prefs.mapIsFollowing = value
    }

    fun putGeometryMarkers(markers: List<Marker>) { _geometryMarkers.value = markers }
    fun clearGeometryMarkers() { _geometryMarkers.value = emptyList() }
    fun setFocusedGeometry(geometry: ElementGeometry?) { _focusedGeometry.value = geometry }
    fun setPins(pins: List<Pin>) { _pins.value = pins }

    override fun onCleared() {
        downloadedTilesSource.removeListener(downloadedTilesListener)
        locationSource.stop()
    }
}
```

- [ ] **Step 2: Update `mapModule` for the new constructor args**

Edit `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt`:
```kotlin
val mapModule = module {
    viewModel { MapViewModel(get(), get(), get()) }
}
```
(`get()` resolves `DownloadedTilesSource`, `LocationSource` (Task 1), `Preferences` — all bound on iOS; never instantiated on Android.)

- [ ] **Step 3: Link gate**

Run the link gate. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Simulator regression check**

Run the Simulator Smoke sequence with the `startDestination = MainMenuDestination.Map` trick, screenshot `/tmp/m3b3-task2.png`, then revert `startDestination`. Expected: the map renders exactly as in M3b.2 (no dot yet — MapScreen doesn't read the new flows). Confirm no crash / no `NoBeanDef` for `MapViewModel`.

- [ ] **Step 5: Commit**
```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt
git commit -m "M3b.3a: MapViewModel location state machine (location flow + LocationState + follow)"
```

---

## Task 3: Wire the location dot + GPS button + follow into the map (M3b.3a complete)

Renders the dot via `CurrentLocationLayers` (heading `null` for now), adds the `LocationStateButton`, recenters/follows the camera, applies first-fix zoom, and drops follow on user pan. Simulator-verifiable.

**Files:**
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/Map.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt`

**Interfaces:**
- Consumes: `MapViewModel.location` / `locationState` / `isFollowing` / `onClickLocationButton()` / `setFollowing()` (Task 2); `CurrentLocationLayers(location, rotation)` (existing); `LocationStateButton(...)` + `LocationState` (existing); `CameraState.moveReason` / `CameraMoveReason.GESTURE` / `animateTo` (maplibre-compose 0.13.0).
- Produces: `Map(..., location: Location? = null, rotation: Float? = null)`.

- [ ] **Step 1: Add `location` + `rotation` params to `Map` and render `CurrentLocationLayers`**

Edit `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/Map.kt`:
- add imports:
```kotlin
import de.westnordost.streetcomplete.data.location.Location
import de.westnordost.streetcomplete.screens.main.map2.layers.CurrentLocationLayers
```
- add the two params to the `Map(...)` signature, after `pins`:
```kotlin
    pins: Collection<Pin> = emptyList(),
    location: Location? = null,
    rotation: Float? = null,
```
- in `aboveLabelsContent`, render the location layers last (top-most):
```kotlin
            aboveLabelsContent = {
                GeometryMarkersLayers(geometryMarkers)
                focusedGeometry?.let { FocusedGeometryLayers(it) }
                PinsLayers(pins)
                location?.let { CurrentLocationLayers(it, rotation) }
            },
```

- [ ] **Step 2: Wire the dot + button + follow into `MapScreen`**

Replace `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt` with:
```kotlin
package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.screens.main.controls.LocationStateButton
import de.westnordost.streetcomplete.screens.main.map2.Map
import de.westnordost.streetcomplete.ui.common.BackIcon
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.spatialk.geojson.Position

/** iOS map screen (M3b.3a): live location dot + Android-parity GPS button (request → follow). */
@Composable
fun MapScreen(onClickBack: () -> Unit) {
    val viewModel: MapViewModel = koinViewModel()
    val downloadedTiles by viewModel.downloadedTiles.collectAsState()
    val markers by viewModel.geometryMarkers.collectAsState()
    val focusedGeometry by viewModel.focusedGeometry.collectAsState()
    val pins by viewModel.pins.collectAsState()
    val location by viewModel.location.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()

    val prefs: Preferences = koinInject()
    val cameraState = rememberCameraState(
        firstPosition = remember {
            if (prefs.mapZoom == 0.0) {
                CameraPosition(target = Position(longitude = 13.4, latitude = 52.5), zoom = 3.0)
            } else {
                CameraPosition(
                    target = Position(longitude = prefs.mapPosition.longitude, latitude = prefs.mapPosition.latitude),
                    zoom = prefs.mapZoom,
                    bearing = prefs.mapRotation,
                    tilt = prefs.mapTilt,
                )
            }
        }
    )

    // Persist camera position when movement ends.
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isCameraMoving to cameraState.position }
            .filterNot { it.first }
            .map { it.second }
            .distinctUntilChanged()
            .collect { pos ->
                prefs.mapPosition = LatLon(latitude = pos.target.latitude, longitude = pos.target.longitude)
                prefs.mapZoom = pos.zoom
                prefs.mapRotation = pos.bearing
                prefs.mapTilt = pos.tilt
            }
    }

    // Follow mode: recenter on each fix; zoom to 18 on the first fix if zoomed out past 17.
    var zoomedYet by remember { mutableStateOf(false) }
    LaunchedEffect(isFollowing, location) {
        if (!isFollowing) { zoomedYet = false; return@LaunchedEffect }
        val loc = location ?: return@LaunchedEffect
        val current = cameraState.position
        val zoom = if (!zoomedYet && current.zoom < 17.0) 18.0 else current.zoom
        zoomedYet = true
        cameraState.animateTo(
            current.copy(
                target = Position(longitude = loc.position.longitude, latitude = loc.position.latitude),
                zoom = zoom,
            ),
            duration = 600.milliseconds,
        )
    }

    // User pan drops follow mode (programmatic follow animations report PROGRAMMATIC, not GESTURE).
    LaunchedEffect(Unit) {
        snapshotFlow { cameraState.moveReason }
            .distinctUntilChanged()
            .collect { reason ->
                if (reason == CameraMoveReason.GESTURE && viewModel.isFollowing.value) {
                    viewModel.setFollowing(false)
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        Map(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            downloadedTiles = downloadedTiles,
            geometryMarkers = markers,
            focusedGeometry = focusedGeometry,
            pins = pins,
            location = location,
            rotation = null, // heading arrow arrives in M3b.3b
        )
        IconButton(
            onClick = onClickBack,
            modifier = Modifier.safeDrawingPadding().padding(8.dp),
        ) { BackIcon() }
        LocationStateButton(
            onClick = viewModel::onClickLocationButton,
            state = locationState,
            isFollowing = isFollowing,
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(16.dp),
        )
    }
}
```

- [ ] **Step 3: Link gate**

Run the link gate. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Simulator verification (the real M3b.3a gate)**

1. Build + install + launch via the Simulator Smoke sequence (use the `startDestination = MainMenuDestination.Map` trick so the map is on screen; revert before committing).
2. Set a location: `xcrun simctl location booted set 52.516,13.378` (or Simulator GUI). 
3. In the booted simulator, tap the GPS button (bottom-right). Expect the iOS permission dialog → **Allow While Using App**. After granting: the camera recenters/zooms to the pin and a **blue dot + accuracy circle** appears; the button tint turns to the secondary colour (following).
4. Screenshot `/tmp/m3b3a-dot.png`.
5. Pan the map by dragging → the button tint returns to normal (follow dropped).
6. Log grep clean of `EXC_`/exceptions/`CoreLocation` errors.

(If taps are awkward to script, the permission dialog can be pre-granted with `xcrun simctl privacy booted grant location-always de.westnordost.streetcomplete.ios`, then a fix set via `simctl location` will surface the dot on next follow.)

- [ ] **Step 5: Commit**
```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/Map.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt
git commit -m "M3b.3a: location dot + GPS button + follow/recenter on the iOS map"
```

---

## Task 4: Compass + heading arrow + navigation mode (M3b.3b complete)

Adds the `Compass` provider + its iOS impl, feeds heading into the VM, computes the camera-relative arrow rotation, and implements navigation mode (tilt + course-based map rotation) as the button's third tap.

**Files:**
- Create: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/Compass.kt`
- Create: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosCompass.kt`
- Modify: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationModule.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt`

**Interfaces:**
- Consumes: `Compass` (this task); `MapViewModel.rotation` / `isNavigationMode` / `setNavigationMode()` (this task); `Location.bearing` (Task 1); `CameraPosition.bearing` (maplibre-compose).
- Produces: `interface Compass { val rotation: StateFlow<Float?>; fun start(); fun stop() }`; `class IosCompass : Compass`; `MapViewModel.rotation: StateFlow<Float?>`, `MapViewModel.isNavigationMode: StateFlow<Boolean>`, `MapViewModel.setNavigationMode(Boolean)`.

- [ ] **Step 1: Create the `Compass` interface**

`app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/Compass.kt`:
```kotlin
package de.westnordost.streetcomplete.data.location

import kotlinx.coroutines.flow.StateFlow

/** Thin provider of device heading. Mirrors androidMain screens/main/map/Compass.
 *  [rotation] = degrees clockwise from true north, magnetic declination already applied;
 *  null when no heading is available. */
interface Compass {
    val rotation: StateFlow<Float?>
    fun start()
    fun stop()
}
```

- [ ] **Step 2: Create `IosCompass`**

`app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosCompass.kt`:
```kotlin
package de.westnordost.streetcomplete.util.location

import de.westnordost.streetcomplete.data.location.Compass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.darwin.NSObject

/** iOS [Compass] backed by CLLocationManager heading updates. trueHeading already has magnetic
 *  declination applied by iOS (unlike Android, which applies GeomagneticField manually). */
class IosCompass : Compass {
    private val _rotation = MutableStateFlow<Float?>(null)
    override val rotation: StateFlow<Float?> = _rotation.asStateFlow()

    private val manager = CLLocationManager()
    private val delegate = Delegate()

    init {
        manager.delegate = delegate
        manager.headingFilter = 1.0 // emit on ≥1° change
    }

    override fun start() { manager.startUpdatingHeading() }
    override fun stop() { manager.stopUpdatingHeading() }

    private inner class Delegate : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
            val heading = if (didUpdateHeading.trueHeading >= 0.0) didUpdateHeading.trueHeading
                          else didUpdateHeading.magneticHeading
            _rotation.value = heading.toFloat()
        }
    }
}
```
(If the K/N compiler asks for `BetaInteropApi` on `Delegate`, add `@OptIn(kotlinx.cinterop.BetaInteropApi::class)`.)

- [ ] **Step 3: Bind `Compass` in `iosLocationModule`**

Edit `app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationModule.kt`:
```kotlin
package de.westnordost.streetcomplete.util.location

import de.westnordost.streetcomplete.data.location.Compass
import de.westnordost.streetcomplete.data.location.LocationSource
import org.koin.dsl.module

val iosLocationModule = module {
    single<LocationSource> { IosLocationSource() }
    single<Compass> { IosCompass() }
}
```

- [ ] **Step 4: Inject `Compass` + add navigation mode to `MapViewModel`**

Edit `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt`:
- add imports:
```kotlin
import de.westnordost.streetcomplete.data.location.Compass
```
- add `compass` as the **third** constructor param (before `prefs`):
```kotlin
class MapViewModel(
    private val downloadedTilesSource: DownloadedTilesSource,
    private val locationSource: LocationSource,
    private val compass: Compass,
    private val prefs: Preferences,
) : ViewModel() {
```
- expose rotation + navigation mode (add next to the `location`/`isFollowing` declarations):
```kotlin
    val rotation: StateFlow<Float?> = compass.rotation

    private val _isNavigationMode = MutableStateFlow(prefs.mapIsNavigationMode)
    val isNavigationMode: StateFlow<Boolean> = _isNavigationMode.asStateFlow()
```
- in the `init { ... hasPermission.collect ... }` block, start the compass alongside the location source:
```kotlin
                if (granted && !_trackingRequested.value) {
                    locationSource.start()
                    compass.start()
                    _trackingRequested.value = true
                    if (pendingFollowOnGrant) { setFollowing(true); pendingFollowOnGrant = false }
                }
```
- replace `onClickLocationButton()`'s `else` branch with the full Android three-way logic:
```kotlin
    fun onClickLocationButton() {
        when {
            !locationState.value.isEnabled -> {
                pendingFollowOnGrant = true
                locationSource.requestPermission()
            }
            !isFollowing.value -> setFollowing(true)
            else -> setNavigationMode(!_isNavigationMode.value)
        }
    }
```
- add the navigation-mode setter (next to `setFollowing`):
```kotlin
    fun setNavigationMode(value: Boolean) {
        _isNavigationMode.value = value
        prefs.mapIsNavigationMode = value
    }
```
- stop the compass in `onCleared()`:
```kotlin
    override fun onCleared() {
        downloadedTilesSource.removeListener(downloadedTilesListener)
        locationSource.stop()
        compass.stop()
    }
```

- [ ] **Step 5: Update `mapModule` for the extra `Compass` arg**

Edit `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt`:
```kotlin
val mapModule = module {
    viewModel { MapViewModel(get(), get(), get(), get()) }
}
```

- [ ] **Step 6: Feed heading + navigation mode into `MapScreen`**

Edit `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt`:
- collect the two new flows (next to the other `collectAsState()` calls):
```kotlin
    val rotation by viewModel.rotation.collectAsState()
    val isNavigationMode by viewModel.isNavigationMode.collectAsState()
```
- replace the follow `LaunchedEffect` with the navigation-aware version (also keyed on `isNavigationMode`):
```kotlin
    var zoomedYet by remember { mutableStateOf(false) }
    LaunchedEffect(isFollowing, isNavigationMode, location) {
        if (!isFollowing) { zoomedYet = false; return@LaunchedEffect }
        val loc = location ?: return@LaunchedEffect
        val current = cameraState.position
        val zoom = if (!zoomedYet && current.zoom < 17.0) 18.0 else current.zoom
        zoomedYet = true
        cameraState.animateTo(
            current.copy(
                target = Position(longitude = loc.position.longitude, latitude = loc.position.latitude),
                zoom = zoom,
                // navigation mode: rotate the map to the GPS course + tilt; normal mode keeps bearing, no tilt.
                // Exiting navigation mode keeps the last bearing (matches Android) and drops the tilt.
                bearing = if (isNavigationMode) (loc.bearing?.toDouble() ?: current.bearing) else current.bearing,
                tilt = if (isNavigationMode) 60.0 else 0.0,
            ),
            duration = 600.milliseconds,
        )
    }
```
- replace the `Map(..., rotation = null)` argument with the camera-relative arrow rotation:
```kotlin
            location = location,
            rotation = rotation?.let { (it - cameraState.position.bearing.toFloat()) },
```
- pass `isNavigationMode` to the button:
```kotlin
        LocationStateButton(
            onClick = viewModel::onClickLocationButton,
            state = locationState,
            isFollowing = isFollowing,
            isNavigationMode = isNavigationMode,
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(16.dp),
        )
```

- [ ] **Step 7: Link gate**

Run the link gate. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Simulator regression check**

Run the Simulator Smoke sequence (map start-destination trick), set a location, grant permission, confirm the **dot + follow** still work (the simulator has no live compass, so the arrow won't move — that's expected; verify no crash and no `CoreLocation` errors). Screenshot `/tmp/m3b3b-sim.png`. Revert the start-destination.

- [ ] **Step 9: Commit**
```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/data/location/Compass.kt \
        app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosCompass.kt \
        app/src/iosMain/kotlin/de/westnordost/streetcomplete/util/location/IosLocationModule.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt
git commit -m "M3b.3b: compass heading arrow + navigation mode (tilt + course rotation) on the iOS map"
```

---

## Task 5: Device verification + documentation

The compass arrow + navigation mode can only be verified on real hardware. Deploy to the iPhone, verify the full flow, and record the milestone.

**Files:**
- Modify: `docs/superpowers/ios-port-backlog.md`
- Modify: `docs/superpowers/plans/2026-06-14-ios-port-m3b-map-and-location.md` (mark Task 3 done)
- Modify: memory `ios-port-project.md` + `MEMORY.md` (done by the orchestrator, not a subagent)

- [ ] **Step 1: Build + install on the device**

Use the backlog cheat-sheet (`docs/superpowers/ios-port-backlog.md`, M2/M3b sections). Key flags:
```
rm -rf build/ios-device
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphoneos \
  -destination 'platform=iOS,id=00008140-000228491A33001C' -derivedDataPath build/ios-device \
  -allowProvisioningUpdates build
APP=$(find build/ios-device/Build/Products/Debug-iphoneos -maxdepth 1 -name 'StreetComplete.app')
xcrun devicectl device install app --device 83B473E9-A697-567F-BDF4-27B28D49D0B5 "$APP"
xcrun devicectl device process launch --device 83B473E9-A697-567F-BDF4-27B28D49D0B5 de.westnordost.streetcomplete.ios
```
(Profile expires 2026-06-21; this redeploy also renews it.)

- [ ] **Step 2: On-device verification checklist (hand to the user)**

Verify on the iPhone 16 Pro and report:
1. Open the Map; tap the GPS button → iOS permission prompt → Allow While Using.
2. Blue dot + accuracy circle appears at the real location; map recenters + zooms in (follow on, button highlighted).
3. The **heading arrow** points the right way and rotates as you physically turn the phone.
4. Tap again → **navigation mode**: map tilts (~60°) and rotates to your direction of travel; arrow stays roughly forward. Tap again → tilt removed, bearing kept.
5. Pan the map → follow turns off (button un-highlights).
6. Background/foreground the app → no crash; dot resumes.

- [ ] **Step 3: Update the backlog**

Add an "M3b.3 ERLEDIGT" section to `docs/superpowers/ios-port-backlog.md` summarising: the two thin provider interfaces + iOS CLLocationManager impls, the VM state machine, the `Location.bearing` addition, nav-mode-from-course (no track recording), `ALLOWED`→`DENIED` collapse, and the device verification result. Mark Task 3 done in the M3b plan.

- [ ] **Step 4: Commit docs**
```bash
git add docs/superpowers/ios-port-backlog.md docs/superpowers/plans/2026-06-14-ios-port-m3b-map-and-location.md
git commit -m "Document M3b.3 completion (location dot + heading + GPS button) on iOS"
```

- [ ] **Step 5: Update memory** (orchestrator)

Append an "M3b.3 COMPLETE" paragraph to `ios-port-project.md` and refresh its `MEMORY.md` hook. Note any device-verification caveats the user reports.

---

## Self-review notes

- **Spec coverage:** `LocationSource`+`IosLocationSource` → Task 1; `Location.bearing` → Task 1 Step 2; Info.plist → Task 1 Step 6; Koin → Task 1 Steps 4–5 (+ Task 4 Step 3 for Compass); VM state machine/`LocationState`/follow → Task 2; dot + button + follow + first-fix-zoom + pan-to-unfollow → Task 3; `Compass`/`IosCompass`/arrow rotation/navigation mode → Task 4; device verify + docs → Task 5. The spec's two sub-increments map to: M3b.3a = Tasks 1–3, M3b.3b = Task 4.
- **`ALLOWED`→`DENIED` collapse**, **nav bearing from `CLLocation.course`**, **two `CLLocationManager` instances** — all implemented as designed (Task 1/4 code + comments).
- **Type consistency:** `MapViewModel` ctor evolves `(DownloadedTilesSource)` → `(…, LocationSource, Preferences)` (Task 2) → `(…, LocationSource, Compass, Preferences)` (Task 4); `mapModule` `get()` count updated in lockstep (Task 2 Step 2 → 3 args, Task 4 Step 5 → 4 args). `Map(...)` gains `location`/`rotation` (Task 3) consumed by `MapScreen` (Task 3, refined Task 4). `setFollowing`/`setNavigationMode`/`onClickLocationButton`/`isFollowing`/`isNavigationMode`/`location`/`rotation`/`locationState` names are consistent across Tasks 2–4.
- **No placeholders:** every code step shows complete code; verification steps give exact commands + expected output via the Verification model.
- **Known empirical points (each with a fallback):** K/N delegate may need `@OptIn(BetaInteropApi::class)` (Global Constraints + noted at the delegate); `simctl location` may be unavailable → Simulator GUI fallback; the compass arrow is device-only to verify (stated in Tasks 4–5).
```
