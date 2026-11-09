import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../models';

/** Mensaje legible a partir de un error HTTP con formato Problem Details (RFC 7807). */
export function problemMessage(error: unknown, fallback = 'Ha ocurrido un error'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Sin conexión con el servidor';
    }
    const body = error.error as ProblemDetail | null;
    if (body?.errors) {
      return Object.entries(body.errors)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join(' · ');
    }
    return body?.detail ?? body?.title ?? fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
