# Device State Snapshots & Diff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zustand eines Android- oder iOS-Geräts als Snapshot aufnehmen, speichern und gegen einen anderen Snapshot oder den aktuellen Zustand ("Jetzt") vergleichen.

**Architecture:** Reine Logik (Modell, Diff, Parser, Store) in einem neuen Package `snapshot/`, getestet mit JUnit. Zwei Collectors (Android über `AdbService`, iOS über `IosService`) liefern dieselbe flache `Map<String, Map<String, String>>`. Ein Screen `SnapshotScreen` hängt als Rail-Eintrag in `ui/App.kt`.

**Tech Stack:** Kotlin 2.0/JVM 17, Compose Desktop 1.6.11, `kotlinx-serialization-json` 1.7.1, neu `kotlin("test")` mit JUnit Platform.

**Spec:** `docs/superpowers/specs/2026-11-05-device-snapshots-design.md`

## Global Constraints

- Snapshot-Pfad: `~/.devicedeck/snapshots/<deviceId>/<id>.json`
- Snapshot-`id` ist zeitstempelbasiert, Format `yyyyMMdd-HHmmss`
- Android-Sektionen: `apps`, `settings`, `props`, `permissions`; iOS-Sektionen: `apps`, `device`
- Settings-Schlüssel: `system/<key>`, `secure/<key>`, `global/<key>`; Permission-Schlüssel: `<paket>/<permission>` mit Wert `granted` oder `denied`
- Eine fehlgeschlagene Sektion fehlt in `sections`; sie gilt im Diff als "nicht vergleichbar", nie als "alles entfernt"
- Kein Vergleich über Plattformgrenzen
- Kein Dateivergleich, kein Wiederherstellen
- Pfade in `adb shell` laufen durch `shellQuote`; One-shot-Calls sind `suspend fun … = withContext(Dispatchers.IO)`
- `CLAUDE.md`, `README` ("Project Structure") und `HelpScreen` werden mitgepflegt
- Versionsnummer wird nicht angefasst

## Review Focus

- Adb-Serial wie `192.168.1.5:5555` enthält `:`, ein ungültiges Zeichen in Dateinamen unter Windows → Store muss die Geräte-ID im Verzeichnisnamen bereinigen
- Zwei Snapshots in derselben Sekunde dürfen sich nicht überschreiben
- `getprop`-Werte mit Zeilenumbruch und `settings`-Werte mit `=` oder leerem Wert dürfen Parser nicht verschieben oder Zeilen verlieren
- Snapshot-Datei mit unbekanntem oder fehlendem Feld (alte/neue Version) darf das Laden der übrigen Snapshots nicht blockieren
- "Jetzt"-Vergleich bei getrenntem oder offline-Gerät muss eine Fehlermeldung zeigen, nicht abstürzen oder einen leeren Zustand als "alles entfernt" auswerten
- Diff zweier Snapshots mit mehreren tausend Einträgen (Android hat typisch 300–600 Pakete, 1000+ Settings/Props) bleibt flüssig

---

### Task 1: Testgerüst, Modell und Diff

**Files:**
- Modify: `build.gradle.kts` (dependencies und `tasks.test`)
- Create: `src/main/kotlin/snapshot/Snapshot.kt`
- Create: `src/main/kotlin/snapshot/SnapshotDiff.kt`
- Test: `src/test/kotlin/snapshot/SnapshotDiffTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class Snapshot(val id: String, val deviceId: String, val deviceName: String, val platform: Platform, val takenAt: Long, val label: String, val sections: Map<String, Map<String, String>>)` (`Platform` aus `device.Platform`, dafür ist `Platform` serialisierbar zu machen: `@Serializable` am Enum in `device/Device.kt`)
  - `data class SectionDiff(val added: Map<String, String>, val removed: Map<String, String>, val changed: Map<String, Pair<String, String>>)` mit `val isEmpty: Boolean`
  - `sealed interface SectionResult { data class Compared(val diff: SectionDiff) : SectionResult; data object NotComparable : SectionResult }`
  - `fun diff(before: Snapshot, after: Snapshot): Map<String, SectionResult>` (Schlüssel = Sektionsname, Vereinigung der Sektionen beider Snapshots)

- [ ] **Step 1: Testabhängigkeit ergänzen.** In `build.gradle.kts` `testImplementation(kotlin("test"))` und einen Block `tasks.test { useJUnitPlatform() }` hinzufügen. Außerdem `kotlin("plugin.serialization") version "2.0.0"` zu den Plugins, falls es fehlt (sonst lässt sich `@Serializable` nicht verwenden; der bestehende JSON-Code nutzt nur `JsonElement`).

