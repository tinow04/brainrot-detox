# Brainrot Stopper - verbindlicher Bauplan (Revision 3: Medien per eigenem Server)

> Stand 2026-11-03: Bauplan (Revision 2 aus der Planungs-Synthese, Revision 3 = Medien-Option S: Abschnitt 5a, Manifest, Rendering; Schnittstelle des Servers in `media-server.md`), noch kein Code. Nach der Umsetzung je Meilenstein an den echten Stand anpassen (Ist vs. Soll kennzeichnen). Vollstaendiger Plan inkl. Meilensteinen: `plan.json`; Aufgabenliste: `todo.md`; Faktenbasis: `research.md`.

> **Ist-Stand nach M4 (2026-11-09):** M3 und M4 sind gebaut und am Emulator belegt: Sperre per Accessibility-Dienst und `LockActivity`, Quiz A1-A5 gegen das echte AnkiDroid (E2E-2 `scripts/e2e/e2e2_quiz.sh`, Contract-Test), Medien vom eigenen Server, Setup-Schritte AnkiDroid, Notausgang. Belegte API-Fakten: `anki-api.md`. Offen: M5 (Einstellungen, App-Auswahl, Statistik-Screens), M6, M7 (echtes Geraet, Sync).

> **Ist-Stand nach M2 (2026-11-03):** Gebaut und JVM-getestet sind `domain/` komplett (Modelle, Zeit, Grant/Gate inkl. `GateStateMachine`, Quiz-Reducer mit `QueuePolicy`/`SessionPolicy`, `RuntimeOps`, Statistik) und `data/store/` (DataStore-Repositories) plus `data/system/SystemTimeSource`, `AppContainer`-Verdrahtung und alle Debug-Hooks. Abweichungen vom Soll unten: (1) `domain/runtime/RuntimeOps` (reine Operationen auf `Runtime`: attach, cancel, setPending, commitAnswer, commitNothingDue, commitEmergency, `EmergencyPolicy`) und `domain/stats/StatsLedger` (`StatsDelta`, `Stats.plus`) sind neu; `domain/model/BsJson` ist die gemeinsame JSON-Konfiguration. (2) `DeckInfo`, `DeckCounts`, `AnkiCapabilities`, `CardSource` liegen in `domain/anki/Anki.kt`; `NextCard` entfiel (`nextCard` liefert `AnkiResult<CardRef?>`, `Ok(null)` = leerer Cursor), `RenderedCard` folgt mit M4. (3) Quiz: ein einziges `Cancel`-Event (Zurueck/Geste/[abbrechen]); Notausgang und NothingDue laufen ueber `CommitGrant`-Effekt und `GrantResult`-Event, `grantPending` verhindert Doppel-Taps; die Statistik ist eine zweite `updateData`-Schreibung nach dem Runtime-Commit (Abbruch dazwischen kostet hoechstens einen Zaehler, nie Grant oder Antwort). (4) Breaker-Cooldown schickt die Zielapp alle `BREAKER_MIN_INTERVAL_MS` per GoHome nach Hause (siehe `decisions.md`). (5) Die Debug-Flags `sessionTtlMsOverride`, `unlockSecondsOverride`, `simulateLaunchBlock`, `throwInHandler`, `gatewayDelayMs`, `cardSource`/`scenario`, `floodRequest` sind gesetzt und im Dump sichtbar, ihre Verbraucher entstehen in M3 (Gate, Launcher) und M4 (Engine, Gateway, Fake-Source); `wallOffsetMs` wirkt schon (`SystemTimeSource`).

Basis ist Entwurf A (kleinster Bauumfang), ergaenzt um Prefetch-freies Intro, je Fehler ein Screen mit einer Hauptaktion, Rate-Guard, Write-ahead-Antwort, strenge Freigabe-Invariante, Launch-Ack, Diagnose und Debug-Dump. Kein Room, kein KSP, kein Hilt, keine Navigation-Bibliothek, kein Material3, kein Robolectric, kein Overlay (SYSTEM_ALERT_WINDOW), kein Foreground-Service, kein Boot-Receiver, kein BuildConfig. INTERNET nur fuer den Bild-Abruf vom eigenen Medienserver (Revision 3, Abschnitt 5a); sonst keine Netzfunktion.

Leitregeln fuer Sonnet 5.5 (Umsetzer, nicht Entscheider):
1. Jede Entscheidung steht in diesem Dokument. Wo ein Spike (M0) ein Ergebnis liefert, ist der Zweig als Tabelle in Abschnitt 11 mechanisch festgelegt. Passt ein Messergebnis in keine Zeile: STOPP, Befund in .agents/gotchas.md, Rueckgabe an Opus (Plan-Korrektur). Nie improvisieren.
2. API-Details nie aus dem Gedaechtnis behaupten: Beleg (URL oder Datei+Zeile, Tag v2.25.3 bei AnkiDroid) oder Kommentar `(unverifiziert)`.
3. Alle UI-Texte stehen in genau einer Quelle `ui/Strings.kt` (Kotlin-Konstanten und reine Formatfunktionen, JVM-getestet). Nur Deutsch, echte Umlaute wie in den Mockups. strings.xml enthaelt nur app_name und die Beschreibung des Accessibility-Dienstes (Ressourcenpflicht).
4. Fail-closed: bei jedem unerwarteten Zustand bleibt die Sperre zu. Einzige Ausnahmen sind explizit benannt (NothingDue-Grant, Notausgang).
5. Debug-Code nie im Release (Abschnitt 1, Quellsatz-Matrix).

## 1. Module, Pakete, Quellsaetze

Gradle: `:app` (applicationId = namespace = dev.tinow.brainrotstopper, kein Debug-Suffix) und `:testapp` mit zwei Product Flavors (Dimension `variant`): `one` = applicationId dev.tinow.brainrotstopper.dummy (knallmagenta Vollbild, Text dummy) und `two` = dev.tinow.brainrotstopper.dummy2 (knallcyan, Text dummy2). Beide: launcher-faehige `DummyActivity` (supportsPictureInPicture=true, resizeableActivity=true, configChanges fuer screenSize|smallestScreenSize|screenLayout|orientation), Buttons `pip` (enterPictureInPictureMode), `dialog` (AlertDialog) und Intent-Extra `slow` (800 ms Sleep in onCreate als Splash-Simulation). Jede Activity loggt `BSDummy resumed t=<uptimeMillis>` in onResume und onWindowFocusChanged. Label und eigenes Vektor-Icon je Flavor. Version Catalog `gradle/libs.versions.toml`, nur benoetigte Libraries. Build-Aufruf fuer beide Flavors: `:testapp:assembleDebug`.

`:app`-Pakete (Basis dev.tinow.brainrotstopper):
- `domain/` - reines Kotlin, KEIN `import android.` (DomainPurityTest). `model/` (Settings, DeckSelection, UnlockGrant, QuizSession, PendingAnswer, Episode, Runtime, Diag, DayStats, Stats, Ease, CardRef, DeckInfo, DeckCounts, RenderedCard, AnkiCapabilities), `time/` (TimeSource, TimeSnapshot), `gate/` (GrantPolicy, GateEvaluator, ProtectedPackages, GateStateMachine, GateEffect, LaunchThrottle), `quiz/` (QuizState, QuizEvent, QuizEffect, QuizReducer, QueuePolicy, SessionPolicy, Constants), `stats/` (StatsAggregator, StreakCalculator), `anki/` (CardSource, AnkiResult, AnkiError, NextCard).
- `data/store/` - `JsonSerializer<T>`, `SettingsRepository`, `RuntimeRepository`, `StatsRepository`. Genau eine DataStore-Instanz je Datei, erzeugt im `AppContainer` mit `CoroutineScope(SupervisorJob()+Dispatchers.IO)`. Mutationen nur ueber `updateData`.
- `data/anki/` - `AnkiContract`, `ProviderGateway` (Interface) + `AndroidProviderGateway`, `AnkiContentProviderSource : CardSource`, `CardHtml`, `AnkiErrorMapper`, `AnkiCapabilities.kt` (Werte aus Abschnitt 11).
- `data/system/` - `SystemTimeSource`, `PackageCatalog`, `PermissionStatusReader`, `AppLog` (Tags BS/Gate, BS/Anki, BS/Quiz, BS/Svc, BS/UI, BSState; nie Karteninhalt loggen).
- `service/` - `BlockerAccessibilityService` (duenn), `ForegroundTracker`, `GateController` (Android-Huelle um die reine `GateStateMachine`), `LockLauncher`, `LockUiBeacon`.
- `lock/` - `LockActivity`, `QuizEngine` (Effekt-Runner um den Reducer, StateFlow, im Container-Scope), `LockRoot` + Screens, `render/` (`CardRenderer`, `WebViewCardRenderer`, `PlainTextCardRenderer`, `RendererSelector`).
- `data/media/` - `MediaConfig` (aus Settings), `MediaFetcher` (Interface) + `HttpMediaFetcher` (java.net.HttpURLConnection, keine neue Library), `MediaCache` (Dateien in `cacheDir/media/`), `MediaInliner` (reine Funktion: Karten-HTML + geholte Bytes -> HTML mit `data:`-URIs bzw. Platzhalter). Fake: `FakeMediaFetcher` (src/debug).
- `ui/theme/` (BsColors, BsTheme, BsType, BsDims), `ui/Strings.kt`, `ui/components/`, `ui/main/MainActivity` (zustandsbasierte Navigation per rememberSaveable, screenOrientation=portrait), `ui/apps`, `ui/config`, `ui/stats`, `ui/setup`, `ui/banner`.
- `hooks/DebugFlags.kt` (main): Interface mit ausschliesslich neutralen Default-Werten plus `object NoDebugFlags`. Produktiver Code liest Flags nur darueber (`container.debugFlags`).
- `di/AppContainer` + `BrainrotApp : Application` - manuelle DI, ein Prozess (kein android:process). `BrainrotApp.onCreate` ruft `DebugInit.install(container)`.
- `scripts/` (bash): `gradlew-jdk17.sh`, `emu-env.sh`, `emu-start.sh`, `emu-stop.sh`, `anki-install.sh`, `a11y-enable.sh`, `install-debug.sh`, `focus.sh`, `shot.sh`, `revlog.sh`, `check.sh`, `check-manifest.sh`, `e2e/*.sh`, `spike/*.sh`. `tools/mockup/` (node): Referenzrenderer, String-Extraktor, Dims-Extraktor (M1-T0).

