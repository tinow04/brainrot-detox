# Entscheidungen

Format: `## YYYY-MM-DD – Titel` / Entscheidung / Warum / Verworfene Alternativen

## 2026-11-02 – Eigene Abfrage-UI über AnkiDroid-API statt Weiterleitung zu Anki

**Entscheidung:** Die App zeigt die Karten selbst an und holt Karten sowie Scheduling über die AnkiDroid-API (Content Provider, `ReviewInfo`). Antworten gehen zurück an AnkiDroid.

**Warum:** Nur so lässt sich zuverlässig zählen, ob 5 Karten richtig waren. Die Review-Historie ist über die API nicht abrufbar (unverifiziert). Außerdem kann der User die Abfrage nicht einfach in AnkiDroid wegwischen.

**Verworfen:** Weiterleitung per Intent zu AnkiDroid und Erkennung über die gesunkene Zahl fälliger Karten. Das ist ungenau und leicht zu umgehen.

## 2026-11-02 – Design-Entscheidungen für Iteration 1

- **Stil:** Modernes Terminal (Monospace, Prompt-Stil, gedeckte Catppuccin-/Gruvbox-Farben). Erste Iteration, kann sich ändern.
- **Theme:** Folgt dem System (Hell und Dunkel gestalten).
- **Sperrbildschirm:** Kurzer Zwischenscreen (gesperrte App, 0/5, Start-Button), dann Karten.
- **Abfrage:** Aufdecken + selbst bewerten (Again/Hard/Good/Easy wie in Anki), weil die Karten des Users überwiegend so funktionieren.
- **Zählregel:** Nur Good/Easy zählen als richtig. Bei Again kommt die Karte später nochmal, der Zähler bleibt.
- **Sperrbildschirm-Elemente:** Fortschritt x/5, gesperrte App, Abbrechen/Zurück, Streak/Statistik.
- **Haupt-App:** App-Auswahl, Einstellungen, Statistik. Ein Berechtigungs-Setup wurde nicht gewählt, wird aber technisch gebraucht (Accessibility, Overlay, Akku) und muss später entschieden werden.
- Prompt dafür: `design-prompt.md`

## 2026-11-02 – Tests im Android-Studio-Emulator statt auf dem Pixel

**Entscheidung:** Entwickelt und getestet wird im Emulator (AVD Pixel 9, Google-APIs-Image ohne Play Store). Android Studio dient dem User zum Ansehen und Starten, der Workflow baut weiter per Gradle-Wrapper in der CLI.

**Warum:** Der User will die App nicht auf dem eigenen Handy testen. Der Emulator lässt sich außerdem automatisieren (`adb`, Screenshots, `uiautomator`), und mit dem Google-APIs-Image geht `adb root` für die Revlog-Prüfung. `/dev/kvm` ist vorhanden. Grenze: Verhalten der Akku-Optimierung des echten Pixel und der echte AnkiWeb-Sync sind im Emulator nicht prüfbar (siehe `test-checklist.md`, später).

**Ersetzt:** Frühere Aussage, dass Testen auf dem echten Gerät sinnvoller sei.

## 2026-11-02 – Verteilung per APK, kein Play Store

**Warum:** Persönliches Tool, `AccessibilityService` und Overlay-Berechtigung sind so deutlich unkomplizierter.

---

Ergaenzungen aus der Planungs-Synthese (2026-11-02). Vollstaendiger Plan: `plan.json`, Bauplan: `architecture.md`, Belege: `research.md`. Als "vom User zu bestaetigen" markierte Punkte stehen auch in `todo.md` unter "Offene Entscheidungen".

## 2026-11-02 - Deck-Auswahl: einzelnes Deck inkl. Unterdecks oder alle Decks (Standard, vom User am 2026-11-02 bestaetigt)

**Entscheidung:** Genau EINE Auswahl: entweder ein einzelnes Deck inklusive Unterdecks oder `alle decks` (Standard). `alle decks` = alle nicht-dynamischen Top-Level-Decks; Zaehler = Summe der Top-Level-Decks (keine Doppelzaehlung), `schedule` wird je Top-Level-Deck mit faelligen Karten in absteigender Summe abgefragt. Keine deckID-lose Abfrage.

