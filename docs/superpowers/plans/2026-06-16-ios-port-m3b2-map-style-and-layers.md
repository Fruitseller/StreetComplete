# iOS-Port M3b.2: Map Style + Layers (port #6352 `map2` into commonMain) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder hosted-JawgMaps map on iOS with StreetComplete's own programmatic vector style, persist the camera, and port the full declarative layer stack (pins / overlay / geometry / location / tracks / downloaded-area) from the `upstream/maplibre-compose` (#6352) reference into commonMain, driven by a fresh commonMain `MapViewModel`.

**Architecture:** Port the reference's `screens/main/map2/` tree **verbatim into commonMain** under the same package `de.westnordost.streetcomplete.screens.main.map2` (keeps all internal cross-imports valid; aligns with the likely upstream destination). The reference is already written against the `org.maplibre.compose.*` API that matches the resolved `0.13.0`; the only systematic edit is the GeoJSON package. `MapScreen` (in `screens/main/map`) hosts the new `Map()` composable. A new commonMain `MapViewModel` (Koin) owns camera persistence and the layer data `StateFlow`s.

**Tech Stack:** maplibre-compose 0.13.0 (`org.maplibre.compose.*`), spatialk geojson 0.7.0 (`org.maplibre.spatialk.geojson.*`), Compose Multiplatform 1.10.3, Kotlin 2.3.20. Reference branch: `upstream/maplibre-compose` (read-only via `git show` / `git checkout … -- path`).

---

## Context & decisions (read first)

This expands **Task 2 (M3b.2)** of `docs/superpowers/plans/2026-06-14-ios-port-m3b-map-and-location.md`, which was intentionally left as an outline until M3b.1 landed and the 0.13.0 API was confirmed in-tree. M3b.1 is done + device-verified (a real JawgMaps map renders on the iPhone). This plan was written from a 4-way investigation; its two supporting reference documents are committed in the repo and MUST be consulted by implementers:

- **`docs/superpowers/references/maplibre-compose-0.13.0-api.md`** — the resolved 0.13.0 public API (every layer/source/expression signature, extracted from the klib). The source of truth for reconciling any DSL/signature mismatch the link gate surfaces.
- **`docs/superpowers/references/m3b2-reference-map2-porting-catalog.md`** — a per-file catalog of the reference `map2` code (signatures, imports, TODOs, hazards).

### Reconciliation rules (apply to EVERY ported file)

The reference `map2` files already import `org.maplibre.compose.*` (matches 0.13.0). Apply these edits when porting any file:

1. **GeoJSON package rename (systematic):** replace `io.github.dellisd.spatialk.geojson` → `org.maplibre.spatialk.geojson` in all imports and in the `typealias GeoJsonBoundingBox` in `GeometryUtils.kt`. Type names (`Position`, `Point`, `LineString`, `Polygon`, `MultiLineString`, `MultiPolygon`, `Feature`, `FeatureCollection`, `BoundingBox`, `Geometry`) are unchanged.
2. **`Feature`/`FeatureCollection` construction:** spatialk 0.7.0 types are generic — the common form is `Feature<Geometry, JsonObject?>`. Where the reference builds a feature with a `Map<String, JsonElement>`/`mapOf(...)` of properties, wrap it: `Feature(geometry = …, properties = JsonObject(mapOf(...)))` (import `kotlinx.serialization.json.JsonObject`). `FeatureCollection(featuresList)` and `FeatureCollection(vararg)` both exist. Resolve exact generics against the link gate + the API reference doc.
3. **`PinsLayers.kt`:** delete the line `import org.maplibre.android.style.expressions.Expression.log2` (Android-only, redundant; the correct `org.maplibre.compose.expressions.dsl.log2` is already imported).
4. **`@JvmName` (ExpressionUtils `byZoom` overloads):** keep them — `kotlin.jvm.JvmName` is a no-op on native and compiles in commonMain. If the link gate rejects it, delete the three `@JvmName(...)` annotations (the overloads differ by `Pair` type-argument and do not clash on native).
5. **Enum constants are PascalCase `const(EnumValue)`** (e.g. `const(LineCap.Round)`), feature access is `feature["key"]` / `feature.get("key")`, comparisons are infix (`a eq b`, `a gt b`). The reference already uses this form; only fix residuals the compiler flags, using the API reference doc.

### Verification model (every task)

Same as M3b.1 — no unit-test gate (Android APK / iOS commonTest are upstream-WIP-red, NOT our gates). Per-task gates:

1. **Link gate:** `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64` → `BUILD SUCCESSFUL`.
2. **Simulator build + run + screenshot** (`rm -rf build/ios` first — stale-relink gotcha):
   ```bash
   rm -rf build/ios
   xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
     -destination 'platform=iOS Simulator,id=5B7C16A4-2C11-4E0C-9549-42F7AC648438' -derivedDataPath build/ios build
   xcrun simctl boot 5B7C16A4-2C11-4E0C-9549-42F7AC648438 2>/dev/null || true
   APP=$(find build/ios/Build/Products/Debug-iphonesimulator -maxdepth 1 -name 'StreetComplete.app')
   xcrun simctl install booted "$APP"; xcrun simctl launch booted de.westnordost.streetcomplete.ios; sleep 8
   xcrun simctl io booted screenshot /tmp/<shot>.png
   xcrun simctl spawn booted log show --last 120s --predicate 'process == "StreetComplete"' 2>/dev/null | grep -iE "exception|crash|Throwable|Fatal|EXC_|NoBeanDef|MapLibre|Metal|NSURLError" | head -40
   ```
   To verify the MAP itself (the menu is the start destination), TEMPORARILY set `MainMenuNavHost`'s `startDestination = MainMenuDestination.Map` for the screenshot, then revert to `MainMenuDestination.Menu` before committing.
