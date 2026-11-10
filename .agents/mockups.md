# Mockups (Iteration 1)

Stand: 2026-11-02. Erzeugt mit Claude Design aus `design-prompt.md`.

**Canvas:** privater Artifact-Link, liegt nur beim User (nicht im Repo)

Lokale Quelle im Repo: `design/mockups/` (identisch mit dem Canvas, Stand 2026-11-03). Referenzbilder: `design/reference/<screen>-<dark|light>.png` (412×915, 16 Dateien, erzeugt mit `tools/mockup/shoot.sh` = `tools/mockup/render.mjs` + Firefox headless; Schrift JetBrains Mono aus `app/src/main/res/font/`). Maßgeblich bleibt der Canvas.

Werkzeuge: `tools/mockup/extract-strings.mjs` (sichtbare Texte -> `design/reference/strings.json`), `tools/mockup/extract-dims.mjs` (px-Masse aus theme.css -> `app/src/test/resources/theme-dims.json`), `tools/visual-diff.sh <ist.png> <referenz.png>` (SSIM, 1.0 = identisch; Selbsttest: gleiches Bild 1.0000, A1 gegen B3 0.65, dunkel gegen hell 0.18), `scripts/check-strings.sh` (jeder Mockup-String muss in `ui/Strings.kt` oder `design/reference/string-whitelist.txt` stehen).

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

## Korrekturen gegenueber der ersten Beschreibung

- Trenner in B1 heisst `gesperrt · N` (nicht `gesperrt`), darunter `alle apps`.
- A2 hat `abbrechen`; A3 und A4 haben keinen abbrechen-Button (Zurueck/Geste = Cancel).
- B2: Decks alphabetisch nach vollem Namen, `alle decks` zuletzt.
- Das Intro-Artboard heisst im Repo `Main.dc.html`, die Referenzbilder `A1-Intro-*`.

## Textmatrix und Layout-Regeln (aus architecture.md Abschnitt 8)

Quelle der Wahrheit fuer Texte: `ui/Strings.kt`, gepflegt anhand der Mockups (maschineller Abgleich in M1-T0). Diese Textmatrix wird in M1-T0 in .agents/mockups.md uebernommen.

### Textmatrix (Variante -> exakter Text)

