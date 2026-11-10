# AnkiDroid-API: was die App nutzt und was belegt ist

Stand: 2026-11-09 (M4-T7). Gemessen gegen **AnkiDroid 2.25.3** (`full`-Build aus GitHub, x86_64) im Emulator (Pixel 9, API 37).
Die Belege stehen ausführlich in `gotchas.md`; diese Datei bündelt sie als Nachschlagewerk.
Status: **belegt** = am Emulator gemessen. **Quelle** = nur im AnkiDroid-Code gelesen (Tag v2.25.3). **offen** = noch nicht geprüft.

## Rolle von AnkiDroid

AnkiDroid hält die Sammlung lokal, plant die Karten und synct mit dem eigenen Sync-Server. Die App liest über den
Content-Provider Decks, Zähler und Karten und gibt die Bewertung zurück. AnkiDroid führt die Planung weiter, als hätte
man in AnkiDroid selbst gelernt. Bilder holt die App vom eigenen Medienserver (`media-server.md`), weil die API keine
Mediendateien liefert.

## Zugang

| Punkt | Status | Fakt |
|---|---|---|
| Authority | belegt | `com.ichi2.anki.flashcards` (nicht der Paketname) |
| Sichtbarkeit | belegt | `<queries><package android:name="com.ichi2.anki"/></queries>` nötig, sonst kein Provider ab SDK 30 |
| Berechtigung | belegt | `com.ichi2.anki.permission.READ_WRITE_DATABASE`, dangerous. Ohne sie wirft `query` `SecurityException`. Erteilt über den Systemdialog oder `pm grant`. Nach zwei Ablehnungen erscheint der Dialog nicht mehr, dann bleibt nur App-Info |
| Installationsreihenfolge | belegt (M4-T5) | **egal**: AnkiDroid nach unserer App installiert, der Dialog erscheint und erteilt die Berechtigung. Die frühere Regel „AnkiDroid zuerst installieren“ ist widerlegt |
| „API muss aktiviert werden“ | belegt (M0-S1b-c, M4-T5) | **teilweise richtig.** Der Schalter „AnkiDroid-API aktivieren“ (`providerEnabled`) ist ab Werk **an**. Schaltet man ihn aus, verschwindet die Provider-Komponente: `resolveContentProvider` liefert null, `query` einen null-Cursor. Bei einer normalen Installation muss niemand etwas aktivieren; der Schalter erklärt nur den Fehlerfall |
| Gradle-Abhängigkeit | Quelle | keine: das API-Modul liegt nicht auf Maven Central, die Konstanten stehen in `data/anki/AnkiContract` |

## Genutzte URIs und Spalten (`data/anki/AnkiContract`)

| URI | Zweck | Status |
|---|---|---|
| `decks` (`deck_id`, `deck_name`, `deck_count`, `deck_dyn`) | Deckliste, Zähler | belegt: `deck_count` = JSON `[learn, review, new]` inkl. Unterdecks (für „alle decks“ nur Top-Level summieren); `deck_dyn` ist der **Text** `true`/`false` |
| `schedule`, Selection `limit=?,deckID=?` | nächste fällige Karte (`note_id`, `ord`, `button_count`, `next_review_times`, `media_files`) | belegt. Leeres oder unbekanntes Deck = leerer, nicht-null Cursor. Quelle: jede Abfrage mit `deckID` ändert kurz die Deckauswahl in AnkiDroid, deshalb Mutex, keine Schleifen, kein Zugriff im Intro |
| `notes/<nid>/cards/<ord>` (`question`, `answer`, `answer_pure`; `reps` nur mit Projektion) | Karteninhalt, Fortschritt | belegt: `answer` enthält die Frage plus `<hr id=answer>` (Cloze ohne `<hr>`), `data-cloze` verrät die Lösung (wird entfernt), Medien stehen roh als `<img src>` / `[sound:]` |
| `notes/<nid>` (`mid`), `models/<mid>` (`css`) | Notiztyp-CSS | belegt, best effort (Fehler = ohne CSS) |
| `update` auf `schedule` (`note_id`, `ord`, `answer_ease`, `time_taken`) | Bewertung zurückgeben | belegt, siehe unten |

## Antworten

- **belegt (M0-S1a, M4-T7 E2E-2):** Jede Bewertung erzeugt genau einen `revlog`-Eintrag mit derselben Ease (1 = nochmal, 3 = gut) und `time` = `time_taken` in ms. `cards.reps` steigt um 1, `usn = -1` heißt: vorgemerkt für den Sync. E2E-2 hat 7 Antworten gegeben (nochmal, gut, nochmal, gut ×4). Ergebnis: revlog +7 mit den Eases `1 3 1 3 3 3 3` und Zeiten von 1,7 bis 3,2 s. Antworten der App zählen also in der Statistik von AnkiDroid.
- **Quelle:** Die Rückgabe von `update` beweist keinen Erfolg (Fehler werden intern geloggt, gezählt wird trotzdem). Deshalb prüft die App den Erfolg über `reps` (answerVerify), und eine Antwort ist erst nach dem Commit in `runtime.json` gezählt (Write-ahead, `architecture.md` 4).
- **offen (M7):** ob der Sync die Antworten wirklich zum eigenen Server hochlädt. Das geht nur am echten Gerät mit dem Server.

