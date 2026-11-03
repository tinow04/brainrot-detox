// Extracts px dimensions per selector from design/mockups/theme.css to app/src/test/resources/theme-dims.json
// (used by BsDimsTest). Usage: node tools/mockup/extract-dims.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const css = fs.readFileSync(path.join(root, 'design/mockups/theme.css'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
const dims = {};
for (const m of css.matchAll(/([^{}@]+)\{([^{}]*)\}/g)) {
  const selector = m[1].trim().replace(/\s+/g, ' ');
  const props = {};
  for (const d of m[2].split(';')) {
    const i = d.indexOf(':');
    if (i < 0) continue;
    const prop = d.slice(0, i).trim();
    const val = d.slice(i + 1).trim();
    if (/\d+(\.\d+)?px/.test(val) && !prop.startsWith('--')) props[prop] = val;
  }
  if (Object.keys(props).length) dims[selector] = { ...(dims[selector] || {}), ...props };
}
const target = path.join(root, 'app/src/test/resources/theme-dims.json');
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, JSON.stringify(dims, null, 1) + '\n');
console.log(`${Object.keys(dims).length} selectors with px values -> ${target}`);
