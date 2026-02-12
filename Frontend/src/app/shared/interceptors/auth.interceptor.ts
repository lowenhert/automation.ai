import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // Get the auth token from the auth service
  const authToken = authService.getToken();

  // Clone the request and add the authorization header if token exists
  let authReq = req;
  if (authToken) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${authToken}`
      }
    });
  }

  // Pass the cloned request to the next handler
  return next(authReq).pipe(
    catchError(error => {
      // If we get a 401 Unauthorized, logout the user
      if (error.status === 401) {
        authService.logout();
      }
      return throwError(() => error);
    })
  );
};