# iOS-Port M1: Upstream-WIP-Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Branch `ios-port` enthält die Upstream-PRs #6842 (compose-quest-form) und #6202 (sqlite-multiplatform), rebased/cherry-picked ohne Merge-Commits; JVM-Tests, Android-APK und iOS-Framework-Link sind grün; offene Punkte sind in einem Backlog-Dokument katalogisiert.

**Architecture:** Reine Git-Integration mit Verifikations-Gates — kein neuer Produktionscode. Mechanik (bewusste Abweichung vom Design-Text, inhaltlich identisches Ergebnis): `upstream/compose-quest-form` (123 Commits, Basis nur 2 Commits hinter master) wird als Grundlage übernommen; darauf werden die 16 Commits von `upstream/sqlite-multiplatform` und unsere 3 Commits (2 Master-Testfixes + Design-Doc) cherry-gepickt. Das minimiert Replays (19 statt 139) und erhält Original-Autorschaft. Nach jedem Schritt laufen drei Gates: JVM-Tests, `assembleDebug`, iOS-Framework-Link.

**Tech Stack:** git (cherry-pick/rebase, keine Merges), Gradle 8.14 via Temurin JDK 21 (`~/jdks/jdk-21.0.11+10`), Kotlin Multiplatform 2.3.20, Android SDK 36 (`~/Library/Android/sdk`), Xcode 26.5.

**Wichtige Fakten (gemessen am 2026-06-11):**
- `upstream/compose-quest-form` tip `b73335c5b` (2026-06-10), 123 Commits ab Basis `41f02ca10`; `git merge-tree` gegen `upstream/master` ist konfliktfrei.
- `upstream/sqlite-multiplatform` tip `98002051e` (2026-03-30), 16 Commits ab Basis `ff841d25a`; `git merge-tree` gegen `upstream/master` ist konfliktfrei.
- Einzige Datei, die BEIDE Branches ändern: `app/build.gradle.kts` → einziger erwarteter Konfliktpunkt.
- `ios-port` = master (`7e4179342`) + Design-Doc-Commit (`d83ae3b92`). master = `41f02ca10` + `1025fbb3b` ("fix tests") + `7e4179342` (Testnamen-Fix iOS Arm64).
- `./gradlew test` führt nur `testDebugUnitTest`/`testReleaseUnitTest` aus (keine iOS-Simulator-Tests) — als Gate sicher.
- Baseline vom 2026-06-11: `:app:linkDebugFrameworkIosSimulatorArm64` ist auf master-Stand grün (7m14s).

---

### Task 1: Build-Umgebung fixieren & Baseline-Gates

**Files:**
- Modify/Create: `~/.gradle/gradle.properties` (User-Home, nicht im Repo)

- [ ] **Step 1: Prüfen, ob `~/.gradle/gradle.properties` existiert und was drinsteht**

Run: `cat ~/.gradle/gradle.properties 2>/dev/null || echo "FEHLT"`

Falls die Datei existiert und bereits ein `org.gradle.java.home` enthält: Wert prüfen, ggf. anpassen. Sonst weiter mit Step 2.

- [ ] **Step 2: JDK 21 als Gradle-JVM fest hinterlegen (macht `JAVA_HOME`-Prefixe überflüssig, auch für spätere Xcode-Run-Scripts)**

```bash
mkdir -p ~/.gradle
printf '\norg.gradle.java.home=/Users/piotr/jdks/jdk-21.0.11+10/Contents/Home\n' >> ~/.gradle/gradle.properties
```

- [ ] **Step 3: Verifizieren**

Run: `cd /Users/piotr/git/StreetComplete && ./gradlew -version`
Expected: `Gradle 8.14`, Zeile `Launcher JVM: 21.0.11` (bzw. `JVM: 21.0.11`) — OHNE gesetztes JAVA_HOME in der Shell.

- [ ] **Step 4: Baseline-Gate A — JVM-Tests auf `ios-port` (Ausgangsstand)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Falls einzelne Tests fehlschlagen: exakte Testnamen notieren — das ist die lokale Baseline; dieselben Fehlschläge später NICHT der Integration zuschreiben.

- [ ] **Step 5: Baseline-Gate B — Android-APK auf `ios-port`**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (erster Lauf lädt Dependencies, kann >10 min dauern). Gate C (iOS-Link) ist von heute bereits als grün dokumentiert — nicht erneut nötig.

---

### Task 2: Integrationsbranch auf compose-quest-form (#6842) aufsetzen + Gates

**Files:** keine Quellcode-Änderungen (nur Git-Operationen)

