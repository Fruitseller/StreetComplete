# iOS-Port M3b: Map (maplibre-compose) + Location — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render a real, interactive vector map on iOS via `maplibre-compose`, reachable from the menu, then bring it toward parity (pins/overlay/geometry layers ported from the upstream reference) and add a live location dot.

**Architecture:** A fresh commonMain map layer built on `org.maplibre.compose:maplibre-compose:0.13.0`. The native `MapLibre.framework` is supplied to the iOS targets via the `spmForKmp` Gradle plugin (Swift Package Manager), not from Maven. The upstream `upstream/maplibre-compose` branch (PR #6352) is used strictly as a code reference for porting the style + layers — it is NOT integrated. Location/compass get a new commonMain interface with an iosMain `CLLocationManager`/`CMMotionManager` actual.

**Tech Stack:** maplibre-compose 0.13.0, native MapLibre (maplibre-gl-native-distribution) 6.25.1, spmForKmp Gradle plugin 1.9.1, Compose Multiplatform 1.10.3, Kotlin 2.3.20. iOS bundle id `de.westnordost.streetcomplete.ios`.

---

## Context & decisions (read first)

Follows **M3a** (committed + pushed on `ios-port`): the app has a shared commonMain `MainMenuNavHost` reaching About / Settings / User-Login, with the full commonMain Koin graph on iOS. Background: `docs/superpowers/ios-port-backlog.md` (M3a + M3b-Spike sections), and the M3a plan's "Bridge to M3b".

This plan is grounded in the **M3b spike (2026-06-14, verdict GO)** — the spike actually built and ran `MaplibreMap` on the iOS simulator and produced the exact integration recipe below. The recipe steps in Task 1 are reconstructions of the spike's *verified* diff, so they are known-good, not speculative.

User decisions (M3 kickoff): **M3b includes the location dot.** Map style first online-only (JawgMaps); offline tiles deferred.

Spike-verified facts this plan relies on:
- `maplibre-compose:0.13.0` resolves + links + renders on iOS sim (Metal, no crash). Its POM pins Compose 1.10.3 / lifecycle 2.10.0 — matches this fork.
- iOS artifacts exist only for `iosArm64` + `iosSimulatorArm64` (NO `iosX64` → must drop that target).
- Native `MapLibre.framework` comes via SPM (`spmForKmp` 1.9.1 + native `6.25.1`), embedded into the `.app` automatically once `FRAMEWORK_SEARCH_PATHS` points at the SPM build dir.
- `koin-androidx-compose-navigation` (currently commonMain) is an Android-only AAR and breaks the strict iOS cinterop config → move it to androidMain (no commonMain code uses its API).
- API 0.13.0: `MaplibreMap(modifier, baseStyle: BaseStyle = BaseStyle.Demo, cameraState = rememberCameraState(), styleState = rememberStyleState(), onMapClick, …, content)`; `BaseStyle.Uri(String)` / `.Json(...)` / `.Demo` / `.Empty`; `rememberCameraState(firstPosition: CameraPosition)`; `CameraPosition(bearing, target: Position, tilt, zoom, padding)`; `Position(longitude=, latitude=)` (GeoJSON lon/lat order).

## Verification model (every task)

Same as M3a — no unit-test gate (Android APK / iOS commonTest are upstream-WIP-red, not gates). Per-task gates:
1. **Link gate:** `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64` → `BUILD SUCCESSFUL`. (Task 1 step 1 also runs the SPM/native download — first run is slow.)
2. **Simulator build + run + screenshot** (stale-relink gotcha → `rm -rf build/ios` first):
   ```
   rm -rf build/ios
   xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
     -destination 'platform=iOS Simulator,id=5B7C16A4-2C11-4E0C-9549-42F7AC648438' -derivedDataPath build/ios build
   xcrun simctl boot 5B7C16A4-2C11-4E0C-9549-42F7AC648438 2>/dev/null || true
   APP=$(find build/ios/Build/Products/Debug-iphonesimulator -maxdepth 1 -name 'StreetComplete.app')
   xcrun simctl install booted "$APP"; xcrun simctl launch booted de.westnordost.streetcomplete.ios; sleep 8
   xcrun simctl io booted screenshot /tmp/<shot>.png
   xcrun simctl spawn booted log show --last 120s --predicate 'process == "StreetComplete"' 2>/dev/null | grep -iE "exception|crash|Throwable|Fatal|EXC_|NoBeanDef|MapLibre|Metal" | head -40
   ```
3. **Device** (needs the physical iPhone; mandatory once per increment, but may be batched — see note): the device build uses a different SPM variant path (`arm64-apple-ios`), so `FRAMEWORK_SEARCH_PATHS` must also include the device path. Commands as in the backlog cheat-sheet with `-destination 'platform=iOS,id=00008140-000228491A33001C' -derivedDataPath build/ios-device -allowProvisioningUpdates`.
4. **Commit** at the end of each task.

Constraints carried forward: `rm -rf build/ios` before each xcodebuild; no `NSLog %@` from K/N; signing TEAM_ID `2DPUG448BC` (profile expires 2026-06-21); no merge commits.

> **Device-deploy note:** Task 1 changes the build system and the Xcode project; the subagent verifies on the **simulator**. The **device** build (which also exercises the `arm64-apple-ios` SPM framework path) is a manual checkpoint requiring the iPhone — fold it into the M3b wrap-up or do it when the device is available.

---

## Task 1 — M3b.1: Integrate maplibre-compose + first visible map behind a menu entry

Reconstruct the spike's verified integration, then render a real (tiled) map reachable from the menu.

**Files:**
- Modify: `app/build.gradle.kts` (plugin, imports, iOS targets, deps)
- Modify: `.gitignore`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (FRAMEWORK_SEARCH_PATHS)
- Create: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/MainMenuNavHost.kt`

- [ ] **Step 1: Add the spmForKmp plugin + imports to `app/build.gradle.kts`**

At the top of the file, with the other imports (after `import java.util.Properties`), add:
```kotlin
import io.github.frankois944.spmForKmp.swiftPackageConfig
import java.net.URI
```
In the `plugins { ... }` block, add as the last entry:
```kotlin
    id("io.github.frankois944.spmForKmp") version "1.9.1"
```

- [ ] **Step 2: Drop `iosX64`, wire SPM + linker for the iOS targets**

Replace the iOS targets block:
```kotlin
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "StreetComplete"
            isStatic = true
        }
    }
