import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
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
      <aside class="brand">
        <rx-logo [size]="40" [inverse]="true" />
        <div class="pitch">
          <h1>Toda tu flota de reparto, en una sola pantalla.</h1>
          <p>Rutas optimizadas, seguimiento en directo e incidencias al momento.</p>
        </div>
        <ul>
          <li><mat-icon>route</mat-icon> Rutas calculadas automáticamente al cerrar la carga</li>
          <li><mat-icon>my_location</mat-icon> Posición y avance de cada repartidor en tiempo real</li>
          <li><mat-icon>notifications_active</mat-icon> Avisos de retrasos, ausencias e incidencias</li>
        </ul>
        <small>© RutaExpress</small>
      </aside>

      <main class="panel">
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div>
            <span class="rx-eyebrow">Panel de tráfico</span>
            <h2>Inicia sesión</h2>
            <p class="rx-muted">Usa la cuenta que te ha dado tu administrador.</p>
          </div>
          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <mat-icon matPrefix>mail_outline</mat-icon>
            <input matInput formControlName="email" type="email" autocomplete="username" />
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
          @if (denied()) { <p class="error" role="alert"><mat-icon>block</mat-icon>Esta cuenta no tiene acceso al panel.</p> }
          <button mat-flat-button class="submit" type="submit" [disabled]="form.invalid || loading()">
            {{ loading() ? 'Entrando…' : 'Entrar' }}
          </button>
          @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
        </form>
      </main>
    </div>
  `,
  styles: `
    .wrap { min-height: 100vh; display: grid; grid-template-columns: minmax(360px, 5fr) 7fr; }
    .brand { display: flex; flex-direction: column; gap: 40px; padding: 48px; color: #e2e8f0;
      background: radial-gradient(circle at 20% 0%, #24456f 0%, transparent 55%), var(--rx-navy-900); }
    .pitch { margin-top: auto; }
    .pitch h1 { color: #fff; font-size: 2rem; line-height: 1.2; max-width: 14em; }
    .pitch p { color: #94a3b8; font-size: 1.05rem; margin: 12px 0 0; }
    ul { list-style: none; padding: 0; margin: 0 0 auto; display: grid; gap: 14px; }
    li { display: flex; align-items: center; gap: 12px; color: #cbd5e1; }
    li mat-icon { color: #f59e0b; flex: none; }
    small { color: #64748b; }
    .panel { display: grid; place-items: center; padding: 32px 16px; background: var(--mat-sys-surface-container-lowest); }
    form { width: min(380px, 100%); display: flex; flex-direction: column; gap: 8px; }
    form > div { margin-bottom: 16px; }
    h2 { font-size: 1.6rem; margin-top: 6px; }
    mat-icon[matPrefix] { margin: 0 8px 0 12px; color: var(--mat-sys-on-surface-variant); }
    .submit { height: 48px; font-size: 1rem; }
    .error { display: flex; align-items: center; gap: 8px; margin: 0 0 8px; padding: 10px 12px; border-radius: var(--rx-radius-sm);
      background: var(--rx-bad-bg); color: var(--rx-bad-fg); font-size: .9rem; }
    @media (max-width: 860px) { .wrap { grid-template-columns: 1fr; } .brand { display: none; } }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly returnUrl = input<string>();
  readonly denied = input<string>();

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
        if (user.role === 'DRIVER') {
          this.auth.clear();
          this.error.set('Los repartidores usan la app móvil.');
          this.loading.set(false);
          return;
        }
        this.router.navigateByUrl(this.returnUrl() ?? '/dashboard');
      },
      error: (e) => {
        this.error.set(problemMessage(e, 'No se pudo iniciar sesión'));
        this.loading.set(false);
      },
    });
  }
}
