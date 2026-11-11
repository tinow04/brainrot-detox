# Test-Checkliste

Matrizen, die ein E2E-Skript belegt. Werte aus den gruenen Laeufen (e2e7 viermal gruen, nach M6-T6 erneut); das Skript druckt die Matrix am Ende selbst aus.

## Fehlerfall-Matrix (M6-T4, `scripts/e2e/e2e7_errors.sh`, Belege `design/screenshots/m6-t4/`)

Grundsatz: fail-closed. Jeder Fehler laesst die Sperre vorn (`focus_stays LockActivity`), es gibt keine Freigabe, `abbrechen` fuehrt nach Home, und jeder Fehlerscreen zeigt den Notausgang mit Countdown. Ausnahmen mit Freigabe gibt es nur bei "nichts faellig" (NOTHING_DUE) und beim Notausgang (EMERGENCY). Echtes AnkiDroid 2.25.3 im Emulator, sonst Fake-Quelle (`SET_SOURCE --es source fake`).

| Fall | Wie erzeugt | Sperre / Gate | Hauptaktion | Setup-Zeilen | Config `decks` | Beleg |
|---|---|---|---|---|---|---|
| AnkiDroid deaktiviert | `pm disable-user com.ichi2.anki` | Fehler NotInstalledOrApiOff ("nicht erreichbar"), keine Freigabe | `$ ./erneut` | `open:InstallAnki,open,open,ok,…` | error NotInstalledOrApiOff | `1-disabled` |
| AnkiDroid deinstalliert | `pm uninstall -k` (Daten bleiben), danach das vorher gezogene APK neu installieren | wie oben | `$ ./erneut` | `open:InstallAnki,open,open,ok,…` | error NotInstalledOrApiOff | `2-uninstalled` |
| API aus | Provider-Komponente `pm disable` (wirkt wie der UI-Schalter „AnkiDroid-API aktivieren“ aus, der per adb nicht setzbar ist) | wie oben | `$ ./erneut` | `ok,ok,error:OpenAnki,ok,…` | error NotInstalledOrApiOff | `3-api-off` |
| Berechtigung entzogen | `pm revoke … READ_WRITE_DATABASE` (beendet den App-Prozess) | Fehler PermissionMissing | `$ ./app-info` | `ok,open:RequestPermission,open,ok,…` | error PermissionMissing | `4-permission` |
| Kein Speicherzugriff | `appops set com.ichi2.anki MANAGE_EXTERNAL_STORAGE ignore` (null-Cursor) | Fehler NotInstalledOrApiOff, nie "nichts faellig" | `$ ./erneut` | `ok,ok,error:OpenAnki+RetryProbe,ok,…` | error NotInstalledOrApiOff | `5-no-storage-access` |
| AnkiDroid frisch (Speicher nicht eingerichtet) | Daten sichern, `pm clear com.ichi2.anki`, danach zuruecksichern | Fehler StorageNotConfigured beim 1. Aufruf; AnkiDroid richtet sich dabei selbst ein | `$ ./ankidroid oeffnen` | 2. Aufruf: alles ok | ok | `6-fresh-ankidroid` |
| AnkiDroid haengt | `kill -STOP <pid>` | Fehler Unreachable nach ca. 4,2 s (erster Aufruf des Prozesses 8,2 s), Retry ebenso; nach `kill -CONT` normal | `$ ./erneut` | - | - | `7-hanging` |
| AnkiDroid gekillt | `kill -9 <pid>` | kein Fehler, der Provider-Aufruf startet AnkiDroid neu (Karte bzw. Freigabe nach ca. 0,3 s) | - | - | - | `8-ankidroid-killed` |
| Leeres Deck | echtes leeres Deck `Leer` der Emulator-Collection (fehlt es, legt das Skript es per Provider an), `SET_DECK` | Freigabe NOTHING_DUE (erlaubt) | `$ ./open dummy` | alles ok | ok | `9-empty-deck` |
| Gewaehltes Deck geloescht | `SET_DECK --es id 4242` (Provider kann Decks nicht loeschen) | Fehler DeckMissing; danach anderes Deck gewaehlt -> gilt sofort in derselben Sitzung (M6-T6) | `$ ./einstellungen` | alles ok | ok (Anzeige "deck fehlt": `e2e_config.sh` Schritt 5) | `10-deck-missing` |
| Counts unlesbar | Fake `counts_unreadable` (leerer Cursor, Counts BadData) | Fehler BadData, nie "nichts faellig" | `$ ./erneut` | `ok,ok,error:RetryProbe,ok,…` | error BadData | `11-counts-unreadable` |
| Giftkarte | Fake `poison_card`: die WebView laedt `chrome://crash`, der Renderer stuerzt wirklich ab | App-Prozess und Dienst leben, Karte als Klartext, bewertbar (1/5 gezaehlt) | - | - | - | `12-poison-question`, `12-poison-answer` |
| Nichts faellig | Fake `empty` | Freigabe NOTHING_DUE (erlaubt) | `$ ./open dummy` | - | - | `13-nothing-due` |
| Lernkarten noch nicht faellig | Fake `waiting` | Wartescreen, keine Freigabe | - | - | - | `13-waiting` |
| Notausgang | Fake `error-unreachable`, 60 s warten | Freigabe EMERGENCY 5 min (erlaubt) | `$ ./notausgang` | - | - | `14-emergency-countdown`, `14-emergency-ready` |

