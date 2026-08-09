import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

let refreshInFlight: ReturnType<AuthService['refreshAccessToken']> | null = null;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();
  const atelierId = auth.getAtelierId();
  const isAuthPublic =
    req.url.includes('/api/auth/login') ||
    req.url.includes('/api/auth/refresh') ||
    req.url.includes('/api/auth/logout');

  const headers: Record<string, string> = {};

  if (token && !auth.isTokenExpired(token)) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  if (atelierId != null && !req.url.includes('/api/auth/')) {
    headers['X-Atelier-Id'] = String(atelierId);
  }

  const authReq = req.clone({
    setHeaders: headers,
    withCredentials: true
  });

  return next(authReq).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || isAuthPublic) {
        return throwError(() => err);
      }
      if (!refreshInFlight) {
        refreshInFlight = auth.refreshAccessToken();
      }
      return refreshInFlight.pipe(
        switchMap((res) => {
          refreshInFlight = null;
          return next(
            req.clone({
              setHeaders: {
                ...headers,
                Authorization: `Bearer ${res.token}`
              },
              withCredentials: true
            })
          );
        }),
        catchError((refreshErr) => {
          refreshInFlight = null;
          auth.handleSessionExpired();
          return throwError(() => refreshErr);
        })
      );
    })
  );
};
