# iOS-Port M4 — Real OSM data + quest pins on the map (Design Spec)

> Bundles **M4.0 (icon registration)**, **M4.1 (data download)**, **M4.2 (quest pins)** into one design,
> with **M4.3 (display-only pin selection)** as a stretch. Grounded in a 7-parallel-reader investigation
> (workflow `wf_6ca3cb49-ac6`, 2026-06-28) that read every file this milestone touches. Follows the four
> FINAL architecture decisions in `docs/superpowers/plans/2026-06-18-ios-port-m4-data-and-quests-kickoff.md`
> — those decisions are inputs here, not re-litigated. Verification model and constraints per the M4 task brief.
>
> **Approval model:** the maintainer delegated every decidable point and instructed autonomous execution,
> stopping only for genuine blockers. The "Decisions" sections below record the autonomous choices.

## Goal

Open the iOS map → data downloads for the visible area → **quest pins with correct per-quest-type icons
appear** (including `OsmNoteQuest` pins). End state is device-verified visible pins.

## The headline finding (re-confirmed)

The entire download → DB → quest → `Pin` pipeline is already commonMain and already in the iOS Koin graph
(`InitKoin.kt` includes `downloadModule`, `uploadModule` [provides `named("SerializeSync")` Mutex],
`visibleQuestsModule`, `mapModule`, `osmApiModule`, …). The render consumer is complete:
`MapViewModel.pins: StateFlow<List<Pin>>` → `MapScreen` → `Map` → `PinsLayers`. **Three things are missing:**

1. **No named images are registered** → `iconImage = image(feature["icon-image"].asString())` (PinsLayers.kt:108,
   SelectedPinsLayer.kt:57, both `// TODO`) resolves names that don't exist → pins would render blank. *(M4.0)*
2. **No download is ever triggered** → `IosDownloadControllerStub` is a no-op; `Preloader.preload()` is never
   called → the DB is empty. *(M4.1)*
3. **Nothing calls `MapViewModel.setPins()`** → no producer exists. *(M4.2)*

---

## M4.0 — Quest-pin icon registration

### Crux resolved: the patch is two methods in one file

maplibre-compose 0.13.0 **already implements** the named-image path end-to-end; it is only unreachable:

- `internal interface Style { fun addImage(id: String, image: ImageBitmap, sdf: Boolean, resizeOptions: ImageResizeOptions?); fun removeImage(id: String) }` — methods are already `public`, only the declaring type is `internal`.
- `IosStyle.addImage` → `impl.setImage(image.toUIImage(scale, sdf, resizeOptions), forName = id)` (the exact `MLNStyle` named-sprite call we want). Fully implemented.
- The public DSL `image(value: Expression<StringValue>)` / `image(value: String)` resolves a **named** style image at render time — exactly what the feature-property lookup needs.
- The ONLY gap: the public handle `StyleState` keeps `private var styleNode: StyleNode?`, so there is no public way to reach `style.addImage(...)`.

**Patch (one file, `StyleState.kt`):**
```kotlin
public fun addImage(
    id: String,
    image: ImageBitmap,
    sdf: Boolean = false,
    resizeOptions: ImageResizeOptions? = null,
) { styleNode?.style?.addImage(id, image, sdf, resizeOptions) }

public fun removeImage(id: String) { styleNode?.style?.removeImage(id) }
```
No visibility change to `Style`/`SafeStyle`/`IosStyle` needed (their methods are already public; `StyleState`
is in the same module/package and can call them). The public `image(ImageBitmap)` DSL is a **red herring** for
us — it registers an auto-generated anonymous id (ref-counted via `ImageManager`), not a name we control.

### Fork + build wiring (composite build)

