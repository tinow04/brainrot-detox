# Prompt für Claude Design (Mockups, Iteration 1)

Stand: 2026-11-02. Entstanden aus Fragerunde mit dem User. Siehe `decisions.md` für die Design-Entscheidungen.

---

Design high-fidelity mobile mockups (Android, Pixel 9, portrait, 412×915 dp) for **Brainrot Stopper**, an app that blocks distracting apps (Instagram, TikTok, YouTube) until the user has correctly answered 5 flashcards from Anki. The UI language is German.

## Visual style: "modern terminal"

- Monospace typography throughout (e.g. JetBrains Mono or similar), shell-prompt flavour: `$ ./unlock`, `> `, blinking block cursor, `[ OK ]`-style tags, box-drawing characters for dividers and cards.
- Muted, modern palettes, no neon CRT green. Dark theme based on Catppuccin Mocha or Gruvbox Dark. Light theme based on Catppuccin Latte or Gruvbox Light. The app follows the system theme, so **show every screen in both light and dark**.
- One accent colour for primary actions and progress, plus semantic colours for the rating buttons (again = red, hard = orange/yellow, good = green, easy = blue/teal).
- Flat, sharp or slightly rounded corners (4 dp max), 1 px borders instead of shadows, generous padding, large touch targets (min 48 dp). It should feel like a terminal, not a gimmick: readable, calm, no scanline or glow effects.

## Screens to produce

### A) Lock overlay (the core experience), 5 screens

Shown fullscreen over the blocked app. It is not part of the main app.

1. **Intro screen** (short interstitial). Shows:
   - Name and icon of the blocked app, e.g. `instagram ist gesperrt`
   - Progress `0/5` as a text/block bar, e.g. `[░░░░░] 0/5`
   - Streak/stats line, e.g. `streak: 12 tage · heute: 15 karten`
   - Primary button `$ ./start`
   - Secondary button `abbrechen` (returns to home screen)
2. **Card front**: Anki card question, progress `1/5` at the top, blocked app name small, large readable card area (card content can be text, with an image variant), button `antwort zeigen`. Still show `abbrechen` in an unobtrusive place.
3. **Card back (rating)**: the question stays visible with the answer revealed below a divider. Four rating buttons in a row or 2×2 grid like Anki: `nochmal`, `schwer`, `gut`, `leicht`. Optionally show the next interval under each button (`<1m`, `6m`, `10m`, `4d`).
4. **Wrong answer feedback** (after `nochmal`): short, non-punishing terminal-style message, e.g. `[ FAIL ] karte kommt später nochmal`. Progress stays unchanged. Explain visually that only good/easy count.
5. **Success screen** at `5/5`: `[ OK ] entsperrt`, unlock duration (e.g. `instagram für 15 min freigegeben`), button `$ ./open instagram`, small stats summary (cards this session, time).

### B) Main app, 3 screens with bottom navigation (`apps`, `einstellungen`, `statistik`)

1. **App selection**: list of installed apps (icon, name) with a toggle per app. Blocked apps are visually highlighted and sorted to the top. Include a search field styled as a prompt (`$ grep `).
2. **Settings**: number of cards required (stepper, default 5), unlock duration (e.g. 5/15/30/60 min), Anki deck selection (dropdown/list), a status row for AnkiDroid connection (`[ OK ] ankidroid verbunden`).
3. **Statistics**: times blocked today/week, cards answered, correct rate, streak, estimated time saved. Show a simple bar chart for the last 7 days in the terminal style (block characters or flat bars) and a ranking of most-blocked apps.

## Deliverables

- All screens above, each in dark and light.
- One short note on the design tokens (colours, font sizes, spacing) so they can be translated into a Jetpack Compose theme later.
- Keep the 5 lock-overlay screens visually consistent as one flow and show how they connect (arrows or numbering).
