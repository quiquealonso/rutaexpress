import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { DistancePipe, LabelPipe, RouteApi, RouteMapComponent, problemMessage } from '@rutaexpress/shared';
import { DriverStateService } from '../core/driver-state.service';
import { GeolocationService } from '../core/geolocation.service';

@Component({
  selector: 'app-route',
  imports: [MatButtonModule, MatButtonToggleModule, MatIconModule, RouterLink, DatePipe, LabelPipe, DistancePipe, RouteMapComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (state.route(); as r) {
      <header class="head">
        <mat-button-toggle-group hideSingleSelectionIndicator [value]="view()" (change)="view.set($event.value)" aria-label="Vista">
          <mat-button-toggle value="list"><mat-icon>list</mat-icon></mat-button-toggle>
          <mat-button-toggle value="map"><mat-icon>map</mat-icon></mat-button-toggle>
        </mat-button-toggle-group>
        <span class="info">@if (r.version > 1) { Recalculada · }{{ r.totalDistanceMeters | rxDistance }} · fin {{ r.estimatedEnd | date: 'HH:mm' }}</span>
        <button mat-icon-button (click)="reoptimize(r.id)" [disabled]="busy()" aria-label="Recalcular ruta"><mat-icon>autorenew</mat-icon></button>
      </header>

      @if (view() === 'map') {
        @defer {
          <rx-route-map class="map" [route]="r" [driverPosition]="gps.position()" (stopClicked)="router.navigate(['/stops', $event])" />
        } @loading { <p>Cargando mapa…</p> }
      } @else {
        <!-- Las cerradas se pliegan: la lista abre en la parada en curso -->
        @if (closed().length > 0) {
          <button class="fold" (click)="showClosed.set(!showClosed())" [attr.aria-expanded]="showClosed()">
            <mat-icon>{{ showClosed() ? 'expand_less' : 'expand_more' }}</mat-icon>
            {{ closed().length }} {{ closed().length === 1 ? 'parada cerrada' : 'paradas cerradas' }}
          </button>
        }
        <ol class="stops">
          @for (s of visible(); track s.id) {
            <li [class]="s.status" [class.current]="s.id === state.nextStop()?.id">
              <a [routerLink]="['/stops', s.id]">
                <span class="seq">
                  @if (s.status === 'DELIVERED') { <mat-icon>check</mat-icon> }
                  @else if (s.status === 'FAILED') { <mat-icon>close</mat-icon> }
                  @else { {{ s.sequence }} }
                </span>
                <span class="text">
                  @if (s.id === state.nextStop()?.id) { <span class="now">Ahora</span> }
                  <b>{{ s.addressLine }}</b>
                  <small>{{ s.parcels.length }} {{ s.parcels.length === 1 ? 'paquete' : 'paquetes' }} · {{ s.status | rxLabel }}
                    @if (s.status === 'PENDING' || s.status === 'SKIPPED') { · ETA {{ s.eta | date: 'HH:mm' }} }
                    @if (s.windowStart) { · franja {{ s.windowStart.slice(0, 5) }}–{{ s.windowEnd?.slice(0, 5) }} }
                  </small>
                </span>
                <mat-icon class="chev">chevron_right</mat-icon>
              </a>
            </li>
          }
        </ol>
      }
    } @else {
      <p>No hay ruta todavía. Escanea y cierra la carga para calcularla.</p>
    }
  `,
  styles: `
    .head { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
    .info { flex: 1; min-width: 0; font-size: .85rem; color: var(--mat-sys-on-surface-variant); }
    .map { display: block; height: calc(100dvh - 200px); border: 1px solid var(--rx-border); }
    .fold { display: flex; align-items: center; gap: 8px; width: 100%; min-height: 44px; padding: 8px 12px; margin-bottom: 8px;
      border: 1px dashed var(--rx-slate-300); border-radius: var(--rx-radius-sm); background: none;
      color: var(--mat-sys-on-surface-variant); font: inherit; font-size: .9rem; font-weight: 500; cursor: pointer; text-align: left; }
    .stops { list-style: none; padding: 0; margin: 0; position: relative; }
    /* Línea vertical que une las paradas */
    .stops::before { content: ''; position: absolute; left: 27px; top: 24px; bottom: 24px; width: 2px; background: var(--rx-border); }
    .stops li { position: relative; }
    .stops a { display: flex; gap: 14px; align-items: center; padding: 10px 8px 10px 10px; margin: 2px 0; border-radius: var(--rx-radius);
      color: inherit; text-decoration: none; min-height: 56px; }
    .stops a:active { background: var(--mat-sys-surface-container); }
    .seq { position: relative; z-index: 1; width: 34px; height: 34px; flex: none; border-radius: 50%; display: grid; place-items: center;
      font-weight: 700; font-size: .9rem; background: var(--mat-sys-surface-container-lowest); color: var(--rx-navy-700);
      border: 2px solid var(--rx-navy-700); }
    .seq mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .DELIVERED .seq { background: var(--rx-ok); border-color: var(--rx-ok); color: #fff; }
    .FAILED .seq { background: var(--rx-bad); border-color: var(--rx-bad); color: #fff; }
    .SKIPPED .seq { border-color: var(--rx-warn); color: var(--rx-warn); border-style: dashed; }
    .DELIVERED .text, .FAILED .text { color: var(--mat-sys-on-surface-variant); }
    .current a { background: var(--mat-sys-surface-container-lowest); border: 1px solid var(--rx-border); box-shadow: var(--rx-shadow); }
    .current .seq { background: var(--rx-navy-700); color: #fff; box-shadow: 0 0 0 4px var(--mat-sys-primary-container); }
    .text { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .text b { font-weight: 600; }
    .text small { font-size: .8rem; color: var(--mat-sys-on-surface-variant); }
    .now { align-self: flex-start; font-size: .68rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase;
      padding: 2px 8px; border-radius: 999px; background: #fef3c7; color: #92400e; }
    .chev { color: var(--rx-slate-300); flex: none; }
  `,
})
export class RoutePage {
  protected readonly state = inject(DriverStateService);
  protected readonly gps = inject(GeolocationService);
  protected readonly router = inject(Router);
  private readonly routes = inject(RouteApi);
  private readonly snack = inject(MatSnackBar);
  protected readonly view = signal<'list' | 'map'>('list');
  protected readonly busy = signal(false);
  protected readonly showClosed = signal(false);
  protected readonly closed = computed(() =>
    (this.state.route()?.stops ?? []).filter((s) => s.status === 'DELIVERED' || s.status === 'FAILED'),
  );
  protected readonly visible = computed(() => {
    const stops = this.state.route()?.stops ?? [];
    return this.showClosed() ? stops : stops.filter((s) => s.status !== 'DELIVERED' && s.status !== 'FAILED');
  });

  constructor() {
    this.state.refresh();
  }

  protected reoptimize(routeId: string): void {
    this.busy.set(true);
    this.routes.reoptimize(routeId, this.gps.position() ?? undefined).subscribe({
      next: (r) => {
        this.state.setRoute(r);
        this.busy.set(false);
        this.snack.open('Ruta recalculada', 'OK', { duration: 3000 });
      },
      error: (e) => {
        this.busy.set(false);
        this.snack.open(problemMessage(e), 'OK', { duration: 5000 });
      },
    });
  }
}
