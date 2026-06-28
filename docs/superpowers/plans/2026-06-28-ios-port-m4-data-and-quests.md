# iOS-Port M4 — Real OSM data + quest pins Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open the iOS map → data downloads for the visible area → quest pins with correct per-quest-type icons appear (incl. `OsmNoteQuest`), device-verified.

**Architecture:** Drive the already-commonMain download→DB→quest pipeline from iOS. M4.0 registers per-quest-type pin bitmaps by name via a 2-method patch to a local maplibre-compose fork (composite build). M4.1 adds a real `IosDownloadController` + `Preloader` at startup + a camera-idle download trigger. M4.2 ports the Android `QuestPinsManager` to a separate commonMain class driven by a viewport `Flow`, feeding `MapViewModel.setPins`.

**Tech Stack:** Kotlin Multiplatform 2.3.20, Compose 1.10.3, maplibre-compose 0.13.0 (local fork), Koin 4.2.1, spmForKmp 1.9.1, Gradle 8.14, JDK 21.

**Spec:** `docs/superpowers/specs/2026-06-28-ios-port-m4-data-and-quests-design.md`

## Global Constraints

- Work directly on `master` (this fork's convention). No merge commits — rebase only; `git pull --rebase`.
- Commit per task with the trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. Push only if the maintainer asks.
- No `NSLog`/`%@` from Kotlin/Native — use `Log`/`println` (the existing `IosLogger`).
- K/N interop may need `@OptIn(ExperimentalForeignApi::class)` / `@OptIn(BetaInteropApi::class)`.
- Run source reads off the main thread (`Dispatchers.IO` — available in this commonMain build via `import kotlinx.coroutines.IO`); Compose state + `styleState.addImage` on main.
- maplibre-compose 0.13.0 rejects **negative** `iconPadding` (Compose `PaddingValues.Absolute`) — never reintroduce negatives.
- **Verification model (no unit-test gate; Android APK + iOS commonTest are upstream-WIP-red, NOT gates):**
  - **Link gate:** `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64` → `BUILD SUCCESSFUL`.
  - **Simulator:** `rm -rf build/ios` first; `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,id=5B7C16A4-2C11-4E0C-9549-42F7AC648438' -derivedDataPath build/ios build` → `xcrun simctl install booted <app>` → `xcrun simctl launch booted de.westnordost.streetcomplete.ios` → screenshot. GPS: `xcrun simctl location booted set <lat>,<lon>`; permission: `xcrun simctl privacy booted grant location de.westnordost.streetcomplete.ios`.
  - **Device:** devicectl `83B473E9-A697-567F-BDF4-27B28D49D0B5`, hardware UDID `00008140-000228491A33001C`, TEAM_ID `2DPUG448BC`, `-allowProvisioningUpdates` (renews the expired free profile). `xcodebuild … -destination 'platform=iOS,id=00008140-000228491A33001C' -derivedDataPath build/ios-device -allowProvisioningUpdates build`.
  - **Iteration trap:** Xcode does NOT relink when only the static framework's contents change → `rm -rf build/ios` (or `build/ios-device`) before every Xcode build.

---

## File structure

| File | Responsibility | Task |
|---|---|---|
| `../maplibre-compose-fork/.../style/StyleState.kt` | +`addImage`/`removeImage` passthrough (the patch) | 0.1 |
| `settings.gradle.kts` | composite-build `includeBuild` + `dependencySubstitution` | 0.2 |
| `app/src/commonMain/.../screens/main/map2/PinImage.kt` (new) | `createPinBitmap`-equivalent, `rememberPinBitmap`, `DrawableResource.pinImageName`, `PinImageRegistry` composable | 0.3 |
| `app/src/commonMain/.../screens/main/map2/Map.kt` | +`styleEpoch` via `onMapLoadFinished`, +`PinImageRegistry` call | 0.3 |
| `app/src/iosMain/.../IosModule.kt` | +`ApplicationScope` Koin binding | 1.1 |
| `app/src/iosMain/.../di/InitKoin.kt` | +`Preloader.preload()` launch behind the once-guard | 1.1 |
| `app/src/iosMain/.../IosControllersModule.kt` | real `IosDownloadController` (replace stub) | 1.2 |
| `app/src/commonMain/.../screens/main/map/MapViewModel.kt` | +`downloadController` dep, `onViewportIdle` (download), then +quest-pins manager + viewport flow | 1.3, 2.2 |
| `app/src/commonMain/.../screens/main/map/MapScreen.kt` | +camera-idle viewport snapshotFlow → `viewModel.onViewportIdle` | 1.3 |
| `app/src/commonMain/.../screens/main/map2/QuestPinsManager.kt` (new) | commonMain quest-pin producer (port) | 2.1 |
| `app/src/commonMain/.../screens/main/map/MapModule.kt` | extend `MapViewModel` Koin binding | 2.2 |

---

# M4.0 — Quest-pin icon registration

## Task 0.1: Fork maplibre-compose + patch `StyleState`; validate standalone iOS compile

**Files:**
- Create: `/Users/piotr/git/maplibre-compose-fork/` (clone)
- Modify: `/Users/piotr/git/maplibre-compose-fork/lib/maplibre-compose/src/commonMain/kotlin/org/maplibre/compose/style/StyleState.kt`

**Interfaces:**
- Produces: `public fun StyleState.addImage(id: String, image: ImageBitmap, sdf: Boolean = false, resizeOptions: ImageResizeOptions? = null)` and `public fun StyleState.removeImage(id: String)` on the published `org.maplibre.compose:maplibre-compose` artifact.

- [ ] **Step 1: Clone the upstream repo at the v0.13.0 tag**

```bash
git clone https://github.com/maplibre/maplibre-compose /Users/piotr/git/maplibre-compose-fork
git -C /Users/piotr/git/maplibre-compose-fork checkout -b sc-addimage-patch v0.13.0
```
Expected: HEAD at tag `v0.13.0` (commit `dc4781959…`). If the tag is missing, list tags: `git -C /Users/piotr/git/maplibre-compose-fork tag | grep 0.13`.

- [ ] **Step 2: Confirm the module path + StyleState location**

```bash
cat /Users/piotr/git/maplibre-compose-fork/settings.gradle.kts
find /Users/piotr/git/maplibre-compose-fork -name StyleState.kt -path '*commonMain*'
```
Expected: a module named `:lib:maplibre-compose` (note the actual path — it is the substitution target in Task 0.2). Note the exact `StyleState.kt` path.

- [ ] **Step 3: Read StyleState.kt and locate the private `styleNode`**

Read the file. Confirm it has `private var styleNode: StyleNode?` and imports for `ImageBitmap` + `ImageResizeOptions` (add the imports if absent: `androidx.compose.ui.graphics.ImageBitmap`, `org.maplibre.compose.util.ImageResizeOptions`).

- [ ] **Step 4: Add the two passthrough methods**

Inside the `StyleState` class body (after the existing `sources` getter), add:
```kotlin
    /** Register a named image into the live style so data-driven `image(<name>)` lookups resolve it.
     *  Added by StreetComplete (PR upstream pending). No-ops until the style has loaded. */
    public fun addImage(
        id: String,
        image: androidx.compose.ui.graphics.ImageBitmap,
        sdf: Boolean = false,
        resizeOptions: org.maplibre.compose.util.ImageResizeOptions? = null,
    ) {
        styleNode?.style?.addImage(id, image, sdf, resizeOptions)
    }

    public fun removeImage(id: String) {
        styleNode?.style?.removeImage(id)
    }
```
(Prefer top-of-file imports over FQNs if the file style uses imports.)

- [ ] **Step 5: Align Kotlin/Compose to avoid build surprises (only if the standalone build fails on version)**

A composite build recompiles from source, so 2.3.21-vs-2.3.20 is normally fine. Only if Step 6 fails with a Kotlin/Compose version error, pin the fork's `gradle/libs.versions.toml` (or its build files) to Kotlin `2.3.20` + Compose `1.10.3`.

- [ ] **Step 6: Validate the fork's iOS-simulator compile standalone (feasibility gate)**

```bash
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home /Users/piotr/git/maplibre-compose-fork/gradlew -p /Users/piotr/git/maplibre-compose-fork :lib:maplibre-compose:compileKotlinIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL`. This exercises the fork's own spmForKmp/cinterop against `maplibre-gl-native-distribution`.
**If it fails** because sibling desktop/js modules (`:lib:maplibre-native-bindings`, `:lib:maplibre-js-bindings`) or their plugins won't configure on this machine: trim the fork's `lib/maplibre-compose/build.gradle.kts` to iOS targets only for local use, and/or remove the offending `include(...)` from the fork's `settings.gradle.kts`. **If both that and the spec's sprite-sheet contingency are infeasible, STOP and escalate** (per the task brief — this is one of the few real blockers).

- [ ] **Step 7: Commit the patch on the fork branch**

```bash
git -C /Users/piotr/git/maplibre-compose-fork add -A
git -C /Users/piotr/git/maplibre-compose-fork commit -m "Expose public StyleState.addImage/removeImage for data-driven named images

Mirrors the already-public Style.addImage/removeImage (only the StyleState.styleNode
handle was private), enabling iconImage = image(<feature-property-name>) to resolve
runtime-registered sprites. Needed by StreetComplete's iOS quest pins."
```

---

## Task 0.2: Composite-build substitution + StreetComplete link gate against the fork

**Files:**
- Modify: `settings.gradle.kts` (StreetComplete root)

**Interfaces:**
- Consumes: the fork's `:lib:maplibre-compose` project + `StyleState.addImage` (Task 0.1).
- Produces: `:app` compiles against the patched fork; `org.maplibre.compose.style.StyleState.addImage(...)` is callable from commonMain.

- [ ] **Step 1: Add `includeBuild` with explicit substitution**

In `settings.gradle.kts`, **after** the `dependencyResolutionManagement { … }` block and **before** `include(":app")`, add (use the actual module path confirmed in Task 0.1 Step 2 — likely `:lib:maplibre-compose`):
```kotlin
includeBuild("../maplibre-compose-fork") {
    dependencySubstitution {
        substitute(module("org.maplibre.compose:maplibre-compose"))
            .using(project(":lib:maplibre-compose"))
    }
}
```

- [ ] **Step 2: Prove the substitution resolves to the included build**

```bash
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:dependencies --configuration iosSimulatorArm64CompileKlibraries 2>&1 | grep -i maplibre-compose | head
```
Expected: the maplibre-compose entry shows it resolved from the composite project (e.g. `-> project :lib:maplibre-compose`), not the Maven coordinate.

- [ ] **Step 3: Add a temporary compile probe for `addImage`, then link**

Temporarily, in `Map.kt`'s `Map()` body (top), add `styleState.addImage("__probe__", ImageBitmap(1, 1))` (import `androidx.compose.ui.graphics.ImageBitmap`). Then:
```bash
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL` (proves `StyleState.addImage` is public + resolved through the fork). Remove the probe line after.

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts
git commit -m "M4.0: substitute maplibre-compose with a local patched fork (composite build)

includeBuild + dependencySubstitution redirects org.maplibre.compose:maplibre-compose
to ../maplibre-compose-fork (StyleState.addImage patch). Recompiles from source, so
the fork's Kotlin version is independent of the app's. Native MapLibre.framework still
comes from :app's own spmForKmp wiring (orthogonal)."
```

---

## Task 0.3: commonMain pin-bitmap compositing + lazy named registration; verify a real quest icon renders

**Files:**
- Create: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/PinImage.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/Map.kt`
- Modify (temporary, removed in Task 2.2): `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt` (synthetic pin for verification)

**Interfaces:**
- Consumes: `StyleState.addImage` (0.2); `Pin` (`screens.main.map2.layers.Pin`, `icon: String`); `Res.allDrawableResources`, `Res.drawable.pin`, `Res.drawable.pin_shadow`.
- Produces: `fun DrawableResource.pinImageName(): String`; `@Composable fun PinImageRegistry(styleState: StyleState, styleEpoch: Int, pins: Collection<Pin>)`; `@Composable fun rememberPinBitmap(icon: DrawableResource): ImageBitmap`.

- [ ] **Step 1: Create `PinImage.kt` with the compositing + name helper + registry**

```kotlin
package de.westnordost.streetcomplete.screens.main.map2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.allDrawableResources
import de.westnordost.streetcomplete.resources.pin
import de.westnordost.streetcomplete.resources.pin_shadow
import de.westnordost.streetcomplete.screens.main.map2.layers.Pin
import kotlin.math.ceil
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.maplibre.compose.style.StyleState

/** Single source of truth for image NAME <-> DrawableResource, backed by the generated
 *  resource map. The manager goes resource -> name; the registry goes name -> resource;
 *  round-trip is guaranteed because both use this map. */
private val drawableNamesByResource: Map<DrawableResource, String> by lazy {
    Res.allDrawableResources.entries.associate { (name, resource) -> resource to name }
}

/** The image name a pin uses for this quest icon — the compose-resources entry name
 *  (e.g. "quest_recycling"), matching Android's getResourceEntryName. */
fun DrawableResource.pinImageName(): String =
    drawableNamesByResource[this] ?: error("Drawable not in Res.allDrawableResources: $this")

/** Registers, by name, the composited pin bitmap for every quest icon currently in [pins].
 *  Lazy (only icons actually present), deduped per (styleEpoch, name), re-run on style reload. */
@Composable
fun PinImageRegistry(styleState: StyleState, styleEpoch: Int, pins: Collection<Pin>) {
    val iconsByName: Map<String, DrawableResource> = remember(pins) {
        pins.mapNotNull { p -> Res.allDrawableResources[p.icon]?.let { p.icon to it } }.toMap()
    }
    iconsByName.forEach { (name, resource) ->
        key(name) {
            val bitmap = rememberPinBitmap(resource)
            LaunchedEffect(styleEpoch, name, bitmap) {
                styleState.addImage(name, bitmap, sdf = false)
            }
        }
    }
}

/** Compose port of androidMain MapIconBitmapCreator.createPinBitmap: shadow + pin body + quest icon
 *  composited into one 71dp-square bitmap, non-SDF. Geometry copied verbatim from the Android rects. */
@Composable
fun rememberPinBitmap(icon: DrawableResource): ImageBitmap {
    val density = LocalDensity.current
    val shadow = painterResource(Res.drawable.pin_shadow)
    val pin = painterResource(Res.drawable.pin)
    val iconPainter = painterResource(icon)
    return remember(icon, density) { drawPinBitmap(density, shadow, pin, iconPainter) }
}

private fun drawPinBitmap(density: Density, shadow: Painter, pin: Painter, icon: Painter): ImageBitmap {
    val size = with(density) { 71.dp.toPx() }
    val sizeInt = ceil(size).toInt()
    val iconSize = with(density) { 48.dp.toPx() }
    val iconPinOffset = with(density) { 2.dp.toPx() }
    val pinTopRightPadding = with(density) { 5.dp.toPx() }
    // pin.xml intrinsic ratio is 52:66 (density cancels in the ratio)
    val pinWidth = (size - pinTopRightPadding) * (pin.intrinsicSize.width / pin.intrinsicSize.height)
    val pinXOffset = size - pinTopRightPadding - pinWidth

    val bitmap = ImageBitmap(sizeInt, sizeInt)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(size, size)) {
        with(shadow) { draw(Size(size, size)) }
        translate(pinXOffset, pinTopRightPadding) {
            with(pin) { draw(Size(pinWidth, size - pinTopRightPadding)) }
        }
        translate(pinXOffset + iconPinOffset, pinTopRightPadding + iconPinOffset) {
            with(icon) { draw(Size(iconSize, iconSize)) }
        }
    }
    return bitmap
}
```
> If `pin`/`pin_shadow` are not valid `Res.drawable.*` accessors, run `ls app/src/commonMain/composeResources/drawable | grep -i pin` and use the exact names `ui/common/Pin.kt` imports (it uses `Res.drawable.pin` + `Res.drawable.pin_shadow`).
> Fallback if `Res.allDrawableResources` is unavailable or `DrawableResource` is identity-keyed (icons stay blank despite registration): derive the name from the resource id by stripping the `"drawable:"` prefix.

- [ ] **Step 2: Wire `Map.kt` — style epoch + registry**

In `Map.kt`, add imports `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`. In `Map()` body, before `MaplibreMap(`:
```kotlin
    var styleEpoch by remember { mutableStateOf(0) }
```
Add `onMapLoadFinished = { styleEpoch++ },` to the `MaplibreMap(...)` argument list. After the `MaplibreMap(...) { … }` block (still inside `Map()`), add:
```kotlin
    PinImageRegistry(styleState, styleEpoch, pins)
```

- [ ] **Step 3: Add a temporary synthetic pin to verify icon rendering**

In `MapViewModel.init { … }` (end of the block), add a temporary line (removed in Task 2.2):
```kotlin
        // TEMP (M4.0 verification — removed in M4.2): a synthetic pin to confirm icon registration.
        _pins.value = listOf(Pin(LatLon(52.5163, 13.3777), "quest_recycling"))
```
(import `de.westnordost.streetcomplete.data.osm.mapdata.LatLon` if not present.)

- [ ] **Step 4: Link gate**

```bash
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Simulator verify the quest icon renders on the pin**

```bash
rm -rf build/ios
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,id=5B7C16A4-2C11-4E0C-9549-42F7AC648438' -derivedDataPath build/ios build
xcrun simctl install booted "$(find build/ios -name 'StreetComplete.app' -type d | head -1)"
xcrun simctl launch booted de.westnordost.streetcomplete.ios
sleep 4 && xcrun simctl io booted screenshot /private/tmp/claude-504/-Users-piotr-git-StreetComplete/01bf8448-8bba-45f0-9d13-e42fc2d74448/scratchpad/m40-pin.png
```
Open the map (menu → Map), pan to Berlin (~52.516, 13.378). Expected: a pin with the **recycling icon** (not a blank pin/box) at the synthetic location. Confirm via the screenshot. (If the icon is blank, check the name round-trip and the registry `styleEpoch` re-run.)

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/PinImage.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/Map.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt
git commit -m "M4.0: commonMain pin-bitmap compositing + lazy named-image registry

Port of MapIconBitmapCreator.createPinBitmap to a Compose canvas (shadow+pin+quest
icon, 71dp, non-SDF). PinImageRegistry registers each visible quest icon by name via
the patched StyleState.addImage, lazily + re-run on style reload (epoch). Verified on
the simulator with a synthetic recycling pin."
```

---

## Task 0.4: Prepare + open the upstream maplibre-compose PR (non-blocking)

**Files:** none in StreetComplete.

- [ ] **Step 1: Check `gh` auth + fork capability**

```bash
gh auth status 2>&1 | head
```
If authenticated to an account that can fork `maplibre/maplibre-compose`, proceed; otherwise prepare the branch/description and note that the maintainer should push + open it.

- [ ] **Step 2: Push the patch branch + open the PR**

```bash
gh repo fork maplibre/maplibre-compose --clone=false --remote=false 2>&1 | tail -2 || true
git -C /Users/piotr/git/maplibre-compose-fork push -u <your-fork-remote> sc-addimage-patch
gh pr create --repo maplibre/maplibre-compose --head <account>:sc-addimage-patch --base main \
  --title "Expose public StyleState.addImage/removeImage for data-driven named images" \
  --body "$(cat <<'BODY'
StyleState has no public way to register a named image into the live style, so
data-driven `iconImage = image(<feature-property>)` cannot resolve runtime sprites.
`Style.addImage`/`removeImage` are already implemented and public on the (internal)
Style interface; only the `StyleState.styleNode` handle is private. This adds two
thin public passthroughs on StyleState. No new types, no visibility changes to Style.

Use case: per-feature icon names resolved from a GeoJSON property (e.g. quest pins).
BODY
)"
```
Expected: a PR URL, or a clear note that pushing/opening needs the maintainer. **This does not gate the milestone** (the app runs against the local fork regardless).

---

# M4.1 — Data download (user-initiated / visible-area path)

## Task 1.1: `ApplicationScope` + `Preloader.preload()` at startup

**Files:**
- Modify: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/IosModule.kt`
- Modify: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt`

**Interfaces:**
- Produces: Koin `single(named("ApplicationScope")) { CoroutineScope }`; `Preloader.preload()` runs once at startup.
- Consumes: `Preloader` (Koin factory, `osmApiModule`); `CountryBoundaries`/`FeatureDictionary` lazies.

- [ ] **Step 1: Add the app scope to `iosModule`**

In `IosModule.kt`, add imports `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.SupervisorJob`, `kotlinx.coroutines.CoroutineName`, `kotlinx.coroutines.Dispatchers`, `org.koin.core.qualifier.named` (already imported). Inside `module { … }`:
```kotlin
    // Long-lived app scope (iOS has no Application); used for downloads + preloading.
    single(named("ApplicationScope")) {
        CoroutineScope(SupervisorJob() + CoroutineName("Application") + Dispatchers.Default)
    }
```

- [ ] **Step 2: Launch preload once in `initKoin()`**

In `InitKoin.kt`, add imports `de.westnordost.streetcomplete.data.Preloader`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.launch`, `org.koin.core.qualifier.named`, `org.koin.mp.KoinPlatform`. At the **end** of `initKoin()` (after the `startKoin { … }` block, still inside the function so it sits behind the `koinStarted` once-guard):
```kotlin
    // Warm CountryBoundaries + FeatureDictionary before any download/quest evaluation,
    // mirroring StreetCompleteApplication.onCreate. Behind the koinStarted guard so a
    // UIViewController recreation does not re-launch it.
    val koin = KoinPlatform.getKoin()
    koin.get<CoroutineScope>(named("ApplicationScope")).launch {
        koin.get<Preloader>().preload()
    }
```

- [ ] **Step 3: Link gate**

```bash
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Simulator verify the preload logs**

Build/install/launch (as Task 0.3 Step 5). Expected in the console (`xcrun simctl spawn booted log stream --predicate 'process == "StreetComplete"'` or the launch console): `Loaded country boundaries in …s`, `Loaded features dictionary in …s`, `Preloading data took …s`.

- [ ] **Step 5: Commit**

```bash
git add app/src/iosMain/kotlin/de/westnordost/streetcomplete/IosModule.kt \
        app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt
git commit -m "M4.1: add iOS ApplicationScope + Preloader.preload() at startup

Warms CountryBoundaries + FeatureDictionary before any download/quest eval (else they
force-load synchronously mid-download). Behind the koinStarted once-guard."
```

---

## Task 1.2: Real `IosDownloadController` (replace the stub)

**Files:**
- Modify: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/IosControllersModule.kt`

**Interfaces:**
- Consumes: `Downloader` (Koin single, `downloadModule`); `ApplicationScope` (1.1).
- Produces: `IosDownloadController : DownloadController` bound as `single<DownloadController>`, REPLACE/KEEP priority.

- [ ] **Step 1: Replace the stub with a real controller**

In `IosControllersModule.kt`, change the `DownloadController` binding and the stub class:
```kotlin
    single<DownloadController> {
        IosDownloadController(get(), get(org.koin.core.qualifier.named("ApplicationScope")))
    }
```
Remove `IosDownloadControllerStub` and add (imports: `de.westnordost.streetcomplete.data.download.Downloader`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Job`, `kotlinx.coroutines.launch`):
```kotlin
/** iOS DownloadController: bridges the non-suspend interface to the shared suspend Downloader
 *  on the app scope. Mirrors Android REPLACE/KEEP: a user-initiated (priority) download cancels
 *  the previous one; a non-user-initiated one is skipped while another is already running.
 *  The shared Downloader self-guards freshness and holds the SerializeSync mutex. */
private class IosDownloadController(
    private val downloader: Downloader,
    private val scope: CoroutineScope,
) : DownloadController {
    private var job: Job? = null
    override fun download(bbox: BoundingBox, isUserInitiated: Boolean) {
        if (isUserInitiated) job?.cancel()
        else if (job?.isActive == true) return
        job = scope.launch { downloader.download(bbox, isUserInitiated) }
    }
}
```