### Quellsatz-Matrix (verbindlich)

| Klasse / Datei | main | debug | release | test | androidTest |
|---|---|---|---|---|---|
| domain/*, data/*, service/*, lock/*, ui/*, di/*, hooks/DebugFlags | ja | | | | |
| `debug/DebugInit` (FQCN dev.tinow.brainrotstopper.debug.DebugInit, Methode install(container)) | | echte Version: setzt `DebugFlagsImpl`, StrictMode `VmPolicy.Builder().detectBlockedBackgroundActivityLaunch().penaltyLog()` | No-op, gleicher FQCN | | |
| `debug/DebugUi` (gleicher FQCN; `Modifier.debugBounds(tag)`) | ruft nur auf | echte Version loggt `BS/UI btn=<tag> x= y= w= h=` (Mitte in Bildschirmpixeln) | No-op | | |
| DebugControlReceiver, ComponentGalleryActivity, SpikeReceiver, SpikeActivity | | ja (src/debug/AndroidManifest.xml, Receiver exported) | nein | | |
| Fakes: FakeCardSource, FakeProviderGateway, FakeTimeSource, FakeLockLauncher, FakeClock (Paket ...debug.fake) | | ja | nein | nutzt sie (testDebugUnitTest sieht src/debug) | nutzt sie (Debug-APK enthaelt sie) |
| JVM-Tests, Fixtures unter src/test/resources | | | | ja | |
| Compose-UI- und DataStore-Tests, Contract-Test | | | | | ja |

check.sh und alle Tests laufen nur gegen die Debug-Variante. `assembleRelease` darf keinen Verweis auf src/debug-Klassen haben (Release-Stubs gleicher FQCN). BuildConfig bleibt aus (`buildFeatures.buildConfig=false`).

### Manifest und Ressourcen

- uses-permission nur `com.ichi2.anki.permission.READ_WRITE_DATABASE` und `android.permission.INTERNET` (Revision 3, nur Medienabruf). Allowlist fuer das Release-Manifest (`scripts/manifest-allowlist.txt`): diese beiden Permissions und, falls androidx.core sie mergt, `dev.tinow.brainrotstopper.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (unverifiziert, per aapt2 pruefen). Verbotsliste: ACCESS_NETWORK_STATE und alle sonstigen Netz-/WLAN-Permissions, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE*, SYSTEM_ALERT_WINDOW, PACKAGE_USAGE_STATS, QUERY_ALL_PACKAGES (ausser nachgewiesen noetig, dann Plan-Korrektur), WRITE_SECURE_SETTINGS, POST_NOTIFICATIONS. `scripts/check-manifest.sh` vergleicht `aapt2 dump permissions` mit der Allowlist und bricht bei jeder Abweichung ab.
- `<queries>` mit `<package android:name=com.ichi2.anki/>` und MAIN/LAUNCHER-Intent. KEIN BOOT, FGS, SAW, USAGE_STATS. `res/xml/network_security_config.xml`: `base-config cleartextTrafficPermitted=false` (Release). `src/debug` ueberschreibt sie zusaetzlich mit Cleartext nur fuer `10.0.2.2` (lokaler Fake-Medienserver der Emulator-Tests); die echte Server-URL steht nie im Manifest oder Repo, sondern in den Einstellungen (5a).
- Backup: `allowBackup=false` UND `data_extraction_rules.xml` mit `<exclude domain=file path=.>` in cloud-backup und device-transfer (bewusst redundant: Einstellungen, Statistik und Runtime werden nie uebertragen; Nutzer richtet nach Geraetewechsel neu ein; Eintrag in decisions.md).
- Dienst: BIND_ACCESSIBILITY_SERVICE, nicht directBootAware, `isAccessibilityTool` NIE setzen, `canRetrieveWindowContent=false`, nur `typeWindowStateChanged`, `android:description` (deutsch, ohne Behauptungen, die nicht stimmen): Erkennt, wenn eine gesperrte App geoeffnet wird, und zeigt dann Anki-Karten an. Es werden keine Inhalte anderer Apps gelesen und nichts ins Netz gesendet. Service-Label: Brainrot Stopper.
- `LockActivity`: exported=false, launchMode=singleTask, eigenes taskAffinity, excludeFromRecents, resizeableActivity=false, screenOrientation=portrait, Theme mit windowBackground in Token-Farbe (values/values-night), `windowAnimationStyle` ohne Animation. `allowCrossUidActivitySwitchFromBelow` nicht setzen.
- Launcher-Icon (adaptive, Vektor): Hintergrund #1e1e2e, Vordergrund `>_` in #cba6f7. App-Name Brainrot Stopper. lint `MissingApplicationIcon` muss sauber sein.

## 2. Datenfluss

Hot-Path (Sperren):
1. System sendet TYPE_WINDOW_STATE_CHANGED. `onAccessibilityEvent` fuehrt nur `try { channel.trySend(Event(pkg, cls, eventTime)) } catch(Throwable)` aus (Channel UNLIMITED statt CONFLATED, siehe decisions.md 2026-11-04; null-Paket ignorieren). Ein einziger Collector-Job im Service-Scope verarbeitet seriell.
2. `ForegroundTracker` (reine Klasse): Entscheidungsprinzip fuer die Ignore-Tabelle: Vordergrund = Paket des letzten Events, das nicht in IGNORE steht. IGNORE enthaelt nur Pakete, die nach S2-Protokoll transiente Fenster ueber einer anderen App zeigen und nach dem Schliessen KEIN neues Event der darunterliegenden App erzeugen. Vorlaeufiger Inhalt bis S2: com.android.systemui, android (Chooser/Systemdialoge), aktivierte IMEs, Permission-Controller (com.google.android.permissioncontroller, com.android.permissioncontroller), Package-Installer (com.google.android.packageinstaller, com.android.packageinstaller). Eigenpaket: `LockActivity`-Events und alle Eigenpaket-Events bei `lockActive` aendern den Vordergrund nie; `lockVisible` kommt ueber `LockUiBeacon`, nach `gone()` wartet das Gate `LOCK_GONE_GRACE_MS` (300 ms) auf ein Vordergrund-Event, bevor es neu bewertet. Jedes nicht gelistete Paket (auch Unbekannte) gilt als normaler Vordergrund (dann Allow, wenn nicht gesperrt). Die endgueltige Tabelle kommt aus S2 und wird als Tabelle in decisions.md fixiert (Paket, Klasse, Beleg-Szenario).
3. `GateController` haelt einen @Volatile Snapshot (Settings, Runtime, bootCount, dynamische Schutzmengen), gespeist aus den Store-Flows. Hydration beim `onServiceConnected` asynchron im Service-Scope (KEIN runBlocking); bis zur Hydration bleiben Events im Channel, danach wird das letzte ausgewertet. Er uebersetzt Events/Timer/SCREEN_ON in Aufrufe der reinen `GateStateMachine` und fuehrt deren `GateEffect`s aus.
4. `GateEvaluator.evaluate(foreground, snapshot, now)` -> `Allow(NotBlocked|Protected|Granted(remainingMs))` oder `Lock(pkg)`. Bei `Allow(Granted)` plant die Maschine einen Ablauf-Re-Evaluate (delay(remainingMs); nur Optimierung, Wahrheit ist die Pruefung). Zusaetzlich Re-Evaluate bei jedem Event, bei `ACTION_SCREEN_ON`/`ACTION_USER_PRESENT` (dynamisch registrierter Receiver) und bei Settings-/Grant-Aenderung (jeweils mit `ForegroundTracker.current`).
5. Bei `Lock(pkg)`: Throttle/Breaker/Re-Evaluate-Regeln in Abschnitt 3A. `LockLauncher.start(pkg)` = direktes `startActivity(Intent(LockActivity).putExtra(EXTRA_TARGET_PKG, pkg).addFlags(NEW_TASK|NO_ANIMATION))`, kein PendingIntent.
6. Launch-Ack: `LockActivity.onResume` setzt `LockUiBeacon.ackAt`. Kein Ack binnen LAUNCH_ACK_MS und Ziel weiter im Vordergrund: einmal Retry; danach `GLOBAL_ACTION_HOME`, Diag `launchFailedCount+1`. Fail-closed (nie Allow).
7. `LockActivity.onCreate/onNewIntent` -> `QuizEngine.attach(pkg)`. Zuerst Resume-Check: gueltige Freigabe fuer pkg -> Ziel-App starten + finish (Race); pkg nicht mehr gesperrt -> finishAndRemoveTask. Sonst Session fortsetzen oder neu anlegen. Das Intro (A1) greift NICHT auf AnkiDroid zu (kein probe, kein schedule, kein Prozessstart von AnkiDroid); es zeigt nur Daten aus Settings/Stats/Session. Waehrend des Intros wird nur der WebView-Warmup (lokal, ohne Netz) gestartet.
8. [start] -> Zustand Loading -> `probe()` + `deckCounts` + `nextCard(selection)` -> `Card|AllDone|WaitingForLearning` (Abschnitt 3B Queue-Matrix); `render(ref)` -> `CardHtml` -> Renderer. Fehler erscheinen erst hier (Fehlerscreens). Spinner/Loading-Screen erst nach SPINNER_DELAY_MS. Aufdecken -> Answer. Bewertung -> Write-ahead-Pipeline (Abschnitt 4).
9. Ease 3/4 und correct<required -> naechste Karte; Ease 1/2 -> Feedback (A4) -> Continue. correct>=required -> ein atomares `commitUnlock` (Grant + Session loeschen + Episode schliessen + Stats), erst DANACH Zustand Unlocked (A5).
10. `OpenTarget` -> `getLaunchIntentForPackage(pkg)` aus der sichtbaren Activity + finish. Cancel/Zurueck/Geste -> HOME-Intent + finish (nie nur finish).
11. Haupt-App liest dieselben Repository-Flows (collectAsStateWithLifecycle), schreibt nur ueber Repository-Methoden; der Dienst reagiert ueber dieselben Flows. Statistik schreibt nur die Engine; `StatsAggregator` rechnet rein funktional.
Sync: kein eigener Code; Setup empfiehlt Auto-Sync in AnkiDroid. DO_SYNC nicht im MVP.

## 3. Zustandsmaschinen

### A) Gate (`GateStateMachine`, rein, virtuelle Zeit, JVM-getestet; Halter `GateController`)

Zustaende: `Unknown`, `Free`, `LockRequested(pkg, since, retried)`, `LockVisible(pkg)`, `CoolDown(pkg, until, breaker)`. Eingaben: `Event(pkg, cls)`, `Timer(kind)`, `ScreenOn`, `SettingsChanged`, `Ack`, `LockVisibleChanged(pkg?)`, `Connected`. Ausgaben (`GateEffect`): `StartLock(pkg, retry)`, `GoHome`, `ScheduleReeval(atElapsed)`, `CancelTimers`, `Diag(kind)`, `Log`.

| Zustand | Eingabe | Bedingung | Folgezustand | Effekte |
|---|---|---|---|---|
| Unknown | Connected | - | Unknown | CancelTimers |
| Unknown/Free/LockVisible/CoolDown | Event (nicht ignoriert) | evaluate = Allow | Free | CancelTimers; Granted: ScheduleReeval(until) |
| Unknown/Free/LockVisible | Event/Reeval/ScreenOn | evaluate = Lock(pkg), Throttle erlaubt, kein lockVisible fuer pkg | LockRequested(pkg) | StartLock(pkg,false), ScheduleReeval(now+LAUNCH_ACK_MS) |
| beliebig | Event/Reeval | evaluate = Lock(pkg), lockVisible fuer pkg | LockVisible(pkg) | - |
| beliebig | Event/Reeval | evaluate = Lock(pkg), Throttle verbietet (Fenster RELAUNCH_MIN_MS je Paket noch offen) | CoolDown(pkg, until=Fensterende, breaker=false) | ScheduleReeval(until + REEVAL_TOLERANCE_MS) |
| beliebig | Lock-Entscheidung | Breaker: mehr als BREAKER_STARTS Starts in BREAKER_WINDOW_MS | CoolDown(pkg, until=now+BREAKER_COOLDOWN_MS, breaker=true) | GoHome, Diag(breaker), ScheduleReeval(now + BREAKER_MIN_INTERVAL_MS) |
| CoolDown | Reeval | evaluate(tracker.current) = Lock(pkg), Mindestabstand erreicht | LockRequested(pkg) | StartLock(pkg,false) |
| CoolDown(breaker) | Reeval | Mindestabstand noch nicht erreicht oder Cooldown noch aktiv | CoolDown(...) | ScheduleReeval(naechster Abstand) |
| CoolDown | Reeval | evaluate = Allow (z. B. Nutzer ging Home) | Free | - |
| LockRequested | Ack | - | LockVisible(pkg) | CancelTimers |
| LockRequested | Reeval(Ack-Frist) | kein Ack, Ziel noch Vordergrund, retried=false | LockRequested(retried=true) | StartLock(pkg,true), ScheduleReeval(+LAUNCH_ACK_MS) |
| LockRequested | Reeval(Ack-Frist) | kein Ack, retried=true | CoolDown(pkg) | GoHome, Diag(launchFailed) |
| LockVisible(pkg) | Event | anderes gesperrtes Paket | LockRequested(neu) | StartLock (onNewIntent) |
| LockVisible | Event | nicht gesperrt | Free | - |
| Free | Timer/ScreenOn/Event | Freigabe abgelaufen, Paket gesperrt | LockRequested | StartLock |

Invariante (kein Dauer-Freipass): Ein Lock-Entscheidung, die durch Throttle/Breaker verschoben wird, plant IMMER einen `ScheduleReeval` fuer das zuletzt bekannte Vordergrundpaket (`ForegroundTracker.current`). Der Re-Evaluate laeuft auch ohne weiteres Event. Der Breaker begrenzt nur die Startrate und gibt NIE frei; waehrend Breaker-Cooldown wird weiter in Abstaenden von BREAKER_MIN_INTERVAL_MS neu bewertet, bis das Fenster ablaeuft. JVM-Test (Pflicht): Event im Throttle-Fenster, danach Stille -> StartLock erscheint nach hoechstens Fensterende + REEVAL_TOLERANCE_MS (virtuelle Zeit); dasselbe im Breaker-Cooldown; Nutzer geht im Fenster Home -> kein StartLock.

Bekannte Luecke (Restrisiko, nicht verschweigen): Nach Dienst-Neubindung (kill, `adb install -r`, Crash-Restart) ist der Vordergrund bis zum naechsten Fensterwechsel unbekannt (canRetrieveWindowContent=false, kein getWindows). Eine bereits geoeffnete gesperrte App wird dann erst beim naechsten Event gesperrt; ein verlorener Ablauf-Timer wird erst bei Event/SCREEN_ON bemerkt. S3-e misst das (Dummy offen, Dienst kill -9, Zeit bis Lock). Gegenmassnahme im MVP: keine; Befund als Matrixzeile in gotchas.md und Hinweis im Setup-Text. Zeigt S3-e, dass der Dienst binnen 5 s ohne Zutun ein Event bekommt und sperrt, ist die Luecke klein; sonst bleibt sie dokumentiert.

### B) Quiz (`QuizEngine`; reiner `reduce(state, event, ctx): Pair<QuizState, List<QuizEffect>>`; Sequenznummer je Effekt, veraltete Ergebnisse werden ignoriert)

Zustaende: Idle -> Intro -> Loading -> Question -> Answer -> (SubmitPending) -> Feedback|Loading|Unlocked; Waiting; Error(kind, retry, firstErrorElapsed); Closed.
- Zaehlregel: nur Ease 3 und 4 erhoehen `correct`; jede erfolgreich gespeicherte Antwort erhoeht `answered`. Ease 1/2 -> Feedback, correct unveraendert.
- Rate-Guard: Aufdecken und Bewerten werden innerhalb RATE_GUARD_MS nach Anzeige bzw. Aufdecken still verworfen (elapsedRealtime).
- Persistenz: nach jeder zaehlenden Aenderung `updateData` VOR dem UI-Wechsel. Aktuelle Karte wird nicht persistiert.
- SessionPolicy: Session je Zielpaket; Leerlauf-TTL SESSION_TTL_MS (elapsed) und gleicher bootCount, sonst frisch bei 0; Zielwechsel innerhalb TTL uebernimmt `correct` (Retarget); `required`, Deck und Dauer werden beim Anlegen eingefroren.
- Cancel/Zurueck/Geste in jedem Zustand ausser Unlocked: Session bleibt (TTL), Episode wird als abgebrochen geschlossen (Abschnitt 4 Stats-Tabelle), HOME + finish. Resume prueft zuerst `GrantPolicy.isValid` fuer das Ziel.
- Unlocked (A5): Zurueck, Geste und [schliessen] = HOME + finish (Freigabe bleibt gueltig, nichts wird abgebrochen oder gezaehlt). [$ ./open app] startet die Zielapp + finish.

#### Queue-Matrix (leere Queue), verbindlich, je Zeile ein Reducer-/Source-Test

Eingabe: `deckCounts` (learn, review, new) und Cursor. Aggregation: `Single(id)` -> counts von decks/<id> (schliesst Unterdecks ein). `All` -> Summe der counts aller nicht-dynamischen Top-Level-Decks (deck_name ohne `::`, deck_dyn=0; Top-Level-Zahlen enthalten Unterdecks, deshalb keine Doppelzaehlung). `All` fragt `schedule` je Top-Level-Deck mit Summe>0 in Reihenfolge absteigender Summe, erstes mit nicht-leerem Cursor gewinnt; sind alle Cursor leer, gilt die Matrix mit den aggregierten Counts. Entscheidung nie ueber den Cursor allein.

| Nr | Cursor | Counts | Ergebnis |
|---|---|---|---|
| 1 | nicht leer | egal | `Card` |
| 2 | null | egal | Fehler NotInstalledOrApiOff (NIE AllDone) |
| 3 | leer | nicht lesbar/Exception | Fehler (Unreachable/BadData), NIE AllDone |
| 4 | leer | learn+review+new = 0 | `AllDone` -> NothingDue-Grant (siehe unten), unabhaengig von correct/answered |
| 5 | leer | learn > 0 | `WaitingForLearning` |
| 6 | leer | learn = 0, review+new > 0 | Fehler BadData (Zaehler passen nicht zur Queue), fail-closed |

NothingDue-Grant (`NothingDuePolicy.GRANT`, Konstante, kein Einstellungs-UI): bei AllDone wird die Zielapp fuer die normale Dauer freigegeben, auch wenn correct<required (Deck wirklich leer; die Huerde sinkt dann bewusst unter required; als Entscheidung ausgewiesen, siehe openDecisions). Nutzer erreicht A5-Variante AllDone nach [start] (Intro wartet nicht auf Anki, keine Auto-Weiterleitung). Der Grant zaehlt `freeUnlocks`, nicht `unlocks`.
WaitingForLearning: Waiting-Screen, pollt alle WAIT_POLL_MS (deckCounts + nextCard), KEINE zeitbasierte Freigabe, jederzeit [abbrechen -> Home]. Gilt Konstante `LEARN_COUNT_INCLUDES_PENDING=false` (Abschnitt 11, S1a-6), kommt Zeile 4a hinzu: ist summe=0 und `session.lastWrongElapsed` juenger als LEARN_PENDING_WAIT_MS (20 min), dann Waiting statt AllDone, danach AllDone.

### C) Freigabe (`RuntimeRepository`; reine `GrantPolicy`)

`UnlockGrant(grantedAtElapsedMs, untilElapsedMs, durationMs, bootCount, grantedAtWallMs, kind: QUIZ|NOTHING_DUE|EMERGENCY)` je Zielpaket (Freigabe gilt je App, siehe Defaults). `isValid` = `now.bootCount >= 0 && g.bootCount == now.bootCount && g.grantedAtElapsedMs <= now.elapsed && now.elapsed < g.untilElapsedMs && g.untilElapsedMs - now.elapsed <= g.durationMs + GRANT_PLAUSIBILITY_SLACK_MS`. BOOT_COUNT unlesbar (-1) = nie gueltig. Wall-Clock nie fuer Entscheidungen. Abgelaufene Grants werden bei jedem Schreiben entfernt. Keine Verlaengerung ohne neues Quiz. Freigabe laeuft bei offener App ab -> sofort wieder sperren.

## 4. Datenmodell und Persistenz

DataStore (JSON, kotlinx-serialization), Dateien in `files/datastore/`, alle mit `schemaVersion` und Defaults fuer neue Felder.
- `settings.json`: `Settings(blockedPackages: Set<String>, requiredCorrect: Int = 5 (1..20), unlockMinutes: Int = 15 (5|15|30|60), deck: DeckSelection = All, onboardingDismissed = false)`. `DeckSelection = All | Single(deckId: Long, nameSnapshot: String)`. Korruptionsschutz: `SettingsRepository` spiegelt nach jedem erfolgreichen Update nach `files/settings.mirror.json` (Temp-Datei + atomares Rename). `ReplaceFileCorruptionHandler` stellt aus der Spiegelkopie her; nur wenn auch diese fehlt/defekt ist, gelten Defaults; in jedem Korruptionsfall `runtime.diag.settingsCorrupted=true` und rotes Banner.
- `runtime.json`: `Runtime(grants: Map<pkg, UnlockGrant>, session: QuizSession?, episode: Episode?, emergency: EmergencyState(lastUsedElapsed, bootCount, lastUsedWall)?, diag: Diag)`. Freigabe, Session und Episode in EINER Datei (atomares `updateData`). `QuizSession(sessionId, targetPkg, deck, required, unlockMinutes, correct, answered, wrong, activeMs, lastWrongElapsed, startedElapsed, lastActiveElapsed, bootCount, startedWall, pending: PendingAnswer?)`. `PendingAnswer(answerId, noteId, ord, ease, takenMs, repsBefore: Int?)`. `Episode(pkg, openedElapsed, lastAttachElapsed, bootCount)`. `Diag(serviceLastConnectedWall, serviceLastUnboundWall, serviceErrorCount, launchFailedCount, settingsCorrupted)`.
- `stats.json`: `Stats(days: Map<Long /*instant.atZone(zone).toLocalDate().toEpochDay()*/, DayStats>)`, `DayStats(blocks, unlocks, freeUnlocks, emergencies, aborted, answered, correct, perApp: Map<pkg,Int>)`. Aufbewahrung 400 Tage, Pruning beim Schreiben. Tagesgrenze ist die lokale Mitternacht (bewusst abweichend vom Anki-Tageswechsel, Eintrag in decisions.md und Fussnote im Statistik-Code-Kommentar).
- Zeit: `TimeSource` (elapsedRealtimeMs, wallMs, bootCount aus `Settings.Global.BOOT_COUNT`, -1 bei Fehler, zone) wird injiziert.

### Statistik: Ereignis -> Zaehler (verbindlich, kein Widerspruch)

Episode = ein zusammenhaengender Sperr-Durchlauf eines Zielpakets. Episode oeffnet bei `attach(pkg)`, wenn keine offene Episode fuer pkg existiert ODER `now - episode.lastAttachElapsed >= BLOCK_DEBOUNCE_MS` (10 s) ODER bootCount anders. Jedes `attach` setzt `lastAttachElapsed`. `lastAttachElapsed` wird auch beim Verlassen der Sperre gesetzt (M5-T4).

| Ereignis | Wirkung |
|---|---|
| attach: neue Episode | `blocks+1` und `perApp[pkg]+1` (Tag = heute). War eine fruehere Episode noch offen (weder Unlock noch Cancel), schliesst sie als abgebrochen: `aborted+1` |
| attach innerhalb 10 s, gleiche Episode (z. B. onNewIntent, Resume, oder Zurueckkommen binnen 10 s nach dem Verlassen der Sperre) | keine Zaehlung |
| Sperre geht in den Hintergrund (Home, andere App) bei offener Episode | `lastAttachElapsed = now` (`RuntimeOps.leave`, M5-T4): die 10 s zaehlen ab dem Verlassen |
| Retarget auf anderes Paket | neue Episode fuer das neue Paket (`blocks+1`), alte Episode `aborted+1` |
| Cancel/Zurueck/Geste (ausser Unlocked) | Episode schliesst: `aborted+1` (nur wenn Episode offen) |
| Antwort gespeichert | `answered+1`; bei Ease>=3 `correct+1`; Session `activeMs += takenMs`; bei Ease<=2 `wrong+1`, `lastWrongElapsed` |
| Unlock (QUIZ, correct>=required) | `unlocks+1`, Episode schliesst ohne aborted |
| NothingDue-Grant | `freeUnlocks+1`, Episode schliesst ohne aborted |
| Notausgang | `emergencies+1`, Episode schliesst ohne aborted |

Invariante: je Episode genau ein `blocks+1` und hoechstens ein `aborted+1`, daher aborted <= blocks (Aggregator klemmt ueber 7 Tage `min(aborted, blocks)`).

### Abgeleitete Statistik (`StatsAggregator`, rein; "heute" injiziert)

- gesperrt heute = days[today].blocks; diese woche = Summe blocks der letzten 7 Tage inkl. heute; karten = Summe answered (7 Tage); richtig-quote = round-half-up(correct*100/answered) in Prozent, Format `86 %`, leer -> `-`.
- streak = aufeinanderfolgende Tage rueckwaerts ab heute mit unlocks+freeUnlocks >= 1 (Entscheidung: auch ein NothingDue-Grant haelt die Streak; zur Bestaetigung in openDecisions). Heute noch ohne Unlock bricht nicht, wenn gestern zaehlte. Intro zeigt die Streak ohne heute, A5 mit heute (13 statt 12 im Mockup).
- Balken = blocks der letzten 7 Tage (aeltester links, heute rechts), Wochentagskuerzel mo di mi do fr sa so, heute hervorgehoben. Hoehe = max(4 dp, Wert/Max x 90 dp) fuer Wert>0, 2 dp fuer 0; Max=0 -> alle 2 dp.
- Ranking = perApp ueber 7 Tage, Top 4, Balkenbreite relativ zum Maximum (Prozent), Label statt Paketname (Fallback Paketname). Leer: dim `noch nichts gesperrt`. Umsetzung (M5-T3): `ui/stats/StatsModel` bildet `StatsSummary` auf den Screen ab (Balkenhoehe aus dem Aggregator als `ChartBar.heightDp`), `StatsViewModel` liest `stats.json` live und bestimmt "heute" bei jedem Resume; die Galerie b3 nutzt denselben Weg (`StatsSeedScreen`).
- zeit gespart* = min(aborted,blocks) (7 Tage) x ASSUMED_MINUTES_SAVED_PER_ABORT (5); Formate: 0 -> `0 min`; unter 60 -> `~N min`; sonst `~Xh Ym` (z. B. `~3h 20m`). Fussnote `* geschätzt, nicht gemessen`.

Konstanten (`domain/quiz/Constants.kt`): REQUIRED_DEFAULT=5, REQUIRED_RANGE=1..20, DURATIONS_MIN=[5,15,30,60], DEFAULT_DURATION_MIN=15, SESSION_TTL_MS=600000, RATE_GUARD_MS=600, LAUNCH_ACK_MS=1500, RELAUNCH_MIN_MS=1500, REEVAL_TOLERANCE_MS=50, BREAKER_STARTS=6, BREAKER_WINDOW_MS=10000, BREAKER_COOLDOWN_MS=30000, BREAKER_MIN_INTERVAL_MS=3000, BLOCK_DEBOUNCE_MS=10000, WAIT_POLL_MS=15000, LEARN_PENDING_WAIT_MS=1200000, ANKI_FIRST_TIMEOUT_MS=8000 (nach S1a-8 per Tabelle), ANKI_TIMEOUT_MS=4000, SPINNER_DELAY_MS=250, GRANT_PLAUSIBILITY_SLACK_MS=1000, ASSUMED_MINUTES_SAVED_PER_ABORT=5, EMERGENCY_WAIT_MS=60000, EMERGENCY_GRANT_MIN=5, EMERGENCY_COOLDOWN_MS=21600000, PROTECTED_REFRESH_MS=60000.

### Write-ahead-Antwortpipeline (Idempotenz ohne Room)

1. `Rate(ease)`: ein `updateData` setzt `session.pending = PendingAnswer(newAnswerId, ref, ease, takenMs, repsBefore)`; `repsBefore` nur bei `capabilities.answerVerify`.
2. `answer(...)` ueber Provider (alle RuntimeExceptions -> AnkiError). Rueckgabewert von update() gilt nicht als Erfolg.
3. Verify (nur bei answerVerify): reps nach > repsBefore, sonst einmal erneut senden, dann Error(AnswerNotSaved) mit pending erhalten.
4. Commit: ein `updateData`: pending loeschen, Zaehler laut Stats-Tabelle (Session und Stats gemeinsam), bei correct>=required zusaetzlich Grant + Session loeschen + Episode schliessen. Erst danach UI weiterschalten.
Recovery (Attach mit pending != null): mit answerVerify: reps pruefen -> angewendet: Schritt 4; sonst erneut senden, dann Schritt 4. Ohne answerVerify: pending verwerfen, NICHT zaehlen, NICHT erneut senden (nie doppelt antworten). Folge fuer den Nutzer: im ungluecklichen Fall muss er die Karte erneut bewerten, und eine in Anki bereits angewendete Antwort bleibt ungezaehlt (dokumentierte Produktfolge). `AnkiCapabilities` ist injiziert, nicht global; Reducer/Engine-Tests laufen fuer answerVerify=true und false.

## 5. AnkiDroid-Integration

`CardSource` (Domain-Interface): `probe()`, `listDecks()`, `deckCounts(deckId)`, `nextCard(sel)`, `render(ref)`, `cardProgress(ref)` (nur bei answerVerify), `answer(ref, ease, takenMs)`. Alle `suspend`, `withContext(Dispatchers.IO)`, serialisiert ueber ein `Mutex` (die schedule-Abfrage mit deckID hat den Nebeneffekt Deck kurz auswaehlen, bei Exception nicht zurueckgesetzt), mit CancellationSignal und Timeout (ANKI_FIRST_TIMEOUT_MS beim ersten Aufruf, sonst ANKI_TIMEOUT_MS). Keine Abfrageschleifen. Zugriff auf AnkiDroid erfolgt nur nach [start] oder aus der Haupt-App (Setup/Config), nie im Intro.

Konstanten (Quelle: FlashCardsContract.kt @ Tag v2.25.3, Kopfzeile/Lizenz vor Uebernahme lesen, nichts 1:1 kopieren; Ease.kt und AddContentApi.kt NICHT kopieren): Authority `com.ichi2.anki.flashcards`, Permission `com.ichi2.anki.permission.READ_WRITE_DATABASE`, Pfade decks, decks/<id>, schedule, notes/<nid>/cards/<ord>; Spalten decks: deck_id, deck_name, deck_count (JSON [learn, review, new]), options, deck_dyn, deck_desc; schedule (Query selection `limit=?, deckID=?`, limit=1): note_id, ord, button_count, next_review_times, media_files; schedule (Update): note_id, ord, answer_ease, time_taken. Spalten fuer Frage/Antwort, Modell-CSS und reps legt Spike S1a fest (Datei+Zeile im KDoc). Ease: AGAIN=1, HARD=2, GOOD=3, EASY=4, counts = value>=3.

Deckauswahl: siehe Queue-Matrix. Die deckID-lose Abfrage wird nicht verwendet (S1b dokumentiert nur das Ergebnis). Dynamische Decks werden in der Einstellungsliste ausgeblendet. Das Deck Default (id 1, Name Default) wird ausgeblendet, wenn alle Counts 0 sind.

Fehlermodell `AnkiError` (reine, JVM-getestete Abbildung): NotInstalledOrApiOff (resolveContentProvider==null ODER query liefert null), PermissionMissing (SecurityException), StorageNotConfigured (IllegalStateException), Unreachable (IllegalArgumentException Unknown URI, Timeout, DeadObjectException), DeckMissing (decks/<id> leer), BadData (JSON/Spalte unerwartet), AnswerNotSaved, Unexpected(msg). Null-Cursor ist NIE nichts faellig. Fail-closed: bei JEDEM Fehler bleibt die Sperre zu. Jeder Fehlerscreen hat genau eine Hauptaktion plus [abbrechen -> Home]; zusaetzlich nach EMERGENCY_WAIT_MS Verweildauer den Notausgang (unten).

| Fehler | Titel | Text | Hauptaktion |
|---|---|---|---|
| NotInstalledOrApiOff | [ FEHLER ] ankidroid nicht erreichbar | nicht installiert, api aus oder kein speicherzugriff. (M4-T7: null-Cursor kommt auch bei AnkiDroid ohne Speicherzugriff, `anki-api.md`; Details nennt das Setup) | $ ./erneut (zusaetzlich $ ./ankidroid oeffnen nur wenn installiert) |
| PermissionMissing | [ FEHLER ] berechtigung fehlt | die app darf ankidroid nicht lesen. | $ ./app-info |
| StorageNotConfigured | [ FEHLER ] ankidroid nicht eingerichtet | ankidroid einmal öffnen und einrichten. | $ ./ankidroid oeffnen |
| Unreachable | [ FEHLER ] keine antwort von ankidroid | timeout oder ankidroid nicht bereit. | $ ./erneut |
| DeckMissing | [ FEHLER ] deck nicht gefunden | das gewählte deck existiert nicht mehr. | $ ./einstellungen |
| BadData/Unexpected | [ FEHLER ] unerwartete antwort | karte konnte nicht gelesen werden. | $ ./erneut |
| AnswerNotSaved | [ FEHLER ] antwort nicht gespeichert | die bewertung kam nicht an und wird nicht gezählt. | $ ./erneut |

Kein Ueberspringen und kein Begraben von Karten (veraendert den Lernplan).

Notausgang (gegen dauerhaften Aussperrung durch defekten Anki-Zustand oder Giftkarte; Entscheidung, zur Bestaetigung in openDecisions): Auf JEDEM Fehlerscreen erscheint nach EMERGENCY_WAIT_MS (60 s, sichtbarer Countdown auf dem Button) zusaetzlich `$ ./notausgang`. Wirkung: Grant kind=EMERGENCY fuer EMERGENCY_GRANT_MIN (5 min) fuer das Zielpaket, maximal einmal je EMERGENCY_COOLDOWN_MS (6 h; gleicher Boot: elapsed; nach Neustart: max(Laufzeit seit Boot, Wall-Clock seit Nutzung), ein Neustart beendet den Cooldown also nicht; im Cooldown Button sofort sichtbar, deaktiviert mit Restzeit `$ ./notausgang (in 4h 12m)`). Der 60-s-Countdown laeuft ab dem ersten Fehler seit der letzten Karte (`$ ./erneut` startet ihn nicht neu). Umsetzung (M4-T6): `EmergencyPolicy.remainingMs`/`button` -> `EmergencyButton`, `QuizEngine.emergencyButton()`, `QuizHost` fragt jede Sekunde ab. Zaehlt `emergencies+1`, A5-Variante Notausgang. Schaltbar per Konstante `EMERGENCY_EXIT_ENABLED` (Default true). Kein Notausgang im Waiting-Screen. Zusaetzlich: Karten-Rendering darf nie blockieren: schlaegt HTML-Aufbau oder WebView fehl, wird der Rohtext (HTML entfernt) im Klartext-Renderer angezeigt und bleibt bewertbar; nur wenn Frage UND Antwort leer sind, ist es BadData.

Rendering: `CardHtml.build(rendered, ord, isDark, showAnswer)`: ein Dokument; `<meta http-equiv=Content-Security-Policy content=default-src 'none'; style-src 'unsafe-inline'; img-src data:>` (Verteidigung in der Tiefe, Wirkung im Test belegen); entfernt `[[type:...]]` per Regex; `[sound:x]`/`[anki:play...]` -> `[ audio ]`; `<img src=x>` -> `[ bild: x ]` (danach ersetzt `MediaInliner` jeden Platzhalter, dessen Bild geholt wurde, durch `<img src="data:...">`, Abschnitt 5a); Default-CSS fuer `.cloze`; Body-Klassen `card cardN mobile android` (+ `nightMode`); Modell-CSS angehaengt (je Notiztyp gecacht, wenn S1a eine Quelle findet); Theme-Farben als CSS-Variablen, Monospace. Enthaelt answerHtml ein `<hr id=answer>`, zeigt A3 nur den Teil danach, sonst das ganze answerHtml (Fixture-Test aus S1). A3: Frage gedaempft (alpha), Trenner antwort, Antwort; A2: nur Frage. Aufdecken = neu geladenes Dokument (kein JavaScript). `WebViewCardRenderer`: JavaScript AUS, `blockNetworkLoads=true`, `allowFileAccess=false`, `allowContentAccess=false`, Link-Klicks verworfen, transparenter Hintergrund, sichtbar erst nach onPageFinished, Kartenrahmen mit fester Mindesthoehe. `RendererSelector` faellt bei jeder WebView-Exception auf `PlainTextCardRenderer` zurueck (HTML -> AnnotatedString, JetBrains Mono, 20 sp). `media_files` nur als Hinweis (Dateinamen); Karten mit Medien werden NICHT uebersprungen. Die WebView-Einstellungen bleiben unveraendert (kein Netz, keine Datei-Zugriffe, CSP `img-src data:`): Bilder gelangen nur als `data:`-URI in das Dokument. next_review_times nur anzeigen, wenn genau 4 nichtleere Strings parsebar sind, sonst Intervall-Label leer (Zeile ausgeblendet).

## 5a. Medien (Revision 3): Bilder vom eigenen Server

Entscheidung 2026-11-03 (decisions.md): Option S. Schnittstelle des laufenden Servers: `media-server.md` (GET `https://<host>/<prozent-kodierter-dateiname>`, `Authorization: Bearer <token>`, nur Bilder, Audio nicht im Umfang).