**Warum:** Der User wollte die Wahl zwischen einem Deck und allen Decks. Die Top-Level-Zaehler enthalten Unterdecks bereits (belegt, `research.md` B1/B2), daher keine Doppelzaehlung. Die Deck-Auswahl wird beim Anlegen der Session eingefroren.

**Verworfen:** Mehrfachauswahl einzelner Decks (mehr UI, mehr Randfaelle).

## 2026-11-02 - Sperr-UI: AccessibilityService + eigene LockActivity statt Overlay (weicht von CLAUDE.md/Aufgabenliste ab)

**Entscheidung:** Erkennung nur per `AccessibilityService` (`typeWindowStateChanged`, `canRetrieveWindowContent=false`, nie `isAccessibilityTool`), Sperre als eigene `LockActivity` (singleTask, eigener Task, excludeFromRecents, portrait), per `startActivity` direkt aus dem Dienst. Kein `SYSTEM_ALERT_WINDOW`, kein `UsageStatsManager`, kein Foreground-Service, kein Boot-Receiver, kein POST_NOTIFICATIONS. Das ersetzt "Overlay" aus `CLAUDE.md` und die Setup-Ankuendigung (Accessibility, Overlay, Akku) in der Design-Entscheidung vom selben Tag.

**Warum:** Der Dienst wird mit `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` gebunden (AOSP, `research.md` A1/A2); eine Activity ist Compose-/Lifecycle-seitig robuster als ein Overlay (P29/P30); weniger Berechtigungen und weniger Setup-Schritte. Stuetzt sich auf AOSP-Code, nicht auf Doku, daher Hypothese bis zum Emulator-Test (Spike M0-S2: 20/20 ohne "Background activity launch blocked!", Median <= 500 ms).

**Verworfen/Fallback-Leiter (nur ueber Plan-Korrektur):** `TYPE_ACCESSIBILITY_OVERLAY`, dann `SYSTEM_ALERT_WINDOW`, dann nur `GLOBAL_ACTION_HOME`.

## 2026-11-02 - Fail-closed als Leitprinzip; Notausgang und NothingDue-Grant als einzige Ausnahmen (vom User zu bestaetigen)

**Entscheidung:** Bei jedem AnkiDroid-Fehler und jedem unerwarteten Zustand bleibt die App gesperrt. Ausnahmen: (a) NothingDue-Grant: ist wirklich nichts faellig (Queue-Matrix Zeile 4), wird die App ohne Quiz freigegeben (zaehlt `freeUnlocks`, auch fuer die Streak); (b) Notausgang auf Fehlerscreens nach 60 s: 5 min Freigabe, max. 1x je 6 h (Konstante `EMERGENCY_EXIT_ENABLED`). Null-Cursor ist nie "nichts faellig".

**Warum:** Ohne Ausnahmen sperrt ein defektes Anki oder eine Giftkarte dauerhaft aus; ein erzwungenes Quiz ohne Karten senkt die Versuchung, den Dienst abzuschalten. Gegenseite: Streak steigt ohne Lernleistung. Offen in `todo.md`.

## 2026-11-02 - Keine Gradle-Abhaengigkeit auf das AnkiDroid-API-Modul

**Entscheidung:** Eigene Kotlin-Datei `AnkiContract` mit Authority `com.ichi2.anki.flashcards`, Spalten, Ease 1..4. Nur noetige Konstanten aus `FlashCardsContract.kt` (v2.25.3, frei kopierbar, Kopfzeile vor Uebernahme lesen), `Ease.kt` (LGPL) und `AddContentApi.kt` werden nicht kopiert.

**Warum:** Modul nur lokal/JitPack, Tag-Builds schlagen fehl, Wiki-Koordinate veraltet (`research.md` A2/A3). Zielversion AnkiDroid v2.25.3, Regression gegen 2.26-Alphas in M7.

## 2026-11-02 - Eigene Zaehler/Statistik, Antworten per Write-ahead-Pipeline

**Entscheidung:** Zaehlen und Statistik komplett in der App (Ease 3/4 = richtig). Antwort laeuft als Write-ahead: pending persistieren -> Provider-Antwort -> optional per `reps` verifizieren -> atomarer Commit (`updateData`) -> erst dann UI. Ohne lesbare `reps` (`answerVerify=false`) wird ein pending nach Neustart verworfen, nie doppelt gesendet.

