# iOS-Port M3a: Navigation Shell + Menu Screens (no map) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the running iOS app beyond the single `AboutScreen` into a real, shared multiplatform navigation shell that reaches About, Settings, and the User/Login screens — all without the map.

**Architecture:** Reuse the existing commonMain Compose screens and `androidx.navigation.compose` (already a commonMain dep). Build one top-level `MainMenuNavHost` in commonMain that switches between section NavHosts (`AboutNavHost`, `SettingsNavHost`, `UserNavHost`). Move the section NavHosts + their screen composables from androidMain to commonMain (they have no Android-specific imports; only the koin import differs). Expand the iOS Koin graph from 2 modules to the full commonMain module set, and add iOS no-op stubs for the four WorkManager-backed controllers so the graph resolves without crashing.

**Tech Stack:** Kotlin Multiplatform 2.3.20, Compose Multiplatform 1.10.3, `org.jetbrains.androidx.navigation:navigation-compose:2.9.2`, Koin 4.2.1 (`koin-compose-viewmodel`), `compose-webview-multiplatform:2.0.3` (OAuth login), BundledSQLiteDriver. iOS app bundle id `de.westnordost.streetcomplete.ios`.

---

## Context & decisions (read first)

This plan resumes the iOS port after **M2** (app runs on the physical iPhone 16 Pro rendering the real `AboutScreen`). Background: `docs/superpowers/plans/2026-06-14-ios-port-m3-kickoff.md`, `docs/superpowers/ios-port-backlog.md`, and the M3.0 investigation (workflow `wf_57026bc9-cd4`).

User decisions taken at M3 kickoff (2026-06-14):
- **M3a scope = full menu including User/Login** (About → Settings → User/Login).
- **Menu shell lives in commonMain** (shared production shell, so Android could adopt it later — not throwaway iosMain scaffolding).
- **M3b (map) will include the location dot** — out of scope for *this* plan; see "Bridge to M3b" at the end.

Investigation facts this plan relies on (verified against the tree):
- `AboutNavHost.kt`, all menu-screen ViewModels, and their transitive Koin providers are already in **commonMain**.
- `aboutScreenModule` already does `includes(aboutScreenPlatformModule)` → **replace**, don't add alongside (double-registration risk).
- The only stub the menu graph actually *reaches* is `MapTilesDownloader` (via `SettingsViewModel → Cleaner`). The other three controllers (`UploadController`/`DownloadController`/`ChangesetAutoCloser`) and `InternetConnectionState` are consumed only by androidMain map/sync code; bind the three controllers as cheap no-ops for completeness, skip `InternetConnectionState`.
- Exactly four modules in Android `startKoin` are androidMain-only and must be **excluded** on iOS: `appModule`, `mainModule`, `questModule`, `androidModule`.
- `SettingsScreen.kt`/`UserScreen.kt` and `SettingsNavHost.kt`/`UserNavHost.kt` are in androidMain **by location only** — they compile in commonMain after swapping `org.koin.androidx.compose.koinViewModel` → `org.koin.compose.viewmodel.koinViewModel`.
- Briefing correction: `questsModule` (the ~190 quest types + `QuestTypeRegistry`) is in **commonMain**, loadable on iOS.

## Verification model (applies to every task)

There is **no unit-test gate** for this UI bring-up (same as M2). The Android APK build and iOS `commonTest` remain **upstream-WIP red** and are NOT gates (see backlog "Build-Brüche"). The reliable gates, run at the end of every task, are:

1. **Fast inner loop (compile + link the iOS framework):**
   ```bash
   cd /Users/piotr/git/StreetComplete
   JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64
   ```
   Expected: `BUILD SUCCESSFUL`. This compiles commonMain + iosMain for the iOS target and catches every Kotlin error (including "a moved file references something not in commonMain").

2. **Simulator build + run + screenshot:**
   ```bash
   rm -rf build/ios   # Xcode does NOT relink when only the static framework content changes
   xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
     -destination 'platform=iOS Simulator,id=5B7C16A4-2C11-4E0C-9549-42F7AC648438' \
     -derivedDataPath build/ios build
   APP=$(find build/ios/Build/Products/Debug-iphonesimulator -maxdepth 1 -name 'StreetComplete.app')
   xcrun simctl boot 5B7C16A4-2C11-4E0C-9549-42F7AC648438 2>/dev/null; open -a Simulator
   xcrun simctl install booted "$APP"
   xcrun simctl launch booted de.westnordost.streetcomplete.ios
   xcrun simctl io booted screenshot /tmp/m3a-<task>.png
   ```
   Expected: app launches, screenshot shows the new screen, no crash in `xcrun simctl spawn booted log stream` (watch for Kotlin/Native exceptions).