- **Konfiguration** in den Einstellungen (DataStore settings): `mediaBaseUrl` (https, ohne abschliessenden Pfad) und `mediaToken`. Leer = Funktion aus, Platzhalter wie bisher. Token nicht in der APK, nicht im Repo, nie in Logs (AppLog gibt nur Dateiname-Hash und Statuscode aus). Eingabe in der Config-Box `BILDER` (Textfeld URL, Passwortfeld Token, Button `$ ./test` = GET `/healthz` und Ergebnis als BsTag). Dieser UI-Block ist neu (nicht in den Mockups, Extra-Screen, Strings in die Whitelist). Kein Pflichtschritt im Setup, hoechstens ein optionaler Hinweis.
- **Ablauf je Karte:** `CardHtml.build` liefert Platzhalter plus die Liste der Bildnamen (`src`-Werte: HTML-Entities aufloesen, nur Namen ohne `/`, `:` und `..` akzeptieren, alles andere bleibt Platzhalter). Vor dem Rendern holt `MediaInliner` die Bilder parallel (max. 4 gleichzeitig, Gesamtfrist `MEDIA_TIMEOUT_MS` = 2500 ms je Karte, einzeln `MEDIA_MAX_BYTES` = 2 MiB, nur Content-Type image/jpeg|png|gif|webp). Erfolg: `data:`-URI im HTML. Fehler, Timeout, 401/404/429, kein Netz: Platzhalter `[ bild: x ]`. Die Karte bleibt immer bewertbar, die Sperre bleibt fail-closed (Mediafehler beeinflussen weder Freigabe noch Zaehlung).
- **Cache:** `cacheDir/media/<sha256(name)>` mit Sidecar fuer ETag/Zeit. 200 bis 24 h frisch; danach bedingte Anfrage (If-None-Match), 304 verlaengert. 404 wird nie auf Platte gespeichert (nur 60 s im Speicher gegen Hammering). Cache-Obergrenze 50 MB (aelteste zuerst loeschen).
- **Netz-Disziplin:** Intro greift weiterhin weder auf AnkiDroid noch auf das Netz zu. Der Abruf startet erst nach `[start]`; die naechste Karte holt ihre Bilder erst nach dem Laden der Karte (kein Prefetch ueber Kartengrenzen).
- **Datenschutz:** Es gehen nur Dateinamen und Token an den eigenen Server, nie Karteninhalt oder Antworten. Kein Redirect folgen (HttpURLConnection `instanceFollowRedirects=false`), nur https, nur der konfigurierte Host.
- **Voraussetzung ausserhalb der App:** AnkiDroid und Desktop syncen mit demselben Anki-Sync-Server (`media-server.md`), sonst fehlen Bilder (404 = noch nicht gesynct).
- **Tests:** JVM: `MediaInlinerTest` (Namen mit Leerzeichen/Entities/Sonderzeichen, Pfadangriffe, Fehlerzweige), `HttpMediaFetcherTest` mit lokalem Fake-Server im Test (200/304/401/404/429/Timeout/Redirect/zu gross/falscher Content-Type). Emulator: `scripts/spike/fake-media-server.py` (Bearer-Pruefung, Dateien aus Testordner) auf `10.0.2.2`, Screenshot einer Karte mit Bild und Platzhalter-Fall.

