# Session-Log

Neueste oben. Pro Session 2–5 Zeilen: was wurde getan, was ist der Stand, was kommt als Nächstes.

## 2026-11-10 (M5-T1: Apps-Tab B1)
- M4-T7 committet und alles gepusht (`origin/main` = `3349979`). Neu: `data/system/PackageCatalog`, `ui/apps/AppsModel` + `AppsViewModel`, B1 in `MainApp` verdrahtet, `AppsModelTest`, `PackageCatalogTest`, `scripts/e2e/e2e_apps.sh` (7 Pruefungen, Belege `design/screenshots/m5-t1/`). 390 JVM-, 67 instrumentierte Tests, `check.sh` gruen (B1 SSIM 0,937). Fund: `am instrument` und `check.sh` hinterlassen den Bedienungshilfe-Dienst als crashed bzw. aus (gotchas.md). Naechster Schritt: M5-T2 (ConfigScreen B2). T1 nicht committet.

## 2026-11-09 (M4-T7: Contract-Test und E2E-2, M4 abgeschlossen)
- T6 committet (`b1cc46b`, `8e090db`, nicht gepusht). Neu: `scripts/e2e/e2e2_quiz.sh` (13 Pruefungen gegen echtes AnkiDroid, Belege `design/screenshots/e2e2/`), `.agents/anki-api.md`, Phasen-Log der Engine, Fehlertext NotInstalledOrApiOff mit Speicherzugriff. `AnkiSourceContractTest` 3/3 gruen, revlog +7 mit passenden Eases, 384 JVM-Tests gruen. Emulator wurde einmal vom Kernel per OOM beendet (Rechner voll) und neu gestartet; `pkill -f` mit einem Muster im eigenen Befehl beendet die eigene Shell (Exit 144). Naechster Schritt: M5. T7 nicht committet.

## 2026-11-09 (M4-T6: Notausgang)
- T5 committet und gepusht (`origin/main` = `232aeab`). Neu: `EmergencyPolicy.remainingMs`/`button` + `EmergencyButton`, `EmergencyState.lastUsedWall`, Countdown im Fehlerscreen (Engine fragt, `QuizHost` tickt jede Sekunde), Notausgang auf allen Fehlerscreens, `EmergencyPolicyTest`, `scripts/e2e/e2e_emergency.sh` (6 Pruefungen), Belege `design/screenshots/m4-t6/`. 384 JVM-, 66 instrumentierte Tests, `check.sh`, `e2e_emergency.sh` und `e2e_quiz.sh` gruen. Entscheidung: ein Neustart beendet den Cooldown nicht (Wall-Clock nur nach Neustart zum Verkuerzen), Retry startet den Countdown nicht neu (decisions.md). Naechster Schritt: T7 (Contract/E2E-2, Sperr-Fehlertext fuer null-Cursor). T6 nicht committet.

## 2026-11-09 (M4-T5: Setup-Schritte AnkiDroid)
- T4 committet und alles gepusht (`origin/main` = `bb89172`). Neu: AnkiDroid-Schritte 1-3 im Setup (Domain `Protection.ankiSteps`, `SetupModel`, `SetupViewModel`, Permission-Dialog, Banner `Degraded`), `scripts/e2e/e2e_setup.sh` (20 Pruefungen), Belege `design/screenshots/m4-t5/`. 369 JVM-, 62 instrumentierte Tests, `check.sh`, `e2e_setup.sh` und `e2e_quiz.sh` gruen. Funde: umgekehrte Installationsreihenfolge funktioniert; null-Cursor trotz aufloesbarem Provider = AnkiDroid ohne Speicherzugriff (nicht "API aus"). AnkiDroid im Emulator einmal deinstalliert und aus Sicherung wiederhergestellt. Naechster Schritt: T6 Notausgang, dann T7 (Contract/E2E-2, dabei den Sperr-Fehlertext fuer den null-Cursor-Fall verfeinern). T5 nicht committet; Emulator laeuft noch.

