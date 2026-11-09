import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { DeliveryApi, Incident, LabelPipe, RealtimeService } from '@rutaexpress/shared';

@Component({
  selector: 'app-incidents',
  imports: [MatTableModule, MatButtonModule, MatButtonToggleModule, MatIconModule, DatePipe, LabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="head">
      <div><h1>Incidencias</h1><p class="rx-muted">Avisos de los repartidores durante la ruta.</p></div>
      <mat-button-toggle-group [value]="onlyOpen()" (change)="onlyOpen.set($event.value); load()">
        <mat-button-toggle [value]="true">Abiertas</mat-button-toggle>
        <mat-button-toggle [value]="false">Todas (hoy)</mat-button-toggle>
      </mat-button-toggle-group>
    </header>
    <table mat-table [dataSource]="incidents()">
      <ng-container matColumnDef="at"><th mat-header-cell *matHeaderCellDef>Hora</th><td mat-cell *matCellDef="let i">{{ i.createdAt | date: 'HH:mm' }}</td></ng-container>
      <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Tipo</th><td mat-cell *matCellDef="let i"><span class="type"><mat-icon>{{ iconOf(i.type) }}</mat-icon>{{ i.type | rxLabel }}</span></td></ng-container>
      <ng-container matColumnDef="description"><th mat-header-cell *matHeaderCellDef>Descripción</th><td mat-cell *matCellDef="let i">{{ i.description }}</td></ng-container>
      <ng-container matColumnDef="source"><th mat-header-cell *matHeaderCellDef>Origen</th><td mat-cell *matCellDef="let i">{{ i.source | rxLabel }}</td></ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let i">
          @if (i.resolvedAt) { Resuelta por {{ i.resolvedBy }} } @else { <button mat-stroked-button (click)="resolve(i)">Resolver</button> }
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns" [class.open]="!row.resolvedAt"></tr>
    </table>
    @if (incidents().length === 0) { <p>Sin incidencias.</p> }
  `,
  styles: `
    .head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
    .head { justify-content: space-between; margin-bottom: 20px; }
    .head p { margin: 2px 0 0; }
    .open { box-shadow: inset 3px 0 0 var(--rx-warn); }
    .type { display: inline-flex; align-items: center; gap: 8px; font-weight: 600; }
    .type mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--rx-warn); }
  `,
})
export class IncidentsPage {
  private readonly api = inject(DeliveryApi);
  private readonly snack = inject(MatSnackBar);
  protected readonly incidents = signal<Incident[]>([]);
  protected readonly onlyOpen = signal(true);
  protected readonly columns = ['at', 'type', 'description', 'source', 'status'];

  constructor() {
    this.load();
    inject(RealtimeService)
      .watchType<Incident>('/topic/dispatch', 'INCIDENT_REPORTED')
      .pipe(takeUntilDestroyed())
      .subscribe((m) => {
        this.snack.open(`Nueva incidencia: ${m.payload.description}`, 'Ver', { duration: 8000 });
        this.load();
      });
  }

  protected iconOf(type: string): string {
    const icons: Record<string, string> = {
      TRAFFIC: 'traffic', BREAKDOWN: 'car_repair', ACCIDENT: 'car_crash', DELAY: 'schedule',
      ROAD_CLOSED: 'block', DAMAGED_PARCEL: 'broken_image',
    };
    return icons[type] ?? 'report';
  }

  protected load(): void {
    this.api.incidents(this.onlyOpen()).subscribe((i) => this.incidents.set(i));
  }

  protected resolve(i: Incident): void {
    this.api.resolveIncident(i.id).subscribe(() => this.load());
  }
}
