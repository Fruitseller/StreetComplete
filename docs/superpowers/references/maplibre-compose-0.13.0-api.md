No `MapState` exists. I have all the data needed. Here is the complete reference.

---

# maplibre-compose 0.13.0 — Resolved API Reference (extracted from disk)

## Source of this reference

- **No sources jars exist in the Gradle cache.** maplibre-compose ships only **Kotlin/Native `.klib`** artifacts (and an Android `.aar` variant that StreetComplete does not resolve — only iOS targets are present). spatialk geojson ships klibs + a JVM jar.
- The signatures below were extracted by running the konan `klib dump-metadata` tool on the iOS klibs:
  - `~/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.20/bin/klib dump-metadata <file>.klib`
  - maplibre-compose: `/Users/piotr/.gradle/caches/modules-2/files-2.1/org.maplibre.compose/maplibre-compose-iossimulatorarm64/0.13.0/.../maplibre-compose-iosSimulatorArm64Main-0.13.0.klib` (full dump saved to `/tmp/mlc_metadata.txt`, 18183 lines)
  - geojson: `/Users/piotr/.gradle/caches/modules-2/files-2.1/org.maplibre.spatialk/geojson-iossimulatorarm64/0.7.0/.../geojson-iosSimulatorArm64Main-0.7.0.klib` (`/tmp/geojson_metadata.txt`, 2090 lines)
- **Default values:** the metadata renders every default as `/* = ... */` — it confirms a parameter *has* a default, but the actual literal is not in the metadata (it lives in the IR bodies, not extractable as source). I note the meaning of each default where the type makes it obvious.
- Throughout, I rewrite the klib path syntax (`/`) to dotted package syntax and Kotlin generics (`<>`) for readability. `Expr<X>` below is shorthand for `org.maplibre.compose.expressions.ast.Expression<X>`.

## Resolved coordinates

| Group | Artifact | Version |
|---|---|---|
| `org.maplibre.compose` | `maplibre-compose` | `0.13.0` |
| `org.maplibre.spatialk` | `geojson` | `0.7.0` (transitive) |
| `org.maplibre.spatialk` | `units` | `0.7.0` (transitive) |
| `co.touchlab` | `kermit` | `2.1.0` (transitive, the `Logger` type) |

## Package renames vs. the reference's `dev.sargunv.maplibrecompose.*`

The library was formerly published as group `dev.sargunv.maplibre-compose` with root package **`dev.sargunv.maplibrecompose`**. In 0.13.0 everything moved to group `org.maplibre.compose`, root package **`org.maplibre.compose`**. Apply these renames to the reference code:

| Reference (old `dev.sargunv`) | 0.13.0 (`org.maplibre.compose`) |
|---|---|
| `dev.sargunv.maplibrecompose.core` / `.compose.map` | `org.maplibre.compose.map` |
| `dev.sargunv.maplibrecompose.compose.layer` | `org.maplibre.compose.layers` |
| `dev.sargunv.maplibrecompose.compose.source` | `org.maplibre.compose.sources` |
| `dev.sargunv.maplibrecompose.core.expression` | `org.maplibre.compose.expressions.dsl` (functions) + `.expressions.value` (value types) + `.expressions.ast` (`Expression`, literals) |
| `dev.sargunv.maplibrecompose.core.source` (CameraState, etc.) | `org.maplibre.compose.camera` |
| `dev.sargunv.maplibrecompose.core.util` | `org.maplibre.compose.util` |
| spatialk: `io.github.dellisd.spatialk.geojson` | `org.maplibre.spatialk.geojson` |

Also note: geometry types **`Point`, `LineString`, `Polygon`, `Feature`, `FeatureCollection`, etc. now come from `org.maplibre.spatialk.geojson`** (the spatialk library), NOT from `org.maplibre.geojson` (that older `org.maplibre.geojson.*` package is the *Android* MapLibre SDK's GeoJSON, used elsewhere in StreetComplete's Android code — do not mix them in iOS/compose code).

StreetComplete's existing iOS code already imports `org.maplibre.compose.map.MaplibreMap`, `org.maplibre.compose.camera.{CameraPosition, rememberCameraState}`, `org.maplibre.compose.style.BaseStyle`, and `org.maplibre.spatialk.geojson.Position`, confirming these paths.

---

## `org.maplibre.compose.map`

### `MaplibreMap` (the root composable)
```kotlin
@Composable
fun MaplibreMap(
    modifier: Modifier = ...,                                   // androidx.compose.ui.Modifier
    baseStyle: BaseStyle = ...,                                 // org.maplibre.compose.style.BaseStyle
    cameraState: CameraState = ...,                             // org.maplibre.compose.camera.CameraState (defaults to rememberCameraState())
    zoomRange: ClosedRange<Float> = ...,
    pitchRange: ClosedRange<Float> = ...,
    boundingBox: BoundingBox? = ...,                            // org.maplibre.spatialk.geojson.BoundingBox?
    styleState: StyleState = ...,                               // org.maplibre.compose.style.StyleState (defaults to rememberStyleState())
    onMapClick: (Position, DpOffset) -> ClickResult = ...,      // type alias MapClickHandler
    onMapLongClick: (Position, DpOffset) -> ClickResult = ...,  // type alias MapClickHandler
    onFrame: (framesPerSecond: Double) -> Unit = ...,
    options: MapOptions = ...,                                  // org.maplibre.compose.map.MapOptions
    logger: co.touchlab.kermit.Logger? = ...,
    onMapLoadFailed: (reason: String?) -> Unit = ...,
    onMapLoadFinished: () -> Unit = ...,
    content: @Composable @MaplibreComposable () -> Unit = ...,  // layers/sources go here
): Unit
```
- `Position` = `org.maplibre.spatialk.geojson.Position`; `DpOffset` = `androidx.compose.ui.unit.DpOffset`; `ClickResult` = `org.maplibre.compose.util.ClickResult`.
- The trailing `content` lambda is where `LineLayer`, `SymbolLayer`, `rememberGeoJsonSource()`, etc. are declared.
- **There is no `MapState` type in the public API.** Map state is split across `CameraState` (camera) and `StyleState` (sources/style). `MapAdapter`/`MapOptions` are the only map-level state holders.

### `MapOptions` (`@Immutable data class`)
```kotlin
data class MapOptions(
    renderOptions: RenderOptions = ...,
    gestureOptions: GestureOptions = ...,
    ornamentOptions: OrnamentOptions = ...,
)
```