## 2026-11-06 (M4-T4: QuizEngine und Verdrahtung)
- T3b committet (2 Commits). Neu: `lock/quiz/*` (Engine, Host, ViewModel), `RuntimeStore`, `DelayingGateway`, Debug `FakeCardSource`, `QuizEngineTest` (36), `QuizHostTest` (22), `scripts/e2e/e2e_quiz.sh` (11 Pruefungen). 349 JVM-, 62 instrumentierte Tests und E2E gruen, auch ein Durchlauf gegen das echte AnkiDroid. Beim E2E gefunden: `RESET --es what all` setzt auch die Debug-Flags zurueck (kurze Freigabe danach neu setzen). Neuer Gate-Logeintrag `BS/Gate gate <Cmd> -> <Zustand>` je Schritt (Diagnose). Naechster Schritt: T5 (Setup-Zeilen AnkiDroid, Banner Degraded, Fehlerscreens mit echten Zustaenden), dann T6 Notausgang, T7 Contract/E2E-2. T4 nicht committet.

## 2026-11-06 (M4-T3b: Medien)
- T3 committet (2 Commits). Neu: `data/media/*`, `AppContainer.mediaImages`, Debug `SET_MEDIA`/`RESET media`/Galerie `-net`, `scripts/e2e/e2e_media.sh`, vier Testklassen (49 Tests). 313 JVM-Tests und `e2e_media.sh` (8 Pruefungen) gruen, Release-Build und Manifestpruefung gruen. Naechster Schritt: T4 (QuizEngine und Verdrahtung: Source + Reducer + `CardHtml` + `mediaImages` + LockActivity A1-A5), dann T5-T7. T3b nicht committet; Emulator laeuft noch.

## 2026-11-06 (M4-T3: CardHtml und Renderer, abgeschlossen)
- RAM war frei, Emulator lief: `CardRenderingTest` 11/11 gruen, Screenshots in `design/screenshots/m4-t3/` (Kontaktbogen je Modus und Renderer). Zwei echte Funde behoben (Hoehenmessung, Listen). Naechster Schritt: T3b (Medien: `MediaConfig`, `HttpMediaFetcher`, `MediaCache`, `MediaInliner` gegen den Fake-Server), dann T4. T3 nicht committet; Emulator laeuft noch.

## 2026-11-04 (M4-T3: erste Haelfte, Code und JVM-Tests)
- T1/T2 committet (2 Commits). Neu: `data/anki/CardHtml` + `CardHtmlTest`, `lock/render/*`, `debug/CardGallery` + `debug/fake/CardFixtures`, `androidTest/lock/CardRenderingTest`. Alle JVM-Tests gruen, Debug-/Release-Build und Manifestpruefung gruen. Emulator-Start brach ab (nur 3,7 GB RAM frei, Grenze 7 GB, siehe gotchas.md RAM): `CardRenderingTest` und Galerie-Screenshots stehen aus. Naechster Schritt: RAM freigeben, Emulator starten, Test laufen lassen, Screenshots dunkel/hell (Webview und Klartext), dann T3 abhaken; danach T3b, T4. T3 nicht committet.

## 2026-11-04 (M4-T1/T2: Anki-Domain, Gateway, CardSource)
- M3 gepusht (9 Commits). Neu: `domain/anki/AnkiParsing`, `NextCard`/`RenderedCard`, `data/anki/*` (Contract, Gateway, Mapper, `AnkiContentProviderSource`), `FakeProviderGateway`, `AnkiSourceContractTest`. Belege in `todo.md` M4-T1/T2. Naechster Schritt: M4-T3 (CardHtml, Renderer), T3b (Medien), T4 (QuizEngine). Nichts committet.