3. **Device build + run** (physical iPhone 16 Pro; do at least once per task, mandatory on the last step of each task):
   ```bash
   rm -rf build/ios-device
   xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
     -destination 'platform=iOS,id=00008140-000228491A33001C' \
     -derivedDataPath build/ios-device -allowProvisioningUpdates build
   APP=$(find build/ios-device/Build/Products/Debug-iphoneos -maxdepth 1 -name 'StreetComplete.app')
   xcrun devicectl device install app --device 83B473E9-A697-567F-BDF4-27B28D49D0B5 "$APP"
   xcrun devicectl device process launch --device 83B473E9-A697-567F-BDF4-27B28D49D0B5 de.westnordost.streetcomplete.ios
   ```

Constraints carried from M2 (do not relearn the hard way):
- `rm -rf build/ios` (or `build/ios-device`) before every `xcodebuild` — stale-relink gotcha.
- No `NSLog` with `%@` Kotlin-string varargs from Kotlin/Native → use `println`/`IosLogger`.
- Signing: TEAM_ID **2DPUG448BC**; free Personal Team profile expires after 7 days (last deploy 2026-06-14 → renew by **2026-06-21**); re-run the device build with `-allowProvisioningUpdates` to renew.
- Git: no merge commits (rebase only); commit at the end of each task.

---

## Task 1 — M3a-1: Render `AboutNavHost` on iOS (sub-screens reachable)

Smallest, highest-confidence increment. Turns the bare `AboutScreen` into the full About section (Changelog, Credits, Privacy Statement, intro tutorial, and the DB-backed Logs viewer) with real ViewModels.

**Files:**
- Modify: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/MainViewController.kt`
- Modify: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt`

- [ ] **Step 1: Point the entry point at `AboutNavHost`**

Replace the body of `app/src/iosMain/kotlin/de/westnordost/streetcomplete/MainViewController.kt` with:

```kotlin
package de.westnordost.streetcomplete

import androidx.compose.ui.window.ComposeUIViewController
import de.westnordost.streetcomplete.di.initKoin
import de.westnordost.streetcomplete.screens.about.AboutNavHost
import de.westnordost.streetcomplete.ui.theme.AppTheme
import platform.UIKit.UIViewController

/** Entry point of the iOS app: hosts the Compose UI in a UIViewController.
 *  Surfaces to Swift as `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController {
        AppTheme {
            // Full About section: Changelog / Credits / Privacy / Logs reachable via the shared NavHost.
            // onClickBack is a no-op here because About is currently the root of the iOS app.
            AboutNavHost(onClickBack = {})
        }
    }
}
```

- [ ] **Step 2: Load the About + Logs Koin modules**

In `app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt`, change the imports and the `modules(...)` call. Replace the standalone `aboutScreenPlatformModule` with `aboutScreenModule` (which already `includes(aboutScreenPlatformModule)`), and add `logsModule` (needed by `LogsViewModelImpl`):

```kotlin
package de.westnordost.streetcomplete.di

import de.westnordost.streetcomplete.iosModule
import de.westnordost.streetcomplete.data.logs.logsModule
import de.westnordost.streetcomplete.screens.about.aboutScreenModule
import de.westnordost.streetcomplete.util.logs.IosLogger
import de.westnordost.streetcomplete.util.logs.Log
import org.koin.core.context.startKoin

private var koinStarted = false

/** Starts Koin for iOS exactly once. Safe to call from every MainViewController()
 *  invocation (Compose may recreate the hosting UIViewController). */
fun initKoin() {
    if (koinStarted) return
    koinStarted = true

    Log.instances.add(IosLogger())

    startKoin {
        modules(iosModule, aboutScreenModule, logsModule)
    }
}
```

- [ ] **Step 3: Compile + link the iOS framework**

Run: `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Build + run on the simulator, screenshot each About sub-screen**

