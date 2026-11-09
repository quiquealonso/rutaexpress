import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { CatalogApi, Depot, Driver, LabelPipe, Vehicle, Zone, problemMessage } from '@rutaexpress/shared';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-drivers',
  imports: [ReactiveFormsModule, MatTableModule, MatExpansionModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, LabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="rx-page-head"><div><h1>Repartidores</h1><p>Equipo de reparto, su zona y su vehículo habitual.</p></div></header>
    <mat-expansion-panel [expanded]="editing() !== null" (closed)="reset()">
      <mat-expansion-panel-header>
        <mat-panel-title>{{ editing() ? 'Editar repartidor' : 'Nuevo repartidor' }}</mat-panel-title>
      </mat-expansion-panel-header>
      <form [formGroup]="form" (ngSubmit)="save()" class="grid">
        <mat-form-field><mat-label>Nombre</mat-label><input matInput formControlName="name" /></mat-form-field>
        <mat-form-field><mat-label>Teléfono</mat-label><input matInput formControlName="phone" /></mat-form-field>
        <mat-form-field><mat-label>Carné</mat-label><input matInput formControlName="licenseNumber" /></mat-form-field>
        <mat-form-field>
          <mat-label>Almacén</mat-label>
          <mat-select formControlName="depotId">@for (d of depots(); track d.id) { <mat-option [value]="d.id">{{ d.name }}</mat-option> }</mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Zona</mat-label>
          <mat-select formControlName="zoneId"><mat-option [value]="null">Sin zona</mat-option>@for (z of zones(); track z.id) { <mat-option [value]="z.id">{{ z.name }}</mat-option> }</mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Vehículo habitual</mat-label>
          <mat-select formControlName="defaultVehicleId"><mat-option [value]="null">Ninguno</mat-option>@for (v of vehicles(); track v.id) { <mat-option [value]="v.id">{{ v.plate }}</mat-option> }</mat-select>
        </mat-form-field>
        @if (editing()) {
          <mat-form-field>
            <mat-label>Estado</mat-label>
            <mat-select formControlName="status">
              @for (s of ['ACTIVE', 'ON_ROUTE', 'OFF_DUTY', 'INACTIVE']; track s) { <mat-option [value]="s">{{ s | rxLabel }}</mat-option> }
            </mat-select>
          </mat-form-field>
        } @else {
          <mat-form-field><mat-label>Email de acceso</mat-label><input matInput formControlName="email" type="email" /></mat-form-field>
          <mat-form-field><mat-label>Contraseña inicial (mín. 10)</mat-label><input matInput formControlName="password" type="password" autocomplete="new-password" /></mat-form-field>
        }
        <div class="actions">
          <button mat-button type="button" (click)="reset()">Cancelar</button>
          <button mat-flat-button [disabled]="form.invalid">Guardar</button>
        </div>
      </form>
    </mat-expansion-panel>

    <table mat-table [dataSource]="drivers()">
      <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Nombre</th><td mat-cell *matCellDef="let d">{{ d.name }}</td></ng-container>
      <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Estado</th><td mat-cell *matCellDef="let d">{{ d.status | rxLabel }}</td></ng-container>
      <ng-container matColumnDef="zone"><th mat-header-cell *matHeaderCellDef>Zona</th><td mat-cell *matCellDef="let d">{{ d.zoneName ?? '—' }}</td></ng-container>
      <ng-container matColumnDef="vehicle"><th mat-header-cell *matHeaderCellDef>Vehículo</th><td mat-cell *matCellDef="let d">{{ d.defaultVehiclePlate ?? '—' }}</td></ng-container>
      <ng-container matColumnDef="phone"><th mat-header-cell *matHeaderCellDef>Teléfono</th><td mat-cell *matCellDef="let d">{{ d.phone }}</td></ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let d">
          <button mat-icon-button (click)="edit(d)" aria-label="Editar"><mat-icon>edit</mat-icon></button>
          <button mat-icon-button (click)="deactivate(d)" aria-label="Dar de baja"><mat-icon>person_off</mat-icon></button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>
  `,
  styles: `
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
    .actions { display: flex; gap: 8px; align-items: center; justify-content: flex-end; grid-column: 1 / -1; }
    table { margin-top: 16px; }
  `,
})
export class DriversPage {
  private readonly api = inject(CatalogApi);
  private readonly snack = inject(MatSnackBar);
  protected readonly drivers = signal<Driver[]>([]);
  protected readonly depots = signal<Depot[]>([]);
  protected readonly zones = signal<Zone[]>([]);
  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly editing = signal<Driver | null>(null);
  protected readonly columns = ['name', 'status', 'zone', 'vehicle', 'phone', 'actions'];

  protected readonly form = inject(FormBuilder).group({
    name: ['', Validators.required],
    phone: [''],
    licenseNumber: [''],
    depotId: ['', Validators.required],
    zoneId: [null as string | null],
    defaultVehicleId: [null as string | null],
    status: ['ACTIVE'],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10)]],
  });

  constructor() {
    forkJoin([this.api.depots(), this.api.zones(), this.api.vehicles()]).subscribe(([d, z, v]) => {
      this.depots.set(d);
      this.zones.set(z);
      this.vehicles.set(v.content);
    });
    this.load();
  }

  private load(): void {
    this.api.drivers().subscribe((p) => this.drivers.set(p.content));
  }

  protected edit(d: Driver): void {
    this.editing.set(d);
    this.form.patchValue({ ...d, zoneId: d.zoneId ?? null, defaultVehicleId: d.defaultVehicleId ?? null });
    this.form.controls.email.disable();
    this.form.controls.password.disable();
  }

  protected reset(): void {
    this.editing.set(null);
    this.form.reset({ status: 'ACTIVE' });
    this.form.controls.email.enable();
    this.form.controls.password.enable();
  }

  protected save(): void {
    const v = this.form.getRawValue();
    const current = this.editing();
    const req = current
      ? this.api.updateDriver(current.id, v)
      : this.api.createDriver(v);
    req.subscribe({
      next: () => {
        this.snack.open('Repartidor guardado', 'OK', { duration: 3000 });
        this.reset();
        this.load();
      },
      error: (e) => this.snack.open(problemMessage(e), 'Cerrar', { duration: 5000 }),
    });
  }

  protected deactivate(d: Driver): void {
    if (!confirm(`¿Dar de baja a ${d.name}? Se desactivará también su acceso.`)) return;
    this.api.deactivateDriver(d.id).subscribe(() => this.load());
  }
}