- [ ] **Step 1: Sauberen Arbeitsstand sicherstellen**

Run: `git -C /Users/piotr/git/StreetComplete status --porcelain`
Expected: leere Ausgabe. (Falls nicht: stoppen und klären, nichts stashen ohne Rückfrage.)

- [ ] **Step 2: Integrationsbranch erstellen**

```bash
git switch -c m1-integration upstream/compose-quest-form
```

Expected: `Switched to a new branch 'm1-integration'`, HEAD = `b73335c5b`.

- [ ] **Step 3: Gate A — JVM-Tests auf rohem #6842-Stand**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Falls Kompilierfehler: Abbruch und Analyse — upstream committet täglich, ggf. ist der Branch mittendrin kaputt; dann letzten grünen Commit des Branches wählen (`git log upstream/compose-quest-form --oneline`, Branch auf älteren Commit setzen, Schritt wiederholen) und Wahl im Backlog dokumentieren. Falls einzelne Testfehler: Namen notieren (→ Backlog, Abgleich mit Baseline aus Task 1).

- [ ] **Step 4: Gate B — Android-APK**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Gate C — iOS-Framework-Link**

Run: `./gradlew :app:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`. (#6842 enthält selbst iosMain-Dateien, sollte also iOS-kompatibel sein.) Bei Fehlern: Fehlermeldung vollständig ins Backlog, dann Analyse.

---

### Task 3: sqlite-multiplatform (#6202) cherry-picken + Gates

**Files:**
- Erwarteter Konflikt: `app/build.gradle.kts` (einzige Überlappung beider Branches)
- Durch #6202 gelöscht: `app/src/androidMain/.../data/AndroidDatabase.kt`, `.../data/StreetCompleteSQLiteOpenHelper.kt`
- Durch #6202 neu (commonMain): `.../data/StreetCompleteDatabase.kt` (301 Zeilen)

- [ ] **Step 1: 16 Commits übernehmen (Autorschaft bleibt erhalten)**

```bash
git cherry-pick ff841d25ac998f8eeddef8b4f2b94e4db144c1c2..upstream/sqlite-multiplatform
```

Expected: 16 Commits werden angewendet; wahrscheinlich stoppt der Vorgang einmal mit Konflikt in `app/build.gradle.kts`.

- [ ] **Step 2: Konflikt(e) auflösen — Regel: Union beider Änderungen**

In `app/build.gradle.kts`: #6202 fügt die androidx.sqlite-KMP-Dependency hinzu und entfernt ggf. Android-SQLite-Bezüge; #6842 ändert Resource-/Prebuild-Logik. Beide Änderungen behalten (sie betreffen verschiedene Blöcke). Bei jedem weiteren unerwarteten Konflikt gilt: Ziel ist der Inhalt von #6202 angewendet auf den #6842-Stand; im Zweifel Datei auf `upstream/sqlite-multiplatform`-Fassung prüfen (`git show upstream/sqlite-multiplatform:<pfad>`) und die #6202-Intention manuell nachziehen. Danach:

```bash
git add -A && git cherry-pick --continue
```

Leere Cherry-picks (Änderung schon enthalten): `git cherry-pick --skip`.

- [ ] **Step 3: Gate A — JVM-Tests (inkl. der DB-Tests aus #6202)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Die in #6202 angepassten Tests (`ApplicationDbTestCase`, `SQLiteDatabaseKtTest`) sind das Frühwarnsystem für das bekannte Concurrency-Risiko — Fehlschläge dort vollständig ins Backlog.

- [ ] **Step 4: Gate B + C**

Run: `./gradlew :app:assembleDebug :app:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL` für beide.

---

### Task 4: Master-Testfixes + Design-Doc übernehmen

**Files:** keine neuen; übernimmt `1025fbb3b`, `7e4179342` (Testfixes, 14 commonTest-Dateien) und `d83ae3b92` (Design-Doc)

- [ ] **Step 1: Die 3 Commits von ios-port cherry-picken**

```bash
git cherry-pick 41f02ca10d5fae72e5b4e0f69f694a07e787c052..ios-port
```

Expected: 3 Commits. Konflikte in commonTest-Dateien möglich (falls #6842 dieselben Tests umbenannt/verschoben hat) — Auflösung: Intention der Testfixes (keine Sonderzeichen in Testnamen; Test-Fixes aus `1025fbb3b`) auf den neuen Stand übertragen; wird ein Fix gegenstandslos (Test existiert nicht mehr), `git cherry-pick --skip` und im Backlog notieren.

- [ ] **Step 2: Schnell-Gate**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

---

### Task 5: `ios-port` umsetzen + Backlog-Dokument

**Files:**
- Create: `docs/superpowers/ios-port-backlog.md`

- [ ] **Step 1: ios-port auf das Integrationsergebnis setzen, Arbeitsbranch entfernen**

```bash
git branch -f ios-port m1-integration
git switch ios-port
git branch -D m1-integration
git log --oneline -5
```

Expected: HEAD-Historie = Design-Doc + Testfixes oben, darunter #6202-Commits, darunter #6842-Commits. Keine Merge-Commits (`git log --merges 41f02ca10..HEAD` → leer).

- [ ] **Step 2: Backlog-Dokument anlegen** (Platzhalter `<...>` mit den tatsächlichen Beobachtungen aus Tasks 1-4 füllen; Abschnitte ohne Beobachtungen explizit mit „keine" kennzeichnen)

```markdown
# iOS-Port Backlog (Stand M1, 2026-06-11)

## Integrierte Upstream-Stände (Snapshot)
- upstream/compose-quest-form (PR #6842): b73335c5b (2026-06-10), 123 Commits ab Basis 41f02ca10
- upstream/sqlite-multiplatform (PR #6202): 98002051e (2026-03-30), 16 Commits, per Cherry-pick
- Re-Sync-Verfahren: upstream fetchen, diese Integration frisch wiederholen (Plan-Datei
  docs/superpowers/plans/2026-06-11-ios-port-m1-upstream-integration.md, Tasks 2-5)

## Bekannte offene Punkte aus PR #6842 (laut PR-Beschreibung, ungeprüft)
- Map-Klick = Zurück-Verhalten fehlt
- Quest-Form wird über Map-Klicks nicht benachrichtigt
- Map-Highlighting reagiert nicht auf Form-Zustand
- Kein State-Restore: ShopTypeForm, ThingsOverlayForm, PlacesOverlayForm

## Bekanntes Risiko aus PR #6202
- Transaktions-Desync bei konkurrierendem DB-Zugriff (FloEdelmann, 2026-03-16).
  Frühwarnsystem: ApplicationDbTestCase, SQLiteDatabaseKtTest.

## Während M1 beobachtet
- Baseline-Testfehler vor Integration: <aus Task 1 Step 4, oder „keine">
- Gate-Abweichungen nach #6842: <aus Task 2, oder „keine">
- Gate-Abweichungen nach #6202: <aus Task 3, oder „keine">
- Übersprungene/angepasste Cherry-picks: <aus Task 3/4, oder „keine">

## Laufzeit-Stubs (Analyse-Stand 2026-06-11 vor Integration; bei M2-Start neu prüfen,
## da #6842 iosMain-Dateien hinzufügt)
- isImeVisible() iosMain = TODO() → crasht Address-Quest-Form auf iOS
- getAnalyzePriority / createOsmNoteQuest nativeMain = TODO()
- linkedHashMapWithAccessOrder ignoriert accessOrder → SpatialCache-LRU auf Native defekt
```

- [ ] **Step 3: Committen**

```bash
git add docs/superpowers/ios-port-backlog.md
git commit -m "Add iOS port backlog after M1 upstream integration

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: M1-Abnahme gegen Spec

**Files:** keine

- [ ] **Step 1: Volle Test-Suite (beide Varianten, wie Upstream-CI)**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL` (bzw. nur die in Task 1 dokumentierten Baseline-Fehlschläge).

- [ ] **Step 2: iOS-Link final**

Run: `./gradlew :app:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Abnahmekriterien aus dem Design-Doc abhaken**

- `ios-port` enthält #6202 und #6842, ohne Merge-Commits → `git log --merges 41f02ca10..HEAD` leer; Stichprobe: `git log --oneline --author=Zwick -5` zeigt upstream-Autorschaft.
- Alle drei Gates grün (Steps 1-2 + Task 3 Step 4).
- Backlog-Dokument existiert und ist gefüllt (Task 5).
- Snapshot-Stände dokumentiert (im Backlog-Dokument).

- [ ] **Step 4: Statuszeile im Design-Doc ergänzen**

In `docs/superpowers/specs/2026-06-11-ios-port-m1-upstream-integration-design.md` unter **Status** ergänzen: `M1 abgeschlossen am <Datum>`. Committen:

```bash
git add docs/superpowers/specs/2026-06-11-ios-port-m1-upstream-integration-design.md
git commit -m "Mark M1 upstream integration as completed in design doc

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