AnkiDroid-Version: Ziel und Test v2.25.3 (variant-abi-AnkiDroid-2.25.3-x86_64.apk, sha256 aus Release-API, apksigner-Fingerprint aus README). Antwortpfad haengt am Legacy path, used by tests (Scheduler.answerCard); Regression gegen 2.26-Alphas in M7. Ob Antworten im Revlog landen und syncen, misst S1a; ein negatives Ergebnis aendert die Architektur nicht (eigene Zaehler), muss aber in gotchas.md und Produktbeschreibung stehen.

## 6. Berechtigungs-Einstieg (Setup-Screen, Terminal-Stil)

`$ setup`; Zeilen mit BsTag `[ OK ]`/`[ OFFEN ]`/`[ FEHLER ]`, dim-Text, ein primaerer Button je offenem Schritt. Kein vierter Tab: beim Erststart (onboardingDismissed=false und Pflichtschritt offen) Startroute, danach ueber das Banner (Abschnitt 8) auf allen Tabs und ueber die ANKIDROID-Box in config. Status wird in onResume und im Sekundentakt bei RESUMED neu gelesen, zusaetzlich `AccessibilityManager` ServicesStateChangeListener. Der Haupt-Screen bleibt bei offenen Schritten bedienbar.
1. [Pflicht] AnkiDroid installiert (Ist-Stand M4-T5: Paket vorhanden und aktiviert, `ApplicationInfo.enabled`); Button `$ ./ankidroid installieren` oeffnet den Play Store. Die Installationsreihenfolge ist egal (gemessen M4-T5, gotchas.md 2026-11-09).
2. [Pflicht] AnkiDroid-Zugriff: Runtime-Permission per `ActivityResultContracts.RequestPermission`; bei dauerhafter Ablehnung (`shouldShowRequestPermissionRationale` false nach Ablehnung) `[ FEHLER ]` mit Button app-info.
3. [Pflicht] AnkiDroid bereit: Provider nicht aufloesbar -> `ankidroid-api aus` (Schaltername); sonst `probe()` + `listDecks()` aus der Haupt-App; Ergebnis `api aktiv · N decks gefunden` oder Fehlertext (null-Cursor bei aufloesbarem Provider = "liefert keine daten", sonst Text der Sperr-Fehlerscreens). Logik: `Protection.ankiSteps`, Zeilen: `ui/setup/SetupModel`.
4. [Pflicht] Bedienungshilfe: dreistufiger Status (nicht aktiviert / aktiviert aber nicht gebunden / aktiv; ComponentName-Vergleich ENABLED_ACCESSIBILITY_SERVICES vs. getEnabledAccessibilityServiceList), Button `ACTION_ACCESSIBILITY_SETTINGS`, Hinweis Eingeschraenkte Einstellungen zulassen (App-Info > drei Punkte) mit Button zur App-Info, Hinweis auf 24-h-Systemwarnung und moegliche Blockade durch Advanced Protection (unverifiziert, nur Text), Hinweis: nach Neustart des Dienstes ist eine bereits offene gesperrte App erst beim naechsten Fensterwechsel gesperrt.
5. [empfohlen] Akku: `PowerManager.isIgnoringBatteryOptimizations` als Statuszeile, Button zur Akku-Optimierungsliste (Intent-Action gegen Doku pruefen). Wirkung am Pixel nicht im Emulator belegbar -> test-checklist.md.
6. [Hinweis] auto-sync in ankidroid einschalten (ohne Status).
Overlay/SAW, UsageStats und Benachrichtigungen gibt es bewusst nicht (decisions.md: Abweichung von der Aufgabenliste begruenden). Das Setup-Design (kein vierter Tab, Banner nur bei offenem Pflichtschritt) ist neu und steht in openDecisions zur Bestaetigung.

