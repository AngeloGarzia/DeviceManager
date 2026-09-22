import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from '../shared/notification.service';
import { apiErrorMessage } from '../shared/api-error';

/**
 * Intercepteur global d'erreurs HTTP.
 * Affiche un snackbar utilisateur pour les erreurs « transverses »
 * (403 après login, 5xx serveur, réseau injoignable) mais laisse
 * les composants recevoir l'erreur pour leurs états locaux.
 *
 * - Les 401 sont gérés par {@code authInterceptor} (refresh / session expirée).
 * - Les 400 / 404 / 409 restent silencieux ici (messages contextuels côté écran).
 * - Certains endpoints « bruit de fond » (health, keep-alive) sont ignorés.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && !isSilentUrl(req.url)) {
        notifyIfNeeded(err, notifications);
      }
      return throwError(() => err);
    })
  );
};

function notifyIfNeeded(err: HttpErrorResponse, notifications: NotificationService): void {
  // 0 = requête interrompue / offline / DNS / CORS (avant réponse serveur).
  if (err.status === 0) {
    notifications.error(
      'Serveur injoignable. Vérifiez votre connexion, puis réessayez.'
    );
    return;
  }
  if (err.status === 403) {
    notifications.warning(apiErrorMessage(err, "Accès refusé pour cette action."));
    return;
  }
  if (err.status === 429) {
    notifications.warning(apiErrorMessage(err, 'Trop de requêtes. Réessayez dans un instant.'));
    return;
  }
  if (err.status === 503) {
    notifications.error(
      apiErrorMessage(err, 'Service temporairement indisponible. Réessayez dans quelques instants.')
    );
    return;
  }
  if (err.status >= 500 && err.status <= 599) {
    notifications.error(
      apiErrorMessage(err, 'Erreur serveur inattendue. Réessayez ou contactez un administrateur.')
    );
  }
}

function isSilentUrl(url: string): boolean {
  return (
    url.includes('/actuator/health') ||
    url.includes('/actuator/info') ||
    url.includes('/api/auth/refresh') ||
    url.includes('/api/auth/logout')
  );
}
