import { DatePipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import {
  AuthService,
  CatalogApi,
  Driver,
  DistancePipe,

  LabelPipe,
  LoadApi,
  RealtimeService,
  RouteApi,
  RouteMapComponent,
  problemMessage,
} from '@rutaexpress/shared';
import { DriverStateService } from '../core/driver-state.service';
import { GeolocationService } from '../core/geolocation.service';

@Component({
  selector: 'app-today',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, RouterLink, DatePipe, LowerCasePipe, LabelPipe, DistancePipe, RouteMapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="hero">
      <div class="hello">
        <span class="rx-eyebrow">{{ today | date: "EEEE d 'de' MMMM" }}</span>
        <h1>Hola, {{ firstName() }}</h1>
        <div class="tags">
          <span><mat-icon>place</mat-icon>{{ driver()?.zoneName ?? 'Sin zona' }}</span>
          <span><mat-icon>local_shipping</mat-icon>{{ driver()?.defaultVehiclePlate ?? 'Sin vehículo' }}</span>
        </div>
      </div>
      @if (state.route(); as r) {
        <div class="ring" role="img" [attr.aria-label]="state.progress().done + ' de ' + state.progress().total + ' paradas cerradas'">
          <svg viewBox="0 0 64 64" aria-hidden="true">
            <circle cx="32" cy="32" r="27" class="track" />
            <circle cx="32" cy="32" r="27" class="fill" [attr.stroke-dasharray]="ringDash()" />
          </svg>
          <span><b>{{ state.progress().done }}</b>/{{ state.progress().total }}</span>
        </div>
      }
    </header>
    @if (state.loading()) { <mat-progress-bar mode="indeterminate" /> }

    @if (state.route(); as r) {
      <section class="stats">
        <div><small>Estado</small><b>{{ r.status | rxLabel }}</b></div>
        <div><small>Distancia</small><b>{{ r.totalDistanceMeters | rxDistance }}</b></div>
        <div><small>Fin previsto</small><b>{{ r.estimatedEnd | date: 'HH:mm' }}</b></div>
      </section>

      @if (r.status === 'COMPLETED') {
        <section class="rx-card state done">
          <mat-icon>verified</mat-icon>
          <h2>Reparto terminado</h2>
          <p>Has cerrado las {{ r.stops.length }} paradas de hoy. ¡Buen trabajo!</p>
        </section>
      } @else if (state.nextStop(); as s) {
        <section class="next rx-card">
          @defer {
            <rx-route-map class="mini" [route]="r" [selectedStopId]="s.id" [focusSelected]="true" [driverPosition]="gps.position()" />
          } @placeholder { <div class="mini"></div> }
          <div class="body">
            <span class="rx-eyebrow">{{ r.status === 'PLANNED' ? 'Primera parada' : 'Siguiente parada' }}</span>
            <div class="addr">
              <span class="seq">{{ s.sequence }}</span>
              <div>
                <h2>{{ s.addressLine }}</h2>
                <small>{{ s.parcels.length }} {{ s.parcels.length === 1 ? 'paquete' : 'paquetes' }} · llegada {{ s.eta | date: 'HH:mm' }}
                  @if (s.windowStart) { · franja {{ s.windowStart.slice(0, 5) }}–{{ s.windowEnd?.slice(0, 5) }} }</small>
              </div>
            </div>
            @if (r.status === 'PLANNED') {
              <button mat-flat-button class="big" (click)="startRoute(r.id)"><mat-icon>play_arrow</mat-icon> Iniciar reparto</button>
            } @else {
              <a mat-flat-button class="big" [routerLink]="['/stops', s.id]">Ir a la parada <mat-icon iconPositionEnd>arrow_forward</mat-icon></a>
            }
          </div>
        </section>
      }
    } @else if (state.load(); as l) {
      <section class="rx-card state">
        <mat-icon>{{ l.status === 'CLOSED' ? 'hourglass_top' : 'inventory_2' }}</mat-icon>
        <h2>Carga {{ l.status | rxLabel | lowercase }}</h2>
        <div class="load">
          <mat-progress-bar mode="determinate" [value]="(100 * l.scannedCount) / (l.expectedCount || 1)" />
          <small>{{ l.scannedCount }} de {{ l.expectedCount }} paquetes escaneados</small>
        </div>
        @if (l.status === 'CLOSED') { <p>Calculando la ruta óptima. Tarda unos segundos.</p> }
        @if (l.status === 'OPEN') { <a mat-flat-button class="big" routerLink="/scan"><mat-icon>qr_code_scanner</mat-icon> Seguir escaneando</a> }
      </section>
    } @else if (!state.loading()) {
      <section class="rx-card state">
        <mat-icon>inventory_2</mat-icon>
        <h2>Aún no has cargado la furgoneta</h2>
        <p>Escanea los paquetes al subirlos. Al cerrar la carga se calcula tu ruta.</p>
        <button mat-flat-button class="big" (click)="openLoad()" [disabled]="busy()"><mat-icon>qr_code_scanner</mat-icon> Empezar la carga</button>
      </section>
    }

    @if (gps.error()) {
      <div class="gps" role="alert">
        <mat-icon>location_off</mat-icon>
        <div>
          <strong>{{ gps.error() }}</strong>
          <p>Sin tu ubicación, tráfico no te ve en el mapa y las horas de llegada son menos precisas.
            @if (gps.denied()) { Actívala en los ajustes del navegador para esta app y pulsa «Reintentar». }</p>
          <button mat-stroked-button (click)="gps.retry()">Reintentar</button>
        </div>
      </div>
    }
  `,
  styles: `
    .hero { display: flex; align-items: center; gap: 16px; margin: -16px -16px 0; padding: 20px 16px 52px;
      background: radial-gradient(circle at 90% -30%, #2b4f7d 0%, transparent 60%), var(--rx-navy-900); color: #fff; }
    .hello { flex: 1; min-width: 0; }
    .hero .rx-eyebrow { color: #94a3b8; }
    .hero h1 { color: #fff; font-size: 1.6rem; margin: 4px 0 10px; }
    .tags { display: flex; flex-wrap: wrap; gap: 8px; }
    .tags span { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px 4px 6px; border-radius: 999px;
      background: rgba(255, 255, 255, .08); color: #cbd5e1; font-size: .8rem; font-weight: 500; }
    .tags mat-icon { font-size: 16px; width: 16px; height: 16px; color: #f59e0b; }
    .ring { position: relative; width: 76px; height: 76px; flex: none; }
    .ring svg { width: 100%; height: 100%; transform: rotate(-90deg); }
    .ring circle { fill: none; stroke-width: 6; }
    .ring .track { stroke: rgba(255, 255, 255, .12); }
    .ring .fill { stroke: #f59e0b; stroke-linecap: round; transition: stroke-dasharray .4s; }
    .ring span { position: absolute; inset: 0; display: grid; place-items: center; font-size: .8rem; color: #94a3b8; }
    .ring b { font-size: 1.3rem; color: #fff; }

    .stats { display: grid; grid-template-columns: repeat(3, 1fr); margin: -36px 0 16px; padding: 14px 4px; border-radius: var(--rx-radius);
      background: var(--mat-sys-surface-container-lowest); border: 1px solid var(--rx-border); box-shadow: var(--rx-shadow); position: relative; }
    .stats div { display: grid; gap: 2px; text-align: center; }
    .stats div + div { border-left: 1px solid var(--rx-border); }
    .stats small { font-size: .72rem; color: var(--mat-sys-on-surface-variant); }
    .stats b { font-size: 1rem; }

    .next { padding: 0; overflow: hidden; }
    .mini { display: block; height: 150px; min-height: 150px; border-radius: 0; background: #eef1f4; }
    .body { display: grid; gap: 14px; padding: 16px; }
    .addr { display: flex; gap: 12px; align-items: flex-start; }
    .addr h2 { font-size: 1.1rem; }
    .addr small { color: var(--mat-sys-on-surface-variant); }
    .seq { width: 36px; height: 36px; flex: none; border-radius: 50%; display: grid; place-items: center;
      background: var(--rx-navy-700); color: #fff; font-weight: 700; }
    .big { width: 100%; height: 54px; font-size: 1.05rem; }

    .state { display: grid; justify-items: center; gap: 10px; text-align: center; padding: 28px 20px; margin-top: 8px; }
    .state > mat-icon { width: 56px; height: 56px; font-size: 30px; line-height: 56px; border-radius: 16px;
      background: var(--rx-info-bg); color: var(--rx-info); }
    .state.done > mat-icon { background: var(--rx-ok-bg); color: var(--rx-ok); }
    .state p { margin: 0; color: var(--mat-sys-on-surface-variant); max-width: 30ch; }
    .load { width: 100%; display: grid; gap: 6px; }
    .load small { color: var(--mat-sys-on-surface-variant); }

    .gps { display: flex; gap: 12px; margin-top: 16px; padding: 14px 16px; border-radius: var(--rx-radius);
      background: var(--rx-warn-bg); color: var(--rx-warn-fg); border: 1px solid #f6c99a; }
    .gps mat-icon { flex: none; color: var(--rx-warn); }
    .gps p { margin: 4px 0 12px; font-size: .9rem; }
  `,
})
export class TodayPage {
  protected readonly auth = inject(AuthService);
  protected readonly state = inject(DriverStateService);
  protected readonly gps = inject(GeolocationService);
  private readonly loads = inject(LoadApi);
  private readonly routes = inject(RouteApi);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  protected readonly driver = signal<Driver | null>(null);
  protected readonly busy = signal(false);
  protected readonly today = new Date();
  protected readonly firstName = computed(() => this.driver()?.name.split(' ')[0] ?? this.auth.user()?.email ?? '');
  /** Arco del anillo de avance (circunferencia de r=27 ≈ 169.6). */
  protected readonly ringDash = computed(() => {
    const { done, total } = this.state.progress();
    const c = 2 * Math.PI * 27;
    return `${(c * done) / (total || 1)} ${c}`;
  });

  constructor() {
    inject(CatalogApi).driverMe().subscribe((d) => this.driver.set(d));
    this.state.refresh();
    // Con la ruta en curso, el GPS se mantiene activo
    effect(() => {
      if (this.state.route()?.status === 'IN_PROGRESS') this.gps.start();
    });
    const driverId = this.auth.user()?.driverId;
    if (driverId) {
      inject(RealtimeService)
        .watch(`/topic/drivers/${driverId}`)
        .pipe(takeUntilDestroyed(inject(DestroyRef)))
        .subscribe(() => this.state.refresh());
    }
  }

  protected openLoad(): void {
    this.busy.set(true);
    this.loads.open().subscribe({
      next: (l) => {
        this.state.setLoad(l);
        this.router.navigate(['/scan']);
      },
      error: (e) => {
        this.busy.set(false);
        this.snack.open(problemMessage(e), 'OK', { duration: 5000 });
      },
    });
  }

  protected startRoute(routeId: string): void {
    this.routes.start(routeId).subscribe({
      next: (r) => {
        this.state.setRoute(r);
        this.gps.start();
      },
      error: (e) => this.snack.open(problemMessage(e), 'OK', { duration: 5000 }),
    });
  }
}
