import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { CatalogApi, Depot, Vehicle, problemMessage } from '@rutaexpress/shared';

@Component({
  selector: 'app-vehicles',
  imports: [ReactiveFormsModule, MatTableModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, MatCheckboxModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="rx-page-head"><div><h1>Vehículos</h1><p>Flota disponible y su capacidad de carga.</p></div></header>
    <form [formGroup]="form" (ngSubmit)="save()" class="grid">
      <mat-form-field><mat-label>Matrícula</mat-label><input matInput formControlName="plate" /></mat-form-field>
      <mat-form-field><mat-label>Carga (kg)</mat-label><input matInput type="number" formControlName="capacityKg" /></mat-form-field>
      <mat-form-field><mat-label>Volumen (m³)</mat-label><input matInput type="number" formControlName="capacityM3" /></mat-form-field>
      <mat-form-field><mat-label>Bultos</mat-label><input matInput type="number" formControlName="capacityParcels" /></mat-form-field>
      <mat-form-field>
        <mat-label>Almacén</mat-label>
        <mat-select formControlName="depotId">@for (d of depots(); track d.id) { <mat-option [value]="d.id">{{ d.name }}</mat-option> }</mat-select>
      </mat-form-field>
      <mat-checkbox formControlName="active">Activo</mat-checkbox>
      <div class="actions">
        @if (editingId()) { <button mat-button type="button" (click)="reset()">Cancelar</button> }
        <button mat-flat-button [disabled]="form.invalid">{{ editingId() ? 'Guardar' : 'Añadir' }}</button>
      </div>
    </form>
    <table mat-table [dataSource]="vehicles()">
      <ng-container matColumnDef="plate"><th mat-header-cell *matHeaderCellDef>Matrícula</th><td mat-cell *matCellDef="let v">{{ v.plate }}</td></ng-container>
      <ng-container matColumnDef="capacity"><th mat-header-cell *matHeaderCellDef>Capacidad</th><td mat-cell *matCellDef="let v">{{ v.capacityKg }} kg · {{ v.capacityM3 }} m³ · {{ v.capacityParcels }} bultos</td></ng-container>
      <ng-container matColumnDef="depot"><th mat-header-cell *matHeaderCellDef>Almacén</th><td mat-cell *matCellDef="let v">{{ v.depotName }}</td></ng-container>
      <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Activo</th><td mat-cell *matCellDef="let v">{{ v.active ? 'Sí' : 'No' }}</td></ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let v">
          <button mat-icon-button (click)="edit(v)" aria-label="Editar"><mat-icon>edit</mat-icon></button>
          <button mat-icon-button (click)="remove(v)" aria-label="Eliminar"><mat-icon>delete</mat-icon></button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>
  `,
  styles: `
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 12px; align-items: center; margin-bottom: 16px; }
    .actions { display: flex; gap: 8px; justify-content: flex-end; }
  `,
})
export class VehiclesPage {
  private readonly api = inject(CatalogApi);
  private readonly snack = inject(MatSnackBar);
  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly depots = signal<Depot[]>([]);
  protected readonly editingId = signal<string | null>(null);
  protected readonly columns = ['plate', 'capacity', 'depot', 'active', 'actions'];
  protected readonly form = inject(FormBuilder).nonNullable.group({
    plate: ['', [Validators.required, Validators.maxLength(15)]],
    capacityKg: [1000, [Validators.required, Validators.min(1)]],
    capacityM3: [8, [Validators.required, Validators.min(0.1)]],
    capacityParcels: [120, [Validators.required, Validators.min(1)]],
    depotId: ['', Validators.required],
    active: [true],
  });

  constructor() {
    this.api.depots().subscribe((d) => {
      this.depots.set(d);
      if (d.length) this.form.controls.depotId.setValue(d[0].id);
    });
    this.load();
  }

  private load(): void {
    this.api.vehicles().subscribe((p) => this.vehicles.set(p.content));
  }

  protected edit(v: Vehicle): void {
    this.editingId.set(v.id);
    this.form.patchValue(v);
  }

  protected reset(): void {
    this.editingId.set(null);
    this.form.reset({ depotId: this.depots()[0]?.id ?? '' });
  }

  protected save(): void {
    this.api.saveVehicle(this.form.getRawValue(), this.editingId() ?? undefined).subscribe({
      next: () => {
        this.reset();
        this.load();
      },
      error: (e) => this.snack.open(problemMessage(e), 'Cerrar', { duration: 5000 }),
    });
  }

  protected remove(v: Vehicle): void {
    if (!confirm(`¿Eliminar el vehículo ${v.plate}?`)) return;
    this.api.deleteVehicle(v.id).subscribe({
      next: () => this.load(),
      error: (e) => this.snack.open(problemMessage(e, 'No se puede eliminar: tiene repartos asociados'), 'Cerrar', { duration: 5000 }),
    });
  }
}
