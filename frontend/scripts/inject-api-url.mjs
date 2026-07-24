/**
 * Remplace apiUrl dans environment.prod.ts à partir de API_URL (Render build).
 * Accepte un host nu (device-manager-api.onrender.com) ou une URL complète.
 */
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const raw = (process.env.API_URL || '').trim().replace(/\/$/, '');
let apiUrl = '';
if (raw) {
  apiUrl = /^https?:\/\//i.test(raw) ? raw : `https://${raw}`;
}

const content = `/**
 * Frontend production — généré au build (scripts/inject-api-url.mjs).
 * Aucune variable secrète.
 */
export const environment = {
  production: true,
  apiUrl: '${apiUrl}'
};
`;

const target = resolve('src/environments/environment.prod.ts');
writeFileSync(target, content, 'utf8');
console.log(`[inject-api-url] apiUrl=${apiUrl || '(vide — même origine)'}`);
