# Session-Log

Neueste oben. Pro Session 2–5 Zeilen: was wurde getan, was ist der Stand, was kommt als Nächstes.

## 2026-11-02
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
