# Stolperfallen

Format: `- YYYY-MM-DD – Problem → Lösung/Workaround`

- 2026-11-02 – Android beendet Hintergrunddienste teils aggressiv (Pixel-Akku-Optimierung) → für die App Akku-Optimierung ausschalten, Accessibility-Service robust gegen Neustarts bauen. (unverifiziert auf dem Gerät)
- 2026-11-02 – Sync mit Anki am PC läuft nur über AnkiDroid → AnkiWeb → Desktop-Anki, die App synchronisiert nichts selbst. Früh per Prototyp prüfen (alles unverifiziert): (1) landet eine per API beantwortete Karte im Revlog und damit in den PC-Statistiken? (2) kann die App einen AnkiDroid-Sync auslösen, oder synct AnkiDroid nur beim Öffnen/Schließen?
- 2026-11-02 – AnkiDroid-API muss in den AnkiDroid-Einstellungen aktiviert sein und die Berechtigung `com.ichi2.anki.permission.READ_WRITE_DATABASE` muss erteilt werden. (unverifiziert, vor der Umsetzung gegen die aktuelle AnkiDroid-Doku prüfen)
