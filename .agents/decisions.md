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