- [ ] **Step 2: Link gate**

```bash
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/iosMain/kotlin/de/westnordost/streetcomplete/IosControllersModule.kt
git commit -m "M4.1: real IosDownloadController driving the shared Downloader

Replaces the no-op stub; launches downloader.download on the ApplicationScope with
Android-parity REPLACE/KEEP priority (user-initiated cancels prior; auto skips if busy)."
```

---

## Task 1.3: Camera-idle download trigger (MapViewModel + MapScreen)

**Files:**
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt`

**Interfaces:**
- Consumes: `DownloadController` (Koin); `cameraState.projection?.queryVisibleBoundingBox()`; `GeometryUtils.toBoundingBox()`; `BoundingBox.enclosingTilesRect`/`TilesRect.contains`.
- Produces: `MapViewModel.onViewportIdle(bbox: BoundingBox?)` (triggers a guarded user-initiated download). Extended in Task 2.2 to also drive pins.

- [ ] **Step 1: Inject `DownloadController` + add the trigger to `MapViewModel`**

In `MapViewModel.kt`, add the constructor param (after `prefs`):
```kotlin
    private val downloadController: DownloadController,
```
Add imports: `de.westnordost.streetcomplete.data.download.DownloadController`, `de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox`, `de.westnordost.streetcomplete.data.download.tiles.TilesRect`, `de.westnordost.streetcomplete.data.download.tiles.enclosingTilesRect`. Add a field + method:
```kotlin
    // Last area requested for download this session — avoid re-downloading a contained area
    // (user-initiated downloads bypass the freshness check, so guard here).
    private var lastDownloadedRect: TilesRect? = null

    /** Called on camera-idle at zoom >= 14. Triggers a user-initiated download of the visible
     *  area unless that area is already covered this session. (Extended in M4.2 to drive pins.) */
    fun onViewportIdle(bbox: BoundingBox?) {
        if (bbox == null) return
        val rect = bbox.enclosingTilesRect(16)
        if (lastDownloadedRect?.contains(rect) == true) return
        lastDownloadedRect = rect
        downloadController.download(bbox, isUserInitiated = true)
    }