```
with (note: `iosX64()` removed — maplibre-compose 0.13.0 publishes no iosX64 artifact):
```kotlin
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "StreetComplete"
            isStatic = true
        }
        // Native MapLibre.framework is NOT in the maven artifact (that ships only the cinterop klib);
        // it is supplied via Swift Package Manager. cinteropName "maplibreNative" determines the
        // build output dir (NOT the target name).
        iosTarget.swiftPackageConfig(cinteropName = "maplibreNative") {
            dependency {
                remotePackageVersion(
                    url = URI("https://github.com/maplibre/maplibre-gl-native-distribution.git"),
                    products = { add("MapLibre", exportToKotlin = true) },
                    packageName = "maplibre-gl-native-distribution",
                    version = "6.25.1",
                )
            }
        }
        val variant = when (iosTarget.targetName) {
            "iosArm64" -> "arm64-apple-ios"
            "iosSimulatorArm64" -> "arm64-apple-ios-simulator"
            else -> error("Unrecognized iOS target: ${iosTarget.targetName}")
        }
        val rpath = "${layout.buildDirectory.get()}/spmKmpPlugin/maplibreNative/scratch/$variant/release/"
        iosTarget.binaries.all { linkerOpts("-F$rpath", "-rpath", rpath) }
    }
```

- [ ] **Step 3: Add the maplibre-compose dependency; move the Android-only koin nav dep**

In the `commonMain { dependencies { ... } }` block, REMOVE the line:
```kotlin
                implementation("io.insert-koin:koin-androidx-compose-navigation")
```
and add, near the other UI deps (after the `navigation-compose` line):
```kotlin
                // Multiplatform map
                implementation("org.maplibre.compose:maplibre-compose:0.13.0")
```
In the `androidMain { dependencies { ... } }` block, add (with the other koin android deps):
```kotlin
                implementation("io.insert-koin:koin-androidx-compose-navigation")
