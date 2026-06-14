# iOS-Port M3: Kickoff & Resume-Anker (Karte / Hauptbildschirm)

> **Zweck:** Einstiegspunkt für eine frische Session nach `/clear`. M2 ist abgeschlossen
> (App läuft auf dem iPhone 16 Pro). M3 ist groß und teilweise unsicher (Karte) und wird
> daher zuerst **untersucht**, bevor ein detaillierter Task-Plan geschrieben wird.

## Zuerst lesen (Resume-Reihenfolge)
1. Memory: `~/.claude/.../memory/ios-port-project.md` und `build-toolchain-setup.md` (Index: MEMORY.md)
2. `docs/superpowers/ios-port-backlog.md` — enthält iOS-Build/Run-Cheatsheet, gelöste Fallstricke,
   Signing (TEAM_ID **2DPUG448BC**, 7-Tage-Profil), und die „Für M3+ vorgemerkt"-Notizen
3. `docs/superpowers/specs/2026-06-11-ios-port-m1-upstream-integration-design.md` — Roadmap M1–M5
4. Dieses Dokument

## Stand zu M3-Beginn
- Branch `ios-port` (M2 fertig). iOS-Koin-Graph lädt aktuell nur `iosModule` +
  `aboutScreenPlatformModule`. Einstiegspunkt: `app/src/iosMain/.../MainViewController.kt` →
  `ComposeUIViewController { AppTheme { AboutScreen(...) } }`.
- Die Karte ist auf Android 100% MapLibre-Android (`screens/main/map/`, ~27 Dateien). Es gibt
  KEINE multiplatform/iOS-Karte im Fork. Upstream-Referenz: PR #6352 (`maplibre-compose`,
  früher WIP, seit 2026-03 unangetastet) und Issue #6072 („Blocked" — maplibre-compose fehlt
  Offline-Tile-Download). `maplibre-compose`-Branch ist im Repo verfügbar (`upstream/maplibre-compose`),
  aber bewusst NICHT integriert.
- `MainScreen.kt`/`MainActivity` sind androidMain + map-/Android-gekoppelt → auf iOS nicht nutzbar.

## Empfohlene M3-Strategie (vor Detailplan zu validieren)
Da der Hauptbildschirm = Karte ist und die Kartenmigration der größte/unsicherste Block bleibt,
ist die wahrscheinlich sinnvollste Reihenfolge:

- **M3.0 (Investigation, wie bei M2):** Workflow mit parallelen Deep-Dives:
  (a) Stand von `upstream/maplibre-compose` (#6352) — was funktioniert, wie weit ist die Compose-MP-
      Karte, was fehlt für iOS; lohnt Integration vs. eigenständig?
  (b) Multiplatform-Navigation: `navigation-compose` ist bereits commonMain-Dep — wie wird auf iOS
      ein NavHost/Hauptgerüst aufgebaut, das Menü-Screens erreichbar macht (auch ohne Karte)?
  (c) Welche commonMain-Module + iOS-Stubs (UploadController/DownloadController/MapTilesDownloader/
      ChangesetAutoCloser/InternetConnectionState) braucht es, damit Settings/User/EditHistory
      laden, ohne bei get() zu crashen?
  (d) Location/Compass-Bedarf der Karte (CLLocationManager/CoreMotion) — was ist für eine erste
      sichtbare Karte minimal nötig?
- **Mögliche Zwischen-Milestones** (in M3.0 entscheiden):
  - **M3a — Nav-Gerüst + Menü-Screens** ohne Karte (niedriges Risiko, erweitert die laufende App):
    multiplatform NavHost, Settings/User/About erreichbar; iOS-Stubs für die Controller ergänzen.
  - **M3b — Karte** via maplibre-compose: Dependency in commonMain, Map-Layer in commonMain neu
    (Referenz #6352), Map-Style nach composeResources, iOS-MapTilesDownloader (anfangs ohne Offline).
- Danach (M4): Location, OSM-Login (WebView-Interception von `streetcomplete://oauth`), Quests
  anzeigen → beantworten → hochladen.

## Wichtige Constraints (aus M2 gelernt — nicht erneut hineinlaufen)
- iOS-Build relinkt nicht bei reiner Framework-Änderung → `rm -rf build/ios` vor `xcodebuild`.
- Kein `NSLog` mit `%@`-Varargs aus Kotlin/Native (Crash) → `println`/IosLogger nutzen.
- Signing: TEAM_ID `2DPUG448BC`; Personal-Team-Profil 7 Tage gültig → ggf. neu deployen.
- Vor neuer Upstream-Arbeit: re-sync (upstream bewegt sich; `compose-quest-form` täglich).
- Regeln: keine Merge-Commits (rebase/cherry-pick); inkrementelle, einzeln verifizierte Milestones
  mit Build-/Run-Check (Simulator-Screenshot, dann Gerät).

## Erste konkrete Aktion nach /clear
„Mach weiter mit M3" → Memory + Backlog + dieses Dokument lesen → kurzen Upstream-Re-Sync-Check →
M3.0-Investigation-Workflow starten → Ergebnisse mit Nutzer abstimmen → Detail-Plan schreiben →
umsetzen.