**Warum:** Die API hat kein Revlog (unverifiziert, D4), `update()` meldet keinen Erfolg (C3). Ob Antworten im Revlog landen und syncen, misst Spike S1a; ein negatives Ergebnis aendert die Architektur nicht, muss aber dokumentiert werden.

## 2026-11-02 - Freigabe je App, nur ueber elapsedRealtime + BOOT_COUNT (vom User zu bestaetigen)

**Entscheidung:** `UnlockGrant` je Zielpaket, gueltig nur bei gleichem `BOOT_COUNT` und `elapsedRealtime < until`; `BOOT_COUNT` unlesbar = ungueltig; Wall-Clock nie fuer Entscheidungen; Pruefung bei jedem Event und bei `SCREEN_ON`/`USER_PRESENT`; keine Verlaengerung ohne neues Quiz.

**Warum:** Wall-Clock ist manipulierbar, Handler-Timer stehen im Deep Sleep still (`research.md` P10-P13).

## 2026-11-02 - Persistenz: DataStore (JSON) statt Room, manuelle DI

**Entscheidung:** Drei DataStore-Dateien (settings, runtime, stats) mit kotlinx-serialization, Spiegelkopie fuer settings, genau eine Instanz je Datei im `AppContainer` (`SupervisorJob()+Dispatchers.IO`). Kein Room, KSP, Hilt, Navigation-Bibliothek, Material3 oder Robolectric. Backup komplett aus (`allowBackup=false` + `data_extraction_rules.xml`, bewusst redundant).

**Warum:** Kleine Datenmenge, kein JOIN-Bedarf; weniger Toolchain-Risiko (KSP/Room 3 mit Kotlin 2.4.21 ungetestet). Backup aus, weil Freigabe/Sitzung geraetegebunden sind und beim Geraetewechsel neu eingerichtet wird. Doku empfiehlt Hilt "when possible"; fuer diese Groesse ist manuelle DI die Einschaetzung des Plans.

**Verworfen:** Room 3.0.3 (Fallback 2.8.5, falls Architektur es spaeter verlangt).

## 2026-11-02 - Versionen und SDK-Ziele

**Entscheidung:** Gradle 9.8.2 (mit Checksum), AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37, targetSdk 36, minSdk 35, JDK 17, applicationId `dev.tinow.brainrotstopper` (Tabelle in `architecture.md`). Fallbackleiter bei Build-Fehlern: Gradle 9.7.1 -> Kotlin 2.4.10 -> aeltere stabile BOM, jede Stufe per Maven-Metadata belegen. applicationId, minSdk 35 und lokale-Mitternacht-Tagesgrenze sind vom User zu bestaetigen.

**Warum:** targetSdk 36 und nicht 37, weil BAL-Verschaerfung und Task-Hijacking-Opt-in erst ab 37 gelten; Compose 1.12 erzwingt compileSdk 37; minSdk 35 deckt Android 15+ aus `CLAUDE.md`.

## 2026-11-02 - Statistik-Definition und Tagesgrenze

**Entscheidung:** blocks = neue Sperr-Episode (Entprellung 10 s), aborted <= blocks, unlocks, freeUnlocks, emergencies getrennt; Streak = Tage mit unlocks+freeUnlocks >= 1; Woche = 7 Tage inkl. heute; Tagesgrenze lokale Mitternacht (nicht Anki-Tageswechsel); "zeit gespart" = abgebrochene Sperren x 5 min, im UI als geschaetzt (`*`) markiert.

**Warum:** Eindeutige Zaehlregel ohne Doppelzaehlung; Anki-Tageswechsel ist ueber die API nicht lesbar. Folge: Lernen nach Mitternacht erscheint am neuen Tag.

## 2026-11-02 - Anti-Umgehung ist Reibung, keine Sicherheitsgrenze

