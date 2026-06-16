I have all the data I need. The investigation is complete.

---

# PORTING CATALOG — `upstream/maplibre-compose` (PR #6352) map2 reference layers

All paths below are under `app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/`. The library API being targeted is the multiplatform `org.maplibre.compose.*` (maplibre-compose) plus `io.github.dellisd.spatialk.geojson.*` (NOT `org.maplibre.spatialk.*` — the branch uses the dellisd spatial-k fork) and `org.jetbrains.compose.resources.*`.

## Top-level porting hazards (read first)

1. **`Map.kt` does not compile as-is — ~12 undefined symbols.** It references parameters/values that are never declared. See the dedicated Map.kt analysis at the end.
2. **`PinsLayers.kt` line 17: `import org.maplibre.android.style.expressions.Expression.log2`** — this is the only genuine `org.maplibre.android.*` (Android-native) import in the entire set. It is also redundant/wrong: line 28 imports the correct `org.maplibre.compose.expressions.dsl.log2`, and the call sites (lines 72, 76) want the compose one. **For commonMain you must delete the android import.** Without doing so, the file won't compile on iOS at all.
3. **`StyledElement.kt` references `context`** (an Android `Context`) at lines 26 and 54 (`context.resources.getResourceEntryName(...)`) but `context` is never declared/imported. `toGeoJsonFeatures()` and `getIcon()` are both `private` and **never called** anywhere — this file is essentially dead/half-ported scaffolding. For commonMain, resolving drawable resource int → name must be replaced (there is no `Context.resources` on iOS; icons are already strings elsewhere via `org.jetbrains.compose.resources`).
4. **`OrnamentOptions` is used in Map.kt with no import** — undefined symbol.

---

## 1. `Map.kt`

Full source (66 LOC + base style constant). The composable wires up all layers. **Does not compile.**

```kotlin
package de.westnordost.streetcomplete.screens.main.map2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.screens.main.map2.layers.CurrentLocationLayers
import de.westnordost.streetcomplete.screens.main.map2.layers.DownloadedAreaLayer
import de.westnordost.streetcomplete.screens.main.map2.layers.FocusedGeometryLayers
import de.westnordost.streetcomplete.screens.main.map2.layers.GeometryMarkersLayers
import de.westnordost.streetcomplete.screens.main.map2.layers.PinsLayers
import de.westnordost.streetcomplete.screens.main.map2.layers.SelectedPinsLayer
import de.westnordost.streetcomplete.screens.main.map2.layers.StyleableOverlayLabelLayer
import de.westnordost.streetcomplete.screens.main.map2.layers.StyleableOverlayLayers
import de.westnordost.streetcomplete.screens.main.map2.layers.StyleableOverlaySideLayer
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.StyleState
import org.maplibre.compose.style.rememberStyleState

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
        options = MapOptions(
            ornamentOptions = OrnamentOptions.AllDisabled
        )
    ) {
        val languages = listOf(Locale.current.language)
        val colors = if (isSystemInDarkTheme()) MapColors.Night else MapColors.Light

        MapStyle(
            colors = colors,
            languages = languages,
            belowRoadsContent = {
                StyleableOverlaySideLayer(styleableOverlaySource, isBridge = false)
            },
            belowRoadsOnBridgeContent = {
                StyleableOverlaySideLayer(styleableOverlaySource, isBridge = true)
            },
            belowLabelsContent = {
                DownloadedAreaLayer(tiles)
                StyleableOverlayLayers(styleableOverlaySource, onClickOverlay)
                TracksLayers()
            },
            aboveLabelsContent = {
                StyleableOverlayLabelLayer(styleableOverlaySource, colors.text, colors.textOutline, onClickOverlay)
                GeometryMarkersLayers(markers)
                FocusedGeometryLayers(geometry)
                CurrentLocationLayers(location, rotation)
                PinsLayers(pins, onClickPin, onClickCluster)
                SelectedPinsLayer(iconPainter, pinPositions)
            }
        )
    }
}

private val BASE_STYLE = """ ... glyphs Res.getUri("files/glyphs/Roboto Regular/0-255.pbf") with {fontstack}/{range} substitution ... """
```

