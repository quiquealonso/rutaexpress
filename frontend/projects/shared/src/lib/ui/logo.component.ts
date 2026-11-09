import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Marca de RutaExpress: una ruta con origen y destino dentro de un cuadrado redondeado. */
@Component({
  selector: 'rx-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="8" [attr.fill]="inverse() ? '#ffffff' : '#1e3a5f'" />
      <path d="M9 22.5c0-4 3-4.5 7-4.5s7-.5 7-4.5-3-4.5-7-4.5" fill="none"
            [attr.stroke]="inverse() ? '#1e3a5f' : '#ffffff'" stroke-width="2.4" stroke-linecap="round" />
      <circle cx="9" cy="22.5" r="2.6" [attr.fill]="inverse() ? '#1e3a5f' : '#ffffff'" />
      <circle cx="16" cy="9" r="2.6" fill="#f59e0b" />
    </svg>
    @if (showName()) { <span class="name">Ruta<b>Express</b></span> }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 10px; }
    svg { flex: none; display: block; }
    .name { font-weight: 500; font-size: 1.05rem; letter-spacing: -0.01em; }
    .name b { font-weight: 700; }
  `,
})
export class LogoComponent {
  readonly size = input(32);
  readonly showName = input(true);
  /** Versión clara, para fondos azul marino. */
  readonly inverse = input(false);
}
