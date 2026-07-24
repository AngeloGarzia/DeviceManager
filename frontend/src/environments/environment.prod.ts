/**
 * Frontend production : aucune variable secrète.
 * En build Render, scripts/inject-api-url.mjs remplit apiUrl depuis API_URL.
 * En local, laisser vide si reverse-proxy ; sinon mettre l'URL absolue de l'API.
 */
export const environment = {
  production: true,
  apiUrl: ''
};
