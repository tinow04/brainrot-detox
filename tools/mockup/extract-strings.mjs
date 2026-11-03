// Extracts all visible static strings per mockup screen (dark render) to design/reference/strings.json.
// Usage: node tools/mockup/extract-strings.mjs   (run after tools/mockup/render.mjs)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const out = path.join(root, 'tools/mockup/out');
const decode = (s) => s.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&nbsp;/g, ' ');
const result = {};
for (const f of fs.readdirSync(out).filter((n) => n.endsWith('-dark.html')).sort()) {
  const html = fs.readFileSync(path.join(out, f), 'utf8')
    .replace(/<style[\s\S]*?<\/style>/g, '').replace(/<script[\s\S]*?<\/script>/g, '');
  const body = html.slice(html.indexOf('<body>'));
  const texts = [];
  for (const m of body.matchAll(/>([^<]+)</g)) {
    const t = decode(m[1]).replace(/\s+/g, ' ').trim();
    if (t) texts.push(t);
  }
  result[f.replace('-dark.html', '')] = texts;
}
fs.writeFileSync(path.join(root, 'design/reference/strings.json'), JSON.stringify(result, null, 1) + '\n');
console.log(Object.entries(result).map(([k, v]) => `${k}: ${v.length}`).join('\n'));
