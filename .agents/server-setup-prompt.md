# Prompt: Anki-Sync-Server + Medienserver auf dem eigenen Server aufsetzen

Stand: 2026-11-03. Zweck: Option S aus `todo.md` (Medien/Bilder der Karten ueber den eigenen Server). Der Block unter der Linie wird auf dem **Server** in einer Claude-Code-Sitzung eingefuegt (nicht auf dem Entwicklungsrechner). Danach den Abschlussbericht hierher bringen, damit wir entscheiden koennen, ob Server (S) oder lokaler Zugriff (A).

Recherchegrundlage (2026-11-03, Details in `todo.md`):
- Eingebauter Sync-Server von Anki, Doku: https://docs.ankiweb.net/sync-server.html. Es gibt **kein offizielles Image**, nur ein Beispiel-Dockerfile (`docs/syncserver/` in ankitects/anki, baut per `cargo install` aus dem Tag) oder die pip-Variante (`pip install anki`, Start `python -m anki.syncserver`).
- Env-Variablen (Quelle `rslib/src/sync/http_server/mod.rs` und Doku): `SYNC_USER1=name:passwort` (Pflicht, weitere `SYNC_USER2`...), `SYNC_HOST` (Standard 0.0.0.0), `SYNC_PORT` (8080), `SYNC_BASE` (Datenordner), `SYNC_IP_HEADER` (Quelle der Client-IP, Standard ConnectInfo), `PASSWORDS_HASHED`, `MAX_SYNC_PAYLOAD_MEGS`. Der Server spricht nur HTTP.
- Datenlayout: `SYNC_BASE/<nutzername>/collection.anki2`, `media.db`, `media/<dateiname>` (Quelle: `user.rs`, `media_manager/mod.rs`, `upload.rs` auf main).
- Versionen: AnkiDroid 2.25.3 bringt Backend `0.1.68-anki26.05` mit. Sync-Protokoll in 26.05 und 26.09.3 identisch (`SYNC_VERSION_MIN=8`, `MAX=11`). Aktuelle Anki-Version 26.09.3, PyPI `anki 26.9.3` hat Wheels `cp310-abi3-manylinux_2_35` fuer x86_64 und aarch64 (also Debian/Ubuntu-Basis, **kein Alpine/musl**).
- AnkiDroid 2.25.3 hat in den Einstellungen "Custom sync server" mit "Sync URL" und "Custom root certificate (PEM)" (Play-Store-Version eingeschlossen).

---

Du richtest auf diesem Server einen selbst gehosteten **Anki-Sync-Server** (Docker) und einen kleinen **Nur-Lese-Medienserver** fuer die Bilder der Karten ein. Beides laeuft hinter dem hier bereits vorhandenen **Traefik**. Arbeite selbststaendig. Frage mich nur, wenn eine Information nicht durch Nachsehen im System zu klaeren ist.

## Hintergrund (damit du die Entscheidungen verstehst)
- Desktop-Anki und AnkiDroid (Play-Store-Version, Anki-Backend 26.05) sollen mit diesem Server statt mit AnkiWeb synchronisieren.
- Der Sync-Server legt pro Nutzer unter `SYNC_BASE/<nutzername>/` ab: `collection.anki2`, `media.db` und den Ordner `media/` mit den Karten-Bildern als normale Dateien (Dateiname = Anki-Medienname).
- Eine Android-App (eigene App, nicht Anki) soll die Bilder zu einer Karte per `GET https://<medien-host>/<url-kodierter-dateiname>` mit Header `Authorization: Bearer <token>` holen. Dafuer brauche ich einen kleinen, schreibgeschuetzten HTTP-Server ueber `SYNC_BASE/<nutzername>/media/`. Mehr nicht.

## Festgelegte Entscheidungen
1. **Sync-Server per pip** in einem eigenen Image: Basis `python:3.12-slim-bookworm`, `pip install --no-cache-dir anki==26.9.3`, Start `python -m anki.syncserver`. Nicht Alpine. Fallback, falls das nicht startet: das offizielle Beispiel-Dockerfile aus `ankitects/anki` (`docs/syncserver/Dockerfile`, `--build-arg ANKI_VERSION=26.09.3`, baut aus dem Quelltext, braucht viel RAM und Zeit).
2. Daten in einem **benannten Docker-Volume** `anki_data` (Mountpunkt `/anki_data`, `SYNC_BASE=/anki_data`). Container laeuft **nicht als root** (eigener User, feste UID/GID, die zur Volume-Besitzerschaft passt).
3. **Zwei Container** in einer Compose-Datei: `anki-sync` (Port 8080 intern) und `anki-media` (nginx, Port 80 intern, Volume `anki_data` nur lesend, Dokumentroot `/data/<nutzername>/media`). **Keine Ports auf dem Host veroeffentlichen**, nur ueber Traefik erreichbar.
4. **Zwei Hostnamen** per Traefik mit HTTPS (Zertifikat ueber den schon vorhandenen Resolver): `anki.<basisdomain>` fuer den Sync-Server und `anki-media.<basisdomain>` fuer die Medien. Wenn du die Basisdomain und gewuenschte Namen nicht aus der bestehenden Traefik-Konfiguration ableiten kannst, frage mich nach genau diesen beiden Hostnamen.
5. Sync-Nutzername: `anki` (kein Sonderzeichen; der Name ist auch der Ordnername). Passwort und Medien-Token **zufaellig erzeugen** (`openssl rand -base64 36 | tr -d '/+='`), in `.env` (chmod 600) ablegen, **erst im Abschlussbericht** anzeigen.