Run the simulator commands from "Verification model" §2. In the running app: tap into **Changelog**, **Credits**, **Privacy Statement**, the intro tutorial link, and **Logs** (Logs must show real DB rows — the schema self-creates on first open). Capture `/tmp/m3a1-logs.png`.
Expected: every sub-screen renders, back navigation returns to About, no Kotlin/Native exception in the log stream.

- [ ] **Step 5: Build + run on the device**

Run the device commands from "Verification model" §3. Confirm the same screens work on the iPhone.

- [ ] **Step 6: Commit**

```bash
git add app/src/iosMain/kotlin/de/westnordost/streetcomplete/MainViewController.kt \
        app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt
git commit -m "M3a.1: render full AboutNavHost on iOS (Changelog/Credits/Privacy/Logs)"
```

---

## Task 2 — M3a-2: Expand the iOS Koin graph + add controller stubs

Load the full commonMain module set and add iOS no-op implementations of the WorkManager-backed controllers, so menu-screen ViewModels resolve without `NoBeanDefFoundException`. Koin definitions are lazy, so loading the modules does not run the providers — nothing new renders yet; this task de-risks the graph before the UI is added.

**Files:**
- Create: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/IosControllersModule.kt`
- Modify: `app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt`

- [ ] **Step 1: Create the iOS controller stubs module**

Create `app/src/iosMain/kotlin/de/westnordost/streetcomplete/IosControllersModule.kt`:

```kotlin
package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.data.download.DownloadController
import de.westnordost.streetcomplete.data.maptiles.MapTilesDownloader
import de.westnordost.streetcomplete.data.osm.edits.upload.changesets.ChangesetAutoCloser
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.upload.UploadController
import org.koin.dsl.module

/** iOS no-op implementations of the controllers that are WorkManager-backed on Android
 *  (bound there in `androidModule`). Real iOS implementations arrive with the map/sync
 *  milestone (M4). For now they satisfy the Koin graph so menu screens load without crashing:
 *  `SettingsViewModel -> Cleaner` reaches [MapTilesDownloader]; the other three are bound for
 *  graph completeness (only androidMain map/sync code consumes them today).
 *  Binding scopes mirror `androidModule` (single vs factory). */
val iosControllersModule = module {
    factory<MapTilesDownloader> { IosMapTilesDownloaderStub() }
    single<UploadController> { IosUploadControllerStub() }
    single<DownloadController> { IosDownloadControllerStub() }
    factory<ChangesetAutoCloser> { IosChangesetAutoCloserStub() }
}

private class IosMapTilesDownloaderStub : MapTilesDownloader {
    override suspend fun download(bbox: BoundingBox) { /* no-op until M4 */ }
    override suspend fun clear() { /* no-op until M4 */ }
}

private class IosUploadControllerStub : UploadController {
    override fun upload(isUserInitiated: Boolean) { /* no-op until M4 */ }
}

private class IosDownloadControllerStub : DownloadController {
    override fun download(bbox: BoundingBox, isUserInitiated: Boolean) { /* no-op until M4 */ }
}

private class IosChangesetAutoCloserStub : ChangesetAutoCloser {
    override fun enqueue(delayInMilliseconds: Long) { /* no-op until M4 */ }
}
```

- [ ] **Step 2: Load the full commonMain module set on iOS**

Replace `app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt` with the version below. This mirrors Android's `startKoin` module list, **minus** the four androidMain-only modules (`appModule`, `mainModule`, `questModule`, `androidModule`), **plus** `iosModule` (platform infra) and `iosControllersModule` (the stubs above):

```kotlin
package de.westnordost.streetcomplete.di

