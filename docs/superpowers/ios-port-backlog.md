# iOS-Port Backlog (Stand: … + Re-Sync + M4.0–M4.2 + Geräte-Gate + M4.3, 2026-06-29)

## ✅ M4.3 ABGESCHLOSSEN (2026-06-29) — Quest-Pin-Tap → Selektion + Highlight auf der iOS-Karte

Tippen auf einen Quest-Pin wählt ihn aus — **getreue Übersetzung von Androids `MainActivity.show­Quest­Details()`-Selektionssequenz, MINUS dem upstream-blockierten Antwort-Formular** (bleibt `// TODO`-Naht).
9 Commits `f7f7eceba`→`46a3a8b47` auf master/origin. Spec: `docs/superpowers/specs/2026-06-29-ios-port-m4.3-pin-tap-selection-design.md`, Plan: `docs/superpowers/plans/2026-06-29-ios-port-m4.3-pin-tap-selection.md`. Umgesetzt via subagent-driven-development (3 Implementer-Wellen + Welle-Review je sonnet + opus-Whole-Branch-Review), geerdet durch Investigation-Workflow `wf_a2027c0c-791`.

**Was es tut:** Tap → QuestKey dekodieren (`getQuestKey`) → Quest laden (`VisibleQuestsSource.get`, off-main) → gewählten Pin highlighten (`SelectedPinsLayer`, Bounce) + Geometrie umranden (`FocusedGeometryLayers`) + Kamera auf die Geometrie fokussieren (+ Restore bei Deselect, `savedCamera` einmal pro Kette) + andere Pins ausblenden (`PinsLayers(visible=false)` = Androids `setVisible(false)`). Cluster-Tap → Fallback-Zoom-in (keine `getClusterLeaves`-API auf iOS — bewusste Divergenz). `onMapClick` → Deselect.

**Architektur:** Selektions-State = ein `@Stable QuestSelection`-Holder im `MapViewModel` (`selectQuest`/`clearSelection`/`selectedQuest`; keine Koin-/Konstruktor-Änderung). `MapScreen` ist die Orchestrierungs-Schicht (wie Androids `MainActivity`). Quest-Beantwortung bleibt Naht (Commit-Pfad upstream-`TODO()`).

**Zwei Befunde in der Verifikation gefixt:** (1) **Punkt-Geometrie-Over-Zoom** — node-Quests (Mehrheit) haben Null-Flächen-BBox → `animateTo(boundingBox)` zoomt maximal (kontextlos). Fix (`4f2ab56ea`): Punkt → `animateTo(CameraPosition, zoom.coerceIn(18,20))`; Ways/Areas behalten bbox-Fit. **Von der Simulator-Verifikation gefangen.** (2) **maplibre-compose 0.13.0 dispatcht `onMapClick` VOR den Layer-Handlern** (Whole-Branch-Review) → Tap-während-selektiert deselektiert; **Android-treu** (Pins während Selektion ausgeblendet → Reselect = wegtippen-dann-tippen, kein Ein-Tipp; Spec-Szenario 4 war falsch, korrigiert). Persist-Effekt zusätzlich auf `selectedQuest == null` gegated.

**Gates:** Link `linkDebugFrameworkIosSimulatorArm64` GRÜN; **Simulator end-to-end GRÜN** (Pariser Platz, frische DB, synthetische Selektions-Injektion): `Created 482 quests` / `Finished download`, Screenshot zeigt hochskalierten Uhr-Pin + Geometrie-Kreis + andere Pins weg + Kamerafokus bei nutzbarem Zoom, kein Crash. **GERÄTE-ECHT-TAP-TEST OFFEN** (interaktiv, Nutzer — kein `idb`, Simulator-Taps nicht scriptbar).

**Offen / Follow-ups:** Geräte-Tap-Test (Nutzer); 2 kosmetische Minor zurückgestellt (Import-Reihenfolge, `focusPadding` ohne `remember` — kein ktlint-Gate); Ein-Tipp-Reselect als mögliche spätere Enhancement (un-Android, da Pins-sichtbar-halten nötig). **NÄCHSTES: M4.1b GPS-Auto-Sync.**

---

## ✅ GERÄTE-GATE BESTANDEN (2026-06-29) — re-synct M4 läuft auf dem iPhone 16 Pro

Das einzige nach dem Re-Sync offene Gate ist durch: das re-syncte M4 (master `4379d4e05`) **baut, signiert,
installiert, startet und lädt echte Daten auf der echten Hardware** — kein Code-/Signing-Problem hatte je
vorgelegen (vorher nur das gesperrte iPhone).

- **Build:** `xcodebuild -sdk iphoneos -destination 'platform=iOS,id=00008140-000228491A33001C'
  -derivedDataPath build/ios-device -allowProvisioningUpdates build` → **BUILD SUCCEEDED**. Profil frisch
  erneuert via `-allowProvisioningUpdates` (`iOS Team Provisioning Profile: …streetcomplete.ios`,
  `37192146-…`, TEAM `2DPUG448BC`); App + `StreetComplete.debug.dylib` + eingebettetes `MapLibre.framework`
  signiert; Geräte-SPM-Pfad `arm64-apple-ios/release` in FRAMEWORK_SEARCH_PATHS. (`rm -rf build/ios-device`
  davor — Relink-Fallstrick.) Run-Script-Phase setzt `JAVA_HOME` selbst → kein Shell-Env nötig.
- **Install + Launch:** `xcrun devicectl device install/launch` (Gerät `iPiotr`, devicectl
  `83B473E9-…`, bundle `de.westnordost.streetcomplete.ios`) → installiert + gestartet.
- **E2E auf dem Gerät (Düsseldorf, Konsolen-Log via `devicectl … --console`):** mehrere Downloads beim
  Pannen/Zoomen, **alle crashfrei** — z. B. `Created 829 quests for bbox in 2.1s` /
  `Finished download (0.5 km²) in 3.1s` / `Persisted 485 new … quests`, mit sauberer Per-Typ-Aufschlüsselung
  (`AddBuildingType: 116`, `AddMaxSpeed: 3`, `CheckShopExistence: 13`, …). **Keine Exception/Crash/FATAL** in
  1585 Log-Zeilen. **Nutzer-Sichtbestätigung 2026-06-29: „sieht alles gut aus"** (Karte + per-Typ-Pins).
- **Perf-Befund: Gerät ist drastisch schneller als der Debug-Simulator** — **1–3 s pro bbox** (statt ~90 s
  Quest-Erzeugung im Sim). Entschärft das M4-Perf-Follow-up weitgehend.

**OFFEN:** **origin-Push** weiterhin erst nach explizitem Nutzer-OK (Backup-Tag `pre-resync-2026-06-28` +
Branch `backup/pre-resync` behalten). Reine Entwicklungs-Follow-ups unverändert: M4.3 Pin-Tap, M4.1b
Auto-Sync, Upstream-PR (maplibre `addImage`), optionales Toast-Eyeballing. **Quest-Beantwortung weiter
Upstream-WIP-blockiert** (display-first bleibt korrekt).

---

## ✅ RE-SYNC auf neuen Upstream ABGESCHLOSSEN (2026-06-28) — Fork auf `compose-quest-form`-Tip rebased

Unsere komplette iOS-Port-Arbeit (M1–M4) wurde **linear per `git rebase --onto` auf den aktuellen
`upstream/compose-quest-form`-Stand neu aufgesetzt** (keine Merge-Commits). Link-Gate grün, Simulator
end-to-end verifiziert (Download + Quest-Pins). Sicherung: Tag `pre-resync-2026-06-28` + Branch
`backup/pre-resync` (alter Tip `d8972bf94`). Ledger: `.git/sdd/resync-progress.md`.