```

- [ ] **Step 2: Update the Koin binding**

In `MapModule.kt`:
```kotlin
    viewModel { MapViewModel(get(), get(), get(), get(), get()) }
```

- [ ] **Step 3: Wire the camera-idle snapshotFlow in `MapScreen`**

In `MapScreen.kt`, add imports `kotlinx.coroutines.flow.filter`, `de.westnordost.streetcomplete.screens.main.map2.toBoundingBox`. Add a third `LaunchedEffect(cameraState)` after the existing two:
```kotlin
    // Trigger a download (and, from M4.2, quest-pin fetch) of the visible area on camera-idle at
    // zoom >= 14. Read projection in the collect (it is not Compose snapshot state); trigger on
    // position/isCameraMoving (which are). Emit only at zoom >= 14; retain below so clusters stay.
    LaunchedEffect(cameraState) {
        snapshotFlow {
            Triple(cameraState.isCameraMoving, cameraState.position.zoom, cameraState.position.target)
        }
            .filterNot { it.first }
            .filter { it.second >= 14.0 }
            .distinctUntilChanged()
            .collect {
                viewModel.onViewportIdle(
                    cameraState.projection?.queryVisibleBoundingBox()?.toBoundingBox()
                )
            }
    }
```

- [ ] **Step 4: Link gate**

```bash
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Simulator verify a download lands + the downloaded-area tile renders**