**Entscheidung:** Verhindert werden Zurueck, Home/Recents/Deeplink, Freigabe-Ablauf, Uhr verstellen, Neustart, Prozesstod, Start-Spam (Throttle/Breaker, nie Freigabe), Doppeltippen. Nur erkannt und per Banner gemeldet (Restrisiko): Dienst abschalten, Force-Stop, App aus Liste nehmen, Deinstallation, Advanced Protection, Dienst durch Akku-/Memory-Killer beendet (keine Benachrichtigung), Dienst-Neustart bei offener gesperrter App (Luecke bis zum naechsten Fensterwechsel, Spike S3-e misst es). Keine `WRITE_SECURE_SETTINGS`-Selbstheilung im MVP.

**Warum:** Ein Accessibility-Dienst ist vom Nutzer jederzeit abschaltbar; ehrliche Dokumentation statt falscher Sicherheit. `ProtectedPackages` (u. a. eigene App, `com.ichi2.anki`, Einstellungen, systemui, Home-Launcher, IMEs, Dialer, Notruf) verhindern Selbstaussperrung.

## 2026-11-02 - Karten-Rendering: sichere WebView mit Klartext-Rueckfall

**Entscheidung:** WebView ohne JavaScript, ohne Netz und Dateizugriff, CSP-Meta, Reveal per neu geladenem Dokument; automatischer Rueckfall auf Klartext-Renderer, Karte bleibt bewertbar. Medien nur als Platzhalter (`[ bild: x ]`, `[ audio ]`), Karten mit Medien werden nicht uebersprungen, keine Karten werden begraben oder uebersprungen. A4 "schwer" hat einen bewusst allgemeinen Text (Hard verschiebt in Anki oft um Tage).

**Warum:** Karteninhalt ist fremd; Medien sind ueber die API nicht lesbar (`research.md` F4); Ueberspringen/Begraben veraendert den Lernplan.

## 2026-11-02 - Quiz-Session, Setup-Design und Texte (vom User zu bestaetigen)

**Entscheidung:** Session bleibt 10 min Leerlauf erhalten (Zielwechsel uebernimmt `correct`); `required`, Deck und Dauer werden beim Anlegen eingefroren. Kein Prefetch im Intro (AnkiDroid wird erst nach `[start]` angefragt). Setup ohne vierten Tab: Startroute beim Erststart, danach Banner nur bei offenem Pflichtschritt (B1-B3 im gesunden Zustand pixelgleich zu den Mockups). Alle Texte aus `ui/Strings.kt`, nur Deutsch. Neue Karten zaehlen mit (die API hat keinen Filter "nur Wiederholungen").

**Warum:** Kein Deckauswahl-Nebeneffekt bei sofortigem Abbruch; Mockups bleiben unveraendert; Details in `architecture.md` Abschnitte 3, 6, 8.

## 2026-11-02 - Debug-Hooks nur in src/debug, Release-Stubs gleicher FQCN

**Entscheidung:** Debug-Code (Receiver, Gallery, Fakes, Spike-Huelle) nur in `src/debug`; `DebugInit` und `DebugUi` haben im Release No-op-Stubs mit gleichem FQCN; BuildConfig aus; Manifest-Allowlist per `scripts/check-manifest.sh` (nur AnkiDroid-Permission). `check.sh` und alle Tests laufen gegen Debug.

**Warum:** Kein Debug-Code im Release, fester Berechtigungsumfang, einfacher E2E-Test per `am broadcast` (Aktionstabelle in `architecture.md` Abschnitt 9).

## 2026-11-03 - Offene Entscheidungen aus der Planung: vom User bestaetigt

Der User hat alle acht "vom User zu bestaetigen"-Punkte aus `todo.md` mit dem jeweiligen Standard beantwortet (Punkt 1 ausdruecklich "pro App"):
1. Freigabe **pro App** (nicht global)
2. Nichts faellig/Deck leer: Freigabe ohne Quiz (NothingDue-Grant), zaehlt auch fuer die Streak
3. Notausgang bei dauerhaftem AnkiDroid-Fehler: Button nach 60 s, 5 min Freigabe, max. 1x je 6 h
4. Zeitersparnis-Schaetzung: 5 min je abgebrochener Sperre, im UI mit `*`
5. Neue Karten zaehlen mit
6. Setup als Startroute beim Erststart, danach nur Banner bei offenem Pflichtschritt, kein vierter Tab
7. Keine Benachrichtigung bei totem Dienst
8. applicationId `dev.tinow.brainrotstopper`, minSdk 35, Tagesgrenze lokale Mitternacht

