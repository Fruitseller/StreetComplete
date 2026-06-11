# iOS-Port — M1: Upstream-WIP-Integration (Design)

**Datum:** 2026-06-11
**Status:** Genehmigt von Piotr am 2026-06-11

## Kontext

Dieser Fork (Fruitseller/StreetComplete) soll die iOS-Portierung von StreetComplete
(Upstream-Tracking: streetcomplete/StreetComplete#5421) bis zu einer auf einem
physischen iPhone 16 Pro lauffähigen App weiterführen. Die Bestandsaufnahme vom
2026-06-11 ergab:

- Das KMP-Setup existiert; das Kotlin-Framework linkt für iOS (lokal verifiziert:
  `:app:linkDebugFrameworkIosSimulatorArm64` erfolgreich).
- `iosApp/` ist ein unverdrahtetes Wizard-Template ohne Kotlin-Einstiegspunkt;
  es gibt keinen iOS-Koin-Bootstrap, keine iOS-Datenbank, keine Karte, keine
  Location-/Foto-/Sync-Implementierung für iOS.
- Upstream arbeitet aktiv an: Quest-/Overlay-Forms in Compose (Draft-PR #6842,
  tägliche Commits), Multiplatform-SQLite (PR #6202), maplibre-compose
  (PR #6352, seit 2026-03 unangetastet, experimentell; Issue #6072 „Blocked").

## Beschlossene Rahmenentscheidungen

| Entscheidung | Wahl |
|---|---|
| Apple-Account | Kostenloses Personal Team (7-Tage-Provisioning, wöchentliches Re-Deploy akzeptiert) |
| Vorgehen | Inkrementelle Meilensteine, ab M2 endet jeder Meilenstein mit installierbarer App |
| Upstream-WIP | Wird integriert (Ansatz B: Integration vor iOS-Shell) |
| Koordination | Lokal im Fork, keine Upstream-Abstimmung; nur Rebase/Cherry-pick, keine Merge-Commits |

### Meilenstein-Roadmap

- **M1 (dieses Dokument):** Upstream-WIP-Integration (#6202, #6842) und Stabilisierung.
- **M2:** iOS-Shell — Xcode-Projekt fixen, `MainViewController`-Entry-Point,
  iOS-Koin-Graph, Dateipfade, Asset-/Metadata-Loading; App startet auf dem iPhone
  (Screens ohne Karte).
- **M3:** Karte via maplibre-compose (PR #6352 als Referenz; anfangs ohne
  Offline-Tiles), Map-Style ins Multiplatform-Bundle.
- **M4:** Kern-Loop auf iOS — Location (CLLocationManager), OSM-Login (WebView-
  Interception von `streetcomplete://oauth` verifizieren), Quests anzeigen →
  beantworten → hochladen.
- **M5:** Richtung Parität — Fotos, Kompass, Offline-Downloads, Sound, Polish.

## M1-Ziel

Der Branch `ios-port` enthält die Multiplatform-SQLite-Migration (#6202) und die
Quest-Forms-Compose-Migration (#6842), rebased auf den aktuellen master.
Android-Build und JVM-Tests sind grün, die iOS-Targets kompilieren und linken
weiterhin, und die offenen Lücken aus #6842 sind als Backlog katalogisiert.

## 1. Git- und Branch-Strategie

- Arbeitsbranch: `ios-port` (ab master = Upstream-master-Stand 2026-06-11).
- Remote `upstream` = streetcomplete/StreetComplete (nur fetch).
- **Integrationsreihenfolge:**
  1. `upstream/sqlite-multiplatform` (PR #6202): klein (+323/−266), in sich
     geschlossen; westnordost bewertete den Ansatz am 2026-06-11 als „the most
     prudent one". Liefert die iOS-fähige Datenbank (androidx.sqlite KMP mit
     Bundled-Driver), die M2 zwingend braucht.
  2. `upstream/compose-quest-form` (PR #6842): groß (+13.753/−100.793, 100
     Dateien); migriert alle Quest-/Overlay-Forms zu Compose und verschiebt
     strings.xml in einen Prebuild-Schritt.
- **Bewusst NICHT in M1:** `upstream/maplibre-compose` (PR #6352). Begründung:
  seit 2026-03-03 unangetastet, vom Autor als frühes Experiment deklariert,
  ersetzt die Android-Karte funktional nicht und kollidiert mit #6842. Dient in
  M3 als Referenz. (Explizit mit Piotr besprochen und genehmigt.)
- Methode: Upstream-Branches per Rebase auf master bringen (Original-Autorschaft
  bleibt erhalten), dann `ios-port` per Fast-Forward/Rebase darauf aufsetzen.
  Keine Merge-Commits (globale Vorgabe).
- Re-Sync: Vor jedem Meilenstein-Abschluss werden upstream/master und die
  WIP-Branches neu gefetcht und re-rebased, solange upstream aktiv ist.
  Snapshot-Stand wird im jeweiligen Commit/Backlog-Dokument festgehalten.

## 2. Stabilisierung und Regressionsgates

Nach jedem Integrationsschritt müssen drei Gates bestehen:

1. `./gradlew test` — JVM-Unit-Tests (entspricht Upstream-CI `unit-test.yml`).
2. `./gradlew assembleDebug` — Android-APK baut.
3. `./gradlew :app:compileKotlinIosArm64` und
   `:app:linkDebugFrameworkIosSimulatorArm64` — iOS bleibt kompilier- und linkbar
   (Baseline vom 2026-06-11 ist grün).

Bekannte offene Punkte aus #6842 (laut PR-Beschreibung: Map-Klick-=-Zurück-
Verhalten, Benachrichtigung der Form bei Map-Klicks, Map-Highlighting abhängig
vom Form-Zustand, fehlender State-Restore bei ShopTypeForm/ThingsOverlayForm/
PlacesOverlayForm) werden in `docs/superpowers/ios-port-backlog.md` katalogisiert.
Was spätere Meilensteine blockiert, wird im Fork selbst gefixt.

Bekanntes Risiko aus #6202: Transaktions-Desynchronisation bei konkurrierendem
DB-Zugriff (von FloEdelmann 2026-03-16 diagnostiziert). DB-bezogene Unit-Tests
sind das Frühwarnsystem; Auffälligkeiten werden im Backlog dokumentiert.

## 3. Teststrategie

- **Primäres Gate:** JVM-Unit-Tests (`./gradlew test`).
- **iOS-Gate:** Kompilieren + Framework-Link. Echte Tests auf iOS-Targets sind
  upstream durch die Mockito-Abhängigkeit blockiert (Issue #5420) — wird in M1
  nicht gelöst.
- **Optionaler Smoke-Test:** Headless-Android-Emulator (über sdkmanager
  installierbar) für manuelle Stichproben der migrierten Quest-Forms.

## 4. Risiken

| Risiko | Einschätzung / Mitigation |
|---|---|
| #6842 ist „far from done" → Android-Regressionen nach Integration | Eingepreistes Risiko von Ansatz B. Regressionen dokumentieren; notfalls einzelne Quests temporär deaktivieren statt die Integration zu blockieren. |
| Upstream committet täglich → Re-Sync-Konflikte | Beherrschbar: unsere Folgearbeit (iOS-Shell, M2) berührt andere Dateien als upstream. Re-Sync-Zyklus vor jedem Meilenstein. |
| #6202-Concurrency-Problem schlägt durch | Tests beobachten; schlimmstenfalls DB-Zugriffe serialisieren (Upstream-Diskussion liefert Analyse-Material). |

## 5. Abnahmekriterien M1

- [ ] `ios-port` enthält #6202 und #6842, rebased, ohne Merge-Commits.
- [ ] Alle drei Regressionsgates grün.
- [ ] Backlog-Dokument mit den offenen #6842-Punkten und ggf. neu entdeckten
      Regressionen existiert.
- [ ] Snapshot-Stand der integrierten Upstream-Branches ist dokumentiert.
