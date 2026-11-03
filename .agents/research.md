# Recherche: verifizierte Faktenbasis

Stand 2026-11-02. Ergebnis der Recherche mit Gegenpruefung durch je zwei Skeptiker. Eintragsformat: **ID** `[Status]` Behauptung. Beleg: ... Konsequenz: ...

Status: `bestaetigt` = gegen Primaerquelle/Code belegt (Belege teils nur per Skeptiker, Details im Eintrag), `umstritten` = Quellen/Pruefer widersprechen sich oder Pruefung unvollstaendig, `unverifiziert` = nicht geprueft, nur Arbeitsannahme. `unverifiziert` heisst: im Code als `(unverifiziert)` kommentieren und per Emulator-Messung (M0-Spikes, siehe `todo.md`) ersetzen.

Hinweis zu Zeilennummern: AnkiDroid-Belege beziehen sich auf Tag **v2.25.3**, ausser ausdruecklich `main`. Einige Zeilen der urspruenglichen Recherche stammen aus `main` und weichen ab (z. B. `getAnkiDroidPackageName` Z. 864 statt 842). Vor Uebernahme in Code gegen Tag v2.25.3 neu setzen.

Zielversion AnkiDroid: v2.25.3. Alle Entscheidungen daraus stehen in `decisions.md`, die Architektur in `architecture.md`.

---

## 1. AnkiDroid-API und Kartenrendering (Ziel v2.25.3)

### Version, Bezug, Lizenz

- **A1** `[bestaetigt]` Stabile Version ist v2.25.3 (2026-10-17); v2.26.0alpha* sind Prereleases. `variant-abi-AnkiDroid-2.25.3-x86_64.apk` (43797921 B) und Universal-APK (111759228 B) liegen im GitHub-Release. Beleg: `gh api repos/ankidroid/Anki-Android/releases` (latest=v2.25.3, alpha10 prerelease), beide Skeptiker; Aussage "alpha1 bis alpha10" nicht vollstaendig geprueft (irrelevant). Konsequenz: gegen v2.25.3 entwickeln und testen, sha256 aus Release-API pruefen, Regression gegen 2.26-Alphas in M7.
- **A2** `[bestaetigt]` api-Modul 2.0.0 (`com.ichi2.anki:api`) nur lokal/JitPack; das Wiki nennt die veraltete Koordinate `api-v1.1.0`. JitPack-Builds einzelner Tags schlagen fehl (v2.25.0, v2.25.2 Error). Beleg: `api/build.gradle.kts` (v2.25.3) Z. 9-10; jitpack.io/api/builds/com.github.ankidroid/Anki-Android; Wiki AnkiDroid-API. "Nicht auf Maven Central" nur aus der Build-Datei abgeleitet, nicht gegen Central geprueft. Konsequenz: keine Gradle-Abhaengigkeit, Konstanten selbst definieren.
- **A3** `[bestaetigt]` `FlashCardsContract.kt` ist ausdruecklich nicht GPL und frei kopierbar. KORREKTUR: in v2.25.3 nutzt die Datei `BuildConfig.AUTHORITY` / `BuildConfig.READ_WRITE_PERMISSION` (Release: `com.ichi2.anki.flashcards`), nicht `ANKI_PACKAGE_NAME`. Beleg: FlashCardsContract.kt v2.25.3 Z. 1-6; api/build.gradle.kts Z. 26-38 (Skeptiker 1); Skeptiker 2 nicht pruefbar. Konsequenz: feste Strings `com.ichi2.anki.flashcards` verwenden, Ease 1..4 als eigene Konstanten; Kopfzeile vor Uebernahme nochmals lesen.
- **A3b** `[unverifiziert]` `Ease.kt` steht unter LGPL-3.0 (Werte 1..4). Von keinem Skeptiker geprueft. Konsequenz: Ease-Datei nicht kopieren, eigene Konstanten.

### Berechtigung, Erkennung, Fehlerverhalten

- **A4/A5** `[bestaetigt]` Permission `com.ichi2.anki.permission.READ_WRITE_DATABASE` ist `protectionLevel=dangerous` (Runtime-Anfrage noetig). Der Provider wirft bei query/update/insert/bulkInsert/delete `SecurityException("Permission not granted for: ...")`; Release nutzt `checkCallingPermission`. Beleg: AndroidManifest.xml v2.25.3 Z. 45-49; CardContentProvider.kt v2.25.3 Z. 234, 488, 761, 801, 891, 1447-1470; Wiki ("On SDK 23+ you must also do a permission check"). Konsequenz: uses-permission + Runtime-Dialog; alle Provider-Aufrufe in try/catch(SecurityException) -> Zustand "Zugriff fehlt".
- **A6** `[bestaetigt]` Manifest braucht `<queries><package android:name="com.ichi2.anki"/></queries>`, sonst "Failed to find provider info" ab SDK 30. Debug-/Parallel-Builds nicht unterstuetzt (Authority fest je Build; Parallel-Authorities nicht geprueft). Beleg: Wiki AnkiDroid-API.md Z. 30-37. Konsequenz: queries-Eintrag setzen.
- **A7** `[bestaetigt]` "Enable AnkiDroid API" (`providerEnabled`) ist Standard an, ausschaltbar; Ausschalten deaktiviert die Provider-Komponente per `setComponentEnabledSetting`. Provider im Manifest `exported=true`. Beleg: preferences_advanced.xml Z. 88-92, AdvancedSettingsFragment.kt Z. 154/157, AndroidManifest.xml v2.25.3 Z. 709-717 (Skeptiker 1). Konsequenz: Setup-Hinweis auf den Schalter. Achtung Widerspruch: ein anderer Pruefer fand per grep keinen "enabled"-Treffer im Provider-Code (Provider selbst hat keinen Schalter, nur Permission- und Rogue-Check); ob/wie der UI-Schalter heisst, ist im Emulator zu klaeren (M0-S1b-c).
- **A8** `[bestaetigt]` Erkennung per `resolveContentProvider("com.ichi2.anki.flashcards", 0)?.packageName`; null = nicht installiert/nicht unterstuetzt. Ob ein deaktivierter Provider ebenfalls null liefert, ist NICHT belegt (Annahme: ja, da ohne MATCH_DISABLED_COMPONENTS). Beleg: AddContentApi.kt v2.25.3 Z. 864-875. Konsequenz: UI-Zustand "AnkiDroid nicht installiert oder API aus" zusammenfassen; im Emulator pruefen.
- **A9** `[bestaetigt]` Ohne aufloesbaren Provider liefert `ContentResolver.query()` null, `update()` wirft `IllegalArgumentException("Unknown URI")`. Beleg: AOSP ContentResolver.java Z. 1213-1216 und 2412 (Skeptiker 1). Konsequenz: null-Cursor = "AnkiDroid nicht erreichbar", nie "nichts faellig"; IllegalArgumentException beim Antworten fangen.
- **A10** `[bestaetigt]` Bei nicht konfiguriertem Speicher wirft der Provider `IllegalStateException("AnkiDroid storage is not configured")`. Beleg: FlashCardsContract.kt v2.25.3 Z. 95-96 (Doku); Mapping in getColUnsafe nicht einzeln nachgelesen. Aktive Commits zu "storage not set up" auf main: Verhalten kann sich in 2.26 aendern. Konsequenz: eigener Fehlerzustand "AnkiDroid einmal oeffnen und einrichten".

### Decks, Warteschlange

