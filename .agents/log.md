# Session-Log

Neueste oben. Pro Session 2–5 Zeilen: was wurde getan, was ist der Stand, was kommt als Nächstes.

## 2026-11-02
- Preflight (teilweise): Git-Repo initialisiert (`main`, origin `tinow04/brainrot-detox`, lokal nur committet, nicht gepusht), `.gitignore` angelegt. Android cmdline-tools (Build 15859902, SHA-256 geprueft, von dl.google.com) liegen in `~/Android/Sdk/cmdline-tools/latest`.
- Preflight BLOCKIERT: SDK-Lizenzen (`sdkmanager --licenses`) wurden vom Permission-System abgelehnt, daher noch keine SDK-Pakete, kein AVD, kein Boot-Test. Geplante Pakete laut `sdkmanager --list`: `platforms;android-37.0`, `build-tools;37.0.0`, `system-images;android-37.0;google_apis;x86_64`, `platform-tools`, `emulator` (API 37.0 als stabil angenommen, unverifiziert; 37.2 hat nur ps16k-Images).
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
