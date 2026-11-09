import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { DistancePipe, LabelPipe, RealtimeService, RouteApi, RouteListItem } from '@rutaexpress/shared';
import { debounceTime } from 'rxjs';

@Component({
  selector: 'app-route-list',
  imports: [MatTableModule, MatProgressBarModule, RouterLink, DatePipe, LabelPipe, DistancePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="rx-page-head"><div><h1>Rutas de hoy</h1><p>Avance de cada reparto. Pulsa una ruta para ver su mapa, paradas y versiones.</p></div></header>
    <div class="table-wrap">
      <table mat-table [dataSource]="routes()">
        <ng-container matColumnDef="driver">
          <th mat-header-cell *matHeaderCellDef>Repartidor</th>
          <td mat-cell *matCellDef="let r"><a class="driver" [routerLink]="['/routes', r.id]"><span class="avatar">{{ initialsOf(r.driverName) }}</span>{{ r.driverName }}</a></td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Estado</th>
          <td mat-cell *matCellDef="let r"><span class="chip" [class]="r.status">{{ r.status | rxLabel }}</span></td>
        </ng-container>
        <ng-container matColumnDef="progress">
          <th mat-header-cell *matHeaderCellDef>Avance</th>
          <td mat-cell *matCellDef="let r" class="progress">
            <mat-progress-bar mode="determinate" [value]="(100 * (r.delivered + r.failed)) / (r.totalStops || 1)" />
            <small>{{ r.delivered }}/{{ r.totalStops }} · {{ r.failed }} fallidas</small>
          </td>
        </ng-container>
        <ng-container matColumnDef="distance">
          <th mat-header-cell *matHeaderCellDef>Distancia</th>
          <td mat-cell *matCellDef="let r">{{ r.totalDistanceMeters | rxDistance }}</td>
        </ng-container>
        <ng-container matColumnDef="algorithm">
          <th mat-header-cell *matHeaderCellDef>Plan</th>
          <td mat-cell *matCellDef="let r">
            @if (r.version > 1) { Recalculada {{ r.version - 1 }} {{ r.version === 2 ? 'vez' : 'veces' }} } @else { Optimizada }
          </td>
        </ng-container>
        <ng-container matColumnDef="end">
          <th mat-header-cell *matHeaderCellDef>Fin estimado</th>
          <td mat-cell *matCellDef="let r"><b>{{ r.estimatedEnd | date: 'HH:mm' }}</b></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
    @if (routes().length === 0) { <p class="rx-muted">No hay rutas todavía. Se crean al cerrar la carga en la app del repartidor.</p> }
  `,
  styles: `
    .table-wrap { overflow: auto; }
    .progress { min-width: 180px; }
    .progress small { color: var(--mat-sys-on-surface-variant); }
    .driver { display: inline-flex; align-items: center; gap: 10px; font-weight: 600; color: inherit; text-decoration: none; }
    .driver:hover { color: var(--mat-sys-primary); }
    .avatar { width: 30px; height: 30px; border-radius: 50%; display: grid; place-items: center; font-size: .72rem; font-weight: 700;
      background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); }
  `,
})
export class RouteListPage {
  private readonly api = inject(RouteApi);
  protected readonly routes = signal<RouteListItem[]>([]);

  protected initialsOf(name: string): string {
    return name.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase();
  }
  protected readonly columns = ['driver', 'status', 'progress', 'distance', 'algorithm', 'end'];

  constructor() {
    this.load();
    inject(RealtimeService)
      .watch('/topic/dispatch')
      .pipe(debounceTime(1500), takeUntilDestroyed())
      .subscribe(() => this.load());
  }

  private load(): void {
    this.api.today().subscribe((r) => this.routes.set(r));
  }
}