Build/install/launch. Set GPS + open the map at a real city (e.g. Berlin): `xcrun simctl location booted set 52.5163,13.3777`. Zoom to ~16–17, let the camera settle. Expected:
- Console logs from `Downloader` (`TAG = "Download"`): download start/finish.
- The **`DownloadedAreaLayer`** hatching now bounds the downloaded tile (the map outside the tile is hatched; inside is clear). Screenshot it.
- Add a one-off count log to confirm data landed: temporarily, after the download, the existing `Downloader` already logs; if needed add `Log.i("M4.1", "...")` of `mapDataController` size and remove after. (Do not leave debug logs in the commit.)

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapScreen.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt
git commit -m "M4.1: camera-idle user-initiated download of the visible area

MapScreen emits the visible bbox on camera-idle at zoom>=14; MapViewModel.onViewportIdle
triggers a user-initiated download with a session contains-guard (no re-download spam).
Verified: data lands in the DB + DownloadedAreaLayer bounds the tile."
```

---

# M4.2 — Quest pins on the map

## Task 2.1: commonMain `QuestPinsManager` (port)

**Files:**
- Create: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/QuestPinsManager.kt`

**Interfaces:**
- Consumes: `VisibleQuestsSource` (`getAll`, `Listener`, `addListener`/`removeListener`); `QuestTypeRegistry`; `QuestTypeOrderSource`; `DrawableResource.pinImageName()` (0.3); `BoundingBox.enclosingTilesRect`/`TilesRect.asBoundingBox`/`contains`/`size`; `BoundingBox.contains(LatLon)`; `Pin` (`map2.layers.Pin`).
- Produces: `class QuestPinsManager(viewportBbox: Flow<BoundingBox?>, visibleQuestsSource, questTypeRegistry, questTypeOrderSource, scope)` with `val pins: StateFlow<List<Pin>>`, `fun start()`, `fun stop()`, `fun getQuestKey(properties: Map<String, String>): QuestKey?`.

