import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { LoadApi, LoadSummary, Route, RouteApi } from '@rutaexpress/shared';
import { catchError, of } from 'rxjs';

/** Estado del día del repartidor (carga y ruta actuales), compartido entre pantallas. */
@Injectable({ providedIn: 'root' })
export class DriverStateService {
  private readonly loads = inject(LoadApi);
  private readonly routes = inject(RouteApi);

  readonly load = signal<LoadSummary | null>(null);
  readonly route = signal<Route | null>(null);
  readonly loading = signal(false);

  readonly nextStop = computed(() => {
    const r = this.route();
    return r?.stops.filter((s) => s.status === 'PENDING' || s.status === 'SKIPPED').sort((a, b) => a.sequence - b.sequence)[0] ?? null;
  });

  readonly progress = computed(() => {
    const r = this.route();
    if (!r) return { done: 0, total: 0 };
    return { done: r.stops.filter((s) => s.status === 'DELIVERED' || s.status === 'FAILED').length, total: r.stops.length };
  });

  refresh(): void {
    this.loading.set(true);
    const notFound = (e: unknown) => (e instanceof HttpErrorResponse && e.status === 404 ? of(null) : of(undefined));
    this.loads.current().pipe(catchError(notFound)).subscribe((l) => {
      if (l !== undefined) this.load.set(l);
    });
    this.routes.current().pipe(catchError(notFound)).subscribe((r) => {
      if (r !== undefined) this.route.set(r);
      this.loading.set(false);
    });
  }

  setRoute(r: Route): void {
    this.route.set(r);
  }

  setLoad(l: LoadSummary): void {
    this.load.set(l);
  }
}