- Upstream repo: `github.com/maplibre/maplibre-compose`, tag **`v0.13.0`** (commit `dc4781959f…`). Library
  module path is **`:lib:maplibre-compose`** (confirm against the clone's `settings.gradle.kts` before writing the substitution). `sargunv/maplibre-compose` has no v0.13.0 — do not use it.
- **No version catalog exists** in StreetComplete (`gradle/libs.versions.toml` absent). The only declaration is
  the inline `implementation("org.maplibre.compose:maplibre-compose:0.13.0")` at `app/build.gradle.kts:181`.
  A composite-build substitution overrides the requested version regardless, so that line stays untouched.
- Clone to a **sibling** dir `/Users/piotr/git/maplibre-compose-fork`, `git checkout v0.13.0`, apply the
  `StyleState.kt` patch, commit on a branch. Add to StreetComplete `settings.gradle.kts` (top-level, outside the
  `pluginManagement`/`dependencyResolutionManagement` blocks, before `include(":app")`):
  ```kotlin
  includeBuild("../maplibre-compose-fork") {
      dependencySubstitution {
          substitute(module("org.maplibre.compose:maplibre-compose"))
              .using(project(":lib:maplibre-compose"))
      }
  }
  ```
- A composite build **recompiles from source**, so the upstream Kotlin 2.3.21 vs StreetComplete 2.3.20 klib-ABI
  skew is irrelevant (no prebuilt-klib metadata mismatch).
- The native `MapLibre.framework` wiring (spmForKmp `swiftPackageConfig` + `linkerOpts`, `app/build.gradle.kts:94-110`)
  is **orthogonal** and stays in `:app` exactly as-is — the maven artifact only ever shipped the cinterop klib.
- The fork must declare **both** `iosArm64()` and `iosSimulatorArm64()` targets (it does upstream). The fork
  builds its own iOS klibs via the same spmForKmp 1.9.1 + `maplibre-gl-native-distribution` toolchain.

**Build-feasibility validation gate (before wiring into StreetComplete):** in the fork, run
`./gradlew :lib:maplibre-compose:compileKotlinIosSimulatorArm64` (or `assemble` for that target) standalone and
confirm it succeeds. This isolates fork-build failures from substitution failures. The upstream multi-module
graph also evaluates sibling modules (`:lib:maplibre-native-bindings` [desktop], `:lib:maplibre-js-bindings`
[js], AGP androidLibrary, buildSrc convention plugins). If their configuration blocks the iOS build on this
machine, trim the desktop/js targets in the fork's `lib` build for local use.

**Contingency (only if the fork build is genuinely infeasible after a real attempt):** the documented
sprite-sheet-in-style fallback — bake all quest pins into one sprite sheet + sprite JSON referenced by the
`BaseStyle` URL/JSON, dropping runtime `addImage`. Decided autonomously per the brief; do not ask.

### commonMain `createPinBitmap` (Compose-canvas composite)

Port Android `MapIconBitmapCreator.createPinBitmap` (androidMain) to commonMain Compose. The canonical
geometry already exists as a live composable at `ui/common/Pin.kt` (shadow 71dp, pin top=5dp, icon top=7dp,
icon size=48dp) — use it to cross-check offsets. Exact rects (dp→px via the live `Density`):

- Canvas: `size = 71.dp.toPx()`, `sizeInt = ceil(size).toInt()` (keep `ceil` — truncation clips the shadow).
- (1) **shadow** (`pin_shadow`, raster PNG): full canvas `(0,0,size,size)`.
- (2) **pin body** (`pin`, vector 52×66dp): `pinWidth = (size − 5.dp.toPx()) * 52/66`;
  `pinXOffset = size − 5.dp.toPx() − pinWidth`; rect `(pinXOffset, 5.dp.toPx(), size − 5.dp.toPx(), size)`.
- (3) **quest icon** (48dp): rect `(pinXOffset + 2.dp.toPx(), 7.dp.toPx(), +48.dp.toPx(), +48.dp.toPx())`.
- Pins are **non-SDF**. No SDF path (that is for monochrome overlay/marker icons — out of scope).

Resources (confirmed against `ui/common/Pin.kt`): `Res.drawable.pin` (vector) + `Res.drawable.pin_shadow`
(raster) + the quest's `quest.type.icon`. The androidMain `createPinBitmap` rects above are **authoritative** for
the baked bitmap; `ui/common/Pin.kt`'s `Box`-layout modifiers differ (live-layout padding, not canvas rects) and
are only a sanity cross-check.

Implementation: obtain painters via `painterResource(...)` (`@Composable`, density-aware), then rasterize with
`CanvasDrawScope().draw(density, Ltr, Canvas(ImageBitmap(sizeInt,sizeInt)), Size(size,size)) { … }` drawing the
three painters at the rects above (`translate { with(painter){ draw(targetSize) } }`).

### commonMain `PinImageRegistry` (lazy, dedup, idempotent)

A `@Composable` that registers, by name, the pin bitmap for each quest icon currently needed, mirroring
Android's `MapImages.addOnce` (lazy on first appearance, not all ~190 eagerly — per Decision 2):

```kotlin
@Composable
fun PinImageRegistry(styleState: StyleState, styleEpoch: Int, pins: Collection<Pin>) {
    val icons = remember(pins) {
        pins.mapNotNull { Res.allDrawableResources[it.icon] }.distinct()
    }
    icons.forEach { icon ->
        key(icon) {
            val bitmap = rememberPinBitmap(icon)          // composited once, remembered
            val name = remember(icon) { icon.pinImageName() }
            LaunchedEffect(styleEpoch, name, bitmap) {
                styleState.addImage(name, bitmap, sdf = false)
            }
        }
    }
}
```
- `styleEpoch`: an `Int` bumped by `MaplibreMap(onMapLoadFinished = { epoch++ })` so images re-register after a
  style (re)load (`addImage` is idempotent — re-adding the same name replaces). Wire `onMapLoadFinished` through
  `Map.kt` to a `remember { mutableStateOf(0) }` in `MapScreen`/`Map`.
- Keyed children naturally dedup per `(epoch,name)` and add lazily as new icons appear. Compositing is light
  (one vector rasterization per icon); runs in the composition/main context. `addImage` no-ops until the style
  has loaded (`styleNode?.` safe-call), then the epoch bump re-runs it.
- Add `PinImageRegistry(styleState, styleEpoch, pins)` inside `Map.kt`'s `aboveLabelsContent` next to
  `PinsLayers(pins)`. The cluster icon stays static (`image(painterResource(Res.drawable.map_pin_circle))`),
  so only per-quest pins need this.

### Icon-name convention

The name is the **compose-resources resource entry name** (e.g. `"quest_recycling"`), identical to Android's
`getResourceEntryName`. Provide one commonMain helper `fun DrawableResource.pinImageName(): String`. Primary:
strip the compose-resources id prefix (the generated id is `"drawable:quest_recycling"` →
`id.substringAfterLast(':')`); verify `DrawableResource.id` is accessible in commonMain. Fallback if not: a
reverse map built once from `Res.allDrawableResources`. The registry's name→resource direction uses
`Res.allDrawableResources[name]` (the canonical map), keeping both directions consistent.

### Upstream PR

Prepare the `StyleState.kt` patch as a clean commit + a PR description on the fork branch. Open the PR to
`maplibre/maplibre-compose` **if** `gh` is authenticated to an account that can fork; otherwise prepare
everything and flag that the maintainer push/open it. **The milestone does not depend on the PR merging** — it
runs against the local fork. (Outward-facing; do this as a non-blocking parallel deliverable.)

### M4.0 Decisions (autonomous)

1. Patch surface = **`StyleState.addImage`/`removeImage` passthrough** (smallest, least upstream-divergent). ✔
2. Build wiring = **composite build (`includeBuild` + explicit `dependencySubstitution`)** over
   `publishToMavenLocal` (auto-rebuild; no version-skew). ✔
3. Registration = **lazy `@Composable` registry keyed on the pins' distinct icons**, re-run on a style epoch. ✔
4. Name = **resource entry name via `DrawableResource.pinImageName()`**; canonical map `Res.allDrawableResources`. ✔
5. Fork version string stays **`0.13.0`** (substitute by coordinate, no rename needed). ✔

---

## M4.1 — Data download (user-initiated / visible-area path)

### Pieces

- **`ApplicationScope`** (iOS has no `Application`): add `single(named("ApplicationScope")) { CoroutineScope(SupervisorJob() + CoroutineName("Application") + Dispatchers.Default) }` to `iosModule`. Used for both the download launch and the preload launch. (`SerializeSync` Mutex is already provided by `uploadModule` — no action.)
- **`Preloader.preload()` at startup** (prerequisite — warms `Lazy<CountryBoundaries>` + `Lazy<FeatureDictionary>` that quest filters need; without it they force-load synchronously mid-download and stall). Resolve `Preloader` from Koin (factory, already resolvable) and launch on `ApplicationScope` **inside `initKoin()` behind the `koinStarted` guard** (so `MainViewController()` recreation does not re-launch it). Mirrors `StreetCompleteApplication.kt:162-165`.
- **Real `IosDownloadController`** replacing `IosDownloadControllerStub` in `IosControllersModule.kt`:
  ```kotlin
  class IosDownloadController(
      private val downloader: Downloader,
      private val scope: CoroutineScope,
  ) : DownloadController {
      private var job: Job? = null
      override fun download(bbox: BoundingBox, isUserInitiated: Boolean) {
          if (isUserInitiated) job?.cancel()           // REPLACE: priority preempts
          else if (job?.isActive == true) return        // KEEP: don't stack auto-downloads
          job = scope.launch { downloader.download(bbox, isUserInitiated) }
      }
  }
  ```
  Bind `single<DownloadController> { IosDownloadController(get(), get(named("ApplicationScope"))) }`. The shared
  `Downloader` is a `single` (from `downloadModule`) and self-guards freshness + holds the `SerializeSync` Mutex;
  the controller adds Android-parity REPLACE/KEEP priority via job tracking. `DownloadProgressSource` is the same
  `Downloader` instance — observable later for a progress indicator (defer the UI to a follow-up).
- `IosMapTilesDownloaderStub` stays no-op for M4.1 (offline tiles deferred #6072; maplibre fetches online
  tiles). The notes + map-data (quest) downloads run regardless.

### Trigger (from `MapScreen`, on camera-idle)

On camera-idle (`isCameraMoving == false`) at `zoom >= 14.0`, compute the visible SC bbox and request a
**user-initiated** download (per Decision 1 / kickoff M4.1), with a **session contains-guard** to avoid
re-download spam (user-initiated bypasses freshness, so this guard is required):

```
val scBbox = cameraState.projection?.queryVisibleBoundingBox()?.toBoundingBox() ?: return
val rect = scBbox.enclosingTilesRect(16)
if (lastDownloadedRect?.contains(rect) == true) return     // already covered this session
lastDownloadedRect = rect
downloadController.download(scBbox, isUserInitiated = true)
```
- `toBoundingBox()` already exists (`GeometryUtils.kt:30-36`). `enclosingTilesRect`/`TilesRect.contains` are
  commonMain (`TilesRect.kt`). `projection` is nullable until first layout → `?.` guard.
- The download trigger lives in `MapViewModel` (holds the injected `DownloadController`), invoked from
  `MapScreen` via a single `onViewportIdle(bbox)` entry point that also drives pins (see M4.2). Keeping the
  guard state in the VM survives recomposition.

### Verification

Log element + quest counts after download (e.g. count in `MapDataController`/`OsmQuestController` via existing
logs, or a one-off `Log.i` of `mapDataController` size). Confirm `DownloadedAreaLayer` renders the downloaded
tile hatching. Device-verify at a real location.

### M4.1 Decisions (autonomous)

1. App scope = **Koin `single(named("ApplicationScope"))` in `iosModule`**, `SupervisorJob + Dispatchers.Default`. ✔
2. Preload launch = **inside `initKoin()` behind the `koinStarted` guard**. ✔
3. REPLACE/KEEP = **full fidelity** (track + cancel prior job on user-initiated; skip if auto already active). ✔
4. Camera-idle download = **`isUserInitiated = true` + session contains-guard** (faithful to kickoff, no spam). ✔
5. `MapTilesDownloader` stays **no-op** for M4.1. ✔

---

## M4.2 — Quest pins on the map

### Separate commonMain `QuestPinsManager` (Decision 3)

New file `app/src/commonMain/.../screens/main/map2/QuestPinsManager.kt` (package `screens.main.map2`, to avoid
clashing with androidMain `screens.main.map.QuestPinsManager` on the shared Android build; zero maplibre-compose
imports). Port the Android logic verbatim, replacing only the map/lifecycle plumbing:

```kotlin
class QuestPinsManager(
    private val viewportBbox: Flow<BoundingBox?>,
    private val visibleQuestsSource: VisibleQuestsSource,
    private val questTypeRegistry: QuestTypeRegistry,
    private val questTypeOrderSource: QuestTypeOrderSource,
    private val scope: CoroutineScope,                  // VM passes viewModelScope
) {
    private val _pins = MutableStateFlow<List<Pin>>(emptyList())
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()
    fun getQuestKey(properties: Map<String, String>): QuestKey? = properties.toQuestKey()
    // start()/stop() add/remove listeners + collect viewportBbox
}
```

Ported behaviour (all collaborators already commonMain):
- **Windowing:** `TILES_ZOOM = 16`; `bbox.enclosingTilesRect(16)`; **perf guard** `if (size > 32) return`;
  contains-check vs `lastDisplayedRect`; fetch bbox = `tilesRect.asBoundingBox(16)`.
- **Fetch:** `visibleQuestsSource.getAll(bbox)` under a `Mutex`, on **`Dispatchers.Default`** (K/N has no
  `Dispatchers.IO`). **Pan-debounce:** single `updateJob`; `updateCurrentScreenArea` cancels the prior job
  before a full refetch; the `onUpdated` listener path **joins** (does not cancel) it.
- **Pin building (`createQuestPins`):** one `Pin` per entry in `quest.markerLocations` (not just `position`):
  `Pin(position = loc, icon = quest.type.icon.pinImageName(), properties = quest.key.toProperties(), order = questTypeOrders[quest.type] ?: 0)`. (The `+50` icon-order offset is added by `Pin.toGeoJsonFeature()`; pass the raw registry index as `order`.)
- **Marker properties (verbatim from androidMain) — the pin-tap round-trip contract:**
  `MARKER_QUEST_GROUP="quest_group"`, `MARKER_ELEMENT_TYPE="element_type"`, `MARKER_ELEMENT_ID="element_id"`,
  `MARKER_QUEST_TYPE="quest_type"`, `MARKER_NOTE_ID="note_id"`; groups `QUEST_GROUP_OSM="osm"`,
  `QUEST_GROUP_OSM_NOTE="osm_note"`. Port `QuestKey.toProperties()` and `Map<String,String>.toQuestKey()`
  verbatim. `OsmNoteQuest` pins flow through with `OsmNoteQuestKey` → no special-casing (Decision 4).
- **Order init:** `val list = questTypeRegistry.toMutableList(); questTypeOrderSource.sort(list)`; map each type to
  its index. (`QuestTypeRegistry : AbstractList<QuestType>`, so `toMutableList()` works.)
- **Cache + retention:** `questsInView: MutableMap<QuestKey, List<Pin>>` under a `Mutex`. On a new bbox, evict
  only entries with exactly one pin OR with no pin inside the new bbox; keep multi-pin quests whose center is
  off-screen but with ≥1 pin in view (prevents flicker on long ways). `updateQuestPins` (incremental
  add/remove) early-returns if `lastDisplayedRect == null` and short-circuits when nothing in view changed.
- **Listeners:** `VisibleQuestsSource.Listener` (`onUpdated` → incremental; `onInvalidated` → clear
  `lastDisplayedRect` + refetch) and `QuestTypeOrderSource.Listener` (re-sort + invalidate). Both are commonMain.
- Concurrency primitives: `kotlinx.atomicfu.locks.ReentrantLock` (order map) + `kotlinx.coroutines.sync.Mutex`
  (cache, source access) — both multiplatform, reuse as-is.

### Viewport + VM integration

- `MapViewModel` gains constructor deps `visibleQuestsSource, questTypeRegistry, questTypeOrderSource,
  downloadController` (Koin: update `MapModule.kt` `viewModel { MapViewModel(get(), …) }`).
- The VM owns `private val viewport = MutableStateFlow<BoundingBox?>(null)` and constructs
  `questPinsManager = QuestPinsManager(viewport, visibleQuestsSource, questTypeRegistry, questTypeOrderSource, viewModelScope)`, calls `questPinsManager.start()`, and collects `questPinsManager.pins` into `setPins(...)` on
  `viewModelScope` (keeps `_pins` as the established sink; `MapScreen` unchanged). `getQuestKey` is exposed via
  the VM for M4.3.
- The VM exposes `fun onViewportIdle(bbox: BoundingBox?)` → updates `viewport.value = bbox` (drives pins) **and**
  triggers the M4.1 download (contains-guard). One entry point, two consumers.
- `MapScreen` adds a third `LaunchedEffect(cameraState)` mirroring the two existing snapshotFlow blocks:
  ```kotlin
  snapshotFlow { Triple(cameraState.isCameraMoving, cameraState.position.zoom, cameraState.position.target) }
      .filterNot { it.first }                         // camera idle
      .filter { it.second >= 14.0 }                   // zoom gate (emit only at >=14; retain below)
      .distinctUntilChanged()
      .collect { viewModel.onViewportIdle(cameraState.projection?.queryVisibleBoundingBox()?.toBoundingBox()) }
  ```
  **Read `projection` in the `collect`, not the `snapshotFlow` producer** — `CameraProjection` is not Compose
  snapshot state, so changes won't retrigger; trigger on `position`/`isCameraMoving` (which are state). Emitting
  only at `zoom >= 14` and retaining below mirrors Android (clusters still render at z13–14 from the last fetch;
  `CLUSTER_MIN/MAX_ZOOM = 13/14`).

### M4.2 Decisions (autonomous)

1. Manager package = **`screens.main.map2`** (avoids the androidMain same-FQN clash; converges later). ✔
2. Output = **manager exposes `StateFlow<List<Pin>>`; VM collects → `setPins`** (VM stays the single sink). ✔
3. Viewport = **`MutableStateFlow<BoundingBox?>` owned by the VM**, fed from `MapScreen` camera-idle; manager
   takes it as `Flow<BoundingBox?>` per Decision 3's signature, plus an explicit `CoroutineScope` (viewModelScope). ✔
4. Lifecycle = **`start()/stop()` tied to the VM** (no Fragment lifecycle); scope = `viewModelScope`. ✔
5. Dispatcher for `getAll` = **`Dispatchers.Default`** (no `Dispatchers.IO` on K/N). ✔
6. Keep the full Android windowing/cache/retention/perf-guard fidelity (not a simplified refetch). ✔

---

## M4.3 — Pin selection (stretch, display-only)

If M4.0–M4.2 are green and time allows: wire `PinsLayers(onClickPin = …)` → read feature props →
`viewModel.getQuestKey(props)` → set a `selectedPins` StateFlow → render `SelectedPinsLayer(icon, positions)`
in `Map.kt`'s `aboveLabelsContent` (uses the same `image(feature["icon-image"])` registry). **No answer form**
(upstream-WIP-blocked). Out of scope if anything above is shaky.

---

## Cross-cutting threading & gotchas

- `cameraState.projection` is **null until first layout** → always `?.` and skip emission.
- SC `BoundingBox.init` requires `min.latitude <= max.latitude`; `toBoundingBox()` does no clamping. A
  degenerate viewport could throw — acceptable for M4 (guard later if observed). No 180th-meridian split.
- `LatLon(lat, lon)` vs spatialk `Position(lon, lat)` order — handled by the existing converters; don't re-derive.
- maplibre-compose 0.13.0 rejects **negative** `iconPadding` (Compose `PaddingValues.Absolute`); PinsLayers
  already clamps to 0 — don't reintroduce negatives (M3b.2e gotcha).
- `setPins` rebuilds the `FeatureCollection` every recomposition (PinsLayers.kt:52 TODO). Acceptable at M4 pin
  counts; `remember(pins)` memoization is a noted follow-up, not a blocker.
- Run all source reads off the main thread (`Dispatchers.Default`); `addImage` and Compose state on main.
- No `NSLog`/`%@` from Kotlin/Native — use `Log`/`println` (existing `IosLogger`).

## Verification model (no unit-test gate; Android APK / iOS commonTest are upstream-WIP-red)

1. **Link gate:** `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (after the composite-build swap, this also proves the fork resolves).
2. **Simulator:** `rm -rf build/ios` first; build + install + launch on sim `5B7C16A4-2C11-4E0C-9549-42F7AC648438`,
   bundle `de.westnordost.streetcomplete.ios`; `xcrun simctl location` to simulate GPS;
   `xcrun simctl privacy booted grant location <bundle>`; screenshot. M4.0 gate: a real quest icon renders on a
   pin (synthetic pin if needed). M4.1 gate: DB counts logged + `DownloadedAreaLayer` tile. M4.2 gate: per-type pins.
3. **Device (end-to-end):** devicectl `83B473E9-A697-567F-BDF4-27B28D49D0B5`, hardware UDID
   `00008140-000228491A33001C`, TEAM_ID `2DPUG448BC`, `-allowProvisioningUpdates` (renews the expired free
   profile). Gate: open map → data downloads → quest pins with correct icons appear.
4. **Commit per increment** (rebase only, no merge commits; Co-Authored-By trailer). Push only if asked.

## Risks / contingencies

- **Fork build infeasible** → sprite-sheet contingency (autonomous pivot; only escalate if BOTH fail).
- **Composite-build sibling-module/plugin config failure** → trim desktop/js targets in the fork; validate
  `:lib:maplibre-compose` builds standalone before wiring.
- **`addImage` timing** → epoch-bump on `onMapLoadFinished` re-registers after style load/reload.
- **Pin flood at low zoom** → z16 perf guard (≤32 tiles) + zoom≥14 emit gate.
- **First-download memory** (`MapDataApiParser` under iOS pressure for dense bboxes) → start at a normal city
  zoom; observe.

## File map (new / changed)

- **Fork:** `maplibre-compose-fork/lib/maplibre-compose/.../style/StyleState.kt` (+2 methods).
- **Build:** `settings.gradle.kts` (`includeBuild` + substitution).
- **M4.0:** new `commonMain/.../map2/PinImage.kt` (`createPinBitmap`/`rememberPinBitmap`/`DrawableResource.pinImageName`/`PinImageRegistry`); `Map.kt` (+`onMapLoadFinished` epoch, +`PinImageRegistry` call); `MapScreen.kt` (epoch state).
- **M4.1:** `iosModule`/`IosModule.kt` (+`ApplicationScope`); `IosControllersModule.kt` (real `IosDownloadController`); `InitKoin.kt` (preload launch); `MapViewModel.kt`/`MapScreen.kt` (idle download trigger).
- **M4.2:** new `commonMain/.../map2/QuestPinsManager.kt`; `MapViewModel.kt` (+deps, manager, viewport, `onViewportIdle`, collect→setPins); `MapModule.kt` (binding); `MapScreen.kt` (viewport snapshotFlow).