import de.westnordost.streetcomplete.data.allEditTypesModule
import de.westnordost.streetcomplete.data.download.downloadModule
import de.westnordost.streetcomplete.data.edithistory.editHistoryModule
import de.westnordost.streetcomplete.data.feedsModule
import de.westnordost.streetcomplete.data.logs.logsModule
import de.westnordost.streetcomplete.data.messages.messagesModule
import de.westnordost.streetcomplete.data.meta.metadataModule
import de.westnordost.streetcomplete.data.osm.created_elements.createdElementsModule
import de.westnordost.streetcomplete.data.osm.edits.elementEditsModule
import de.westnordost.streetcomplete.data.osm.geometry.elementGeometryModule
import de.westnordost.streetcomplete.data.osm.mapdata.mapDataModule
import de.westnordost.streetcomplete.data.osm.osmquests.osmQuestModule
import de.westnordost.streetcomplete.data.osmApiModule
import de.westnordost.streetcomplete.data.osmcal.calendarEventsModule
import de.westnordost.streetcomplete.data.osmnotes.edits.noteEditsModule
import de.westnordost.streetcomplete.data.osmnotes.notequests.osmNoteQuestModule
import de.westnordost.streetcomplete.data.osmnotes.notesModule
import de.westnordost.streetcomplete.data.overlays.overlayModule
import de.westnordost.streetcomplete.data.preferences.preferencesModule
import de.westnordost.streetcomplete.data.presets.editTypePresetsModule
import de.westnordost.streetcomplete.data.upload.uploadModule
import de.westnordost.streetcomplete.data.urlconfig.urlConfigModule
import de.westnordost.streetcomplete.data.user.achievements.achievementDefinitionsModule
import de.westnordost.streetcomplete.data.user.achievements.achievementsModule
import de.westnordost.streetcomplete.data.user.achievements.editTypeAliasesModule
import de.westnordost.streetcomplete.data.user.statistics.statisticsModule
import de.westnordost.streetcomplete.data.user.userModule
import de.westnordost.streetcomplete.data.visiblequests.visibleQuestsModule
import de.westnordost.streetcomplete.data.weeklyosm.weeklyOsmModule
import de.westnordost.streetcomplete.iosControllersModule
import de.westnordost.streetcomplete.iosModule
import de.westnordost.streetcomplete.overlays.overlaysModule
import de.westnordost.streetcomplete.quests.questsModule
import de.westnordost.streetcomplete.screens.about.aboutScreenModule
import de.westnordost.streetcomplete.screens.settings.settingsScreenModule
import de.westnordost.streetcomplete.screens.user.userScreenModule
import de.westnordost.streetcomplete.ui.util.measure.arModule
import de.westnordost.streetcomplete.ui.util.photo.photoModule
import de.westnordost.streetcomplete.util.logs.IosLogger
import de.westnordost.streetcomplete.util.logs.Log
import org.koin.core.context.startKoin

private var koinStarted = false

/** Starts Koin for iOS exactly once. Safe to call from every MainViewController()
 *  invocation (Compose may recreate the hosting UIViewController).
 *
 *  This is the iOS counterpart of `StreetCompleteApplication.onCreate()`'s `startKoin`,
 *  minus the four androidMain-only modules (appModule, mainModule, questModule, androidModule),
 *  plus iosModule (platform infra) and iosControllersModule (no-op controller stubs). */
fun initKoin() {
    if (koinStarted) return
    koinStarted = true

    Log.instances.add(IosLogger())

    startKoin {
        modules(
            iosModule,
            iosControllersModule,
            achievementsModule,
            achievementDefinitionsModule,
            editTypeAliasesModule,
            aboutScreenModule,
            userScreenModule,
            createdElementsModule,
            logsModule,
            downloadModule,
            editHistoryModule,
            elementEditsModule,
            elementGeometryModule,
            mapDataModule,
            metadataModule,
            noteEditsModule,
            notesModule,
            messagesModule,
            osmApiModule,
            osmNoteQuestModule,
            osmQuestModule,
            preferencesModule,
            editTypePresetsModule,
            visibleQuestsModule,
            allEditTypesModule,
            questsModule,
            settingsScreenModule,
            statisticsModule,
            uploadModule,
            userModule,
            arModule,
            photoModule,
            overlaysModule,
            overlayModule,
            urlConfigModule,
            weeklyOsmModule,
            calendarEventsModule,
            feedsModule,
        )
    }
}
```

- [ ] **Step 3: Compile + link**

Run: `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`.

If a module import does not resolve (a module was renamed/moved upstream), find it with
`JAVA_HOME=... ./gradlew :app:linkDebugFrameworkIosSimulatorArm64 2>&1 | grep -i unresolved` and locate the file with `grep -rl "val <moduleName> = module" app/src/commonMain`. Fix the import; do not drop the module.

- [ ] **Step 4: Build + run on the simulator — confirm no startup/About regression**

Run the simulator commands (§2). The app must still launch into About and all About sub-screens must still work (Koin definitions are lazy, so loading the bigger graph must not change runtime behaviour). Watch `xcrun simctl spawn booted log stream --predicate 'process == "StreetComplete"'` for any `NoBeanDefFoundException`/`DefinitionOverrideException` at launch.
Expected: identical behaviour to Task 1, no new exceptions. Screenshot `/tmp/m3a2-about.png`.

- [ ] **Step 5: Build + run on the device**, confirm no regression.

- [ ] **Step 6: Commit**

```bash
git add app/src/iosMain/kotlin/de/westnordost/streetcomplete/IosControllersModule.kt \
        app/src/iosMain/kotlin/de/westnordost/streetcomplete/di/InitKoin.kt