### `GestureOptions` (`@Immutable data class`)
```kotlin
data class GestureOptions(
    isRotateEnabled: Boolean = ...,
    isScrollEnabled: Boolean = ...,
    isTiltEnabled: Boolean = ...,
    isZoomEnabled: Boolean = ...,
    isHapticFeedbackEnabled: Boolean = ...,
)
companion object {
    val Standard: GestureOptions
    val PositionLocked: GestureOptions
    val RotationLocked: GestureOptions
    val ZoomOnly: GestureOptions
    val AllDisabled: GestureOptions
}
```

### `OrnamentOptions` (`@Immutable data class`)
```kotlin
data class OrnamentOptions(
    padding: PaddingValues = ...,                 // androidx.compose.foundation.layout.PaddingValues
    isLogoEnabled: Boolean = ...,
    logoAlignment: Alignment = ...,               // androidx.compose.ui.Alignment
    isAttributionEnabled: Boolean = ...,
    attributionAlignment: Alignment = ...,
    isCompassEnabled: Boolean = ...,
    compassAlignment: Alignment = ...,
    isScaleBarEnabled: Boolean = ...,
    scaleBarAlignment: Alignment = ...,
)
companion object {
    val AllEnabled: OrnamentOptions
    val AllDisabled: OrnamentOptions
    val OnlyLogo: OrnamentOptions
}
```

### `RenderOptions` (`@Immutable data class`)
```kotlin
data class RenderOptions(
    maximumFps: Int? = ...,
    debugSettings: RenderOptions.DebugSettings = ...,
)
companion object { val Standard: RenderOptions; val Debug: RenderOptions }

data class RenderOptions.DebugSettings(
    isTileBoundariesEnabled: Boolean = ...,
    isTileInfoEnabled: Boolean = ...,
    isTimestampsEnabled: Boolean = ...,
    isCollisionBoxesEnabled: Boolean = ...,
    isOverdrawVisualizationEnabled: Boolean = ...,
)
```

> `MapAdapter` and its `Callbacks` interface exist but are **`internal`** — not usable from your code. Camera reads/writes go through `CameraState` (below).

---

## `org.maplibre.compose.camera`

### `CameraPosition` (`@Stable data class`) — constructor params
```kotlin
data class CameraPosition(
    bearing: Double = ...,                 // default 0.0
    target: Position = ...,                // org.maplibre.spatialk.geojson.Position; default Position(0,0)
    tilt: Double = ...,                    // default 0.0
    zoom: Double = ...,                    // default 0.0
    padding: PaddingValues = ...,          // androidx.compose.foundation.layout.PaddingValues
)
// vals: bearing, target, tilt, zoom, padding
```

