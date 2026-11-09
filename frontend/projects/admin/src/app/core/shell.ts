import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, LabelPipe, LogoComponent, RealtimeService } from '@rutaexpress/shared';
import { map } from 'rxjs';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    LabelPipe,
    LogoComponent,
    DatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-sidenav-container class="container">
      <mat-sidenav [mode]="isMobile() ? 'over' : 'side'" [opened]="!isMobile()" #nav class="nav">
        <div class="brand"><rx-logo [size]="30" [inverse]="true" /></div>
        @for (group of menu; track group.title) {
          <div class="group">
            <span class="group-title">{{ group.title }}</span>
            @for (item of group.items; track item.path) {
              <a class="item" [routerLink]="item.path" routerLinkActive="active" (click)="isMobile() && nav.close()">
                <mat-icon>{{ item.icon }}</mat-icon>
                <span>{{ item.label }}</span>
              </a>
            }
          </div>
        }
        <div class="me">
          <span class="avatar" aria-hidden="true">{{ initials() }}</span>
          <span class="who"><b>{{ auth.user()?.email }}</b><small>{{ auth.role() | rxLabel }}</small></span>
          <button mat-icon-button (click)="auth.logout()" matTooltip="Cerrar sesión" aria-label="Cerrar sesión">
            <mat-icon>logout</mat-icon>
          </button>
        </div>
      </mat-sidenav>
      <mat-sidenav-content>
        <header class="topbar">
          @if (isMobile()) {
            <button mat-icon-button (click)="nav.toggle()" aria-label="Menú"><mat-icon>menu</mat-icon></button>
            <rx-logo [size]="26" />
          }
          <span class="spacer"></span>
          <span class="today">{{ today | date: "EEEE, d 'de' MMMM" }}</span>
          <span class="realtime" [class.on]="realtime.connected()" role="status">
            <span class="dot" aria-hidden="true"></span>{{ realtime.connected() ? 'En directo' : 'Reconectando…' }}
          </span>
        </header>
        <main class="content"><router-outlet /></main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    .container { height: 100vh; }
    .nav { width: 248px; display: flex; flex-direction: column; border: 0; border-radius: 0;
      background: var(--rx-navy-900); color: #cbd5e1; }
    :host ::ng-deep .nav .mat-drawer-inner-container { display: flex; flex-direction: column; }
    .brand { padding: 20px 20px 12px; color: #fff; }
    .group { display: grid; gap: 2px; padding: 12px; }
    .group-title { font-size: .7rem; font-weight: 600; letter-spacing: .08em; text-transform: uppercase; color: #64748b; padding: 4px 12px 6px; }
    .item { display: flex; align-items: center; gap: 12px; height: 40px; padding: 0 12px; border-radius: 8px;
      color: #cbd5e1; text-decoration: none; font-size: .9rem; font-weight: 500; }
    .item mat-icon { font-size: 20px; width: 20px; height: 20px; color: #64748b; }
    .item:hover { background: rgba(255, 255, 255, .06); color: #fff; }
    .item.active { background: rgba(255, 255, 255, .1); color: #fff; box-shadow: inset 3px 0 0 #f59e0b; }
    .item.active mat-icon { color: #f59e0b; }
    .me { margin-top: auto; display: flex; align-items: center; gap: 10px; padding: 16px 12px 16px 20px;
      border-top: 1px solid rgba(255, 255, 255, .08); }
    .me button { color: #94a3b8; }
    .avatar { width: 34px; height: 34px; flex: none; border-radius: 50%; display: grid; place-items: center;
      background: var(--rx-navy-500); color: #fff; font-weight: 600; font-size: .8rem; }
    .who { display: grid; min-width: 0; flex: 1; }
    .who b { font-size: .8rem; font-weight: 500; color: #e2e8f0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .who small { font-size: .75rem; color: #94a3b8; }
    .topbar { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 16px; height: 56px; padding: 0 24px;
      background: rgba(245, 247, 250, .85); backdrop-filter: blur(8px); border-bottom: 1px solid var(--rx-border); }
    .spacer { flex: 1; }
    .today { display: inline-block; font-size: .85rem; color: var(--mat-sys-on-surface-variant); }
    .today::first-letter { text-transform: uppercase; }
    .realtime { display: inline-flex; align-items: center; gap: 6px; font-size: .78rem; font-weight: 600; padding: 4px 10px;
      border-radius: 999px; background: var(--rx-bad-bg); color: var(--rx-bad-fg); }
    .realtime.on { background: var(--rx-ok-bg); color: var(--rx-ok-fg); }
    .dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
    .realtime.on .dot { animation: pulse 2s infinite; }
    @keyframes pulse { 50% { opacity: .35; } }
    .content { padding: 28px 32px 40px; max-width: 1440px; margin: 0 auto; }
    @media (max-width: 600px) { .content { padding: 16px; } .topbar { padding: 0 8px; } .today { display: none; } }
    @media (prefers-reduced-motion: reduce) { .realtime.on .dot { animation: none; } }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly realtime = inject(RealtimeService);
  protected readonly isMobile = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );
  protected readonly isAdmin = computed(() => this.auth.hasRole('ADMIN'));
  protected readonly today = new Date();
  protected readonly initials = computed(() => {
    const local = this.auth.user()?.email?.split('@')[0] ?? '';
    return local.slice(0, 2).toUpperCase();
  });

  protected readonly menu = [
    {
      title: 'Operación',
      items: [
        { path: '/dashboard', icon: 'dashboard', label: 'Dashboard' },
        { path: '/live', icon: 'map', label: 'Mapa en directo' },
        { path: '/routes', icon: 'route', label: 'Rutas' },
        { path: '/parcels', icon: 'inventory_2', label: 'Paquetes' },
        { path: '/incidents', icon: 'report', label: 'Incidencias' },
      ],
    },
    {
      title: 'Configuración',
      items: [
        { path: '/drivers', icon: 'badge', label: 'Repartidores' },
        { path: '/vehicles', icon: 'local_shipping', label: 'Vehículos' },
        { path: '/zones', icon: 'polyline', label: 'Zonas' },
        { path: '/depots', icon: 'warehouse', label: 'Almacenes' },
      ],
    },
  ];}
