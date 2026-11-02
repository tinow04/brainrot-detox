# Workflow-Prompt: Brainrot Stopper entwickeln (Ultracode)

Stand: 2026-11-02. Anwendung: in einer Session mit eingeschaltetem Ultracode den Block unter der Linie einfügen. Oder kürzer: „Lies `.agents/workflow-prompt.md` und führe den Prompt darin als Workflow aus."

Getestet wird im **Android-Studio-Emulator**, nicht auf dem Handy.

Vorher vom User zu erledigen (braucht sudo/GUI, kann der Workflow nicht):
1. `! yay -S android-studio` (AUR, groß). Beim ersten Start den Setup-Wizard mit „Standard" durchlaufen. Das installiert das SDK nach `~/Android/Sdk`.
2. In Android Studio → **Settings → Languages & Frameworks → Android SDK**:
   - Tab *SDK Platforms*: die neueste stabile Android-Version (API 35 oder höher) mit **Google APIs** x86_64 System Image anhaken (**nicht** „Google Play", sonst gibt es kein `adb root`)
   - Tab *SDK Tools*: **Android SDK Command-line Tools (latest)**, **Android Emulator** und **Android SDK Platform-Tools** anhaken, Apply
3. Optional: in *Device Manager* ein virtuelles Gerät „Pixel 9" mit diesem Image anlegen. Fehlt es, legt der Workflow es per `avdmanager` an.

---

Ultracode. Entwickle die Android-App **Brainrot Stopper** vollständig mit einem Workflow aus mehreren Agenten. Ziel ist die bestmögliche, korrekte und getestete App, Tokenkosten spielen keine Rolle. Die Agentenzahl ist nicht festgelegt, aber begrenzt: so viele wie nötig, keine Endlosschleifen.

## Rollen und Modelle

- **Denken, Recherche, Entwurf, Review, Bewertung:** Agenten mit `model: 'opus'` (Opus 5.5) und `effort: 'high'`.
- **Implementierung, Build, Fixes, Doku-Pflege:** Agenten mit `model: 'sonnet'` (Sonnet 5.5) und `effort: 'high'`.
- Opus schreibt keinen Produktivcode. Sonnet trifft keine Architekturentscheidungen, sondern setzt die Aufgaben der Opus-Pläne um. Weicht Sonnet ab, muss es das begründen und in `.agents/decisions.md` eintragen.

## Kontext (nicht neu erkunden, sondern lesen)

Arbeitsverzeichnis: `<projektordner>`. Jeder Agent liest zuerst `CLAUDE.md` und `.agents/INDEX.md`, dann nur die für seine Aufgabe relevanten Dateien:
- `.agents/decisions.md` (getroffene Entscheidungen), `.agents/architecture.md` (geplante Komponenten), `.agents/gotchas.md` (Annahmen, die **noch unverifiziert** sind), `.agents/todo.md`, `.agents/mockups.md`
- `design/mockups/*.dc.html`, `theme.css`, `canvas.json`: Quelle der UI-Mockups (16 Ansichten: 8 Artboards je dunkel und hell). Dazu die Design-Tokens in `.agents/mockups.md`. Die UI wird 1:1 danach gebaut (Jetpack Compose, JetBrains Mono, Catppuccin Mocha/Latte, folgt dem System-Theme).

Kurzfassung des Produkts: Wird eine ausgewählte App (z. B. Instagram, TikTok) geöffnet, legt sich ein Overlay darüber. Es fragt Anki-Karten aus AnkiDroid ab (Aufdecken, dann selbst bewerten: nochmal/schwer/gut/leicht). Nur gut und leicht zählen. Nach 5 richtigen Karten ist die App für eine einstellbare Dauer (Standard 15 min) freigegeben. Bei nochmal/schwer kommt die Karte später wieder und der Zähler bleibt. Die Haupt-App hat App-Auswahl, Einstellungen (Kartenanzahl, Dauer, Deck) und Statistik. Karten und Scheduling kommen über die **AnkiDroid-API**, Antworten werden an AnkiDroid zurückgegeben, sodass der Fortschritt über AnkiWeb mit dem PC synchronisiert werden kann. Es gibt keinen eigenen Sync-Code.

Zielgerät: Pixel 9 (Android). Entwickelt und getestet wird im **Emulator** (AVD auf Basis Pixel 9, Google-APIs-Image ohne Play Store, x86_64). Kein physisches Gerät, kein Play Store. Die App ist sideloaded (APK), also gelten auch im Emulator die „eingeschränkten Einstellungen" für Accessibility.

## Umgebung (Fakten, Stand 2026-11-02)

- Arch Linux, 12 Kerne, 15 GB RAM (davon zeitweise nur ca. 7 GB frei), `/dev/kvm` ist für alle schreibbar (Emulator-Beschleunigung geht). Installiert: `jdk17-openjdk` (`/usr/lib/jvm/java-17-openjdk`), `jdk25-openjdk` (Standard-`java`, für Android-Builds **zu neu**), `adb` (`android-tools`), `gh`, `git`.
- **Gradle immer mit `JAVA_HOME=/usr/lib/jvm/java-17-openjdk` ausführen**, nicht mit dem Standard-Java. Für `sdkmanager`/`avdmanager`/`emulator` ebenfalls ein passendes JDK verwenden (JDK 17 oder das von Android Studio mitgelieferte).
- Android Studio und das SDK (`~/Android/Sdk`) stehen laut Preflight-Voraussetzung bereit. Phase 0 prüft das erneut und bricht sonst ab. Gradle läuft trotzdem nur über den Gradle-Wrapper im Projekt, nicht über Android Studio.
- Kein sudo.
- GitHub-Repo: `https://github.com/tinow04/brainrot-detox` (privat, leer, `gh` ist eingerichtet). Der lokale Ordner heißt `brainrot-stopper`, das ist beabsichtigt.

## Harte Regeln

1. Kein `git push` und keine GitHub-Aktionen, die Daten veröffentlichen, ohne ausdrückliche Freigabe des Users. Lokal committen ist erlaubt (siehe Phase 0).
2. Nichts außerhalb des Projektordners ändern, außer dem SDK unter `$HOME/Android/Sdk` (Phase 0). Kein `sudo`.
3. Commit-Messages auf Englisch, Chat-Ausgaben und `.agents/`-Doku auf Deutsch, Code und Bezeichner auf Englisch. Jeder Commit endet mit der Zeile `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
4. Keine Secrets, `local.properties`, Keystores oder `build/` ins Repo (`.gitignore` anlegen).
5. Alles, was nicht belegt ist, als `(unverifiziert)` kennzeichnen. API-Details von AnkiDroid und Android nie aus dem Gedächtnis behaupten, sondern gegen Quellcode oder Doku prüfen (WebSearch/WebFetch, Repo `ankidroid/Anki-Android`, developer.android.com).
6. `.agents/` laufend pflegen (siehe `.agents/README.md`): Entscheidungen mit Begründung, Architektur, Stolperfallen, `todo.md`, Session-Eintrag in `log.md`, `INDEX.md` bei neuen Dateien.
7. Nach jedem Schritt, der Code ändert, muss `./gradlew assembleDebug` erfolgreich sein. Rote Builds werden nicht committet.

## Phasen

### Phase 0: Preflight (Sonnet, ein Agent, kein Opus nötig)
- `git init` im Projektordner (Branch `main`), `origin` auf das GitHub-Repo setzen, `.gitignore` für Android/Gradle/IDE anlegen, ersten Commit mit dem bisherigen Stand (`CLAUDE.md`, `.agents/`, `design/`) erstellen. **Nicht pushen.**
- Prüfen: `JAVA_HOME` (JDK 17), `adb`, SDK unter `$HOME/Android/Sdk` mit `cmdline-tools`, `emulator`, `platform-tools` und einem **google_apis x86_64** System Image. Fehlt etwas Wesentliches (`sdkmanager`, `emulator`, System Image), **bricht der Workflow hier ab** und meldet dem User genau, was er in Android Studio nachholen muss. Fehlen nur Kleinigkeiten (Lizenzen, `build-tools`, `platforms;android-NN` laut Phase 2), selbst per `sdkmanager --sdk_root=$HOME/Android/Sdk` nachinstallieren. `sdk.dir` nur per `local.properties` setzen, `ANDROID_HOME` nicht global.
- Emulator: Gibt es schon ein AVD für Pixel 9, dieses verwenden, sonst per `avdmanager` eines anlegen (Gerät `pixel_9`, falls vorhanden; RAM ca. 3 GB wegen des knappen Arbeitsspeichers). Läuft bereits ein Emulator (`adb devices`), diesen verwenden, sonst headless starten (`emulator -avd <name> -no-window -no-audio -no-snapshot-save`, GPU-Modus passend wählen, Boot mit `adb wait-for-device` und `sys.boot_completed` abwarten). Vorher `adb root` testen: nur mit dem Google-APIs-Image (ohne Play Store) geht es. Das Ergebnis ist für die Revlog-Prüfung in Phase 1 wichtig.
- **AnkiDroid im Emulator:** Beschaffung und Installation (offizielle Quelle wie GitHub-Releases von `ankidroid/Anki-Android` oder F-Droid, passende ABI für x86_64, Herkunft nennen) ist Teil von Phase 1/2, nicht von Phase 0. Nie mit dem echten AnkiWeb-Account des Users anmelden und nie dessen Zugangsdaten verwenden. Es wird nur mit lokalen Testdaten gearbeitet.
- Ergebnis kurz in `log.md` festhalten.

### Phase 1: Recherche und Verifikation (Opus, parallel, dann Gegenprüfung)
Parallele Recherche-Agenten mit je eigenem Blickwinkel. Jeder liefert belegte Fakten mit Quellen (URL, Datei, Zeile) und markiert Unsicheres:
1. **AnkiDroid-API:** aktuelle Doku und Quellcode des `api`-Moduls (`FlashCardsContract`, `ReviewInfo`, Content-Provider, Berechtigung, Intents, Decks und fällige Karten abfragen, Karte beantworten mit Ease). Wird eine per API beantwortete Karte im **Revlog** verbucht (damit PC-Statistiken stimmen)? Das soll zusätzlich **praktisch im Emulator** geprüft werden: Testdeck mit Beispielkarten anlegen (per `AddContentApi` aus einem Debug-Hilfscode oder per Import), eine Karte per API beantworten, dann mit `adb root` die AnkiDroid-Collection (`collection.anki2`) ziehen und den `revlog` mit `sqlite3` lesen. Geht das nicht, als `(unverifiziert)` dokumentieren. Kann die App einen **Sync** in AnkiDroid auslösen (nur per Quellcode/Doku bewerten, kein Login im Emulator)? Wie rendert man Kartentemplates (HTML, Bilder/Medien, Audio, Cloze) in einer WebView? Package-Sichtbarkeit (`<queries>`) auf Android 11+.
2. **App-Blocking auf Android 15/16:** `AccessibilityService` vs. `UsageStatsManager`; Overlay (`SYSTEM_ALERT_WINDOW`) vs. eigene Activity; Einschränkungen für Background-Activity-Starts; Foreground-Service-Typen; Verhalten bei Neustart/Doze/Akku-Optimierung des Pixel; „eingeschränkte Einstellungen" bei sideloaded Apps für Accessibility; wie man Umgehungen (Zurück-Taste, Recents, Split-Screen) erschwert.
3. **Build-Toolchain:** aktuelle stabile, miteinander kompatible Versionen von Gradle, Android Gradle Plugin, Kotlin, Compose BOM, `compileSdk`/`targetSdk`, lauffähig mit JDK 17. Version Catalog verwenden.
4. **Persistenz und Hintergrund:** DataStore/Room für Einstellungen, Statistik, Freigabe-Timer; Zuverlässigkeit des Timers (Zeitstempel statt laufender Timer).
- Danach pro Recherche-Ergebnis **unabhängige Opus-Skeptiker** (mindestens 2), die zentrale Behauptungen zu widerlegen versuchen (im Zweifel „nicht bestätigt"). Nur bestätigte Fakten gelten. Widersprüche und Lücken gehen in eine zweite Recherche-Runde, bis nichts Neues kommt (max. 3 Runden).
- Ergebnisse in `.agents/gotchas.md` und `.agents/decisions.md` einarbeiten (belegt statt `(unverifiziert)`). Ist der Revlog-/Sync-Punkt negativ, die Konsequenz für die Architektur ausdrücklich festhalten.

### Phase 2: Entwurf (Opus, Judge-Panel)
- 3 unabhängige Architekturentwürfe aus verschiedenen Blickwinkeln (z. B. risikoärmste Lösung, UX-nächste Lösung an den Mockups, robusteste gegen Umgehung/Hintergrundkill), jeder inkl. Modulaufteilung, Datenfluss, Zustandsmodell des Sperr-Flows, Datenmodell, Berechtigungs-Einstieg (nötig, obwohl nicht in den Mockups: Accessibility, Overlay, Akku-Optimierung, AnkiDroid-API-Berechtigung; schlicht im Terminal-Stil gestalten).
- Mehrere Judges bewerten die Entwürfe nach festen Kriterien (Korrektheit, Umsetzbarkeit durch Sonnet, Robustheit, Nähe zu den Mockups, Testbarkeit) und synthetisieren die beste Architektur samt der besten Ideen der anderen.
- Ein weiterer Opus-Agent zerlegt das Ergebnis in **Meilensteine mit Aufgabenlisten**. Jede Aufgabe nennt Dateien, Schnittstellen, Akzeptanzkriterien und wie sie gebaut/getestet wird. Vorschlag für die Reihenfolge (der Plan darf begründet abweichen): M1 Projektgerüst + Theme/Typografie aus den Tokens, Hell/Dunkel; M2 Berechtigungs-Einstieg + App-Erkennung + Overlay-Grundgerüst; M3 AnkiRepository + Quiz-UI (alle 5 Sperr-Screens); M4 Freigabe-Logik, Einstellungen, Persistenz; M5 App-Auswahl + Statistik; M6 Härtung (Neustart, Umgehungen, Fehlerfälle: AnkiDroid fehlt/API aus, keine fälligen Karten, Deck leer).
- Ergebnis in `.agents/architecture.md` und `.agents/todo.md` schreiben. **Offene Entscheidungen, die der User treffen muss, sammeln statt raten** und am Ende melden. Bis dahin sinnvolle Standardwerte nehmen und als solche markieren.

### Phase 3: Implementierung (Sonnet, Meilenstein für Meilenstein, nacheinander)
Meilensteine laufen **sequenziell**, weil sie dasselbe Repo ändern. Innerhalb eines Meilensteins nur dann parallel, wenn die Aufgaben disjunkte Dateien haben. Pro Meilenstein:
1. **Implementieren** (Sonnet): Aufgaben aus dem Plan umsetzen, dazu Unit-Tests, wo sinnvoll (z. B. Zähl-/Freigabe-Logik, Zustandsmaschine).
2. **Prüfen** (Sonnet): `./gradlew assembleDebug`, Unit-Tests und Lint mit JDK 17. Ausgabe auswerten, Fehler beheben. Ab M2 zusätzlich **im Emulator**: APK installieren (`adb install -r`), Berechtigungen per `adb shell` bzw. `settings put`/`appops`/`cmd` setzen, soweit ohne UI möglich, sonst per `uiautomator dump` und `adb shell input`-Eingaben durch die Einstellungen klicken. Dann den Sperr-Flow wirklich durchspielen (gesperrte Test-App öffnen, Overlay erscheint, 5 Karten beantworten, Freigabe). Belege sind Screenshots (`adb exec-out screencap -p`), die der Agent selbst ansieht und mit den Mockups vergleicht, plus `logcat`-Auszüge. Als Test-App dienen Systemapps wie Einstellungen oder ein Minimal-Dummy-App-Modul im Projekt, nicht Instagram/TikTok.
3. **Review** (Opus, parallele Blickwinkel, alle gegen den echten Code und die Mockups): (a) Korrektheit und Logikfehler, (b) Android-Lebenszyklus, Berechtigungen, Hintergrundverhalten, (c) Umsetzung gegen Mockups (Layout, Tokens, Texte, Hell/Dunkel, Touch-Ziele ≥ 48 dp), (d) Umgehbarkeit/Sicherheit des Sperr-Flows. Jeder Befund wird von einem unabhängigen Opus-Agenten adversarial bestätigt oder verworfen.
4. **Fixen** (Sonnet): bestätigte Befunde beheben, wieder bauen und prüfen. Wiederholen, bis ein Review-Durchlauf nichts Bestätigtes mehr findet (max. 3 Runden pro Meilenstein; Rest dokumentieren).
5. **Commit** lokal (Englisch, mit Co-Author-Zeile), `.agents/` aktualisieren (`todo.md`, `log.md`, ggf. `architecture.md`, `gotchas.md`, `INDEX.md`).
Zeigt sich bei der Implementierung, dass der Plan falsch ist, geht die Frage zurück an Opus (Plan-Korrektur), nicht an Sonnet zum Improvisieren.

### Phase 4: Abschluss (Opus + Sonnet)
- **Vollständigkeits-Kritiker** (Opus): Was fehlt gegenüber Produktbeschreibung und Mockups? Welche Annahmen sind weiter unbelegt? Welche Fehlerfälle sind ungetestet? Befunde werden als Nacharbeit in einer weiteren Sonnet-Fix-Runde mit Review abgearbeitet.
- **End-to-End-Test im Emulator:** Debug-APK bauen und installieren, den kompletten Ablauf durchspielen (Berechtigungs-Einstieg, App auswählen, gesperrte App öffnen, 5 Karten mit Fehlern dazwischen, Freigabe, Ablauf der Freigabe, erneutes Sperren). Dazu: Neustart des Emulators (Dienst läuft wieder an?), Hell/Dunkel-Wechsel, Umgehungsversuche (Zurück, Home, Recents), AnkiDroid nicht installiert/API aus/kein fälliges Deck. Screenshots der Hauptansichten in `design/screenshots/` ablegen (klein halten) und gegen die Mockups prüfen.
- Alles, was im Emulator nicht prüfbar ist (echtes Pixel-Verhalten bei Akku-Optimierung, echter AnkiWeb-Sync zum PC, echte Social-Apps), kurz in `.agents/test-checklist.md` sammeln, mit Schritten und erwartetem Ergebnis, aber **ohne dass der User es jetzt tun muss**. Es ist eine spätere, optionale Checkliste.
- Ist ein physisches Gerät per `adb devices` verbunden, wird **nicht** darauf installiert, außer der User verlangt es.
- `.agents/` final aktualisieren.

## Rückgabe an den User (Chat, Deutsch, knapp)

1. Was gebaut wurde und was nicht (ehrlich: Build-Status, Testergebnisse, übersprungene Schritte, wo die Review-Schleifen mit Restbefunden endeten)
2. Ergebnis der Verifikation der bisher unverifizierten Annahmen (Revlog, Sync, Blocking-Mechanismus)
3. Offene Entscheidungen für den User
4. Wie der User die App selbst im Android-Studio-Emulator ausprobiert (Projekt öffnen, AVD starten, Run), plus Verweis auf die optionale Checkliste für später
5. Hinweis, dass nichts gepusht wurde, plus Frage, ob nach `origin` gepusht werden soll
