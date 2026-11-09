import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { AuthService, CatalogApi, Depot, problemMessage } from '@rutaexpress/shared';

@Component({
  selector: 'app-depots',
  imports: [ReactiveFormsModule, MatTableModule, MatInputModule, MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="rx-page-head"><div><h1>Almacenes</h1><p>Puntos de salida y vuelta de las rutas.</p></div></header>
    @if (isAdmin) {
      <form [formGroup]="form" (ngSubmit)="save()" class="grid">
        <mat-form-field><mat-label>Nombre</mat-label><input matInput formControlName="name" /></mat-form-field>
        <mat-form-field class="wide"><mat-label>Dirección</mat-label><input matInput formControlName="address" /></mat-form-field>
        <mat-form-field><mat-label>Latitud</mat-label><input matInput type="number" step="0.000001" formControlName="lat" /></mat-form-field>
        <mat-form-field><mat-label>Longitud</mat-label><input matInput type="number" step="0.000001" formControlName="lon" /></mat-form-field>
        <div class="actions">
          @if (editingId()) { <button mat-button type="button" (click)="reset()">Cancelar</button> }
          <button mat-flat-button [disabled]="form.invalid">{{ editingId() ? 'Guardar' : 'Añadir' }}</button>
        </div>
      </form>
    }
    <table mat-table [dataSource]="depots()">
      <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Nombre</th><td mat-cell *matCellDef="let d">{{ d.name }}</td></ng-container>
      <ng-container matColumnDef="address"><th mat-header-cell *matHeaderCellDef>Dirección</th><td mat-cell *matCellDef="let d">{{ d.address }}</td></ng-container>
      <ng-container matColumnDef="location"><th mat-header-cell *matHeaderCellDef>Ubicación</th><td mat-cell *matCellDef="let d">{{ d.location.lat.toFixed(5) }}, {{ d.location.lon.toFixed(5) }}</td></ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let d">@if (isAdmin) { <button mat-icon-button (click)="edit(d)" aria-label="Editar"><mat-icon>edit</mat-icon></button> }</td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>
  `,
  styles: `
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; margin-bottom: 16px; }
    .wide { grid-column: span 2; }
    .actions { display: flex; gap: 8px; align-items: center; }
  `,
})
export class DepotsPage {
  private readonly api = inject(CatalogApi);
  private readonly snack = inject(MatSnackBar);
  protected readonly isAdmin = inject(AuthService).hasRole('ADMIN');
  protected readonly depots = signal<Depot[]>([]);
  protected readonly editingId = signal<string | null>(null);
  protected readonly columns = ['name', 'address', 'location', 'actions'];
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    address: ['', Validators.required],
    lat: [39.4699, [Validators.required, Validators.min(-90), Validators.max(90)]],
    lon: [-0.3763, [Validators.required, Validators.min(-180), Validators.max(180)]],
  });

  constructor() {
    this.load();
  }

  private load(): void {
    this.api.depots().subscribe((d) => this.depots.set(d));
  }

  protected edit(d: Depot): void {
    this.editingId.set(d.id);
    this.form.setValue({ name: d.name, address: d.address, lat: d.location.lat, lon: d.location.lon });
  }

  protected reset(): void {
    this.editingId.set(null);
    this.form.reset();
  }

  protected save(): void {
    const v = this.form.getRawValue();
    this.api
      .saveDepot({ name: v.name, address: v.address, location: { lat: Number(v.lat), lon: Number(v.lon) } }, this.editingId() ?? undefined)
      .subscribe({
        next: () => {
          this.reset();
          this.load();
        },
        error: (e) => this.snack.open(problemMessage(e), 'Cerrar', { duration: 5000 }),
      });
  }
}