- **B1/B2** `[bestaetigt]` Query auf `.../decks` liefert alle Decks (deck_id, deck_name mit `::`, deck_count, options, deck_dyn, deck_desc). `deck_count` ist JSON `[learn, review, new]` inkl. Unterdecks und Tageslimits; Einzeldeck via `decks/<id>`. Beleg: CardContentProvider.kt v2.25.3 Z. 386-410, 474-479 (Skeptiker 1). Rekursion/Unterdeck-Einschluss nur aus DeckNode-KDoc der Recherche. Konsequenz: Deckauswahl per DECKS-Query; deck_dyn markieren/ausblenden.
- **B3** `[bestaetigt]` Naechste Karte per Query auf `.../schedule` mit selection `limit=?, deckID=?` (limit Default 1); der Provider waehlt das Deck kurz aus und setzt es zurueck. Liefert note_id, ord, button_count, next_review_times, media_files. ZUSATZ: Wirft getQueuedCards/nextIvlStr eine Exception, wird das vorher gewaehlte Deck NICHT zurueckgesetzt (kein try/finally). Beleg: CardContentProvider.kt v2.25.3 Z. 327-389. Konsequenz: eine schedule-Query mit limit=1; Frage/Antwort per zweiter Query.
- **B5/G2** `[bestaetigt]` Nichts faellig oder ungueltige deckID -> leerer (nicht-null) Cursor ohne Exception. Jede schedule-Query mit deckID hat Schreib-Nebeneffekt (Deckauswahl kurz geaendert). Beleg: CardContentProvider.kt v2.25.3 Z. 347-352, 362-368, 327-389. Konsequenz: leer = faellig-0 oder Deck geloescht (per decks/<id> unterscheiden); nicht schleifenartig abfragen.
- **B4** `[unverifiziert]` Warteschlange enthaelt Lern-, Wiederholungs- und neue Karten im Rahmen der Tageslimits; Elterndeck schliesst Unterdecks ein. Von keinem Skeptiker geprueft (Recherche zitiert Scheduler.kt, rslib builder/mod.rs, docs.ankiweb.net/getting-started.html). Konsequenz: neue Karten zaehlen zum Tageslimit; Produktentscheidung "nur Wiederholungen?" bleibt offen (die API hat keinen Filter).

### Antworten

- **C1** `[bestaetigt]` Antworten per `update(.../schedule)` mit note_id, ord, answer_ease (1..4 = Again/Hard/Good/Easy), time_taken (ms). buried=1/suspended=1 schliessen sich mit answer_ease aus. Beleg: CardContentProvider.kt v2.25.3 Z. 680-725, 1269-1286. Konsequenz: Buttons auf 1..4 mappen; kein Ueberspringen/Begraben (veraendert den Lernplan).
- **C2** `[bestaetigt]` `time_taken` wird via `timerStarted = jetzt - time_taken` uebernommen (nur wenn != -1, Default -1), gekappt auf Deck-Limit; Backend-Cap nicht neu geprueft. Beleg: CardContentProvider.kt v2.25.3 Z. 1276-1281; Card.kt Z. 275-279; Scheduler.kt Z. 139-163. Die urspruengliche Recherche behauptete "timerStarted bleibt 0"; die Abweichung gilt zugunsten des Skeptikers. Konsequenz: time_taken immer mitsenden (elapsedRealtime, Pausen abziehen, >= 1).
- **C3** `[bestaetigt]` Die `update()`-Rueckgabe beweist keinen Erfolg (RuntimeException in answerCard wird geloggt, `updated++` trotzdem). Fehlt note_id/ord -> 0; fehlt answer_ease -> NPE (ausserhalb try). Ungueltiger Ease-Wert (0/5) wirft vermutlich ebenfalls ausserhalb des try (unverifiziert). Beleg: CardContentProvider.kt v2.25.3 Z. 680-725, 1282-1285. Konsequenz: alle RuntimeExceptions fangen; Erfolg optional ueber reps/last_review_time_secs verifizieren.
- **C4** `[unverifiziert]` `next_review_times` = JSON-Array mit 4 Eintraegen, `button_count` immer 4 (Provider-Code bestaetigt, Z. 372-389, 1259). Textformat (ANSWER_BUTTONS, kein "<"-Praefix, lokalisiert) nicht gegen rslib geprueft. Konsequenz: Intervall-Labels anzeigen, Format im Emulator pruefen (inkl. deutsche Lokalisierung).
- **C5** `[bestaetigt]` `media_files` = JSON-Array lokaler Mediendateinamen. Nur Naeherung: filesInStr ruft renderLatex auf, und der Remote-Test `Regex("(?i)https?|ftp://")` matcht "http" irgendwo im Namen (Falschpositive). Beleg: CardContentProvider.kt v2.25.3 Z. 1260-1262; Media.kt Z. 112-140. Konsequenz: nur als Hinweis nutzen; ob der Wert per getString als JSON-Text ankommt, ist ungeprueft.
- **D1** `[bestaetigt]` Die Provider-Antwort nutzt denselben Backend-Aufruf `backend.answerCard(buildAnswer(...))` wie der Reviewer, ueber den als "Legacy path, used by tests" kommentierten `Scheduler.answerCard(card, rating)`. Beleg: Scheduler.kt v2.25.3 Z. 127-163. Konsequenz: Scheduling wie in AnkiDroid; Risiko: Legacy-Pfad kann spaeter entfallen -> Regressionstest gegen 2.26.x.
- **D2/D3** `[unverifiziert]` Antworten landen via rslib im Revlog und in der Karte mit aktueller usn und gehen in den normalen Sync ein. Beide Skeptiker: nicht pruefbar; Belege der Recherche nur aus rslib-main (answering/mod.rs Z. 317-418), nicht aus der in 2.25.3 gebundenen Backend-Version. Konsequenz: Emulatortest (Antwort per Provider, danach sqlite3 read-only auf collection.anki2/revlog) in M0-S1a.
- **D4** `[unverifiziert]` Die API hat keine URI fuer das Revlog; pro Karte sind reps, lapses, last_review_time_secs lesbar. Skeptiker 1: UriMatcher nicht Zeile fuer Zeile verglichen, aber keine Override-Methode fuer Revlog gefunden. Konsequenz: eigene Statistik in DataStore fuehren.
- **D5** `[bestaetigt]` Nach erfolgreichem update ruft der Provider `notifyAllValuesChanged()` (updated > 0). Beleg: CardContentProvider.kt v2.25.3 update() Ende; Inhalt von ChangeManager nicht nachgelesen. Konsequenz: offene AnkiDroid-UI wird informiert; Konflikt bei paralleler Nutzung derselben Karte bleibt moeglich.

### Sync

- **E1** `[bestaetigt]` Sync-Anstoss per Intent-Action `com.ichi2.anki.DO_SYNC` auf exportierte IntentHandler-Activity; AnkiDroid kommt kurz in den Vordergrund und beendet den DeckPicker. KORREKTUR: in v2.25.3 ruft DoSync `deckPicker.sync()` auf (SyncWorker/WorkManager-Beteiligung nicht belegt); `ACTION_DO_SYNC`-Konstante existiert nur in main. Beleg: AndroidManifest.xml v2.25.3 Z. 287-289; IntentHandler.kt v2.25.3 Z. 411, 446-486. Konsequenz: nicht im MVP.
- **E2** `[bestaetigt]` Intent-Sync ignoriert bei Abstand < 2 min (`INTENT_SYNC_MIN_INTERVAL`), nicht eingeloggt oder offline (dann Benachrichtigung). Metered-Policy-Sperre und "Sync laeuft schon" fuer v2.25.3 NICHT belegt (nur in main). Das Wiki nennt 5 min (veraltet). Beleg: IntentHandler.kt v2.25.3 Z. 446-489. Konsequenz: falls je genutzt, Mindestabstand >= 10 min, Erfolg nie garantiert.
- **E3** `[bestaetigt]` Auto-Sync in AnkiDroid nur wenn eingeschaltet (Standard aus, nicht einzeln nachgelesen): beim Start bei Abstand > 10 min und beim Verlassen des DeckPickers (`runInBackground=true` ohne Intervallpruefung). Kein periodischer Worker (nicht neu geprueft). Beleg: DeckPicker.kt v2.25.3 Z. 413, 1571-1607, 2464 (`AUTOMATIC_SYNC_MINIMAL_INTERVAL` = 10 min). Konsequenz: im Setup Auto-Sync in AnkiDroid empfehlen.