Zusaetzlich: kein Crash des Eigenpakets in `dumpsys dropbox`, `diag.serviceErrorCount`/`launchFailedCount` 0. Setup-Zeilen: AnkiDroid installiert, Berechtigung, Provider, Bedienungshilfe, Akku (`open:Battery` im Emulator, Akku-Ausnahme nicht gesetzt).

## Am Geraet (Pixel 9, manuell, noch offen)

Alles oben ist im Emulator (API 37, Pixel-9-Profil) belegt. Diese Punkte gehen nur auf echter Hardware; je Schritt das erwartete Ergebnis. Ergebnis mit Datum in `gotchas.md` eintragen, Abweichungen als Aufgabe in `todo.md`. Installation: `install.md` Abschnitt A.

| # | Schritt | Erwartet |
|---|---|---|
| 1 | Release per `adb install` installieren | Keine Play-Protect-Blockade; falls Warnung/Scan: Ergebnis notieren (24-h-Warnung bei Sideload?) |
| 2 | Bedienungshilfe im Setup einschalten | Ohne „eingeschraenkte Einstellungen“-Sperre; sonst ueber App-Info ⋮ freigeben, Setup-Zeile wird gruen. Android-Erinnerung per Benachrichtigung erscheint |
| 3 | Instagram, TikTok, YouTube sperren und je 5x oeffnen (kalt und aus Recents) | Sperre jedes Mal; kurzes Aufblitzen des Splash-Screens hoechstens ca. 0,5 s, kein Inhalt bedienbar |
| 4 | Latenz grob: App oeffnen bis Sperre sichtbar | gefuehlt sofort (Emulator: Median 65 ms, Release 111-123 ms) |
| 5 | 5 Karten richtig beantworten | Freigabe, App startet, laeuft 15 min ohne Sperre; danach sofort wieder gesperrt |
| 6 | Am Desktop-Anki nach dem Sync die Revlog-Eintraege pruefen (Browser > Karteninfo) | Antworten aus der App stehen im Verlauf mit richtiger Bewertung |
| 7 | AnkiDroid-Sync nach Freigabe (Schalter an, Konto am eigenen Server) | AnkiDroid blitzt ca. 0,5 s auf, Sync laeuft, danach Ziel-App ohne Sperre; bei Sync-Dialog bleibt AnkiDroid vorn (bekannt) |
| 8 | Karte mit Bild (eigener Server, Token gesetzt) | Bild sichtbar; im Flugmodus Platzhalter `[ bild: x ]`, Karte bewertbar |
| 9 | Handy 24 h normal nutzen, Akku-Optimierung aus | Dienst bleibt gebunden (kein Banner „schutz inaktiv“), Sperren greifen am naechsten Tag |
| 10 | Dasselbe mit Akku-Optimierung an (Adaptive Battery) | Notieren, ob/wann der Dienst stirbt (Standby-Bucket `adb shell am get-standby-bucket dev.tinow.brainrotstopper`) |
| 11 | Neustart des Handys | Nach dem Entsperren bindet der Dienst wieder; laufende Freigaben sind ungueltig (so gewollt) |
| 12 | Advanced Protection (falls genutzt) | Bedienungshilfe ggf. gesperrt -> Setup zeigt den Hinweis; dann sperrt die App nichts |
| 13 | Speicherdruck (viele Apps offen, Spiel) | Dienst bleibt oder kommt zurueck (ActivityManager-Backoff 1/4/16/64 s, M6-T1); Ergebnis notieren |
| 14 | Hell/Dunkel, Schriftgroesse gross, Gestennavigation | Layout wie im Emulator (M6-T5), nichts abgeschnitten |