## 7. Anti-Umgehung (Reibung gegen Impuls, keine Sicherheitsgrenze)

Verhindert/abgefangen: Zurueck/Predictive Back (BackHandler = Cancel -> HOME + finish); Home, Recents, App-Wechsel, Notification, Deeplink (jedes Wiedereintreten der gesperrten App erzeugt ein Event; excludeFromRecents, eigener Task); Freigabe-Ablauf bei offener App (Timer + Event + SCREEN_ON); Uhr verstellen (nur monotone Uhr + bootCount); Neustart (Grants ungueltig); Prozesstod (Zustand persistiert, Write-ahead); defekte Dateien (Spiegelkopie, Banner); Start blockiert (Launch-Ack, Retry, HOME); Start-Spam (Throttle+Breaker mit Re-Evaluate, nie Freigabe); Doppeltippen (Rate-Guard); AnkiDroid-Manipulation (fail-closed).

Selbstaussperrung vermeiden: `ProtectedPackages` (immer Allow, nicht waehlbar): eigenes Paket, com.ichi2.anki (sonst loescht sich das Setup und die Fehler-Aktion $ ./ankidroid oeffnen selbst), com.android.settings, com.android.systemui, android, Permission-Controller und Package-Installer (Pakete wie in Abschnitt 2), Notruf com.android.emergency, sowie dynamisch: aktuelle Home-Launcher (`PackageManager.queryIntentActivities(ACTION_MAIN+CATEGORY_HOME)`), aktivierte IMEs (`InputMethodManager.enabledInputMethodList`), Standard-Dialer (`TelecomManager.getDefaultDialerPackage()`, API-Nutzung gegen developer.android.com pruefen, unverifiziert). Aktualisierung der dynamischen Mengen: bei `onServiceConnected`, bei `ACTION_PACKAGE_ADDED/REMOVED/CHANGED` (dynamischer Receiver), per ContentObserver auf Settings.Secure ENABLED_INPUT_METHODS und DEFAULT_INPUT_METHOD, beim Oeffnen der Apps-Liste und vor jeder Lock-Entscheidung, wenn der Stand aelter als PROTECTED_REFRESH_MS ist. LockActivity hat immer einen Weg zu abbrechen (sichtbar auf A1, A2, Error, Waiting, Loading, A5 schliessen; auf A3/A4 laut Mockup ohne Button, dort Zurueck/Geste).

