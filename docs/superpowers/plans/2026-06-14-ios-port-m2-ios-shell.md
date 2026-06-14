# iOS-Port M2: iOS-Shell / App-Bring-up — Implementation Plan

> **For agentic workers:** inkrementelle, je einzeln verifizierbare Milestones (M2.0–M2.4).
> Jedes endet mit einem konkreten Build-/Run-Check. Basis: Branch `ios-port` (e27a9eb44).

**Goal:** Die App startet auf dem iOS-Simulator und dann auf dem physischen iPhone 16 Pro und
zeigt echtes Compose-UI — **ohne Karte** (Karte = M3).

**Architecture:** Das Kotlin-`:app`-Framework (baseName `StreetComplete`, isStatic) kompiliert/linkt
bereits für iOS. M2 fügt den fehlenden iOS-Einstiegspunkt (`MainViewController` →
`ComposeUIViewController`), den iOS-Koin-Graph (folgt der `*.ios.kt`-PlatformModule-Konvention aus
Upstream `1f0ea5dcf`), die iOS-Plattform-Bindings (Database via `BundledSQLiteDriver`, HttpClient,
FileSystem, Pfade, Logger) und die korrekte Xcode-Projekt-Verdrahtung hinzu. Asset-/Metadata-Loading
ist bereits multiplatform (`MetadataModule.ios.kt`, `Res.ios.kt`).

**Verifikation pro Milestone:** Simulator iPhone 16 Pro (iOS 18.5, UDID
`5B7C16A4-2C11-4E0C-9549-42F7AC648438`) via `xcodebuild` + `simctl`; M2.4 auf Gerät `iPiotr`.

## Entscheidungen (Defaults aus der Untersuchung übernommen)

- **Modulname:** `ContentView.swift` `import ComposeApp` → `import StreetComplete` (baseName bleibt
  `StreetComplete`; statisches Framework → keine Namenskollision).
- **Bundle-ID:** `de.westnordost.streetcomplete.ios`; `TEAM_ID=44Q5Q437K2` (vorhandenes Personal Team).
- **Erster echter Screen:** `AboutScreen` (commonMain, einzige Koin-Dep `AppStoreInfo`), mit
  No-op-Navigations-Handlern. Voraussetzung: `IosAppStoreInfo.getRatingUri()`-TODO stubben (sonst
  Crash bei Komposition).
- **Map/Upload/Download:** in M2 ausgeklammert; Android-only Module werden NICHT geladen.

## Milestones

### M2.0 — Walking Skeleton (Xcode-Verdrahtung + Hello-Compose, kein Koin/DB)
1. `app/src/iosMain/.../MainViewController.kt`: top-level
   `fun MainViewController(): UIViewController = ComposeUIViewController { AppTheme { Text("Hello from Compose") } }`.
2. `ContentView.swift`: `import StreetComplete`.
3. `project.pbxproj` Run-Script: `:composeApp:` → `:app:embedAndSignAppleFrameworkForXcode`,
   `JAVA_HOME`/`PATH` (JDK 21) vor `cd` exportieren.
4. `project.pbxproj` `FRAMEWORK_SEARCH_PATHS` (Debug+Release):
   `"$(SRCROOT)/../app/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"`.
5. `Config.xcconfig`: `TEAM_ID=44Q5Q437K2`, `PRODUCT_BUNDLE_IDENTIFIER=de.westnordost.streetcomplete.ios`.
6. Shared Scheme `iosApp.xcscheme` committen.
- **Verify:** `embedAndSignAppleFrameworkForXcode` Ausgabepfad prüfen → `xcodebuild` (Simulator) →
  `simctl install/launch` → Simulator zeigt „Hello from Compose".

### M2.1 — initKoin + minimale iOS-Plattform-Bindings (kein Screen konsumiert sie)
1. `IosLogger : Logger` (println/NSLog), nach Vorbild `AndroidLogger`.
2. `IosModule` erweitern: `HttpClient` (userAgent+gzip, Darwin-Engine), `Res`, `FileSystem`
   (`SystemFileSystem`), `Database` = `StreetCompleteDatabase(BundledSQLiteDriver().open(<Documents>/streetcomplete_v2.db))`,
   `DatabaseLogger`, `factory(named("AvatarsCacheDirectory"))` (NSCachesDirectory).
3. idempotentes `initKoin()` (`GlobalContext.getOrNull() == null`), kein `androidContext()`/`workManagerFactory()`;
   danach `Log.instances.add(IosLogger())` + `DatabaseLogger`.
4. `MainViewController()` ruft `initKoin()` einmal vor dem Body; vorerst nur `iosModule` (+ optional
   `metadataModule` zum Asset-Test) laden.
- **Verify:** App startet weiter, Konsole ohne Koin/DB/Asset-Exception; DB-Datei im App-Container.
- **Risiko:** composeResources-Bundling in der `.app` (für CountryBoundaries/FeatureDictionary/Strings)
  — im Build-Output verifizieren.

### M2.2 — Erster echter commonMain-Screen (AboutScreen)
1. `IosAppStoreInfo.getRatingUri()`-TODO durch Platzhalter ersetzen.
2. `aboutScreenPlatformModule` zur initKoin-Liste.
3. `MainViewController`-Body: `AppTheme { AboutScreen(onClick…={}) }` (keine Navigation in
   Credits/Changelog/Logs — die brauchen DB-gestützte VMs).
- **Verify:** AboutScreen rendert unter AppTheme, kein Crash, keine fehlenden Strings.

### M2.3 — Robustheit / Idempotenz
- initKoin-Idempotenz unter UIViewController-Recreation; keine Doppel-Module; optional No-op-Stubs für
  `UploadController`/`DownloadController`/`MapTilesDownloader`/`ChangesetAutoCloser`.
- **Verify:** mehrfaches Starten/Rotieren/Hintergrund ohne `KoinAppAlreadyStarted`/`MissingDefinition`.

### M2.4 — Start auf physischem iPhone 16 Pro
- Automatic Signing mit Personal Team; ggf. einmalige Geräte-Registrierung/Trust.
- `xcodebuild` (iphoneos) → `devicectl install` → `devicectl launch` auf `iPiotr`.
- **Verify:** AboutScreen rendert auf dem Gerät.

## Risiken / offene Punkte
- composeResources-Bundling bei `isStatic=true` (M2.1-Experiment).
- `embedAndSignAppleFrameworkForXcode`-Ausgabepfad ggf. abweichend → Search-Path nachziehen.
- Free Personal Team: 10 App-IDs, 7-Tage-Resign, evtl. manueller Xcode-Trust-Schritt (M2.4).
- `koin-androidx-compose-navigation` (commonMain-Dep) — iOS-Link prüfen, wenn Module geladen werden.