### Rendering und Medien

- **F4** `[bestaetigt]` Medien sind ueber den Provider nicht lesbar: CardContentProvider ueberschreibt nur onCreate, getType, query, update, delete, bulkInsert, insert (kein openFile/openAssetFile). Der zweite Provider (androidx FileProvider `apkgfileprovider`) ist nur fuer apkg-Export gedacht, seine Freigaben ungeprueft. Beleg: grep "override fun" in CardContentProvider.kt (v2.25.3 und main); Manifest v2.25.3 Z. 718ff. Konsequenz: Bilder/Audio nicht ueber die API ladbar; Platzhalter, Karten mit Medien nicht ueberspringen.
- **F1/F2/F3/F6/F7/F8** `[unverifiziert]` question/answer = fertiges HTML ohne CSS, `[anki:play]` -> `[sound:...]`; CSS aus `models/<mid>.css`; Body-Klassen card/cardN/mobile/android/nightMode; Cloze serverseitig als `span.cloze`; `{{type:}}` bleibt als `[[type:Feld]]`; Template-Fehler werden als Text geliefert. Von keinem Skeptiker geprueft (F1 nur indirekt: Helper replaceWithSoundTags/pureAnswer am Dateiende gesehen). Recherche-Belege: CardContentProvider.kt, TemplateManager.kt, Sound.kt, rslib cloze.rs/template_filters.rs, docs.ankiweb.net/templates/styling.html. Konsequenz: im Emulator mit echten Karten pruefen (Basic, Cloze, Typ-Feld, Bild). Default-CSS fuer `.cloze`, `[[type:...]]` per Regex entfernen, nightMode-Klasse.
- **F5** `[unverifiziert]` Speicherort einer Neuinstallation haengt von StoragePolicy ab (PUBLIC `/sdcard/AnkiDroid` vs APP_PRIVATE `Android/data/com.ichi2.anki/files/AnkiDroid`); Full-Version deklariert MANAGE_EXTERNAL_STORAGE. SetupStorage.kt v2.25.3 bestaetigt nur Default "AnkiDroid" im oeffentlichen Speicher und Nutzerwahl. Konsequenz: Medien-Direktzugriff nicht in den MVP-Pfad.

### Offen (AnkiDroid)

- Verhalten von `resolveContentProvider` bei per Einstellung deaktiviertem Provider (null oder nicht?) - Emulator-Test (M0-S1b).
- Revlog-Eintrag und Sync-Aufnahme einer per Provider beantworteten Karte in 2.25.3 praktisch pruefen (adb root + sqlite3); gebundene Backend-/rslib-Version von 2.25.3 nicht ermittelt.
- Install-Reihenfolge der dangerous-Permission (unsere App vor AnkiDroid): Runtime-Anfrage moeglich oder Neuinstallation noetig - unverifiziert.
- Medienzugriff (MANAGE_EXTERNAL_STORAGE, `Android/data` auf Android 15/16 gesperrt, anderer offizieller Weg) - kein Primaerbeleg.
- Standard-Speicherort der GitHub-Full-APK bei Neuinstallation im Emulator.
- Wann erscheinen Lernkarten nach Again wieder in getQueuedCards (Lernvorlauf/collapseTime, Standard 1200 s) - nicht gegen rslib-Queue-Logik geprueft.
- Welche Body-Klassen und welches Basis-CSS setzt AnkiDroids Reviewer exakt - nicht im Code gelesen.
- Parallelnutzung: Verhalten, wenn der User dieselbe Karte gleichzeitig in AnkiDroid lernt; eigene Sperre noetig?
- LaTeX/MathJax-Rendering im Provider-HTML (Bild vs. rohes `\( ... \)`); filesInStr ruft renderLatex auf.
- Ankommen von `media_files` per getString als JSON-Text sowie deutsche Formatierung der Intervall-Labels.
- Metered-Sync-Policy und "Sync laeuft schon" in v2.25.3 nicht gefunden; SyncWorker-Beteiligung bei DO_SYNC unklar.

### Empfehlungen (AnkiDroid)

1. Keine Gradle-Abhaengigkeit auf AnkiDroid-api: eigene Kotlin-Datei mit Authority `content://com.ichi2.anki.flashcards`, Spaltennamen und Ease 1..4. Ziel- und Testversion v2.25.3, Emulator-APK `variant-abi-AnkiDroid-2.25.3-x86_64.apk`.
2. Manifest: uses-permission `com.ichi2.anki.permission.READ_WRITE_DATABASE` plus `<queries>`; Runtime-Anfrage im Setup.
3. `AnkiRepository` mit klaren Zustaenden: NotInstalledOrApiOff, PermissionMissing (SecurityException), StorageNotConfigured (IllegalStateException), Unreachable (IllegalArgumentException bei update), NothingDue (leerer Cursor). Null-Cursor nie als "nichts faellig" werten; alle RuntimeExceptions fangen.
4. Ablauf pro Karte: eine schedule-Query (limit=1, deckID) -> zweite Query notes/<nid>/cards/<ord> fuer question/answer -> update schedule mit answer_ease 1..4 und time_taken (elapsedRealtime, Pausen abziehen). Keine Abfrageschleifen wegen Deck-Auswahl-Nebeneffekt.
5. Zaehllogik und Statistik selbst fuehren (kein Revlog in der API); Ease 3/4 = richtig. Dynamische Decks (deck_dyn=1) kennzeichnen.
6. Produktentscheidungen offen halten: nur Wiederholungen oder auch neue Karten; Verhalten bei "nichts faellig"; Ueberspringen via Begraben vs. nur in der App.
7. Rendering (alles per Emulator zu verifizieren): HTML = `<style>` Notiztyp-CSS + body mit Klassen card cardN mobile android (+nightMode); Default-CSS fuer `.cloze`; `[[type:...]]` entfernen; `[sound:...]` durch Audio-Hinweis; Medien nicht ueber API ladbar -> Platzhalter; Template-Fehlertext erkennen.
8. Sync nicht selbst implementieren; Auto-Sync in AnkiDroid empfehlen; DO_SYNC hoechstens selten und erst nach Schliessen des Overlays.
9. Emulator-Testplan vor Entwurf-Freeze: AnkiDroid einrichten; Provider-Schalter aus -> resolveContentProvider; Antwort per Provider -> revlog pruefen; next_review_times/media_files/question-HTML mit Basic-, Cloze-, Typ-Feld-, Bildkarte; Permission bei umgekehrter Installationsreihenfolge; Regression gegen 2.26.x-Alphas.

---

## 2. App-Blocking auf Android 15/16/17

