import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) {
    return true;
  }

  // Access token en mémoire perdu au reload → tenter le cookie refresh HttpOnly.
  return auth.tryRestoreSession().pipe(
    map((ok) => (ok ? true : router.createUrlTree(['/login'])))
  );
};