3. **Commit** at the end of each task (no merge commits; work on `master`).

**Data-availability caveat (important):** On iOS there is currently NO downloaded OSM data and NO location provider (DownloadController/LocationManager are no-op stubs until M3b.3+). So the data-driven layers (pins, overlay, focused-geometry, markers, downloaded-area, tracks, location) have **no real data to show yet**. This plan therefore verifies those layers with **synthetic data** injected temporarily into the `Map()`/VM for a screenshot, then reverted before commit (same technique as M3b.1's temporary `startDestination`). Real-data wiring lands when download/location exist; that is explicitly out of scope here and recorded as follow-up.

Constraints carried forward: `rm -rf build/ios` before each xcodebuild; no `NSLog %@` from K/N; signing TEAM_ID `2DPUG448BC` (profile expires 2026-06-21); device deploy is a manual batched checkpoint at the M3b.2 wrap-up (uses `arm64-apple-ios` SPM path + `-allowProvisioningUpdates`).

### File structure (created/modified across all tasks)

Ported verbatim+reconciled into `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/`:
- `MapColors.kt`, `ExpressionUtils.kt`, `MapStyle.kt`, `GeometryUtils.kt`, `Map.kt`
- `layers/CurrentLocationLayers.kt`, `DownloadedAreaLayer.kt`, `FocusedGeometryLayers.kt`, `GeometryMarkersLayers.kt`, `PinsLayers.kt`, `SelectedPinsLayer.kt`, `StyleableOverlayLayers.kt`, `StyledElement.kt`, `TracksLayer.kt`

New commonMain code:
- `screens/main/map/MapViewModel.kt` (new), `screens/main/map/MapModule.kt` (new Koin module)
- `data/osm/mapdata/CameraPosition` mapping helpers (inline in MapViewModel; no separate file needed)

Modified:
- `screens/main/map/MapScreen.kt` (host `Map()`, camera seed/persist, pass VM state)
- `screens/main/MainMenuNavHost.kt` (provide VM to MapScreen)
- `ui/theme/Color.kt` (+3 color extensions)
- `di/` Koin bootstrap (`InitKoin.kt` / module list) to register `mapModule`
- composeResources (asset port via `git checkout`)

---

## Task 1 — M3b.2a: StreetComplete's own programmatic vector style on iOS

Port the style backbone (colors + expression utils + style + glyphs + oneway drawable) and render it via a new `Map()` composable with empty content slots. Biggest visible win: the real SC map look replaces generic JawgMaps.

**Files:**
- Port assets (composeResources) via `git checkout`
- Create: `screens/main/map2/MapColors.kt`, `ExpressionUtils.kt`, `MapStyle.kt`, `Map.kt`
- Modify: `ui/theme/Color.kt`
- Modify: `screens/main/map/MapScreen.kt`

- [ ] **Step 1: Port the style assets from the reference branch**

```bash
cd /Users/piotr/git/StreetComplete
git checkout upstream/maplibre-compose -- \
  "app/src/commonMain/composeResources/drawable/map_oneway_arrow.xml" \
  "app/src/commonMain/composeResources/drawable/map_location_shadow.xml" \
  "app/src/commonMain/composeResources/drawable/map_location_view_direction.xml" \
  "app/src/commonMain/composeResources/drawable/map_pin.xml" \
  "app/src/commonMain/composeResources/drawable/map_pin_circle.xml" \
  "app/src/commonMain/composeResources/drawable-hdpi/map_location_nyan.png" \
  "app/src/commonMain/composeResources/drawable-mdpi/map_location_nyan.png" \
  "app/src/commonMain/composeResources/drawable-xhdpi/map_location_nyan.png" \
  "app/src/commonMain/composeResources/drawable-xxhdpi/map_location_nyan.png" \
  "app/src/commonMain/composeResources/drawable-hdpi/map_pin_shadow.png" \
  "app/src/commonMain/composeResources/drawable-mdpi/map_pin_shadow.png" \
  "app/src/commonMain/composeResources/drawable-xhdpi/map_pin_shadow.png" \
  "app/src/commonMain/composeResources/drawable-xxhdpi/map_pin_shadow.png" \
  "app/src/commonMain/composeResources/drawable-mdpi/map_track_nyan.png" \
  "app/src/commonMain/composeResources/drawable-mdpi/map_track_nyan_record.png"
# 512 glyph .pbf in two fontstacks (Roboto Regular + Roboto Bold):
git checkout upstream/maplibre-compose -- "app/src/commonMain/composeResources/files/glyphs"
```
Verify: `find app/src/commonMain/composeResources -iname 'map_*' | wc -l` → 15; `git ls-files --others --cached app/src/commonMain/composeResources/files/glyphs | grep -c '\.pbf'` → 512 (or `find … -name '*.pbf' | wc -l` → 512).

- [ ] **Step 2: Add the 3 map Color extensions to `ui/theme/Color.kt`**

Append to `app/src/commonMain/kotlin/de/westnordost/streetcomplete/ui/theme/Color.kt` (verbatim from the reference branch):
```kotlin
val Color.Companion.Location get() = Color(0xff536dfe)
val Color.Companion.GeometryMarker get() = Color(0xffD140D0)
val Color.Companion.Recording get() = Color(0xfffe1616)
```
(Ensure `androidx.compose.ui.graphics.Color` is imported — it already is in this file.)

- [ ] **Step 3: Port `MapColors.kt` (clean — no reconciliation)**

```bash
mkdir -p app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/layers
git show upstream/maplibre-compose:app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/MapColors.kt \
  > app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/MapColors.kt
```
This file imports only `androidx.compose.runtime.Immutable` + `androidx.compose.ui.graphics.Color` — fully multiplatform, no edits. Confirm the package line reads `package de.westnordost.streetcomplete.screens.main.map2`.

- [ ] **Step 4: Port `ExpressionUtils.kt` (apply reconciliation rule 4 if needed)**

```bash
git show upstream/maplibre-compose:app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/ExpressionUtils.kt \
  > app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/ExpressionUtils.kt
```
Imports are `org.maplibre.compose.expressions.*` only (no geojson). Leave `@JvmName` in place; if the link gate (Step 7) rejects it on native, delete the three `@JvmName(...)` annotations.

- [ ] **Step 5: Port `MapStyle.kt` (no geojson; large file, reconcile only what the compiler flags)**

```bash
git show upstream/maplibre-compose:app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/MapStyle.kt \
  > app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/MapStyle.kt
```
This depends on `MapColors`, `ExpressionUtils` helpers, `Res.drawable.map_oneway_arrow`, and `org.maplibre.compose.{expressions,layers,sources,util}`. No GeoJSON, no Android APIs. Any residual DSL signature mismatch → fix against `docs/superpowers/references/maplibre-compose-0.13.0-api.md` at the link gate. Keep the hardcoded JawgMaps token and the `// TODO localization of attribution` as-is (matches Android build; externalizing the token is a tracked follow-up, not this task).

- [ ] **Step 6: Create `Map.kt` (the host composable — fixes the reference's undefined symbols)**

The reference `Map.kt` does not compile (≈13 undefined symbols + a non-existent `TracksLayers()`). Create a MINIMAL version for this task: render the style with **empty content slots** (data layers are wired in later tasks). Create `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/Map.kt`:

```kotlin
package de.westnordost.streetcomplete.screens.main.map2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import de.westnordost.streetcomplete.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.StyleState
import org.maplibre.compose.style.rememberStyleState

@OptIn(ExperimentalResourceApi::class)
@Composable
fun Map(
    modifier: Modifier = Modifier,
    cameraState: CameraState = rememberCameraState(),
    styleState: StyleState = rememberStyleState(),
) {
    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Json(BASE_STYLE),
        zoomRange = 0f..22f,
        cameraState = cameraState,
        styleState = styleState,
        options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
    ) {
        val languages = listOf(Locale.current.language)
        val colors = if (isSystemInDarkTheme()) MapColors.Night else MapColors.Light
        MapStyle(colors = colors, languages = languages)
    }
}

// Minimal MapLibre style v8: empty sources/layers; supplies only the locally-bundled glyphs URL
// (the real layers are added programmatically by MapStyle()). Glyphs are compose resources read
// via Res.getUri and rewritten into MapLibre's {fontstack}/{range} template.
private val BASE_STYLE: String get() =
    """
    {
      "version": 8,
      "name": "Empty",
      "metadata": {},
      "sources": {},
      "glyphs": "${
        Res.getUri("files/glyphs/Roboto Regular/0-255.pbf")
            .replace("Roboto Regular", "{fontstack}")
            .replace("0-255", "{range}")
    }",
      "layers": []
    }
    """.trimIndent()
```
> If `OrnamentOptions` is not in `org.maplibre.compose.map`, check the API reference doc — it is in `org.maplibre.compose.map` per the klib dump (`MapOptions(renderOptions, gestureOptions, ornamentOptions)`, `OrnamentOptions.AllDisabled`). If `BASE_STYLE` as a `get()` causes issues with `Res.getUri` (a suspend/composable-context concern), move the glyph-URL construction into a `remember { }` inside `Map()` and pass the string down. Confirm `Res.getUri` is non-suspend in this version (it is used the same way in the reference's `Map.kt`).

- [ ] **Step 7: Link gate**

Run: `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`. Fix any DSL/signature residuals using the API reference doc. (composeResources codegen will pick up the new `map_*` drawables + glyphs automatically; if `Res.drawable.map_oneway_arrow` is unresolved, run a clean `:app:generateComposeResClass`-touching build or `./gradlew :app:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` once.)

- [ ] **Step 8: Switch `MapScreen` to the programmatic style**

Replace the body of `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt` so it calls the new `Map()` instead of the hosted JawgMaps `BaseStyle.Uri`. Keep the back button. New file:
```kotlin
package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.screens.main.map2.Map
import de.westnordost.streetcomplete.ui.common.BackIcon

/** iOS map screen (M3b.2a): StreetComplete's own programmatic vector style. */
@Composable
fun MapScreen(onClickBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Map(modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = onClickBack,
            modifier = Modifier.safeDrawingPadding().padding(8.dp),
        ) { BackIcon() }
    }
}
```
(The JawgMaps token now lives only in `MapStyle.kt`.)

- [ ] **Step 9: Simulator verification (temporary `startDestination = Map`)**

Per Verification model §2, set `MainMenuNavHost` `startDestination = MainMenuDestination.Map`, build+run+screenshot `/tmp/m3b2a-style.png`, then revert `startDestination` to `Menu`.
Expected: a StreetComplete-styled vector map (its own land/water/road-casing colors, not the generic JawgMaps palette) centered on Berlin (lon 13.4, lat 52.5 — but note `rememberCameraState()` default is (0,0)/zoom 0 here since `Map()` uses the default camera; for THIS screenshot temporarily pass `cameraState = rememberCameraState(CameraPosition(target = Position(13.4, 52.5), zoom = 12.0))` into `Map()` so the screenshot shows Berlin, then revert — camera seeding is Task 2). **Text labels must render** (proves the locally-bundled Roboto glyphs load — log must be clean of glyph `NSURLError`/404). No `EXC_`/Kotlin exceptions.

- [ ] **Step 10: Commit**

```bash
git add app/src/commonMain/composeResources app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2 \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/ui/theme/Color.kt
git commit -m "M3b.2a: render StreetComplete's own programmatic vector map style on iOS"
```

---

## Task 2 — M3b.2b: Camera persistence (seed from + save to Preferences)

Seed the map camera from `Preferences` on open and persist it when movement settles. Removes the hardcoded Berlin default.

**Files:**
- Modify: `screens/main/map/MapScreen.kt`
- Reference: `data/preferences/Preferences.kt` (`mapPosition: LatLon`, `mapZoom/mapRotation/mapTilt: Double`)

- [ ] **Step 1: Inject `Preferences` into `MapScreen` and seed the camera**

`Preferences` is a Koin `single`. Use `org.koin.compose.koinInject()` to get it in the composable. Build the initial `CameraPosition` from prefs and pass a seeded `cameraState` to `Map()`:
```kotlin
// in MapScreen.kt
import androidx.compose.runtime.remember
import de.westnordost.streetcomplete.data.preferences.Preferences
import org.koin.compose.koinInject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.spatialk.geojson.Position

@Composable
fun MapScreen(onClickBack: () -> Unit) {
    val prefs: Preferences = koinInject()
    val cameraState = rememberCameraState(
        firstPosition = remember {
            CameraPosition(
                target = Position(longitude = prefs.mapPosition.longitude, latitude = prefs.mapPosition.latitude),
                zoom = prefs.mapZoom,
                bearing = prefs.mapRotation,
                tilt = prefs.mapTilt,
            )
        }
    )
    // … Box { Map(modifier = …, cameraState = cameraState); back button }
}
```
> Note: a brand-new install has `mapPosition = LatLon(0.0, 0.0)`, `mapZoom = 0.0` (mid-ocean, zoomed out). That is acceptable behavior (matches Android's cold-start before first GPS). For a nicer first-run you MAY, if `prefs.mapZoom == 0.0`, fall back to a default `CameraPosition(target = Position(13.4, 52.5), zoom = 3.0)`; keep it minimal.

- [ ] **Step 2: Persist the camera when movement settles**

Add a `LaunchedEffect` that observes the camera via `snapshotFlow` and writes to prefs once movement stops:
```kotlin
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.snapshotFlow   // androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

LaunchedEffect(cameraState) {
    snapshotFlow { cameraState.isCameraMoving to cameraState.position }
        .filter { (moving, _) -> !moving }
        .map { (_, pos) -> pos }
        .distinctUntilChanged()
        .collect { pos ->
            prefs.mapPosition = LatLon(latitude = pos.target.latitude, longitude = pos.target.longitude)
            prefs.mapZoom = pos.zoom
            prefs.mapRotation = pos.bearing
            prefs.mapTilt = pos.tilt
        }
}
```
(Imports: `androidx.compose.runtime.snapshotFlow`, `kotlinx.coroutines.flow.*`, `de.westnordost.streetcomplete.data.osm.mapdata.LatLon`.) Add a single `IosLogger`/`println`-style log on write, e.g. `// android.util.Log not available` → use the project's logger if one is injectable; otherwise a `println("map camera persisted: $pos")` is fine for verification and can stay (matches the project's `println`-based `IosLogger`).

- [ ] **Step 3: Link gate**

Run the link gate → `BUILD SUCCESSFUL`.

- [ ] **Step 4: Simulator verification — seed path (pre-write NSUserDefaults)**

Camera persistence on iOS is backed by `NSUserDefaults` (the `ObservableSettings` actual). Verify the SEED (read) path by pre-writing a distinctive camera into the app's defaults, then launching:
```bash
xcrun simctl spawn booted defaults write de.westnordost.streetcomplete.ios "map.latitude" -float 48.8584
xcrun simctl spawn booted defaults write de.westnordost.streetcomplete.ios "map.longitude" -float 2.2945
xcrun simctl spawn booted defaults write de.westnordost.streetcomplete.ios "map.zoom2" -float 14.0
```
(With `startDestination = Map` temporarily.) Build+run+screenshot `/tmp/m3b2b-paris.png` → the map opens on the **Eiffel Tower / Paris**, proving the camera is seeded from prefs. Then verify the WRITE path: check the run log for the `map camera persisted:` line after the map settles (the initial settle emits one write). Revert `startDestination`. (Full pan→relaunch round-trip is a device/interaction check, batched into the M3b.2 wrap-up.)

- [ ] **Step 5: Commit**

```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt
git commit -m "M3b.2b: persist iOS map camera position via Preferences"
```

---

## Task 3 — M3b.2c: Port the full layer stack + GeometryUtils + data classes (compile-only)

Bring all 9 layer composables, `GeometryUtils`, and the `Pin`/`Marker`/`StyledElement` data classes into commonMain so the whole stack compiles. No wiring yet — this de-risks the API reconciliation (esp. GeoJSON `Feature` construction) in one focused increment.

**Files (all created):** `screens/main/map2/GeometryUtils.kt` and `screens/main/map2/layers/{CurrentLocationLayers,DownloadedAreaLayer,FocusedGeometryLayers,GeometryMarkersLayers,PinsLayers,SelectedPinsLayer,StyleableOverlayLayers,StyledElement,TracksLayer}.kt`

- [ ] **Step 1: Port `GeometryUtils.kt` (reconciliation rule 1)**

```bash
git show upstream/maplibre-compose:app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/GeometryUtils.kt \
  > app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/GeometryUtils.kt
```
Then replace `io.github.dellisd.spatialk.geojson` → `org.maplibre.spatialk.geojson` (imports + the `typealias GeoJsonBoundingBox`). Depends only on commonMain `ElementGeometry`/`LatLon`/`BoundingBox` + `util.math.{isInPolygon, isRingDefinedClockwise, measuredArea}` (all confirmed commonMain) + spatialk geojson. No Android.

- [ ] **Step 2: Port the 9 layer files with reconciliation rules 1–4**

For each file, `git show upstream/maplibre-compose:app/src/androidMain/…/map2/layers/<File>.kt > app/src/commonMain/…/map2/layers/<File>.kt`, then apply:
- **All files:** GeoJSON package rename (rule 1); `Feature(…, properties = JsonObject(mapOf(...)))` where features are built (rule 2).
- **`PinsLayers.kt`:** also delete `import org.maplibre.android.style.expressions.Expression.log2` (rule 3).
- **`StyledElement.kt`:** port the `data class StyledElement(element, geometry, overlayStyle)` and the helper privates that DON'T use `context`. **Delete** the two `context.resources.getResourceEntryName(overlayStyle.icon)` usages and the unused `private fun StyledElement.toGeoJsonFeatures()` / `OverlayStyle.getIcon()` for now (they're dead in the reference). Keep `createProperties`, `getLineWidth`, `isBridge`, `Color.darkened()`, `Color.toRgbaString()` (uses multiplatform `toArgb`). The overlay feature-building is implemented in Task 5; here we only need it to COMPILE. If removing `toGeoJsonFeatures()` orphans imports, drop them.

Per-file consumers (for reference; signatures unchanged from the catalog):
- `CurrentLocationLayers(location: Location, rotation: Float?)` — uses `Color.Location`, `map_location_*` drawables, `isApril1st`, `toGeometry`.
- `DownloadedAreaLayer(tiles: Collection<TilePos>)` — uses `TilePos.asBoundingBox`, `toPolygon`, `downloaded_area_hatching`, `toPosition`.
- `FocusedGeometryLayers(geometry: ElementGeometry)` — Material-2 `MaterialTheme.colors.secondary`, breathing animation.
- `GeometryMarkersLayers(markers: Collection<Marker>)` — defines `data class Marker(geometry, icon: String?, title: String?)`; `Color.GeometryMarker`.
- `PinsLayers(pins, onClickPin, onClickCluster)` — defines `data class Pin(position: LatLon, icon: String, properties, order)`; `map_pin_circle`; clustering.
- `SelectedPinsLayer(icon: String, pinPositions: Collection<LatLon>)`.
- `StyleableOverlayLayers`: `StyleableOverlayLabelLayer(source, color, haloColor, onClick)`, `StyleableOverlayLayers(source, onClick)`, `StyleableOverlaySideLayer(source, isBridge)`.
- `TracksLayer(id: String, source: Source, opacity: Expression<FloatValue> = const(0.6f))` — `Color.Recording`/`Color.Location`, `map_track_nyan*`.

- [ ] **Step 3: Link gate (the real test for this task)**

Run the link gate → `BUILD SUCCESSFUL`. This is where GeoJSON `Feature`/`FeatureCollection` generics and any DSL residuals surface. Fix each against `docs/superpowers/references/maplibre-compose-0.13.0-api.md`. Common fixes expected: wrap property maps in `JsonObject(...)`; `FeatureCollection(list)` vs `FeatureCollection(*array)`; `feature["k"]` access; PascalCase enum `const(...)`.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2
git commit -m "M3b.2c: port the maplibre-compose layer stack + GeometryUtils to commonMain (compiles)"
```

---

## Task 4 — M3b.2d: MapViewModel + wire the portable layers (synthetic-verified)

Create the commonMain `MapViewModel` and wire the layers that need only VM-owned or fully-portable state: focused geometry, geometry markers, and the downloaded-area layer. Verify rendering with synthetic data.

**Files:**
- Create: `screens/main/map/MapViewModel.kt`, `screens/main/map/MapModule.kt`
- Modify: `screens/main/map2/Map.kt` (accept VM state, render these 3 layers), `screens/main/map/MapScreen.kt` (get VM, pass state), `di` bootstrap (register `mapModule`)

- [ ] **Step 1: Create `MapViewModel`**

Create `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt`. Start with the portable surface:
```kotlin
package de.westnordost.streetcomplete.screens.main.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.download.tiles.TilePos
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.screens.main.map2.layers.Marker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(
    private val downloadedTilesSource: DownloadedTilesSource,
) : ViewModel() {

    private val _downloadedTiles = MutableStateFlow<List<TilePos>>(emptyList())
    val downloadedTiles: StateFlow<List<TilePos>> = _downloadedTiles.asStateFlow()

    private val _geometryMarkers = MutableStateFlow<List<Marker>>(emptyList())
    val geometryMarkers: StateFlow<List<Marker>> = _geometryMarkers.asStateFlow()

    private val _focusedGeometry = MutableStateFlow<ElementGeometry?>(null)
    val focusedGeometry: StateFlow<ElementGeometry?> = _focusedGeometry.asStateFlow()

    private val downloadedTilesListener = object : DownloadedTilesSource.Listener {
        override fun onUpdated() { reloadDownloadedTiles() }
    }

    init {
        downloadedTilesSource.addListener(downloadedTilesListener)
        reloadDownloadedTiles()
    }

    private fun reloadDownloadedTiles() {
        viewModelScope.launch {
            _downloadedTiles.value =
                downloadedTilesSource.getAll(ApplicationConstants.MAX_DOWNLOADABLE_AREA_IN_SQKM.let { 0L }.let {
                    // ignoreOlderThan: use the project's data-retention constant if present, else 0 (all)
                    0L
                })
        }
    }

    // VM-owned highlight state (replaces the androidMain ShowsGeometryMarkers interface):
    fun putGeometryMarkers(markers: List<Marker>) { _geometryMarkers.value = markers }
    fun clearGeometryMarkers() { _geometryMarkers.value = emptyList() }
    fun setFocusedGeometry(geometry: ElementGeometry?) { _focusedGeometry.value = geometry }

    override fun onCleared() {
        downloadedTilesSource.removeListener(downloadedTilesListener)
    }
}
```
> Resolve `getAll(ignoreOlderThan)`'s argument against the real `DownloadedTilesSource` signature: pass the data-retention threshold the codebase already uses (search `getAll(` call sites / `ApplicationConstants` for a `DELETE_OLD_DATA_AFTER`-style constant; the data-layer report references `ApplicationConstants.DELETE_OLD_DATA_AFTER`). Use that constant computed as `nowAsEpochMilli() - DELETE_OLD_DATA_AFTER` if that's the contract, mirroring the androidMain `DownloadedAreaManager`. Verify by reading `git show upstream/maplibre-compose:app/src/androidMain/…/map/DownloadedAreaManager.kt` for the exact call. Keep it correct, not guessed.

- [ ] **Step 2: Create `MapModule` (Koin) and register it**

Create `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt`:
```kotlin
package de.westnordost.streetcomplete.screens.main.map

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mapModule = module {
    viewModel { MapViewModel(get()) }
}
```
Register `mapModule` in the iOS Koin bootstrap. Find where the commonMain modules are listed for iOS (per memory: `InitKoin.kt`). Add `mapModule` to that list. Verify the `koin-compose-viewmodel` `viewModel { }` DSL import matches what other commonMain VMs use (e.g. `EditHistoryViewModel` registration) — mirror that exact import/style.

- [ ] **Step 3: Extend `Map.kt` to render the 3 portable layers**

Add data params + render them inside the `MapStyle` content slots (matching the reference's layer ordering):
```kotlin
@Composable
fun Map(
    modifier: Modifier = Modifier,
    cameraState: CameraState = rememberCameraState(),
    styleState: StyleState = rememberStyleState(),
    downloadedTiles: Collection<TilePos> = emptyList(),
    geometryMarkers: Collection<Marker> = emptyList(),
    focusedGeometry: ElementGeometry? = null,
) {
    MaplibreMap(...) {
        ...
        MapStyle(
            colors = colors,
            languages = languages,
            belowLabelsContent = {
                DownloadedAreaLayer(downloadedTiles)
            },
            aboveLabelsContent = {
                GeometryMarkersLayers(geometryMarkers)
                focusedGeometry?.let { FocusedGeometryLayers(it) }
            },
        )
    }
}
```
(Imports: `DownloadedAreaLayer`, `GeometryMarkersLayers`, `FocusedGeometryLayers` from `…map2.layers.*`; `TilePos`, `Marker`, `ElementGeometry`.)

- [ ] **Step 4: Wire VM state into `MapScreen`**

In `MapScreen.kt`, get the VM and collect its flows:
```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MapScreen(onClickBack: () -> Unit) {
    val viewModel: MapViewModel = koinViewModel()
    val downloadedTiles by viewModel.downloadedTiles.collectAsStateWithLifecycle()
    val markers by viewModel.geometryMarkers.collectAsStateWithLifecycle()
    val focusedGeometry by viewModel.focusedGeometry.collectAsStateWithLifecycle()
    // … prefs/camera as Task 2 …
    Box(Modifier.fillMaxSize()) {
        Map(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            downloadedTiles = downloadedTiles,
            geometryMarkers = markers,
            focusedGeometry = focusedGeometry,
        )
        // back button
    }
}
```
Use the same `koinViewModel` import the M3a screens use (`org.koin.compose.viewmodel.koinViewModel`).

- [ ] **Step 5: Link gate → `BUILD SUCCESSFUL`.**

- [ ] **Step 6: Simulator verification with SYNTHETIC data (temporary)**

Temporarily seed synthetic data to prove the 3 layers render, then revert. Easiest: in `MapScreen` (or a temporary debug block in `Map.kt`), pass literals instead of the empty/VM values for the screenshot:
```kotlin
// TEMP for screenshot — revert before commit
val berlin = LatLon(52.5, 13.4)
Map(
    cameraState = rememberCameraState(CameraPosition(target = Position(13.4, 52.5), zoom = 13.0)),
    geometryMarkers = listOf(Marker(ElementPointGeometry(berlin), icon = null, title = "Test")),
    focusedGeometry = ElementPointGeometry(LatLon(52.51, 13.40)),
    downloadedTiles = listOf(/* a TilePos covering Berlin at DOWNLOAD_TILE_ZOOM — compute via berlin.enclosingTilePos(zoom) */),
)
```
Screenshot `/tmp/m3b2d-synthetic.png` → expect: a pulsing/breathing focus highlight (FocusedGeometryLayers) at the focused point, a geometry marker dot, and (if a TilePos is supplied) the surrounding-area hatching from DownloadedAreaLayer. Log clean of `EXC_`/exceptions. **Revert all temporary synthetic seeding** so `MapScreen` passes the real (empty) VM flows. (For the `TilePos`, use the commonMain helper to convert a LatLon+zoom to a tile, e.g. `LatLon(...).enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM)` — confirm the exact helper name in `data/download/tiles/`.)

- [ ] **Step 7: Commit**

```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/Map.kt \
        <di bootstrap file>
git commit -m "M3b.2d: MapViewModel + wire downloaded-area/markers/focused-geometry layers (synthetic-verified)"
```

---

## Task 5 — M3b.2e: Quest pins + overlay orchestration (port + synthetic-verified)

Port the `Pin` model + `QuestKey↔properties` mapping + the pins/overlay manager orchestration into the VM, wire `PinsLayers` + `StyleableOverlay*` + `SelectedPinsLayer`, and verify pin/overlay RENDERING with synthetic data. Real quest/overlay data does not exist on iOS yet, so end-to-end data verification is deferred (documented); this task proves the rendering path and lands the orchestration code.

**Files:**
- Create: `screens/main/map/QuestPins.kt` (Pin↔QuestKey property mapping + `createQuestPins`, ported from androidMain `QuestPinsManager`)
- Modify: `screens/main/map/MapViewModel.kt` (pins + styledElements + selectedPins flows, source listeners), `screens/main/map2/Map.kt` (render pins/overlay/selected layers), `screens/main/map/MapScreen.kt`

- [ ] **Step 1: Port the Quest↔Pin property mapping**

Read `git show upstream/maplibre-compose:app/src/androidMain/…/map/QuestPinsManager.kt`. Extract the pure-Kotlin pieces into a new commonMain `screens/main/map/QuestPins.kt`: `QuestKey.toProperties(): List<Pair<String,String>>`, `Collection<Pair<String,String>>.toQuestKey(): QuestKey?` (or `Map`-based), and `createQuestPins(quest: Quest, order: Int): List<Pin>` using `quest.markerLocations` and `quest.type.icon`. **Icon as String:** `Pin.icon` is a `String` resource name, but `EditType.icon` is a `DrawableResource`. Resolve the drawable's resource name — check whether `DrawableResource` exposes a stable name in this Compose version; if not, key pins by an icon-id string derived from the quest type name and leave the actual icon image as the documented TODO (no quest data exists yet, so the icon string is not yet rendered). Keep `toProperties`/`toQuestKey` exact (round-trip correctness matters for click handling later).

- [ ] **Step 2: Add pins + overlay state to `MapViewModel`**

Add (mirroring androidMain `QuestPinsManager`/`StyleableOverlayManager`, minus the `MapLibreMap` camera calls — take the current `BoundingBox`+zoom as inputs):
```kotlin
val pins: StateFlow<List<Pin>>
val styledElements: StateFlow<List<StyledElement>>      // feeds the overlay source
val selectedPinPositions: StateFlow<List<LatLon>>       // feeds SelectedPinsLayer
fun onCameraViewport(bbox: BoundingBox?, zoom: Double)  // input from the UI's CameraState
fun onClickPin(properties: Map<String,String>): QuestKey?
```
- Inject `VisibleQuestsSource`, `QuestTypeOrderSource`, `QuestTypeRegistry`, `SelectedOverlaySource`, `MapDataWithEditsSource` (all commonMain, via Koin `get()`; update `MapModule`).
- Pins: register a `VisibleQuestsSource.Listener`; on viewport/update, `getAll(bbox)` → sort by quest-type order → `createQuestPins` → `pins.value`. Debounce with a coroutine + the current viewport (only query at zoom ≥ a threshold, mirroring the manager's `CLUSTER_MIN_ZOOM`/min display zoom).
- Overlay: listen to `SelectedOverlaySource` + `MapDataWithEditsSource`; on viewport/update with a selected overlay, `mapDataSource.getMapDataWithGeometry(bbox)` → `overlay.getStyledElements(mapData)` → `StyledElement(element, geometry, style)` list.
- Implement a commonMain `List<StyledElement>.toFeatureCollection()` (the overlay source builder that `StyledElement.toGeoJsonFeatures()` was meant to be, using String icon names via `PresetIcons`/style names) so `StyleableOverlayLayers(source)` can be fed a `rememberGeoJsonSource`.

- [ ] **Step 3: Render pins/overlay/selected in `Map.kt`**

Add params `pins: Collection<Pin>`, `styledElements: Collection<StyledElement>`, `selectedPinPositions: Collection<LatLon>`, click handlers. Build the overlay source via `rememberGeoJsonSource(GeoJsonData.Features(styledElements.toFeatureCollection()))` and pass it to `StyleableOverlaySideLayer`/`StyleableOverlayLayers`/`StyleableOverlayLabelLayer` in the correct slots (per the reference `Map.kt` ordering: side layers belowRoads/belowRoadsOnBridge, fills/lines belowLabels, labels aboveLabels). Render `PinsLayers(pins, onClickPin, onClickCluster)` and `SelectedPinsLayer(icon = "map_pin", pinPositions = selectedPinPositions)` in `aboveLabelsContent`. **Dynamic icon TODO:** per-feature `image(feature["icon-image"])`/`image(feature["icon"])` reference unregistered images; since there is no data, no lookup happens at runtime. To be safe against a future non-empty-but-unregistered case, leave the reference's expression as-is (documented TODO) — the compose icon registry is a separate follow-up.

- [ ] **Step 4: Feed viewport + click from `MapScreen`**

In `MapScreen`, drive `viewModel.onCameraViewport(...)` from the camera. Compute the visible bbox + zoom from `cameraState` — use `cameraState.projection?.queryVisibleBoundingBox()` (convert the spatialk `BoundingBox` to the app `BoundingBox` via `GeoJsonBoundingBox.toBoundingBox()` from the ported `GeometryUtils`) and `cameraState.position.zoom`, inside the same `snapshotFlow` used for persistence. Wire `onClickPin` to `viewModel.onClickPin(...)` (for now just log the resolved `QuestKey?`; opening a quest form is a later milestone).

- [ ] **Step 5: Link gate → `BUILD SUCCESSFUL`.**

- [ ] **Step 6: Simulator verification with SYNTHETIC pins + overlay (temporary)**

Temporarily pass synthetic `pins` (a few `Pin(LatLon, "map_pin_circle", …)` around Berlin) and a synthetic `selectedPinPositions`, screenshot `/tmp/m3b2e-pins.png` → expect pin dots + cluster circles (using the static `map_pin_circle` image) and the selected-pin spring animation; overlay synthetic `StyledElement`s (a polygon + polyline with an `OverlayStyle`) render fills/lines. Log clean of `EXC_`. **Revert synthetic seeding.** (Real quest/overlay pins with per-type icons require downloaded data + the icon registry — deferred.)

- [ ] **Step 7: Commit**

```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/Map.kt
git commit -m "M3b.2e: port quest-pins + overlay orchestration into MapViewModel (synthetic-verified)"
```

---

## Task 6 — M3b.2 wrap-up: device verification + docs

- [ ] **Step 1: Device build + deploy + on-device check** (batched, needs the iPhone)

```bash
rm -rf build/ios-device
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphoneos \
  -destination 'platform=iOS,id=00008140-000228491A33001C' \
  -derivedDataPath build/ios-device -allowProvisioningUpdates build
APP=$(find build/ios-device/Build/Products/Debug-iphoneos -maxdepth 1 -name 'StreetComplete.app')
xcrun devicectl device install app --device 83B473E9-A697-567F-BDF4-27B28D49D0B5 "$APP"
xcrun devicectl device process launch --device 83B473E9-A697-567F-BDF4-27B28D49D0B5 de.westnordost.streetcomplete.ios
```
On device (interactive): open the map → confirm the SC vector style renders, pan/zoom → kill → relaunch → camera restored at the panned location (the real persistence round-trip). Confirms the `arm64-apple-ios` SPM path still links with the new layer code. If the signing profile has expired (after 2026-06-21), `-allowProvisioningUpdates` renews it.

- [ ] **Step 2: Update backlog + memory**

Update `docs/superpowers/ios-port-backlog.md` with an M3b.2 section (what shipped: SC style, camera persistence, ported layer stack + VM; what's deferred: real pin/overlay DATA + per-feature icon registry await download+data; location dot is M3b.3). Update `docs/superpowers/plans/2026-06-14-ios-port-m3b-map-and-location.md` Task 2 to "DONE — see this plan." Save a memory update noting M3b.2 done.

- [ ] **Step 3: Commit docs**

```bash
git add docs/superpowers
git commit -m "Document M3b.2 completion (programmatic map style + camera + layer stack on iOS)"
```

---

## Self-review notes

- **Spec coverage (vs the M3b plan's Task 2 outline):** style + colors + expression utils + glyphs → Task 1; camera persistence via Preferences → Task 2; port all stateless layer composables + GeometryUtils → Task 3; real `Map()` taking VM/state + layer ordering from the reference → Tasks 1/4/5; wire quest pins / overlays from the commonMain data layer → Task 5 (orchestration ported; real-data verification deferred with documented reason — no download/data on iOS yet). Token externalization explicitly deferred (tracked). Offline tiles remain deferred (#6072).
- **Placeholder scan:** New code (Map.kt, MapViewModel, MapModule, MapScreen, Color.kt, BASE_STYLE) is given in full. Ported files use the `git show … > file` + explicit reconciliation-rule pattern, which is concrete (every transformation is specified), not a placeholder — inlining ~1500 lines of verbatim reference would be transcription-error-prone; the reference is read directly from git. Two points are deliberately resolved-at-build-time and flagged with exact resolution steps + reference-doc pointers: GeoJSON `Feature` generics (Task 3 Step 3) and `DownloadedTilesSource.getAll` retention arg (Task 4 Step 1) — each says how to resolve, not "TBD".
- **Type consistency:** `Pin(position: LatLon, icon: String, properties, order)`, `Marker(geometry, icon: String?, title)`, `StyledElement(element, geometry, overlayStyle)` are used identically in the layers (Task 3) and the VM (Tasks 4–5). `CameraPosition` here always means `org.maplibre.compose.camera.CameraPosition`. `Position` always means `org.maplibre.spatialk.geojson.Position`. Camera fields map 1:1: `mapPosition↔target`, `mapZoom↔zoom`, `mapRotation↔bearing`, `mapTilt↔tilt`.
- **Risk ordering:** the hard/unverifiable-with-real-data work (pins/overlay orchestration, icon registry) is last (Task 5) and isolated; Tasks 1–4 each deliver an independently verifiable improvement. If Task 5 proves to need data that doesn't exist, stop after Task 4 with a clean, valuable increment and record Task 5 as the next step.
