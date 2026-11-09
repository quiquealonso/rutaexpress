import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models';
import { AuthService } from './auth.service';

/** Exige sesión y, opcionalmente, uno de los roles indicados. */
export const authGuard =
  (...roles: Role[]): CanActivateFn =>
  (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    if (roles.length > 0 && !auth.hasRole(...roles)) {
      auth.clear();
      return router.createUrlTree(['/login'], { queryParams: { denied: 1 } });
    }
    return true;
  };
