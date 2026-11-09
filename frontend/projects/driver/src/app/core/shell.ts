import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, RealtimeService } from '@rutaexpress/shared';
import { OfflineQueueService } from './offline-queue.service';

/** Navegación inferior, pensada para usar con una mano. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!queue.online()) {
      <div class="offline" role="status">Sin conexión · {{ queue.pending() }} pendientes de enviar</div>
    } @else if (queue.pending() > 0) {
      <div class="syncing" role="status">Sincronizando {{ queue.pending() }}…</div>
    }
    <main class="content"><router-outlet /></main>
    <nav class="bottom">
      @for (item of menu; track item.path) {
        <a [routerLink]="item.path" routerLinkActive="active" [attr.aria-label]="item.label">
          <mat-icon>{{ item.icon }}</mat-icon><span>{{ item.label }}</span>
        </a>
      }
    </nav>
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100dvh; }
    .content { flex: 1; overflow: auto; padding: 16px 16px 20px; }
    .bottom { display: grid; grid-template-columns: repeat(5, 1fr); padding: 6px 4px calc(6px + env(safe-area-inset-bottom));
      background: var(--mat-sys-surface-container-lowest); border-top: 1px solid var(--rx-border);
      box-shadow: 0 -4px 16px rgba(15, 23, 42, .04); }
    .bottom a { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; min-height: 56px;
      font-size: 0.72rem; font-weight: 500; color: var(--mat-sys-on-surface-variant); text-decoration: none; }
    .bottom mat-icon { width: 56px; height: 30px; line-height: 30px; text-align: center; border-radius: 999px; transition: background .15s; }
    .bottom a.active { color: var(--mat-sys-primary); font-weight: 700; }
    .bottom a.active mat-icon { background: var(--mat-sys-primary-container); }
    .offline { background: var(--rx-bad); color: #fff; text-align: center; padding: 8px; font-size: 0.85rem; font-weight: 600; }
    .syncing { background: var(--rx-warn-bg); color: var(--rx-warn-fg); text-align: center; padding: 6px; font-size: 0.8rem; font-weight: 600; }
  `,
})
export class Shell {
  protected readonly queue = inject(OfflineQueueService);
  protected readonly menu = [
    { path: '/today', icon: 'today', label: 'Hoy' },
    { path: '/scan', icon: 'qr_code_scanner', label: 'Carga' },
    { path: '/route', icon: 'route', label: 'Ruta' },
    { path: '/assistant', icon: 'mic', label: 'Asistente' },
    { path: '/more', icon: 'more_horiz', label: 'Más' },
  ];

  constructor() {
    const auth = inject(AuthService);
    const snack = inject(MatSnackBar);
    const driverId = auth.user()?.driverId;
    if (driverId) {
      // Avisos en tiempo real del propio repartidor (ruta recalculada, fallo de optimización...)
      inject(RealtimeService)
        .watch(`/topic/drivers/${driverId}`)
        .pipe(takeUntilDestroyed(inject(DestroyRef)))
        .subscribe((m) => {
          if (m.type === 'ROUTE_OPTIMIZED') snack.open('Ruta calculada / actualizada', 'OK', { duration: 3000 });
          if (m.type === 'ROUTE_OPTIMIZATION_FAILED') snack.open('No se pudo calcular la ruta. Avisa a tráfico.', 'OK', { duration: 6000 });
        });
    }
    void this.queue.sync();
  }
}
