import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { PublicTracking, TrackingApi } from '@rutaexpress/shared';

/** Seguimiento público para el destinatario: estado y franja estimada de llegada, sin datos personales. */
@Component({
  selector: 'app-public-tracking',
  imports: [ReactiveFormsModule, MatCardModule, MatInputModule, MatButtonModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      <h1>¿Dónde está mi paquete?</h1>
      <form (ngSubmit)="search()" class="search">
        <mat-form-field><mat-label>Código de seguimiento</mat-label><input matInput [formControl]="codeInput" autocapitalize="characters" /></mat-form-field>
        <button mat-flat-button [disabled]="codeInput.invalid">Buscar</button>
      </form>
      @if (notFound()) { <p class="error">No encontramos ningún paquete con ese código.</p> }
      @if (result(); as r) {
        <mat-card>
          <mat-card-header>
            <mat-card-title>{{ r.statusLabel }}</mat-card-title>
            <mat-card-subtitle>{{ r.trackingCode }} · {{ r.city }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (r.etaFrom && r.etaTo) {
              <p class="eta">Llegada estimada entre las <b>{{ r.etaFrom | date: 'HH:mm' }}</b> y las <b>{{ r.etaTo | date: 'HH:mm' }}</b></p>
              @if (r.stopsBefore !== undefined && r.stopsBefore !== null) { <p>Quedan {{ r.stopsBefore }} entregas antes de la tuya.</p> }
            }
            <ol class="timeline">
              @for (e of r.events; track $index) { <li>{{ e.label }} · {{ e.at | date: 'dd/MM HH:mm' }}</li> }
            </ol>
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: `
    .wrap { max-width: 560px; margin: 0 auto; padding: 24px 16px; }
    .search { display: flex; gap: 8px; align-items: center; }
    .search mat-form-field { flex: 1; }
    .eta { font-size: 1.15rem; }
    .error { color: var(--mat-sys-error); }
  `,
})
export class PublicTrackingPage implements OnInit {
  readonly code = input<string>();
  private readonly api = inject(TrackingApi);
  private readonly router = inject(Router);
  protected readonly result = signal<PublicTracking | null>(null);
  protected readonly notFound = signal(false);
  protected readonly codeInput = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^[A-Za-z0-9]{6,20}$/)],
  });

  ngOnInit(): void {
    const code = this.code();
    if (code) {
      this.codeInput.setValue(code);
      this.load(code);
    }
  }

  protected search(): void {
    const code = this.codeInput.value.trim().toUpperCase();
    this.router.navigate(['/seguimiento', code]);
    this.load(code);
  }

  private load(code: string): void {
    this.notFound.set(false);
    this.api.publicTrack(code).subscribe({
      next: (r) => this.result.set(r),
      error: (e: unknown) => {
        this.result.set(null);
        this.notFound.set(e instanceof HttpErrorResponse && (e.status === 404 || e.status === 400));
      },
    });
  }
}