**Offen bleibt:** Medien (Bilder/Audio) der Karten. Der User findet "nur Platzhalter" nicht akzeptabel; Recherche zu allen Wegen laeuft (Ergebnis siehe `research.md`/`log.md`). Die Entscheidung "Medien nur als Platzhalter" (2026-11-02, Karten-Rendering) ist damit **vorlaeufig** und wird nach der Recherche ersetzt.

## 2026-11-03 - Medien (Bilder) ueber eigenen Anki-Sync-Server (Option S)

**Entscheidung:** Kartenbilder kommen von einem selbst gehosteten Medienserver (nginx, nur lesend) ueber dem Medienordner des eigenen Anki-Sync-Servers hinter Traefik/HTTPS. Die App holt Bilder per Dateiname (aus dem Karten-HTML) mit Bearer-Token, cached sie und bettet sie als `data:`-URI in die WebView ein (WebView bleibt ohne Netz, CSP `img-src data:`). Nur Bilder, kein Audio. URL und Token stehen in den App-Einstellungen, nicht in der APK oder im Repo. INTERNET wird zur einzigen zusaetzlichen Berechtigung. Bei Fehlern gilt der Platzhalter `[ bild: x ]`, die Karte bleibt bewertbar und die Sperre fail-closed. Ersetzt die vorlaeufige Entscheidung "Medien nur als Platzhalter" (2026-11-02, Karten-Rendering).

**Warum:** AnkiDroid-API liefert keine Mediendateien. Option A (GitHub-Build, PUBLIC-Ordner, All-files-Zugriff) haette zwei zusaetzliche Rechte und einen Build-Wechsel gebraucht; der Server laeuft schon, ist getestet (200/304/206/401/405) und funktioniert mit dem Play-Store-AnkiDroid. Option B (manueller Export) verworfen.

**Konsequenzen:** Manifest-Allowlist und Verbotsliste angepasst; Tests laufen nur gegen einen lokalen Fake-Server (kein echtes Token im Emulator); AnkiDroid und Desktop muessen mit demselben Sync-Server syncen (404 = noch nicht gesynct). Schnittstelle: `media-server.md`. Details: `architecture.md` Abschnitt 5a.

## 2026-11-03 - M2: Breaker-Cooldown schickt die Zielapp nach Hause, weitere Domain-Entscheidungen

**Entscheidung:**
- **Breaker (Abweichung von Tabelle 3A):** Waehrend des 30-s-Cooldowns nach dem Breaker laeuft die Zielapp nicht frei weiter. Die Zustandsmaschine sendet `GoHome` hoechstens alle `BREAKER_MIN_INTERVAL_MS` (3 s) und plant ein Re-Evaluate; nach 30 s folgt wieder `StartLock`. Geht der Nutzer selbst Home oder in eine andere App, endet der Cooldown ohne Lock.
- **Kein Block fuer gueltigen Grant:** `RuntimeOps.attach` zaehlt keinen Block und oeffnet keine Episode, wenn fuer das Ziel ein gueltiger Grant existiert (Wiederkehr in die Unlocked-Ansicht). Sonst zaehlte jedes erneute Oeffnen nach dem Unlock als Block.
- **Quiz-Events:** nur `Cancel` (Zurueck/Geste/[abbrechen]); Notausgang und NothingDue-Grant als `CommitGrant`-Effekt mit `GrantResult`-Event (UI wechselt erst nach dem Commit), `grantPending` blockiert Doppel-Taps. Richtige Antwort ohne Unlock geht direkt zur naechsten Karte (Loading), nur Again/Hard zeigen Feedback. Nach Retry bei `AnswerNotSaved` wird `pending` verworfen (`DiscardPending`), damit die Recovery sie nicht doppelt sendet.
- **Statistik als zweite Datei:** `RuntimeRepository.apply` schreibt erst `runtime.json` (ein `updateData`), dann `stats.json`. Ein Prozesstod dazwischen kostet hoechstens einen Zaehler, nie Grant oder Antwort. Eine gemeinsame Datei wurde verworfen, weil 400 Tage Statistik bei jedem Antwort-Commit neu geschrieben wuerden.
- **Row 4a:** Wartezeit bei frischer falscher Antwort gilt nur, wenn `LEARN_COUNT_INCLUDES_PENDING=false`; gemessen ist `true`, also greift sie nicht. Negative Zeitdifferenz zaehlt dabei als "frisch" (fail-closed).
- **Korruption:** `settings.json` -> Spiegelkopie -> Defaults, immer mit Flag `runtime.diag.settingsCorrupted`. `runtime.json`/`stats.json` defekt -> leer (fail-closed: keine Grants). Der Corruption-Callback ist nicht suspendierend (`ReplaceFileCorruptionHandler` erlaubt nur ein normales Lambda), der Container startet das Flag-Schreiben im IO-Scope.
- **Streak:** `StreakCalculator.streak` (mit heute, leerer Tag heute bricht nicht, wenn gestern zaehlte) und `streakBeforeToday` (Intro); Notausgang-Tage zaehlen nicht, NothingDue-Tage schon.