- [ ] **Step 1: Create the manager (faithful port of androidMain QuestPinsManager)**

```kotlin
package de.westnordost.streetcomplete.screens.main.map2

import de.westnordost.streetcomplete.data.download.tiles.TilesRect
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilesRect
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderSource
import de.westnordost.streetcomplete.screens.main.map2.layers.Pin
import de.westnordost.streetcomplete.util.math.contains
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** commonMain quest-pin producer (port of androidMain QuestPinsManager): listens to
 *  VisibleQuestsSource + a viewport bbox flow, windows by z16 tiles, fetches visible quests off
 *  the main thread, builds Pins (incl. OsmNoteQuest), and exposes them as a StateFlow. Imports
 *  ZERO maplibre-compose types. */
class QuestPinsManager(
    private val viewportBbox: Flow<BoundingBox?>,
    private val visibleQuestsSource: VisibleQuestsSource,
    private val questTypeRegistry: QuestTypeRegistry,
    private val questTypeOrderSource: QuestTypeOrderSource,
    private val scope: CoroutineScope,
) {
    private val _pins = MutableStateFlow<List<Pin>>(emptyList())
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    private val questTypeOrdersLock = ReentrantLock()
    private val questTypeOrders: MutableMap<QuestType, Int> = mutableMapOf()
    private var lastDisplayedRect: TilesRect? = null
    private val questsInView: MutableMap<QuestKey, List<Pin>> = mutableMapOf()
    private val questsInViewMutex = Mutex()
    private val visibleQuestsSourceMutex = Mutex()
    private var updateJob: Job? = null
    private var currentBbox: BoundingBox? = null

    private val visibleQuestsListener = object : VisibleQuestsSource.Listener {
        override fun onUpdated(added: Collection<Quest>, removed: Collection<QuestKey>) {
            val oldUpdateJob = updateJob
            updateJob = scope.launch {
                oldUpdateJob?.join() // don't cancel: updateQuestPins only updates existing data
                updateQuestPins(added, removed)
            }
        }
        override fun onInvalidated() = invalidate()
    }

    private val questTypeOrderListener = object : QuestTypeOrderSource.Listener {
        override fun onQuestTypeOrderAdded(item: QuestType, toAfter: QuestType) = reinitializeQuestTypeOrders()
        override fun onQuestTypeOrdersChanged() = reinitializeQuestTypeOrders()
    }

    fun start() {
        initializeQuestTypeOrders()
        visibleQuestsSource.addListener(visibleQuestsListener)
        questTypeOrderSource.addListener(questTypeOrderListener)
        scope.launch {
            viewportBbox.collect { bbox ->
                currentBbox = bbox
                updateCurrentScreenArea(bbox)
            }
        }
    }

    fun stop() {
        visibleQuestsSource.removeListener(visibleQuestsListener)
        questTypeOrderSource.removeListener(questTypeOrderListener)
    }

    fun getQuestKey(properties: Map<String, String>): QuestKey? = properties.toQuestKey()

    private fun invalidate() {
        lastDisplayedRect = null
        scope.launch { updateCurrentScreenArea(currentBbox) }
    }

    private suspend fun updateCurrentScreenArea(bbox: BoundingBox?) {
        bbox ?: return
        val tilesRect = bbox.enclosingTilesRect(TILES_ZOOM)
        if (tilesRect.size > 32) return // area too big -> skip (performance)
        if (lastDisplayedRect?.contains(tilesRect) == true) return
        lastDisplayedRect = tilesRect
        // discard all but the last fetch while panning fast (see Android comment)
        updateJob?.cancel()
        updateJob = scope.launch { setQuestPins(tilesRect.asBoundingBox(TILES_ZOOM)) }
    }

    private suspend fun setQuestPins(bbox: BoundingBox) {
        val quests = visibleQuestsSourceMutex.withLock {
            withContext(Dispatchers.IO) { visibleQuestsSource.getAll(bbox) }
        }
        val pins = questsInViewMutex.withLock {
            // keep multi-pin quests with a pin still in view (don't clear) — see Android comment
            questsInView.entries.removeAll { (_, pins) ->
                pins.size == 1 || pins.none { it.position in bbox }
            }
            quests.forEach { questsInView[it.key] = createQuestPins(it) }
            questsInView.values.flatten()
        }
        _pins.value = pins
    }

    private suspend fun updateQuestPins(added: Collection<Quest>, removed: Collection<QuestKey>) {
        val pins = questsInViewMutex.withLock {
            val displayedBBox = lastDisplayedRect?.asBoundingBox(TILES_ZOOM) ?: return
            var hasChanges = false
            removed.forEach { if (questsInView.remove(it) != null) hasChanges = true }
            added.forEach {
                if (displayedBBox.contains(it.position)) {
                    questsInView[it.key] = createQuestPins(it); hasChanges = true
                } else if (questsInView.remove(it.key) != null) hasChanges = true
            }
            if (!hasChanges) return
            questsInView.values.flatten()
        }
        _pins.value = pins
    }

    private fun initializeQuestTypeOrders() {
        val sortedQuestTypes = questTypeRegistry.toMutableList()
        questTypeOrderSource.sort(sortedQuestTypes)
        questTypeOrdersLock.withLock {
            questTypeOrders.clear()
            sortedQuestTypes.forEachIndexed { index, questType -> questTypeOrders[questType] = index }
        }
    }

    private fun createQuestPins(quest: Quest): List<Pin> {
        val props = quest.key.toProperties()
        val order = questTypeOrdersLock.withLock { questTypeOrders[quest.type] ?: 0 }
        val iconName = quest.type.icon.pinImageName()
        return quest.markerLocations.map { Pin(it, iconName, props, order) }
    }

    private fun reinitializeQuestTypeOrders() {
        initializeQuestTypeOrders()
        invalidate()
    }

    companion object {
        private const val TILES_ZOOM = 16
    }
}

private const val MARKER_QUEST_GROUP = "quest_group"
private const val MARKER_ELEMENT_TYPE = "element_type"
private const val MARKER_ELEMENT_ID = "element_id"
private const val MARKER_QUEST_TYPE = "quest_type"
private const val MARKER_NOTE_ID = "note_id"
private const val QUEST_GROUP_OSM = "osm"
private const val QUEST_GROUP_OSM_NOTE = "osm_note"

private fun QuestKey.toProperties(): List<Pair<String, String>> = when (this) {
    is OsmNoteQuestKey -> listOf(
        MARKER_QUEST_GROUP to QUEST_GROUP_OSM_NOTE,
        MARKER_NOTE_ID to noteId.toString(),
    )
    is OsmQuestKey -> listOf(
        MARKER_QUEST_GROUP to QUEST_GROUP_OSM,
        MARKER_ELEMENT_TYPE to elementType.name,
        MARKER_ELEMENT_ID to elementId.toString(),
        MARKER_QUEST_TYPE to questTypeName,
    )
}

private fun Map<String, String>.toQuestKey(): QuestKey? = when (get(MARKER_QUEST_GROUP)) {
    QUEST_GROUP_OSM_NOTE -> OsmNoteQuestKey(getValue(MARKER_NOTE_ID).toLong())
    QUEST_GROUP_OSM -> OsmQuestKey(
        ElementType.valueOf(getValue(MARKER_ELEMENT_TYPE)),
        getValue(MARKER_ELEMENT_ID).toLong(),
        getValue(MARKER_QUEST_TYPE),
    )
    else -> null
}
```
> If `import kotlinx.coroutines.IO` doesn't resolve `Dispatchers.IO` here, match exactly how `MapViewModel.kt` imports it (it already uses `Dispatchers.IO` for `getAll`). If `QuestKey` is `sealed` with more subtypes than the two cases, the `when` will fail to compile exhaustively — add the missing branches mirroring androidMain (it had exactly these two).

