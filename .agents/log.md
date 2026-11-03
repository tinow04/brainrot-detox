# Session-Log

Neueste oben. Pro Session 2–5 Zeilen: was wurde getan, was ist der Stand, was kommt als Nächstes.

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