**Warum:** Ein Breaker, der die App frei laesst, waere ein Umgehungsweg per Schnell-Wiederoeffnen; die Tabelle nannte nur "kein Lock starten", der Invariante "Verzoegerung gibt die App nie frei" nach muss die App aber weg. Die uebrigen Punkte folgen aus der Statistik-Tabelle (Episoden, aborted <= blocks) und der Write-ahead-Pipeline und sind durch Tests belegt.


## 2026-11-04 M3-T2: Gate-Huelle

- **Ein UNLIMITED-Kanal statt CONFLATED** fuer alle Eingaben des `GateController` (Events, Timer, ScreenOn, Store-Aenderung, Lock-Meldungen), eine Schleife arbeitet sie in Reihenfolge ab. Bis die Stores und die dynamische Schutzmenge das erste Mal geliefert haben, wartet die Schleife (kein Entscheiden auf leerem Snapshot, kein Freipass). **Warum:** Mit CONFLATED koennte das Event einer gesperrten App durch ein direkt folgendes ignoriertes Overlay-Event (systemui) verloren gehen; die Eventmenge ist klein, der Breaker begrenzt die Startrate.
- **Eigenpaket im Tracker:** Events der `LockActivity` aendern den Vordergrund nie; andere Eigenpaket-Events (Starting-Window `FrameLayout` beim Lock-Start) zaehlen nicht, solange `lockActive` (angefordert/sichtbar) gilt, sonst ist das Eigenpaket normaler (geschuetzter) Vordergrund. **Warum:** Sonst setzt der Starting-Window-Event direkt nach `StartLock` den Zustand auf Free und die Ack-Ueberwachung ginge verloren.
- **Karenz `LOCK_GONE_GRACE_MS` = 300 ms** nach `LockUiBeacon.gone()`: erst danach wird neu bewertet, ein dazwischen kommendes Vordergrund-Event entscheidet statt dessen. **Warum:** Beim Verlassen der Sperre ist der Tracker noch auf der Ziel-App; sofortiges Neubewerten wuerde die Sperre beim Home-Druck erneut starten (Flackern). Kommt kein Event, sperrt das Gate nach der Karenz wieder (fail-closed). Gemessen: nur JVM-Test, Emulator-Verhalten mit der echten Huelle in M3-T3/T5 pruefen.
- **Schutzmenge dynamisch:** alle HOME-Launcher (`<queries>`-Eintrag noetig), Standard-Dialer, aktivierte IMEs; Neuladen beim Start, alle `PROTECTED_REFRESH_MS` und bei SCREEN_ON. Schlaegt das Laden fehl oder dauert > 5 s, bleibt die letzte Menge (oder leer); die statische Schutzmenge gilt immer. IMEs sind zusaetzlich im Tracker ignoriert.

## 2026-11-04 M3-T3: Sperrbildschirm