- [ ] **Step 2: Link gate**

```bash
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map2/QuestPinsManager.kt
git commit -m "M4.2: commonMain QuestPinsManager (port of androidMain)

Separate single-concern producer: viewport-bbox flow + VisibleQuestsSource listener ->
z16 tile windowing (size>32 perf guard, contains-check, pan-debounce) -> Pins on
Dispatchers.IO -> StateFlow. MARKER_* + toProperties/toQuestKey verbatim; includes
OsmNoteQuest. Zero maplibre-compose imports. Not yet wired into the VM."
```

---

## Task 2.2: Wire `QuestPinsManager` into `MapViewModel`; remove the synthetic pin

**Files:**
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt`
- Modify: `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt`

**Interfaces:**
- Consumes: `QuestPinsManager` (2.1); `VisibleQuestsSource`, `QuestTypeRegistry`, `QuestTypeOrderSource` (Koin).
- Produces: `MapViewModel.pins` now driven by real quests; `onViewportIdle` also updates the viewport flow; `getQuestKey` exposed.

- [ ] **Step 1: Inject the sources, build the manager, drive pins + viewport**

In `MapViewModel.kt`, add constructor params (after `downloadController`):
```kotlin
    private val visibleQuestsSource: VisibleQuestsSource,
    private val questTypeRegistry: QuestTypeRegistry,
    private val questTypeOrderSource: QuestTypeOrderSource,
