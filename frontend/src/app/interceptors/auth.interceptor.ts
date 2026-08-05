import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();
  const atelierId = auth.getAtelierId();
  const headers: Record<string, string> = {};
  const isLogin = req.url.includes('/api/auth/login');

  if (token && !auth.isTokenExpired(token)) {
    headers['Authorization'] = `Bearer ${token}`;
  } else if (token && auth.isTokenExpired(token) && !isLogin) {
    auth.handleSessionExpired();
  }

  if (atelierId != null && !req.url.includes('/api/auth/')) {
    headers['X-Atelier-Id'] = String(atelierId);
  }

  const handle = Object.keys(headers).length === 0 ? next(req) : next(req.clone({ setHeaders: headers }));

  return handle.pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && !isLogin) {
        auth.handleSessionExpired();
      }
      return throwError(() => err);
    })
  );
};
