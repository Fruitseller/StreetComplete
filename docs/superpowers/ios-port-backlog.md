# iOS-Port Backlog (Stand: M1 + Re-Sync + M2 + M3a, 2026-06-14)

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

**Nächster Schritt:** maplibre-compose-Spike (Version gegen Compose 1.10.3 validieren, triviale Karte
auf iOS rendern) → dann M3b-Detailplan (Karte + Location-Dot, vom Nutzer so gewählt). Siehe
`docs/superpowers/plans/2026-06-14-ios-port-m3a-nav-and-menu-screens.md` Abschnitt „Bridge to M3b".

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
