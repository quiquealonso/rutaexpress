import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/** Captura de firma con el dedo sobre un canvas (Pointer Events: táctil, ratón y lápiz). */
@Component({
  selector: 'app-signature-pad',
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <canvas #canvas (pointerdown)="down($event)" (pointermove)="move($event)" (pointerup)="up()" (pointerleave)="up()"></canvas>
    <div class="row">
      <small>{{ empty() ? 'Firma aquí' : 'Firma capturada' }}</small>
      <button mat-button type="button" (click)="clear()">Borrar</button>
    </div>
  `,
  styles: `
    canvas { width: 100%; height: 200px; border: 2px dashed var(--mat-sys-outline); border-radius: 12px; touch-action: none; background: #fff; }
    .row { display: flex; justify-content: space-between; align-items: center; }
  `,
})
export class SignaturePad {
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private ctx?: CanvasRenderingContext2D;
  private drawing = false;
  readonly empty = signal(true);

  constructor() {
    afterNextRender(() => {
      const canvas = this.canvasRef().nativeElement;
      const ratio = window.devicePixelRatio || 1;
      canvas.width = canvas.offsetWidth * ratio;
      canvas.height = canvas.offsetHeight * ratio;
      this.ctx = canvas.getContext('2d')!;
      this.ctx.scale(ratio, ratio);
      this.ctx.lineWidth = 2.5;
      this.ctx.lineCap = 'round';
      this.ctx.strokeStyle = '#0d47a1';
    });
  }

  protected down(e: PointerEvent): void {
    if (!this.ctx) return;
    this.drawing = true;
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    this.ctx.beginPath();
    this.ctx.moveTo(e.offsetX, e.offsetY);
  }

  protected move(e: PointerEvent): void {
    if (!this.drawing || !this.ctx) return;
    this.ctx.lineTo(e.offsetX, e.offsetY);
    this.ctx.stroke();
    this.empty.set(false);
  }

  protected up(): void {
    this.drawing = false;
  }

  clear(): void {
    const canvas = this.canvasRef().nativeElement;
    this.ctx?.clearRect(0, 0, canvas.width, canvas.height);
    this.empty.set(true);
  }

  toBlob(): Promise<Blob | null> {
    return new Promise((resolve) => this.canvasRef().nativeElement.toBlob((b) => resolve(b), 'image/png'));
  }
}