- **Vollbild ohne Systemleisten** in der `LockActivity` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`). **Warum:** Mockups zeigen keine Leisten; mit Leisten lag der SSIM gegen A1 bei 0,83, ohne bei 0,94. Die Leisten sind per Wischen von der Kante weiter erreichbar.
- **Bis M4 hat `[start]` keine Quiz-Wirkung** (nur Ladescreen mit abbrechen). `attach`/`cancel` laufen schon in der Huelle ueber `RuntimeOps`, damit Block-/Abbruchstatistik stimmt; die `QuizEngine` uebernimmt das in M4.

## 2026-11-04 M3-T4: Setup und Shell

- **Setup v1 hat nur Bedienungshilfe (Pflicht) und Akku (empfohlen)**; die drei AnkiDroid-Zeilen und der Banner `Degraded` kommen mit M4-T5. `Protection.mandatoryOpen` zaehlt bis dahin nur die Bedienungshilfe.
- **Status "blockiert durch eingeschraenkte Einstellungen" (`Restricted`) wird vom Reader nicht erkannt** (keine oeffentliche API; (unverifiziert) ob ein AppOps-Wert dafuer taugt). Zustand, Banner, Setup-Zeile und Debug-Fake existieren; im echten Betrieb sieht der Nutzer "nicht aktiviert" plus den Tipp zu den eingeschraenkten Einstellungen. Am Geraet pruefen (M7, `test-checklist.md`).
- **`onboardingDismissed` wird gesetzt, wenn der Nutzer das Setup verlaesst** (Zurueck oder `$ ./weiter`), nicht beim Anzeigen. Danach erscheint das Setup nur noch ueber den Banner. `$ ./weiter` gibt es nur, wenn kein Pflichtschritt offen ist.
- **Bindestatus = `AccessibilityManager.getEnabledAccessibilityServiceList`** (listet laufende Dienste, am Emulator bestaetigt), Aktivierung = Eintrag in `enabled_accessibility_services`. Tab-Inhalte (B1-B3) sind Platzhalter bis M5.

## 2026-11-04 M4-T1/T2: Anki-Quelle

- **`IllegalStateException` -> StorageNotConfigured nur bei Meldung mit "storage"**, sonst `Unexpected("IllegalStateException")`. **Warum:** Die Architektur-Tabelle bildet jede ISE auf "ankidroid einmal oeffnen und einrichten" ab; eine andere ISE (z. B. geschlossene Sammlung) wuerde den Nutzer in die Irre fuehren. Gemessene Meldung: `AnkiDroid storage is not configured`.
- **`CardSource.nextCard` liefert `NextCard`** (Ref, Tastenanzahl, Intervall-Labels, Medienliste) statt nur `CardRef`; die Queue-Matrix (`QueuePolicy`) bleibt auf `CardRef`, die Engine (T4) bildet ab. `render` liefert die Rohseiten, HTML-Aufbau bleibt `CardHtml`.
- **Modell-CSS ist best effort:** jeder Fehler beim Lesen heisst "keine CSS", nie ein Fehler und nie ein Grund, die Sperre geschlossen zu halten.
- **Antwort:** `update`-Zeilenzahl >= 1 ist `Ok`, 0 ist `AnswerNotSaved`; ob die Antwort wirklich angekommen ist, prueft die Engine ueber `cardProgress` (answerVerify).

- 2026-11-04 - **Kartenfarben (M4-T3):** Das Theme gewinnt immer. `CardHtml` haengt das Notiztyp-CSS (bereinigt: `@import`, `url()`, `expression` raus) an und danach ein Theme-Block mit `!important` fuer Text- und Hintergrundfarbe aller Elemente (`*`). Grund: Notiztypen setzen `color:black;background:white` und Karten harte Inline-Farben, das waere im Dunkelmodus unlesbar. Preis: Autorenfarben auf Karten gehen verloren, nur `.cloze` bekommt die Akzentfarbe. Schrift ist `monospace` (JetBrains Mono ist in der WebView ohne Dateizugriff nicht ladbar). Alle `data-*`-Attribute werden entfernt (Cloze-Fragen tragen die Loesung in `data-cloze`).
- 2026-11-04 - **Bilder in `CardHtml` (M4-T3, Vorarbeit fuer T3b):** `build(..., images: Map<Name, data-URI>)`; nur `data:image/(png|jpeg|gif|webp);base64,...` wird eingesetzt, sonst Platzhalter. `imageNames` liefert nur sichere Namen (kein `/`, `\`, `:`, `..`, keine Steuerzeichen), andere bleiben Platzhalter `[ bild ]`. Bildmarker im Zwischenformat sind U+E000/E001, vorhandene werden aus Karten entfernt.