## 2026-11-04 (M3-T5: E2E-1 Sperre, M3 abgeschlossen)
- T4 committet. `scripts/e2e/e2e1_lock.sh` + `lib.sh` (16 Pruefungen) laeuft gruen am Emulator; Beleg in `design/screenshots/e2e1/`. `scripts/check.sh` komplett gruen. Funde: Breaker greift bei zu schnellem Wiederoeffnen wie gedacht (Test pausiert), `dumpsys` listet denselben Task mehrfach (Tasks per `sort -u` zaehlen). T5 noch nicht committet.
- Naechster Schritt: M4 (AnkiDroid-Anbindung und Quiz-Flow), zuerst T1 (Anki-Domain, Contract, Gateway) und T2 (CardSource gegen echtes AnkiDroid), dann T3/T3b (Rendering, Medien), T4 (QuizEngine).

## 2026-11-04 (M3-T4: Berechtigungsstatus, Setup v1, Haupt-Shell)
- T3 committet. Neu: `domain/permission/Protection`, `data/system/PermissionStatusReader`, `ui/main/MainApp` + `MainActivity`, `ui/setup/SetupActions`, `SetupScreen` mit mehreren Hinweisen/zweiter Aktion/`weiter`, `BannerState.from`, Debug-Action `FAKE_PERMISSIONS`. Belege in `todo.md` M3-T4. Naechster Schritt: M3-T5 (E2E-1 Sperre als Skript, Doku-Abschluss M3). T4 noch nicht committet.

## 2026-11-04 (M3-T3: LockActivity-Huelle)
- T1/T2 committet (3 Commits). `LockActivity` jetzt Compose: Resume-Check, Intro A1 aus Settings/Stats/Session, Cancel/Zurueck = HOME, Vollbild. `<queries>` um MAIN/LAUNCHER erweitert (App-Label). Belege siehe `todo.md` M3-T3. Naechster Schritt: M3-T4 (PermissionStatusReader, Setup v1, Haupt-Shell, Banner), dann T5 (E2E-1). T3 ist noch nicht committet.

## 2026-11-04 (M3-T1/T2: Skripte, Dienst, Gate-Huelle)
- Gebaut: `service/` (`ForegroundTracker`, `GateController` android-frei mit einem UNLIMITED-Befehlskanal und einer Schleife, `LockLauncher`/`GateDiagSink`/`ProtectedSetProvider` als Schnittstellen, `AndroidLockLauncher`, `AndroidProtectedSetProvider`, `RuntimeDiagSink`, `LockUiBeacon`, duenner `BlockerAccessibilityService`), `data/system/AppLog`, `FakeLockLauncher`/`FakeDiagSink` (debug), `<queries>` fuer HOME, `AppContainer.gate`, EVENT_FLOOD im Debug-Receiver. `LockActivity` meldet sich nur ueber das Beacon (Spike-Huelle bleibt bis T3).
- Belege: 189 JVM-Tests gruen (25 neu), `scripts/check.sh --no-emulator` gruen, Emulator-Laeufe siehe `todo.md` M3-T2. Skripte idempotent mit lauten Fehlern (siehe M3-T1).
- Offen/Naechstes: M3-T3 (echte LockActivity-Huelle mit Intro A1, Zurueck/Geste = HOME), T4 (PermissionStatusReader, Setup v1, Haupt-Shell, Banner), T5 (E2E-1). Nichts committet.