- **A1** `[bestaetigt]` Accessibility-Services werden mit `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` u. a. gebunden. Beleg: AccessibilityServiceConnection.java (android-17.0.0_r1), Skeptiker 1; Zeilennummern ungeprueft. Konsequenz: Grundlage fuer startActivity aus dem Service.
- **A2** `[bestaetigt]` Das Token ohne originatingToken wird in BackgroundLaunchProcessController immer erlaubt (`BAL_ALLOW_TOKEN`). Beleg: `isBackgroundStartAllowedByToken()` in android-17.0.0_r1 BackgroundLaunchProcessController.java, Kommentar "internal token - always allow". Vorgelagerte Kette (ActiveServices/ServiceRecord) nicht neu gelesen. Konsequenz: Activity-Start aus dem Service laut Code erlaubt; Emulator-Test mit StrictMode/logcat bleibt Pflicht.
- **A3** `[bestaetigt]` Die BAL-Doku nennt Accessibility-Services nicht namentlich, aber die Ausnahme "bound by a service that has been granted permission to start background activities". Beleg: https://developer.android.com/guide/components/activities/background-starts; Meldung "Background activity launch blocked!", Tag ActivityTaskManager. Konsequenz: Tragfaehigkeit stuetzt sich auf AOSP-Code, nicht auf Doku.
- **A5** `[bestaetigt]` PendingIntent-Opt-in ab targetSdk 34 (Sender) und 35 (Creator); empfohlen `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` (added in SDK 36, nicht Android 17). Fuer `IntentSender.sendIntent()` gilt ab API 37 ein Sender-Opt-in. Beleg: BAL-Doku. Konsequenz: Sperr-Activity direkt per startActivity, keine PendingIntents.
- **C3/C4/G3** `[bestaetigt]` Predictive Back (targetSdk 36), Edge-to-Edge, sw600dp-Ausnahme. Beleg: Android-16-Behavior-Changes-Seite (Skeptiker 1). Konsequenz: BackHandler und Insets in der Sperr-Activity.
- **F3** `[bestaetigt]` Android 17 Advanced Protection (opt-in) sperrt Accessibility-Services ohne `isAccessibilityTool`; bereits erteilte Berechtigungen werden beim Einschalten entzogen, Neuvergabe blockiert. Beleg: Presse (thehackernews.com 2026/10, heise.de/-11210703, helpnetsecurity.com 2026/03/19, securityaffairs.com/189497, Android Authority); AOSP AccessibilityManagerService nicht neu gelesen. Quellen widersprechen sich bei der Einfuehrung (Beta 2 vs. 17.2), fuer die Folgerung irrelevant. Konsequenz: bei aktivem Advanced Protection funktioniert die App nicht; Hinweis anzeigen; nie `isAccessibilityTool=true`. (Andere Pruefung: nur Presse, keine offizielle Doku -> Status faktisch `unverifiziert` auf Primaerquellenebene.)
- **H1** `[bestaetigt]` Developer Verification: 30.09.2026 nur BR/ID/SG/TH, global 2027; Limited Distribution bis 20 Geraete. Beleg: https://developer.android.com/developer-verification. Die Zukunftsform ist veraltet (Phase 1 laeuft seit 30.09.2026). ADB-Aussagen nur aus der FAQ, nicht erneut geprueft. Konsequenz: fuer Deutschland 2026 keine Aenderung; 2027: adb install oder Limited-Distribution-Konto.
- **TH** `[bestaetigt]` Task-Hijacking-Schutz (Activity Sandwiching) ist Opt-in ab targetSdk 37 (`android:allowCrossUidActivitySwitchFromBelow=false`). Die Doku schreibt "Android 15 introduces", meint aber targetSdk 37 mit Opt-in. Beleg: BAL-Doku, Abschnitt "Task Hijacking Prevention". Konsequenz: Attribut nicht setzen; bei targetSdk 37 pruefen, ob Regel 2 den Start aus dem Service betrifft.
- **SM** `[bestaetigt]` `StrictMode.detectBlockedBackgroundActivityLaunch()` ab Android 16 loggt blockierte oder gefaehrdete BALs. Beleg: BAL-Doku, Abschnitt "Strict Mode". Konsequenz: in Debug-Builds aktivieren.
- **A17** `[umstritten]` Android 17 ist stabil seit 16.06.2026; Pixel 9 bekommt es. Nur Skeptiker 2 per Android Authority; nicht selbst geprueft. Android 17 wird in der BAL-Doku und im AOSP-Tag android-17.0.0_r1 bereits als vorhanden behandelt. Konsequenz: Test auf Android 16 und 17 (Emulator nur API 37).
- **E1** `[unverifiziert]` Empfehlung AccessibilityService + eigene Activity ohne SAW/UsageStats/FGS ist eine Bewertung, keine Primaerquelle. Tragende Teile A1-A3 belegt, Geraeteverhalten (PiP, Split-Screen, Flackern) nicht. Konsequenz: Hypothese, mit Emulator-Prototyp verifizieren (M0-S2).
- **Diverse** `[unverifiziert]` A4, B1-B4, C1, C2, D1-D6, F1, F2, F4, G1, G2, H2, R1 (AOSP-/Doku-Details): beide Skeptiker nicht pruefbar bzw. nicht neu geprueft, kein Widerspruch. Ob `canRetrieveWindowContent` fuer `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` zwingend ist, ist unverifiziert. R1 (Risiko Re-Launch-Schleifen/Flackern) ist Synthese. Konsequenz: Arbeitsannahmen; Emulator-/Pixel-Test vor "fest" in decisions.md.

### Offen (Blocking)

- Pixel-9-Androidversion (16 QPR oder 17) und Emulator-Image: nur am Geraet/AVD-Manager klaerbar.
- Ob per adb installierte APKs ECM-guarded sind (Restricted-Dialog beim Aktivieren): Emulator-Test.
- Verhalten von `TYPE_ACCESSIBILITY_OVERLAY` (Z-Order, PiP, Compose-Lifecycle, WebView, Back); Status von `createAccessibilityOverlayAppOp` auf Pixel.
- Zuverlaessigkeit von TYPE_WINDOW_STATE_CHANGED bei Recents, Split-Screen, PiP, Freeform: Emulatortest.
- PiP der gesperrten App schliessen oder verhindern.
- Pixel-Akkuverwaltung und Low-Memory-Killer gegen aktiven Accessibility-Service: nur am echten Geraet.
- `isNonIdleWhitelisted`, Akku-Optimierungsausnahme, 24-h-Warnbenachrichtigung auf Pixel, Play-Protect bei Sideload.
- Timer bei Deep Sleep (uptimeMillis), ob Exact Alarm noetig ist.
- Einstellungen-Sperre gegen Force-Stop, DeviceAdmin unter ECM, Freigabe pro App oder global.
- Nicht geprueft: Android-16-QPR/17-QPR1-Aenderungen zu BAL/Accessibility.
- Primaerquelle (Release Notes) zum Advanced-Protection-Rollout und genaue Android-17-Minor-Version fehlt.

### Empfehlungen (Blocking)

1. AccessibilityService + eigene Sperr-Activity (startActivity direkt aus dem Service, `FLAG_ACTIVITY_NEW_TASK`, kein PendingIntent); als Hypothese in decisions.md.
2. Zuerst Emulator-Prototyp: kein "Background activity launch blocked!" in logcat (Tag ActivityTaskManager), StrictMode-BAL-Erkennung im Debug-Build.
3. targetSdk 36; Test auf Android 16 und 17; `allowCrossUidActivitySwitchFromBelow` nicht setzen.
4. Sperr-Activity: BackHandler (Zurueck -> HOME), Edge-to-Edge/Insets, `screenOrientation=portrait`.
5. Onboarding: "Eingeschraenkte Einstellungen zulassen", Hinweis auf 24-h-Systemwarnung, Advanced-Protection-Erkennung/-Hinweis, nie `isAccessibilityTool=true`.
6. Keine SAW-/UsageStats-/FGS-/Boot-Receiver-Abhaengigkeit; Freigabe-Zeitstempel persistent, bei Events und SCREEN_ON gegen die Uhr pruefen.
7. Event-Handler gegen Schleifen und Flackern entprellen und crashsicher bauen.