Erkannt und gemeldet, nicht verhindert (ehrlich, im Produkttext und decisions.md als Restrisiko): Dienst abschalten, Force-Stop (entfernt Dienst dauerhaft aus ENABLED_ACCESSIBILITY_SERVICES), App aus Liste nehmen, App deinstallieren, Advanced Protection, toter Dienst durch Akku-/Memory-Killer (Nutzer merkt es erst beim Oeffnen der Haupt-App; keine Benachrichtigungen, siehe openDecisions). Rotes Banner mit dreistufigem Status beim Oeffnen/onResume. Selbstheilung per WRITE_SECURE_SETTINGS ist NICHT im MVP. Split-Screen/PiP/Freeform der gesperrten App misst S2/M6 (Befund in gotchas.md; ist die gesperrte App danach in Vollbild-Nutzung laenger als 2 s ohne Lock-Reaktion = gravierend -> Plan-Korrektur an Opus). Overlay-Vorhang und WindowsResolver sind NICHT im MVP.

## 8. UI-Regeln (Texte, Layout, Semantik)

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
- Error: Tabelle in Abschnitt 5; Notausgang-Button `$ ./notausgang (45s)` mit Countdown, danach `$ ./notausgang`; im 6-h-Cooldown deaktiviert `$ ./notausgang (in 4h 12m)`.
- Banner (ueber allen Tabs unter dem Titel, tippbar -> Setup; nur bei Bedarf, im gesunden Zustand sind B1-B3 pixelgleich zu den Mockups):
  | ProtectionStatus | Text | Farbe |
  |---|---|---|
  | DEGRADED (Pflichtschritt 1-3 offen, Dienst aktiv) | `[ WARN ] setup unvollständig` | warn |
  | OFF nicht aktiviert | `[ FEHLER ] schutz inaktiv - bedienungshilfe aus` | fail |
  | OFF aktiviert aber nicht gebunden | `[ FEHLER ] schutz inaktiv - dienst nicht gebunden` | fail |
  | OFF blockiert (eingeschraenkte Einstellungen) | `[ FEHLER ] schutz inaktiv - einstellungen blockiert` | fail |
  | settingsCorrupted | `[ FEHLER ] einstellungen beschädigt - blockliste prüfen` | fail |