- [ ] **Step 2: Failing tests schreiben** in `SnapshotDiffTest`, mit einem Helfer `snap(vararg sections: Pair<String, Map<String, String>>)`:
  - `added_removed_changed_are_detected`: before `apps = {a:1, b:1}`, after `apps = {b:2, c:1}` → added `{c:1}`, removed `{a:1}`, changed `{b: 1→2}`
  - `identical_snapshots_yield_empty_diffs`: alle `SectionDiff.isEmpty`
  - `section_missing_in_one_snapshot_is_not_comparable`: before hat `permissions`, after nicht → `SectionResult.NotComparable`, nicht `removed`
  - `section_missing_in_both_is_absent_from_result`
  - `empty_section_vs_filled_section_is_compared`: leere Map ist vorhanden und daher vergleichbar → alles `added`

- [ ] **Step 3: Tests laufen lassen, Fehlschlag prüfen.** `./gradlew test --tests 'snapshot.SnapshotDiffTest'` → FAIL (Klassen fehlen).

- [ ] **Step 4: Implementieren.** `Snapshot.kt` und `SnapshotDiff.kt` wie in Interfaces; `diff` läuft über `before.sections.keys + after.sections.keys` und baut pro Sektion einmal Key-Mengen (`O(n)`, keine verschachtelten Suchen).

- [ ] **Step 5: Tests grün.** Gleicher Befehl → PASS. Zusätzlich `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.** `git add build.gradle.kts src/main/kotlin/device/Device.kt src/main/kotlin/snapshot src/test && git commit -m "Add snapshot model, diff and test setup"`

### Task 2: SnapshotStore

**Files:**
- Create: `src/main/kotlin/snapshot/SnapshotStore.kt`
- Test: `src/test/kotlin/snapshot/SnapshotStoreTest.kt`

**Interfaces:**
- Consumes: `Snapshot` (Task 1)
- Produces: `class SnapshotStore(private val root: File = File(System.getProperty("user.home"), ".devicedeck/snapshots"))` mit
  - `fun save(snapshot: Snapshot): Snapshot` (gibt den gespeicherten Snapshot zurück; hängt bei Kollision `-2`, `-3` … an die `id` an)
  - `fun list(deviceId: String): List<Snapshot>` (neueste zuerst)
  - `fun delete(snapshot: Snapshot)`
  - `fun newId(now: Long = System.currentTimeMillis()): String` (Format `yyyyMMdd-HHmmss`)
  - interne `fun dirNameFor(deviceId: String): String` ersetzt alles außer `[A-Za-z0-9._-]` durch `_`

- [ ] **Step 1: Failing tests** (jeweils mit `@TempDir`-Verzeichnis als `root`):
  - `save_then_list_roundtrips`
  - `list_is_newest_first`
  - `same_second_does_not_overwrite`: zweimal `save` mit identischer `id` → zwei Dateien, zweite `id` endet auf `-2`
  - `device_id_with_colon_is_sanitized`: `192.168.1.5:5555` → Verzeichnisname enthält kein `:`, `list("192.168.1.5:5555")` findet den Snapshot
  - `corrupt_file_is_skipped`: kaputte JSON-Datei neben einer gültigen → `list` liefert nur die gültige
  - `unknown_json_fields_are_ignored`: Datei mit zusätzlichem Feld wird geladen (`Json { ignoreUnknownKeys = true }`)
  - `delete_removes_file`
  - `list_for_unknown_device_is_empty`

- [ ] **Step 2: Fehlschlag prüfen.** `./gradlew test --tests 'snapshot.SnapshotStoreTest'` → FAIL.

- [ ] **Step 3: Implementieren.** Die Datei wird als `<id>.json` geschrieben; die Kollisionsprüfung nutzt `File.exists`. `list` fängt pro Datei Exceptions ab und überspringt sie.

- [ ] **Step 4: Tests grün.** Gleicher Befehl → PASS.

- [ ] **Step 5: Commit.** `git add src && git commit -m "Add snapshot store"`

### Task 3: Android-Parser und Collector

**Files:**
- Create: `src/main/kotlin/snapshot/AdbParsers.kt` (reine Funktionen)
- Create: `src/main/kotlin/snapshot/AdbSnapshot.kt`
- Test: `src/test/kotlin/snapshot/AdbParsersTest.kt`

**Interfaces:**
- Consumes: `Snapshot` (Task 1); `AdbService.shell(serial, command): String`; `AdbService.getProps(serial): Map<String, String>`
- Produces:
  - `fun parsePackageList(output: String): Map<String, String>` (Eingabe `package:com.x versionCode:12`, Ausgabe Paket → versionCode)
  - `fun parseSettings(namespace: String, output: String): Map<String, String>` (Eingabe `key=value`-Zeilen; Schlüssel mit Präfix `<namespace>/`; Teilung am **ersten** `=`)
  - `fun parsePermissions(dump: String): Map<String, String>` (aus `dumpsys package`-Gesamtdump, `Package [x]`-Blöcke mit `runtime permissions:` / `granted=true|false`)
  - `fun filterVolatileProps(props: Map<String, String>): Map<String, String>` (entfernt `sys.boot_completed`, `ro.runtime.firstboot`, `sys.uptime*`, `debug.*`, `persist.sys.boot.reason*`, `init.svc.*`, `sys.sysctl.*`, `ro.boottime.*`)
  - `suspend fun AdbService.snapshot(device: AdbDevice, label: String): Snapshot` (Extension, `id`/`takenAt` aus aktueller Zeit; die `id` wird beim Speichern vom Store vergeben, hier nur provisorisch gesetzt)

- [ ] **Step 1: Failing tests** mit festen Beispielausgaben als Raw-Strings:
  - `package_list_parses_name_and_version_code`, `package_list_ignores_blank_and_malformed_lines`
  - `settings_splits_on_first_equals`: `a=b=c` → Wert `b=c`; leerer Wert (`key=`) bleibt erhalten; Zeile ohne `=` wird ignoriert
  - `settings_prefixes_namespace`
  - `permissions_parses_granted_and_denied` mit einem Dump aus zwei Paketen, einem davon ohne `runtime permissions`-Block
  - `props_multiline_value_does_not_shift_following_props`: `getprop`-Wert, der über zwei Zeilen geht, darf das nächste Property nicht verlieren (dieser Fall betrifft `AdbService.getProps`; ist die vorhandene Regex dort zu eng, im Collector einen eigenen Parser verwenden und hier testen)
  - `volatile_props_are_filtered`

- [ ] **Step 2: Fehlschlag prüfen.** `./gradlew test --tests 'snapshot.AdbParsersTest'` → FAIL.

- [ ] **Step 3: Parser implementieren** in `AdbParsers.kt`.

- [ ] **Step 4: Collector implementieren** in `AdbSnapshot.kt`. Kommandos: `pm list packages --show-versioncode`, `settings list system|secure|global`, `getprop`, `dumpsys package`. Die vier Gruppen laufen parallel per `coroutineScope { async { … } }`; jede Gruppe in `runCatching`, bei Fehler oder leerer Ausgabe von `pm list packages` wird die Sektion weggelassen. Eine Sektion ist nur dann vorhanden, wenn ihr Kommando erfolgreich war (leere Map ist erlaubt, z. B. keine Permissions).

- [ ] **Step 5: Tests grün und Build.** `./gradlew test` → PASS; `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit.** `git add src && git commit -m "Add Android snapshot collector and parsers"`