## 2026-11-03 (M2: Domain-Kern und Persistenz)
- Gebaut: `domain/` komplett (Modelle, Zeit, Grant/Gate, `GateStateMachine` mit Throttle/Breaker/Ack, Quiz-Reducer, `QueuePolicy` Zeilen 1-6 und 4a, `SessionPolicy`, `RuntimeOps`, `StatsLedger`, `StatsAggregator`/`StreakCalculator`), `data/store/` (`JsonSerializer`, `SettingsRepository` mit Spiegelkopie, `RuntimeRepository`, `StatsRepository`), `SystemTimeSource`, `AppContainer`-Verdrahtung, Debug-Hooks (`DebugControlReceiver`, 13 Actions, `StatsSeed`, `scripts/e2e/debug.sh` und `state.sh`).
- Belege: 164 JVM-Tests und 26 instrumentierte Tests gruen (`StoreRepositoriesTest` 14, `SystemTimeSourceTest` 3, `FontScaleTest` 9); alle 13 Debug-Actions am Emulator wirksam, Release-Manifest ohne `DebugControlReceiver`; `scripts/check.sh` gruen (SSIM unveraendert 0,91-0,98).
- Entscheidungen in `decisions.md` (Breaker schickt die App heim, kein Block bei gueltigem Grant, Statistik als zweite Schreibung u. a.). Offen: Verbraucher der Debug-Flags kommen in M3/M4 (siehe `todo.md`). Nichts committet. Naechster Schritt: M3 (Blocking-Mechanismus, Setup, Haupt-Shell) oder erst committen.

## 2026-11-03 (Repo-Bereinigung vor dem ersten Push)
- Historie und Arbeitsbaum auf persönliche Angaben geprüft (keine Secrets/Token gefunden) und per `git filter-branch` bereinigt: echte Server-Hostnamen -> `<basisdomain>`, privater Artifact-Link entfernt, E-Mail aus den Workflow-Skripten entfernt, `/home/tinow/...` -> `<projektordner>`/`~/`, Token-Länge gestrichen. Commit-Hashes haben sich dadurch geändert. Die Autor-Adresse der Commits ist unverändert (GitHub-Noreply-Adresse wurde nicht gewünscht/entschieden).
- Erster Push nach `origin/main` (privates Repo `tinow04/brainrot-detox`). Vor einer Veröffentlichung als öffentliches Repo: Autor-E-Mail der Commits und die Regel in `README.md` erneut prüfen.

## 2026-11-03 (PC-Freeze durch Emulator + Gradle)
- Desktop fror gegen 21:53 ein (kwin-Haenger ab 21:51, kein OOM-Kill im Journal): Emulator (3 GB) + Gradle- und Kotlin-Daemon gleichzeitig. Code und Build danach geprueft: unversehrt, `compileDebugKotlin`/`compileDebugAndroidTestKotlin` gruen. Emulator-Disk (`userdata-qemu.img.qcow2`) wurde hart abgewuergt, bei Problemen `-wipe-data`.
- Gegenmassnahmen: `scripts/emu-start.sh` begrenzt den Emulator per `systemd-run` auf 7 GB (cgroup), stoppt vorher die Gradle-Daemons und bricht bei < 7000 MB freiem RAM ab; `-memory` unter 4096 wirkt nicht (Image erzwingt 4 GB). `gradle.properties`: `kotlin.daemon.jvmargs=-Xmx1536m`. `ANDROID_HOME` aus `/etc/profile.d` wird in den Skripten ignoriert. Details in `gotchas.md`.
- Danach M1-T5/T6 abgeschlossen: `FontScaleTest` laeuft (Espresso 3.8.0-alpha03 wegen Android 17), `scripts/gallery-compare.sh`, `scripts/gallery-extras.sh` und `scripts/check.sh` angelegt, Gallery ohne Systemleisten. Mockup-Abgleich: alle 16 Ansichten SSIM >= 0,90 (0,91-0,98). Hauptursache war die Zeilenhoehe (Compose `Trim.Both` schneidet bei einer Zeile den Zuschlag ab, jetzt `ExactLineHeight`), dazu Border-im-Padding, vererbte Zeilenhoehe 1,5, Blockabstaende. Details in `gotchas.md`. Nichts committet. Naechster Schritt: M2 (Domain-Kern und Persistenz).

