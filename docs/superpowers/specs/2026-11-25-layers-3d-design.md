# Layers 3D – Design

Datum: 2026-11-25 · Status: wartet auf Freigabe

## Intent und Warum
Neuer Menüpunkt „Layers": zeigt den aktuell auf dem Android-Gerät sichtbaren Screen als 3D-Explosionsansicht (Ebenen hintereinander, drehbar), ähnlich dem Layout Inspector in Android Studio. Zwei Zwecke (vom Nutzer genannt): überlappende Views finden und eine Visualisierung der View-Hierarchie haben.

## Ziele
- Neuer Rail-Eintrag „Layers" (Screen-Enum + NavGroup), nur für Android (`platforms = setOf(Platform.Android)`).
- Schnappschuss per Button „Capture": `uiautomator dump` (View-Baum) + `screencap` (bestehendes `AdbService.screenshot`).
- Ebenen = Baumtiefe; jede View ein Rechteck mit passendem Screenshot-Ausschnitt als Textur.
- Drehen per Ziehen, Zoom per Scroll, Regler für Ebenenabstand.
- Überlappungen: Views, deren Bounds sich schneiden und die nicht Eltern/Kind sind, werden rot markiert; Seitenliste, Klick springt zur View.
- Klick auf View zeigt Klasse, Resource-ID, Text, Bounds.

## Nicht-Ziele
- iOS (kein View-Baum über simctl/devicectl; später mit idb/WDA denkbar).
- Live-Aktualisierung, Perspektivprojektion, neue Abhängigkeiten.

## Anti-Ziele
- Fehlalarme verschweigen: „Überlappung" = nur geometrisch (Dump kennt kein Z-Order/Transparenz); das steht im UI als Hinweis.

## Constraints
- Konventionen aus CLAUDE.md: Logik in `AdbService` (`suspend`, `Result<T>`, `shellQuote` für Pfade), UI als ein `@Composable` pro Screen mit lokalem State. README „Project Structure" und `HelpScreen` aktualisieren.
- Reine Logik (XML-Parser, Überlappungsberechnung, Projektion) in eigenem Paket testbar, Tests unter `src/test/kotlin`.

## Ansätze
- **A) Eigene Canvas-Projektion (gewählt):** orthogonale Projektion, Ebenen parallel zum Screen → exakte Affin-Transformation je Ebene, volle Kontrolle über Sortierung/Hit-Test, performant, keine Abhängigkeit. Nachteil: keine Perspektive.
- B) `graphicsLayer` pro View: Perspektive gratis, aber fummelige Tiefensortierung/Hit-Tests, schwerfällig bei vielen Nodes. Verworfen.

## Design (Eckpunkte, Entscheidungen des Assistenten markiert)
- `layers/ViewNode.kt`: Datenmodell (Klasse, id, text, bounds, depth, children). *(Annahme)*
- `layers/UiDumpParser.kt`: parst uiautomator-XML (`node`-Elemente, `bounds="[l,t][r,b]"`).
- `layers/Overlaps.kt`: reine Funktion `findOverlaps(nodes)`.
- `layers/Projection.kt`: reine Rotations-/Projektionsmathematik.
- `AdbService.uiDump(serial)`: `uiautomator dump` auf Gerätepfad, dann per `exec-out cat` lesen *(Annahme: vermeidet Datei-Pull)*.
- `ui/LayersScreen.kt`: Capture-Button, Canvas, Seitenliste, Detailpanel.
- Nur sichtbare/relevante Nodes mit Fläche > 0 werden gezeichnet *(Annahme, im Plan zu schärfen)*.

## Dem Builder überlassen
Farbgebung der Ebenen, Standard-Rotationswinkel, genaue Layout-Aufteilung, Umgang mit sehr tiefen Bäumen (Tiefe kappen/Abstand skalieren).