### Task 4: iOS-Collector

**Files:**
- Create: `src/main/kotlin/snapshot/IosSnapshot.kt`
- Create: `src/main/kotlin/snapshot/IosMapping.kt` (reine Mapping-Funktionen)
- Test: `src/test/kotlin/snapshot/IosMappingTest.kt`

**Interfaces:**
- Consumes: `Snapshot` (Task 1); `IosService.apps(device): Result<List<IosApp>>`; `IosService.deviceDetails(device): Result<IosDeviceDetails>`; `IosService.appearance(device): Result<String?>`
- Produces:
  - `fun appsSection(apps: List<IosApp>): Map<String, String>` (Bundle-ID → `displayVersion`)
  - `fun deviceSection(details: IosDeviceDetails, appearance: String?): Map<String, String>` (Zeilen als Label → Wert; Appearance als Schlüssel `appearance`, nur wenn nicht `null`)
  - `suspend fun IosService.snapshot(device: IosDevice, label: String): Snapshot`

- [ ] **Step 1: Failing tests** für die Mapping-Funktionen:
  - `apps_section_maps_bundle_id_to_display_version` (inkl. App ohne Version → Build, wie `IosApp.displayVersion`)
  - `device_section_maps_rows`, `device_section_adds_appearance_only_when_present`
  - `volatile_device_rows_are_dropped`: Zeilen wie Akkustand oder freier Speicher aus `deviceDetails` ändern sich ständig; Zeilen, deren Label `battery`, `storage` oder `free` (ohne Beachtung der Groß-/Kleinschreibung) enthält, werden aus der Sektion entfernt, damit der Diff nicht durch Rauschen unlesbar wird