git commit -m "M3a.2: load full commonMain Koin graph on iOS + no-op controller stubs"
```

---

## Task 3 — M3a-3: Shared `MainMenuNavHost` + Settings on iOS

Create the shared top-level navigation shell in commonMain and reach Settings. Move `SettingsNavHost`/`SettingsScreen` to commonMain. The shell switches between section NavHosts (`AboutNavHost`, `SettingsNavHost`) — the same one-section-per-host model Android uses with Activities, composed under one outer NavHost. The User/Profile entry is wired in Task 4.

**Files:**
- Move: `app/src/androidMain/.../screens/settings/SettingsScreen.kt` → `app/src/commonMain/.../screens/settings/SettingsScreen.kt`
- Move: `app/src/androidMain/.../screens/settings/SettingsNavHost.kt` → `app/src/commonMain/.../screens/settings/SettingsNavHost.kt`
- Create: `app/src/commonMain/.../screens/main/MainMenuNavHost.kt`
- Modify: `app/src/iosMain/.../MainViewController.kt`
- Possibly create (iOS-excluded debug path): see Step 3.

- [ ] **Step 1: Move `SettingsScreen.kt` to commonMain and fix the koin import**

```bash
git mv app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/settings/SettingsScreen.kt \
       app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/settings/SettingsScreen.kt
```
Then open the moved file and, if present, change `import org.koin.androidx.compose.koinViewModel` to `import org.koin.compose.viewmodel.koinViewModel`. (The investigation found `SettingsScreen.kt` uses the multiplatform import already and has no `android.*`/`R`/fragment imports; verify with `grep -nE "import android|org.koin.androidx" app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/settings/SettingsScreen.kt` → expect no matches.)

- [ ] **Step 2: Move `SettingsNavHost.kt` to commonMain and fix the koin import**

```bash
git mv app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/settings/SettingsNavHost.kt \
       app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/settings/SettingsNavHost.kt
```
In the moved file change line ~18 `import org.koin.androidx.compose.koinViewModel` → `import org.koin.compose.viewmodel.koinViewModel`.

- [ ] **Step 3: Resolve the debug `ShowQuestForms` path for commonMain**

`SettingsNavHost` references `de.westnordost.streetcomplete.screens.settings.debug.ShowQuestFormsScreen` and passes `onClickShowQuestTypeForDebug` (Android opens a Fragment-based quest-form preview — not available on iOS). Determine where `ShowQuestFormsScreen` lives:
```bash
grep -rl "fun ShowQuestFormsScreen" app/src
```
- If it is already in commonMain: keep the `ShowQuestForms` destination; callers pass a no-op `onClickShowQuestTypeForDebug` (iOS) — the debug screen lists quest types but the open-form action is inert on iOS.
- If it is in androidMain: it cannot move cleanly (it depends on the Android quest-form Fragments). In that case, delete the `composable(SettingsDestination.ShowQuestForms) { ... }` block and the `ShowQuestForms` route from the moved `SettingsNavHost.kt`, and drop the `onClickShowQuestTypeForDebug` parameter (also remove the `onClickShowQuestForms` wiring in `SettingsScreen` by passing `onClickShowQuestForms = {}` from `SettingsNavHost`). The Android `SettingsActivity` keeps its own debug entry separately; do not touch it.

Pick whichever the grep dictates; the rest of `SettingsNavHost` (presets, quest selection, overlay selection, language, messages) is commonMain-clean.

- [ ] **Step 4: Create the shared `MainMenuNavHost`**

Create `app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/main/MainMenuNavHost.kt`. The User/Profile entry is added in Task 4 (here it is present but disabled):

```kotlin
package de.westnordost.streetcomplete.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.action_about2
import de.westnordost.streetcomplete.resources.action_settings
import de.westnordost.streetcomplete.screens.about.AboutNavHost
import de.westnordost.streetcomplete.screens.settings.SettingsNavHost
import org.jetbrains.compose.resources.stringResource