---

## 3. Build-Toolchain und Versionen

- **T01** `[bestaetigt]` Gradle 9.8.2 ist aktuell stabil; sha256 `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`. Beleg: services.gradle.org/versions/current (selbst abgerufen), beide Skeptiker. Gradle 9.9.0-rc-1 existiert bereits (Skeptiker 2), stabil bleibt 9.8.2.
- **T02** `[bestaetigt]` AGP 9.5.0 stabil, 9.6 nur Alpha. Beleg: Google-Maven-Metadaten (latest 9.6.0-alpha02), beide Skeptiker. Mindestanforderungen (Gradle 9.6.0, JDK 17, API 37) nur aus Release Notes.
- **T03** `[unverifiziert]` AGP 9.5 + Gradle 9.8.2 vertraegt sich. Gradle-Doku testet nur bis AGP 9.5.0-alpha02; kein Probe-Build. Konsequenz: Fallback Gradle 9.7.1.
- **T04** `[bestaetigt]` Kotlin 2.4.21 stabil, 2.5.0 nur Beta. Beleg: Maven Central, beide Skeptiker.
- **T05** `[unverifiziert]` Built-in Kotlin in AGP 9; kein `org.jetbrains.kotlin.android`, kein kapt, KSP statt kapt. Ursprungsbeleg developer.android.com/build/migrate-to-built-in-kotlin, nicht erneut abgerufen. Konsequenz: als Arbeitsregel nutzen, Build zeigt es sofort.
- **T06** `[bestaetigt]` AGP 9.5.0 hat Runtime-Abhaengigkeit auf KGP 2.2.10 (POM, ein Skeptiker).
- **T07** `[umstritten]` `compose-compiler-gradle-plugin` 2.4.21 hebt KGP nicht belegt auf 2.4.21: im POM steht `kotlin-gradle-plugin-api` 2.4.21 (compile) und ein BOM-Import, aber kein direktes `kotlin-gradle-plugin`. Konsequenz: buildscript-classpath `kotlin-gradle-plugin:2.4.21` explizit setzen und mit `./gradlew buildEnvironment` pruefen.
- **T08** `[unverifiziert]` JetBrains-Matrix fuer KGP 2.4.20-2.4.21 reicht nur bis Gradle 9.7.0 / AGP 9.3.1; AGP 9.5 + Gradle 9.8.2 darueber; Google nutzt AGP 9.5.0 + Kotlin 2.4.10.
- **T09** `[bestaetigt]` Compose BOM 2026.09.00 aktuell (ui 1.12.1, material3 1.4.0). Beleg: beide Skeptiker.
- **T10** `[bestaetigt]` Compose 1.12 verlangt compileSdk 37 und AGP >= 9.1.0 (aar-metadata ui-android-1.12.1: minCompileSdk=37, minAGP 9.1.0).
- **T11** `[bestaetigt]` Plattformen android-37.0/37.1/37.2, build-tools 37.0.0, 36.1.0, 36.0.0 im Repository (repository2-3.xml). System-Image-Pfad dort nicht geprueft; lokal ist `system-images;android-37.0;google_apis;x86_64` installiert (ls am 2026-11-02). 37.1/37.2 haben laut Preflight nur ps16k-Images; "37.0 stabil" aus fehlendem Beta-Suffix geschlossen (unverifiziert).
- **T12** `[unverifiziert]` targetSdk 37 bringt BAL-Verschaerfung u. a. Konsequenz: targetSdk 36.
- **T13** `[unverifiziert]` minSdk 34 sinnvoll; Pixel 9 mit Android 14 ausgeliefert (Designentscheidung; Pixel-9-Fakt nur aus Suchzusammenfassung). Der Plan waehlt minSdk 35 (siehe decisions.md).
- **T14** `[unverifiziert]` AGP-9-DSL `compileSdk { version = release(N) }` (Beleg developer.android.com/build/gradle-build-overview, nicht geprueft). Klaeren in M0-S0.
- **T15** `[unverifiziert]` JDK 17 reicht fuer AGP 9.5 CLI; JDK 25 kann Gradle 9.8 offiziell. Lokal bestaetigt: `/usr/lib/jvm` enthaelt java-17, java-21, java-25.
- **T16** `[umstritten]` Robolectric 4.17 aktuell (Maven Central bestaetigt); "SDK 36/37 braucht Java 21" nicht geprueft. Robolectric ist nicht im Plan.
- **T17** `[bestaetigt]` Nur JRE 21 (kein javac) installiert; `jdk21-openjdk` wuerde ersetzen; Studio 2026.2.2 mit JBR 25.0.4. Beleg: lokal.
- **T18** `[unverifiziert]` Studio nutzt Gradle-JDK aus Settings; Daemon-JVM-Criteria haben Vorrang vor JAVA_HOME.
- **T19** `[unverifiziert]` Android Studio Rabbit 2 (2026.2.2) unterstuetzt AGP bis 9.5.
- **T20** `[bestaetigt]` KSP 2.3.13 neueste stabile Version; Kompatibilitaet mit Kotlin 2.4.21 + built-in Kotlin ungetestet. (Im Plan ohne KSP.)
- **T21** `[bestaetigt]` Stabile Bibliotheksversionen: core-ktx 1.19.1, activity-compose 1.13.0, lifecycle 2.11.0 (2.12 nur Alpha), nav 2.10.2, datastore 1.3.0, room 2.8.5, webkit 1.18.0, coroutines 1.11.0, junit 4.13.2, robolectric 4.17, turbine 1.2.1, truth 1.4.5; activity-compose latest 1.14.0-alpha03. Nicht geprueft: androidx.test.*, espresso, uiautomator.
- **T22** `[umstritten]` Version Catalog (Zusammenstellung aus T01-T21): gepruefte Versionen stimmen; Test-Libs und Plugin-Marker teils ungeprueft; keine gebaute Kombination.
- **T23** `[unverifiziert]` Build-Geruest AGP 9.5 mit buildscript+plugins-alias: ungetestet, Probe-Build noetig.
- **T24/T25** `[unverifiziert]` JetBrains Mono v2.304, OFL 1.1; lokale Fonts in `res/font` (Kleinschreibung), `FontFamily(Font(...))`.
- **T26** `[unverifiziert]` AGP-9-Property-Defaults (Unit-Tests nur fuer getesteten Build-Typ, targetSdk-Fallback, R8-Strict-Keep).
- **T27** `[unverifiziert]` lint-api 32.5.0 passend zu AGP 9.5.0.
- **Z1** `[bestaetigt]` Standard-java ist JDK 25 (25.0.5, Studio-JBR 25.0.4); JAVA_HOME muss im Workflow explizit gesetzt werden. Beleg: lokal.

### Offen (Toolchain)

- Hebt der Compose-Plugin-Eintrag allein KGP auf 2.4.21? -> `buildEnvironment`.
- Funktioniert `buildscript{}` + `plugins{}`-Alias zusammen? Ungetestet.
- Warnungen/Fehler bei AGP 9.5 + KGP 2.4.21 + Gradle 9.8.2 ausserhalb der JetBrains-Matrix: kein Probe-Build.
- Akzeptiert Gradle JRE 21 ohne javac? Wahrscheinlich nein fuer Compile-Tasks.
- Laeuft der Build mit JBR 25 oder System-JDK 25? Unbelegt (Plan: JDK 17).
- Plattformname bei `compileSdk release(37)` (`platforms;android-37.0`): laedt AGP es automatisch? Ungeprueft.
- BAL-Ausnahme fuer AccessibilityService unter Android 17: im Emulator API 37 testen.
- Android 17 offizielles Release-Datum und stabile Systemimages: nicht belegt.
- KSP 2.3.13 mit Kotlin 2.4.21 (im Plan nicht benoetigt); Daemon-JVM-Criteria ohne foojay; Lint-Strategie.