- Plural: `1 karte`, `N karten`; `1 tag`, `N tage`; Dauer immer `N min`.
- Fortschritt: `[` + N_c mal `█` + (required-N_c) mal `░` + `]` + ` c/required`; bis 20 Zeichen. Hero-Fortschritt in A1 28 sp bei required<=10, sonst 20 sp.
- Deck-Label (Header, Intro): Single -> letztes Segment nach `::`, klein (`lowercase(Locale.GERMAN)`); All -> `alle decks`. App-Label klein. Header einzeilig, Ellipsis am Ende.
- A1 Intro: Header `~/brainrot-stopper` | `lock.sh`; `$ ./check --app <app>`; Tag `[ LOCK ] gesperrt`; Text `beantworte <b>N karten</b> richtig, dann geht's weiter.`; Hero Fortschritt; Box `stats`: streak `N tage`, heute `N karten` (answered heute), deck `<deck-label>`; Buttons `$ ./start` (primaer), `abbrechen`. Fortsetzung (correct>0): Text `noch <b>M karten</b> richtig, dann geht's weiter.`, Hero c/N, Button `$ ./weiter`.
- A2: Header `<app> · <deck>` | Fortschritt; Box-Label `karte K · frage` (K = answered+1); Buttons `antwort zeigen`, `abbrechen`.
- A3: wie Mockup (Frage gedaempft, Trenner `antwort`, Antwort, Text `wie gut wusstest du's? nur gut und leicht zählen.`, vier Rate-Buttons `[1] nochmal`, `[2] schwer`, `[3] gut`, `[4] leicht` mit Intervall-Label falls vorhanden). Kein abbrechen-Button (Zurueck/Geste = Cancel).
- A4 nochmal: `$ ./rate --card K --again` (K = answered nach dieser Antwort), Hero `[ FAIL ]` (fail), Text `kein problem. die karte kommt später nochmal.`. A4 schwer: `$ ./rate --card K --hard`, Hero `[ WARN ]` (warn), Text `knapp. die karte kommt später wieder.` (Hinweis in decisions.md: Hard verschiebt in Anki oft um Tage, Text bleibt bewusst allgemein). Box `was zählt` wie Mockup; Text `fortschritt bleibt bei <b>c/N</b>. noch <b>M</b> richtige bis <app> offen ist.`; Button `$ ./weiter`. Kein abbrechen-Button.
- A5: `$ ./unlock <app>`, Hero `[ OK ] entsperrt`, Text `<app> ist für <b>D min</b> freigegeben.`, Box `session`: karten `A (C richtig)`, zeit `mm:ss` (= Summe `activeMs` der Session, d. h. Summe der takenMs ueber Cancel/TTL hinweg), streak `S tage` (mit heute). Buttons `$ ./open <app>`, `schließen`. Variante AllDone: Text `alles gelernt für heute. <app> ist für <b>D min</b> freigegeben.`, Box karten `0 (0 richtig)`, zeit `00:00`. Variante Notausgang: `$ ./notausgang <app>`, Hero `[ WARN ] pause`, Text `<app> ist für <b>5 min</b> freigegeben. ankidroid-problem beheben.`, keine Box.
- Waiting: Hero `[ WAIT ] nächste karte folgt`, Text `lernkarten sind noch nicht fällig. die app prüft alle 15 s.`, nur Button `abbrechen`. Loading: `$ ./laden` + Blink-Cursor.
- Error: Tabelle in Abschnitt 5; Notausgang-Button `$ ./notausgang (45s)` mit Countdown, danach `$ ./notausgang`; im 6-h-Cooldown deaktiviert `$ ./notausgang (in 4h 12m)` (ohne Mockup, M4-T6).
- Banner (ueber allen Tabs unter dem Titel, tippbar -> Setup; nur bei Bedarf, im gesunden Zustand sind B1-B3 pixelgleich zu den Mockups):
  | ProtectionStatus | Text | Farbe |
  |---|---|---|
  | DEGRADED (Pflichtschritt 1-3 offen, Dienst aktiv) | `[ WARN ] setup unvollständig` | warn |
  | OFF nicht aktiviert | `[ FEHLER ] schutz inaktiv - bedienungshilfe aus` | fail |
  | OFF aktiviert aber nicht gebunden | `[ FEHLER ] schutz inaktiv - dienst nicht gebunden` | fail |
  | OFF blockiert (eingeschraenkte Einstellungen) | `[ FEHLER ] schutz inaktiv - einstellungen blockiert` | fail |
  | settingsCorrupted | `[ FEHLER ] einstellungen beschädigt - blockliste prüfen` | fail |
- Setup-Zeilen: `$ setup`; `[ OK ]|[ OFFEN ]|[ FEHLER ]` + Schritttext aus Abschnitt 6; Buttons `$ ./zugriff erlauben`, `$ ./app-info`, `$ ./bedienungshilfe`, `$ ./akku`.
- B1 (apps): Trenner exakt `gesperrt · N` und `alle apps` (Mittelpunkt wie im Mockup). Sortierung beider Abschnitte alphabetisch nach Label (Collator de). Gesperrte Apps erscheinen nicht unter alle apps. Suche filtert beide Abschnitte nach Teilstring im Label (case-insensitive). Leerzustand: `gesperrt · 0` + dim `noch keine app gesperrt`; keine Treffer: dim `keine treffer`. Nur Apps mit Launcher-Intent im eigenen Profil (Work/Private Space nicht erfasst, dokumentiert). Gesperrte, aber nicht mehr installierte Pakete erscheinen mit Paketname als Label (entsperrbar). Nicht waehlbar: ProtectedPackages inkl. com.ichi2.anki.
- B2 (config): Deckliste alphabetisch nach vollem Namen (alle nicht-dynamischen Decks inkl. Unterdecks mit vollem `::`-Namen wie im Mockup), `alle decks` als letzte Zeile; `N fällig` je Deck = learn+review+new, bei `alle decks` Summe der Top-Level-Decks (keine Doppelzaehlung). Anki nicht erreichbar -> Fehlerzeile statt Liste.
- B3 (stats): Kacheln und Formate wie Abschnitt 4.

### Layout-Regeln