## Schritte

### 1. Bestandsaufnahme (nur lesen, nichts veraendern)
- `docker version`, `docker compose version`, CPU-Architektur (`uname -m`), Linux-Distribution, freier Platz.
- Traefik finden (`docker ps`, `docker inspect`, Compose-Dateien/Labels/statische Config): Version (v2 oder v3), Name des HTTPS-Entrypoints, Name des Certificate-Resolvers, verwendetes Docker-Netzwerk (`providers.docker.network`), ob `exposedByDefault=false` ist, wie bestehende Dienste ihre Labels schreiben, vorhandene Middlewares (z. B. rate limit), `respondingTimeouts` der Entrypoints.
- Wo liegen die anderen Compose-Stacks (Konvention fuer Verzeichnis, `.env`, Backups)? Lege den neuen Stack nach diesem Muster an, sonst unter `/opt/anki-sync`.
- Pruefe, ob die beiden Hostnamen per DNS auf diesen Server zeigen (`dig +short`). Wenn nicht, sage mir genau, welche Eintraege ich setzen muss, und fahre fort.
- Fasse die Ergebnisse in wenigen Zeilen zusammen, bevor du etwas anlegst.

### 2. Dateien anlegen (neues Verzeichnis, nichts Bestehendes anfassen)
- `Dockerfile` fuer `anki-sync` (siehe Entscheidung 1). Healthcheck: das `--healthcheck`-Flag gibt es nur im Rust-Binary `anki-sync-server`, **nicht** sicher in `python -m anki.syncserver`. Pruefe, ob es funktioniert, sonst nimm einen einfachen TCP-Check auf Port 8080 per Python.
- `docker-compose.yml` mit beiden Diensten, `restart: unless-stopped`, Traefik-Labels im Stil der vorhandenen Dienste, Anbindung nur an das Traefik-Netzwerk (plus ein internes Netzwerk, falls noetig). Container-Namen `anki-sync` und `anki-media`. Ressourcen sparsam (z. B. `mem_limit` 512m fuer den Sync-Server, 64m fuer nginx), `read_only: true` und `cap_drop: [ALL]` wo es funktioniert, `no-new-privileges`.
- Sync-Server-Umgebung: `SYNC_USER1=anki:<passwort>`, `SYNC_HOST=0.0.0.0`, `SYNC_PORT=8080`, `SYNC_BASE=/anki_data`. Hinter Traefik sehen alle Anfragen sonst wie eine einzige Client-IP aus: pruefe im Quelltext (`ankitects/anki`, `rslib/src/sync/http_server/`, `ClientIpSource`), welche Werte `SYNC_IP_HEADER` annimmt, und setze ihn passend zu `X-Forwarded-For` bzw. dem, was Traefik sendet, **nur wenn du den Wert belegen kannst**. Sonst weglassen und im Bericht erwaehnen.
- `anki-media` (`nginx:alpine`, Konfiguration ueber das Template-Verzeichnis oder eine gemountete Datei): nur `GET` und `HEAD`, `autoindex off`, kein Directory-Listing, Antwort **401**, wenn `Authorization` nicht exakt `Bearer <token>` ist, **404** bei fehlender Datei, `try_files $uri =404`, `X-Content-Type-Options: nosniff`, `Cache-Control: private, max-age=86400`, ein Endpunkt `/healthz`, der ohne Token `200` liefert. Schreib das Token nur ueber die Umgebung in die Konfiguration, nicht fest in eine Datei, die ins Repo gehen koennte. Teste, dass `..`-Pfade und doppelt kodierte Pfade nicht aus dem Medienordner herausfuehren.
- Traefik-Middleware **Rate-Limit** fuer beide Router (z. B. 20 Anfragen/s im Durchschnitt, Burst 100), HTTP wird auf HTTPS umgeleitet. Die Medien-URL darf kein Token im Pfad oder in der Query haben.
- `.env` (chmod 600) mit Passwort und Token. `.gitignore` falls der Stack in einem Git-Repo liegt. Ein kurzes `README.md` im Stack-Ordner mit Befehlen fuer Start, Stopp, Logs, Update und Backup.