- Setup-Zeilen: `$ setup`; `[ OK ]|[ OFFEN ]|[ FEHLER ]` + Schritttext aus Abschnitt 6; Buttons `$ ./zugriff erlauben`, `$ ./app-info`, `$ ./bedienungshilfe`, `$ ./akku`.
- B1 (apps): Trenner exakt `gesperrt · N` und `alle apps` (Mittelpunkt wie im Mockup). Sortierung beider Abschnitte alphabetisch nach Label (Collator de). Gesperrte Apps erscheinen nicht unter alle apps. Suche filtert beide Abschnitte nach Teilstring im Label (case-insensitive). Leerzustand: `gesperrt · 0` + dim `noch keine app gesperrt` (nur ohne Suche); keine Treffer: dim `keine treffer` einmal unter `alle apps`, nur wenn beide Abschnitte leer sind (M5-T1). Protected-Pakete in der Blockliste werden nicht angezeigt (der Gate ignoriert sie ohnehin). Umsetzung: `data/system/PackageCatalog`, `ui/apps/AppsModel` (Regeln, JVM-getestet), `ui/apps/AppsViewModel` (Neuladen bei jedem Resume des Tabs). Nur Apps mit Launcher-Intent im eigenen Profil (Work/Private Space nicht erfasst, dokumentiert). Gesperrte, aber nicht mehr installierte Pakete erscheinen mit Paketname als Label (entsperrbar). Nicht waehlbar: ProtectedPackages inkl. com.ichi2.anki.
- B2 (config): Deckliste alphabetisch nach vollem Namen (alle nicht-dynamischen Decks inkl. Unterdecks mit vollem `::`-Namen wie im Mockup), `alle decks` als letzte Zeile; `N fällig` je Deck = learn+review+new, bei `alle decks` Summe der Top-Level-Decks (keine Doppelzaehlung). Anki nicht erreichbar -> Fehlerzeile statt Liste (`decks nicht lesbar ...`), Box ankidroid mit `[ FEHLER ] <Grund aus errorTexts>` und `$ ./setup`; gewaehltes Deck nicht mehr in der Liste -> rote Zeile `gewähltes deck fehlt: <name>`, kein Radio gewaehlt. Box `bilder` immer sichtbar (unter dem Mockup-Bereich), `$ ./test` = `MediaHealthCheck` (healthz ohne Token, dann Token-Probe auf eine nicht vorhandene Datei: 404 ok, 401/403 abgelehnt). Am Ende zwei dim Hinweise (Aenderungen ab naechster neuer Sperre; AnkiDroid waehlt das Deck beim Abfragen kurz aus). Umsetzung: `ui/config/ConfigModel` (Regeln, JVM-getestet), `ConfigViewModel` (Decks bei jedem Resume des Tabs, URL/Token mit 400 ms Verzoegerung gespeichert). Sperrfehler DeckMissing -> `$ ./einstellungen` oeffnet `MainActivity --es route config`.
- B3 (stats): Kacheln und Formate wie Abschnitt 4.

### Layout-Regeln

- Masse aus theme.css (BsDims enthaelt sie, per Test gegen `tools/mockup/extract-dims.mjs`-Ausgabe geprueft): scr-Padding 56/24/32, main-Padding 56/24/16, hdr min 36, btn 52, ghost 48, rate 84, row 60, kv 30, nav 56 (+16 unten), search/step/segs 48, chart 150, rk 34, ico 40 (lg 64), img-Platzhalter 96, switch 48x28.
- Insets: `enableEdgeToEdge(statusBarStyle=SystemBarStyle.auto(TRANSPARENT,TRANSPARENT), navigationBarStyle=gleich)` vor setContent. Oben `max(56 dp, statusBarInset + 24 dp)`, unten `max(Mockup-Wert, navigationBarInset + 8 dp)` (Mockup-Padding nie zusaetzlich zu Insets addieren). Suchfeld B1: `imePadding()`.
- Karten (A2/A3): Karteninhalt scrollt in seinem Rahmen (verticalScroll), Buttons und Rate-Buttons stehen fest unten. Alle anderen Screens: Inhalt in verticalScroll, Hauptbutton in einer festen unteren Zeile (auch bei fontScale 2.0 erreichbar).
- Tests bei fontScale 1.0, 1.3, 2.0: kein abgeschnittener Hauptbutton (UI-Test: Button ist `assertIsDisplayed` und hat Mindesthoehe 48 dp).

### Semantik und Barrierefreiheit

BsSwitch `toggleable(role=Role.Switch)`, BsRadioOption `selectable(role=Role.RadioButton)`, BsSegments als selectableGroup, Buttons mit Role.Button; Zeilen B1 als eine Touch-Flaeche (mergeDescendants, contentDescription `<app>, gesperrt|nicht gesperrt`). Fortschritt `contentDescription = c von N richtig`. BlinkCursor ist aus der Semantik ausgenommen. Alle Touch-Ziele >= 48 dp.

## 9. Debug-Hooks (nur src/debug, Praefix `dev.tinow.brainrotstopper.debug.action.`)

Aufruf: `adb shell am broadcast -n dev.tinow.brainrotstopper/.debug.DebugControlReceiver -a dev.tinow.brainrotstopper.debug.action.<NAME> [--es k v]`. Wirkung geht ueber `DebugFlagsImpl` (im Container) bzw. Repositories.

| Action | Extras | Wirkung |
|---|---|---|
| SET_BLOCKED | pkgs (csv), on (true/false) | Blockliste setzen/entfernen |
| GRANT_NOW | pkg, minutes (Default Einstellung) | Grant kind=QUIZ schreiben |
| RESET | what = all, grants, session, stats, settings | Zustand zuruecksetzen |
| DUMP_STATE | - | loggt eine Zeile `BSState {json}` (now-Snapshot, Blockliste, Grants inkl. remainingMs, Session, Episode, Diag, Flags) |
| SET_SOURCE | source = fake, real; scenario | CardSource umschalten; Fake-Szenario (normal, poison_card, counts_unreadable, timeout, empty, waiting, api_off) |
| SET_SHORT_DURATION | seconds | ueberschreibt Freigabedauer (Debug) |
| SET_TTL_MS | ms | ueberschreibt SESSION_TTL_MS |
| SIMULATE_LAUNCH_BLOCK | on | LockLauncher startet nicht (Test Watchdog -> HOME) |
| THROW_IN_HANDLER | on | Event-Handler wirft absichtlich (Test Catch-all) |
| SET_GATEWAY_DELAY_MS | stage = answer, query; ms | Verzoegerung im Gateway (pending-Test mit kill -9) |
| EVENT_FLOOD | pkg, n | erzeugt n synthetische Events fuer pkg |
| SET_WALL_OFFSET_MS | ms | verschiebt nur wallMs/Tagesgrenze (elapsed nie) |
| SEED_STATS | preset = week, empty | Statistik-Beispieldaten (Mockup-Werte) |
| FAKE_PERMISSIONS | state = off, unbound, restricted, ok, none | ersetzt den vom `PermissionStatusReader` gelesenen Berechtigungsstatus (M3-T4); none = echt |

## 10. Teststrategie

1. JVM-Unit-Tests (`testDebugUnitTest`, JUnit 4, coroutines-test `runTest`, Turbine, Truth, FakeTimeSource): GrantPolicy (Grenzen, Neustart, bootCount -1, now<grantedAt, Rest>duration+slack), GateEvaluator, GateStateMachine (jede Tabellenzeile aus 3A, Throttle- und Breaker-Wiederaufnahme, Ack/Retry/HOME), QuizReducer tabellengetrieben (jede Transition, jede Zeile der Queue-Matrix, nur 3/4 zaehlen, Fortschritt bleibt nach Nochmal, AnswerFailed zaehlt nicht, DeckMissing, Resume mit gueltiger Freigabe, Cancel behaelt Session, TTL, Zielwechsel, doppeltes Rate, Rate-Guard, veraltete Sequenz, Notausgang), Engine-Tests mit FakeCardSource und Prozess-Neustart-Simulation inkl. Abbruch an jedem Pipeline-Schritt, jeweils fuer answerVerify=true und false, Stats-Tabelle (aborted<=blocks), StatsAggregator/StreakCalculator (Luecken, Tageswechsel, DST ueber epochDay), AnkiContentProviderSource mit FakeProviderGateway, AnkiErrorMapper, CardHtml mit Fixtures aus S1 (kein externes src/href), Strings-Formatfunktionen (Plural, Zeit), BsColors/BsDims gegen Referenzdateien, JsonSerializer/Spiegelkopie/Korruption, ForegroundTracker, ProtectedPackages, DomainPurityTest.
2. Instrumentierte Tests (`connectedDebugAndroidTest`, AVD pixel9): DataStore-Repositories (defekte Datei -> Spiegelkopie, parallele updateData), Compose-UI-Tests aller Screens (hell/dunkel, fontScale 1.0/1.3/2.0) mit Fakes, Touch-Ziele >= 48 dp, WebView-CSP-Test (JS-aktivierte Test-WebView mit CardHtml-Fixture, die Script und externes Bild enthaelt: Script laeuft nicht, kein Netzzugriff; belegt die CSP), Contract-Test gegen echtes AnkiDroid (`assumeTrue(installiert)`).
3. Emulator-E2E (`scripts/e2e/*.sh`, Exit-Code): Assertions ueber `BSState`-Dump, `dumpsys activity activities`, logcat-Marker (`BS/Gate lock pkg=... reason=...`, `BS/Svc connected`), Screenshots; Eingaben per `adb shell input tap` auf `BS/UI`-Koordinaten. NIE `uiautomator dump` im Sperrmoment und NIE `am start -S`/`force-stop` auf das eigene Paket ohne Re-Enable. Nach jedem Install Dienststatus per logcat pruefen. UiAutomation: `getUiAutomation(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)` als erster Aufruf. Zweite Test-App `dummy2` fuer Retarget und App-Wechsel.
4. Visueller Abgleich (M1-T0/T6/M5-T5): Referenz-PNGs aus den Mockups (tools/mockup, Firefox headless) gegen Emulator-Screenshots im Mockup-Modus (`wm size 1236x2745`, `wm density 480` = 412x915 dp; Rueckstellung `wm size reset; wm density reset`; Machbarkeit in M1 pruefen). Schwellen: SSIM >= 0.90 ueber beide auf 412x915 skalierte Graustufenbilder, Warnbereich 0.90-0.95 wird einzeln geprueft; < 0.90 = Fehler. Ergaenzend (seit M5-T5) `tools/color-diff.py`: weichgezeichneter Farbvergleich je Ansicht, findet Farb-/Inhaltsfehler, die SSIM nicht sieht; Ergebnis und Abweichungsliste in mockups.md. Maschineller String-Diff gegen die Mockup-Texte (zero unexplained strings). Extra-Screens ohne Mockup (Setup, Error, Waiting, Loading, Banner, A4-hard, A5-Varianten, Intro-Fortsetzung): Screenshots hell/dunkel in design/screenshots/ plus Pruefliste gegen Textmatrix, kein SSIM.
5. Messmethoden: Zeiten ausschliesslich aus eigenen Log-Zeilen mit `SystemClock.uptimeMillis` (Event-Zeit `AccessibilityEvent.getEventTime`, `BSDummy resumed`, `BS/Svc lock-resumed`), zusaetzlich `ActivityTaskManager: Displayed` als Gegenprobe. Screenrecord/Screenshots im headless Emulator nur als Illustration, nie als Kriterium.
6. Definition of Done je Aufgabe: `scripts/check.sh` (assembleDebug + testDebugUnitTest + lintDebug) gruen, neue Logik hat JVM-Test, UI-Aufgabe hat Screenshot-Beleg und (wo Mockup existiert) SSIM-/String-Diff-Ergebnis, Annahmen in `.agents/` als (unverifiziert) oder durch Messung ersetzt, `.agents/` aktualisiert, kein Commit/Push ohne Aufforderung.
7. Nicht im Emulator pruefbar -> `.agents/test-checklist.md`: Pixel-Akkuverwaltung/Adaptive Battery/Standby-Buckets, echter AnkiWeb-Sync, echte Social-Apps und Splash-Flackern, Play-Protect/24-h-Warnung, Restricted-Settings-Dialog am Pixel, Android-17-MemoryLimiter, Advanced Protection, Latenz auf Hardware. Der Emulator laeuft mit Android 17 (API 37); Android 16 ist nicht testbar.