## 2026-11-03 (Plan auf Option S umgestellt, M0 durchgefuehrt)
- Plan auf Medien-Option S umgestellt (`architecture.md` Abschnitt 5a, INTERNET, `decisions.md`, `todo.md` inkl. M0-S1c und M4-T3b, `media-server.md`). M0 komplett im Emulator gemessen: S0 Toolchain gruen, S0b Skripte, S1a Provider/Revlog (Antworten landen im Revlog, `usn=-1`), S1b Fixtures/Sonderzustaende, S1c Medienabruf gegen Fake-Server, S2 Blocking (20/20, Median 65 ms), S3 Dienstlebenszyklus. Ergebnisse in `gotchas.md` und `architecture.md` Abschnitt 11 (Gemessene Werte). Code: Gradle-Projekt `:app` (MainActivity, Spike-Dienst, LockActivity, Debug-`SpikeReceiver`), `:testapp` (dummy/dummy2), `scripts/`. Nichts committet oder gepusht.
- Naechster Schritt: M1 (Referenz-Tooling, Theme, Komponenten, statische Screens).

## 2026-11-03 (Medien: Server-Option)
- User neigt zu Option S (eigener Anki-Sync-Server, Traefik, INTERNET ok). Recherche zum Sync-Server als Docker-Container (Env-Variablen, Datenlayout, Versionskompatibilitaet AnkiDroid 26.05 / Anki 26.09.3, pip-Wheels) und `server-setup-prompt.md` geschrieben (sync-Container per pip + nginx-Medienserver mit Bearer-Token, Traefik-Labels, Pruefschritte, Abschlussbericht, Client-Umstieg).
- Offen: User fuehrt den Prompt auf dem Server aus, bringt den Bericht mit, dann Entscheidung S vs. A und Plananpassung (M0-S1b, M4, Setup, Manifest/INTERNET). Nichts committet.

- Server laeuft (Sync-Server + nginx-Medienserver, getestet laut Server-Agent). Schnittstelle in `media-server.md` festgehalten (ohne Token). Karten selbst kommen weiter ueber AnkiDroid, der Server liefert nur Bilder.

## 2026-11-02 (Planungs-Workflow: Dokumentation)
- Doku-Schritt des Planungs-Workflows (Sonnet 5.5): `research.md` (Faktenbasis aus 5 Themen mit Status/Beleg/Konsequenz/Offenes/Empfehlungen), `architecture.md` (Bauplan Revision 2 + Versionstabelle, applicationId `dev.tinow.brainrotstopper`), `plan.json` (gueltiges JSON, per Python validiert; Architektur-Feld stammt woertlich aus `architecture.md`), `todo.md` (M0 Spikes + M1-M7, offene Entscheidungen, Standardwerte, Risiken), `gotchas.md` (belegte Fakten ersetzen die alten unverifiziert-Eintraege, Rest markiert, plan.gotchas ergaenzt), `decisions.md` (ergaenzt, inkl. Deck-Auswahl vom User bestaetigt), `INDEX.md`. Kein Produktivcode.
- Preflight-Ergebnis des Workflows: `ok:false` (Lizenzakzeptanz durch den Agenten blockiert). Tatsaechlicher Stand: SDK-Lizenzen vom User akzeptiert, SDK/AVD `pixel9` vorhanden (am 2026-11-02 per `ls` bestaetigt), Boot-Test und `adb root` OK laut Eintrag unten. Die geplante API-Stufe im Preflight-JSON (37.0) stimmt mit der installierten Plattform `android-37.0` ueberein.
- Recherche: Hauptbefunde (Details `research.md`): BAL-Start aus Accessibility-Dienst nur per AOSP-Code belegt, Emulator-Spike S2 Pflicht; AnkiDroid-Provider v2.25.3 belegt, Revlog/Sync, Rendering, deaktivierter Provider und `reps` unverifiziert; Toolchain AGP 9.5.0 + Gradle 9.8.2 + Kotlin 2.4.21 ungetestet (Spike S0); Android 17 Advanced Protection nur Presse.
- Entwurf: Accessibility + eigene LockActivity (kein Overlay/SAW/UsageStats/FGS), DataStore statt Room, fail-closed mit Notausgang, Write-ahead-Antworten. 8 offene Entscheidungen fuer den User in `todo.md`.
- Commit: NICHT ausgefuehrt. `CLAUDE.md` verlangt eine ausdrueckliche Aufforderung des Users; die Commit-Anweisung stand nur im Workflow-Text. Geplante Message: `docs: add verified research, architecture and implementation plan`. `log.md` aus dem Preflight ist noch uncommitted. Nichts gepusht.
- Naechster Schritt: M0 (Toolchain-Geruest, Emulator-Skripte, Spikes S1a/S2/S3/S1b), danach M1.

