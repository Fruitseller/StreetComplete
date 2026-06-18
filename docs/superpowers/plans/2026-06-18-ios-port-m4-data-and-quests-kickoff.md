# iOS-Port M4 — Real OSM data + quest pins on the map (Kickoff / Resume doc)

> Written so a fresh post-`/clear` session resumes cleanly. Investigation: workflow `wf_ef519e08-db5`
> (5 parallel deep-dives, 2026-06-18). Follows M3b.3 (location dot + heading + GPS button, done +
> reviewed; tip `071e81b20`). This is a KICKOFF doc, not yet a plan — start M4 with a short
> brainstorm on the open decisions below, then design+plan the first sub-milestone.

## Goal

Get **real OSM data and quest pins onto the iOS map**: download → store → derive quests → render
per-quest-type pins → tap a pin to select it. This is the central step toward a functional iOS app
(currently the map shows no pins because no download has ever run on iOS).

## The headline finding (why this is mostly wiring, not porting)

**The entire download → DB → quest pipeline is already in commonMain and already bound in the iOS
Koin graph** (`di/InitKoin.kt`). iOS just needs to *drive* it. Verified chain, all commonMain:

```
DownloadController.download(bbox)                      ← interface commonMain; iOS impl is a NO-OP stub
  → Downloader.download()                              app/src/commonMain/.../data/download/Downloader.kt  (3 concurrent coroutines, Mutex "SerializeSync")
      → MapDataDownloader.download()                   .../data/osm/mapdata/MapDataDownloader.kt
          → MapDataApiClient.getMap()                  .../data/osm/mapdata/MapDataApiClient.kt  (Ktor; iOS uses ktor-client-darwin from iosModule)
          → MapDataController.putAllForBBox()          .../data/osm/mapdata/MapDataController.kt  (SQLite via BundledSQLiteDriver, fires listeners)
              → OsmQuestController (listener)          .../data/osm/osmquests/OsmQuestController.kt  (runs quest filters, persists OsmQuests)
                  → VisibleQuestsSource.onUpdated      .../data/quest/VisibleQuestsSource.kt  (getAll(bbox) → visible quests, z16 SpatialCache)
      → NotesDownloader.download()                     .../data/osmnotes/NotesDownloader.kt  (commonMain)
      → MapTilesDownloader.download()                  iOS NO-OP stub (offline tiles deferred #6072; maplibre-compose fetches online)
```

The commonMain `MapViewModel` already has the sink hooks (`setPins`, `putGeometryMarkers`,
`setFocusedGeometry`, `clearGeometryMarkers`) and the full map2 layer stack (`PinsLayers`,
`SelectedPinsLayer`, `DownloadedAreaLayer`, …) consumes them. **Nothing calls `setPins()` with real
quest data yet.**

### What is Android-only (must be replaced/written for iOS — all thin)

| Android-only | What it does | iOS replacement |
|---|---|---|
| `DownloadControllerAndroid` + `DownloadWorker` (androidMain) | WorkManager/CoroutineWorker shell that enqueues `Downloader.download()` | **Real `IosDownloadController`** (~15–30 lines): inject `Downloader`, launch a coroutine calling `downloader.download(bbox, isUserInitiated)`. Replaces `IosDownloadControllerStub` in `IosControllersModule.kt`. |
| `QuestAutoSyncer` (androidMain) | LifecycleObserver: GPS + ConnectivityManager → auto-download radius (`AVariableRadiusStrategy`, commonMain) → `download()` | **Minimal:** trigger download from `MapScreen` on camera-idle (or first GPS fix) for the visible bbox. Full auto-sync (Wi-Fi gating via `NWPathMonitor`, radius strategy) is a later refinement. |
| `QuestPinsManager` + `StyleableOverlayManager` + `EditHistoryPinsManager` (androidMain) | Bridge `VisibleQuestsSource`/`MapDataWithEditsSource` → build `Pin`s for the visible bbox → push to the Fragment map. Tied to `org.maplibre.android.MapLibreMap`. | **New commonMain/iosMain `IosQuestPinsManager`**: `snapshotFlow { cameraState.position }` → `CameraState.projection?.queryVisibleBoundingBox()` → `VisibleQuestsSource.getAll(bbox)` on `Dispatchers.IO` → `Pin` list → `MapViewModel.setPins()`. |
| `createPinBitmap()` / `MapImages` (androidMain) | Composite shadow+pin+quest-icon bitmap, register via `Style.addImage` | **The hard unknown — see M4.0 spike.** |
| `InternetConnectionState` (androidMain) | ConnectivityManager network gate | Not needed minimally — let Ktor attempt and handle `ConnectionException`. (Later: `NWPathMonitor`.) |

