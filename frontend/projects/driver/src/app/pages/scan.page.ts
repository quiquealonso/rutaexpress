import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { LoadApi, LoadSummary, ScanResult, problemMessage } from '@rutaexpress/shared';
import type { IScannerControls } from '@zxing/browser';
import { DriverStateService } from '../core/driver-state.service';
import { scanFeedback, uuid } from '../core/feedback';
import { OfflineQueueService, isNetworkError } from '../core/offline-queue.service';

interface ScanLine {
  code: string;
  result: ScanResult | 'QUEUED';
  message: string;
}

interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<{ rawValue: string }[]>;
}

/** Escáner de carga: cámara (BarcodeDetector nativo o ZXing), contador y avisos de duplicados. */
@Component({
  selector: 'app-scan',
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatInputModule, MatExpansionModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (load(); as l) {
      <div class="counter" [class.complete]="l.scannedCount >= l.expectedCount && l.expectedCount > 0">
        <span class="big">{{ l.scannedCount }}</span> / {{ l.expectedCount }} <small>escaneados / esperados</small>
      </div>

      @if (l.status === 'OPEN') {
        <div class="camera">
          <video #video playsinline muted></video>
          @if (!scanning()) {
            <button mat-flat-button class="start" (click)="startCamera()"><mat-icon>photo_camera</mat-icon> Activar cámara</button>
          }
          @if (flash(); as f) { <div class="flash" [class]="f.result">{{ f.message }}</div> }
        </div>

        <form class="manual" (ngSubmit)="manual()">
          <mat-form-field><mat-label>Código manual</mat-label><input matInput [formControl]="code" autocapitalize="characters" /></mat-form-field>
          <button mat-stroked-button [disabled]="!code.value">Añadir</button>
        </form>

        <ul class="log">
          @for (s of lines(); track $index) {
            <li [class]="s.result"><b>{{ s.code }}</b> · {{ s.message }}</li>
          }
        </ul>

        @if (l.missing.length > 0) {
          <mat-expansion-panel>
            <mat-expansion-panel-header>Faltan {{ l.missing.length }}</mat-expansion-panel-header>
            @for (m of l.missing; track m.parcelId) { <div class="missing"><b>{{ m.trackingCode }}</b> · {{ m.addressLine }}</div> }
          </mat-expansion-panel>
        }

        <button mat-flat-button class="close" (click)="close()" [disabled]="l.scannedCount === 0 || closing() || queue.pending() > 0">
          <mat-icon>lock</mat-icon> Cerrar carga y calcular ruta
        </button>
        @if (queue.pending() > 0) { <p class="muted">Hay escaneos pendientes de enviar: espera a tener cobertura.</p> }
      } @else {
        <p>Carga cerrada. La ruta se está calculando…</p>
        <button mat-flat-button (click)="router.navigate(['/today'])">Volver</button>
      }
    } @else {
      <p>No tienes una carga abierta.</p>
      <button mat-flat-button (click)="openLoad()">Empezar la carga</button>
    }
  `,
  styles: `
    .counter { text-align: center; padding: 8px; border-radius: 12px; background: var(--mat-sys-surface-container-high); }
    .counter.complete { background: #c8e6c9; }
    .big { font-size: 2.4rem; font-weight: 700; }
    .camera { position: relative; margin: 12px 0; border-radius: 12px; overflow: hidden; background: #000; aspect-ratio: 4 / 3; }
    video { width: 100%; height: 100%; object-fit: cover; }
    .start { position: absolute; inset: 0; margin: auto; width: 220px; height: 52px; }
    .flash { position: absolute; bottom: 0; left: 0; right: 0; padding: 12px; color: #fff; font-weight: 600; text-align: center; }
    .flash.OK { background: rgba(46, 125, 50, .9); }
    .flash.DUPLICATE, .flash.QUEUED { background: rgba(249, 168, 37, .95); color: #000; }
    .flash.UNKNOWN, .flash.NOT_EXPECTED { background: rgba(198, 40, 40, .9); }
    .manual { display: flex; gap: 8px; align-items: center; }
    .manual mat-form-field { flex: 1; }
    .log { list-style: none; padding: 0; margin: 8px 0; max-height: 160px; overflow: auto; font-size: .9rem; }
    .log li { padding: 4px 8px; border-left: 4px solid; margin-bottom: 2px; }
    .log .OK { border-color: var(--rx-ok); } .log .DUPLICATE, .log .QUEUED { border-color: var(--rx-warn); } .log .UNKNOWN, .log .NOT_EXPECTED { border-color: var(--rx-bad); }
    .missing { font-size: .85rem; padding: 2px 0; }
    .close { width: 100%; height: 56px; margin-top: 12px; }
    .muted { color: var(--mat-sys-on-surface-variant); }
  `,
})
export class ScanPage {
  private readonly loads = inject(LoadApi);
  private readonly state = inject(DriverStateService);
  private readonly snack = inject(MatSnackBar);
  protected readonly queue = inject(OfflineQueueService);
  protected readonly router = inject(Router);
  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');

  protected readonly load = computed(() => this.state.load());
  protected readonly lines = signal<ScanLine[]>([]);
  protected readonly flash = signal<ScanLine | null>(null);
  protected readonly scanning = signal(false);
  protected readonly closing = signal(false);
  protected readonly code = new FormControl('', { nonNullable: true });

  private controls?: IScannerControls;
  private stream?: MediaStream;
  private detectorLoop = 0;
  private lastCode = '';
  private lastAt = 0;
  private flashTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    if (!this.state.load()) this.state.refresh();
    inject(DestroyRef).onDestroy(() => this.stopCamera());
  }

  protected openLoad(): void {
    this.loads.open().subscribe({
      next: (l) => this.state.setLoad(l),
      error: (e) => this.snack.open(problemMessage(e), 'OK', { duration: 5000 }),
    });
  }

  protected async startCamera(): Promise<void> {
    const video = this.video()?.nativeElement;
    if (!video) return;
    this.scanning.set(true);
    try {
      const Detector = (window as unknown as { BarcodeDetector?: new (o: object) => BarcodeDetectorLike }).BarcodeDetector;
      if (Detector) {
        // API nativa (Chrome Android): más rápida y sin descargar el decodificador
        const detector = new Detector({ formats: ['qr_code', 'code_128', 'ean_13', 'data_matrix'] });
        this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
        video.srcObject = this.stream;
        await video.play();
        const tick = async () => {
          if (!this.scanning()) return;
          try {
            const codes = await detector.detect(video);
            if (codes.length) this.onCode(codes[0].rawValue);
          } catch {
            /* fotograma no válido */
          }
          this.detectorLoop = requestAnimationFrame(() => void tick());
        };
        void tick();
      } else {
        const { BrowserMultiFormatReader } = await import('@zxing/browser');
        const reader = new BrowserMultiFormatReader();
        this.controls = await reader.decodeFromConstraints({ video: { facingMode: 'environment' } }, video, (result) => {
          if (result) this.onCode(result.getText());
        });
      }
    } catch {
      this.scanning.set(false);
      this.snack.open('No se pudo acceder a la cámara. Usa el código manual.', 'OK', { duration: 5000 });
    }
  }

  private stopCamera(): void {
    this.scanning.set(false);
    cancelAnimationFrame(this.detectorLoop);
    this.controls?.stop();
    this.stream?.getTracks().forEach((t) => t.stop());
  }

  protected manual(): void {
    this.onCode(this.code.value);
    this.code.reset();
  }

  private onCode(raw: string): void {
    const code = raw.trim().toUpperCase();
    const load = this.load();
    // El mismo código seguido (la cámara lo lee varias veces por segundo) se ignora 2 s
    if (!code || !load || (code === this.lastCode && Date.now() - this.lastAt < 2000)) return;
    this.lastCode = code;
    this.lastAt = Date.now();
    const clientScanId = uuid();
    const scannedAt = new Date().toISOString();
    this.loads.scan(load.id, code, clientScanId, scannedAt).subscribe({
      next: (r) => {
        scanFeedback(r.result === 'OK');
        this.push({ code, result: r.result, message: r.message });
        this.state.setLoad({
          ...load,
          scannedCount: r.scannedCount,
          expectedCount: r.expectedCount,
          missing: r.result === 'OK' ? load.missing.filter((m) => m.trackingCode !== code) : load.missing,
        } satisfies LoadSummary);
      },
      error: (e) => {
        if (isNetworkError(e)) {
          // Sin cobertura: se guarda y se envía después con el mismo clientScanId
          void this.queue.enqueue({ kind: 'scan', loadId: load.id, code, clientScanId, scannedAt });
          scanFeedback(true);
          this.push({ code, result: 'QUEUED', message: 'Guardado sin conexión' });
        } else {
          scanFeedback(false);
          this.push({ code, result: 'UNKNOWN', message: problemMessage(e) });
        }
      },
    });
  }

  private push(line: ScanLine): void {
    this.lines.update((l) => [line, ...l].slice(0, 50));
    this.flash.set(line);
    clearTimeout(this.flashTimer);
    this.flashTimer = setTimeout(() => this.flash.set(null), 1800);
  }

  protected close(): void {
    const load = this.load();
    if (!load) return;
    const missing = load.expectedCount - load.scannedCount;
    if (missing > 0 && !confirm(`Faltan ${missing} paquetes por escanear. ¿Cerrar la carga igualmente?`)) return;
    this.closing.set(true);
    this.loads.close(load.id).subscribe({
      next: (l) => {
        this.stopCamera();
        this.state.setLoad(l);
        this.snack.open('Carga cerrada. Calculando la ruta…', 'OK', { duration: 4000 });
        this.router.navigate(['/today']);
      },
      error: (e) => {
        this.closing.set(false);
        this.snack.open(problemMessage(e), 'OK', { duration: 5000 });
      },
    });
  }
}
