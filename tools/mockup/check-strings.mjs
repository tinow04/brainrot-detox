// Every extracted mockup string must occur in ui/Strings.kt (substring) or be listed in design/reference/string-whitelist.txt.
// Usage: node tools/mockup/check-strings.mjs ; exit 1 on unexplained strings.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const stringsKt = path.join(root, 'app/src/main/kotlin/dev/tinow/brainrotstopper/ui/Strings.kt');
if (!fs.existsSync(stringsKt)) { console.error(`missing ${stringsKt}`); process.exit(1); }
const kt = fs.readFileSync(stringsKt, 'utf8');
const lines = fs.readFileSync(path.join(root, 'design/reference/string-whitelist.txt'), 'utf8')
  .split('\n').filter((l) => l.trim() && !l.startsWith('#')).map((l) => l.split(' # ')[0].trim());
// `re:<regex>` entries cover composed texts (numbers, app/deck names); their exact formats are asserted in StringsTest.
const white = new Set(lines.filter((l) => !l.startsWith('re:')));
const patterns = lines.filter((l) => l.startsWith('re:')).map((l) => new RegExp(l.slice(3)));
const data = JSON.parse(fs.readFileSync(path.join(root, 'design/reference/strings.json'), 'utf8'));
const bad = [];
for (const [screen, list] of Object.entries(data)) for (const s of list) if (!white.has(s) && !patterns.some((r) => r.test(s)) && !kt.includes(s)) bad.push(`${screen}: ${s}`);
if (bad.length) { console.error(`unexplained strings (${bad.length}):\n` + bad.join('\n')); process.exit(1); }
console.log('check-strings: ok');
