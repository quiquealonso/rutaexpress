import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import {
  Dashboard,
  DeliveryApi,
  DurationPipe,
  Incident,
  LabelPipe,
  RealtimeService,
  RouteApi,
  RouteListItem,
  labelOf,
} from '@rutaexpress/shared';
import { debounceTime, forkJoin } from 'rxjs';

@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, RouterLink, LabelPipe, DurationPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="rx-page-head">
      <div>
        <h1>{{ greeting() }}</h1>
        <p>Así va el reparto de hoy.</p>
      </div>
      <a mat-flat-button routerLink="/live"><mat-icon>map</mat-icon> Ver mapa en directo</a>
    </header>

    @if (data(); as d) {
      <section class="kpis">
        <a class="kpi" routerLink="/routes">
          <span class="ico info"><mat-icon>route</mat-icon></span>
          <span class="label">Rutas de hoy</span>
          <span class="value">{{ d.routesToday }}</span>
          <span class="sub">{{ d.routesInProgress }} en curso · {{ d.routesCompleted }} terminadas</span>
        </a>
        <a class="kpi" routerLink="/routes">
          <span class="ico ok"><mat-icon>task_alt</mat-icon></span>
          <span class="label">Paradas entregadas</span>
          <span class="value">{{ d.deliveredStopsToday }}<small> / {{ totalStops() }}</small></span>
          <span class="sub">{{ completion() }} % del día completado</span>
        </a>
        <a class="kpi" routerLink="/parcels" [queryParams]="{ status: 'FAILED_ATTEMPT' }" [class.alert-bad]="d.failedStopsToday > 0">
          <span class="ico" [class.bad]="d.failedStopsToday > 0"><mat-icon>person_off</mat-icon></span>
          <span class="label">Entregas fallidas</span>
          <span class="value">{{ d.failedStopsToday }}</span>
          <span class="sub">{{ d.failedStopsToday > 0 ? 'Revisar con los repartidores' : 'Ninguna por ahora' }}</span>
        </a>
        <div class="kpi">
          <span class="ico"><mat-icon>timer</mat-icon></span>
          <span class="label">Tiempo medio por parada</span>
          <!-- Con pocas entregas la media no significa nada -->
          @if (d.deliveredStopsToday >= 3 && d.averageSecondsPerStop > 0) {
            <span class="value">{{ d.averageSecondsPerStop | rxDuration }}</span>
            <span class="sub">Desde la llegada hasta la entrega</span>
          } @else {
            <span class="value muted">—</span>
            <span class="sub">Faltan datos (mínimo 3 entregas)</span>
          }
        </div>
        <a class="kpi" routerLink="/incidents" [class.alert-warn]="d.openIncidents > 0">
          <span class="ico" [class.warn]="d.openIncidents > 0"><mat-icon>report</mat-icon></span>
          <span class="label">Incidencias abiertas</span>
          <span class="value">{{ d.openIncidents }}</span>
          <span class="sub">{{ d.openIncidents > 0 ? 'Pendientes de resolver' : 'Todo en orden' }}</span>
        </a>
      </section>

      <div class="grid">
        <section class="rx-card">
          <header class="card-head"><h2>Repartos del día</h2><a routerLink="/routes">Ver todas</a></header>
          @for (r of routes(); track r.id) {
            <a class="route" [routerLink]="['/routes', r.id]">
              <span class="avatar">{{ initialsOf(r.driverName) }}</span>
              <span class="who">
                <b>{{ r.driverName }}</b>
                <small>{{ r.delivered }} entregadas · {{ r.failed }} fallidas · {{ r.pending }} pendientes</small>
              </span>
              <span class="bar">
                <mat-progress-bar mode="determinate" [value]="(100 * (r.delivered + r.failed)) / (r.totalStops || 1)" />
                <small>{{ r.delivered + r.failed }} de {{ r.totalStops }} paradas</small>
              </span>
              <span class="chip" [class]="r.status">{{ r.status | rxLabel }}</span>
              <span class="end">
                @if (r.estimatedEnd) { <small>Fin</small><b>{{ r.estimatedEnd | date: 'HH:mm' }}</b> }
              </span>
            </a>
          } @empty {
            <div class="empty"><mat-icon>local_shipping</mat-icon><p>Aún no hay rutas hoy. Aparecerán cuando los repartidores cierren su carga.</p></div>
          }
        </section>

        <div class="side">
          <section class="rx-card">
            <header class="card-head"><h2>Requiere atención</h2>
              @if (alerts().length > 0) { <span class="count">{{ alerts().length }}</span> }
            </header>
            @for (a of alerts(); track a.key) {
              <a class="alert-row" [routerLink]="a.link">
                <span class="ico sm" [class.bad]="a.level === 'high'" [class.warn]="a.level !== 'high'"><mat-icon>{{ a.icon }}</mat-icon></span>
                <span class="txt"><b>{{ a.title }}</b><small>{{ a.detail }}</small></span>
                <mat-icon class="chev">chevron_right</mat-icon>
              </a>
            } @empty {
              <div class="empty sm"><mat-icon>verified</mat-icon><p>Todo en orden. Sin incidencias ni entregas fallidas.</p></div>
            }
          </section>

          <section class="rx-card">
            <header class="card-head"><h2>Paquetes por estado</h2><a routerLink="/parcels">Ver paquetes</a></header>
            <div class="stack" role="img" [attr.aria-label]="stackLabel()">
              @for (s of d.parcelsByStatus; track s.status) {
                <span [class]="'seg ' + s.status" [style.flex-grow]="s.total"></span>
              }
            </div>
            <ul class="legend">
              @for (s of d.parcelsByStatus; track s.status) {
                <li><span [class]="'sw ' + s.status"></span>{{ s.status | rxLabel }}<b>{{ s.total }}</b></li>
              } @empty { <li class="muted">Sin paquetes.</li> }
            </ul>
          </section>
        </div>
      </div>
    } @else {
      <mat-progress-bar mode="indeterminate" />
    }
  `,
  styles: `
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .kpi { display: grid; grid-template-columns: auto 1fr; grid-template-areas: 'ico label' 'value value' 'sub sub';
      align-items: center; column-gap: 10px; row-gap: 6px; padding: 18px; border-radius: var(--rx-radius);
      background: var(--mat-sys-surface-container-lowest); border: 1px solid var(--rx-border); box-shadow: var(--rx-shadow-sm);
      color: inherit; text-decoration: none; transition: border-color .15s, box-shadow .15s; }
    a.kpi:hover { border-color: var(--rx-slate-300); box-shadow: var(--rx-shadow); }
    a.kpi:focus-visible { outline: 2px solid var(--mat-sys-primary); outline-offset: 2px; }
    .kpi.alert-bad { border-color: #f5b4ad; box-shadow: inset 3px 0 0 var(--rx-bad); }
    .kpi.alert-warn { border-color: #f6c99a; box-shadow: inset 3px 0 0 var(--rx-warn); }
    .label { grid-area: label; font-size: .85rem; font-weight: 500; color: var(--mat-sys-on-surface-variant); }
    .value { grid-area: value; font-size: 2rem; font-weight: 700; letter-spacing: -0.02em; line-height: 1.1; margin-top: 6px; }
    .value small { font-size: 1rem; font-weight: 500; color: var(--mat-sys-on-surface-variant); }
    .value.muted { color: var(--rx-slate-300); }
    .sub { grid-area: sub; font-size: .8rem; color: var(--mat-sys-on-surface-variant); }
    .ico { grid-area: ico; width: 32px; height: 32px; border-radius: 8px; display: grid; place-items: center;
      background: var(--rx-muted-bg); color: var(--rx-muted-fg); }
    .ico mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .ico.info { background: var(--rx-info-bg); color: var(--rx-info); }
    .ico.ok { background: var(--rx-ok-bg); color: var(--rx-ok); }
    .ico.warn { background: var(--rx-warn-bg); color: var(--rx-warn); }
    .ico.bad { background: var(--rx-bad-bg); color: var(--rx-bad); }
    .ico.sm { width: 30px; height: 30px; flex: none; }

    .grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr); gap: 16px; align-items: start; }
    .side { display: grid; gap: 16px; }
    .card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
    .card-head a { font-size: .85rem; font-weight: 600; text-decoration: none; }
    .count { min-width: 22px; height: 22px; padding: 0 6px; border-radius: 999px; display: grid; place-items: center;
      background: var(--rx-warn); color: #fff; font-size: .75rem; font-weight: 700; }

    .route { display: grid; grid-template-columns: auto minmax(140px, 1.3fr) minmax(120px, 1fr) auto 56px; align-items: center; gap: 16px;
      padding: 14px 8px; margin: 0 -8px; border-radius: var(--rx-radius-sm); color: inherit; text-decoration: none;
      border-top: 1px solid var(--rx-border); }
    .route:hover { background: #f8fafc; }
    .avatar { width: 36px; height: 36px; border-radius: 50%; display: grid; place-items: center; font-size: .8rem; font-weight: 700;
      background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); }
    .who { display: grid; gap: 2px; min-width: 0; }
    .who small, .bar small, .end small { color: var(--mat-sys-on-surface-variant); font-size: .78rem; }
    .bar { display: grid; gap: 4px; }
    .end { display: grid; text-align: right; }
    .end b { font-size: 1rem; }

    .alert-row { display: flex; align-items: center; gap: 12px; padding: 10px 8px; margin: 0 -8px; border-radius: var(--rx-radius-sm);
      color: inherit; text-decoration: none; }
    .alert-row:hover { background: #f8fafc; }
    .txt { flex: 1; display: grid; gap: 2px; min-width: 0; }
    .txt b { font-size: .9rem; font-weight: 600; }
    .txt small { font-size: .8rem; color: var(--mat-sys-on-surface-variant); }
    .chev { color: var(--rx-slate-300); }

    .stack { display: flex; gap: 3px; height: 12px; border-radius: 999px; overflow: hidden; background: var(--rx-muted-bg); }
    .seg, .sw { background: var(--rx-slate-300); }
    .seg.DELIVERED, .sw.DELIVERED { background: var(--rx-ok); }
    .seg.OUT_FOR_DELIVERY, .sw.OUT_FOR_DELIVERY, .seg.LOADED, .sw.LOADED { background: var(--rx-navy-500); }
    .seg.FAILED_ATTEMPT, .sw.FAILED_ATTEMPT, .seg.RETURNED, .sw.RETURNED { background: var(--rx-bad); }
    .seg.REGISTERED, .sw.REGISTERED { background: #94a3b8; }
    .legend { list-style: none; padding: 0; margin: 16px 0 0; display: grid; gap: 8px; }
    .legend li { display: flex; align-items: center; gap: 10px; font-size: .9rem; }
    .legend b { margin-left: auto; font-variant-numeric: tabular-nums; }
    .sw { width: 10px; height: 10px; border-radius: 3px; }
    .muted { color: var(--mat-sys-on-surface-variant); }

    .empty { display: grid; justify-items: center; gap: 4px; padding: 32px 16px; text-align: center; color: var(--mat-sys-on-surface-variant); }
    .empty mat-icon { font-size: 32px; width: 32px; height: 32px; color: var(--rx-slate-300); }
    .empty p { margin: 0; max-width: 32ch; }
    .empty.sm { padding: 16px; }
    @media (max-width: 1100px) { .grid { grid-template-columns: 1fr; } }
    @media (max-width: 640px) { .route { grid-template-columns: auto 1fr auto; } .bar, .end { display: none; } }
  `,
})
export class DashboardPage {
  private readonly delivery = inject(DeliveryApi);
  private readonly routeApi = inject(RouteApi);
  protected readonly data = signal<Dashboard | null>(null);
  protected readonly routes = signal<RouteListItem[]>([]);
  protected readonly incidents = signal<Incident[]>([]);

  protected readonly greeting = computed(() => {
    const h = new Date().getHours();
    return h < 14 ? 'Buenos días' : h < 21 ? 'Buenas tardes' : 'Buenas noches';
  });
  protected readonly totalStops = computed(() => this.routes().reduce((n, r) => n + r.totalStops, 0));
  protected readonly completion = computed(() => {
    const total = this.totalStops();
    const closed = this.routes().reduce((n, r) => n + r.delivered + r.failed, 0);
    return total ? Math.round((100 * closed) / total) : 0;
  });
  protected readonly stackLabel = computed(() =>
    (this.data()?.parcelsByStatus ?? []).map((x) => `${labelOf(x.status)}: ${x.total}`).join(', '),
  );

  protected initialsOf(name: string): string {
    return name.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase();
  }

  /** Lo que tráfico debería mirar ya: incidencias abiertas y repartos con entregas fallidas. */
  protected readonly alerts = computed(() => {
    const names = new Map(this.routes().map((r) => [r.driverId, r.driverName]));
    const fromIncidents = this.incidents().map((i) => ({
      key: 'i' + i.id,
      level: 'medium',
      icon: 'report',
      title: `${labelOf(i.type)} · ${names.get(i.driverId) ?? 'Repartidor'}`,
      detail: `${i.description} · ${new Date(i.createdAt).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })}`,
      link: '/incidents',
      query: {},
    }));
    const fromRoutes = this.routes()
      .filter((r) => r.failed > 0)
      .map((r) => ({
        key: 'r' + r.id,
        level: 'high',
        icon: 'person_off',
        title: `${r.failed} ${r.failed === 1 ? 'entrega fallida' : 'entregas fallidas'} · ${r.driverName}`,
        detail: `${r.delivered} entregadas · ${r.pending} pendientes`,
        link: '/routes/' + r.id,
        query: {},
      }));
    return [...fromRoutes, ...fromIncidents];
  });

  constructor() {
    this.load();
    // Se refresca al llegar eventos en tiempo real (agrupados para no saturar)
    inject(RealtimeService)
      .watchType('/topic/dispatch', 'STOP_STATUS_CHANGED', 'ROUTE_OPTIMIZED', 'ROUTE_COMPLETED', 'INCIDENT_REPORTED', 'ROUTE_STARTED')
      .pipe(debounceTime(2000), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.load());
  }

  private load(): void {
    forkJoin([this.delivery.dashboard(), this.routeApi.today(), this.delivery.incidents(true)]).subscribe(([d, r, i]) => {
      this.data.set(d);
      this.routes.set(r);
      this.incidents.set(i);
    });
  }
}
