/**
 * Frontend production : aucune variable secrète.
 * apiUrl reste vide si le front est servi derrière le même reverse-proxy que l'API.
 * Ne jamais y mettre JWT, mots de passe, clés AWS, etc.
 */
export const environment = {
  production: true,
  apiUrl: ''
};
