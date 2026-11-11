# Installation

Stand 2026-11-11 (M7). Zwei Wege: Entwicklung am Emulator und Installation auf dem Pixel 9.

## A) Pixel 9 (Release, per USB)

Voraussetzungen am Handy:
1. **AnkiDroid** aus dem Play Store installiert, einmal geoeffnet, Sammlung vorhanden. Schalter „AnkiDroid-API aktivieren“ ist ab Werk an (falls aus: AnkiDroid-Einstellungen, Erweitert).
2. Fuer Bilder: AnkiDroid und Desktop-Anki syncen mit dem **eigenen Sync-Server** (`media-server.md`), Auto-Sync in AnkiDroid an. Ohne das zeigt die App Platzhalter `[ bild: x ]`, die Karten bleiben bewertbar.
3. Entwickleroptionen an (Einstellungen > Ueber das Telefon > 7x auf Build-Nummer), darin **USB-Debugging** an. Handy per USB anschliessen, Dialog „USB-Debugging zulassen“ bestaetigen.

Am Rechner:
```bash
scripts/release-build.sh                      # baut, prueft Manifest, signiert -> build/release/brainrot-stopper.apk
~/Android/Sdk/platform-tools/adb devices       # Handy muss als "device" erscheinen; laeuft auch der Emulator: -s <seriennummer>
~/Android/Sdk/platform-tools/adb install build/release/brainrot-stopper.apk
```
- Der Signaturschluessel liegt in `~/.config/brainrot-stopper/` (`release.jks`, `release.pass`), wird beim ersten Lauf erzeugt und **muss gesichert werden**. Updates: `adb install -r ...` mit demselben Schluessel behaelt alle Daten. Neuer Schluessel oder Wechsel Debug <-> Release geht nur mit Deinstallation (Einstellungen, Statistik, Berechtigungen weg).
- Ist eine Debug-Version auf dem Handy, vorher `adb uninstall dev.tinow.brainrotstopper`.
- Die Projektskripte (`scripts/`, ausser `release-build.sh`) sprechen **nur mit dem Emulator** (`emu-env.sh` setzt `ANDROID_SERIAL`); fuers Handy immer `adb` direkt aufrufen. Meldet adb `device 'emulator-5554' not found`, ist `ANDROID_SERIAL` in der Shell gesetzt: `unset ANDROID_SERIAL`.

Erster Start (die App oeffnet den Setup-Screen):
1. **AnkiDroid-Zugriff erlauben** (Systemdialog). Abgelehnt mit „nicht mehr fragen“: App-Info > Berechtigungen.
2. **Bedienungshilfe** einschalten: Einstellungen > Bedienungshilfen > Brainrot Stopper. Laesst sie sich nicht einschalten (eingeschraenkte Einstellungen): App-Info > ⋮ > „Eingeschraenkte Einstellungen zulassen“, dann erneut. Per `adb install` installierte APKs waren im Emulator nicht eingeschraenkt (M0-S3); am Geraet (unverifiziert). Mit Advanced Protection kann Android die Bedienungshilfe sperren.
3. **Akku-Optimierung aus** (empfohlen): App-Info > Akkunutzung der App > „Nicht eingeschraenkt“ (Menuepfad unverifiziert). Sonst kann Android den Dienst beenden.
4. Tab **apps**: zu sperrende Apps einschalten (Instagram, TikTok, YouTube ...).
5. Tab **einstellungen**: Deck (Standard „alle decks“), Karten pro Entsperrung (Standard 5), Freigabe-Dauer (Standard 15 min), Box `bilder` (Server-URL `https://...` und Token, `$ ./test`), Box `sync` (AnkiDroid-Sync nach Freigabe, Standard an).

Android erinnert nach dem Einschalten per Benachrichtigung an die aktive Bedienungshilfe; das ist normal. Erscheint in der App das Banner „schutz inaktiv“, ist der Dienst aus (Bedienungshilfe wieder einschalten).

Danach die Geraete-Checkliste in `test-checklist.md` (Abschnitt „Am Geraet“) einmal durchgehen.

## B) Entwicklung am Emulator

- JDK 17 (`/usr/lib/jvm/java-17-openjdk`); Gradle nur ueber `scripts/gradlew-jdk17.sh` (Standard-java ist JDK 25). In Android Studio: Settings > Build Tools > Gradle > Gradle JDK = dieses JDK 17.
- SDK in `~/Android/Sdk` (`scripts/emu-env.sh` ignoriert ein geerbtes `ANDROID_HOME`), AVD `pixel9` (API 37).
- Reihenfolge:
  ```bash
  scripts/emu-start.sh            # headless, mit RAM-Deckel; --window fuer ein Fenster
  scripts/anki-install.sh         # AnkiDroid v2.25.3 VOR der eigenen App (Signatur geprueft); optional Version als $1
  scripts/install-debug.sh        # baut und installiert App + Dummy-Apps
  adb shell pm grant dev.tinow.brainrotstopper com.ichi2.anki.permission.READ_WRITE_DATABASE
  scripts/a11y-enable.sh          # Dienst aktivieren und auf "gebunden" warten
  scripts/check.sh                # alles Gruen-Pruefende (Unit, APKs, Manifest, Instrumentiert, SSIM)
  ```
- Nach `check.sh` ist der Dienst oft nicht gebunden (Instrumentierung): `scripts/a11y-disable.sh && scripts/a11y-enable.sh`.
- E2E-Skripte: `scripts/e2e/` (Liste in `INDEX.md`). Release-Smoke: `scripts/e2e/e2e10_release.sh` (ersetzt kurz den Debug-Build und stellt ihn danach wieder her).