## Fehlerbilder (was der Client sieht -> `AnkiError` -> Anzeige)

| Zustand von AnkiDroid | Client sieht | `AnkiError` | Sperre | Setup | Status |
|---|---|---|---|---|---|
| nicht installiert / Paket deaktiviert (`pm disable-user`) | Provider nicht auflösbar, null-Cursor | NotInstalledOrApiOff | „ankidroid nicht erreichbar“: „nicht installiert, api aus oder kein speicherzugriff.“ | Schritt 1 offen | belegt (M4-T5, E2E-2) |
| API-Schalter aus | Provider nicht auflösbar, null-Cursor | NotInstalledOrApiOff | wie oben | „ankidroid-api aus“ | belegt |
| kein Speicherzugriff (`MANAGE_EXTERNAL_STORAGE` entzogen) | Provider löst auf, **null-Cursor** (intern `StorageAccessException`) | NotInstalledOrApiOff | wie oben | `NoData`: „ankidroid liefert keine daten …“ | belegt (M4-T5, E2E-2) |
| frisch installiert, nie eingerichtet | `IllegalStateException: AnkiDroid storage is not configured` | StorageNotConfigured | „ankidroid nicht eingerichtet“ | Fehlertext + `$ ./ankidroid oeffnen` | belegt |
| Berechtigung fehlt | `SecurityException` | PermissionMissing | „berechtigung fehlt“, `$ ./app-info` | Schritt 2 offen | belegt (E2E-2) |
| Timeout, Provider gestorben, `Unknown URI` | Timeout / `DeadObjectException` / `IllegalArgumentException` | Unreachable | „keine antwort von ankidroid“ | Fehlertext + `$ ./erneut` | Quelle, Timeout per Fake |
| gewähltes Deck gelöscht | `decks/<id>` leer | DeckMissing | „deck nicht gefunden“ | - | Unit-Test |

Ein null-Cursor heißt **nie** „nichts fällig“. Ob nichts fällig ist, entscheidet nur ein leerer, nicht-null Cursor
zusammen mit `deck_count` (Queue-Matrix, `architecture.md` 3B). Auf jedem Fehlerscreen bleibt die Sperre zu, nach 60 s
gibt es den Notausgang (M4-T6).

## Zeitverhalten

- belegt (M0-S1a): Der Kaltstart des Providers (AnkiDroid per `force-stop` beendet) braucht 49 bis 184 ms, warm 1 bis 11 ms (kleine Sammlung im Emulator). Die Fristen `ANKI_FIRST_TIMEOUT_MS` (8 s) und `ANKI_TIMEOUT_MS` (4 s) lassen viel Luft.
- belegt (E2E-2): Das Intro startet den AnkiDroid-Prozess nicht. Nach `force-stop` läuft bis `[start]` kein AnkiDroid-Prozess, danach schon.

## Prüfen

- **Contract-Test** `app/src/androidTest/.../data/AnkiSourceContractTest` liest Decks, holt eine Karte, rendert sie und beantwortet sie (mit Prüfung über `reps`). Er wird übersprungen, wenn AnkiDroid fehlt, die Berechtigung fehlt oder keine Karte fällig ist. Aufruf siehe `gotchas.md` (2026-11-04): `connectedDebugAndroidTest` erteilt die Berechtigung nicht, deshalb `pm grant` und `am instrument`.
- **E2E-2** `scripts/e2e/e2e2_quiz.sh` ist der ganze Sperrablauf gegen das echte AnkiDroid (siehe Skriptkopf). Es seedet Karten, wenn weniger als 10 fällig sind (`scripts/spike/seed-basic.sh`), und verändert den Lernstand der Emulator-Sammlung.
- `scripts/revlog.sh [n]` zeigt die letzten revlog-Einträge (nur lesend, braucht `adb root`).

## Offen

- Sync der per API gegebenen Antworten zum eigenen Server (M7, echtes Gerät).
- Play-Store-Build statt `full`: Die Sammlung liegt dann app-privat. Das Provider-Verhalten sollte gleich sein, der Pfad in `revlog.sh` nicht.
- Parallelzugriff, während der Reviewer von AnkiDroid offen ist (nicht gemessen; der Mutex schützt nur unsere eigenen Aufrufe).