/** Top-level shared navigation shell for platforms without the Fragment-based main screen
 *  (currently iOS). Switches between the section NavHosts; each section manages its own
 *  internal back stack, mirroring the one-Activity-per-section model on Android. */
@Composable
fun MainMenuNavHost() {
    val navController = rememberNavController()
    fun goBack() { navController.popBackStack() }

    NavHost(navController = navController, startDestination = MainMenuDestination.Menu) {
        composable(MainMenuDestination.Menu) {
            MainMenuScreen(
                onClickSettings = { navController.navigate(MainMenuDestination.Settings) },
                onClickAbout = { navController.navigate(MainMenuDestination.About) },
            )
        }
        composable(MainMenuDestination.About) {
            AboutNavHost(onClickBack = ::goBack)
        }
        composable(MainMenuDestination.Settings) {
            SettingsNavHost(onClickBack = ::goBack, onClickShowQuestTypeForDebug = {})
        }
    }
}

@Composable
private fun MainMenuScreen(
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("StreetComplete") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MenuRow(stringResource(Res.string.action_settings), onClickSettings)
            MenuRow(stringResource(Res.string.action_about2), onClickAbout)
        }
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.h6,
        modifier = Modifier.fillMaxSize().clickable(onClick = onClick).padding(24.dp)
    )
}

object MainMenuDestination {
    const val Menu = "menu"
    const val About = "about"
    const val Settings = "settings"
    const val User = "user"
}
```

> Note: if `SettingsNavHost`'s signature was trimmed in Step 3 (no `onClickShowQuestTypeForDebug`), drop that argument here too. Verify the `Res.string.action_settings`/`action_about2` keys exist with `grep -rn "action_about2\|action_settings" app/src/commonMain/composeResources/values/strings.xml`; they are used by `MainMenuDialog.kt` so they exist.

- [ ] **Step 5: Host `MainMenuNavHost` from the iOS entry point**

In `app/src/iosMain/.../MainViewController.kt`, replace `AboutNavHost(onClickBack = {})` with `MainMenuNavHost()` and update the import:

```kotlin
import de.westnordost.streetcomplete.screens.main.MainMenuNavHost
// ...
        AppTheme {
            MainMenuNavHost()
        }
