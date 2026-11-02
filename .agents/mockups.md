# Mockups (Iteration 1)

Stand: 2026-11-02. Erzeugt mit Claude Design aus `design-prompt.md`.

**Canvas:** <privater Artifact-Link, nicht im Repo> (privat, nur für den User)

Lokale Quelle (nur temporär, Scratchpad): `/tmp/claude-1000/-home-tinow-dev-Software-slop/1355ce29-33bf-4b07-af05-85ea2d448acb/scratchpad/bs-mockups/project/`. Maßgeblich ist der Canvas.

## Screens (je Artboard: links dark, rechts light, 412×915 dp)

Sperrbildschirm: A1 intro (`Main.dc.html`) → A2 karte vorne → A3 aufdecken + bewerten → A4 nochmal/schwer → A5 entsperrt (5/5)
Haupt-App: B1 apps · B2 einstellungen · B3 statistik

Die Artboards sind per Links verbunden (Play-Modus klickbar).

## Design-Tokens

- Schrift: JetBrains Mono 400/500/700. Größen: 12 label · 13 meta · 14 body · 16 button · 20 karte · 22 titel · 28 hero
- Spacing: 4 · 8 · 12 · 16 · 24 · 32, Seitenrand 24. Radius 4, Border 1, Touch-Ziel min. 48
- Dark (Catppuccin Mocha): bg `#1e1e2e`, bg2 `#181825`, fill `#313244`, line `#45475a`, control `#7f849c`, text `#cdd6f4`, muted `#bac2de`, dim `#9399b2`, accent `#cba6f7` (Text darauf `#11111b`), nochmal `#f38ba8`, schwer `#fab387`, gut `#a6e3a1`, leicht `#89b4fa`
- Light (Catppuccin Latte, Textfarben für Kontrast abgedunkelt): bg `#eff1f5`, bg2 `#e6e9ef`, fill `#dce0e8`, line `#bcc0cc`, control `#7c7f93`, text `#4c4f69`, muted `#5c5f77`, dim `#646880`, accent `#8839ef` (Text darauf `#eff1f5`), nochmal `#d20f39`, schwer `#b04a00`, gut `#2a7a16`, leicht `#1a56d6`

## Offen / Annahmen

- Fortschritt `x/5` im Header zählt richtige Karten (nicht Kartennummer). A2 zeigt z. B. Karte 2 bei 1/5.
- Schwer (Hard) zählt wie Nochmal nicht als richtig, siehe `decisions.md`.
- Kein Berechtigungs-Setup-Screen (Accessibility, Overlay, Akku), noch zu entwerfen.
- Kein Bild-Variante der Karte außer Platzhalter auf der Rückseite (A3).
- Stats-Zahlen sind Beispieldaten.