## The one hard unknown — quest-pin icon registration (do a spike FIRST)

`PinsLayers.kt:108` and `SelectedPinsLayer.kt:57` already use
`iconImage = image(feature["icon-image"].asString())` (data-driven, by string name) — with a `TODO`,
because **no images are ever registered**. The 200 quest-type icons (`composeResources/drawable/quest_*.xml`)
+ `map_pin.xml`/`map_pin_shadow.png` are all in commonMain, but **maplibre-compose 0.13.0 has no public
API to register named images for data-driven feature-property lookup** (`Style.addImage`/`ImageManager`
are `internal`; the public `image(Painter)` DSL only does static, inline images — not name-keyed lookup).

Candidate approaches (the spike must pick one):
1. **Sprite sheet baked into the `BaseStyle` JSON** — composite all pin bitmaps into one PNG + a sprite
   manifest, base64/URI-embed it in the style `sprite` field. No `addImage` needed. Most promising.
2. **Build-time PNG generation** — a Gradle task rasterizes each quest icon→composite PNG into
   composeResources; reference by name. Avoids runtime canvas compositing.
3. **Upstream API** — get `StyleState.addImage(id, ImageBitmap)` exposed publicly in maplibre-compose
   (PR), then register composites at runtime via a `@MaplibreComposable` registry.
4. **Reflection** into the `internal` API — last resort, fragile.

**M4.0 = a throwaway spike** that renders ≥2 named quest-pin images via the chosen approach and confirms
`iconImage = image("quest_xxx")` resolves them at runtime. This de-risks the whole milestone (icons are
the only thing not provably feasible). Compositing itself can reuse Compose canvas (`drawImage`) in
commonMain (port of `createPinBitmap()`), the question is purely *registration*.

## Prerequisite (must land before any download)

**`Preloader.preload()` is never called on iOS.** It warms `CountryBoundaries` + `FeatureDictionary`
that `OsmQuestController` needs to evaluate quest filters. On Android it runs in
`StreetCompleteApplication.onCreate()`. Add a background-coroutine call in `initKoin()` /
`MainViewController()` before the first download. (Bundle paths already work — `MetadataModule.ios.kt`,
fixed in M3a.)

## Recommended decomposition (display-first; answering is upstream-blocked)

- **M4.0 — Icon-registration spike** (throwaway). Resolve the maplibre-compose named-image unknown.
  Output: the chosen approach + a recipe. Gate: a quest icon renders on a pin on the simulator.
- **M4.1 — Data download on iOS.** `Preloader.preload()` at startup + real `IosDownloadController` +
  a minimal trigger (manual "load this area" button or camera-idle at zoom ≥ 14, visible bbox via
  `CameraState.projection.queryVisibleBoundingBox()`). Gate: real elements+quests land in the iOS DB
  (log counts), `DownloadedAreaLayer` shows the downloaded tile; device-verified at a real location.
- **M4.2 — Quest pins on the map.** `IosQuestPinsManager` (camera bbox → `VisibleQuestsSource.getAll`
  on IO → `Pin` list → `setPins`) + the M4.0 icon solution. Gate: per-quest-type pins render at a
  downloaded location on device.
- **M4.3 — Pin selection (display-only).** `onClickPin` → decode `QuestKey` from feature props (match
  `QuestPinsManager`'s `MARKER_*` constants exactly) → `SelectedPinsLayer` highlight + minimal quest
  info. No answering.
- **Deferred / upstream-gated — Quest answer form.** `OsmQuestFormContainer` has every action handler
  as `TODO()` and the old `AbstractQuestForm` was deleted upstream (`b42205f4d`, 2026-06-05) "without
  replacement yet" — even Android is mid-rewrite (Tobias Zwick, PR #6842). Wire the form (and real
  `UploadController`/`ChangesetAutoCloser`) only once upstream completes it. The 422 quest `Form()`
  composables are already commonMain and compile for iOS, so this becomes cheap when unblocked.