```

- [ ] **Step 4: gitignore the SPM-generated Swift bridge**

The plugin generates `app/src/swift/maplibreNative/StartYourBridgeHere.swift` during the build. Add to `.gitignore`:
```
# spmForKmp-generated Swift bridge (regenerated each build)
/app/src/swift/
```

- [ ] **Step 5: Run the link gate (this triggers the first SPM/native download — slow)**

Run: `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`. (Downloads `maplibre-gl-native-distribution` via SPM and builds it for the simulator variant. If it fails on SPM resolution, re-run once — SPM checkouts can be flaky on first fetch. If a `cinterop` task complains about `iosX64`, confirm Step 2 removed `iosX64()`.)

- [ ] **Step 6: Add `FRAMEWORK_SEARCH_PATHS` to the Xcode project**

Open `iosApp/iosApp.xcodeproj/project.pbxproj`. Find the `XCBuildConfiguration` blocks for the app target (the ones that already set `FRAMEWORK_SEARCH_PATHS` for the StreetComplete framework — there are Debug and Release blocks). In each, add to the `FRAMEWORK_SEARCH_PATHS` array the SPM framework path. For a simulator-capable Debug build add the simulator variant; for device builds add the device variant. Add BOTH so either SDK links (Xcode ignores a non-existent path):
```
"$(SRCROOT)/../app/build/spmKmpPlugin/maplibreNative/scratch/arm64-apple-ios-simulator/release",
"$(SRCROOT)/../app/build/spmKmpPlugin/maplibreNative/scratch/arm64-apple-ios/release",
```
(If `FRAMEWORK_SEARCH_PATHS` is currently a single string, convert it to a list including the existing value and these two. Preserve `$(inherited)` if present.) Embedding into the `.app` happens automatically via the existing `@executable_path/Frameworks` rpath — no new Embed-Frameworks phase needed.

- [ ] **Step 7: Create the map screen**

Create `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt`:
```kotlin
package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** First iOS map (M3b.1): a real vector map reachable from the menu. Online-only for now
 *  (offline tiles deferred); style + camera are minimal — pins/overlays/location come in later
 *  M3b increments. */
@Composable
fun MapScreen() {
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = 13.4, latitude = 52.5), // Berlin
            zoom = 12.0,
        )
    )
    MaplibreMap(
        modifier = Modifier.fillMaxSize(),
        baseStyle = BaseStyle.Uri(JAWG_STREETS_STYLE_URL),
        cameraState = cameraState,
    )
}

// JawgMaps hosted vector style (same provider/token the Android build + PR #6352 use).
// TODO M3b: externalize this token out of source.
private const val JAWG_STREETS_STYLE_URL =
    "https://api.jawg.io/styles/jawg-streets.json?access-token=" +
        "mL9X4SwxfsAGfojvGiion9hPKuGLKxPbogLyMbtakA2gJ3X88gcVlTSQ7OD6OfbZ"
