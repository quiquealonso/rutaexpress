import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const withToken = (req: HttpRequest<unknown>, token: string | null) =>
  token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

/**
 * Añade el JWT, lo refresca antes de que caduque y, si el servidor responde 401, refresca una vez
 * y reintenta. Si el refresco falla, cierra la sesión.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthCall = req.url.includes('/api/auth/login') || req.url.includes('/api/auth/refresh');
  const isPublic = req.url.includes('/public/');
  if (isAuthCall || isPublic || !auth.isAuthenticated()) {
    return next(req);
  }

  const send = () => next(withToken(req, auth.accessToken));

  const request$ = auth.isAccessTokenExpired
    ? auth.refresh().pipe(switchMap(() => send()))
    : send();

  return request$.pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        return auth.refresh().pipe(
          switchMap((token) => next(withToken(req, token))),
          catchError((refreshError) => {
            auth.logout();
            return throwError(() => refreshError);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
