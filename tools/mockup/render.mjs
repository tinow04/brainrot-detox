// Renders design/mockups/*.dc.html statically (dark/light) to tools/mockup/out/<name>-<theme>.html.
// The mockups only use <sc-for list="{{themes}}" as="t"> with {{t.cls}}; any other sc-* directive or hole aborts.
// Usage: node tools/mockup/render.mjs   (then tools/mockup/shoot.sh takes the screenshots)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const src = path.join(root, 'design/mockups');
const out = path.join(root, 'tools/mockup/out');
fs.mkdirSync(out, { recursive: true });
fs.copyFileSync(path.join(src, 'theme.css'), path.join(out, 'theme.css'));

const fontDir = path.join(root, 'app/src/main/res/font');
const fontCss = [['regular', 400], ['medium', 500], ['bold', 700]]
  .map(([n, w]) => `@font-face{font-family:'JetBrains Mono';font-weight:${w};src:url('file://${fontDir}/jetbrains_mono_${n}.ttf')}`)
  .join('');

const names = fs.readdirSync(src).filter((f) => f.endsWith('.dc.html'));
for (const f of names) {
  const html = fs.readFileSync(path.join(src, f), 'utf8');
  const m = html.match(/<sc-for list="\{\{themes\}\}"[^>]*>([\s\S]*?)<\/sc-for>/);
  if (!m) throw new Error(`${f}: no sc-for themes block`);
  const rest = html.replace(m[0], '');
  const other = rest.match(/<sc-[a-z-]+|\{\{[^}]*\}\}/g);
  if (other) throw new Error(`${f}: unsupported directive/hole outside themes: ${other.join(', ')}`);
  const inner = m[1];
  const holes = inner.match(/\{\{[^}]*\}\}/g) || [];
  for (const h of holes) if (h !== '{{t.cls}}') throw new Error(`${f}: unsupported hole ${h}`);
  const name = f === 'Main.dc.html' ? 'A1-Intro' : f.replace('.dc.html', '');
  for (const theme of ['dark', 'light']) {
    const body = inner.replaceAll('{{t.cls}}', theme);
    const page = `<!doctype html><html lang="de"><head><meta charset="utf-8"><link rel="stylesheet" href="theme.css"><style>${fontCss}html,body{margin:0;background:#000}</style></head><body>${body}</body></html>`;
    fs.writeFileSync(path.join(out, `${name}-${theme}.html`), page);
  }
}
console.log(`rendered ${names.length * 2} pages to ${out}`);
