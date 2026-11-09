import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { CatalogApi, Depot, Driver, ParcelApi, ParcelRequest, problemMessage } from '@rutaexpress/shared';

@Component({
  selector: 'app-parcel-form',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatInputModule, MatSelectModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ id() ? 'Editar paquete' : 'Nuevo paquete' }}</h1>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-card>
        <mat-card-header><mat-card-title>Destinatario</mat-card-title></mat-card-header>
        <mat-card-content class="grid" formGroupName="recipient">
          <mat-form-field><mat-label>Nombre</mat-label><input matInput formControlName="name" /></mat-form-field>
          <mat-form-field><mat-label>Teléfono</mat-label><input matInput formControlName="phone" type="tel" /></mat-form-field>
          <mat-form-field><mat-label>Email</mat-label><input matInput formControlName="email" type="email" /></mat-form-field>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header>
          <mat-card-title>Dirección</mat-card-title>
          <mat-card-subtitle>Se geocodifica automáticamente; las direcciones dudosas quedan marcadas para revisión</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content class="grid" formGroupName="address">
          <mat-form-field class="wide"><mat-label>Calle</mat-label><input matInput formControlName="street" /></mat-form-field>
          <mat-form-field><mat-label>Número</mat-label><input matInput formControlName="number" /></mat-form-field>
          <mat-form-field><mat-label>Piso, puerta…</mat-label><input matInput formControlName="extra" /></mat-form-field>
          <mat-form-field><mat-label>Código postal</mat-label><input matInput formControlName="postalCode" maxlength="5" /></mat-form-field>
          <mat-form-field><mat-label>Ciudad</mat-label><input matInput formControlName="city" /></mat-form-field>
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header><mat-card-title>Paquete</mat-card-title></mat-card-header>
        <mat-card-content class="grid">
          <mat-form-field><mat-label>Peso (kg)</mat-label><input matInput formControlName="weightKg" type="number" min="0.01" step="0.01" /></mat-form-field>
          <mat-form-field><mat-label>Largo (cm)</mat-label><input matInput formControlName="lengthCm" type="number" /></mat-form-field>
          <mat-form-field><mat-label>Ancho (cm)</mat-label><input matInput formControlName="widthCm" type="number" /></mat-form-field>
          <mat-form-field><mat-label>Alto (cm)</mat-label><input matInput formControlName="heightCm" type="number" /></mat-form-field>
          <mat-form-field><mat-label>Franja desde</mat-label><input matInput formControlName="windowStart" type="time" /></mat-form-field>
          <mat-form-field><mat-label>Franja hasta</mat-label><input matInput formControlName="windowEnd" type="time" /></mat-form-field>
          <mat-form-field>
            <mat-label>Almacén</mat-label>
            <mat-select formControlName="depotId">
              @for (d of depots(); track d.id) { <mat-option [value]="d.id">{{ d.name }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Repartidor (opcional)</mat-label>
            <mat-select formControlName="assignedDriverId">
              <mat-option [value]="null">Según la zona</mat-option>
              @for (d of drivers(); track d.id) { <mat-option [value]="d.id">{{ d.name }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field class="wide"><mat-label>Notas para el repartidor</mat-label><textarea matInput formControlName="notes" rows="2"></textarea></mat-form-field>
        </mat-card-content>
      </mat-card>

      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      <div class="actions">
        <a mat-button routerLink="/parcels">Cancelar</a>
        <button mat-flat-button type="submit" [disabled]="form.invalid || saving()">Guardar</button>
      </div>
    </form>
  `,
  styles: `
    form { display: grid; gap: 16px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; padding-top: 12px; }
    .wide { grid-column: span 2; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; }
    .error { color: var(--mat-sys-error); }
    @media (max-width: 600px) { .wide { grid-column: auto; } }
  `,
})
export class ParcelFormPage implements OnInit {
  readonly id = input<string>();
  private readonly api = inject(ParcelApi);
  private readonly catalog = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  protected readonly depots = signal<Depot[]>([]);
  protected readonly drivers = signal<Driver[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).group({
    recipient: inject(FormBuilder).nonNullable.group({
      name: ['', [Validators.required, Validators.maxLength(120)]],
      phone: [''],
      email: ['', Validators.email],
    }),
    address: inject(FormBuilder).nonNullable.group({
      street: ['', Validators.required],
      number: [''],
      extra: [''],
      postalCode: ['', [Validators.required, Validators.pattern(/^\d{5}$/)]],
      city: ['Valencia', Validators.required],
    }),
    weightKg: [1, [Validators.required, Validators.min(0.01)]],
    lengthCm: [null as number | null],
    widthCm: [null as number | null],
    heightCm: [null as number | null],
    windowStart: [''],
    windowEnd: [''],
    depotId: ['', Validators.required],
    assignedDriverId: [null as string | null],
    notes: [''],
  });

  ngOnInit(): void {
    this.catalog.depots().subscribe((d) => {
      this.depots.set(d);
      if (!this.form.controls.depotId.value && d.length > 0) this.form.controls.depotId.setValue(d[0].id);
    });
    this.catalog.drivers().subscribe((p) => this.drivers.set(p.content));
    const id = this.id();
    if (id) {
      this.api.get(id).subscribe((p) =>
        this.form.patchValue({
          recipient: { name: p.recipient.name, phone: p.recipient.phone ?? '', email: p.recipient.email ?? '' },
          address: {
            street: p.address.street,
            number: p.address.number ?? '',
            extra: p.address.extra ?? '',
            postalCode: p.address.postalCode,
            city: p.address.city,
          },
          weightKg: p.weightKg,
          lengthCm: p.lengthCm ?? null,
          widthCm: p.widthCm ?? null,
          heightCm: p.heightCm ?? null,
          windowStart: p.windowStart?.slice(0, 5) ?? '',
          windowEnd: p.windowEnd?.slice(0, 5) ?? '',
          depotId: p.depotId,
          assignedDriverId: p.assignedDriverId ?? null,
          notes: p.notes ?? '',
        }),
      );
    }
  }

  protected save(): void {
    const v = this.form.getRawValue();
    const blank = (s: string | null | undefined) => (s ? s : undefined);
    const request: ParcelRequest = {
      recipient: { name: v.recipient.name, phone: blank(v.recipient.phone), email: blank(v.recipient.email) },
      address: { ...v.address, number: blank(v.address.number), extra: blank(v.address.extra), country: 'España' },
      weightKg: Number(v.weightKg),
      lengthCm: v.lengthCm ?? undefined,
      widthCm: v.widthCm ?? undefined,
      heightCm: v.heightCm ?? undefined,
      windowStart: blank(v.windowStart),
      windowEnd: blank(v.windowEnd),
      notes: blank(v.notes),
      depotId: v.depotId ?? undefined,
      assignedDriverId: v.assignedDriverId ?? undefined,
    };
    this.saving.set(true);
    this.error.set(null);
    const id = this.id();
    (id ? this.api.update(id, request) : this.api.create(request)).subscribe({
      next: (p) => {
        this.snack.open(
          p.address.needsReview ? 'Guardado. La dirección necesita revisión.' : `Paquete ${p.trackingCode} guardado`,
          'OK',
          { duration: 4000 },
        );
        this.router.navigate(['/parcels', p.id]);
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(problemMessage(e));
      },
    });
  }
}