**Basiswahl (mit Nutzer bestätigt — Option A):** `upstream/compose-quest-form` (`4d6c6f092`, 2026-06-27),
unsere historische Basis (#6842). Begründung: cqf ist ein **Superset von master** (master hatte nur 5
Commits, die nicht in cqf sind) und enthält den 133-Commit-Quest-Form-Rewrite, auf dem unsere iOS-Quest-
Shims aufbauen. Ein Rebase auf `upstream/master` hätte den Rewrite weggeworfen → keine Option für iOS.
Mechanismus: `git rebase --onto upstream/compose-quest-form 1f0ea5dcf master` auf Branch `resync`, danach
`master` per Ref-Move auf `resync` (alter Tip gesichert).

**Replay:** 78 Commits, davon **3 automatisch gedroppt** (patch-id bzw. leer): `c6e06e863`/`70b2301bf`
(unsere master-Testfix-Cherrypicks — jetzt als `1025fbb3b`/`7e4179342` in cqf) und `91bcad9e8`
(CopyStringsTask-Fix — Upstream hat denselben Fix als `DOT_MATCHES_ALL` schon drin). Keine Merge-Commits.

**TOOLCHAIN-BUMP aus cqf (+47 Commits) — adoptiert, KEIN maplibre-fork-Rebuild nötig:**
Kotlin 2.3.20→**2.4.0**, Compose-Compiler 2.3.20→**2.4.0**, AGP 8.11.2→**8.13.2**, JetBrains-Compose
1.10.3→**1.11.1**, atomicfu 0.32.1→0.33.0, koin-bom 4.2.1→4.2.2, ktor 3.4.2→**3.5.0**, Kermit 2.1.0 neu,
Android-MapLibre→`android-sdk-opengl:13.3.0` (nur androidMain). Gradle-Wrapper unverändert (8.14, JDK 21).
**Der lokale `maplibre-compose:0.13.0-sc1`-Fork (gebaut mit Kotlin 2.3.21) ist mit Kotlin-2.4.0-Consumern
KOMPATIBEL — kein Republish nötig**; `spmForKmp 1.9.1`-cinterop läuft mit K/N 2.4.0. (Risiko war im Plan
markiert; Link-Gate hat es entkräftet.)

**Konflikte aufgelöst (intent-aware):**
- `buildSrc/CopyStringsTask.kt`: Upstream-Version genommen (enthält unseren `DOT_MATCHES_ALL`-Fix bereits
  + `MULTILINE`) → unser Commit redundant/gedroppt.
- `app/build.gradle.kts` plugins: **beide Seiten gemerged** — Upstreams Versions-Bumps + neue Plugins
  (`dev.mokkery`, `kotlin.plugin.allopen`) UND unser `id("io.github.frankois944.spmForKmp")`.
- `UrlConfigQRCodeDialog.kt` + `LoginScreen.kt`: **Upstream-Version genommen — die fallengelassenen Toasts
  sind WIEDER DA.** Upstream hat eine multiplatform `ToastPopup`-Lösung (`a6bb27c01`) implementiert, die
  unsere TODO-markierten „url copied"- + Login-Fehler-Toasts ersetzt (commonMain → läuft auch auf iOS).
- `OsmQuestFormContainer.kt`: unser `8bbe12bc3` (bare `TODO`→`TODO()`, `composeNote()`→`TODO()`) wurde von
  git auf den umbenannten Pfad (`bottom_sheet/quest/`) angewandt und ist **weiterhin nötig + korrekt** —
  cqfs Version hat dort weiterhin nicht-kompilierende bare `TODO` + ein in commonMain unauflösbares
  `composeNote()`. **Quest-ANTWORT-Commit-Pfad bleibt Upstream-WIP** (alles `TODO()`), iOS-Quest-Beantwortung
  also weiterhin blockiert; M4-„display-first" bleibt richtig.

**Zwei neue Integrations-Fix-Commits oben drauf (Link-Gate-validiert):**
- `Re-sync: dedup copyStringsToAndroid task …`: der Rebase-Auto-Merge hinterließ ZWEI identische
  `copyStringsToAndroid`-Task-Registrierungen (unsere + Upstreams ausgelagerte `0ab8d9b0c`) → Overload-
  Ambiguity, brach die Build-Skript-Kompilierung. Duplikat entfernt.
