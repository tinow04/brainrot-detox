# Medienserver (Option S): Schnittstelle fuer die App

Stand: 2026-11-03. Vom Server-Agenten gemeldet und gegen den laufenden Server getestet (200, 304, 206, 401, 405, Pruefsumme eines echten Bildes). Nicht getestet: 429 (kommt aus der Traefik-Konfiguration).

**Das Token steht nicht hier und nicht im Repo.** Es liegt nur beim User und wird in der App im Tab einstellungen (Box `bilder`) eingegeben (DataStore, nie geloggt; architecture.md 5a).

## Was es ist
- Nur-Lese-nginx ueber dem Medienordner des selbst gehosteten Anki-Sync-Servers. Dateiname = Anki-Medienname. Bestand: 204 Dateien (202 jpg, 2 png), ca. 12 MB.
- Keine API fuer Karten/Decks/Notizen, kein Verzeichnislisting. Die App muss den Dateinamen kennen: kommt aus dem Kartenfeld/-HTML (`<img src="...">`) bzw. `media_files` der AnkiDroid-API. Karteninhalte kommen weiter ueber AnkiDroid, der Server liefert nur Bilder.

## Endpunkt
`GET/HEAD https://anki-media.<basisdomain>/<prozent-kodierter-dateiname>` mit `Authorization: Bearer <MEDIA_TOKEN>`.
- Nur HTTPS (Let's Encrypt, HTTP/2). `GET /healthz` ohne Token -> 200.
- Token: zufaellig erzeugt, A-Za-z0-9, nur im Header (in Query/Pfad -> 401).
- Dateiname: aus `src="..."`, HTML-Entities aufloesen (`&amp;` -> `&`), UTF-8-prozentkodieren, Leerzeichen `%20` (nicht `+`). Kotlin: `Uri.encode(name)`. Getestet: `Screenshot 2025-02-10 at 19.11.02.png`.

## Antworten
200 Datei · 206 Range · 304 ETag/If-Modified-Since · 400 ungueltiger Pfad · 401 Token fehlt/falsch · 404 Datei (noch) nicht da · 405 nicht GET/HEAD · 429 Rate-Limit (20 req/s im Schnitt, Burst 100, pro IP).
Header bei 200: `Cache-Control: private, max-age=86400`, ETag, Last-Modified, Accept-Ranges, nosniff.

## Regeln fuer die App
- 404 = noch nicht synchronisiert (neue Bilder erst nach Sync in Anki/AnkiDroid): **nicht dauerhaft cachen**, Platzhalter zeigen.
- 200 bis 24 h cachen, danach per ETag revalidieren (OkHttp-Cache). Hoechstens 4-8 parallele Downloads.
- Nichts schreiben, nichts unter `anki.<basisdomain>/sync/...` aufrufen (gehoert Anki).
- Schnelltest: `curl -sSI https://anki-media.<basisdomain>/healthz`.

## Umsetzung (Stand 2026-11-11)
Umgesetzt in M4-T3b, Details in `architecture.md` Abschnitt 5a: `INTERNET` in der Allowlist, Release ohne Cleartext, Abruf per `HttpURLConnection` (kein OkHttp) nur zum konfigurierten Host ohne Redirects, Bilder vorab geladen und in die WebView ohne Netz/JS eingebettet, 404/Netzfehler -> Platzhalter `[ bild: x ]`, Karte bleibt bewertbar. Voraussetzung bleibt: AnkiDroid synct mit **demselben** Server wie der Desktop.