### Empfehlungen (Toolchain)

1. Gradle 9.8.2 mit `distributionSha256Sum` pinnen; Fallback 9.7.1.
2. AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37; stabile Versionen explizit pinnen, nie `latest`.
3. Zuerst minimales Geruest bauen; `./gradlew buildEnvironment | grep kotlin-gradle-plugin`; bis dahin buildscript-classpath `kotlin-gradle-plugin:2.4.21`. Kein `org.jetbrains.kotlin.android`, kein kapt.
4. Per sdkmanager: platforms;android-37.0, build-tools, platform-tools, emulator, system-image (lokal bereits vorhanden).
5. JAVA_HOME explizit setzen; JDK 17 fuer Builds (lokal vorhanden). JDK 21 nur bei Robolectric (nicht im Plan).
6. Kein `java.toolchain`-Block, keine `gradle-daemon-jvm.properties`; Studio-Gradle-JDK manuell setzen.
7. Version Catalog mit nur benoetigten Libraries (DataStore statt Room).
8. Ungeprueft uebernommene Punkte (DSL `release(N)`, Mono-Font, AGP-Defaults, Android-17-Aenderungen) als Annahmen markieren.

---

## 4. Persistenz und Hintergrundzuverlaessigkeit

- **P01** `[bestaetigt]` DataStore fuer kleine Datensaetze, ohne partielle Updates/referenzielle Integritaet; fuer grosse/komplexe Daten Room. Beleg: developer.android.com/topic/libraries/architecture/datastore (Note-Box), beide Skeptiker.
- **P02** `[bestaetigt]` Pro Datei nur eine DataStore-Instanz pro Prozess (sonst `IllegalStateException`), T unveraenderlich, kein Mischen von Single-/MultiProcess. Konsequenz: einmal im AppContainer anlegen.
- **P03** `[bestaetigt]` Ohne `android:process` laufen alle Komponenten im Standardprozess (service-element-Doku). Bibliotheks-Manifeste koennten Prozesse einfuehren: nach Manifest-Merge pruefen.
- **P04** `[bestaetigt]` DataStore 1.3.0 (21.10.2026) stabil; core/preferences-core ohne Android-Abhaengigkeit; JSON (kotlinx-serialization) und Protobuf moeglich. Doku nennt Serialization-Plugin 2.2.20, json 1.9.0: Plugin-Version muss zur Kotlin-Version passen.
- **P05** `[bestaetigt]` `updateData` ist atomares Read-Modify-Write; 1.3.0-alpha03 behob Race zwischen neuen data-Collectoren und updateData (b/431787506). Konsequenz: Zaehler/Freigabe nur ueber updateData, mind. 1.3.0.
- **P06** `[bestaetigt]` DataStore-Dateien (`files/datastore/`) sind standardmaessig in Auto Backup/D2D; per `data_extraction_rules.xml` ausschliessbar (ab API 31). Dass JSON-Delegate-Dateien ebenfalls dort liegen ist plausibel, nicht woertlich belegt. `<include>` in cloud-backup schraenkt auf genannte Pfade ein. Konsequenz: nur `<exclude>` verwenden (der Plan schaltet zusaetzlich `allowBackup=false`).
- **P07** `[bestaetigt]` Room 3.0.3 (`androidx.room3`) stabil: KSP Pflicht, nur Kotlin-Codegen, SQLiteDriver Pflicht. Room 2.8.5 weiter gepflegt. 3.1.0-alpha03 verlangt Kotlin 2.2. Kotlin-Mindestversion fuer 3.0.x nicht belegt. (Im Plan kein Room.)
- **P08** `[bestaetigt]` Room-Tests mit In-Memory-DB und BundledSQLiteDriver; Robolectric nicht empfohlen. JVM-Weg nur fuer Room KMP beschrieben.
- **P09** `[bestaetigt]` Bei einem Prozess RoomDatabase als Singleton.
- **P10-P12** `[bestaetigt]` Wall Clock ist manipulierbar; `elapsedRealtime` ist monoton, inkl. Deep Sleep, seit Boot; `uptimeMillis`/Handler/`Thread.sleep` stehen im Deep Sleep still. Beleg: developer.android.com/reference/android/os/SystemClock. Konsequenz: Freigabe als `untilElapsed`, Pruefung bei jedem Event/Bildschirm-an; Timer nur Optimierung.
- **P13** `[bestaetigt]` `Settings.Global.BOOT_COUNT` (API 24) lesbar (@Readable). Beleg: Reference, AOSP Settings.java Z. 17768-17769. Verhalten bei Soft-/Userspace-Reboot nicht geprueft. Konsequenz: UnlockGrant mit bootCount; nach Neustart konservativ abgelaufen.
- **P14** `[bestaetigt]` `currentNetworkTimeClock` (API 33) nicht user-verstellbar, aber nicht sicherheitstauglich, kann `DateTimeException` werfen. Nicht in die Kernlogik.
- **P15** `[bestaetigt]` BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED sind von der Implicit-Broadcast-Beschraenkung ausgenommen; RECEIVE_BOOT_COMPLETED noetig. Konsequenz: keine Receiver noetig, weglassen.
- **P16** `[bestaetigt]` `LocalDate`/`toEpochDay` ab API 26, `LocalDate.ofInstant` erst API 34; Alternative `instant.atZone(zone).toLocalDate()`. Konsequenz: localEpochDay beim Einfuegen speichern.
- **P17** `[bestaetigt]` Lebenszyklus des AccessibilityService vom System gesteuert; Start nur durch User (praktisch auch per `settings put`, Ende auch per Force-Stop). Konsequenz: Zustand in onServiceConnected aus Persistenz neu aufbauen.
- **P18** `[bestaetigt]` AccessibilityManagerService bindet aktivierte Dienste selbst; nicht-directBootAware erst nach Entsperrung; Dienste in crashedServices/bindingServices werden uebersprungen. Beleg: AOSP AccessibilityManagerService.java Z. 2969-3015 (main 2026-11-02). Konsequenz: kein Boot-Receiver; nicht directBootAware; Exceptions abfangen (Crash-Loop-Risiko).
- **P19** `[bestaetigt]` Bindung mit `BIND_AUTO_CREATE|BIND_FOREGROUND_SERVICE_WHILE_AWAKE|BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS|BIND_INCLUDE_CAPABILITIES`; bei Tod wird der Dienst als crashed gefuehrt, bei Neuverbindung zurueckgesetzt. Beleg: AccessibilityServiceConnection.java Z. 157-173, 449-466; AccessibilityUserState Z. 301-304. LMK-Wahrscheinlichkeit und Pixel-Akkuoptimierung nicht belegt. Android 17 MemoryLimiter (`REASON_OTHER "MemoryLimiter:AnonSwap"`, `adb shell am memory-limiter`) ist weiterer Kill-Pfad (behavior-changes-all).
- **P20** `[bestaetigt]` Force-Stop entfernt den Dienst dauerhaft aus ENABLED_ACCESSIBILITY_SERVICES (auch via `am start -S`). Beleg: AOSP AccessibilityManagerService.java Z. 949-985, 6629-6660. Konsequenz: groesste Umgehungsluecke; Banner; E2E-Skripte nie `am start -S`/force-stop ohne Re-Enable.
- **P21** `[bestaetigt]` Paket-Update bindet den Dienst neu, deaktiviert ihn nicht (AOSP onPackageUpdateFinished Z. 6550-6590; nicht auf Geraet getestet). Nach `adb install -r` per logcat pruefen.
- **P22** `[bestaetigt]` `getEnabledAccessibilityServiceList` liefert nur gebundene Dienste (leer bei UiAutomation-Unterdrueckung); ENABLED_ACCESSIBILITY_SERVICES ist lesbar (AOSP AMS Z. 1486-1524, Settings.java Z. 8807-8808). Konsequenz: dreistufige Statusanzeige.
- **P23** `[bestaetigt]` Accessibility-State-Listener (API 33 / 14 / 26) existieren; der ServicesStateChangeListener feuert laut AOSP auch bei Bind/Unbind (AccessibilityUserState Z. 259-290, AMS Z. 795-800, 3104-3118). Nicht auf Geraet getestet. Konsequenz: Listener + Recheck in onResume.
- **P24** `[bestaetigt]` UiAutomation unterdrueckt Accessibility-Dienste, ausser mit `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` (UiAutomation-Reference; AOSP AMS Z. 2994-3019). Konsequenz: instrumentierte E2E-Tests holen `getUiAutomation(FLAG_DONT_SUPPRESS...)` als Erstes.
- **P25** `[bestaetigt]` `adb shell uiautomator dump` verbindet mit flags=0 und unterdrueckt damit den Dienst (AOSP DumpCommand.java Z. 78, UiAutomationShellWrapper.java Z. 25-36). Quelltext-Kette lueckenlos, nicht auf Emulator beobachtet. Konsequenz: nicht im Sperrmoment, danach auf onServiceConnected warten; einmal per logcat verifizieren.
- **P26** `[bestaetigt]` ContentObserver auf ENABLED_ACCESSIBILITY_SERVICES; `settings put` loest Bind/Unbind aus (AOSP AMS Z. 5744-5760, 5833, 5900-5906). Wirkung von Restricted Settings auf den Shell-Weg ungeklaert.
- **P27/P28** `[bestaetigt]` targetSdk 35+ erzwingt Edge-to-Edge; Opt-out bei targetSdk 36 auf Android 16 deaktiviert; Predictive Back standardmaessig an, onBackPressed/KEYCODE_BACK entfallen. Beleg: about/versions/16/behavior-changes-16; compose/system/setup-e2e. Android 17 aendert hier nichts. Konsequenz: `enableEdgeToEdge()` vor setContent, `BackHandler`.
- **P29/P30** `[bestaetigt]` `isSystemInDarkTheme` liest `LocalConfiguration.uiMode`; `ComposeView` ohne Activity braucht ViewTreeLifecycleOwner und SavedStateRegistryOwner, sonst `IllegalStateException` (androidx-main DarkTheme.android.kt Z. 44-49, WindowRecomposer, ComposeViewContext). Konsequenz: Sperre als ComponentActivity statt Overlay.
- **P31/P32** `[bestaetigt]` ViewModel ueberlebt keinen Prozesstod; Saved State ueberlebt keinen User-Dismiss; SavedStateHandle schreibt in Compose erst bei Stop. Konsequenz: Quiz-Fortschritt sofort per DataStore.updateData.
- **P33/P34** `[bestaetigt]` Manuelle DI mit AppContainer dokumentiert (Doku empfiehlt Hilt "when possible"); DataStore im Repository, ViewModel `stateIn(WhileSubscribed(5000))`, `collectAsStateWithLifecycle`. Eignung fuer unsere Groesse ist Einschaetzung.
- **P35/P36** `[bestaetigt]` `runTest` ueberspringt delay, TestDispatcher teilen Scheduler, `Dispatchers.setMain`; Testpyramide. Konsequenz: UnlockManager/Quiz/Streak als reines Kotlin mit injiziertem TimeSource per JVM-Test.
- **P37** `[bestaetigt]` DataStore 1.3.0 `DataStore.Builder` verlangt CoroutineContext mit Job, application-scoped; sonst brechen interne Operationen ab. Konsequenz: im AppContainer `SupervisorJob()+Dispatchers.IO`.
- **N1** `[unverifiziert]` Android 17 Advanced Protection kann Accessibility-Dienste ohne `isAccessibilityTool=true` einschraenken: nur Presse (thehackernews.com 2026/03, Android Authority), keine offizielle Doku.
- **N2** `[unverifiziert]` Restricted Settings (Android 13+) / Enhanced Confirmation (15) blockieren Aktivierung sideloaded Accessibility-Dienste in der UI; Wirkung auf `adb install` und `settings put` offiziell undokumentiert (xda, androidauthority). Konsequenz: Onboarding-Hinweis, Test am Pixel 9 (und Emulator, M0-S3).
- **N3** `[unverifiziert]` `WRITE_SECURE_SETTINGS` mit protectionLevel `development` per `adb pm grant` vergebbar; App koennte den Dienst nach Force-Stop selbst wieder eintragen (AOSP core/res/AndroidManifest.xml Z. 5153-5154, nur Skeptiker 1). Optionale Gegenmassnahme zu P20, nicht im MVP.
- **N4** `[unverifiziert]` kotlinx-coroutines `delay` auf Dispatchers.Main nutzt auf Android vermutlich `Handler.postDelayed` (uptimeMillis), nur aus dem Gedaechtnis.