- `iOS: adapt locale helpers to Compose 1.11 …`: Compose 1.11 machte
  `androidx.compose.ui.text.intl.Locale.platformLocale` `internal`. Die geteilten iosMain-Locale/Date/
  Number-Formatter (Upstreams #6405 + NumberFormatter-Refactor) kompilierten nur auf iOS nicht mehr
  (Upstream baut iOS nicht). Fix: 11 Aufrufstellen in 8 Dateien auf neuen Helfer
  `Locale.toNSLocale()` = `NSLocale(localeIdentifier = toLanguageTag())` (verhaltensgleich) umgestellt.

**Gates:** (1) Link `:app:linkDebugFrameworkIosSimulatorArm64` **GRÜN** (3m15s, Kotlin 2.4.0).
(2) **Simulator end-to-end GRÜN** (frische DB, Pariser Platz/Berlin, zoom 16): `Created 480 quests in
7.0s` / `Finished download (0.5 km²) in 12.4s`; DB = **480 osm_quests / 40 Quest-Typen / 4 downloaded
tiles**; Screenshot zeigt **echte per-Typ-Pins** (Uhr/Preisschild/Häkchen) + SC-Vektor-Style +
Downloaded-Area-Hatching, kein Crash — identisch zur M4-Baseline. (3) **Geräte-Gate GRÜN (2026-06-29)** —
re-synct M4 auf dem iPhone 16 Pro gebaut/installiert/gestartet, Download+Quest-Erzeugung per Konsolen-Log
+ Nutzer-Sichtbestätigung verifiziert (Details in der Top-Sektion „GERÄTE-GATE BESTANDEN").

**Reconciliations no-op/bestätigt:** Kermit-Migration ließ das `Log`/`Logger`-Interface + `Log.instances`
unverändert → unser `IosLogger` + `Log.instances.add(IosLogger())` unverändert gültig. Drei geteilte
Bugfixes (XmlReaderSource, CountryInfos non-strict yaml, IosDownloadController-Crash-Guard) alle present.
MapViewModel-8-arg-Koin-Binding + koin-androidx-compose-navigation in androidMain intakt.

**FOLLOW-UPS aus dem Re-Sync:** ~~Geräte-Deploy~~ **ERLEDIGT 2026-06-29** (siehe Top-Sektion); origin-Push
erst nach Nutzer-OK (Backup-Tag/Branch behalten); optional: Toast-Restores auf iOS interaktiv eyeballen
(Login-Fehler / URL-kopiert). Bestehende M4-Follow-ups (Auto-Sync M4.1b, Pin-Tap M4.3, Upstream-PR)
unverändert offen; **M4-Perf weitgehend entschärft** (Gerät 1–3 s/bbox statt ~90 s im Sim).

---

## ✅ M4.0 + M4.1 + M4.2 ABGESCHLOSSEN (2026-06-28) — echte OSM-Daten + Quest-Pins auf der iOS-Karte

**Das Kernziel ist erreicht (Simulator-verifiziert):** Karte öffnen → Daten für den sichtbaren Bereich
werden heruntergeladen → **echte Quest-Pins mit korrekten per-Quest-Typ-Icons erscheinen** (Uhr / Preis-
schild / Häkchen / …). Spec: `docs/superpowers/specs/2026-06-28-ios-port-m4-data-and-quests-design.md`,
Plan: `docs/superpowers/plans/2026-06-28-ios-port-m4-data-and-quests.md`. Umgesetzt via
subagent-driven-development (fresh implementer + Spec/Quality-Review je Task) + Whole-Branch-Review
(opus) = **READY TO MERGE** (kein Critical/Important).

**E2E-Beweis (Simulator, frische DB, Berlin/Pariser Platz):** `Finished download in 4.0s`; DB =
25655 nodes / 4365 ways / 120 relations / 2 notes / **480 osm_quests über 40 Quest-Typen** / 4
downloaded_tiles; Screenshot zeigt echte per-Typ-Pins. M4.0-Icons + M4.1-Download + M4.2-Pins arbeiten
end-to-end zusammen.

### M4.0 — Quest-Pin-Icon-Registrierung (Commits `ef76111d6`)
- **maplibre-compose 0.13.0 hatte `Style.addImage(id, ImageBitmap, sdf, …)` schon implementiert + public**
  — nur unerreichbar, weil `StyleState.styleNode` `private` ist. **2-Methoden-Patch** (`StyleState.addImage`/
  `removeImage` Passthrough) auf einem **lokalen Fork** (`/Users/piotr/git/maplibre-compose-fork`, Tag
  `v0.13.0-sc1` auf Commit `afc5c828`). **Konsum via `publishToMavenLocal` (Option D)**, NICHT Composite-Build:
  hält die StreetComplete-Toolchain unangetastet (Gradle 8.14 / AGP 8.11.2 / Kotlin 2.3.20) — der Composite
  hätte einen App-weiten Gradle-9/AGP-9-Zwang + eine Android-Resolve-Regression erzeugt. `mavenLocal()` in
  **beiden** Repos-Blöcken (settings.gradle.kts + app/build.gradle.kts — der App-Block überschreibt die
  settings-Repos für `:app`), Dependency auf `…:0.13.0-sc1`.
  ⚠️ **REPRODUZIERBARKEIT:** `0.13.0-sc1` ist nur lokal. Ein frischer Checkout/CI muss den Fork zuerst
  publishen (Sibling `../maplibre-compose-fork` @ Tag `v0.13.0-sc1`, JDK 21, ANDROID_HOME) — dokumentiert in
  Commit + Build-Datei-Kommentaren. **Upstream-PR steht noch aus (Task 0.4, non-blocking).**
- **commonMain `PinImage.kt`:** Port von Androids `createPinBitmap` auf eine Compose-Canvas (Schatten + Pin
  + Quest-Icon, 71dp, non-SDF) + `rememberPinBitmap` + `DrawableResource.pinImageName()` (Name↔Resource
  über `Res.allDrawableResources`, Round-Trip garantiert) + `@Composable PinImageRegistry` (lazy, dedup
  pro `(styleEpoch, name)`, Re-Registrierung bei Style-Reload via `onMapLoadFinished`-Epoch).
- Simulator-verifiziert: synthetischer Recycling-Pin rendert (Icon, nicht leer).

### M4.1 — Daten-Download (Commits `73a82349b`, `8d31cc343`, `2e2b4998d`, `538118b81`)
- **`ApplicationScope`** (Koin single, `SupervisorJob + Dispatchers.Default`) + **`Preloader.preload()`** beim
  Start (hinter dem `koinStarted`-Guard in `initKoin()`) — wärmt CountryBoundaries + FeatureDictionary.
- **Echter `IosDownloadController`** (ersetzt den Stub): launcht den geteilten suspend `Downloader` auf der
  App-Scope mit Android-Parität REPLACE/KEEP + **Crash-Guard** (try/catch, `CancellationException`
  rethrow, `@Volatile job`) — ein fehlgeschlagener Download loggt statt die App zu crashen.
- **Trigger:** `MapScreen` emittiert die sichtbare BBox bei Kamera-Idle (zoom ≥ 14; `projection` im
  `collect` lesen, nicht im snapshotFlow-Producer) + **`awaitProjection()`-Initial-Fire**, damit beim
  statischen Karten-Öffnen ohne Pan heruntergeladen wird. `MapViewModel.onViewportIdle` triggert den
  user-initiated Download mit Session-`contains`-Guard.
- **ZWEI echte Bugs entlarvt + gefixt (commonMain, plattformneutral):**
  1. **K/N-XML-Parser-Bug** (`2e2b4998d`): xmlutils `kxio.newReader(Source)` **dekodiert UTF-8 auf
     Kotlin/Native falsch** — stirbt am ersten Mehrbyte-Zeichen ("ö") mit `XmlException: End of document`.
     **Hat JEDEN OSM-API-Download auf iOS gekillt.** Fix = `data/XmlReaderSource.kt`
     (`xmlStreaming.newReader(source.readString())`, also erst zu UTF-8-String dekodieren), 4 Parser
     migriert (Notes, MapData, User, WeeklyOsm-RSS). **WICHTIGE LEHRE:** Der „72125 von 81833 Bytes"-
     Trunkierungs-Verdacht war ein **Red Herring** — die Darwin-Engine liefert den vollen Body; ein custom
     NSURLSession-Engine war weder nötig noch hinreichend. Immer per Isolation beweisen.
- Simulator-verifiziert: Download landet echte Daten in der DB (25655 nodes etc.) + `DownloadedAreaLayer`.

### M4.2 — Quest-Pins (Commits `325a1ec92`, `52a78c2da`, `25c95728f`, `f2cde3775`)
- **Separater commonMain `QuestPinsManager`** (Paket `screens.main.map2`, **null maplibre-compose-Imports**):
  treuer Port des androidMain-`QuestPinsManager` (z16-TilesRect-Windowing, Perf-Guard size>32,
  `contains`-Check, Pan-Debounce [updateJob cancel vs onUpdated join], Multi-Pin-Retention-Regel, eine Pin
  pro `markerLocations`, alle `MARKER_*` + `toProperties`/`toQuestKey` wörtlich, inkl. `OsmNoteQuest`,
  `Dispatchers.IO` für `getAll`). Exponiert `StateFlow<List<Pin>>`.
- **`MapViewModel`-Integration:** injiziert `VisibleQuestsSource` + `QuestTypeRegistry` +
  `QuestTypeOrderSource` (das **instanziiert den `OsmQuestController`**, dessen Listener Quests beim
  Download erzeugt), baut den Manager mit `viewModelScope`, sammelt `manager.pins` → `setPins`, treibt den
  Viewport aus `onViewportIdle` (vor dem Download-Guard, damit Pins auch beim Pannen im bereits geladenen
  Bereich aktualisieren — Review-Fix `f2cde3775`), `getQuestKey`-Passthrough, `stop()` in `onCleared`. Den
  synthetischen M4.0-Pin entfernt. 8-arg Koin-Binding.
- **DRITTER echter Bug gefixt** (`25c95728f`): `CountryInfos.load()` rief `readYamlOrNull(path)` **ohne** das
  eigene non-strict `yaml` → der Default-**strikte** `Yaml.default` warf `kaml UnknownPropertyException`
  (`chargingStationSocketTypes`, ein Metadaten-Key der nicht in `CountryInfo` modelliert ist) → **brach die
  Quest-Erzeugung ab + rollte die Download-Transaktion zurück** (osm_quests=0, keine Pins). Fix: das
  non-strict `yaml` durchreichen (entspricht der dokumentierten Absicht; plattformneutral).

**NOCH OFFEN — GERÄTE-GATE (Nutzer-Aktion nötig):** Der Geräte-Build/-Deploy schlug fehl, weil das **iPhone
gesperrt** ist („device is passcode protected" / „developer disk image could not be mounted" / destination
timed out) — KEIN Code-/Signing-Problem. Die Geräte-Arch (`linkDebugFrameworkIosArm64`) kompiliert sauber,
also ist der Code deploy-bereit. **Zum finalen Geräte-Gate: iPhone entsperren (+ entsperrt/vertraut halten),
dann erneut deployen** (`-allowProvisioningUpdates`, TEAM 2DPUG448BC; Profil lief 2026-06-21 ab → wird
erneuert). Visuelle Pin-Bestätigung auf dem Gerät ist dann Nutzer-Sache (Map öffnen, Ortung erlauben).

**ZURÜCKGESTELLT / FOLLOW-UPS:**
- **Quest-Erzeugung ist auf dem Debug-Simulator langsam** (~90s für ~200 Quest-Typen über 25k nodes;
  `CheckShopExistence` allein 7,7s) und liegt auf dem **Download-Critical-Path** (downloaded_tiles/osm_quests
  committen erst am Ende). Funktioniert, aber Release/Gerät ist schneller; Perf-Iteration vorgemerkt (Kickoff
  hatte das markiert).
- **Whole-Branch-Review-Minors (kein Merge-Blocker):** unused `import kotlinx.coroutines.cancel` in
  QuestPinsManager; `MapViewModel.setPins`/`getQuestKey` sind aktuell tote öffentliche API (M4.3-Hooks);
  Import-Reihenfolge in NotesApiParser/MapDataApiParser.
- **M4.1b Auto-Sync** (GPS-getriebener Download beim Laufen, `IosQuestAutoSyncer` + NWPathMonitor) — wie im
  Kickoff (Decision 1) geplant, noch offen.
- **M4.3 Pin-Auswahl** (Tap → `getQuestKey` → `SelectedPinsLayer`-Highlight, display-only) — Stretch, offen.
- **Upstream-PR** für den maplibre-compose `StyleState.addImage`-Patch (Task 0.4) — vorbereitet, noch zu öffnen.

---

# iOS-Port Backlog (Stand: M1 + Re-Sync + M2 + M3a + M3b.1–M3b.3, 2026-06-18)

## ✅ M3a ABGESCHLOSSEN — iOS-Navigationsgerüst + Menü-Screens (ohne Karte)

Die iOS-App rendert jetzt ein geteiltes commonMain-`MainMenuNavHost` (Menü → About / Settings /
User-Login) statt nur der nackten AboutScreen. Commits auf `ios-port` (Range `9e3b527de..e445caa68`):
`47be236` (M3a.1 AboutNavHost), `203399134` (M3a.2 vollständiger Koin-Graph + Stubs), `7c6b1a6`
(VisibleQuestsSource-Binding nach commonMain), `7042c7c` (M3a.3 Menü-Shell + Settings; enthält auch
das M3a-Plandokument), `ef3d444` (M3a.4 User + OAuth-Login), `e445caa` (Fix iOS-compose-resources-Pfad).

**Was funktioniert (Simulator-verifiziert, iPhone 16 Pro sim — durch erzwungenes Rendern je Screen):**
- Menü → About (Changelog/Credits/Privacy/Logs/Tutorial), Settings, User/Login — erreichbar.
- Settings-Liste rendert (SettingsViewModel + QuestTypeRegistry mit ~190 Quests aufgelöst).
- Settings → Quest-Auswahl rendert (CountryBoundaries aus `boundaries.ser`, FeatureDictionary aus
  `osmfeatures`, QuestTypeRegistry — alle aus dem App-Bundle aufgelöst).
- Login-Screen rendert (UserViewModel/LoginViewModel; multiplatform-WebView).

**iOS-Koin-Graph:** `InitKoin.kt` lädt jetzt den vollständigen commonMain-Modulsatz (gespiegelt von
Android `StreetCompleteApplication`), MINUS die 4 androidMain-only Module (appModule, mainModule,
questModule, androidModule), PLUS `iosModule` + `iosControllersModule`. `iosControllersModule` =
No-op-Stubs für MapTilesDownloader/UploadController/DownloadController/ChangesetAutoCloser (nur
MapTilesDownloader wird tatsächlich erreicht: SettingsViewModel→Cleaner). `InternetConnectionState`
bewusst NICHT gestubt (von iOS unerreichbar — nur androidMain-Code nutzt es). `VisibleQuestsSource`-
Binding von androidMain `questModule` nach commonMain `visibleQuestsModule` verschoben (auf Android
verhaltensneutral, auf iOS jetzt auflösbar) — sonst Crash beim Öffnen von Settings
(MessagesSource→VisibleQuestsSource).

**Source-Set-Moves (androidMain → commonMain, per git mv):** SettingsScreen, SettingsNavHost,
EditTypePresetsScreen, EditTypePresetRow, UrlConfigQRCodeDialog, UserScreen, UserNavHost, LoginScreen.
`koinViewModel`-Import dabei von `org.koin.androidx.compose` auf `org.koin.compose.viewmodel`
umgestellt. `SettingsActivity`/`UserActivity` bleiben in androidMain (Android-Einstiegspunkte;
androidMain darf commonMain referenzieren).

**Wichtiger Fix (`e445caa`):** `MetadataModule.ios.kt` las `boundaries.ser`/`osmfeatures` unter
`resourcePath/compose-resources/files/`, tatsächlich liegen compose-resources aber unter
`compose-resources/composeResources/<packageOfResClass>/files/` (hier
`de.westnordost.streetcomplete.resources`). Bestand seit M2, fiel erst auf, als ein
metadaten-abhängiger Screen (Quest-Auswahl) erreichbar wurde → FileNotFoundException/Crash.
**Lehre:** compose-resources, die NICHT über `Res` sondern als rohe Dateien (kotlinx.io) gelesen
werden, brauchen den vollen verschachtelten Pfad.

**Offene Punkte / für M4 vorgemerkt:**
- **OAuth-Login-Happy-Path NICHT verifiziert:** Login-Screen rendert, aber der WebView-Round-Trip
  (OSM-Authorize → `streetcomplete://oauth`-Interception → Token) wurde nicht getestet (braucht
  Interaktion + Netz + echtes OSM-Login → manueller Gerätetest). Die Interception-Logik ist bereits
  multiplatform (compose-webview-multiplatform 2.0.3, in `LoginScreen.kt` unverändert).
- **About → Logs ist auf iOS quasi leer:** kein DatabaseLogger-Sink auf iOS registriert (nur
  IosLogger → Konsole). Screen rendert, zeigt aber kaum Einträge. Einfacher Follow-up: in
  `initKoin()` einen iOS-DatabaseLogger zu `Log.instances` hinzufügen (DatabaseLogger ist commonMain).
- **Toast-Feedback verloren:** LoginScreen (Login-Fehler) und UrlConfigQRCodeDialog (URL kopiert)
  hatten Android-Toasts; entfernt + per TODO markiert, da es noch keine multiplatform Toast/Snackbar-
  Utility gibt (Upstream hat dasselbe TODO). Wiederherstellen, sobald eine existiert.
- **Nach Login springt UserNavHost nicht automatisch weiter:** `startDestination` wird einmalig
  ausgewertet; nach erfolgreichem Login bleibt der Spinner bis man zurück ins Menü geht (vorbestehend,
  auf iOS sichtbarer).
- **Gerätedeployment des M3a-Builds steht aus** (Personal-Team-Profil läuft 2026-06-21 ab).
- ShowQuestForms-Debug-Pfad ist auf iOS via `BuildConfig.DEBUG=false` ausgeblendet — nicht erreichbar.

**Restrisiko „verdrahtet aber nicht interaktiv getestet" (Reihenfolge nach Risiko):** About→Logs
(leer, FileKit-Share ungetestet) · Settings→Presets→QR-Share-Dialog (qrose + UrlConfig ungetestet) ·
User-Tabs nach Login (Avatar-Download; nur per echtem Login erreichbar) · Overlay/Language/Messages-
Auswahl (einfache Listen, geringes Risiko).

## ✅ M3b-Spike ERLEDIGT (2026-06-14) — maplibre-compose auf iOS: **GO**

`org.maplibre.compose:maplibre-compose:0.13.0` kompiliert, linkt, bindet das native MapLibre-Framework
ein und rendert auf dem iOS-Simulator (arm64, Metal aktiv, kein Crash) im exakten Fork-Stack
(CMP 1.10.3, Kotlin 2.3.20, Koin 4.2.1). Verifiziert per Screenshot (MapLibre-Attribution/Info-Button
auf lebender Map-Surface) + Logs. Der Spike war exploratorisch und wurde vollständig zurückgerollt
(Tree clean); das Ergebnis ist dieses Rezept. Vollständiger Report: Task-Output des Agents `m3b-spike`.

**Version:** 0.13.0 (neueste; Maven Central hat 0.11.0/0.11.1/0.12.0/0.12.1/0.13.0). 0.13.0s POM pinnt
Compose-Foundation 1.10.3 + lifecycle 2.10.0 + Kotlin-stdlib 2.3.21 → fast exakt unser Stack (besser
als #6352s 0.11.1 gegen Compose 1.10.0). iOS-Artefakte nur für **iosArm64 + iosSimulatorArm64** (KEIN iosX64).

**iOS-Integrations-Rezept (das eigentliche Spike-Ergebnis — #6352 lief NIE auf iOS):**
1. **Natives `MapLibre.framework` kommt via SPM, NICHT aus dem Maven-Artefakt** (das klib enthält nur
   das cinterop-Binding). Plugin **`io.github.frankois944.spmForKmp` Version `1.9.1`**; natives MapLibre
   `maplibre-gl-native-distribution` **6.25.1** (was 0.13.0 testet).
2. **`iosX64()` aus den Targets entfernen** (0.13.0 publiziert es nicht; spmForKmp-cinterop-Task bricht
   sonst hart ab). iosX64 = veralteter Intel-Sim, gefahrlos streichbar.
3. **`koin-androidx-compose-navigation` von commonMain → androidMain verschieben** (Android-only-AAR ohne
   Native-Varianten; die strikte cinterop-Konfiguration scheitert sonst. Vorbestehender latenter Bug;
   kein commonMain-Code nutzt dessen API).
4. **build.gradle.kts:** in der iOS-Target-Schleife pro Target `iosTarget.swiftPackageConfig(cinteropName
   = "maplibreNative") { dependency { remotePackageVersion(url = URI("https://github.com/maplibre/
   maplibre-gl-native-distribution.git"), products = { add("MapLibre", exportToKotlin = true) },
   packageName = "maplibre-gl-native-distribution", version = "6.25.1") } }` + `linkerOpts("-F$rpath",
   "-rpath", rpath)` mit `rpath = build/spmKmpPlugin/maplibreNative/scratch/<variant>/release/`
   (variant: iosArm64→arm64-apple-ios, iosSimulatorArm64→arm64-apple-ios-simulator). Imports
   `io.github.frankois944.spmForKmp.swiftPackageConfig` + `java.net.URI`. Plugin-Block:
   `id("io.github.frankois944.spmForKmp") version "1.9.1"`.
5. **Xcode `FRAMEWORK_SEARCH_PATHS`** (Debug + Release-Gerät) auf den SPM-Build-Pfad zeigen lassen:
   `$(SRCROOT)/../app/build/spmKmpPlugin/maplibreNative/scratch/arm64-apple-ios-simulator/release` (Sim)
   bzw. `.../arm64-apple-ios/release` (Gerät) — Pfad ist nach **cinteropName** `maplibreNative` benannt,
   NICHT nach Target. Ohne den Pfad: `framework 'MapLibre' not found` / undefined `_OBJC_CLASS_$_MLNMapView`.
   Einbettung in die .app passierte automatisch (landet in `StreetComplete.app/Frameworks/`, lädt via
   bestehendem `@executable_path/Frameworks`-rpath) — keine manuelle Embed-Phase nötig.
6. Plugin erzeugt `app/src/swift/maplibreNative/StartYourBridgeHere.swift` (Bridge-Stub) → in M3b
   committen oder gitignoren. Metal läuft auf dem Sim ohne Info.plist-/Entitlement-Änderung.

**API 0.13.0:** `MaplibreMap(modifier, baseStyle: BaseStyle = BaseStyle.Demo, cameraState =
rememberCameraState(), styleState = rememberStyleState(), onMapClick, …, content)`. `BaseStyle`:
`Uri(String)`/`Json(...)`/`Demo`/`Empty`. `rememberCameraState(firstPosition: CameraPosition)`;
`CameraPosition(bearing, target: Position, tilt, zoom, padding)`; `Position(longitude=, latitude=)`
(GeoJSON lon/lat-Reihenfolge, benannte Args nutzen). Packages `org.maplibre.compose.{map,camera,style,
layers,sources,expressions}` — wie in #6352, dessen `map2/Map.kt`+`MapStyle.kt` mit minimalen
Signatur-Anpassungen portierbar sind.

**Tiles:** im Spike NICHT sichtbar gerendert — `BaseStyle.Demo` (demotiles.maplibre.org) lieferte
wiederholt NSURLError -999 (cancelled). Map-Surface + Ornamente rendern, kein Crash. In M3b einen echten
Vektor-Style (z. B. JawgMaps aus der #6352-Referenz) nutzen, um Tiles zu verifizieren.

**M3b-Plan:** `docs/superpowers/plans/2026-06-14-ios-port-m3b-map-and-location.md` (Task 1 voll
ausführbar aus dem Spike-Rezept; Task 2 Layer-Port + Task 3 Location als Outline).

### ✅ M3b.1 ERLEDIGT (2026-06-14) — erste sichtbare iOS-Karte
Das obige Integrations-Rezept umgesetzt + eine echte Vektorkarte gerendert. Commits `e10bbab8`
(Integration + MapScreen + Menü-Eintrag „Map") und `58c3bee4` (Back-Navigation auf der Karte).
Simulator-verifiziert: **echte JawgMaps-Vektorkacheln rendern** (Berlin: Straßen, Labels, Spree,
Parks), `MapLibre.framework` wird automatisch in die `.app` eingebettet, Metal aktiv, kein Crash,
Back-Button (Chevron) oben links. Umgesetzt: spmForKmp-Plugin + natives MapLibre 6.25.1 via SPM,
`iosX64` aus kotlin-Targets UND buildkonfig-Loop entfernt, `koin-androidx-compose-navigation`
commonMain→androidMain, maplibre-compose 0.13.0 in commonMain, Xcode-FRAMEWORK_SEARCH_PATHS (Sim+Gerät),
`/app/src/swift/` gitignored (spmForKmp-Bridge-Stub). MapScreen ist vorerst ein Menü-Eintrag; in einem
späteren Increment wird die Karte der echte Hauptbildschirm. JawgMaps-Token noch im Quellcode (TODO,
wie im Android-Build). **GERÄTE-VERIFIZIERT 2026-06-14:** HEAD aufs physische iPhone 16 Pro deployt
(deckt M3a + M3b.1 ab) — App startet ins Menü, **die Karte rendert echte Tiles auf dem Gerät**;
`MapLibre.framework` wird in die Geräte-`.app` eingebettet, der Geräte-SPM-Pfad `arm64-apple-ios`
funktioniert. **OAuth-Login GERÄTE-VERIFIZIERT 2026-06-14:** My Profile→Login→OSM-WebView→
`streetcomplete://oauth`-Interception→Token→eingeloggt, Account-Tab sichtbar auf dem iPhone. War das
größte iOS-Unbekannte (Redirect-Interception in der WebView) — funktioniert end-to-end auf dem Gerät.
Damit sind M3a + M3b.1 vollständig gerätebestätigt.

### ✅ M3b.2 ERLEDIGT (2026-06-16) — eigener Vektor-Map-Style + Kamera + Layer-Stack in commonMain
Plan: `docs/superpowers/plans/2026-06-16-ios-port-m3b2-map-style-and-layers.md` (5 Increments, je
Implementer + Spec- + Quality-Review + Simulator-Screenshot). Was geliefert wurde:
- **M3b.2a** (`bb03da67b`): StreetComplete's **eigener programmatischer Vektor-Style** rendert auf iOS
  (statt des generischen JawgMaps-Hosted-Style) — Land/Wasser/Straßen-Casing/Labels in SC-Farben, mit
  lokal gebündelten Roboto-Glyphs (512 `.pbf`). Port von `MapColors`/`ExpressionUtils`/`MapStyle` +
  `map_*`-Drawables aus `upstream/maplibre-compose`. Simulator-verifiziert (Berlin).
- **M3b.2b** (`d358d0f46`): **Kamera-Persistenz** via `Preferences` (`mapPosition/Zoom/Rotation/Tilt`) —
  seed beim Öffnen, speichern bei Bewegungs-Ende (`snapshotFlow`). Seed-Pfad verifiziert (NSUserDefaults
  vorbeschrieben → Karte öffnet auf Paris).
- **M3b.2c** (`385efb9c8`): kompletter **Layer-Stack** (`GeometryUtils` + 9 Layer-Composables) nach
  commonMain portiert, kompiliert. Reconciliations: geojson `io.github.dellisd`→`org.maplibre.spatialk`,
  `Feature<Geometry,JsonObject?>` (Properties als `JsonObject`), android-`log2`-Import raus,
  `image(feature["k"].asString())`, `StyledElement`-Context-Code entfernt.
- **M3b.2d** (`1511dae6d`): **`MapViewModel`** (commonMain, Koin `mapModule` in `InitKoin`) + 3 Layer
  verdrahtet (downloaded-area / geometry-markers / focused-geometry). Synthetisch verifiziert (Hatching +
  pulsierender Focus-Kreis). `getAll` läuft via `withContext(Dispatchers.IO)` (Parität mit androidMain).
- **M3b.2e** (`ed1466cb1` + Fix `94b80d204`): **Quest-Pins-Layer** verdrahtet (VM `pins`-Flow + Setter).
  Synthetik-Verifizierung fing einen echten Runtime-Crash: `PinsLayers`/`SelectedPinsLayer` setzten
  `iconPadding` mit **negativen** Werten → in maplibre-compose 0.13.0 wirft Compose `PaddingValues.Absolute`
  „Padding must be non-negative" (Link-Gate grün, crasht aber beim Composen!). Fix = auf 0 geklemmt. Danach:
  Cluster-Pin „6" rendert + leere Produktions-Karte rendert crashfrei. Gotcha in der Memory festgehalten.

**Bewusst ZURÜCKGESTELLT in M3b.2** (auf iOS noch keine OSM-Daten [kein Download] und keine Icon-Registry):
die **Daten-Orchestrierung** (VisibleQuestsSource/SelectedOverlaySource/MapDataWithEditsSource → bbox →
getAll → Pins/StyledElements bauen — nicht ohne echte Daten verifizierbar), die **per-Quest-Typ-Pin-Icons**
(Icon-Registry/Rasterisierung fehlt), sowie **SelectedPinsLayer + StyleableOverlay-Layer-Verdrahtung**.
Diese sind portiert/kompilieren, aber nicht in `Map()` verdrahtet. Layer-Setter (`putGeometryMarkers`/
`setFocusedGeometry`/`setPins`) bilden die saubere Integrations-Schnittstelle für später.

**Geräte-Build + Install + LAUNCH VERIFIZIERT (2026-06-16):** `xcodebuild -sdk iphoneos` (arm64-apple-ios-
SPM-Pfad) **BUILD SUCCEEDED** mit dem kompletten neuen Layer-Code; App via `devicectl` aufs iPhone 16 Pro
**installiert und gestartet** (läuft auf echter Hardware).

**Zwei Dark-Mode-Folgefixes (2026-06-16, nach Nutzer-Feedback):**
1. **Menü-Schriften unsichtbar im Dark Mode** (das eigentlich gemeldete Problem — „Schriften in den Menüs
   kaputt"): `SettingsScreen` (und andere Screens) nutzen ein nacktes `Column` ohne `Scaffold`/`Surface`, daher
   erbte der Text den Compose-Default `LocalContentColor` = **schwarz** in BEIDEN Modi → im Dark Mode unsichtbar
   auf dunklem Grund. Das Hauptmenü war ok, weil es ein `Scaffold` (→ themed `Surface`) nutzt. Auf Android liefert
   die Activity diese Surface; auf iOS (`ComposeUIViewController` + `AppTheme`) fehlte sie. **Fix:** `MainViewController`
   wrappt den Inhalt in `Surface(color = MaterialTheme.colors.background)` → korrekte `onBackground`-Contentfarbe für
   ALLE Screens. Settings hell+dunkel verifiziert (alle Zeilen-Labels sichtbar).
2. **Karte komplett schraffiert (`565d69f6d`):** der `DownloadedAreaLayer` schraffiert ohne heruntergeladene Tiles
   die GANZE Karte (auf hellem Land kaum sichtbar, auf dunklem Earth prominent). **Fix:** Layer nur rendern
   `if (downloadedTiles.isNotEmpty())`. Beide Modi sauber.
(Hinweis: dass die Karte sonst KEINE Pins/Overlays zeigt, ist korrekt — keine OSM-Daten auf iOS.)

**NOCH OFFEN (interaktiv, Nutzer prüft):** Pan/Zoom→Kill→Relaunch→Kamera-Wiederherstellung auf dem Gerät.
Profil läuft 2026-06-21 ab (`-allowProvisioningUpdates`).

**Nächster Schritt:** M3b.3 (Location-Dot: CLLocationManager + neues commonMain-LocationManager/Compass-
Interface + `NSLocationWhenInUseUsageDescription`) — bringt erstmals **echte Daten** auf die Karte (GPS).
Danach evtl. Daten-Orchestrierung + Icon-Registry, sobald Download auf iOS existiert. Offline-Tiles
bleiben zurückgestellt (#6072).

---

### ✅ M3b.3 ERLEDIGT (2026-06-18) — Live-Standort-Punkt + Heading-Pfeil + GPS-Button (Android-Parität)
Design: `docs/superpowers/specs/2026-06-18-ios-port-m3b3-location-and-heading-design.md` ·
Plan: `docs/superpowers/plans/2026-06-18-ios-port-m3b3-location-and-heading.md`. Erste **echten Geräte-
Daten** auf der Karte (GPS + Kompass). Via subagent-driven-development (4 Code-Tasks, je Link-Gate +
Simulator + Spec-/Quality-Review; Whole-Branch-Review (opus) = **ready-to-merge**).

**Architektur (Variante B, mit Nutzer entschieden):** zwei **dünne** commonMain-Interfaces
`LocationSource` + `Compass` (Koin **nur auf iOS** gebunden — `mapModule`/`MapViewModel`/`map2` werden auf
Android nie geladen, daher keine Android-Änderung). Die ganze Zustandslogik (`LocationState`, Follow-,
Navigations-Modus) lebt im `MapViewModel`/`MapScreen` — gespiegelt von Androids dummen Wrappern
`FineLocationManager` + `Compass`, wo `MainActivity`/`MainViewModel` orchestrieren.

**Commits:**
- `7d99b50ba` (Task 1): `LocationSource` + `IosLocationSource` (CLLocationManager) + additives
  `Location.bearing: Float?` + `iosLocationModule` + `NSLocationWhenInUseUsageDescription`.
- `ee9812b85` (Task 2): `MapViewModel`-Zustandsmaschine (location-Flow, `LocationState`-Ableitung via
  `combine(hasPermission, trackingRequested, location)` + `SharingStarted.Eagerly`, Follow persistiert).
- `cb22a7b51` (Task 3, **M3b.3a**): Dot + `LocationStateButton` + Follow/Recenter + First-Fix-Zoom (→18 wenn
  <17) + Pan→Follow-aus (via `CameraMoveReason.GESTURE`). Simulator-Screenshot-verifiziert (Dot + Accuracy-
  Kreis am Pariser Platz). Hier auch `accuracy` auf `>=0` geklemmt (CLLocation `horizontalAccuracy` ist bei
  ungültigen Fixes negativ → maplibre `CircleLayer`-Radius crasht sonst — vgl. M3b.2e-Gotcha).
- `639852820` (Task 4, **M3b.3b**): `Compass` + `IosCompass` (CLLocationManager-Heading) + Heading-Pfeil +
  Navigations-Modus (Tilt 60° + Karten-Rotation zur GPS-Fahrtrichtung).

**iOS-Impl-Details:** `IosLocationSource` + `IosCompass` = **zwei** Klassen mit je eigenem
`CLLocationManager` (vermeidet den `start()/stop()`-Methodennamen-Clash einer Einzelklasse + spiegelt
Androids 2-Klassen-Split). Beide Delegates als starkes `private val`-Feld gehalten (gegen die schwache
`delegate`-Referenz des Managers). `elapsedDuration` aus `NSProcessInfo.systemUptime` (monoton, wie Androids
`elapsedRealtimeNanos` — `RecentLocations` rechnet nur Deltas). Heading aus `trueHeading` (iOS wendet die
Deklination bereits an → kein `GeomagneticField` nötig, anders als Android), Fallback `magneticHeading` wenn
`trueHeading < 0`. Pfeil-Rotation = `Heading − Kamera-Bearing` (kamerarelativ, exakt wie Androids
`CurrentLocationMapComponent`).

**Bewusste Entscheidungen / Abweichungen:**
- **`ALLOWED` in `DENIED` kollabiert** (Button verhält sich identisch — gleiches Icon, gleiche „Permission
  anfragen"-Aktion). Der seltene Fall „Permission erteilt, aber globale Ortungsdienste aus" wird nicht separat
  modelliert.
- **Nav-Bearing aus `CLLocation.course`** (GPS-Fahrtrichtung) statt Androids `getTrackBearing()` → spart den
  Port des `osmtracks`-Track-Recording-Subsystems; dafür das additive `Location.bearing`.
- Whole-Branch-Review bestätigte: **`iconRotate`/`SymbolLayer` hat in maplibre-compose 0.13.0 KEINE
  Range-Validierung** (negative/>360 Rotation ist safe — die Negativ-Ablehnung betraf nur `PaddingValues`/
  icon-padding aus M3b.2e). K/N-Threading safe (Delegate-Callbacks auf Main → `MutableStateFlow` → Compose).

**Geräte-Build + Install + LAUNCH VERIFIZIERT (2026-06-18):** `xcodebuild -sdk iphoneos` (arm64-apple-ios
SPM-Pfad) **BUILD SUCCEEDED** mit dem neuen Compass-/Location-Code; via `devicectl` aufs iPhone 16 Pro
installiert und gestartet. (Profil läuft 2026-06-21 ab; `-allowProvisioningUpdates` erneuert es.)

**NOCH OFFEN (interaktiv, nur auf Gerät prüfbar — Nutzer):** Permission-Prompt → Allow; Dot an echter
Position + Auto-Zoom/Follow; **Heading-Pfeil dreht beim physischen Drehen des Telefons**; **Navigations-Modus**
(3. Tipp: Tilt + Karte rotiert zur Fahrtrichtung; nochmal Tippen: Tilt weg, Bearing bleibt); Pan → Follow aus.
(Kompass + Nav sind auf dem Simulator NICHT verifizierbar — kein Magnetometer, 3-Tipp-Toggle nicht skriptbar.)

**Code-Review (xhigh, `--fix`, Commit `0765e857f`):** 10-Winkel-Review des M3b.3-Diffs (alle Findings
bestätigt, auf 7 Ursachen reduziert), Fixes angewandt + Simulator-verifiziert (Dot rendert weiter):
(1) `IosLocationSource.distanceFilter = 1m` (Android-Parität) — GPS-Rausch-Samples brachen sonst ständig die
Follow-Animation ab; (2) `stop()` setzt `_location = null` — der Singleton überlebt das VM, sonst sprang die
Kamera beim Wieder-Öffnen auf eine veraltete Position; (3) `zoomedYet` erst NACH `animateTo` gesetzt — ein Fix
mitten im Initial-Zoom strandete die Kamera sonst bei Zwischen-Zoom; (4) Kamera-Persistenz NICHT während Follow
(schrieb sonst Tilt=60/Heading ~1 Hz in die Prefs); (5) Nav-Toggle nur bei `UPDATING` (Race im kurzen
ENABLED-Fenster); (6) „Following"-Highlight nur wenn Location enabled; (7) `IosCompass` fährt einen billigen
groben Location-Stream mit, damit `trueHeading` (mit Deklination) gültig wird statt `magneticHeading`.

**ZURÜCKGESTELLT (Backlog, kein Blocker):** **kein pause-on-background** für die Sensoren — Updates starten bei
Permission und stoppen erst in `onCleared()` (Android stoppt in `onPause`/startet in `onResume`). Kein
permanentes Leak (`onCleared()` stoppt beide Manager), aber Akku-Drain im Hintergrund; für eine spätere
Iteration mit Lifecycle-Parität vorgemerkt. **`Location.elapsedDuration`** stempelt aktuell die Lieferzeit
(`systemUptime`) statt das Fix-Alter (`CLLocation.timestamp`) — latent, solange `RecentLocations`/`SurveyChecker`
auf iOS nicht verdrahtet sind; braucht eine in K/N auflösbare NSDate-Intervall-API (timeIntervalSince* lösen hier
nicht auf). Offline-Tiles (#6072) weiterhin zurückgestellt.

---

## ✅ M2 ABGESCHLOSSEN — App läuft auf dem iPhone 16 Pro

Die gebaute StreetComplete-App startet auf physischem iPhone 16 Pro (iOS 26.5) und
rendert die echte AboutScreen. Meilensteine M2.0–M2.4 committet auf `ios-port`.

**iOS-Build/Run-Cheatsheet:**
- **Simulator:** `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator
  -destination 'platform=iOS Simulator,id=5B7C16A4-2C11-4E0C-9549-42F7AC648438'
  -derivedDataPath build/ios build` → `xcrun simctl install/launch …`
- **Gerät:** `xcodebuild … -destination 'platform=iOS,id=00008140-000228491A33001C'
  -derivedDataPath build/ios-device -allowProvisioningUpdates build`
  → `xcrun devicectl device install app --device 83B473E9-A697-567F-BDF4-27B28D49D0B5 <…>.app`
  → `xcrun devicectl device process launch --device 83B473E9-… de.westnordost.streetcomplete.ios`
- **Signing:** TEAM_ID `2DPUG448BC` (Account „Piotr Großmann", in Xcode angemeldet); NICHT das alte
  Keychain-Team `44Q5Q437K2`. Free Personal Team → Profil **läuft nach 7 Tagen ab** (neu deployen).
- **Einmalig pro Gerät:** Entwicklerzertifikat am iPhone vertrauen (Einstellungen → Allgemein →
  VPN & Geräteverwaltung).
- **Wichtiger Iterations-Fallstrick:** Xcode relinkt die App NICHT, wenn sich nur der Inhalt des
  statischen Frameworks ändert → vor jedem Build `rm -rf build/ios` (bzw. `build/ios-device`).
  Dauerhafte Lösung noch offen (Framework als Input-Dependency des Link-Steps deklarieren).

**Auf iOS gelöste Fallstricke (für künftige Arbeit):**
- Swift-Modulnamen-Kollision: `PRODUCT_MODULE_NAME=iosApp` in Config.xcconfig, damit
  `import StreetComplete` aufs Framework (nicht aufs App-Modul) auflöst.
- `NSLog` mit `%@`-Kotlin-String-Varargs crasht Kotlin/Native (EXC_BAD_ACCESS) → `IosLogger`
  nutzt `println`.
- `BundledSQLiteDriver` (commonMain via sqlite-bundled) öffnet die DB auf iOS problemlos
  (31 Tabellen, WAL).

**Für M3+ vorgemerkt (noch nicht geladen):** Nur `iosModule` + `aboutScreenPlatformModule` sind im
iOS-Koin-Graph. Beim Anbinden weiterer Screens (Settings/User/EditHistory) die jeweiligen
commonMain-Module laden und für `UploadController`/`DownloadController`/`MapTilesDownloader`/
`ChangesetAutoCloser`/`InternetConnectionState` iOS-No-op-Stubs ergänzen (sonst Crash bei get()).
Karte = eigener großer Block (maplibre-compose, PR #6352) — M3.

---

# iOS-Port Backlog (Stand: M1 + Re-Sync, 2026-06-14)

## Integrierte Upstream-Stände (Snapshot nach Re-Sync 2026-06-14)

- **upstream/compose-quest-form (PR #6842):** Tip `1f0ea5dcf` (2026-06-12), als Basis von
  `ios-port` übernommen (inkl. der Upstream-Master-Merges des Autors — wir erzeugen selbst
  keine Merges). Gegenüber dem ersten M1-Stand (`b73335c5b`, 11.06.) zwei neue Commits:
  `f4c896cf1` (multiplatform/file-basiertes Log-Sharing via FileKit, fixes #5561) und
  `1f0ea5dcf` (einheitliches Muster für plattformspezifische Teile von Koin-Modulen,
  `*.ios.kt`-Konvention — direkt relevant für den iOS-Koin-Graph in M2).
- **upstream/sqlite-multiplatform (PR #6202):** Tip `98002051e` (2026-03-30), unverändert,
  per Cherry-pick übernommen (konfliktfrei). Bringt `androidx.sqlite` 2.6.2 (`sqlite` +
  `sqlite-bundled`, beide in **commonMain**) und `commonMain/.../data/StreetCompleteDatabase.kt`.
- **upstream/master:** auf `d7fdb2cdf` (2026-06-12) bewegt; #6842 hat es noch nicht neu
  gemergt, daher folgen wir #6842 als Basis und mergen master NICHT separat (konsistent mit
  der Basiswahl des Autors). Die master-Testfixes `1025fbb3b`/`7e4179342` sind separat
  cherry-gepickt (sie fehlen in #6842).
- **upstream/maplibre-compose (PR #6352):** Tip `2216d3ac5` (2026-03-03), unverändert,
  bewusst NICHT integriert — Referenz für M3.

### Commit-Lineage auf `ios-port` (unten → oben)
1. #6842 bis `1f0ea5dcf`
2. eigene Fixes: CopyStringsTask, OsmQuestFormContainer (siehe unten)
3. #6202: 16 Commits (`493e30bd0` … `949519f4d`), cherry-gepickt
4. master-Testfixes: `c6e06e863` ("fix tests"), `70b2301bf` (Testnamen iOS Arm64)
5. Doku: Design-Doc, Implementierungsplan, dieses Backlog, Status-Update

### Re-Sync-Verfahren (für die nächste Iteration)
upstream fetchen → frische `m1-integration` von `upstream/compose-quest-form` →
`git cherry-pick` der 2 eigenen Fixes → `git cherry-pick ff841d25a..upstream/sqlite-multiplatform`
→ `git cherry-pick 1025fbb3b 7e4179342` (master-Fixes) → Design-Doc/Plan cherry-picken →
Backlog/Status neu schreiben → iOS-Gate → `ios-port` umsetzen. Plan-Datei:
`docs/superpowers/plans/2026-06-11-ios-port-m1-upstream-integration.md`.

## Re-Scoping der M1-Gates (Abweichung vom Design-Doc)

Design-Doc nannte drei Gates (JVM-Tests, Android-APK, iOS-Compile/Link). **Befund:** PR #6842
ist laut Autor „far from done" und hat auf keinem Commit einen grünen Android- oder
iOS-Test-Build (UI-Migration mittendrin). Maßgebliches M1-Gate daher: **iOS-Main-Compile +
`linkDebugFrameworkIosSimulatorArm64`** (grün, und genau das, was die iOS-App braucht).
Android-APK/-Tests und iOS-Test-Compile werden erst grün, wenn der Bottom-Sheet-/Hauptbildschirm-
Umbau upstream abgeschlossen ist (Upstream-WIP, nicht unsere Aufgabe).

## Eigene Eingriffe (was und warum)

- **CopyStringsTask** (`buildSrc`): Regex ohne `DOT_MATCHES_ALL` quotete mehrzeilige `<string>`
  nicht → aapt2-Fehler („unescaped apostrophe") in 12 Sprachen. Lazy-Quantifier + DOT_MATCHES_ALL.
  (Android-Task-Fix; fürs iOS-Gate nicht nötig, aber echter Upstream-Bug → behalten.)
- **OsmQuestFormContainer** (commonMain): halb fertige Composable (Upstream `e4288d678`) mit
  nackten `TODO`-Markern + unaufgelöstem `composeNote()` → kompilierte auf keinem Target.
  `TODO` → `TODO()`; toter Code, keine Laufzeitwirkung. **Erforderlich fürs iOS-Compile.**

### Beim Re-Sync bewusst FALLENGELASSEN: „parked tip fixes" (alt `03a43ea2e`)
Der erste M1-Lauf enthielt einen Sammelcommit mit androidMain-Reparaturen (CopyIconsTask
dpi-Varianten, `toAndroidResourceId()`-Aufrufstellen, wiederhergestellte gelöschte Ressourcen,
`AndroidManifest`-Icon, entschlackte `SettingsActivity`). Beim Re-Sync **weggelassen**, weil:
(a) reiner androidMain-Code → fürs iOS-Framework-Gate irrelevant (iOS kompiliert androidMain
nicht); (b) Upstream bewegt sich jetzt genau dort (z. B. `res/xml/file_paths.xml` neu für das
FileKit-Log-Sharing) → die alten Restores würden kollidieren bzw. veraltete Dateien wiederbeleben;
(c) Android-Reparatur ist laut Strategie Upstreams Job. Falls je ein Android-Build nötig wird,
liefert die alte Commit-Historie (vor Re-Sync) die Vorlage.

## Bekannte offene Punkte aus PR #6842 (Upstream-WIP)

- Map-Klick = Zurück-Verhalten fehlt
- Quest-Form wird über Map-Klicks nicht benachrichtigt
- Map-Highlighting reagiert nicht auf Form-Zustand
- Kein State-Restore: ShopTypeForm, ThingsOverlayForm, PlacesOverlayForm

## Build-Brüche (alle Upstream-WIP, NICHT von uns verursacht)

- **Android-Kotlin-Compile rot:** ~204 Fehler in 5 Dateien des alten Bottom-Sheet-Systems
  (MainActivity, CreateNoteFragment, LeaveNoteInsteadFragment, SplitWayFragment, MoveNodeFragment).
  Ursache: #6842 löschte Abhängigkeiten „without replacement yet" (`b42205f4d`), Compose-Ersatz
  in Arbeit.
- **iOS-commonTest-Compile rot:** viele Testnamen mit illegalen Zeichen jenseits der von
  `70b2301bf` gefixten master-Dateien + unaufgelöste Referenzen auf von #6842 umbenannte
  Answer-Typen; zusätzlich Mockito (Upstream-Issue #5420). Main-Framework-Compile/Link davon
  unberührt (kompiliert commonTest nicht).
- **Baseline (master) war grün** vor der Integration — keine vorbestehenden Testfehler in unserer
  Ausgangsbasis.

## Bekanntes Risiko aus PR #6202

- Transaktions-Desync bei konkurrierendem DB-Zugriff (FloEdelmann, 2026-03-16). Frühwarnsystem:
  `ApplicationDbTestCase`, `SQLiteDatabaseKtTest` (laufen erst, wenn Android-Tests wieder bauen).
  westnordosts Einschätzung 2026-06-11: Ansatz dennoch „the most prudent one".

## Für M2 vorgemerkt (iOS-Bring-up)

- **DB-Treiber:** `androidx.sqlite:sqlite-bundled` ist in commonMain → `BundledSQLiteDriver` auf
  iOS verfügbar; iOS-`Database`-Bindung kann darauf aufbauen (kein cinterop nötig).
- **Koin-Plattformmodule:** Upstream-Commit `1f0ea5dcf` etabliert die `*.ios.kt`-Modulkonvention
  — beim iOS-Koin-Graph daran orientieren.
- **Laufzeit-Stubs (bei M2-Start neu prüfen, da #6842 iosMain-Dateien ändert):** `isImeVisible()`
  (iosMain) = `TODO()` → crasht Address-Quest-Form; `getAnalyzePriority`/`createOsmNoteQuest`
  (nativeMain) = `TODO()`; `linkedHashMapWithAccessOrder` ignoriert `accessOrder` → SpatialCache-LRU
  auf Native defekt.
- **Xcode-Shell:** unverdrahtetes Wizard-Template (siehe Gesamtanalyse) — Kern von M2.
