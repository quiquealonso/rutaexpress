import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import {
  DriverPosition,
  LiveDriver,
  RealtimeService,
  Route,
  RouteApi,
  TrackingApi,
  createMap,
  driverMarker,
  escapeHtml,
  fitTo,
  routeLine,
  stopMarker,
  toLatLng,
} from '@rutaexpress/shared';
import * as L from 'leaflet';
import { debounceTime, forkJoin } from 'rxjs';

/** Resumen del reparto de un repartidor para la lista lateral. */
interface RouteProgress {
  routeId: string;
  done: number;
  total: number;
  nextAddress?: string;
  /** Minutos que va tarde respecto a la ETA de su siguiente parada (0 si va en hora). */
  delayMinutes: number;
  estimatedEnd?: string;
}

/** Posición de cada repartidor y estado de sus paradas, en directo por WebSocket. */
@Component({
  selector: 'app-live-map',
  imports: [MatCardModule, MatIconModule, MatProgressBarModule, RouterLink, DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="rx-page-head"><div><h1>Mapa en directo</h1><p>Posición de cada repartidor. Selecciona uno para ver su ruta.</p></div></header>
    <div class="layout">
      <div #map class="map"></div>
      <mat-card class="side">
        <mat-card-header><mat-card-title>Repartidores</mat-card-title></mat-card-header>
        <div class="list" role="list">
          @for (d of drivers(); track d.driverId) {
            @let p = progress()[d.driverId];
            <button class="item" role="listitem" (click)="select(d)" [class.sel]="d.driverId === selected()"
                    [attr.aria-pressed]="d.driverId === selected()">
              <span class="name">
                <strong>{{ d.driverName }}</strong>
                @if (p && p.delayMinutes > 0) {
                  <span class="badge late">+{{ p.delayMinutes }} min</span>
                } @else if (p) {
                  <span class="badge ok">En hora</span>
                }
              </span>
              @if (p) {
                <mat-progress-bar mode="determinate" [value]="(100 * p.done) / (p.total || 1)" />
                <small>{{ p.done }}/{{ p.total }} paradas
                  @if (p.estimatedEnd) { · fin {{ p.estimatedEnd | date: 'HH:mm' }} }</small>
                @if (p.nextAddress) { <small class="next">Siguiente: {{ p.nextAddress }}</small> }
              }
              <small class="muted">{{ d.speedKmh ?? 0 | number: '1.0-0' }} km/h · posición {{ d.recordedAt | date: 'HH:mm' }}</small>
            </button>
            @if (d.driverId === selected() && p) {
              <a class="detail" [routerLink]="['/routes', p.routeId]">Ver detalle de la ruta <mat-icon>chevron_right</mat-icon></a>
            }
          } @empty { <p class="empty">Sin posiciones recientes.</p> }
        </div>
      </mat-card>
    </div>
  `,
  styles: `
    .layout { display: grid; grid-template-columns: 1fr 340px; gap: 16px; height: calc(100vh - 210px); }
    .map { border-radius: var(--rx-radius); overflow: hidden; min-height: 400px; border: 1px solid var(--rx-border); }
    .side { overflow: auto; }
    .list { display: grid; padding: 0 8px 8px; }
    .item { display: grid; gap: 4px; text-align: left; padding: 12px; border: 0; border-radius: 10px; background: none;
      color: inherit; font: inherit; cursor: pointer; }
    .item:hover { background: var(--mat-sys-surface-container); }
    .item:focus-visible { outline: 2px solid var(--mat-sys-primary); }
    .sel, .sel:hover { background: var(--mat-sys-secondary-container); }
    .name { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
    .badge { font-size: 0.75rem; padding: 2px 8px; border-radius: 10px; font-weight: 600; }
    .badge.late { background: var(--rx-warn-bg); color: var(--rx-warn-fg); }
    .badge.ok { background: var(--rx-ok-bg); color: var(--rx-ok-fg); }
    .next { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .muted { color: var(--mat-sys-on-surface-variant); }
    .detail { display: flex; align-items: center; justify-content: flex-end; padding: 4px 12px 8px; min-height: 32px; }
    .empty { padding: 16px; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; height: auto; } .map { height: 60vh; } }
  `,
})
export class LiveMapPage {
  private readonly tracking = inject(TrackingApi);
  private readonly routes = inject(RouteApi);
  private readonly realtime = inject(RealtimeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly mapEl = viewChild.required<ElementRef<HTMLElement>>('map');

  protected readonly drivers = signal<LiveDriver[]>([]);
  protected readonly selected = signal<string | null>(null);
  protected readonly progress = signal<Record<string, RouteProgress>>({});

  private map?: L.Map;
  private readonly markers = new Map<string, L.Marker>();
  private readonly routeLayer = L.layerGroup();
  private selectedRoute: Route | null = null;

  constructor() {
    afterNextRender(() => {
      this.map = createMap(this.mapEl().nativeElement, undefined, 12);
      this.routeLayer.addTo(this.map);
      this.tracking.liveDrivers().subscribe((list) => {
        this.drivers.set(list);
        list.forEach((d) => this.placeDriver(d.driverId, d.driverName, d.location));
        // Encuadre inicial: solo la zona donde hay repartidores
        if (this.map) fitTo(this.map, list.map((d) => d.location));
      });
    });
    this.loadProgress();

    this.realtime
      .watchType<DriverPosition>('/topic/positions', 'POSITION')
      .pipe(takeUntilDestroyed())
      .subscribe(({ payload }) => {
        const known = this.drivers().find((d) => d.driverId === payload.driverId);
        const name = known?.driverName ?? 'Repartidor';
        this.placeDriver(payload.driverId, name, payload.location);
        const updated: LiveDriver = {
          driverId: payload.driverId,
          driverName: name,
          location: payload.location,
          speedKmh: payload.speedKmh,
          recordedAt: payload.recordedAt,
          routeId: known?.routeId,
        };
        this.drivers.update((list) =>
          known ? list.map((d) => (d.driverId === payload.driverId ? updated : d)) : [...list, updated],
        );
      });

    // Cambios de estado de paradas o recálculos: se repinta la ruta seleccionada y se actualiza el avance
    const changes = this.realtime
      .watchType<{ routeId: string }>('/topic/dispatch', 'STOP_STATUS_CHANGED', 'ROUTE_OPTIMIZED', 'ROUTE_STARTED', 'ROUTE_COMPLETED')
      .pipe(takeUntilDestroyed());
    changes.subscribe(({ payload }) => {
      if (this.selectedRoute && payload.routeId === this.selectedRoute.id) {
        this.loadRoute(payload.routeId, false);
      }
    });
    changes.pipe(debounceTime(2000)).subscribe(() => this.loadProgress());

    this.destroyRef.onDestroy(() => this.map?.remove());
  }

  private placeDriver(driverId: string, name: string, location: { lat: number; lon: number }): void {
    if (!this.map) return;
    const existing = this.markers.get(driverId);
    if (existing) {
      existing.setLatLng(toLatLng(location));
    } else {
      const m = driverMarker(location, name).bindTooltip(escapeHtml(name), { direction: 'top', offset: [0, -14] });
      m.addTo(this.map);
      this.markers.set(driverId, m);
    }
  }

  /** Solo el repartidor seleccionado lleva la etiqueta fija; el resto, al pasar el ratón (evita etiquetas pisadas). */
  private highlight(driverId: string): void {
    this.markers.forEach((m, id) => {
      const name = this.drivers().find((d) => d.driverId === id)?.driverName ?? '';
      m.unbindTooltip().bindTooltip(escapeHtml(name), { permanent: id === driverId, direction: 'top', offset: [0, -14] });
    });
  }

  protected select(d: LiveDriver): void {
    this.selected.set(d.driverId);
    this.highlight(d.driverId);
    const routeId = d.routeId ?? this.progress()[d.driverId]?.routeId;
    if (routeId) {
      this.loadRoute(routeId, true);
    } else {
      this.map?.setView(toLatLng(d.location), 14);
    }
  }

  private loadRoute(routeId: string, fit: boolean): void {
    this.routes.get(routeId).subscribe((route) => {
      this.selectedRoute = route;
      this.routeLayer.clearLayers();
      routeLine(route.geometry).addTo(this.routeLayer);
      route.stops.forEach((s) =>
        stopMarker(s.location, s.sequence, s.status).bindTooltip(escapeHtml(s.addressLine)).addTo(this.routeLayer),
      );
      if (fit && this.map) {
        const driver = this.drivers().find((x) => x.driverId === route.driverId);
        fitTo(this.map, [...route.stops.map((s) => s.location), ...(driver ? [driver.location] : [])]);
      }
    });
  }

  /** Avance, retraso y siguiente parada de cada reparto activo. */
  private loadProgress(): void {
    this.routes.today().subscribe((list) => {
      const active = list.filter((r) => r.status === 'IN_PROGRESS' || r.status === 'PLANNED');
      if (active.length === 0) {
        this.progress.set({});
        return;
      }
      forkJoin(active.map((r) => this.routes.get(r.id))).subscribe((routes) => {
        const now = Date.now();
        const result: Record<string, RouteProgress> = {};
        for (const r of routes ?? []) {
          const done = r.stops.filter((s) => s.status === 'DELIVERED' || s.status === 'FAILED').length;
          const next = [...r.stops]
            .filter((s) => s.status === 'PENDING' || s.status === 'SKIPPED')
            .sort((a, b) => a.sequence - b.sequence)[0];
          const eta = next?.eta ? new Date(next.eta).getTime() : null;
          result[r.driverId] = {
            routeId: r.id,
            done,
            total: r.stops.length,
            nextAddress: next?.addressLine,
            delayMinutes: eta !== null && r.status === 'IN_PROGRESS' ? Math.max(0, Math.round((now - eta) / 60000)) : 0,
            estimatedEnd: r.estimatedEnd,
          };
        }
        this.progress.set(result);
      });
    });
  }
}