```
> If `BaseStyle.Uri` with the JawgMaps URL does not paint tiles at runtime, fall back to porting the inline style from the reference (`git show upstream/maplibre-compose:app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/MapStyle.kt`) as `BaseStyle.Json(...)`. Confirm the exact import paths (`org.maplibre.compose.camera`, `.map`, `.style`, `org.maplibre.spatialk.geojson.Position`) against the resolved 0.13.0 artifact — the spike verified these, but if any differ, use the resolved ones and note it.

- [ ] **Step 8: Add a Map entry to the menu**

In `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/MainMenuNavHost.kt`:
- add `import de.westnordost.streetcomplete.screens.main.map.MapScreen`
- add a `Map` constant to `MainMenuDestination` (`const val Map = "map"`)
- add a `composable(MainMenuDestination.Map) { MapScreen() }` route
- add an `onClickMap` param to `MainMenuScreen` and a first `MenuRow` for it. There is a `Res.string` for the map/quests action — use `Res.string.action_open_location_in_other_app`? No: prefer a simple label. Check for an existing suitable string with `grep -rn "\"Map\"\|map_btn\|action_.*map" app/src/commonMain/composeResources/values/strings.xml`; if none fits, use the literal `"Map"` for this increment (it is the first, throwaway-ish menu entry; the map becomes the real main screen in a later increment). Wire `onClickMap = { navController.navigate(MainMenuDestination.Map) }`.

- [ ] **Step 9: Link gate + simulator verification**

Run the link gate, then the simulator sequence (Verification model §2), screenshot `/tmp/m3b1-map.png`. Tap is not automatable, so to verify the MAP renders, TEMPORARILY set `MainMenuNavHost`'s `startDestination = MainMenuDestination.Map` for the screenshot, confirm a tiled map renders with no crash in the log, then revert `startDestination` back to `MainMenuDestination.Menu` before committing.
Expected: a street map (tiles visible) centered on Berlin; log clean of `EXC_`/Kotlin exceptions (a benign `automaticallyAdjustsScrollViewInsets` deprecation warning is fine).

- [ ] **Step 10: Commit**

```bash
git add app/build.gradle.kts .gitignore iosApp/iosApp.xcodeproj/project.pbxproj \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/MainMenuNavHost.kt
git commit -m "M3b.1: integrate maplibre-compose; first visible iOS map behind a menu entry"
```

---

## Task 2 — M3b.2: Port the map style + layers from the #6352 reference (OUTLINE — detail after Task 1)

To be turned into bite-sized steps once Task 1 lands and the 0.13.0 API is confirmed in-tree. Scope (from the M3.0 investigation + spike):
- Reference-copy from `upstream/maplibre-compose` (read-only `git show`, do not integrate): `map2/MapStyle.kt` (JawgMaps vector style port), `MapColors.kt`, `ExpressionUtils.kt`, `GeometryUtils.kt`, and the glyph `.pbf` set + `map_*` drawables already under `commonMain/composeResources`. Externalize the JawgMaps token.
- Port the stateless layer composables (`CurrentLocationLayers`, `DownloadedAreaLayer`, `FocusedGeometryLayers`, `GeometryMarkersLayers`, `PinsLayers`, `SelectedPinsLayer`, `StyleableOverlayLayers`, `TracksLayer`) into commonMain, finishing the icon/animation TODOs and removing the stray `org.maplibre.android` import. Verify the `org.maplibre.compose.{layers,sources,expressions}` package APIs against 0.13.0.
- Write a real `Map()` taking a ViewModel/state (use #6352's `Map.kt` only for layer ordering — its body references ~12 undefined symbols). Persist camera via `Preferences.mapPosition/Zoom/Rotation/Tilt`.
- Wire quest pins / overlays from the existing commonMain data layer (design fresh commonMain ViewModels; #6352 has no manager layer).

## Task 3 — M3b.3: Location dot (OUTLINE — detail after Task 2)

- Add `NSLocationWhenInUseUsageDescription` to `iosApp/iosApp/Info.plist`.
- Introduce a commonMain `LocationManager` interface (none exists today): expose `Flow<Location>` using the existing commonMain `Location(position, accuracy, elapsedDuration)`; Android actual wraps the existing `FineLocationManager`, iOS actual wraps `CLLocationManager` (mirror `android.location.Location.toLocation()`; use a monotonic clock for `elapsedDuration` to match `RecentLocations`/`SurveyChecker`).
- Drive the existing commonMain `LocationState` enum + `LocationStateButton`; render the dot via the ported `CurrentLocationLayers` (renders fine with `rotation == null`).
- Heading/compass (`CLHeading`/`CMMotionManager`) is a further sub-step — `CurrentLocationLayers` works without it.

## Task 4 — M3b wrap-up (OUTLINE)

Update backlog + memory; device deploy + on-device map/location verification; record offline-tiles as still deferred (#6072).

---

## Self-review notes
- **Spec coverage:** integration recipe (spike-verified) → Task 1; layer/style port → Task 2; location dot → Task 3; device verification + docs → Task 4. Offline tiles explicitly deferred.
- **Known empirical points in Task 1 (not placeholders — each has a concrete fallback):** Step 7 style URL (fall back to the inline `BaseStyle.Json` port), Step 7 import paths (confirm against resolved 0.13.0), Step 8 menu label string. The build/SPM/Xcode recipe (Steps 1–6) is a reconstruction of the spike's *verified* working diff.
- **Type consistency:** `MaplibreMap`/`BaseStyle`/`rememberCameraState`/`CameraPosition`/`Position` signatures as verified by the spike against the 0.13.0 sources jar.