- [ ] **Step 2: Fehlschlag prüfen.** `./gradlew test --tests 'snapshot.IosMappingTest'` → FAIL.

- [ ] **Step 3: Mapping und Collector implementieren.** `IosService.snapshot` ruft `apps`, `deviceDetails` und `appearance` parallel auf; schlägt `apps` fehl, fehlt die Sektion `apps`, schlägt `deviceDetails` fehl, fehlt `device`. Nur die Simulator-Appearance ist lesbar, bei Fehler bleibt der Schlüssel weg.

- [ ] **Step 4: Tests grün und Build.** `./gradlew test` → PASS; `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.** `git add src && git commit -m "Add iOS snapshot collector"`

### Task 5: Screen, Navigation und Doku

**Files:**
- Create: `src/main/kotlin/ui/SnapshotScreen.kt`
- Modify: `src/main/kotlin/ui/App.kt` (Enum `Screen` bei Z. 47–62, `DeviceGroups` bei Z. 71–78, `AndroidScreen`/`IosScreen` bei Z. 177–203)
- Modify: `src/main/kotlin/ui/HelpScreen.kt`, `README.md` ("Project Structure"), `CLAUDE.md` (Abschnitt Architecture/`settings/AppSettings.kt`)

**Interfaces:**
- Consumes: `SnapshotStore` (Task 2), `diff`/`SectionResult` (Task 1), `AdbService.snapshot` (Task 3), `IosService.snapshot` (Task 4)
- Produces: `@Composable fun SnapshotScreen(device: Device)`

- [ ] **Step 1: Navigation.** `Screen.Snapshots("Snapshots")` (beide Plattformen) ergänzen, `NavGroup("Snapshots", Icons.Filled.CameraAlt, listOf(Screen.Snapshots))` vor `Device` einfügen, in `AndroidScreen` und `IosScreen` je `Screen.Snapshots -> SnapshotScreen(device)` ergänzen (beide `when` sind erschöpfend, der Compiler zeigt fehlende Zweige).

- [ ] **Step 2: Screen implementieren**, State lokal mit `remember`/`rememberCoroutineScope` wie die anderen Screens:
  - Links: Liste der Snapshots von `SnapshotStore().list(device.id)`, Textfeld für das Label, Button "Snapshot aufnehmen" (ruft je nach Gerätetyp den Collector auf, speichert über `store.save`, lädt die Liste neu, zeigt während der Aufnahme einen Fortschrittsindikator), Löschen pro Eintrag
  - Auswahl: erster Klick wählt "Vorher"; ein zweiter Klick wählt "Nachher"; zusätzlich ein Eintrag "Jetzt" als Vergleichsziel, der den Zustand live erfasst und nicht speichert
  - Rechts: Diff gruppiert nach Sektion mit Zähler pro Sektion, Farben grün/rot/gelb aus `Theme.kt`, Suchfeld (filtert Schlüssel und Werte), Hinweis "nicht vergleichbar" für `SectionResult.NotComparable`, Button "Als Text kopieren" (Klartext-Diff in die Zwischenablage)
  - Fehler beim "Jetzt"-Erfassen oder wenn alle Sektionen fehlen (Gerät offline) → sichtbare Fehlermeldung, kein Diff
  - Lange Diff-Listen über `LazyColumn`
  - Nur Snapshots mit gleicher `platform` wie das Gerät werden in der Liste angeboten

- [ ] **Step 3: Doku.** `HelpScreen` um einen Snapshots-Abschnitt erweitern (Aufnahme, Vergleich, "Jetzt", Speicherort `~/.devicedeck/snapshots`); `README` "Project Structure" um das Package `snapshot/` und `ui/SnapshotScreen.kt` ergänzen; `CLAUDE.md` (liegt seit dem Commit "untrack CLAUDE.md" nicht mehr im Repo, wird nur lokal angepasst und nicht committet) um den Hinweis erweitern, dass Einstellungen in Prefs und Snapshots als JSON-Dateien liegen (Prefs-Limit 8 KB pro Wert), und um das Package `snapshot/`.

- [ ] **Step 4: Build.** `./gradlew build` → BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 5: Manuell verifizieren** mit `./gradlew run` an einem Android-Gerät oder Emulator und einem iOS-Simulator: Snapshot aufnehmen, eine App installieren oder ein Setting ändern, gegen "Jetzt" vergleichen und prüfen, dass die Änderung genau einmal in der richtigen Sektion erscheint; Gerät trennen und "Jetzt" wählen → Fehlermeldung statt Absturz; App neu starten → Snapshots sind noch da.

- [ ] **Step 6: Commit.** `git add src README.md && git commit -m "Add snapshots screen"`