- Masse aus theme.css (BsDims enthaelt sie, per Test gegen `tools/mockup/extract-dims.mjs`-Ausgabe geprueft): scr-Padding 56/24/32, main-Padding 56/24/16, hdr min 36, btn 52, ghost 48, rate 84, row 60, kv 30, nav 56 (+16 unten), search/step/segs 48, chart 150, rk 34, ico 40 (lg 64), img-Platzhalter 96, switch 48x28.
- Insets: `enableEdgeToEdge(statusBarStyle=SystemBarStyle.auto(TRANSPARENT,TRANSPARENT), navigationBarStyle=gleich)` vor setContent. Oben `max(56 dp, statusBarInset + 24 dp)`, unten `max(Mockup-Wert, navigationBarInset + 8 dp)` (Mockup-Padding nie zusaetzlich zu Insets addieren). Suchfeld B1: `imePadding()`.
- Karten (A2/A3): Karteninhalt scrollt in seinem Rahmen (verticalScroll), Buttons und Rate-Buttons stehen fest unten. Alle anderen Screens: Inhalt in verticalScroll, Hauptbutton in einer festen unteren Zeile (auch bei fontScale 2.0 erreichbar).
- Tests bei fontScale 1.0, 1.3, 2.0: kein abgeschnittener Hauptbutton (UI-Test: Button ist `assertIsDisplayed` und hat Mindesthoehe 48 dp).

### Semantik und Barrierefreiheit

BsSwitch `toggleable(role=Role.Switch)`, BsRadioOption `selectable(role=Role.RadioButton)`, BsSegments als selectableGroup, Buttons mit Role.Button; Zeilen B1 als eine Touch-Flaeche (mergeDescendants, contentDescription `<app>, gesperrt|nicht gesperrt`). Fortschritt `contentDescription = c von N richtig`. BlinkCursor ist aus der Semantik ausgenommen. Alle Touch-Ziele >= 48 dp.


## Abweichungsliste (M5-T5, 2026-11-10)

Pruefung: `scripts/gallery-compare.sh` (SSIM, alle 16 >= 0,90), `scripts/check-strings.sh` (Exit 0) und zusaetzlich `tools/color-diff.py <referenz> <ist> <out> [schwelle]` je Ansicht (weichgezeichneter Farbvergleich; findet falsche Textfarben, fehlende Praefixe und verschobene Spalten, die SSIM kaum sieht). Ergebnis SSIM dunkel/hell: a1 0,948/0,949 · a2 0,985/0,985 · a3 0,908/0,914 · a4 0,935/0,935 · a5 0,954/0,955 · b1 0,937/0,937 · b2 0,937/0,937 · b3 0,940/0,940.

**In M5-T5 behoben** (waren unter der SSIM-Schwelle unsichtbar):
- A4 Box `was zählt`: Schluessel stand ohne `[n]` und farbig; jetzt `[1] nochmal` usw. in Textfarbe, nur `– zählt nicht` / `+ zählt` farbig (a4 0,926 -> 0,935).
- B2 `richtige karten`: Farbe `dim` -> `muted` wie im Mockup.
- B3 Ranking: Zahlen rechtsbuendig in der 24-dp-Spalte (`text-align: right`), standen ~9 dp zu weit links.
- A5 Galerie-Beispiel: zeit `02:41`, streak `13 tage` wie im Mockup (vorher 04:22 / 12).

**Bleibt, begruendet:**
- B2 `api aktiv · 2 decks gefunden` statt `3`: das Mockup zaehlt `alle decks` mit, die App zaehlt nur echte Decks (Beispiel hat 2). Bewusst.
- Zeilenmetrik: Compose und Browser runden Zeilenhoehen verschieden; Text, Schalter und Kacheln liegen stellenweise <= 1 dp versetzt (`color-diff` bei Schwelle 14 sichtbar, bei 30 nicht). Ausgleich wo noetig per `trimFirstLine()` (gotchas.md 2026-11-03), der Rest ist unter der Wahrnehmungsgrenze.
- A3 hat das niedrigste SSIM (0,908): die Schraffur des Bild-Platzhalters liegt um Bruchteile eines Pixels in anderer Phase; feine Diagonalen druecken SSIM, sichtbar ist nichts (`color-diff` Schwelle 30: none).
- A5 Hero `[ OK ] entsperrt`: Glyphenkanten der 28-sp-Schrift weichen leicht ab (`color-diff` max 33-40 an Einzelbuchstaben), Position und Breite gleich.
- Screens ohne Mockup (Setup, Banner, Fehler, Waiting, Loading, A4-schwer, A5-Varianten, Intro-Fortsetzung, B1 leer, B2 Medien) werden gegen die Textmatrix geprueft, nicht per SSIM (architecture.md 10.4, `scripts/gallery-extras.sh`).