```
Add imports: `de.westnordost.streetcomplete.data.quest.VisibleQuestsSource`, `de.westnordost.streetcomplete.data.quest.QuestTypeRegistry`, `de.westnordost.streetcomplete.data.quest.QuestKey`, `de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderSource`, `de.westnordost.streetcomplete.screens.main.map2.QuestPinsManager`. Add the viewport flow + manager (near the other fields):
```kotlin
    private val viewport = MutableStateFlow<BoundingBox?>(null)
    private val questPinsManager = QuestPinsManager(
        viewport, visibleQuestsSource, questTypeRegistry, questTypeOrderSource, viewModelScope
    )
```
In `init { … }`, **remove the TEMP synthetic-pin line from Task 0.3** and add:
```kotlin
        questPinsManager.start()
        viewModelScope.launch { questPinsManager.pins.collect { _pins.value = it } }
```
Extend `onViewportIdle` to also drive the manager (add the line at the end):
```kotlin
        viewport.value = bbox
```
Add a passthrough + stop the manager in `onCleared`:
```kotlin
    fun getQuestKey(properties: Map<String, String>): QuestKey? = questPinsManager.getQuestKey(properties)
```
In `onCleared()` (first line):
```kotlin
        questPinsManager.stop()
```

- [ ] **Step 2: Update the Koin binding**

In `MapModule.kt`:
```kotlin
    viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
