Raw provider answers (AnkiDroid v2.25.3 `full` x86_64, emulator, trivial test content, no personal data).
Format: one `content query` row per file (question, answer, answer_pure, question_simple, answer_simple, card_name).
Seeded by scripts/spike/seed-fixtures.py (first three files come from a first partial run).

- card-00 Basic (type in the answer), 04 same again: `[[type:Back]]` stays raw in question and answer
- card-01, 05: image `<img src="bild ä 1.png">` (name unencoded, with space and umlaut)
- card-02, 07: HTML entities and tags (`&amp;`, `<b>`, `<br>`, `<ul>`)
- card-03: Cloze (`span.cloze` with data-cloze attribute containing the hidden text, `span.cloze-inactive`; answer has NO `<hr id=answer>`)
- card-06: audio `[sound:klang.mp3]` stays as raw tag
- card-08: broken-looking template text (`{{c1::...}}` in a Basic note, stays literal)
- models-css.txt: css per note type
