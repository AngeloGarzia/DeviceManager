import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthPublic =
    req.url.includes('/api/auth/login') ||
    req.url.includes('/api/auth/refresh') ||
    req.url.includes('/api/auth/logout');

  const buildHeaders = (): Record<string, string> => {
    const headers: Record<string, string> = {};
    const token = auth.getToken();
    const atelierId = auth.getAtelierId();
    if (token && !auth.isTokenExpired(token)) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    if (atelierId != null && !req.url.includes('/api/auth/')) {
      headers['X-Atelier-Id'] = String(atelierId);
    }
    return headers;
  };

  const authReq = req.clone({
    setHeaders: buildHeaders(),
    withCredentials: true
  });

  return next(authReq).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || isAuthPublic) {
        return throwError(() => err);
      }
      return auth.refreshAccessToken().pipe(
        switchMap(() =>
          next(
            req.clone({
              setHeaders: buildHeaders(),
              withCredentials: true
            })
          )
        ),
        catchError((refreshErr) => {
          auth.handleSessionExpired();
          return throwError(() => refreshErr);
        })
      );
    })
  );
};
