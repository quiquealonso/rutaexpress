import { DatePipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import {
  DeliveryApi,
  DistancePipe,
  FailReason,
  LabelPipe,
  ProofType,
  RouteApi,
  RouteMapComponent,
  problemMessage,
} from '@rutaexpress/shared';
import { SignaturePad } from '../components/signature-pad';
import { DriverStateService } from '../core/driver-state.service';
import { GeolocationService } from '../core/geolocation.service';
import { OfflineQueueService, isNetworkError } from '../core/offline-queue.service';

type Mode = 'view' | 'deliver' | 'fail';

@Component({
  selector: 'app-stop',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    SignaturePad,
    LowerCasePipe,
    DatePipe,
    LabelPipe,
    DistancePipe,
    RouteMapComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (stop(); as s) {
      <nav class="top">
        <button mat-icon-button (click)="back()" aria-label="Volver a la ruta"><mat-icon>arrow_back</mat-icon></button>
        <span>Parada {{ s.sequence }} de {{ total() }}</span>
      </nav>
      @if (state.route(); as r) {
        @defer {
          <rx-route-map class="mini" [route]="r" [selectedStopId]="s.id" [focusSelected]="true" [driverPosition]="gps.position()" />
        } @placeholder { <div class="mini"></div> }
      }
      <header class="addr">
        <span class="seq" [class]="s.status">{{ s.sequence }}</span>
        <div>
          <h2>{{ s.addressLine }}</h2>
          <small>{{ s.status | rxLabel }} · ETA {{ s.eta | date: 'HH:mm' }}
            @if (distance() !== null) { · a {{ distance() | rxDistance }} }
            @if (s.windowStart) { · franja {{ s.windowStart.slice(0, 5) }}–{{ s.windowEnd?.slice(0, 5) }} }</small>
        </div>
      </header>

      <section class="parcels">
        @for (p of s.parcels; track p.id) {
          <div class="parcel">
            <div class="who">
              <span class="avatar"><mat-icon>person</mat-icon></span>
              <div><b>{{ p.recipientName }}</b><code>{{ p.trackingCode }}</code></div>
            </div>
            @if (p.notes) { <div class="note"><mat-icon>sticky_note_2</mat-icon>{{ p.notes }}</div> }
            @if (p.recipientPhone) { <a mat-stroked-button class="call" [href]="'tel:' + p.recipientPhone"><mat-icon>call</mat-icon> Llamar</a> }
          </div>
        }
      </section>

      @if (s.status === 'PENDING' || s.status === 'SKIPPED') {
        @switch (mode()) {
          @case ('view') {
            <div class="nav-row">
              <a matButton="tonal" class="tall" [href]="mapsUrl()" target="_blank" rel="noopener"><mat-icon>navigation</mat-icon> Navegar</a>
              <a mat-stroked-button class="tall" [href]="wazeUrl()" target="_blank" rel="noopener">Waze</a>
            </div>
            <!-- Acciones de cierre abajo, al alcance del pulgar -->
            <div class="actions">
              <button mat-flat-button class="big ok" (click)="mode.set('deliver')"><mat-icon>check_circle</mat-icon> Entregar</button>
              <div class="pair">
                <button mat-stroked-button class="tall" (click)="mode.set('fail')"><mat-icon>person_off</mat-icon> Ausente / incidencia</button>
                <button mat-button class="tall" (click)="skip()" [disabled]="busy()"><mat-icon>skip_next</mat-icon> Al final</button>
              </div>
            </div>
          }
          @case ('deliver') {
            <div class="form">
              <mat-button-toggle-group [value]="proofType()" (change)="proofType.set($event.value)">
                <mat-button-toggle value="SIGNATURE">Firma</mat-button-toggle>
                <mat-button-toggle value="PHOTO">Foto</mat-button-toggle>
                <mat-button-toggle value="CODE">Sin prueba</mat-button-toggle>
              </mat-button-toggle-group>
              <mat-form-field><mat-label>Quién recoge</mat-label><input matInput [formControl]="receiver" /></mat-form-field>
              @if (proofType() === 'SIGNATURE') { <app-signature-pad #pad /> }
              @if (proofType() === 'PHOTO') {
                <input type="file" accept="image/*" capture="environment" (change)="onPhoto($event)" />
                @if (photoUrl()) { <img [src]="photoUrl()" alt="Foto de la entrega" class="photo" /> }
              }
              <div class="row">
                <button mat-button (click)="mode.set('view')">Cancelar</button>
                <button mat-flat-button class="ok" (click)="deliver()" [disabled]="busy()">Confirmar entrega</button>
              </div>
            </div>
          }
          @case ('fail') {
            <div class="form">
              <mat-form-field>
                <mat-label>Motivo</mat-label>
                <mat-select [formControl]="reason">
                  @for (r of reasons; track r) { <mat-option [value]="r">{{ r | rxLabel }}</mat-option> }
                </mat-select>
              </mat-form-field>
              <mat-form-field><mat-label>Nota (opcional)</mat-label><input matInput [formControl]="note" /></mat-form-field>
              <mat-checkbox [formControl]="retryLater">Reintentar más tarde en esta ruta</mat-checkbox>
              <div class="row">
                <button mat-button (click)="mode.set('view')">Cancelar</button>
                <button mat-flat-button color="warn" (click)="fail()" [disabled]="busy()">Registrar</button>
              </div>
            </div>
          }
        }
      } @else {
        <p class="closed">Parada {{ s.status | rxLabel | lowercase }} {{ s.completedAt | date: 'HH:mm' }}
          @if (s.failReason) { · {{ s.failReason }} }</p>
        <button mat-flat-button (click)="goNext()">Siguiente parada</button>
      }
    } @else {
      <p>Cargando…</p>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100%; }
    .top { position: absolute; z-index: 700; top: 8px; left: 8px; display: flex; align-items: center; gap: 2px; padding-right: 12px;
      border-radius: 999px; background: rgba(255, 255, 255, .95); box-shadow: var(--rx-shadow); font-size: .85rem; font-weight: 600; }
    :host { position: relative; }
    .mini { display: block; height: 210px; min-height: 210px; margin: -16px -16px 0; border-radius: 0; background: #eef1f4; }
    .addr { display: flex; gap: 12px; align-items: center; position: relative; margin: -14px 0 0; padding: 14px;
      border-radius: var(--rx-radius); background: var(--mat-sys-surface-container-lowest); border: 1px solid var(--rx-border);
      box-shadow: var(--rx-shadow); z-index: 500; }
    .addr small { color: var(--mat-sys-on-surface-variant); }
    h2 { margin: 0; font-size: 1.1rem; }
    .seq { width: 40px; height: 40px; border-radius: 50%; display: grid; place-items: center; color: #fff; font-weight: 700;
      background: var(--rx-navy-700); flex: none; }
    .seq.DELIVERED { background: var(--rx-ok); } .seq.FAILED { background: var(--rx-bad); } .seq.SKIPPED { background: var(--rx-warn); }
    .parcels { margin: 12px 0; display: grid; gap: 8px; }
    .parcel { padding: 12px; border-radius: var(--rx-radius); background: var(--mat-sys-surface-container-lowest); border: 1px solid var(--rx-border); }
    .who { display: flex; gap: 10px; align-items: center; }
    .who div { display: grid; }
    .who code { color: var(--mat-sys-on-surface-variant); font-size: .8rem; }
    .avatar { width: 36px; height: 36px; border-radius: 50%; display: grid; place-items: center; flex: none;
      background: var(--mat-sys-primary-container); color: var(--mat-sys-on-primary-container); }
    .note { display: flex; gap: 8px; align-items: flex-start; margin-top: 10px; padding: 8px 10px; border-radius: var(--rx-radius-sm);
      background: #fef9e7; color: #713f12; font-size: .9rem; }
    .note mat-icon { font-size: 18px; width: 18px; height: 18px; flex: none; color: #b45309; }
    .form { display: grid; gap: 10px; }
    .nav-row { display: grid; grid-template-columns: 2fr 1fr; gap: 10px; }
    .actions { display: grid; gap: 10px; margin: auto -16px -20px; position: sticky; bottom: -20px; padding: 14px 16px 20px;
      background: var(--mat-sys-surface-container-lowest); border-top: 1px solid var(--rx-border); box-shadow: 0 -4px 16px rgba(15, 23, 42, .05);
      z-index: 600; }
    .pair { display: grid; grid-template-columns: 1fr auto; gap: 10px; }
    .big { height: 64px; font-size: 1.15rem; }
    .tall { height: 48px; }
    .call { display: flex; width: 100%; margin-top: 8px; height: 44px; }
    .ok { background: var(--rx-ok) !important; color: #fff !important; }
    .row { display: flex; justify-content: space-between; }
    .photo { max-width: 100%; border-radius: 8px; }
    .closed { font-size: 1.05rem; }
  `,
})
export class StopPage {
  readonly id = input.required<string>();
  protected readonly state = inject(DriverStateService);
  private readonly delivery = inject(DeliveryApi);
  private readonly routes = inject(RouteApi);
  protected readonly gps = inject(GeolocationService);
  private readonly queue = inject(OfflineQueueService);
  private readonly snack = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly pad = viewChild<SignaturePad>('pad');

  protected readonly total = computed(() => this.state.route()?.stops.length ?? 0);
  protected readonly stop = computed(() => this.state.route()?.stops.find((s) => s.id === this.id()) ?? null);
  protected readonly distance = computed(() => {
    const p = this.gps.position();
    const s = this.stop();
    if (!p || !s) return null;
    const R = 6371000;
    const dLat = ((s.location.lat - p.lat) * Math.PI) / 180;
    const dLon = ((s.location.lon - p.lon) * Math.PI) / 180;
    const a = Math.sin(dLat / 2) ** 2 + Math.cos((p.lat * Math.PI) / 180) * Math.cos((s.location.lat * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(a));
  });
  protected readonly mapsUrl = computed(() => {
    const s = this.stop();
    return s ? `https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=${s.location.lat},${s.location.lon}` : '';
  });
  protected readonly wazeUrl = computed(() => {
    const s = this.stop();
    return s ? `https://waze.com/ul?ll=${s.location.lat},${s.location.lon}&navigate=yes` : '';
  });

  protected readonly mode = signal<Mode>('view');
  protected readonly proofType = signal<ProofType>('SIGNATURE');
  protected readonly busy = signal(false);
  protected readonly photo = signal<Blob | null>(null);
  protected readonly photoUrl = signal<string | null>(null);
  protected readonly receiver = new FormControl('', { nonNullable: true });
  protected readonly reason = new FormControl<FailReason>('ABSENT', { nonNullable: true });
  protected readonly note = new FormControl('', { nonNullable: true });
  protected readonly retryLater = new FormControl(false, { nonNullable: true });
  protected readonly reasons: FailReason[] = ['ABSENT', 'WRONG_ADDRESS', 'REFUSED', 'DAMAGED', 'INACCESSIBLE', 'OTHER'];

  constructor() {
    if (!this.state.route()) this.state.refresh();
  }

  /** Reduce la foto a 1600 px de lado y JPEG 0.8: suficiente como prueba y ligera para enviar con poca cobertura. */
  protected async onPhoto(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, 'image/jpeg', 0.8));
    this.photo.set(blob);
    this.photoUrl.set(blob ? URL.createObjectURL(blob) : null);
  }

  protected async deliver(): Promise<void> {
    const s = this.stop();
    if (!s) return;
    let file: Blob | undefined;
    if (this.proofType() === 'SIGNATURE') {
      if (this.pad()?.empty()) {
        this.snack.open('Falta la firma', 'OK', { duration: 3000 });
        return;
      }
      file = (await this.pad()?.toBlob()) ?? undefined;
    } else if (this.proofType() === 'PHOTO') {
      file = this.photo() ?? undefined;
      if (!file) {
        this.snack.open('Haz la foto de la entrega', 'OK', { duration: 3000 });
        return;
      }
    }
    this.busy.set(true);
    const position = await this.gps.current();
    const receiverName = this.receiver.value || undefined;
    this.delivery.deliver(s.id, this.proofType(), receiverName, file, position).subscribe({
      next: () => this.done('Entregado ✅'),
      error: (e) => {
        if (isNetworkError(e)) {
          void this.queue.enqueue({ kind: 'deliver', stopId: s.id, proofType: this.proofType(), receiverName, file, position });
          this.markLocally('DELIVERED');
          this.done('Entrega guardada: se enviará al recuperar cobertura');
        } else {
          this.busy.set(false);
          this.snack.open(problemMessage(e), 'OK', { duration: 5000 });
        }
      },
    });
  }

  protected async fail(): Promise<void> {
    const s = this.stop();
    if (!s) return;
    this.busy.set(true);
    const position = await this.gps.current();
    const op = { reason: this.reason.value, note: this.note.value || undefined, retryLater: this.retryLater.value };
    this.delivery.fail(s.id, op.reason, op.note, op.retryLater, position).subscribe({
      next: () => this.done(op.retryLater ? 'Anotado: se reintentará al final' : 'Entrega fallida registrada'),
      error: (e) => {
        if (isNetworkError(e)) {
          void this.queue.enqueue({ kind: 'fail', stopId: s.id, ...op, position });
          this.markLocally(op.retryLater ? 'SKIPPED' : 'FAILED');
          this.done('Guardado sin conexión');
        } else {
          this.busy.set(false);
          this.snack.open(problemMessage(e), 'OK', { duration: 5000 });
        }
      },
    });
  }

  protected skip(): void {
    const s = this.stop();
    if (!s) return;
    this.busy.set(true);
    this.delivery.skip(s.id, this.gps.position() ?? undefined).subscribe({
      next: (r) => {
        this.state.setRoute(r);
        this.done('Parada aplazada. Ruta recalculada.');
      },
      error: (e) => {
        this.busy.set(false);
        this.snack.open(problemMessage(e), 'OK', { duration: 5000 });
      },
    });
  }

  private markLocally(status: 'DELIVERED' | 'FAILED' | 'SKIPPED'): void {
    const route = this.state.route();
    if (route) {
      this.state.setRoute({ ...route, stops: route.stops.map((x) => (x.id === this.id() ? { ...x, status } : x)) });
    }
  }

  private done(message: string): void {
    this.busy.set(false);
    this.mode.set('view');
    this.snack.open(message, 'OK', { duration: 3000 });
    const route = this.state.route();
    if (route && navigator.onLine) {
      this.routes.get(route.id).subscribe((r) => {
        this.state.setRoute(r);
        this.goNext();
      });
    } else {
      this.goNext();
    }
  }

  protected back(): void {
    this.router.navigate(['/route']);
  }

  protected goNext(): void {
    const next = this.state.nextStop();
    this.router.navigate(next ? ['/stops', next.id] : ['/today'], { replaceUrl: true });
  }
}
