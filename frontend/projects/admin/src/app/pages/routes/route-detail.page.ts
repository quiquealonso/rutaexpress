import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  ComparisonResponse,
  DeliveryApi,
  DistancePipe,
  DurationPipe,
  LabelPipe,
  RealtimeService,
  Route,
  RouteApi,
  RouteMapComponent,
  RouteVersion,
} from '@rutaexpress/shared';
import { debounceTime, filter } from 'rxjs';
import { openBlob } from '../../core/files';

/** Detalle de ruta: planificado frente a real, versiones y comparativa de algoritmos. */
@Component({
  selector: 'app-route-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    ScrollingModule,
    MatCardModule,
    MatTabsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressBarModule,
    RouteMapComponent,
    LabelPipe,
    DistancePipe,
    DurationPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (route(); as r) {
      <header class="head">
        <h1>Ruta de hoy <span class="chip" [class]="r.status">{{ r.status | rxLabel }}</span></h1>
        <span class="muted">{{ r.algorithm | rxLabel }} · {{ r.totalDistanceMeters | rxDistance }} · {{ r.totalDurationSeconds | rxDuration }}
          @if (r.estimatedEnd) { · fin {{ r.estimatedEnd | date: 'HH:mm' }} }</span>
      </header>
      <div class="layout">
        @defer (on viewport) {
          <rx-route-map class="map" [route]="r" [comparison]="comparisonGeometry()" [selectedStopId]="selectedStop()" (stopClicked)="selectedStop.set($event)" />
        } @placeholder {
          <div class="map placeholder">Cargando mapa…</div>
        }
        <mat-card class="stops">
          <cdk-virtual-scroll-viewport itemSize="72" class="viewport">
            <div *cdkVirtualFor="let s of r.stops" class="stop" [class.sel]="s.id === selectedStop()" (click)="selectedStop.set(s.id)">
              <span class="seq" [class]="s.status">{{ s.sequence }}</span>
              <div class="info">
                <div class="addr">{{ s.addressLine }}</div>
                <small>
                  {{ s.parcels.length }} paq. · plan {{ s.plannedEta | date: 'HH:mm' }}
                  @if (s.completedAt) { · real {{ s.completedAt | date: 'HH:mm' }} <b [class.late]="delayMinutes(s.plannedEta, s.completedAt) > 10">({{ delayMinutes(s.plannedEta, s.completedAt) | number: '1.0-0' }} min)</b> }
                  @else if (s.eta) { · ETA {{ s.eta | date: 'HH:mm' }} }
                  @if (s.failReason) { · {{ s.failReason }} }
                </small>
              </div>
              @if (s.status === 'DELIVERED') {
                <button mat-icon-button matTooltip="Ver prueba de entrega" (click)="proof(s.id); $event.stopPropagation()"><mat-icon>draw</mat-icon></button>
              }
            </div>
          </cdk-virtual-scroll-viewport>
        </mat-card>
      </div>

      <mat-tab-group (selectedTabChange)="$event.index === 1 && !comparison() && compare()">
        <mat-tab label="Versiones">
          <table mat-table [dataSource]="versions()">
            <ng-container matColumnDef="version"><th mat-header-cell *matHeaderCellDef>Versión</th><td mat-cell *matCellDef="let v">v{{ v.version }}</td></ng-container>
            <ng-container matColumnDef="reason"><th mat-header-cell *matHeaderCellDef>Motivo</th><td mat-cell *matCellDef="let v">{{ v.reason | rxLabel }}</td></ng-container>
            <ng-container matColumnDef="algorithm"><th mat-header-cell *matHeaderCellDef>Algoritmo</th><td mat-cell *matCellDef="let v">{{ v.algorithm | rxLabel }}</td></ng-container>
            <ng-container matColumnDef="cost"><th mat-header-cell *matHeaderCellDef>Duración / distancia</th><td mat-cell *matCellDef="let v">{{ v.totalDurationSeconds | rxDuration }} · {{ v.totalDistanceMeters | rxDistance }}</td></ng-container>
            <ng-container matColumnDef="compute"><th mat-header-cell *matHeaderCellDef>Cálculo</th><td mat-cell *matCellDef="let v">{{ v.computeMillis }} ms ({{ v.matrixSource }})</td></ng-container>
            <ng-container matColumnDef="at"><th mat-header-cell *matHeaderCellDef>Hora</th><td mat-cell *matCellDef="let v">{{ v.createdAt | date: 'HH:mm:ss' }}</td></ng-container>
            <ng-container matColumnDef="show">
              <th mat-header-cell *matHeaderCellDef>Comparar</th>
              <td mat-cell *matCellDef="let v"><button mat-button (click)="comparisonVersion.set(v)">ver en mapa</button></td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="versionColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: versionColumns"></tr>
          </table>
        </mat-tab>
        <mat-tab label="Comparativa de algoritmos">
          @if (comparing()) { <mat-progress-bar mode="indeterminate" /> }
          @if (comparison(); as c) {
            <p>{{ c.stops }} paradas pendientes · matriz {{ c.matrixSource }} · orden de escaneo: {{ c.scanOrderDurationSeconds | rxDuration }}</p>
            <table mat-table [dataSource]="c.results">
              <ng-container matColumnDef="algorithm"><th mat-header-cell *matHeaderCellDef>Algoritmo</th><td mat-cell *matCellDef="let a">{{ a.algorithm | rxLabel }}</td></ng-container>
              <ng-container matColumnDef="duration"><th mat-header-cell *matHeaderCellDef>Tiempo de conducción</th><td mat-cell *matCellDef="let a">{{ a.totalDurationSeconds | rxDuration }}</td></ng-container>
              <ng-container matColumnDef="saved"><th mat-header-cell *matHeaderCellDef>Ahorro vs. escaneo</th><td mat-cell *matCellDef="let a">{{ a.savedSecondsVsScanOrder | rxDuration }} ({{ a.savedPercentVsScanOrder }} %)</td></ng-container>
              <ng-container matColumnDef="compute"><th mat-header-cell *matHeaderCellDef>Tiempo de cálculo</th><td mat-cell *matCellDef="let a">{{ a.computeMillis }} ms</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="comparisonColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: comparisonColumns"></tr>
            </table>
            <button mat-stroked-button (click)="compare()">Repetir</button>
          }
        </mat-tab>
      </mat-tab-group>
    } @else {
      <mat-progress-bar mode="indeterminate" />
    }
  `,
  styles: `
    .head { display: flex; flex-wrap: wrap; align-items: baseline; gap: 12px; }
    .muted { color: var(--mat-sys-on-surface-variant); }
    .layout { display: grid; grid-template-columns: 1fr 380px; gap: 16px; height: 520px; margin-bottom: 16px; }
    .map { height: 100%; }
    .placeholder { display: grid; place-items: center; background: var(--mat-sys-surface-container); border-radius: 12px; }
    .stops { overflow: hidden; }
    .viewport { height: 100%; }
    .stop { height: 72px; display: flex; align-items: center; gap: 10px; padding: 0 12px; cursor: pointer; border-bottom: 1px solid var(--mat-sys-outline-variant); box-sizing: border-box; }
    .stop.sel { background: var(--mat-sys-secondary-container); }
    .seq { width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center; color: white; font-weight: 600; background: var(--rx-navy-700); flex: none; }
    .seq.DELIVERED { background: var(--rx-ok); } .seq.FAILED { background: var(--rx-bad); } .seq.SKIPPED { background: var(--rx-warn); }
    .info { flex: 1; min-width: 0; }
    .addr { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .late { color: var(--rx-bad); }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; height: auto; } .map { height: 50vh; } .stops { height: 400px; } }
  `,
})
export class RouteDetailPage implements OnInit {
  readonly id = input.required<string>();
  private readonly api = inject(RouteApi);
  private readonly delivery = inject(DeliveryApi);
  private readonly realtime = inject(RealtimeService);

  protected readonly route = signal<Route | null>(null);
  protected readonly versions = signal<RouteVersion[]>([]);
  protected readonly comparison = signal<ComparisonResponse | null>(null);
  protected readonly comparing = signal(false);
  protected readonly selectedStop = signal<string | null>(null);
  protected readonly comparisonVersion = signal<RouteVersion | null>(null);
  protected readonly comparisonGeometry = computed(() => this.comparisonVersion()?.geometry ?? null);

  protected readonly versionColumns = ['version', 'reason', 'algorithm', 'cost', 'compute', 'at', 'show'];
  protected readonly comparisonColumns = ['algorithm', 'duration', 'saved', 'compute'];

  constructor() {
    this.realtime
      .watchType<{ routeId: string }>('/topic/dispatch', 'STOP_STATUS_CHANGED', 'ROUTE_OPTIMIZED', 'ROUTE_COMPLETED')
      .pipe(
        filter((m) => m.payload.routeId === this.id()),
        debounceTime(500),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.load());
  }

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api.get(this.id()).subscribe((r) => this.route.set(r));
    this.api.versions(this.id()).subscribe((v) => this.versions.set(v));
  }

  protected compare(): void {
    this.comparing.set(true);
    this.api.compare(this.id()).subscribe({
      next: (c) => {
        this.comparison.set(c);
        this.comparing.set(false);
      },
      error: () => this.comparing.set(false),
    });
  }

  protected proof(stopId: string): void {
    this.delivery.proofFile(stopId).subscribe({ next: (b) => openBlob(b), error: () => alert('Entrega sin fichero (código o asistente)') });
  }

  protected delayMinutes(planned?: string, actual?: string): number {
    if (!planned || !actual) return 0;
    return (new Date(actual).getTime() - new Date(planned).getTime()) / 60000;
  }
}