```

- [ ] **Step 6: Compile + link**

Run: `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`. If the moved `SettingsScreen`/`SettingsNavHost` references an androidMain-only symbol, the error names it here — resolve by moving that helper to commonMain too, or excluding it (as with the debug path in Step 3).

- [ ] **Step 7: Build + run on the simulator**

Run §2. Verify: launch → menu shows **Settings** and **About** rows → tap **Settings** → the Settings screen renders → open at least **Language selection** and **Quest selection** (this is the first time `QuestTypeRegistry`/`CountryBoundaries`/`FeatureDictionary` are resolved on iOS — a known runtime risk: the metadata reads `boundaries.ser`/`osmfeatures` from the app bundle). Watch the log stream for `NoBeanDefFoundException` (missing binding) or a missing-resource crash. Capture `/tmp/m3a3-settings.png`, `/tmp/m3a3-questselection.png`.
Expected: Settings and its sub-screens render with real data, back returns to the menu.

> If Quest/Overlay selection crashes because `composeResources/files/boundaries.ser` or `osmfeatures/*` are not in the `.app` bundle: confirm packaging with `find build/ios/Build/Products/Debug-iphonesimulator/StreetComplete.app -name 'boundaries.ser' -o -name 'osmfeatures' | head`. If absent, that is a resource-bundling fix (separate, surfaced here) — fall back to opening only Language/Theme settings for this task's screenshot and note the gap for follow-up; do not block the task.

- [ ] **Step 8: Build + run on the device.**

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "M3a.3: shared commonMain MainMenuNavHost + Settings reachable on iOS"
```

---

## Task 4 — M3a-4: User screen + OAuth Login on iOS

Reach the User section (Profile / Statistics / Achievements / Links) and the OAuth login via the multiplatform WebView. Highest runtime risk of M3a (network + WebView + the `streetcomplete://oauth` redirect interception), so it is the last increment.

**Files:**
- Move: `app/src/androidMain/.../screens/user/UserScreen.kt` → `app/src/commonMain/.../screens/user/UserScreen.kt`
- Move: `app/src/androidMain/.../screens/user/UserNavHost.kt` → `app/src/commonMain/.../screens/user/UserNavHost.kt`
- Modify: `app/src/commonMain/.../screens/main/MainMenuNavHost.kt`

- [ ] **Step 1: Move `UserScreen.kt` to commonMain, fix koin import**

```bash
git mv app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/user/UserScreen.kt \
       app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/user/UserScreen.kt
```
Change `import org.koin.androidx.compose.koinViewModel` → `import org.koin.compose.viewmodel.koinViewModel` if present. Check for android-only imports: `grep -nE "import android|org.koin.androidx" app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/user/UserScreen.kt`.

- [ ] **Step 2: Move `UserNavHost.kt` to commonMain, fix koin import**

```bash
git mv app/src/androidMain/kotlin/de/westnordost/streetcomplete/screens/user/UserNavHost.kt \
       app/src/commonMain/kotlin/de/westnordost/streetcomplete/screens/user/UserNavHost.kt
```
Change line ~10 `import org.koin.androidx.compose.koinViewModel` → `import org.koin.compose.viewmodel.koinViewModel`.

- [ ] **Step 3: Resolve `LoginScreen` / WebView for commonMain**

`UserNavHost` references `de.westnordost.streetcomplete.screens.user.login.LoginScreen`. Locate it and its WebView usage:
```bash
grep -rl "fun LoginScreen" app/src
grep -rn "com.multiplatform.webview\|WebView\|streetcomplete://oauth" app/src
```
`compose-webview-multiplatform:2.0.3` is a commonMain dependency (build.gradle.kts:172), so `LoginScreen` should be commonMain-compatible. If `LoginScreen` is in androidMain but uses only the multiplatform WebView API, move it to commonMain with the same `git mv` + koin-import treatment. If it pulls an Android-only WebView, stop and surface it (this is the one place an `expect/actual` may be needed) — do not fake it.

- [ ] **Step 4: Enable the User/Profile entry in the shell**

In `app/src/commonMain/.../screens/main/MainMenuNavHost.kt`:
1. Add to the `MainMenuScreen` parameters `onClickProfile: () -> Unit` and render a third `MenuRow(stringResource(Res.string.user_profile), onClickProfile)` (add `import de.westnordost.streetcomplete.resources.user_profile`).
2. In `MainMenuNavHost`, pass `onClickProfile = { navController.navigate(MainMenuDestination.User) }` and add the destination:

```kotlin
        composable(MainMenuDestination.User) {
            UserNavHost(launchAuth = false, onClickBack = ::goBack)
        }
```
add `import de.westnordost.streetcomplete.screens.user.UserNavHost`.

- [ ] **Step 5: Compile + link**

Run: `JAVA_HOME=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home ./gradlew :app:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Build + run on the simulator — validate the login flow**

Run §2. Verify: menu → **Profile** → since not logged in, the **Login** screen renders → tap login → the OAuth WebView opens the OSM authorize page → after granting, the `streetcomplete://oauth` redirect is intercepted inside the WebView and the app shows the logged-in **User** screen (Profile/Statistics/Achievements/Links tabs). Capture `/tmp/m3a4-login.png` and `/tmp/m3a4-profile.png`.
Expected: login completes and the profile loads. If the WebView renders but the redirect is not intercepted on iOS, capture the exact redirect URL from the log and stop for a focused fix (the redirect-interception is the known iOS unknown from the project assessment) — do not force a workaround.

- [ ] **Step 7: Build + run on the device**, repeat the login validation on the iPhone.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "M3a.4: User screen + OAuth WebView login reachable on iOS"
```

---

## Task 5 — M3a wrap-up: backlog, memory, and the bridge to M3b

- [ ] **Step 1: Update the backlog**

In `docs/superpowers/ios-port-backlog.md`, add an "M3a ABGESCHLOSSEN" section at the top: what works on iOS now (menu shell, About, Settings, User/Login), the module-graph change (full commonMain set minus 4 androidMain-only modules + `iosControllersModule` stubs), any resource-bundling or login findings from Tasks 3–4, and the iOS commit range.

- [ ] **Step 2: Update project memory**

Update `~/.claude/projects/-Users-piotr-git-StreetComplete/memory/ios-port-project.md` with M3a completion and the next step (maplibre spike → M3b). Keep the MEMORY.md index line current.

- [ ] **Step 3: Run the verification gate one final time** on the device and confirm the whole menu (About + Settings + User/Login) works end-to-end. Commit any doc changes:

```bash
git add docs/superpowers/ios-port-backlog.md
git commit -m "Document M3a completion (iOS nav shell + menu screens) in backlog"
```

---

## Bridge to M3b (map + location) — separate plan, written after a spike

M3b is intentionally **not** detailed here: its concrete steps depend on a maplibre-compose capability/version spike (the investigation's strongest recommendation). Writing bite-sized M3b tasks before the spike would be speculative. The user has chosen that **M3b includes the location dot**, so the eventual M3b plan covers both the map and CoreLocation.

**Spike (do first, time-boxed — its result feeds the M3b plan):**
1. Add `org.maplibre.compose:maplibre-compose:<version>` to the `commonMain` dependencies block of `app/build.gradle.kts`. Try the latest release first; PR #6352 used `0.11.1` against Compose 1.10.0, and this fork is on Compose 1.10.3 / Kotlin 2.3.20 / koin 4.2.1 — so the version must be re-validated.
2. Render a trivial `MaplibreMap` at a fixed camera in a throwaway iOS screen; build + run on the simulator and device.
3. Output: go/no-go on the library + the pinned version + notes on which capabilities work on iOS (vector source, SDF icon coloring, clustering, click hit-testing).

**M3b scope (to be turned into a detailed plan after the spike), per the investigation synthesis:**
- Build a fresh commonMain map layer; reference-copy only assets from `upstream/maplibre-compose` (`MapStyle.kt` JawgMaps style port, `MapColors`/`ExpressionUtils`/`GeometryUtils`, glyph `.pbf` set + `map_*` drawables). Externalize the hardcoded JawgMaps token (it appears both in `map2/MapStyle.kt` on the branch and in `build.gradle.kts:454` `UpdateMapStyleTask`).
- Port the stateless layer composables one at a time, finishing the icon/animation TODOs and removing the stray `org.maplibre.android` import.
- Write a real `Map()` taking a ViewModel/state (use #6352's `Map.kt` only for layer ordering — its body references ~12 undefined symbols).
- Location dot (this milestone, per user choice): add `NSLocationWhenInUseUsageDescription` to `iosApp/iosApp/Info.plist`; introduce a commonMain `LocationManager`/`Compass` interface (none exists today); add an iosMain `CLLocationManager` actual emitting the existing commonMain `Location(position, accuracy, elapsedDuration)` (mirror `android.location.Location.toLocation()`, use a monotonic clock); drive the existing commonMain `LocationState` enum. Heading/compass (`CLHeading`/`CMMotionManager`) is a further sub-step (`CurrentLocationLayers` renders the dot with `rotation == null`).
- Offline tiles deferred (issue #6072): ship online-only JawgMaps first.

---

## Self-review notes

- **Spec coverage:** M3a-1 (AboutNavHost) → Task 1; iOS Koin graph + stubs → Task 2; shared commonMain shell + Settings → Task 3; User/Login → Task 4; docs/memory → Task 5; map/location → explicitly deferred to the M3b plan after the spike.
- **Type consistency:** stub classes implement the exact interface signatures read from source (`MapTilesDownloader.download(BoundingBox)/clear()`, `UploadController.upload(Boolean)`, `DownloadController.download(BoundingBox, Boolean)`, `ChangesetAutoCloser.enqueue(Long)`); binding scopes (`single`/`factory`) mirror `androidModule`. `MainMenuDestination` route constants are referenced consistently across Tasks 3–4.
- **Known iterative points (not placeholders — each has a concrete decision rule):** Step 3.3 (debug ShowQuestForms location), Step 3.7 (metadata resource bundling), Step 4.3 (LoginScreen WebView source set), Step 4.6 (`streetcomplete://oauth` interception on iOS). Each says exactly how to detect and what to do.
