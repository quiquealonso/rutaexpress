import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CatalogApi, Depot, GeoPoint, Zone, createMap, escapeHtml, problemMessage, toLatLng } from '@rutaexpress/shared';
import * as L from 'leaflet';
import { forkJoin } from 'rxjs';

/** Las zonas se dibujan sobre el mapa: cada clic añade un vértice del polígono. */
@Component({
  selector: 'app-zones',
  imports: [ReactiveFormsModule, MatCardModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, MatListModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="rx-page-head"><div><h1>Zonas de reparto</h1><p>Áreas que se asignan a cada repartidor.</p></div></header>
    <div class="layout">
      <div #map class="map"></div>
      <mat-card class="side">
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save()" class="form">
            <p class="hint">{{ editingId() ? 'Editando zona.' : 'Haz clic en el mapa para dibujar la zona.' }} Vértices: {{ vertices().length }}</p>
            <mat-form-field><mat-label>Nombre</mat-label><input matInput formControlName="name" /></mat-form-field>
            <mat-form-field>
              <mat-label>Almacén</mat-label>
              <mat-select formControlName="depotId">@for (d of depots(); track d.id) { <mat-option [value]="d.id">{{ d.name }}</mat-option> }</mat-select>
            </mat-form-field>
            <mat-form-field><mat-label>Color</mat-label><input matInput type="color" formControlName="color" /></mat-form-field>
            <div class="actions">
              <button mat-button type="button" (click)="clearDrawing()">Limpiar</button>
              <button mat-flat-button [disabled]="form.invalid || vertices().length < 3">Guardar zona</button>
            </div>
          </form>
          <mat-list>
            @for (z of zones(); track z.id) {
              <mat-list-item>
                <span matListItemTitle><span class="dot" [style.background]="z.color"></span>{{ z.name }}</span>
                <span matListItemLine>{{ z.depotName }}</span>
                <div matListItemMeta>
                  <button mat-icon-button (click)="edit(z)" aria-label="Editar"><mat-icon>edit</mat-icon></button>
                  <button mat-icon-button (click)="remove(z)" aria-label="Eliminar"><mat-icon>delete</mat-icon></button>
                </div>
              </mat-list-item>
            }
          </mat-list>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .layout { display: grid; grid-template-columns: 1fr 340px; gap: 16px; height: calc(100vh - 180px); }
    .map { border-radius: 12px; overflow: hidden; min-height: 400px; cursor: crosshair; }
    .side { overflow: auto; }
    .form { display: grid; gap: 8px; }
    .hint { margin: 0; color: var(--mat-sys-on-surface-variant); }
    .actions { display: flex; justify-content: flex-end; gap: 8px; }
    .dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; height: auto; } .map { height: 55vh; } }
  `,
})
export class ZonesPage {
  private readonly api = inject(CatalogApi);
  private readonly snack = inject(MatSnackBar);
  private readonly mapEl = viewChild.required<ElementRef<HTMLElement>>('map');
  protected readonly zones = signal<Zone[]>([]);
  protected readonly depots = signal<Depot[]>([]);
  protected readonly vertices = signal<GeoPoint[]>([]);
  protected readonly editingId = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    depotId: ['', Validators.required],
    color: ['#1976d2'],
  });

  private map?: L.Map;
  private readonly zonesLayer = L.layerGroup();
  private readonly drawLayer = L.layerGroup();

  constructor() {
    afterNextRender(() => {
      this.map = createMap(this.mapEl().nativeElement, undefined, 13);
      this.zonesLayer.addTo(this.map);
      this.drawLayer.addTo(this.map);
      this.map.on('click', (e: L.LeafletMouseEvent) => {
        this.vertices.update((v) => [...v, { lat: e.latlng.lat, lon: e.latlng.lng }]);
        this.drawCurrent();
      });
      this.load();
    });
    inject(DestroyRef).onDestroy(() => this.map?.remove());
  }

  private load(): void {
    forkJoin([this.api.zones(), this.api.depots()]).subscribe(([z, d]) => {
      this.zones.set(z);
      this.depots.set(d);
      if (!this.form.controls.depotId.value && d.length) this.form.controls.depotId.setValue(d[0].id);
      this.zonesLayer.clearLayers();
      z.forEach((zone) =>
        L.polygon(zone.area.map(toLatLng), { color: zone.color, weight: 2, fillOpacity: 0.12 })
          .bindTooltip(escapeHtml(zone.name))
          .addTo(this.zonesLayer),
      );
    });
  }

  private drawCurrent(): void {
    this.drawLayer.clearLayers();
    const v = this.vertices();
    if (v.length > 0) {
      L.polygon(v.map(toLatLng), { color: this.form.controls.color.value, dashArray: '6 6' }).addTo(this.drawLayer);
      v.forEach((p) => L.circleMarker(toLatLng(p), { radius: 4 }).addTo(this.drawLayer));
    }
  }

  protected clearDrawing(): void {
    this.vertices.set([]);
    this.editingId.set(null);
    this.drawLayer.clearLayers();
  }

  protected edit(z: Zone): void {
    this.editingId.set(z.id);
    this.form.setValue({ name: z.name, depotId: z.depotId, color: z.color });
    this.vertices.set(z.area);
    this.drawCurrent();
  }

  protected save(): void {
    const v = this.form.getRawValue();
    this.api.saveZone({ ...v, area: this.vertices() }, this.editingId() ?? undefined).subscribe({
      next: () => {
        this.snack.open('Zona guardada', 'OK', { duration: 3000 });
        this.clearDrawing();
        this.form.controls.name.reset();
        this.load();
      },
      error: (e) => this.snack.open(problemMessage(e), 'Cerrar', { duration: 5000 }),
    });
  }

  protected remove(z: Zone): void {
    if (!confirm(`¿Eliminar la zona ${z.name}?`)) return;
    this.api.deleteZone(z.id).subscribe(() => this.load());
  }
}
