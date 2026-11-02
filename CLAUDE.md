# Brainrot Stopper

Android-App (Pixel 9), komplett per Vibe-Coding gebaut. Wenn eine ausgewählte App (z. B. Instagram, TikTok, YouTube) geöffnet wird, blockiert ein Overlay die App, bis 5 Karteikarten richtig beantwortet wurden. Die Karten kommen aus AnkiDroid.

## Wichtigste Regeln für Agents

1. **Zuerst `.agents/INDEX.md` lesen**, nicht das ganze Repo durchsuchen. Dort steht, was es gibt und wo es liegt.
2. **Nach jeder relevanten Änderung `.agents/` aktualisieren** (siehe `.agents/README.md`). Das gilt für Entscheidungen, Architekturänderungen, neue Dateien, Stolperfallen und offene Aufgaben.
3. Kommunikation mit dem User auf Deutsch. Code, Bezeichner und Commit-Messages auf Englisch.
4. Kein Commit/Push ohne ausdrückliche Aufforderung.

## Projektziel

- App-Erkennung: Wechsel in eine gesperrte App wird erkannt (`AccessibilityService` und/oder `UsageStatsManager`).
- Sperre: Fullscreen-Overlay bzw. eigene Activity über der gesperrten App.
- Abfrage: Eigene Karteikarten-UI, Karten und Scheduling kommen über die **AnkiDroid-API** (Content Provider, `ReviewInfo`). Antworten werden an Anki zurückgegeben, sodass der Lernfortschritt normal weiterläuft.
- Freigabe: Nach 5 richtig beantworteten Karten (Good/Easy) wird die gesperrte App für einen Zeitraum freigegeben.

## Tech-Stack (Stand: Start)

- Kotlin, Jetpack Compose, Gradle (Kotlin DSL)
- Ziel: Android 15+ (Pixel 9), Installation per APK/USB-Debugging, kein Play Store
- Abhängigkeit: AnkiDroid muss installiert sein, API in den AnkiDroid-Einstellungen aktiviert

## Bekannte Risiken

- Hintergrunddienst kann durch Akku-Optimierung beendet werden (Pixel: Optimierung für die App ausschalten).
- AnkiDroid-API legt die Review-Historie nicht offen, deshalb zählt die App die richtigen Antworten selbst.
- Karten-Rendering (HTML, Bilder, Audio) braucht eine WebView und ist aufwendiger als reiner Text.

## Struktur

Siehe `.agents/INDEX.md` (wird laufend gepflegt). Hier bewusst keine Dateiliste, damit es nicht veraltet.
