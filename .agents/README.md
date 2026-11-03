# .agents – Gedächtnis für Agents

Dieser Ordner hält Wissen aus vergangenen Chats fest, damit kein Agent das Repo jedes Mal neu erkunden muss.

## Ablauf

**Beim Start einer Session:**
1. `CLAUDE.md` lesen
2. `INDEX.md` lesen
3. Nur die Dateien lesen, die für die aktuelle Aufgabe relevant sind

**Während/nach der Arbeit** aktualisieren:

| Was ist passiert? | Wohin schreiben |
|---|---|
| Entscheidung getroffen (Bibliothek, Ansatz, Verworfenes) | `decisions.md` |
| Struktur, Module, Datenfluss geändert | `architecture.md` |
| Stolperfalle, Bug, Android-/Anki-Eigenheit entdeckt | `gotchas.md` |
| Aufgabe erledigt/neu/offen | `todo.md` |
| Session abgeschlossen | `log.md` (kurzer Eintrag oben) |
| Neue Datei in `.agents/` angelegt | `INDEX.md` |

## Regeln

- Kurz und faktisch schreiben. Keine Romane, keine Wiederholung dessen, was im Code offensichtlich ist.
- Jeder Eintrag mit Datum (`YYYY-MM-DD`).
- Bei Entscheidungen immer das **Warum** festhalten.
- Veraltete Einträge löschen oder korrigieren, nicht nur ergänzen.
- Unverifizierte Annahmen als `(unverifiziert)` markieren, besonders bei API-Details von AnkiDroid und Android.
- Keine Secrets oder persönlichen Daten. Das gilt auch für echte Hostnamen des eigenen Servers (immer `<basisdomain>`), E-Mail-Adressen, private Links (z. B. claude.ai-Artifacts) und absolute Home-Pfade (`<projektordner>`, `~/`). Vor jedem Push die Historie prüfen: `git grep -nIiE "<hostname>|<email>|/home/<user>" $(git rev-list --all)` (in bash, nicht zsh).
