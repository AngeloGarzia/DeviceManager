/**
 * Frontend : aucune variable secrète.
 * Les secrets / config serveur sont uniquement dans backend/.env.*
 * L'API est appelée en relatif (proxy Angular en dev).
 */
export const environment = {
  production: false,
  apiUrl: ''
};