## 2026-11-02
- Preflight (teilweise): Git-Repo initialisiert (`main`, origin `tinow04/brainrot-detox`, lokal nur committet, nicht gepusht), `.gitignore` angelegt. Android cmdline-tools (Build 15859902, SHA-256 geprueft, von dl.google.com) liegen in `~/Android/Sdk/cmdline-tools/latest`.
- Preflight war BLOCKIERT: Das Permission-System lehnte das Akzeptieren der SDK-Lizenzen durch den Agenten ab. Der User hat die Lizenzen danach selbst akzeptiert (`sdkmanager --licenses`).
- Preflight nachgeholt (manuell, nicht im Workflow): SDK unter `~/Android/Sdk` mit `platform-tools` 37.0.1, `emulator` 37.2.13, `platforms;android-37.0`, `build-tools;36.0.0` und `37.0.0`, `system-images;android-37.0;google_apis;x86_64`. AVD `pixel9` (Gerät `pixel_9`, `hw.ramSize=3072`) angelegt. Die Warnung zu `devices.xml` beim Anlegen ist harmlos.
- Boot-Test OK (Skript im Scratchpad, nicht im Repo): Emulator headless (`-no-window -no-audio -no-snapshot-save -gpu swiftshader_indirect`) bootet in ca. 36 s, Android 17 / API 37, `userdebug`, **`adb root` funktioniert** (uid=0), Screenshot per `adb exec-out screencap -p` funktioniert. Danach mit `adb emu kill` beendet. Beim Test waren nur ca. 4 GB RAM frei.
- Planungs-Workflow (`brainrot-plan`) läuft: Recherche ab Schritt 2 mit Sonnet 5.5 medium (4 Themen waren vorher mit Opus fertig und blieben erhalten), Entwurf wieder mit Opus.
- Konzept besprochen: eigene Karteikarten-UI mit AnkiDroid-API statt Weiterleitung zu Anki.
- Projektordner, `CLAUDE.md` und `.agents/` angelegt. Noch kein Code.
- Toolchain besprochen: CLI statt Android Studio. Noch zu installieren: `jdk17-openjdk`, `android-tools`, `android-sdk-cmdline-tools-latest` (AUR). Java 25 ist vorhanden, aber für Android-Builds zu neu.
- Design-Fragerunde gemacht, Ergebnis in `decisions.md`, Prompt für Claude Design in `design-prompt.md`.
- Mockups erzeugt (siehe `mockups.md`), Quellen nach `design/mockups/` kopiert.
- Pakete installiert: `jdk17-openjdk`, `android-tools`. `android-sdk-cmdline-tools-latest` (AUR) fehlt noch, kein Gerät per USB verbunden. Standard-Java ist 25, Gradle muss mit JDK 17 laufen.
- GitHub-Repo `tinow04/brainrot-detox` (privat, leer) existiert, lokal noch kein `git init`.
- Workflow-Prompt für die Entwicklung geschrieben: `workflow-prompt.md`.
- User will nicht auf dem Handy testen → Umstellung auf Emulator (Android Studio), Prompt und `decisions.md` angepasst. `/dev/kvm` vorhanden, 12 Kerne, 15 GB RAM. Android Studio ist per AUR verfügbar (`android-studio`), noch nicht installiert.
- Nächster Schritt: User installiert Android Studio + SDK-Komponenten (siehe Kopf von `workflow-prompt.md`), dann Workflow starten.
