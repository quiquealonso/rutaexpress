import { InjectionToken } from '@angular/core';

/** Prefijo de la API. Vacío = mismo origen (Nginx en producción, proxy de `ng serve` en desarrollo). */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => '',
});

/** URL del WebSocket STOMP. Por defecto se deriva del origen actual. */
export const WS_URL = new InjectionToken<string>('WS_URL', {
  providedIn: 'root',
  factory: () => {
    const { protocol, host } = window.location;
    return `${protocol === 'https:' ? 'wss' : 'ws'}://${host}/ws`;
  },
});

/** Clave bajo la que cada app guarda su sesión (admin y driver son orígenes distintos en dev). */
export const SESSION_STORAGE_KEY = new InjectionToken<string>('SESSION_STORAGE_KEY', {
  providedIn: 'root',
  factory: () => 'rutaexpress.session',
});