### Offen (Persistenz/Lebenszyklus)

- Pixel 9: Adaptive Battery/App-Standby-Buckets gegen den gebundenen Dienst (nur echtes Geraet, test-checklist.md).
- Restart-Backoff/"Funktioniert nicht"-Anzeige bei wiederholten Dienst-Abstuerzen.
- Deaktiviert "Daten loeschen" (`pm clear`) den Dienst wie Force-Stop?
- Wird der Dienst nach `uiautomator dump` sofort neu gebunden und bleibt der Prozess am Leben?
- BundledSQLiteDriver in JVM-Unit-Tests eines Android-App-Moduls (Host-Natives)? (Room nicht im Plan.)
- Kotlin-/KSP-Mindestversion fuer Room 3.0.3.
- Android 17 / targetSdk 37: BAL-Verschaerfung fuer den Start der Sperr-Activity.
- Emulator-Uhr manipulieren (auto_time, adb root, date): ungeprueft; Unit-Tests mit injizierter Uhr genuegen.
- Bootcount bei Userspace-/Soft-Reboot.
- Produktentscheidungen offen: Quiz-Fortschritt nach Wischen aus Recents, Freigabe pro App/global, WRITE_SECURE_SETTINGS-Selbstheilung, minSdk (siehe `todo.md` "Offene Entscheidungen").

### Empfehlungen (Persistenz)

1. Manueller AppContainer; ein Prozess; DataStore (JSON, kotlinx-serialization, 1.3.0, SupervisorJob+IO) fuer Einstellungen, UnlockGrant, Quiz-Sitzung; Room 3.0.3 nur falls die Architektur es verlangt (Plan: nein).
2. Freigabemodell `UnlockGrant(scope, untilElapsed, bootCount, grantedAtWall)`: gueltig nur bei gleichem BOOT_COUNT und `elapsedRealtime() < untilElapsed`; Pruefung bei Event und Bildschirm-an; Wall Clock nur fuer Statistik.
3. Fluechtigen Zustand in eigene DataStore-Datei und per `data_extraction_rules.xml` vom Backup ausnehmen.
4. Zustandsaenderungen nur ueber `updateData`; Quiz-Fortschritt sofort persistieren.
5. Ereignisse mit localEpochDay beim Einfuegen speichern; `ofInstant` (API 34) meiden bei minSdk < 34.
6. Dienst: nicht directBootAware, kein `android:process`, kein Boot-Receiver; Zustand in onServiceConnected laden; Exceptions abfangen; Scope in onUnbind/onDestroy canceln.
7. Statusanzeige dreistufig plus ServicesStateChangeListener und Recheck in onResume; Fall Force-Stop/Advanced Protection warnen.
8. Sperre als ComponentActivity mit `enableEdgeToEdge` und `BackHandler`, nicht als Compose-Overlay.
9. Tests: TimeSource injizieren; JVM-Tests fuer Logik; DAO/Instrumentierte Tests; E2E nur mit `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`; `uiautomator dump` und `am start -S`/force-stop in gotchas.md.
10. Pixel-spezifische Punkte (Akku, Restricted Settings, MemoryLimiter, Advanced Protection) in test-checklist.md.