### `CameraState` (class)
```kotlin
class CameraState(firstPosition: CameraPosition) {
    // OBSERVABLE STATE (Compose snapshot state — read these to react/persist):
    var position: CameraPosition          // read current camera; SETTING jumps the camera immediately
    val moveReason: CameraMoveReason
    val metersPerDpAtTarget: Double
    val isCameraMoving: Boolean
    val projection: CameraProjection?     // null until map is laid out

    // MOVEMENT:
    suspend fun animateTo(finalPosition: CameraPosition, duration: Duration = ...): Unit
    suspend fun animateTo(
        boundingBox: BoundingBox,
        bearing: Double = ...,
        tilt: Double = ...,
        padding: PaddingValues = ...,
        duration: Duration = ...,
    ): Unit
    suspend fun jumpTo(
        boundingBox: BoundingBox,
        bearing: Double = ...,
        tilt: Double = ...,
        padding: PaddingValues = ...,
    ): Unit
    suspend fun awaitProjection(): CameraProjection
}
```
- **To read/observe the current position for persistence:** read `cameraState.position` (it's backed by Compose snapshot state, so reads inside a `snapshotFlow { cameraState.position }` or a composable will recompose/emit on change). `isCameraMoving` / `moveReason` let you debounce persistence to when movement ends.
- **To move:** set `cameraState.position = ...` for an instant jump, or call the `suspend animateTo(...)` / `jumpTo(...)`. There is no synchronous `moveTo`; use the `position` setter for a jump.

### `rememberCameraState`
```kotlin
@Composable
fun rememberCameraState(firstPosition: CameraPosition = ...): CameraState   // default CameraPosition()
```

### `CameraMoveReason` (`@Immutable enum`)
`NONE, UNKNOWN, GESTURE, PROGRAMMATIC`

### `CameraProjection` (class) — screen/world conversion + queries
```kotlin
class CameraProjection {  // internal constructor; obtain via cameraState.projection / awaitProjection()
    fun screenLocationFromPosition(position: Position): DpOffset
    fun positionFromScreenLocation(offset: DpOffset): Position
    fun queryRenderedFeatures(
        offset: DpOffset,
        layerIds: Set<String>? = ...,
        predicate: Expr<BooleanValue> = ...,
    ): List<Feature<Geometry, JsonObject?>>
    fun queryRenderedFeatures(rect: DpRect, layerIds: Set<String>? = ..., predicate: Expr<BooleanValue> = ...): List<Feature<Geometry, JsonObject?>>
    fun queryVisibleBoundingBox(): BoundingBox
    fun queryVisibleRegion(): VisibleRegion
}
```

---

## `org.maplibre.compose.style`

### `BaseStyle` (`@Immutable sealed interface`)
```kotlin
sealed interface BaseStyle {
    data class Uri(uri: String) : BaseStyle
    data class Json(json: String) : BaseStyle {
        constructor(json: JsonObject)                                              // kotlinx.serialization.json.JsonObject
        constructor(builderAction: JsonObjectBuilder.() -> Unit)
    }
    companion object {
        val Demo: BaseStyle.Uri      // there is NO BaseStyle.Demo subclass — it's this companion val
        val Empty: BaseStyle.Json    // likewise NO BaseStyle.Empty subclass
    }
}
```
- Usage: `BaseStyle.Uri("https://…/style.json")`, `BaseStyle.Json(jsonString)`, `BaseStyle.Demo`, `BaseStyle.Empty`. There is **no** `BaseStyle.Demo`/`BaseStyle.Empty` *type* — they are `Companion` properties (so `BaseStyle.Demo` works, but you cannot write `is BaseStyle.Demo`).

### `StyleState` (class) + `rememberStyleState`
```kotlin
class StyleState {  // internal constructor
    val sources: Map<String, Source>   // observable map of sources currently in the style
}
@Composable fun rememberStyleState(): StyleState
```

### Image / sprite registration — **NOT via StyleState**
In 0.13.0, images are **not** registered through a state object. The internal `Style.addImage(...)` is `internal`. Instead you register images inline through the **`image(...)` expression DSL** (package `org.maplibre.compose.expressions.dsl`), and pass the resulting `Expression<ImageValue>` directly to a layer property such as `SymbolLayer(iconImage = image(painter))` or `FillLayer(pattern = image(...))`:
```kotlin
// org.maplibre.compose.expressions.dsl
fun image(value: Expr<StringValue>): Expr<ImageValue>          // reference a sprite already in the style by id
fun image(value: String): Expr<ImageValue>                     // same, literal id
fun image(
    value: ImageBitmap,                                        // androidx.compose.ui.graphics.ImageBitmap
    isSdf: Boolean = ...,
    resizeOptions: ImageResizeOptions? = ...,
): Expr<ImageValue>
fun image(
    value: Painter,                                            // androidx.compose.ui.graphics.painter.Painter
    size: DpSize? = ...,
    drawAsSdf: Boolean = ...,
    resizeOptions: ImageResizeOptions? = ...,
): Expr<ImageValue>
fun image(
    value: Painter, size: DpSize? = ..., drawAsSdf: Boolean = ...,
    resizeOptions: ImageResizeOptions? = ...,
    alpha: Float = ..., colorFilter: ColorFilter? = ...,
): Expr<ImageValue>
```
There is **no `rememberStyleImage` and no `addImage` painter API** in 0.13.0. (Underlying `PainterLiteral.Companion.of(painter, size, drawAsSdf, resizeOptions, alpha, colorFilter)` in `expressions.ast` is what `image(painter, …)` builds.)

`ImageResizeOptions` (`org.maplibre.compose.util`, `@Immutable data class`):
```kotlin
data class ImageResizeOptions(left: Dp, top: Dp, right: Dp, bottom: Dp) {
    constructor(leftTop: DpOffset, rightBottom: DpOffset)
}
```

> `Style`, `SafeStyle`, `ImageManager`, `LayerManager`, `SourceManager` classes are all **`internal`** — ignore them.

---

## `org.maplibre.compose.layers`

All layer composables are top-level `@Composable @MaplibreComposable` functions returning `Unit`, called inside `MaplibreMap { … }`. The underlying `Layer`/`FeatureLayer` classes are `internal`. **Every styling parameter is an `Expression<…>` and has a default.**

Shorthand: `Expr<X>` = `org.maplibre.compose.expressions.ast.Expression<X>`; value types (`ColorValue`, `FloatValue`, `DpValue`, …) live in `org.maplibre.compose.expressions.value`. `NumF` = `NumberValue<Number>` (alias `FloatValue`); `NumDp` = `NumberValue<Dp>` (alias `DpValue`); `Feats` = `List<Feature<Geometry, JsonObject?>>`; `FeaturesClickHandler` = `(Feats) -> ClickResult`.

### `BackgroundLayer` (no source)
```kotlin
@Composable fun BackgroundLayer(
    id: String,
    minZoom: Float = ...,
    maxZoom: Float = ...,
    visible: Boolean = ...,
    opacity: Expr<NumF> = ...,
    color: Expr<ColorValue> = ...,
    pattern: Expr<ImageValue> = ...,
)
```

### `CircleLayer`
```kotlin
@Composable fun CircleLayer(
    id: String,
    source: Source,
    sourceLayer: String = ...,
    minZoom: Float = ..., maxZoom: Float = ...,
    filter: Expr<BooleanValue> = ...,
    visible: Boolean = ...,
    sortKey: Expr<NumF> = ...,
    translate: Expr<OffsetValue<Dp>> = ...,
    translateAnchor: Expr<TranslateAnchor> = ...,
    opacity: Expr<NumF> = ...,
    color: Expr<ColorValue> = ...,
    blur: Expr<NumF> = ...,
    radius: Expr<NumDp> = ...,
    strokeOpacity: Expr<NumF> = ...,
    strokeColor: Expr<ColorValue> = ...,
    strokeWidth: Expr<NumDp> = ...,
    pitchScale: Expr<CirclePitchScale> = ...,
    pitchAlignment: Expr<CirclePitchAlignment> = ...,
    onClick: FeaturesClickHandler? = ...,
    onLongClick: FeaturesClickHandler? = ...,
)
```

### `FillLayer`
```kotlin
@Composable fun FillLayer(
    id: String, source: Source, sourceLayer: String = ...,
    minZoom: Float = ..., maxZoom: Float = ...,
    filter: Expr<BooleanValue> = ..., visible: Boolean = ...,
    sortKey: Expr<NumF> = ...,
    translate: Expr<OffsetValue<Dp>> = ...,
    translateAnchor: Expr<TranslateAnchor> = ...,
    opacity: Expr<NumF> = ...,
    color: Expr<ColorValue> = ...,
    pattern: Expr<ImageValue> = ...,
    antialias: Expr<BooleanValue> = ...,
    outlineColor: Expr<ColorValue> = ...,
    onClick: FeaturesClickHandler? = ..., onLongClick: FeaturesClickHandler? = ...,
)
```

### `FillExtrusionLayer`
```kotlin
@Composable fun FillExtrusionLayer(
    id: String, source: Source, sourceLayer: String = ...,
    minZoom: Float = ..., maxZoom: Float = ...,
    filter: Expr<BooleanValue> = ..., visible: Boolean = ...,
    translate: Expr<OffsetValue<Dp>> = ...,
    translateAnchor: Expr<TranslateAnchor> = ...,
    opacity: Expr<NumF> = ...,
    color: Expr<ColorValue> = ...,
    pattern: Expr<ImageValue> = ...,
    height: Expr<NumF> = ...,
    base: Expr<NumF> = ...,
    verticalGradient: Expr<BooleanValue> = ...,
    onClick: FeaturesClickHandler? = ..., onLongClick: FeaturesClickHandler? = ...,
)
```

### `LineLayer`
```kotlin
@Composable fun LineLayer(
    id: String, source: Source, sourceLayer: String = ...,
    minZoom: Float = ..., maxZoom: Float = ...,
    filter: Expr<BooleanValue> = ..., visible: Boolean = ...,
    sortKey: Expr<NumF> = ...,
    translate: Expr<OffsetValue<Dp>> = ...,
    translateAnchor: Expr<TranslateAnchor> = ...,
    opacity: Expr<NumF> = ...,
    color: Expr<ColorValue> = ...,
    dasharray: Expr<VectorValue<Number>> = ...,
    pattern: Expr<ImageValue> = ...,
    gradient: Expr<ColorValue> = ...,
    blur: Expr<NumDp> = ...,
    width: Expr<NumDp> = ...,
    gapWidth: Expr<NumDp> = ...,
    offset: Expr<NumDp> = ...,
    cap: Expr<LineCap> = ...,
    join: Expr<LineJoin> = ...,
    miterLimit: Expr<NumF> = ...,
    roundLimit: Expr<NumF> = ...,
    onClick: FeaturesClickHandler? = ..., onLongClick: FeaturesClickHandler? = ...,
)
```

### `SymbolLayer` (the big one — full param list)
```kotlin
@Composable fun SymbolLayer(
    id: String, source: Source, sourceLayer: String = ...,
    minZoom: Float = ..., maxZoom: Float = ...,
    filter: Expr<BooleanValue> = ..., visible: Boolean = ...,
    sortKey: Expr<NumF> = ...,
    placement: Expr<SymbolPlacement> = ...,
    spacing: Expr<NumDp> = ...,
    avoidEdges: Expr<BooleanValue> = ...,
    zOrder: Expr<SymbolZOrder> = ...,
    // icon
    iconImage: Expr<ImageValue> = ...,
    iconOpacity: Expr<NumF> = ...,
    iconColor: Expr<ColorValue> = ...,
    iconHaloColor: Expr<ColorValue> = ...,
    iconHaloWidth: Expr<NumDp> = ...,
    iconHaloBlur: Expr<NumDp> = ...,
    iconSize: Expr<NumF> = ...,
    iconRotationAlignment: Expr<IconRotationAlignment> = ...,
    iconPitchAlignment: Expr<IconPitchAlignment> = ...,
    iconTextFit: Expr<IconTextFit> = ...,
    iconTextFitPadding: Expr<DpPaddingValue> = ...,
    iconKeepUpright: Expr<BooleanValue> = ...,
    iconRotate: Expr<NumF> = ...,
    iconAnchor: Expr<SymbolAnchor> = ...,
    iconOffset: Expr<OffsetValue<Dp>> = ...,
    iconPadding: Expr<DpPaddingValue> = ...,
    iconAllowOverlap: Expr<BooleanValue> = ...,
    iconOverlap: Expr<StringValue> = ...,
    iconIgnorePlacement: Expr<BooleanValue> = ...,
    iconOptional: Expr<BooleanValue> = ...,
    iconTranslate: Expr<OffsetValue<Dp>> = ...,
    iconTranslateAnchor: Expr<TranslateAnchor> = ...,
    // text
    textField: Expr<FormattedValue> = ...,
    textOpacity: Expr<NumF> = ...,
    textColor: Expr<ColorValue> = ...,
    textHaloColor: Expr<ColorValue> = ...,
    textHaloWidth: Expr<NumDp> = ...,
    textHaloBlur: Expr<NumDp> = ...,
    textFont: Expr<ListValue<StringValue>> = ...,
    textSize: Expr<NumberValue<TextUnit>> = ...,            // alias TextUnitValue
    textTransform: Expr<TextTransform> = ...,
    textLetterSpacing: Expr<NumberValue<TextUnit>> = ...,
    textRotationAlignment: Expr<TextRotationAlignment> = ...,
    textPitchAlignment: Expr<TextPitchAlignment> = ...,
    textMaxAngle: Expr<NumF> = ...,
    textMaxWidth: Expr<NumberValue<TextUnit>> = ...,
    textLineHeight: Expr<NumberValue<TextUnit>> = ...,
    textJustify: Expr<TextJustify> = ...,
    textWritingMode: Expr<ListValue<TextWritingMode>> = ...,
    textKeepUpright: Expr<BooleanValue> = ...,
    textRotate: Expr<NumF> = ...,
    textAnchor: Expr<SymbolAnchor> = ...,
    textOffset: Expr<OffsetValue<TextUnit>> = ...,          // alias TextUnitOffsetValue
    textVariableAnchor: Expr<ListValue<SymbolAnchor>> = ...,
    textRadialOffset: Expr<NumberValue<TextUnit>> = ...,
    textVariableAnchorOffset: Expr<Nothing> = ...,          // build with textVariableAnchorOffset(...) DSL
    textPadding: Expr<NumDp> = ...,
    textAllowOverlap: Expr<BooleanValue> = ...,
    textOverlap: Expr<SymbolOverlap> = ...,
    textIgnorePlacement: Expr<BooleanValue> = ...,
    textOptional: Expr<BooleanValue> = ...,
    textTranslate: Expr<OffsetValue<Dp>> = ...,
    textTranslateAnchor: Expr<TranslateAnchor> = ...,
    onClick: FeaturesClickHandler? = ..., onLongClick: FeaturesClickHandler? = ...,
)
```

### `HeatmapLayer`
```kotlin
@Composable fun HeatmapLayer(
    id: String, source: Source, sourceLayer: String = ...,
    minZoom: Float = ..., maxZoom: Float = ...,
    filter: Expr<BooleanValue> = ..., visible: Boolean = ...,
    color: Expr<ColorValue> = ...,
    opacity: Expr<NumF> = ...,
    radius: Expr<NumDp> = ...,
    weight: Expr<NumF> = ...,
    intensity: Expr<NumF> = ...,
    onClick: FeaturesClickHandler? = ..., onLongClick: FeaturesClickHandler? = ...,
)
```

### `HillshadeLayer` (no sourceLayer; raster-dem source)
```kotlin
@Composable fun HillshadeLayer(
    id: String, source: Source,
    minZoom: Float = ..., maxZoom: Float = ...,
    visible: Boolean = ...,
    shadowColor: Expr<ColorValue> = ...,
    highlightColor: Expr<ColorValue> = ...,
    accentColor: Expr<ColorValue> = ...,
    illuminationDirection: Expr<NumF> = ...,
    illuminationAnchor: Expr<IlluminationAnchor> = ...,
    exaggeration: Expr<NumF> = ...,
)
```

### `RasterLayer` (no sourceLayer)
```kotlin
@Composable fun RasterLayer(
    id: String, source: Source,
    minZoom: Float = ..., maxZoom: Float = ...,
    visible: Boolean = ...,
    opacity: Expr<NumF> = ...,
    hueRotate: Expr<NumF> = ...,
    brightnessMin: Expr<NumF> = ...,
    brightnessMax: Expr<NumF> = ...,
    saturation: Expr<NumF> = ...,
    contrast: Expr<NumF> = ...,
    resampling: Expr<RasterResampling> = ...,
    fadeDuration: Expr<NumberValue<Duration>> = ...,        // alias MillisecondsValue
)
```

### Layer ordering — `Anchor` (`@Immutable sealed interface`)
```kotlin
sealed interface Anchor {
    data object Top : Anchor
    data object Bottom : Anchor
    data class Above(layerId: String) : Anchor
    data class Below(layerId: String) : Anchor
    data class Replace(layerId: String) : Anchor

    companion object {  // anchor-scoped composable wrappers — place child layers relative to existing style layers:
        @Composable fun Top(block: @Composable () -> Unit)
        @Composable fun Bottom(block: @Composable () -> Unit)
        @Composable fun Above(layerId: String, block: @Composable () -> Unit)
        @Composable fun Below(layerId: String, block: @Composable () -> Unit)
        @Composable fun Replace(layerId: String, block: @Composable () -> Unit)
        @Composable fun At(anchor: Anchor, block: @Composable () -> Unit)
    }
}
```
Usage: `Anchor.Below("waterway") { LineLayer(...) }`.

---

## `org.maplibre.compose.sources`

Base type (`@Stable sealed class`):
```kotlin
sealed class Source {                  // protected constructor
    val attributionHtml: String
    // NOTE: `id` is INTERNAL on the base class — not readable from your code.
    // subclasses: GeoJsonSource, VectorSource, RasterSource, RasterDemSource, ImageSource, ComputedSource, UnknownSource
}
```

### GeoJSON
```kotlin
// data wrapper (sealed interface)
sealed interface GeoJsonData {
    data class Uri(uri: String) : GeoJsonData
    data class JsonString(json: String) : GeoJsonData
    data class Features(geoJson: GeoJsonObject) : GeoJsonData   // GeoJsonObject = spatialk Feature/FeatureCollection/Geometry
}

@Immutable data class GeoJsonOptions(
    minZoom: Int = ...,
    maxZoom: Int = ...,
    buffer: Int = ...,
    tolerance: Float = ...,
    cluster: Boolean = ...,
    clusterRadius: Int = ...,
    clusterMinPoints: Int = ...,
    clusterMaxZoom: Int = ...,
    clusterProperties: Map<String, GeoJsonOptions.ClusterPropertyAggregator<*>> = ...,
    lineMetrics: Boolean = ...,
    synchronousUpdate: Boolean = ...,
)
@Immutable data class GeoJsonOptions.ClusterPropertyAggregator<T : ExpressionValue>(
    mapper: Expr<T>, reducer: Expr<T>,
)

class GeoJsonSource : Source {
    constructor(id: String, data: GeoJsonData, options: GeoJsonOptions)
    fun setData(data: GeoJsonData): Unit
    fun isCluster(feature: Feature<*, JsonObject?>): Boolean
    fun getClusterExpansionZoom(feature: Feature<*, JsonObject?>): Double
    fun getClusterChildren(feature: Feature<*, JsonObject?>): FeatureCollection<*, JsonObject?>
    fun getClusterLeaves(feature: Feature<*, JsonObject?>, limit: Long, offset: Long): FeatureCollection<*, JsonObject?>
}

@Composable
fun rememberGeoJsonSource(
    data: GeoJsonData,
    options: GeoJsonOptions = ...,          // default GeoJsonOptions()
): GeoJsonSource
```
- **Create a source from a `FeatureCollection`/`Feature`/geometry:** `rememberGeoJsonSource(GeoJsonData.Features(featureCollection))`. From raw JSON: `GeoJsonData.JsonString(jsonStr)`. From URL: `GeoJsonData.Uri(url)`. The `id` is generated automatically by `rememberGeoJsonSource`; use the explicit `GeoJsonSource(id, data, options)` constructor if you must control it.

### Vector
```kotlin
class VectorSource : Source {
    constructor(id: String, uri: String)
    constructor(id: String, tiles: List<String>, options: TileSetOptions)
    fun querySourceFeatures(sourceLayerIds: Set<String>, predicate: Expr<BooleanValue> = ...): List<Feature<Geometry, JsonObject?>>
}
@Composable fun rememberVectorSource(uri: String): VectorSource
@Composable fun rememberVectorSource(tiles: List<String>, options: TileSetOptions = ...): VectorSource
```

### Other sources (present, for completeness)
```kotlin
@Composable fun rememberRasterSource(uri: String, tileSize: Int = ...): RasterSource
@Composable fun rememberRasterSource(tiles: List<String>, options: TileSetOptions = ..., tileSize: Int = ...): RasterSource
@Composable fun rememberRasterDemSource(uri: String, tileSize: Int = ...): RasterDemSource
@Composable fun rememberRasterDemSource(tiles: List<String>, options: TileSetOptions = ..., tileSize: Int = ..., encoding: RasterDemEncoding = ...): RasterDemSource
@Composable fun rememberImageSource(position: PositionQuad, uri: String): ImageSource
@Composable fun rememberImageSource(position: PositionQuad, bitmap: ImageBitmap): ImageSource
@Composable fun rememberComputedSource(options: ComputedSourceOptions = ..., getFeatures: (bounds: BoundingBox, zoomLevel: Int) -> FeatureCollection<*, *>): ComputedSource
```

---

## `org.maplibre.compose.expressions` — the DSL

The expression system is split into three packages:

| Package | What's in it |
|---|---|
| `org.maplibre.compose.expressions.dsl` | **All builder functions** (`const`, `get`/`feature`, `interpolate`, `step`, `switch`/`case`, color/number/string/image builders, operators). Import these. |
| `org.maplibre.compose.expressions.value` | Phantom value types used as the `Expression<…>` type parameter (`ColorValue`, `FloatValue`, `BooleanValue`, the layer enums like `LineCap`, …). |
| `org.maplibre.compose.expressions.ast` | `Expression<T>`, `CompiledExpression<T>`, `Literal`, and concrete literal classes (`StringLiteral`, `FloatLiteral`, `ColorLiteral`, …). You rarely name these directly. |

`Expression<out T : ExpressionValue>` core members: `fun compile(context): CompiledExpression<T>`, `fun <X> cast(): Expression<X>`.

### `const` — literal builders (package `…dsl`)
```kotlin
fun const(string: String): StringLiteral
fun const(float: Float): FloatLiteral
fun const(int: Int): IntLiteral
fun const(bool: Boolean): BooleanLiteral
fun const(color: Color): ColorLiteral                        // androidx.compose.ui.graphics.Color
fun const(dp: Dp): DpLiteral
fun const(textUnit: TextUnit): TextUnitCalculation
fun const(duration: Duration): MillisecondsLiteral
fun const(offset: Offset): OffsetLiteral                     // androidx.compose.ui.geometry.Offset
fun const(dpOffset: DpOffset): DpOffsetLiteral
fun const(padding: PaddingValues.Absolute): DpPaddingLiteral
fun <T : EnumValue<T>> const(value: T): EnumLiteral<T>       // e.g. const(LineCap.Round)
fun <T : ExpressionValue> const(list: List<Literal<T, *>>): ListLiteral<T>
fun const(list: List<String>): ListLiteral<StringValue>
fun <T : EnumValue<T>> const(list: List<EnumValue<T>>): ListLiteral<EnumValue<T>>
fun const(list: List<Number>): Literal<VectorValue<Number>, *>
fun nil(): Expr<Nothing>
```
Helpers: `offset(x: Float, y: Float)`, `offset(x: Dp, y: Dp)`, `offset(x: TextUnit, y: TextUnit)`, `padding(left,top,right,bottom: Dp)`, `rgbColor(red, green, blue, alpha?)`, `textVariableAnchorOffset(vararg Pair<SymbolAnchor, Offset>)`.

### Feature data access — the `feature` object (package `…dsl`)
There is **no top-level `get(...)`**; feature access goes through `val feature: Feature` (a singleton):
```kotlin
val feature: Feature      // top-level property

object Feature {
    operator fun get(key: Expr<StringValue>): Expr<*>
    operator fun get(key: String): Expr<*>
    fun has(key: String): Expr<BooleanValue>           // and Expr<StringValue> overload
    fun properties(): Expr<MapValue<*>>
    fun <T : ExpressionValue> state(key: String): Expr<T>
    fun geometryType(): Expr<GeometryType>
    fun <T : ExpressionValue> id(): Expr<T>
    fun lineProgress(): Expr<NumF>
    fun accumulated(): Expr<*>
    fun within(geometry: Expr<GeoJsonValue>): Expr<BooleanValue>
    fun distance(geometry: Expr<GeoJsonValue>): Expr<NumF>
}
```
So a property access in the reference like the old `get("foo")` becomes **`feature.get("foo")`** (or `feature["foo"]`), and `feature.cast()` to a concrete type, e.g. `feature.get("count").asNumber()`.

> Old `dev.sargunv` reference used a top-level `get("key")`. In 0.13.0 it's `feature["key"]` / `feature.get("key")`.

### Decision / conditional expressions (package `…dsl`)
```kotlin
// switch by boolean conditions:
fun <T : ExpressionValue> switch(vararg conditions: Condition<T>, fallback: Expr<T>): Expr<T>
fun <T : ExpressionValue> condition(test: Expr<BooleanValue>, output: Expr<T>): Condition<T>

// switch by matching an input value (the "match" expression):
fun <I : MatchableValue, O : ExpressionValue> switch(input: Expr<I>, vararg cases: Case<I, O>, fallback: Expr<O>): Expr<O>
fun <O : ExpressionValue> case(label: String, output: Expr<O>): Case<StringValue, O>
fun <O : ExpressionValue> case(label: Number, output: Expr<O>): Case<NumF, O>
fun <O, E : EnumValue<E>> case(label: E, output: Expr<O>): Case<EnumValue<E>, O>
fun <O> case(label: List<String>, output: Expr<O>): Case<StringValue, O>     // + List<Number>, List<E> overloads
fun <T : ExpressionValue> coalesce(vararg values: Expr<T>): Expr<T>
```
> The old MapLibre/`dev.sargunv` `case {…}`/`switch {…}` maps to this `switch(condition(...), …, fallback = …)` / `switch(input, case(...), …, fallback = …)` form. There is **no `case`/`match` builder block** — use vararg `condition()`/`case()`.

### Interpolation & steps (package `…dsl`)
```kotlin
fun <T, V : InterpolatableValue<T>> interpolate(type: Expr<InterpolationValue>, input: Expr<NumF>, vararg stops: Pair<Number, Expr<V>>): Expr<V>
fun interpolateHcl(type: Expr<InterpolationValue>, input: Expr<NumF>, vararg stops: Pair<Number, Expr<ColorValue>>): Expr<ColorValue>
fun interpolateLab(type: Expr<InterpolationValue>, input: Expr<NumF>, vararg stops: Pair<Number, Expr<ColorValue>>): Expr<ColorValue>
fun <T : ExpressionValue> step(input: Expr<NumF>, fallback: Expr<T>, vararg stops: Pair<Number, Expr<T>>): Expr<T>
// interpolation curves:
fun linear(): Expr<InterpolationValue>
fun exponential(base: Float): Expr<InterpolationValue>                  // + Expr<NumF> overload
fun cubicBezier(x1, y1, x2, y2: Float): Expr<InterpolationValue>       // + Expr<NumF> overloads
```
Typical: `interpolate(linear(), zoom(), 10 to const(2.dp), 16 to const(8.dp))`.

### Camera / runtime inputs (package `…dsl`)
```kotlin
fun zoom(): Expr<NumF>
fun heatmapDensity(): Expr<NumF>
```

### Color (package `…dsl`)
```kotlin
fun rgbColor(red: Expr<IntValue>, green: Expr<IntValue>, blue: Expr<IntValue>, alpha: Expr<NumF>? = ...): Expr<ColorValue>
fun Expr<ColorValue>.toRgbaComponents(): Expr<VectorValue<Number>>
```

### Arithmetic / comparison / boolean — operators & infix (package `…dsl`)
```kotlin
// arithmetic (operators) on NumberValue:
operator fun <V : NumberValue<U>> Expr<V>.plus(other: Expr<V>): Expr<V>
operator fun Expr<StringValue>.plus(other: Expr<StringValue>): Expr<StringValue>   // string concat
operator fun <V : NumberValue<U>> Expr<V>.minus(other: Expr<NumberValue<U>>): Expr<V>
operator fun <V : NumberValue<U>> Expr<V>.unaryMinus(): Expr<V>
operator fun <V : NumberValue<U>> Expr<V>.times(other: Expr<NumF>): Expr<V>         // + symmetric & NumF*NumF
operator fun <V : NumberValue<U>> Expr<V>.div(divisor: Expr<NumF>): Expr<V>         // + V/V→NumF, NumF/NumF
operator fun <V : NumberValue<U>> Expr<V>.rem(divisor: Expr<IntValue>): Expr<V>
fun Expr<NumF>.pow(exponent: Float): Expr<NumF>                                     // + Expr<NumF> overload
fun sqrt/ln/log10/log2/sin/cos/tan/asin/acos/atan(value: Expr<NumF>): Expr<NumF>
fun <V : NumberValue<U>> min(vararg numbers: Expr<V>): Expr<V>                      // + max, abs
fun round/ceil/floor(value: Expr<NumF>): Expr<IntValue>

// comparison (INFIX):
infix fun Expr<EquatableValue>.eq(other: Expr<EquatableValue>): Expr<BooleanValue>
infix fun Expr<EquatableValue>.neq(other: Expr<EquatableValue>): Expr<BooleanValue>
infix fun <T> Expr<ComparableValue<T>>.gt(other: Expr<ComparableValue<T>>): Expr<BooleanValue>   // + lt, gte, lte
// string-collated comparison (function form): eq/neq/gt/lt/gte/lte(left, right, collator)

// boolean (INFIX) + operator:
infix fun Expr<BooleanValue>.and(other: Expr<BooleanValue>): Expr<BooleanValue>
infix fun Expr<BooleanValue>.or(other: Expr<BooleanValue>): Expr<BooleanValue>
operator fun Expr<BooleanValue>.not(): Expr<BooleanValue>
fun all(vararg expressions: Expr<BooleanValue>): Expr<BooleanValue>
fun any(vararg expressions: Expr<BooleanValue>): Expr<BooleanValue>
```
> The old reference's `eq(a, b)` / `gt(a, b)` (function form) becomes **infix** `a eq b`, `a gt b`. The Android-SDK style `Expression.gt(...)` static calls do not exist here.

### Collections / map / string ops (package `…dsl`)
```kotlin
operator fun <T : ExpressionValue> Expr<ListValue<T>>.get(index: Int): Expr<T>            // + Expr<IntValue>
operator fun <T : ExpressionValue> Expr<MapValue<T>>.get(key: String): Expr<T>            // + Expr<StringValue>
fun <T> Expr<ListValue<T>>.contains(item: Expr<T>): Expr<BooleanValue>
fun <T> Expr<ListValue<T>>.indexOf(item: Expr<T>, startIndex: Int? = ...): Expr<IntValue>
fun <T> Expr<ListValue<T>>.slice(startIndex: Int, endIndex: Int? = ...): Expr<ListValue<T>>
fun Expr<ListValue<*>>.length(): Expr<IntValue>
fun Expr<MapValue<*>>.has(key: String): Expr<BooleanValue>
fun Expr<StringValue>.contains/indexOf/substring/length/uppercase/lowercase/isScriptSupported(...)
```

### Type conversions / coercions (package `…dsl`)
```kotlin
fun Expr<*>.type(): Expr<ExpressionType>
fun Expr<*>.asString(vararg fallbacks: Expr<*>): Expr<StringValue>      // "as*" = assert type
fun Expr<*>.asNumber(vararg fallbacks: Expr<*>): Expr<NumF>
fun Expr<*>.asBoolean(vararg fallbacks: Expr<*>): Expr<BooleanValue>
fun Expr<*>.asMap(...) / asList(...) / asVector(...) / asOffset() / asDpOffset() / asPadding()
fun Expr<*>.convertToString(): Expr<StringValue>                       // "convertTo*" = coerce/parse
fun Expr<*>.convertToNumber(vararg fallbacks): Expr<NumF>
fun Expr<*>.convertToBoolean(): Expr<BooleanValue>
fun Expr<*>.convertToColor(vararg fallbacks): Expr<ColorValue>
fun Expr<NumberValue<*>>.formatToString(locale, currency, minFractionDigits, maxFractionDigits: ... = ...): Expr<StringValue>
```

### Text formatting & images (package `…dsl`)
```kotlin
fun format(vararg spans: FormatSpan): Expr<FormattedValue>
fun span(value: String, textFont: String? = ..., textColor: Color? = ..., textSize: TextUnit? = ...): FormatSpan   // + Expr-typed overload
fun image(...)   // see Style section above
fun collator(caseSensitive: Boolean? = ..., diacriticSensitive: Boolean? = ..., locale: String? = ...): Expr<CollatorValue>
```

### Layer-property enum value types (`org.maplibre.compose.expressions.value`) — exact constants
Pass these via `const(LineCap.Round)` (the layer parameter type is `Expr<LineCap>`):

| Type | Constants (PascalCase) |
|---|---|
| `LineCap` | `Butt, Round, Square` |
| `LineJoin` | `Bevel, Round, Miter` |
| `SymbolPlacement` | `Point, Line, LineCenter` |
| `SymbolAnchor` | `Center, Left, Right, Top, Bottom, TopLeft, TopRight, BottomLeft, BottomRight` |
| `SymbolZOrder` | `Auto, ViewportY, Source` |
| `SymbolOverlap` | `Never, Always, Cooperative` |
| `IconTextFit` | `None, Width, Height, Both` |
| `IconRotationAlignment` | `Map, Viewport, Auto` |
| `IconPitchAlignment` | `Map, Viewport, Auto` |
| `TextJustify` | `Auto, Left, Center, Right` |
| `TextTransform` | `None, Uppercase, Lowercase` |
| `TextRotationAlignment` | `Map, Viewport, ViewportGlyph, Auto` |
| `TextPitchAlignment` | `Map, Viewport, Auto` |
| `TextWritingMode` | `Horizontal, Vertical` |
| `TranslateAnchor` | `Map, Viewport` |
| `CirclePitchScale` | `Map, Viewport` |
| `CirclePitchAlignment` | `Map, Viewport` |
| `IlluminationAnchor` | `Map, Viewport` |
| `RasterResampling` | `Linear, Nearest` |
| `GeometryType` | `Point, LineString, Polygon, MultiPoint, MultiLineString, MultiPolygon` |
| `ExpressionType` | `Number, String, Object, Boolean, Color, Array` |

> These are **PascalCase enum constants** in 0.13.0 (e.g. `LineCap.Round`), not string constants. The Android-SDK reference's `Property.LINE_CAP_ROUND` strings do not apply.

---

## `org.maplibre.spatialk.geojson` (geojson 0.7.0)

### `Position` (class, iterable of Double)
```kotlin
class Position {           // primary ctor is internal(DoubleArray)
    constructor(longitude: Double, latitude: Double)
    constructor(longitude: Double, latitude: Double, altitude: Double)
    constructor(longitude: Double, latitude: Double, altitude: Double? = null)
    @SensitiveGeoJsonApi constructor(longitude: Double, latitude: Double, altitude: Double, vararg additionalElements: Double)
    val longitude: Double
    val latitude: Double
    val altitude: Double?
    operator fun get(index: Int): Double
    operator fun component1/component2/component3(): Double / Double / Double?
    fun toJson(): String
}
```
Note: **longitude first, then latitude** (GeoJSON order).

### `Point`
```kotlin
data class Point(coordinates: Position, bbox: BoundingBox? = null) : Geometry {
    constructor(longitude: Double, latitude: Double, altitude: Double? = null, bbox: BoundingBox? = null)
    val coordinates: Position
    val longitude: Double; val latitude: Double; val altitude: Double?
}
```

### `LineString`
```kotlin
data class LineString(coordinates: List<Position>, bbox: BoundingBox? = null) : Geometry {
    constructor(vararg coordinates: Position, bbox: BoundingBox? = null)
    constructor(vararg points: Point, bbox: BoundingBox? = null)
    constructor(coordinates: Array<DoubleArray>, bbox: BoundingBox? = null)
    val coordinates: List<Position>
}
```

### `Polygon`
```kotlin
data class Polygon(coordinates: List<List<Position>>, bbox: BoundingBox? = null) : Geometry {
    constructor(vararg coordinates: List<Position>, bbox: BoundingBox? = null)   // each list = a ring
    constructor(vararg lineStrings: LineString, bbox: BoundingBox? = null)
    constructor(coordinates: Array<Array<DoubleArray>>, bbox: BoundingBox? = null)
    val coordinates: List<List<Position>>
}
```
Also present with analogous constructors: `MultiPoint(List<Position>…)`, `MultiLineString(List<List<Position>>…)`, `MultiPolygon(List<List<List<Position>>>…)`, `GeometryCollection<G : Geometry>(List<G>…)`.

### `Feature<G : Geometry?, P>`
```kotlin
data class Feature<out G : Geometry?, out P>(   // P : @Serializable Any?
    geometry: G,
    properties: P,
    id: JsonPrimitive? = null,          // typealias FeatureId = JsonPrimitive (FeatureIdSerializer)
    bbox: BoundingBox? = null,
) : GeoJsonObject {
    val geometry: G; val properties: P; val id: JsonPrimitive?
}
// Common concrete form used across maplibre-compose: Feature<Geometry, JsonObject?>
```

### `FeatureCollection<G : Geometry?, P>`
```kotlin
data class FeatureCollection<out G : Geometry?, out P>(
    features: List<Feature<G, P>> = emptyList(),
    bbox: BoundingBox? = null,
) : Collection<Feature<G, P>>, GeoJsonObject {
    constructor(vararg features: Feature<G, P>, bbox: BoundingBox? = null)
}
```

### `BoundingBox` (class)
```kotlin
class BoundingBox {
    constructor(west: Double, south: Double, east: Double, north: Double)
    constructor(west: Double, south: Double, minAltitude: Double, east: Double, north: Double, maxAltitude: Double)
    constructor(southwest: Position, northeast: Position)
    val southwest: Position; val northeast: Position
    val west: Double; /* south, east, north … */
}
```

`GeoJsonObject` is the sealed supertype of all of the above (`Geometry` is the sealed supertype of the geometry types). `GeoJsonData.Features(geoJson: GeoJsonObject)` accepts any of these.

---

## `org.maplibre.compose.util` (supporting types)

```kotlin
enum class ClickResult { Consume, Pass }                       // return from onClick handlers
typealias MapClickHandler = (Position, DpOffset) -> ClickResult
typealias FeaturesClickHandler = (List<Feature<Geometry, JsonObject?>>) -> ClickResult
@Immutable data class ImageResizeOptions(left, top, right, bottom: Dp) { constructor(leftTop, rightBottom: DpOffset) }
data class PositionQuad(topLeft, topRight, bottomRight, bottomLeft: Position)
data class VisibleRegion(...)            // returned by CameraProjection.queryVisibleRegion()
annotation class MaplibreComposable      // marker on layer/source composables; @MaplibreComposable scope
```

---

## Key gotchas when porting the old `dev.sargunv` reference

1. **Package roots:** `dev.sargunv.maplibrecompose.*` → `org.maplibre.compose.*`; geometry → `org.maplibre.spatialk.geojson.*` (NOT `org.maplibre.geojson.*`, which is the Android SDK).
2. **Feature access:** old top-level `get("k")` → `feature.get("k")` / `feature["k"]` (the `feature` singleton).
3. **Comparisons are infix:** `a eq b`, `a gt b`, `a and b`, not function calls.
4. **`switch`/`case`/`condition`** replace any `case {}`/`match {}` builders; `step` and `interpolate` take `vararg Pair<Number, Expr<T>>` stops (`10 to const(...)`).
5. **Enum values are PascalCase `EnumValue` constants** wrapped with `const(...)` (e.g. `cap = const(LineCap.Round)`), not strings.
6. **Images:** no `addImage`/`rememberStyleImage`; use `image(painter|bitmap|id)` inline as the property value.
7. **`BaseStyle.Demo`/`.Empty` are companion vals**, `.Uri`/`.Json` are the only subtypes.
8. **Camera persistence:** read `cameraState.position` (snapshot-state); move with the `position` setter (jump) or `suspend animateTo/jumpTo`.
9. **No `MapState`** type exists; state = `CameraState` + `StyleState`.
10. **`Layer`, `Source.id`, `Style`, `MapAdapter`** are `internal` — drive everything through the composables and `CameraState`/`StyleState`.

All file paths referenced are absolute under `/Users/piotr/.gradle/caches/modules-2/files-2.1/org.maplibre.compose/` and `/org.maplibre.spatialk/`; the extracted dumps are at `/tmp/mlc_metadata.txt` and `/tmp/geojson_metadata.txt` if you want to grep further for any signature I didn't surface.