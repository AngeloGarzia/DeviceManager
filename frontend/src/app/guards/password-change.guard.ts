import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

/** Redirige vers /change-password si le compte doit changer son mot de passe. */
export const passwordChangeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn() && auth.mustChangePassword()) {
    return router.createUrlTree(['/change-password']);
  }
  return true;
};

/** Empêche d'accéder à /change-password si le changement n'est pas requis. */
export const requirePasswordChangeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const decide = () => {
    if (!auth.isLoggedIn()) {
      return router.createUrlTree(['/login']);
    }
    if (!auth.mustChangePassword()) {
      return router.createUrlTree(['/devices']);
    }
    return true;
  };

  if (auth.isLoggedIn()) {
    return decide();
  }
  return auth.tryRestoreSession().pipe(map(() => decide()));
};