## 11. Spike-Ergebnis -> Entscheidung (mechanisch)

| Messung | Ergebnis | Setzen |
|---|---|---|
| S1a-5 reps (oder aequivalente Spalte) lesbar und steigt nach Antwort | ja | `answerVerify=true` |
| | nein | `answerVerify=false` (Recovery verwirft pending) |
| S1a-5 Revlog-Eintrag nach Antwort (ease passt) | ja | Doku-Satz: Antworten landen im Revlog |
| | nein | Architektur unveraendert, gotchas.md + Produkttext, Opus entscheidet ueber Provider-Antwortpfad |
| S1a-6 learn-Count enthaelt noch nicht faellige Intraday-Lernkarte | ja | `LEARN_COUNT_INCLUDES_PENDING=true` |
| | nein | `false` (Queue-Zeile 4a aktiv) |
| S1a-8 Kaltstart-Dauer p95 des ersten Zugriffs | gemessen | `ANKI_FIRST_TIMEOUT_MS = clamp(2 x p95 aufgerundet auf 1000, 8000, 20000)`; ANKI_TIMEOUT_MS=4000 bleibt, wenn warmer Zugriff p95 < 1500 ms, sonst 2 x p95 aufgerundet |
| S1a-4 Modell-CSS lesbar | ja/nein | `modelCssAvailable` |
| S1a-3 next_review_times parsebar (4 nichtleere Strings) | ja/nein | `intervalLabelsAvailable` |
| S1b-h resolveContentProvider/query bei deaktiviertem Provider | null oder Exception | Fehlertext NotInstalledOrApiOff mit beobachtetem Menuenamen; existiert kein Schalter, Text bleibt ohne Pfad |
| S2 BAL | 20/20 ohne Block | Weg bleibt; sonst STOPP/Opus |
| S2 Latenz Dummy->Lock | Median <= 500 ms und p95 <= 1000 ms | Weg bleibt; sonst Opus (Overlay-Vorhang) |
| S3 settings put wirksam | ja/nein | a11y-enable.sh Variante direkt bzw. UI-Klickpfad |

### Gemessene Werte aus M0 (2026-11-03, Emulator; Belege in gotchas.md)

| Konstante / Schalter | Wert | Messung |
|---|---|---|
| `answerVerify` | `true` | `reps` per Projektion lesbar und steigt nach Antwort (1 -> 2) |
| Antworten im Revlog | ja | je Antwort 1 Eintrag (ease passt, `usn=-1`, zum Sync vorgemerkt); Doku-Satz gilt, Sync zum echten Server in M7 pruefen |
| `LEARN_COUNT_INCLUDES_PENDING` | `true` | `deck_count` learn zaehlt auch Karten, die erst in 10 min faellig sind; die `schedule`-Query liefert sie innerhalb des Learn-Ahead (20 min) sofort |
| Kaltstart erste Provider-Abfrage | p95 ca. 184 ms (n=6, Emulator, kleine Sammlung) | `ANKI_FIRST_TIMEOUT_MS = 8000`, `ANKI_TIMEOUT_MS = 4000` (Untergrenzen der Formel); auf dem Geraet neu messen (M7) |
| `modelCssAvailable` | `true` | `models`-Query liefert `css` je Notiztyp (Zuordnung ueber `mid` der Notiz) |
| `intervalLabelsAvailable` | `true` | `next_review_times` = JSON-Array aus 4 Strings, **mit U+2068/U+2069 um Zahlen** (vor Anzeige entfernen) |
| Provider deaktiviert | `resolveContentProvider` = null, `query` = null-Cursor | Schalter in AnkiDroid: `providerEnabled`, Titel deutsch "AnkiDroid-API aktivieren" (Menuepfad im UI nicht abgeklickt, Kategorie "Erweitert" existiert); Fehlertext nennt nur den Schalternamen |
| Speicher nicht eingerichtet | `IllegalStateException: AnkiDroid storage is not configured` bei `decks`; `schedule` lieferte danach einen null-Cursor | StorageNotConfigured / NotInstalledOrApiOff je nach Quelle |
| Leeres/unbekanntes Deck | `schedule` liefert nicht-null Cursor mit 0 Zeilen | Queue-Matrix: leerer Cursor + Counts > 0 pruefen wie geplant |
| S2 BAL | 20/20 ohne Block, Latenz Median 65 ms, p95 295 ms | Weg bleibt (Direktstart aus dem Dienst) |
| S3 `settings put` | wirksam; Dienst bindet nach `install -r`, `kill -9` (ca. 2 s), Reboot | `a11y-enable.sh` direkte Variante |
| IGNORE-Tabelle | `com.android.systemui`, `android` | siehe gotchas.md (Protokoll) |

## Versionen und applicationId

Quelle: Synthese 2026-11-02 (`plan.json`, Feld `versions`). Verifizierungsstatus je Version steht in `research.md` (Topic Build-Toolchain).

| Eintrag | Wert |
|---|---|
| applicationId / namespace | `dev.tinow.brainrotstopper` (Test-Dummys: `dev.tinow.brainrotstopper.dummy`, `dev.tinow.brainrotstopper.dummy2`) |
| gradle | 9.8.2 (Wrapper, distributionSha256Sum 21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607; Fallback 9.7.1, neue Checksum von services.gradle.org holen). Verifiziert: services.gradle.org/versions/current. Unverifiziert: Zusammenspiel mit AGP 9.5.0 (kein Probe-Build) -> Spike M0-S0. |
| agp | 9.5.0 (stabil, 9.6 nur Alpha). Kein org.jetbrains.kotlin.android-Plugin (built-in Kotlin, unverifiziert, Build zeigt es), kein kapt, kein KSP, kein Room. buildFeatures.buildConfig bleibt aus. |
| kotlin | 2.4.21 (stabil). Plugins org.jetbrains.kotlin.plugin.compose 2.4.21 und org.jetbrains.kotlin.plugin.serialization 2.4.21. kotlin-gradle-plugin:2.4.21 zusaetzlich im buildscript-classpath pinnen und mit ./gradlew buildEnvironment pruefen (T07 umstritten). Entscheidungsregel S0: Build gruen + genau eine KGP-Version + keine Warnung der Art incompatible/unsupported = akzeptiert; reine 'not tested with'-Hinweise = akzeptiert und in gotchas.md protokolliert; Warnung mit Handlungsanweisung (Property/Flag) = Anweisung befolgen und protokollieren; Fehler = Fallback-Leiter: Gradle 9.7.1 -> Kotlin 2.4.10 -> aeltere stabile Compose-BOM; jede Stufe per Maven-Metadata belegen, keine Alphas. |
| composeBom | 2026.09.00 (Compose ui 1.12.1; verlangt compileSdk 37 und AGP >= 9.1.0, verifiziert). Weitere Libraries (alle stabil, verifiziert ausser Test-Libs): core-ktx 1.19.1, activity-compose 1.13.0, lifecycle-runtime-compose 2.11.0, datastore 1.3.0 (JSON-Serializer mit kotlinx-serialization, DataStore.Builder-API gegen 1.3.0-Doku pruefen), kotlinx-coroutines 1.11.0, kotlinx-serialization-json (neueste stabile zu Kotlin 2.4.21, in M0 per Maven-Metadata ermitteln), webkit 1.18.0 (nur falls benoetigt), junit 4.13.2, coroutines-test 1.11.0, turbine 1.2.1, truth 1.4.5. androidx.test/uiautomator/compose-ui-test: Versionen sind NICHT belegt -> in M1-T1 per Maven-Metadata ermitteln, nur stabile. |
| compileSdk | 37 (platforms;android-37.0 ist lokal installiert). DSL-Form (compileSdk = 37 vs. compileSdk { version = release(37) }) ist unverifiziert -> in M0-S0 gegen die AGP-9-Doku/Build pruefen. |
| targetSdk | 36 (bewusst nicht 37: BAL-Verschaerfungen, Task-Hijacking-Opt-in erst ab 37) |
| minSdk | 35 (Standardwert, deckt Android 15+ aus CLAUDE.md; java.time ohne Desugaring nutzbar) |
| jdk | JDK 17 fuer Gradle/AGP: /usr/lib/jvm/java-17-openjdk ist lokal installiert und enthaelt javac, jar, jlink -> KEINE Installation noetig. JAVA_HOME nur per scripts/gradlew-jdk17.sh setzen (Standard-java ist JDK 25, nicht verwenden; JRE 21 hat kein javac). Kein java.toolchain-Block, keine gradle-daemon-jvm.properties. Android Studio (nur zum Ansehen): Settings > Build Tools > Gradle > Gradle JDK manuell auf /usr/lib/jvm/java-17-openjdk stellen, sonst scheitert der Sync mit JDK 25. JDK 21 wird nur gebraucht, falls spaeter Robolectric eingefuehrt wird (NICHT im Plan; dann `sudo pacman -S jdk21-openjdk` durch den User). Lokal vorhanden: build-tools 36.0.0 und 37.0.0, platform-tools, emulator, system-image android-37.0 google_apis x86_64, AVD pixel9. Host-Tools: node (v24, nvm) und firefox (/usr/bin/firefox) fuer die Mockup-Referenz; ImageMagick/Pillow-Verfuegbarkeit wird in M1-T0 geprueft (sonst Installationsbedarf an den User melden, kein sudo). |
