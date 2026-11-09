import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { AuthService, LogoComponent, problemMessage } from '@rutaexpress/shared';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressBarModule, LogoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      <header class="hero">
        <rx-logo [size]="44" [inverse]="true" [showName]="false" />
        <h1>Ruta<b>Express</b></h1>
        <p>App del repartidor</p>
      </header>
      <form class="sheet" [formGroup]="form" (ngSubmit)="submit()">
        <h2>Inicia sesión</h2>
        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <mat-icon matPrefix>mail_outline</mat-icon>
          <input matInput formControlName="email" type="email" autocomplete="username" inputmode="email" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Contraseña</mat-label>
          <mat-icon matPrefix>lock_outline</mat-icon>
          <input matInput formControlName="password" [type]="showPassword() ? 'text' : 'password'" autocomplete="current-password" />
          <button mat-icon-button matSuffix type="button" (click)="showPassword.set(!showPassword())"
                  [attr.aria-label]="showPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
            <mat-icon>{{ showPassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
        </mat-form-field>
        @if (error()) { <p class="error" role="alert"><mat-icon>error_outline</mat-icon>{{ error() }}</p> }
        <button mat-flat-button class="big" type="submit" [disabled]="form.invalid || loading()">
          {{ loading() ? 'Entrando…' : 'Entrar' }}
        </button>
        @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
      </form>
    </div>
  `,
  styles: `
    .wrap { min-height: 100dvh; display: flex; flex-direction: column; background: var(--rx-navy-900); }
    .hero { display: grid; justify-items: center; gap: 6px; padding: 56px 24px 40px; color: #fff; text-align: center;
      background: radial-gradient(circle at 50% -20%, #2b4f7d 0%, transparent 65%); }
    .hero h1 { color: #fff; font-size: 1.75rem; font-weight: 500; margin: 10px 0 0; }
    .hero h1 b { font-weight: 700; }
    .hero p { margin: 0; color: #94a3b8; }
    .sheet { flex: 1; display: flex; flex-direction: column; gap: 8px; padding: 28px 20px calc(24px + env(safe-area-inset-bottom));
      background: var(--mat-sys-surface-container-lowest); border-radius: 24px 24px 0 0; }
    h2 { margin-bottom: 12px; }
    mat-icon[matPrefix] { margin: 0 8px 0 12px; color: var(--mat-sys-on-surface-variant); }
    .big { height: 54px; font-size: 1.05rem; margin-top: 4px; }
    .error { display: flex; align-items: center; gap: 8px; margin: 0 0 8px; padding: 10px 12px; border-radius: var(--rx-radius-sm);
      background: var(--rx-bad-bg); color: var(--rx-bad-fg); font-size: .9rem; }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly loading = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submit(): void {
    const { email, password } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(email, password).subscribe({
      next: (user) => {
        if (user.role !== 'DRIVER') {
          this.auth.clear();
          this.error.set('Esta app es para repartidores. Usa el panel web.');
          this.loading.set(false);
          return;
        }
        this.router.navigate(['/today']);
      },
      error: (e) => {
        this.loading.set(false);
        this.error.set(problemMessage(e, 'No se pudo iniciar sesión'));
      },
    });
  }
}
