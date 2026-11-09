import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { LabelPipe, Parcel, ParcelApi, ParcelEvent, createMap, problemMessage, stopMarker } from '@rutaexpress/shared';
import type * as L from 'leaflet';
import { forkJoin } from 'rxjs';
import { openBlob } from '../../core/files';

@Component({
  selector: 'app-parcel-detail',
  imports: [DatePipe, RouterLink, MatCardModule, MatButtonModule, MatIconModule, LabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (parcel(); as p) {
      <header class="head">
        <h1><span class="code">{{ p.trackingCode }}</span> · {{ p.status | rxLabel }}</h1>
        <span class="spacer"></span>
        <button mat-stroked-button (click)="label()"><mat-icon>qr_code</mat-icon> Etiqueta</button>
        @if (p.status === 'REGISTERED' || p.status === 'FAILED_ATTEMPT') {
          <a mat-stroked-button [routerLink]="['/parcels', p.id, 'edit']"><mat-icon>edit</mat-icon> Editar</a>
          <button mat-stroked-button color="warn" (click)="cancel()"><mat-icon>block</mat-icon> Cancelar</button>
        }
      </header>
      <div class="grid">
        <mat-card>
          <mat-card-content>
            <h3>{{ p.recipient.name }}</h3>
            <p>{{ p.address.oneLine }}</p>
            @if (p.address.needsReview) {
              <p class="warn"><mat-icon>warning</mat-icon> Geocodificación con baja confianza ({{ ((p.address.geocodeQuality ?? 0) * 100).toFixed(0) }} %). Revisa la dirección.</p>
            }
            <p>{{ p.recipient.phone }} {{ p.recipient.email }}</p>
            <p>{{ p.weightKg }} kg
              @if (p.lengthCm) { · {{ p.lengthCm }}×{{ p.widthCm }}×{{ p.heightCm }} cm }
              @if (p.windowStart) { · franja {{ p.windowStart.slice(0, 5) }}–{{ p.windowEnd?.slice(0, 5) }} }
            </p>
            @if (p.notes) { <p><em>{{ p.notes }}</em></p> }
            <p>Intentos de entrega: {{ p.deliveryAttempts }}</p>
          </mat-card-content>
        </mat-card>
        <div #map class="map"></div>
      </div>
      <mat-card>
        <mat-card-header><mat-card-title>Historial</mat-card-title></mat-card-header>
        <mat-card-content>
          <ol class="timeline">
            @for (e of events(); track e.id) {
              <li>
                <strong>{{ e.type | rxLabel }}</strong> · {{ e.occurredAt | date: 'dd/MM HH:mm:ss' }}
                <small>· {{ e.actor }} ({{ e.source }})</small>
                @if (e.data?.['reason']) { <div class="reason">{{ e.data?.['reason'] }}</div> }
              </li>
            }
          </ol>
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: `
    .head { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .spacer { flex: 1; }
    .code { font-family: monospace; }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
    .map { min-height: 300px; border-radius: 12px; overflow: hidden; }
    .warn { color: var(--rx-warn); display: flex; gap: 4px; align-items: center; }
    .timeline li { margin: 6px 0; }
    .reason { color: var(--mat-sys-on-surface-variant); }
    @media (max-width: 800px) { .grid { grid-template-columns: 1fr; } }
  `,
})
export class ParcelDetailPage implements OnInit, OnDestroy {
  readonly id = input.required<string>();
  private readonly api = inject(ParcelApi);
  private readonly snack = inject(MatSnackBar);
  private readonly mapEl = viewChild<ElementRef<HTMLElement>>('map');
  private map?: L.Map;

  protected readonly parcel = signal<Parcel | null>(null);
  protected readonly events = signal<ParcelEvent[]>([]);

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    forkJoin([this.api.get(this.id()), this.api.events(this.id())]).subscribe(([p, e]) => {
      this.parcel.set(p);
      this.events.set(e);
      // El contenedor del mapa aparece en el siguiente ciclo de render
      queueMicrotask(() => setTimeout(() => this.drawMap(p)));
    });
  }

  private drawMap(p: Parcel): void {
    const el = this.mapEl()?.nativeElement;
    const location = p.address.location;
    if (!el || !location || this.map) return;
    this.map = createMap(el, location, 16);
    stopMarker(location, '📦', 'PENDING').addTo(this.map);
  }

  protected label(): void {
    this.api.label(this.id()).subscribe((b) => openBlob(b));
  }

  protected cancel(): void {
    if (!confirm('¿Cancelar el paquete? No se podrá repartir.')) return;
    this.api.cancel(this.id()).subscribe({
      next: () => this.reload(),
      error: (e) => this.snack.open(problemMessage(e), 'Cerrar', { duration: 5000 }),
    });
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