## Risks (carry into the plan)

- **Icon registration** — the gating unknown; M4.0 spike decides feasibility before committing M4.2.
- **`Preloader` ordering** — quests silently fail if filters run before CountryBoundaries/FeatureDictionary load.
- **Threading** — `VisibleQuestsSource.getAll(bbox)` and `OsmQuestController`'s parallel filter runs are
  blocking/CPU-heavy under a `ReentrantLock`; must run on `Dispatchers.IO`, never the Compose main thread (iOS jank/ANR).
- **`SerializeSync` Mutex** is shared between `Downloader` and `Uploader` (one Koin singleton) — download/upload serialize.
- **Pin perf guard** — Android caps displayed z16 tiles at ≤32; the iOS manager needs the same guard or a country-zoom view floods pins.
- **`CameraProjection` is null until layout** — guard / use `awaitProjection()`.
- **First-download size** — `WifiAutoDownloadStrategy` ≈ 20 km², `MobileData` ≈ 10 km²; `MapDataApiParser` (kotlinx-io XML) untested under iOS memory pressure for dense bboxes.
- **OSM auth** — reads need no token (public API); `MapDataApiClient` skips bearer when `UserAccessTokenSource` is null → pins appear logged-out (verify the skip).

## Open decisions for the M4 brainstorm (ask the user)

1. **Download trigger:** manual "load this area" button vs. auto-download on GPS (QuestAutoSyncer-lite). (Lean: manual button for M4.1, auto later.)
2. **Icon approach:** pending the M4.0 spike result (sprite-sheet-in-style is the current favorite).
3. **Where the pins manager lives:** inside `MapViewModel` vs. a separate Koin-injected class.
4. **Note quests:** include `OsmNoteQuest` pins in display (they come via `VisibleQuestsSource`) or exclude for now? (Their form path is still Fragment-based.)

## Key file anchors (for the next session)

- Stubs to replace: `app/src/iosMain/.../IosControllersModule.kt`
- Download: `commonMain/.../data/download/{Downloader,DownloadController}.kt`; android `DownloadControllerAndroid.kt`
- Data→DB: `commonMain/.../data/osm/mapdata/{MapDataDownloader,MapDataApiClient,MapDataController}.kt`
- Quests: `commonMain/.../data/osm/osmquests/OsmQuestController.kt`; `commonMain/.../data/quest/VisibleQuestsSource.kt`
- Android bridges (reference): `androidMain/.../data/quest/QuestAutoSyncer.kt`; `androidMain/.../screens/main/map/QuestPinsManager.kt`; `androidMain/.../screens/main/map/StyleableOverlayManager.kt`
- Icons: `commonMain/.../screens/main/map2/layers/PinsLayers.kt:108` (TODO) + `SelectedPinsLayer.kt:57`; android compositing `MapIconBitmapCreator`/`MapImages`; resources `commonMain/composeResources/drawable/quest_*.xml`, `map_pin*.{xml,png}`
- maplibre-compose camera/projection: `docs/superpowers/references/maplibre-compose-0.13.0-api.md` (`CameraProjection.queryVisibleBoundingBox()`, `awaitProjection()`)
- Preloader: search `Preloader` (commonMain) + its Android call site in `StreetCompleteApplication.onCreate()`
- Quest form (upstream WIP): `commonMain/.../OsmQuestFormContainer*` (all `TODO()`); deletion commit `b42205f4d`
- Sink: `commonMain/.../screens/main/map/MapViewModel.kt` (`setPins` etc.), `MapScreen.kt`, `map2/Map.kt`

## Status of the rest of the port (context)

M1–M3b.3 done (see `ios-port-backlog.md`): app runs on the iPhone 16 Pro; menu/Settings/About/OAuth
login; SC vector map with camera persistence + full layer stack; live GPS location dot + compass
heading + Android-parity GPS button. Deferred: offline tiles (#6072); sensor pause-on-background;
`Location.elapsedDuration` fix-age stamp. Code through `071e81b20` is on `master`, **not yet pushed** to origin.