---

## 5. Testen im Emulator (AnkiDroid, Berechtigungen, Revlog)

- **E-1** `[bestaetigt]` AnkiDroid v2.25.3 (2026-10-17) hat Asset `variant-abi-AnkiDroid-2.25.3-x86_64.apk` (43797921 B, sha256 `154101f0...edac7`); Universal-APK 111759228 B. Prereleases verschieben "latest" nicht. Beleg: https://api.github.com/repos/ankidroid/Anki-Android/releases/latest. Konsequenz: Tag v2.25.3 pinnen, sha256 aus Release-API pruefen.
- **E-2** `[bestaetigt]` Offizieller Signatur-Fingerprint SHA-256 `2071534f...b21a45` steht im README (Z. 57-60, main). apksigner-Lauf nicht ausgefuehrt. Konsequenz: `apksigner verify --print-certs` gegen den README-Wert.
- **E-3** `[bestaetigt]` Permission `${applicationId}.permission.READ_WRITE_DATABASE` ist dangerous; Provider-Authority `${applicationId}.flashcards`, exported; Release prueft `checkCallingPermission`, Debug `checkCallingOrSelfPermission`. Zusaetzlich `knownRogueClient`-Check (Z. 1496): Aufrufer muss die Permission per uses-permission im eigenen Manifest deklarieren. Beleg: Manifest Z. 45 und 633, CardContentProvider.kt Z. 1489-1496 (selbst per curl geprueft). `pm grant` nicht ausgefuehrt. Konsequenz: AnkiDroid VOR unserer App installieren, dann `pm grant` oder `install -g`.
- **E-4** `[bestaetigt]` Im Provider gibt es keinen "API aktiviert"-Schalter; nur Permission- und Rogue-Check (grep ohne "enabled"-Treffer, zwei Pruefer). Ob eine UI-Einstellung existiert, nicht geprueft (siehe A7: laut Quelle `providerEnabled`-Preference vorhanden). Konsequenz: gotchas.md-Eintrag "API muss aktiviert werden" ist widerspruechlich/unbelegt; im Emulator pruefen.
- **E-5** `[bestaetigt]` Schedule-Endpunkt: query liefert Karte, update mit note_id, ord, answer_ease 1-4, optional time_taken; kein insert. Provider ruft `col.sched.answerCard(card, Rating.forNumber(ease-1))` und setzt `timerStarted = now - timeTaken` (CardContentProvider.kt Z. 778, 1116, 1301-1303).
- **E-6** `[unverifiziert]` Der Revlog-Eintrag entsteht beim Beantworten (Rust-Backend): nicht im Provider-Code gezeigt. Emulator-Test: Revlog vor/nach vergleichen.
- **E-7** `[bestaetigt]` `AddContentApi` (addNewDeck(deckName), addNewBasicModel(name), addNote, addNotes, getAnkiDroidPackageName) existiert wie beschrieben; Parametername `deckName` (AddContentApi.kt Z. 49, 102, 331, 495, 864). Konsequenz: Debug-only Testdeck-Anlage moeglich; AddContentApi nicht kopieren.
- **E-8** `[bestaetigt]` Lokal vorhanden: `~/Android/Sdk` mit `emulator/emulator` (nicht im PATH), cmdline-tools und `system-images/android-37.0/google_apis/x86_64`. "Emulator nicht vorhanden" war nur ein PATH-Problem. Konsequenz: Pfade aus `~/Android/Sdk` ableiten. Zusatz (log.md): `adb root` funktioniert mit dem lokalen google_apis-Image (uid=0, 2026-11-02 getestet).
- **E-9** `[unverifiziert]` Ob google_apis-Image `adb root` erlaubt war offen; inzwischen durch Boot-Test in log.md bestaetigt (Android 17 / API 37, userdebug, uid=0). Das AnkiDroid-Paket ist nicht debuggable: `run-as` nicht moeglich, Revlog nur ueber `adb root` + Dateizugriff.
- **E-10** `[unverifiziert]` Collection-Pfad, Dateiname und WAL-Nutzung in AnkiDroid 2.25.3 (Forum: `Android/data/com.ichi2.anki/files/AnkiDroid`). Per `find` im Emulator ermitteln; -wal/-shm mitziehen; Host-Python mit `sqlite3` `mode=ro`.
- **E-11** `[unverifiziert]` appops-Befehle (SYSTEM_ALERT_WINDOW, GET_USAGE_STATS), `settings put secure enabled_accessibility_services`, ACCESS_RESTRICTED_SETTINGS-Workaround, uiautomator dump/input, `sys.boot_completed`, `cmd deviceidle`: nicht aus Primaerdoku belegt, gaengige Praxis. Konsequenz: im Emulator erproben, jeweils mit Verifikationsabfrage.
- **E-12** `[unverifiziert]` Emulator-Flags (`-no-window`, `-no-snapshot`, `-no-snapshot-save`, `-wipe-data`), Deprecation von emulator/avdmanager zugunsten Android CLI: beim Skriptbau mit `emulator -help` pruefen. (Der Boot-Test mit `-no-window -no-audio -no-snapshot-save -gpu swiftshader_indirect` lief am 2026-11-02 erfolgreich.)
- **E-13** `[unverifiziert]` F-Droid-Version/ABI und Signaturgleichheit mit GitHub-Fingerprint. Konsequenz: GitHub-Release als Primaerquelle.

### Offen (Emulator)

- Collection-Pfad/Dateiname/WAL in 2.25.3 und Dialoge der Ersteinrichtung (Storage, Notifications, Welcome).
- Schreibt die API-Antwort revlog mit korrektem ease/time? Gibt es in der UI einen "Enable AnkiDroid API"-Schalter?
- Funktionieren `pm grant` der AnkiDroid-Permission, `appops set`, Accessibility per `settings put` und ACCESS_RESTRICTED_SETTINGS bei `adb install` auf API 37?
- Wie wird Doze/Standby-Bucket im Emulator belastbar getestet?

### Empfehlungen (Emulator)

1. Setup-Skript: AnkiDroid v2.25.3 x86_64 per `gh release download` pinnen, sha256 und apksigner-Fingerprint pruefen, vor unserer App installieren.
2. Emulator aus `~/Android/Sdk`, PATH nicht voraussetzen; Boot per `sys.boot_completed` pollen.
3. Unsere App deklariert `com.ichi2.anki.permission.READ_WRITE_DATABASE` per uses-permission; Grant per `pm grant` oder `install -g`.
4. Testdeck per Debug-only AddContentApi (oder .apkg-Seeder); `time_taken` senden.
5. Revlog-Verifikation: `adb root` (funktioniert lokal), Pfad per `find`, db inkl. WAL read-only mit `python3 -I` lesen.
6. Alle unverifizierten Shell-Befehle mit Folgeabfrage absichern; bei Fehlschlag Testfehler melden.
7. gotchas.md: "API muss aktiviert werden" als widerspruechlich kennzeichnen.
