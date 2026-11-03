# Index

Einstiegspunkt für Agents. Bei neuen Dateien hier eintragen.

## Dateien in `.agents/`

- `README.md` – Regeln, wie dieser Ordner gepflegt wird
- `decisions.md` – Entscheidungen mit Begründung
- `architecture.md` – verbindlicher Bauplan (Revision 2) inkl. Versionstabelle und applicationId
- `gotchas.md` – Stolperfallen und Eigenheiten
- `todo.md` – Meilensteine M0-M7 mit Aufgaben, offene Entscheidungen, Standardwerte, Risiken
- `log.md` – Session-Protokoll (neueste oben)
- `design-prompt.md` – Prompt für Claude Design (Mockups Iteration 1)
- `mockups.md` – Link zu den Mockups und Design-Tokens (Iteration 1)
- `workflow-prompt.md` – Ultracode-Workflow-Prompt, der die App entwickelt (Opus denkt, Sonnet implementiert)
- `research.md` – verifizierte Faktenbasis je Thema (Behauptung, Status, Beleg, Konsequenz), Offenes, Empfehlungen (Stand 2026-11-02)
- `server-setup-prompt.md` – Prompt fuer den eigenen Server: Anki-Sync-Server + Nur-Lese-Medienserver (Docker, Traefik), Option S fuer Kartenbilder (Stand 2026-11-03)
- `media-server.md` – Schnittstelle des laufenden Medienservers (Endpunkt, Kodierung, Statuscodes, App-Regeln, Planfolgen; ohne Token)
- `plan.json` – kompletter Plan als JSON (Architektur, Versionen, Spikes M0, Meilensteine M1-M7 mit Aufgaben, Defaults, offene Entscheidungen, Risiken, Gotchas); `todo.md` und `architecture.md` sind daraus abgeleitet

## Außerhalb von `.agents/`

- `design/mockups/` – Quelldateien der Mockups (`*.dc.html`, `theme.css`, `canvas.json`), Kopie des Canvas

## Code-Struktur

Stand nach M1 (Theme, Komponenten und alle Screens statisch; Logik folgt ab M2). Gradle-Projekt (Wrapper 9.8.2, nur ueber `scripts/gradlew-jdk17.sh` aufrufen):
- `app/` - `dev.tinow.brainrotstopper`: `MainActivity` (Platzhalter), `service/BlockerAccessibilityService` und `lock/LockActivity` (Spike-Stand, werden in M3/M4 ausgebaut), `src/debug/.../SpikeReceiver` (Spike-Befehle per `am broadcast`, nur Debug), `res/xml/` (Accessibility-Config, network_security_config, Debug-Variante mit Cleartext nur fuer 10.0.2.2), `src/test/resources/anki/` (Provider-Fixtures)
- `app/.../ui/` (M1): `theme/` (`BsColors`, `BsDims`, `BsType` mit `ExactLineHeight`, `BsTheme`), `Strings.kt` (alle Texte), `components/` (`Basics`, `Controls`, `Blocks`, `Showcase`: Terminal-Komponenten), Screens zustandslos: `main/` (`MainScaffold`, Tabs), `apps/`, `config/`, `stats/`, `setup/`, `banner/`; Sperrscreens unter `lock/screens/` (`LockScaffold`, `LockScreens`)
- `app/src/debug/.../debug/` - `ComponentGalleryActivity` (`--es screen <name>`) und `ScreenSamples` (Beispieldaten aller Screens, `names`); `app/src/androidTest/` - `FontScaleTest`
- `testapp/` - Flavors `one` (dummy, magenta) und `two` (dummy2, cyan) als Ziel-Apps der Blocking-Tests
- `scripts/` - `gradlew-jdk17.sh`, `emu-env.sh`, `emu-start.sh`, `emu-stop.sh`, `anki-install.sh` (AnkiDroid v2.25.3 mit Signaturpruefung), `install-debug.sh`, `a11y-enable.sh`, `shot.sh`, `check.sh` (alles Gruen-Pruefende), `gallery-compare.sh` (SSIM der Gallery gegen design/reference), `gallery-extras.sh` (Screenshots/Kontaktbogen der Extra-Screens), `focus.sh`, `revlog.sh`, `spike/` (`measure-lock.sh`, `protocol.sh`, `seed-basic.sh`, `seed-fixtures.py`, `fake-media-server.py`)