### 3. Starten und pruefen (nichts als "funktioniert" melden, was du nicht getestet hast)
- Bauen und starten, `docker compose ps`, Healthchecks gruen, Logs ohne Fehler.
- `curl -sSI https://anki.<basisdomain>/` und `https://anki-media.<basisdomain>/healthz` von aussen (und vom Server selbst), Zertifikat gueltig, HTTP→HTTPS-Umleitung.
- Medienserver: ohne Token `401`, mit Token und nicht vorhandener Datei `404`, `POST` → `405`, Pfad-Traversal-Versuche → nicht `200`. **Schreibe keine Dateien in den Datenordner des Sync-Servers** (Doku: Daten nur per Sync dorthin, keine manuellen Kopien). Einen echten Dateiabruf pruefen wir spaeter nach dem ersten Sync.
- Existiert `/anki_data/anki/` mit `media/` nach dem Start (Sync-Server legt sie beim Start an)? Ist sie fuer den nginx-Container lesbar?
- Pruefe das Verhalten bei groesseren Anfragen hinter Traefik: Der Sync-Server hat standardmaessig ein Limit von 100 MB pro Anfrage (`MAX_SYNC_PAYLOAD_MEGS`). Die Doku empfiehlt bei Caddy `read_buffer 512k`, fuer Traefik habe ich dazu nichts Belegtes: pruefe, ob Traefik Anfragekoerper puffert oder begrenzt (Middleware `buffering`, `respondingTimeouts.readTimeout`), und **aendere keine globale Entrypoint-Einstellung**, sondern melde mir den Befund.

### 4. Betrieb
- Frage mich, ob ein taegliches Backup des Volumes `anki_data` eingerichtet werden soll (Vorschlag: `tar` in ein Backup-Verzeichnis, 7 Tage behalten, per systemd-Timer oder Cron, passend zur Konvention auf diesem Server). Richte es nur nach meiner Antwort ein.
- Schreibe in die README: Update-Ablauf (neue `anki`-Version in der Dockerfile aendern, neu bauen, Reihenfolge **erst Server, dann Clients**, weil neuere Clients ein neueres Protokoll brauchen koennen) und Rollback.

## Regeln
- **Bestehende Container, Netzwerke, Volumes und die Traefik-Konfiguration nicht aendern, stoppen oder neu starten.** Nur lesen. Neue Container duerfen ueber Labels vom Docker-Provider erkannt werden.
- Keine Ports am Host oeffnen, keine Firewall-Regeln aendern, kein `sudo`, wenn es nicht noetig ist. Bei zerstoererischen oder unklaren Aktionen vorher fragen.
- Secrets nur in `.env` (chmod 600), nie in Logs, Commit-Nachrichten oder Zwischenausgaben. Im Abschlussbericht darfst du sie einmal ausgeben.
- Nichts installieren, was ausserhalb des Docker-Builds liegt, ausser ich stimme zu.
- Wenn etwas scheitert: Fehlermeldung wiedergeben, nicht umgehen, nicht raten.

## Abschlussbericht (kurz, auf Deutsch)
1. Was laeuft: Container, Hostnamen, Pfade der Dateien, Version von Traefik und `anki`.
2. **Zugangsdaten**: Sync-URL (mit abschliessendem Schraegstrich), Nutzername, Passwort; Medien-URL und Token.
3. Welche Pruefungen bestanden/nicht bestanden/nicht moeglich waren (jede einzeln, mit Befehl und Ergebnis in einer Zeile).
4. Offene Punkte und Unsicherheiten (z. B. `SYNC_IP_HEADER`, Healthcheck-Variante, Traefik-Timeouts, DNS).
5. So aendere ich die Clients, **bitte erst ausfuehren, wenn ich es sage**: nichts an Anki/AnkiDroid veraendern.

---

## Danach (nicht Teil des Prompts, Umstieg der Clients)

Reihenfolge, damit nichts verloren geht (alles ungetestet, bitte beim Umstieg so vorgehen):
1. In Desktop-Anki und AnkiDroid noch einmal mit **AnkiWeb** synchronisieren, damit AnkiWeb den vollen Stand hat. Zusaetzlich Backup: Desktop `Datei > Exportieren > Anki-Sammlungspaket (.colpkg)` mit Medien.
2. Desktop-Anki: Einstellungen > Synchronisation > "Self-hosted sync server" auf `https://anki.<basisdomain>/` setzen, Nutzer `anki` und das Passwort eintragen, synchronisieren und "Hochladen" waehlen (Vollsync zum Server, auch Medien).
3. AnkiDroid: Einstellungen > Synchronisation > "Custom sync server" > "Sync URL" `https://anki.<basisdomain>/`, Nutzer/Passwort, synchronisieren, "Vom Server herunterladen" waehlen. Bei einem nicht oeffentlich vertrauten Zertifikat zusaetzlich "Custom root certificate (PEM)".
4. Pruefen: `docker exec anki-media ls /data/anki/media | head`, dann ein Bild per `curl -H "Authorization: Bearer <token>" https://anki-media.<basisdomain>/<dateiname>`.

Bekannte Einschraenkung: Die Anki-Doku beschreibt AnkiDroid nur bis Version 2.16 genauer. Menuenamen und das Verhalten des Vollsyncs bei 2.25.3 sind nicht von mir getestet.
