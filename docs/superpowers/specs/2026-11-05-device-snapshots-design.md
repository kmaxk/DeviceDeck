# Device State Snapshots & Diff — Design

Datum: 2026-11-05 · Status: Entwurf, vom Nutzer im Chat abgenommen, Spec-Review offen

## Ziel

Vorher/nachher-Test auf einem Gerät: Zustand aufnehmen, Aktion ausführen (App installieren, Setting ändern, Update, Reboot), Veränderungen sichtbar machen. Android und iOS (Simulator und iPhone).

Nicht-Ziele: Gerätevergleich über Plattformgrenzen, Wiederherstellen eines Zustands (Device Profiles, #15), Dateivergleich.

## Datenmodell (`snapshot/Snapshot.kt`)

```kotlin
@Serializable data class Snapshot(
    val id: String,          // Zeitstempel, z. B. 20261105-143012
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
    val takenAt: Long,
    val label: String,
    val sections: Map<String, Map<String, String>>
)
```

- Android-Sektionen: `apps` (Paket → Version/Versionscode), `settings` (`system/<key>`, `secure/<key>`, `global/<key>` → Wert), `props` (Key → Wert), `permissions` (`<paket>/<permission>` → `granted`/`denied`).
- iOS-Sektionen: `apps` (Bundle-ID → Version), `device` (Detail-Key → Wert, inkl. Appearance soweit lesbar).
- Alles flache String-Maps, damit der Diff keine Sonderfälle pro Sektion braucht.
- Fehlt eine Sektion (Collector fehlgeschlagen), fehlt der Key in `sections`.

## Collectors

`snapshot/AdbSnapshot.kt` und `snapshot/IosSnapshot.kt`, Konventionen wie `AdbService`/`IosService` (`Dispatchers.IO`, `Result`).

- Android: `pm list packages --show-versioncode`, `settings list system|secure|global`, `getprop`, Permissions aus einem einzigen `dumpsys package`-Dump (Parser, kein Aufruf pro App).
- iOS: `IosService.apps()` und `deviceDetails()`, Appearance falls verfügbar.
- Sektionen laufen parallel. Eine fehlgeschlagene Sektion wird weggelassen, der Snapshot bricht nicht ab.
- Volatile Props (Uptime, `sys.boot_completed` u. ä.) stehen auf einer Ausschlussliste.

## Diff (`snapshot/SnapshotDiff.kt`)

Reine Funktion `diff(a, b)`. Ergebnis pro Sektion: `added`, `removed`, `changed(alt → neu)`. Fehlt eine Sektion in einem der beiden Snapshots, gilt sie als "nicht vergleichbar", nicht als "alles entfernt". Vergleich über Plattformgrenzen wird nicht angeboten.

## Store (`snapshot/SnapshotStore.kt`)

- Pfad: `~/.devicedeck/snapshots/<deviceId>/<id>.json`, serialisiert mit `kotlinx-serialization-json`.
- `save`, `list(deviceId)`, `load`, `delete`.
- Beschädigte Dateien werden beim Listen übersprungen.
- `CLAUDE.md` wird ergänzt: Einstellungen liegen in Prefs, Snapshots als Dateien (Prefs-Limit 8 KB pro Wert).

## UI (`ui/SnapshotScreen.kt`)

- Neuer `Screen`-Eintrag und `NavGroup`-Eintrag "Snapshots", für beide Plattformen, nur mit gewähltem Gerät.
- Links: Liste der Snapshots des Geräts, Button "Snapshot aufnehmen" mit optionalem Label.
- Vergleich: zwei Snapshots, oder ein Snapshot gegen "Jetzt" (aktueller Zustand wird live erfasst, nicht gespeichert).
- Rechts: Diff gruppiert nach Sektion, farbig (grün hinzugefügt, rot entfernt, gelb geändert), Suchfeld, Zähler pro Sektion, Löschen, "Als Text kopieren".
- `HelpScreen` und README-Abschnitt "Project Structure" werden aktualisiert.

## Tests

Neu: JUnit-Testgerüst (Gradle-Testabhängigkeiten) nur für reine Logik:
- `SnapshotDiff`: hinzugefügt/entfernt/geändert, fehlende Sektion, identische Snapshots.
- Serialisierung und `SnapshotStore` (temporäres Verzeichnis, beschädigte Datei).
- `dumpsys package`-Parser und Parser für `pm list packages`/`settings list` mit festen Beispielausgaben.

`./gradlew build` führt die Tests damit auch in der CI-Validate-Stufe aus.

## Offene Punkte

Keine. Die Umsetzung erfolgt in dieser Reihenfolge: Modell und Diff mit Tests, Store, Android-Collector, iOS-Collector, UI, Doku.
