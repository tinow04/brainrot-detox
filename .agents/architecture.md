# Architektur

Geplant, noch nicht implementiert. Nach der Umsetzung hier an den echten Stand anpassen.

## Komponenten (geplant)

1. **AppWatcher** – erkennt, welche App im Vordergrund ist (`AccessibilityService` und/oder `UsageStatsManager`)
2. **BlockedAppsStore** – Liste der gesperrten Apps und Einstellungen (z. B. DataStore)
3. **QuizOverlay** – Fullscreen-Overlay/Activity mit Karteikarten-UI (Compose, WebView fürs Karten-HTML)
4. **AnkiRepository** – Zugriff auf die AnkiDroid-API: fällige Karten holen, Antwort zurückgeben
5. **UnlockManager** – zählt richtige Antworten, gibt die App nach 5 richtigen für einen Zeitraum frei

## Datenfluss (geplant)

Gesperrte App öffnet sich → AppWatcher meldet → UnlockManager prüft, ob freigegeben → falls nein: QuizOverlay → AnkiRepository liefert Karte → User antwortet → Antwort an Anki → nach 5 richtigen: Freigabe, Overlay schließt.