```
(order: downloadedTilesSource, locationSource, compass, prefs, downloadController, visibleQuestsSource, questTypeRegistry, questTypeOrderSource — verify each resolves; all are in the iOS graph.)

- [ ] **Step 3: Link gate**

```bash
JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapViewModel.kt \
        app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/map/MapModule.kt
git commit -m "M4.2: wire QuestPinsManager into MapViewModel (real quest pins)

VM owns the viewport flow + manager (viewModelScope), collects manager.pins into the
existing _pins sink, drives the viewport from onViewportIdle, exposes getQuestKey.
Removes the M4.0 synthetic verification pin."
```

---

## Task 2.3: End-to-end verification (simulator + device)

**Files:** none (verification only).

- [ ] **Step 1: Simulator end-to-end**

```bash
rm -rf build/ios
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,id=5B7C16A4-2C11-4E0C-9549-42F7AC648438' -derivedDataPath build/ios build
xcrun simctl install booted "$(find build/ios -name 'StreetComplete.app' -type d | head -1)"
xcrun simctl privacy booted grant location de.westnordost.streetcomplete.ios
xcrun simctl location booted set 52.5163,13.3777
xcrun simctl launch booted de.westnordost.streetcomplete.ios
```
Open the map, let it settle at a city, then **zoom in** to ~z16–17 (so the visible area is ≤32 z16 tiles → pins fetch). Wait for the download. Expected: **quest pins with per-type icons** appear (and cluster bubbles when zoomed to z13–14). Screenshot to `…/scratchpad/m42-pins.png`. (If pins are blank squares, the icon registry/name round-trip is wrong — debug there.)

- [ ] **Step 2: Device end-to-end (the milestone gate)**

```bash
rm -rf build/ios-device
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphoneos -destination 'platform=iOS,id=00008140-000228491A33001C' -derivedDataPath build/ios-device -allowProvisioningUpdates build
xcrun devicectl device install app --device 83B473E9-A697-567F-BDF4-27B28D49D0B5 "$(find build/ios-device -name 'StreetComplete.app' -type d | head -1)"
xcrun devicectl device process launch --device 83B473E9-A697-567F-BDF4-27B28D49D0B5 de.westnordost.streetcomplete.ios
```
At a real location: open the map → data downloads → quest pins with correct icons appear. (Profile expired 2026-06-21; `-allowProvisioningUpdates` renews it. If renewal fails, that is a real blocker — escalate.)

- [ ] **Step 3: No code commit; record results in the backlog (done in the final task).**

---

# M4.3 — Pin selection (stretch, display-only) — only if M4.0–M4.2 are green

## Task 3.1: Tap a pin → highlight via `SelectedPinsLayer` (no answer form)

**Files:**
- Modify: `app/src/commonMain/.../screens/main/map/MapViewModel.kt`, `MapScreen.kt`, `screens/main/map2/Map.kt`

**Interfaces:**
- Consumes: `MapViewModel.getQuestKey(props)`; `SelectedPinsLayer(icon, pinPositions)`; `PinsLayers(onClickPin = …)`.
- Produces: `MapViewModel.selectedQuestPins: StateFlow<...>`; click highlight.

- [ ] **Step 1: Add selected-pin state to the VM**

```kotlin
    private val _selectedPins = MutableStateFlow<Pair<String, List<LatLon>>?>(null)
    val selectedPins: StateFlow<Pair<String, List<LatLon>>?> = _selectedPins.asStateFlow()

    /** Display-only: highlight all pins of the tapped quest (matched by decoded QuestKey). */
    fun onClickPin(properties: Map<String, String>) {
        val key = getQuestKey(properties) ?: return
        val pins = _pins.value.filter { getQuestKey(it.properties.toMap()) == key }
        _selectedPins.value = pins.firstOrNull()?.let { first -> first.icon to pins.map { it.position } }
    }
```
(import `de.westnordost.streetcomplete.data.osm.mapdata.LatLon` if not present.)

- [ ] **Step 2: Pass an `onClickPin` from `MapScreen` → `Map` → `PinsLayers`; render `SelectedPinsLayer`**

In `Map.kt` `aboveLabelsContent`, after `PinsLayers(pins, onClickPin = onClickPin)`, add:
```kotlin
                selectedPins?.let { (icon, positions) -> SelectedPinsLayer(icon, positions) }
```
Thread `onClickPin: FeaturesClickHandler?` + `selectedPins` params through `Map()` and `MapScreen`. The click handler reads the tapped feature's properties and calls `viewModel.onClickPin(props)`.

- [ ] **Step 3: Link gate + simulator verify the highlight; commit**

Link gate, then tap a pin on the simulator → a larger highlighted copy renders. Commit:
```bash
git commit -m "M4.3: display-only pin selection (tap -> SelectedPinsLayer highlight)"
```

---

## Self-review checklist (run after execution, not now)

- Every spec section maps to a task (icons 0.1–0.3, build 0.2, download 1.1–1.3, pins 2.1–2.3, selection 3.1, PR 0.4). ✔
- Names consistent across tasks: `addImage`, `pinImageName`, `PinImageRegistry`, `onViewportIdle`, `QuestPinsManager`, `getQuestKey`. ✔
- No placeholders except the intentionally-sketched stretch Task 3.1 (refine at execution). ✔