**Imports flagged:**
- (a) `org.maplibre.compose.*`: `camera.CameraState`, `camera.rememberCameraState`, `map.MapOptions`, `map.MaplibreMap`, `style.BaseStyle`, `style.StyleState`, `style.rememberStyleState`.
- (b) geojson/spatialk: none.
- (c) Android-only: none imported, **but `OrnamentOptions` used with no import (undefined).**
- (d) StreetComplete app types: `resources.Res` (compose-resources), the 9 layer composables (`CurrentLocationLayers`, `DownloadedAreaLayer`, `FocusedGeometryLayers`, `GeometryMarkersLayers`, `PinsLayers`, `SelectedPinsLayer`, `StyleableOverlayLabelLayer`, `StyleableOverlayLayers`, `StyleableOverlaySideLayer`), and same-package `MapColors`/`MapStyle`.

**Signature:** `@Composable fun Map(modifier: Modifier = Modifier, cameraState: CameraState = rememberCameraState(), styleState: StyleState = rememberStyleState())`. It takes **no data parameters** yet uses many — that is the bug.

**Layer ordering implied** (the call sequence inside `MapStyle`'s injection slots, top-to-bottom = back-to-front):
- `belowRoadsContent` → `StyleableOverlaySideLayer(isBridge=false)`
- `belowRoadsOnBridgeContent` → `StyleableOverlaySideLayer(isBridge=true)`
- `belowLabelsContent` → `DownloadedAreaLayer(tiles)`, then `StyleableOverlayLayers(...)`, then `TracksLayers()`
- `aboveLabelsContent` → `StyleableOverlayLabelLayer(...)`, then `GeometryMarkersLayers(markers)`, then `FocusedGeometryLayers(geometry)`, then `CurrentLocationLayers(location, rotation)`, then `PinsLayers(pins, onClickPin, onClickCluster)`, then `SelectedPinsLayer(iconPainter, pinPositions)`.

**Undefined / would-not-compile symbols in Map.kt** (confirmed: none of these are declared as params or locals anywhere in the file, and none defined elsewhere in the branch):
1. `OrnamentOptions` (and `.AllDisabled`) — no import, no definition.
2. `styleableOverlaySource` — used 4×; never declared. (Expected to be an `org.maplibre.compose.sources.Source`, per the layer signatures.)
3. `onClickOverlay` — used 2×; never declared. (Expected `FeaturesClickHandler?`.)
4. `tiles` — `DownloadedAreaLayer(tiles)`; expected `Collection<TilePos>`.
5. `markers` — `GeometryMarkersLayers(markers)`; expected `Collection<Marker>`.
6. `geometry` — `FocusedGeometryLayers(geometry)`; expected `ElementGeometry`.
7. `location` — `CurrentLocationLayers(location, rotation)`; expected `Location`.
8. `rotation` — same call; expected `Float?`.
9. `pins` — `PinsLayers(pins, ...)`; expected `Collection<Pin>`.
10. `onClickPin` — expected `FeaturesClickHandler?`.
11. `onClickCluster` — expected `FeaturesClickHandler?`.
12. `iconPainter` — `SelectedPinsLayer(iconPainter, pinPositions)`; **type mismatch hazard**: `SelectedPinsLayer`'s first param is `icon: String`, not a painter. Even once declared, the name/type is wrong.
13. `pinPositions` — expected `Collection<LatLon>`.
14. **`TracksLayers()`** (plural, zero-arg) — called but **does not exist**. Only `TracksLayer(id: String, source: Source, opacity)` (singular, 3 params) is defined in `TracksLayer.kt`. This is a separate undefined symbol.

So ~13–14 undefined references. Net: `Map()` needs to be given all these as parameters (or hoisted state), `OrnamentOptions` needs an import, and the `TracksLayers()` call needs to be either implemented or rewritten to call `TracksLayer(...)` with real arguments.

---

## 2. `MapStyle.kt` (large, ~570 LOC — summarized per instructions)

The core background-map style. All composables are `@Composable @MaplibreComposable`.

**Imports flagged:**
- (a) `org.maplibre.compose.*`: `expressions.ast.Expression`; `expressions.dsl.{Feature, all, asNumber, const, feature, image, nil, not}`; `expressions.value.{BooleanValue, IconRotationAlignment, LineCap, LineJoin, SymbolPlacement}`; `layers.{BackgroundLayer, CircleLayer, FillExtrusionLayer, FillLayer, LineLayer, SymbolLayer}`; `sources.{Source, TileSetOptions, rememberVectorSource}`; `util.MaplibreComposable`.
- (b) geojson/spatialk: none.
- (c) Android-only: none.
- (d) StreetComplete: `resources.Res`, `resources.map_oneway_arrow`, `org.jetbrains.compose.resources.painterResource`. Same-package helpers from `ExpressionUtils.kt` (`fadeInAtZoom`, `fadeOutAtZoom`, `byZoom`, `has`, `hasAny`, `isPoint`, `isLines`, `isArea`, `localizedName`) and `MapColors`.

**Public signature:**
```kotlin
@Composable @MaplibreComposable
fun MapStyle(
    colors: MapColors,
    languages: List<String>,
    belowRoadsContent: @Composable @MaplibreComposable () -> Unit = {},
    belowRoadsOnBridgeContent: @Composable @MaplibreComposable () -> Unit = {},
    belowLabelsContent: @Composable @MaplibreComposable () -> Unit = {},
    aboveLabelsContent: @Composable @MaplibreComposable () -> Unit = {},
)
```
Consumes a `MapColors` palette and a language list. Creates a single shared vector source via `rememberVectorSource(tiles = [JawgMaps streets-v2+hillshade-v1 URL with embedded access-token], options = TileSetOptions(maxZoom=16, attributionHtml=...))`. Builds `RoadType` definitions (`remember(colors)`), then renders layer groups in this fixed order:

`LandLayers` → `HillshadeLayers` → `WaterLayers(None)` → `AerowaysLayer` → `BuildingLayers` → `RoadLayers(Tunnel)` → `PedestrianAreaLayers(None)` → **belowRoadsContent()** → `RoadLayers(None)` → `RailwayLayer(None)` → `BarriersLayers` → `BridgeAreasLayers` → `WaterLayers(Bridge)` → `PedestrianAreaLayers(Bridge)` → **belowRoadsOnBridgeContent()** → `RoadLayers(Bridge)` → `RailwayLayer(Bridge)` → `OnewayArrowsLayer` → `BoundaryLayer` → **belowLabelsContent()** → `LabelLayers` → **aboveLabelsContent()**.

**Private composable signatures (all `@Composable @MaplibreComposable`):**
- `LandLayers(source: Source, colors: MapColors)`
- `HillshadeLayers(source: Source, colors: MapColors)`
- `WaterLayers(source: Source, colors: MapColors, structure: Structure)`
- `AerowaysLayer(source: Source, colors: MapColors)`
- `BuildingLayers(source: Source, colors: MapColors)`
- `PedestrianAreaLayers(source: Source, colors: MapColors, structure: Structure)`
- `RoadLayers(source: Source, colors: MapColors, roads: List<RoadType>, paths: RoadType, serviceRoads: RoadType, structure: Structure)`
- `BarriersLayers(source: Source, colors: MapColors)`
- `BridgeAreasLayers(source: Source, colors: MapColors)`
- `OnewayArrowsLayer(source: Source, colors: MapColors)` — uses `painterResource(Res.drawable.map_oneway_arrow)`
- `BoundaryLayer(source: Source, colors: MapColors)`
- `LabelLayers(source: Source, colors: MapColors, languages: List<String>)`
- `BuildingExtrudeLayer(source: Source, colors: MapColors)` — defined but its only call site in `MapStyle` is commented out.
- `RoadLayer(road: RoadType, source: Source, structure: Structure)`
- `RoadCasingLayer(road: RoadType, source: Source, structure: Structure)`
- `RoadPrivateOverlayLayer(road: RoadType, source: Source, colors: MapColors, structure: Structure)`
- `RailwayLayer(source: Source, colors: MapColors, structure: Structure)`
- `StepsOverlayLayer(source: Source, colors: MapColors, structure: Structure)`

**Private types/helpers:**
- `@Immutable private data class RoadType(id: String, minZoom: Float = 0f, filters: Expression<BooleanValue>, color: Color, colorOutline: Color, widthStops: List<Pair<Number, Dp>>)`
- `private enum class Structure(val id: String?) { Bridge("bridge"), Tunnel("tunnel"), None(null) }`
- Feature extension helpers: `Feature.isStructure(structure)`, `isBridge()`, `isTunnel()`, `isOnGround()`, `inClass(vararg)`, `inType(vararg)`, `localizedName(languages)` (delegates to ExpressionUtils' `localizedName` with `nameKey="name"`, `localizedNameKey={ "name_$it" }`, `extraNameKeys=["name_ltn"]`).

**TODO/FIXME (verbatim):**
- `// TODO localization of attribution: map_attribution_osm` (inside `attributionHtml`).
- `// I don't know, kind of does not look good due to missing extrusion outline.` followed by commented-out `//BuildingExtrudeLayer(source, colors)`.

**Platform notes:** The Jawg access token is hard-coded inline (`accessToken = "mL9X4..."`). No Android-only API calls. `painterResource` is the multiplatform compose-resources one.

---

## 3. `MapColors.kt`

```kotlin
package de.westnordost.streetcomplete.screens.main.map2

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class MapColors(
    val earth: Color, val water: Color, val waterShore: Color, val green: Color,
    val forest: Color, val town: Color, val building: Color, val buildingOutline: Color,
    val barrier: Color, val pointBarrier: Color, val adminBoundary: Color, val railway: Color,
    val aeroway: Color, val path: Color, val steps: Color, val road: Color, val roadOutline: Color,
    val pedestrian: Color, val motorway: Color, val motorwayOutline: Color, val text: Color,
    val textOutline: Color, val textWater: Color, val textWaterOutline: Color,
    val privateOverlay: Color, val hillshadeLight: Color, val hillshadeShadow: Color,
    val onewayArrow: Color
) { companion object }

val MapColors.Companion.Light get() = mapColorsLight
private val mapColorsLight = MapColors( ... )   // uses Color(0x..), Color(r,g,b), Color.hsl(...)

val MapColors.Companion.Night get() = mapColorsNight
private val mapColorsNight = MapColors( ... )
```
**Imports:** only `androidx.compose.runtime.Immutable` and `androidx.compose.ui.graphics.Color`. (a)/(b)/(c) none. (d) none. **Fully multiplatform-clean — ports unchanged.** No TODO/FIXME, no platform calls.

---

## 4. `ExpressionUtils.kt`

```kotlin
package de.westnordost.streetcomplete.screens.main.map2

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.{Feature, all, any, coalesce, condition, const, contains,
    convertToBoolean, convertToNumber, convertToString, div, dp, eq, exponential, interpolate, neq,
    plus, switch, times, zoom}
import org.maplibre.compose.expressions.value.{GeometryType, NumberValue, StringValue}
import kotlin.math.PI
import kotlin.math.cos
```
**Imports flagged:** (a) extensive `org.maplibre.compose.expressions.*` only; (b) none; (c) none; (d) none. **Multiplatform-clean.**

**Top-level functions / signatures (all consume only their args — pure, no state):**
- `fadeInAtZoom(start: Float, range: Float = 1f, endOpacity: Float = 1f)` → calls `byZoom`.
- `fadeOutAtZoom(start: Float, range: Float = 1f, startOpacity: Float = 1f)`.
- `@JvmName("byZoomFloat") byZoom(vararg stops: Pair<Number, Float>)` — interpolate exponential(2f) over zoom().
- `@JvmName("byZoomDp") byZoom(vararg stops: Pair<Number, Dp>)`.
- `@JvmName("byZoomTextUnit") byZoom(vararg stops: Pair<Number, TextUnit>)`. **`@JvmName` is a JVM-only annotation — on iOS/native targets these will collide unless renamed or the annotation is dropped; this is a porting hazard for commonMain.**
- `Feature.has(key: String, value: String)`, `has(key, value: Int)`, `has(key, value: Boolean)`.
- `Feature.hasAny(key: String, values: List<String>)`.
- `Feature.isPoint()`, `isLines()`, `isArea()` (using `GeometryType.*`).
- `Feature.localizedName(languages: List<String>, nameKey: String, localizedNameKey: (String) -> String, extraNameKeys: List<String>): Expression<StringValue>`.
- `inMeters(width: Expression<NumberValue<Number>>, latitude: Double = 30.0): Expression<NumberValue<Dp>>`.
- `inMeters(width: Float, latitude: Double = 30.0): Expression<NumberValue<Dp>>`.

No TODO/FIXME. No Android calls (note the `@JvmName` annotations above).

---

## 5. `GeometryUtils.kt`

```kotlin
package de.westnordost.streetcomplete.screens.main.map2

import de.westnordost.streetcomplete.data.osm.geometry.{ElementGeometry, ElementPointGeometry,
    ElementPolygonsGeometry, ElementPolylinesGeometry}
import de.westnordost.streetcomplete.data.osm.mapdata.{BoundingBox, LatLon}
import de.westnordost.streetcomplete.util.math.{isInPolygon, isRingDefinedClockwise, measuredArea}
import io.github.dellisd.spatialk.geojson.{Geometry, LineString, MultiLineString, MultiPolygon,
    Point, Polygon, Position}

typealias GeoJsonBoundingBox = io.github.dellisd.spatialk.geojson.BoundingBox
```
**Imports flagged:** (a) none; (b) **`io.github.dellisd.spatialk.geojson.*`** (Geometry, LineString, MultiLineString, MultiPolygon, Point, Polygon, Position, BoundingBox) — note this is the **dellisd** spatial-k, not `org.maplibre.spatialk`; (c) none; (d) SC `ElementGeometry`/`ElementPointGeometry`/`ElementPolylinesGeometry`/`ElementPolygonsGeometry`, `BoundingBox`, `LatLon`, and `util.math.{isInPolygon, isRingDefinedClockwise, measuredArea}`.

**Functions:** `BoundingBox.toGeoJsonBoundingBox()`, `GeoJsonBoundingBox.toBoundingBox()`, `ElementGeometry.toGeometry(): Geometry` (sealed dispatch), `ElementPointGeometry.toGeometry(): Point`, `ElementPolylinesGeometry.toGeometry(): Geometry`, `ElementPolygonsGeometry.toGeometry(): Geometry` (does outer/inner ring grouping with area sort + `isInPolygon`), `LatLon.toGeometry(): Point`, `LatLon.toPosition(): Position`, `Position.toLatLon(): LatLon`. Pure conversion helpers, no Compose, no Android. **Multiplatform-clean (already commonMain-friendly).** No TODO/FIXME.

---

## 6. `layers/CurrentLocationLayers.kt`

```kotlin
@Composable @MaplibreComposable
fun CurrentLocationLayers(location: Location, rotation: Float?)
```
**Imports flagged:** (a) `org.maplibre.compose.expressions.dsl.{const, image}`, `expressions.value.{CirclePitchAlignment, IconPitchAlignment}`, `layers.{CircleLayer, SymbolLayer}`, `sources.{GeoJsonData, rememberGeoJsonSource}`, `util.MaplibreComposable`. (b) none directly (uses `toGeometry()`). (c) none. (d) SC `data.location.Location`, `resources.Res` + `map_location_nyan`/`map_location_shadow`/`map_location_view_direction`, `screens.main.map2.inMeters`, `screens.main.map2.toGeometry`, `ui.theme.Location` (a `Color` extension), `util.ktx.isApril1st`, `org.jetbrains.compose.resources.painterResource`.

**Consumes:** `location` (position → `GeoJsonData.Features(location.position.toGeometry())`, `location.accuracy`, `location.position.latitude`), `rotation` (nullable → direction symbol drawn only when non-null). `isApril1st` is `remember`-ed and switches the `location` circle for a `location-nyan` symbol. Layer order: `accuracy` (CircleLayer) → `direction` (if rotation) → `location-shadow` → `location`/`location-nyan`.

**TODO/FIXME:** `// TODO animate accuracy, position`. No Android calls.

---

## 7. `layers/DownloadedAreaLayer.kt`

```kotlin
@Composable @MaplibreComposable
fun DownloadedAreaLayer(tiles: Collection<TilePos>)
```
**Imports flagged:** (a) `org.maplibre.compose.expressions.dsl.{const, image}`, `layers.FillLayer`, `sources.{GeoJsonData, rememberGeoJsonSource}`, `util.MaplibreComposable`. (b) `io.github.dellisd.spatialk.geojson.Polygon`. (c) none. (d) SC `ApplicationConstants` (`DOWNLOAD_TILE_ZOOM`), `data.download.tiles.TilePos` (+ `asBoundingBox`), `data.osm.mapdata.LatLon`, `data.osm.mapdata.toPolygon`, `resources.Res` + `downloaded_area_hatching`, `screens.main.map2.toPosition`, `org.jetbrains.compose.resources.painterResource`.

**Consumes:** `tiles` → `toHolesInWorldPolygon()` builds a world-spanning `Polygon` with tile-shaped holes (`asBoundingBox(zoom).toPolygon().asReversed()`). Single `FillLayer` id `downloaded-area`, opacity 0.6, hatching pattern image. No TODO/FIXME, no Android calls.

---

## 8. `layers/FocusedGeometryLayers.kt`

```kotlin
@MaplibreComposable @Composable
fun FocusedGeometryLayers(geometry: ElementGeometry)
```
**Imports flagged:** (a) `org.maplibre.compose.expressions.dsl.{any, const, feature}`, `expressions.value.{LineCap, LineJoin}`, `layers.{CircleLayer, FillLayer, LineLayer}`, `sources.{GeoJsonData, rememberGeoJsonSource}`, `util.MaplibreComposable`. (b) none directly. (c) none. (d) SC `data.osm.geometry.ElementGeometry`, `screens.main.map2.{isArea, isLines, isPoint, toGeometry}`. Also `androidx.compose.material.MaterialTheme` (uses `MaterialTheme.colors.secondary` — **Material 2; commonMain must have the same material artifact available**) and `androidx.compose.animation.core.*`.

**Consumes:** `geometry.toGeometry()` as the source. Runs an `infiniteRepeatable` "breathing" animation (`rememberInfiniteTransition` → `animateFloat`) driving `opacity` (0.5..1), `lineWidth` (8..16.dp), `circleRadius` (10..20.dp). Three layers: `focus-geo-fill` (FillLayer, area), `focus-geo-lines` (LineLayer, area|lines), `focus-geo-circle` (CircleLayer, point). No TODO/FIXME, no Android calls.

---

## 9. `layers/GeometryMarkersLayers.kt`

```kotlin
@MaplibreComposable @Composable
fun GeometryMarkersLayers(markers: Collection<Marker>)

data class Marker(
    val geometry: ElementGeometry,
    val icon: String? = null,   // drawable resource name
    val title: String? = null
)
private fun Marker.toGeoJsonFeature(): List<Feature>
```
**Imports flagged:** (a) `org.maplibre.compose.expressions.dsl.{any, const, convertToString, feature, image, offset}`, `expressions.value.{LineCap, LineJoin, SymbolAnchor}`, `layers.{FillLayer, LineLayer, SymbolLayer}`, `sources.{GeoJsonData, rememberGeoJsonSource}`, `util.MaplibreComposable`. (b) `io.github.dellisd.spatialk.geojson.{Feature, FeatureCollection}`. (c) none. (d) SC `data.osm.geometry.{ElementGeometry, ElementPointGeometry, ElementPolygonsGeometry, ElementPolylinesGeometry}`, `screens.main.map2.{byZoom, isArea, isLines, isPoint, toGeometry}`, `ui.theme.GeometryMarker` (Color ext). Also `kotlinx.serialization.json.{JsonElement, JsonPrimitive}`.

**Consumes:** `markers.flatMap { it.toGeoJsonFeature() }` → `FeatureCollection`. Layers: `geo-fill`, `geo-lines`, `geo-symbols` (icon from `feature["icon"]`, label from `feature["label"]`).

**TODO/FIXME:** `iconImage = image(feature["icon"]), // TODO get icon!!` and inside `toGeoJsonFeature`: `// TODO some icons should be sdf, others, not` / `//   val sdf = name.startsWith("preset_")`. No Android calls.

---

## 10. `layers/PinsLayers.kt`  ← **CONTAINS THE android-native import**

```kotlin
@MaplibreComposable @Composable
fun PinsLayers(
    pins: Collection<Pin>,
    onClickPin: FeaturesClickHandler? = null,
    onClickCluster: FeaturesClickHandler? = null,
)

data class Pin(
    val position: LatLon,
    val icon: String,
    val properties: Collection<Pair<String, String>> = emptyList(),
    val order: Int = 0
)
private fun Pin.toGeoJsonFeature()
```
**Imports flagged:**
- (a) `org.maplibre.compose.expressions.dsl.{all, any, const, convertToNumber, convertToString, div, feature, gt, gte, image, log2, lte, offset, plus, sp, zoom}`, `expressions.value.TranslateAnchor`, `layers.{CircleLayer, SymbolLayer}`, `sources.{GeoJsonData, GeoJsonOptions, rememberGeoJsonSource}`, `util.{FeaturesClickHandler, MaplibreComposable}`.
- (b) `io.github.dellisd.spatialk.geojson.{Feature, FeatureCollection}`.
- (c) **`org.maplibre.android.style.expressions.Expression.log2` (line 17) — ANDROID-ONLY, must be removed.** It conflicts with the compose `log2` (line 28). Call sites (lines 72 & 76) use the compose-DSL one. Delete the android import for commonMain.
- (d) SC `data.osm.mapdata.LatLon`, `resources.Res` + `map_pin_circle`, `screens.main.map2.toGeometry`, `org.jetbrains.compose.resources.painterResource`, `kotlinx.serialization.json.JsonPrimitive`.

**Consumes:** `pins.map { it.toGeoJsonFeature() }` → clustered `GeoJsonOptions(cluster=true, clusterMaxZoom=CLUSTER_MAX_ZOOM=14, clusterRadius=55)`. Constants `CLUSTER_MIN_ZOOM=13`, `CLUSTER_MAX_ZOOM=14`. Layers: `pin-cluster-layer` (SymbolLayer, onClick=onClickCluster, icon `map_pin_circle`, size & text scale via `log2(point_count)`), `pin-dot-layer` (CircleLayer), `pins-layer` (SymbolLayer, onClick=onClickPin, sortKey from `icon-order`, icon from `feature["icon-image"]`). `Pin.toGeoJsonFeature()` puts `icon-image` and `icon-order = order + 50` into properties.

**TODO/FIXME:** `iconImage = image(feature["icon-image"]), // TODO` (pins-layer); `// TODO is this recomposed all the time? In that case, remember the features`.

---

## 11. `layers/SelectedPinsLayer.kt`

```kotlin
@MaplibreComposable @Composable
fun SelectedPinsLayer(icon: String, pinPositions: Collection<LatLon>)
```
Note: first param is **`icon: String`** — Map.kt incorrectly calls it with `iconPainter` (mismatch).

**Imports flagged:** (a) `org.maplibre.compose.expressions.dsl.{const, feature, image}`, `layers.SymbolLayer`, `sources.{GeoJsonData, rememberGeoJsonSource}`, `util.MaplibreComposable`. (b) `io.github.dellisd.spatialk.geojson.{Feature, FeatureCollection}`. (c) none. (d) SC `data.osm.mapdata.LatLon`, `screens.main.map2.toGeometry`, `kotlinx.serialization.json.JsonPrimitive`. Also `androidx.compose.animation.core.{Animatable, Spring, spring}`.

**Consumes:** `pinPositions` → FeatureCollection with `icon-image` property; `icon` string. `LaunchedEffect(pinPositions)` springs `pinsSize` 0.5f→1.5f (bouncy). Single `selected-pins-layer` SymbolLayer.

**TODO/FIXME:** `iconImage = image(feature["icon-image"]), // TODO`. No Android calls.

---

## 12. `layers/StyleableOverlayLayers.kt`

Three public composables:
```kotlin
@MaplibreComposable @Composable
fun StyleableOverlayLabelLayer(source: Source, color: Color, haloColor: Color, onClick: FeaturesClickHandler? = null)

@MaplibreComposable @Composable
fun StyleableOverlayLayers(source: Source, onClick: FeaturesClickHandler? = null)

@MaplibreComposable @Composable
fun StyleableOverlaySideLayer(source: Source, isBridge: Boolean)

private val MIN_ZOOM = 14f
```
**Imports flagged:** (a) `org.maplibre.compose.expressions.dsl.{all, asNumber, condition, const, convertToBoolean, convertToColor, convertToNumber, convertToString, feature, image, nil, not, offset, step, switch, zoom}`, `expressions.value.{LineCap, LineJoin, SymbolAnchor, SymbolZOrder}`, `layers.{FillExtrusionLayer, FillLayer, LineLayer, SymbolLayer}`, `sources.Source`, `util.{FeaturesClickHandler, MaplibreComposable}`. (b) none. (c) none. (d) SC `screens.main.map2.{byZoom, inMeters, isArea, isLines, isPoint}` only — **takes the source as a parameter** (the shared `styleableOverlaySource` that Map.kt fails to provide).

**Consumes:** all three read feature-property-driven styling (`feature["dashed"|"opacity"|"color"|"outline-color"|"width"|"offset"|"height"|"min-height"|"bridge"|"icon"|"label"]`). `StyleableOverlayLayers` renders `overlay-lines-casing`, `overlay-fills` (onClick), `overlay-lines` (onClick), `overlay-fills-outline`, `overlay-heights` (FillExtrusion). `StyleableOverlaySideLayer` renders `overlay-lines-side` filtered on `bridge == isBridge`. `StyleableOverlayLabelLayer` renders `overlay-symbols` (onClick).

**Comment/FIXME:** `// data-driven-styling not supported (...maplibre-style-spec...fill-extrusion-opacity)` and `opacity = const(1f), // cannot use opacity = opacity`. No Android calls. (Note: the `image(feature["icon"])` icon-by-name in the label layer has the same string-icon resolution concern as the other layers, though not TODO-tagged here.)

---

## 13. `layers/StyledElement.kt`  ← **dead/half-ported; references undefined `context`**

```kotlin
data class StyledElement(
    val element: Element,
    val geometry: ElementGeometry,
    val overlayStyle: OverlayStyle
)
private fun StyledElement.toGeoJsonFeatures(): List<Feature>   // UNUSED
private fun createProperties(key: ElementKey): MutableMap<String, JsonPrimitive>
private fun getLineWidth(tags: Map<String, String>): Float
private fun isBridge(tags: Map<String, String>): Boolean
private fun OverlayStyle.getIcon(): Int?                        // UNUSED
private fun Color.darkened(): Color
private fun Color.toRgbaString(): String                       // uses Color.toArgb()
private const val ELEMENT_TYPE = "element_type"; ELEMENT_ID = "element_id"
```
**Imports flagged:** (a) none. (b) `io.github.dellisd.spatialk.geojson.Feature`. (c) none imported, **but `context` is used undefined (lines 26, 54) — an implicit Android `Context` dependency.** (d) SC `data.osm.geometry.ElementGeometry`, `data.osm.mapdata.{Element, ElementKey, key}`, `data.overlays.OverlayStyle` (with `.Point/.Polygon/.Polyline`, `.icon/.label/.color/.height/.minHeight/.stroke/.strokeLeft/.strokeRight/.dashed`), `screens.main.map2.toGeometry`, `kotlinx.serialization.json.JsonPrimitive`, `androidx.compose.ui.graphics.{Color, toArgb}`.

**Porting hazards:**
- `context.resources.getResourceEntryName(overlayStyle.icon)` (×2) — `context` is **undefined** and is an Android-only API. On iOS there is no `Context`; this whole file doesn't compile. `toGeoJsonFeatures()` is `private` and never called — it appears to be unfinished code carried over from the old (non-compose) implementation. For the port you'll likely rebuild this to map `StyledElement` → GeoJSON features feeding `styleableOverlaySource`, using string icon names directly (consistent with `Pin.icon: String`) rather than resolving int resource IDs.
- `Color.toArgb()` is multiplatform in compose-ui (ok). `getIcon()` also unused.

No `// TODO` text, but it is functionally incomplete (undefined `context`, dead private functions).

---

## 14. `layers/TracksLayer.kt`

```kotlin
@MaplibreComposable @Composable
fun TracksLayer(id: String, source: Source, opacity: Expression<FloatValue> = const(0.6f))

@MaplibreComposable @Composable
private fun TracksLayerApril1st(id: String, source: Source, opacity: Expression<FloatValue>)

@MaplibreComposable @Composable
private fun TracksLayerDefault(id: String, source: Source, opacity: Expression<FloatValue>)
```
**Imports flagged:** (a) `org.maplibre.compose.expressions.ast.Expression`, `expressions.dsl.{condition, const, convertToBoolean, feature, image, switch}`, `expressions.value.{FloatValue, LineCap}`, `layers.LineLayer`, `sources.Source`, `util.MaplibreComposable`. (b) none. (c) none. (d) SC `resources.Res` + `map_track_nyan`/`map_track_nyan_record`, `ui.theme.{Location, Recording}` (Color exts), `util.ktx.isApril1st`, `org.jetbrains.compose.resources.painterResource`.

**Consumes:** `id`, `source` (passed in), `opacity` expression; switches on `remember { isApril1st() }`. Default draws a dashed round-cap `LineLayer` colored `Color.Recording` vs `Color.Location` based on `feature["recording"]`; April 1st variant uses nyan-cat pattern images.

**Important for Map.kt port:** Map.kt calls `TracksLayers()` (plural, no args) which **does not exist here** — only the singular `TracksLayer(id, source, opacity)` is defined, and it requires an `id` and a `Source`. The caller must supply a tracks GeoJSON source. No TODO/FIXME, no Android calls.

---

## Summary table of porting hazards

| File | Hazard |
|---|---|
| `Map.kt` | ~13–14 undefined symbols (data params never declared); `OrnamentOptions` unimported; `TracksLayers()` doesn't exist; `iconPainter` wrong type for `SelectedPinsLayer(icon: String, …)`. **Does not compile.** |
| `PinsLayers.kt` | `import org.maplibre.android.style.expressions.Expression.log2` — Android-only, redundant, must delete. |
| `StyledElement.kt` | Undefined `context` (Android `Context`) used 2×; `toGeoJsonFeatures()`/`getIcon()` unused; uses `Context.resources.getResourceEntryName`. Half-ported, doesn't compile. |
| `ExpressionUtils.kt` | `@JvmName` on the three `byZoom` overloads — JVM-only; on native/iOS the overloads need disambiguation or annotation removal. |
| `GeometryUtils.kt`, all layer files | geojson types are **`io.github.dellisd.spatialk.geojson.*`** (dellisd fork), **not** `org.maplibre.spatialk.*`. Confirm the iOS build pulls the same artifact. |
| `FocusedGeometryLayers.kt` | Uses Material-2 `MaterialTheme.colors.secondary`. |
| Multiple layers | String-icon resolution `image(feature["icon"/"icon-image"])` is TODO-flagged; icon-by-name pipeline is unfinished. |
| Clean / port unchanged | `MapColors.kt`, `MapStyle.kt`, `GeometryUtils.kt` (modulo geojson dep) — no Android-only APIs. |

Source location of all files (for reference, on the branch only — not checked out): `app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/` and its `layers/` subdirectory.