import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  AccountApi,
  AuthService,
  DeliveryApi,
  IncidentType,
  LabelPipe,
  PersonalToken,
  PersonalTokenCreated,
  problemMessage,
} from '@rutaexpress/shared';
import { GeolocationService } from '../core/geolocation.service';
import { OfflineQueueService } from '../core/offline-queue.service';

@Component({
  selector: 'app-more',
  imports: [ReactiveFormsModule, MatCardModule, MatButtonModule, MatIconModule, MatInputModule, MatSelectModule, MatListModule, DatePipe, LabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card>
      <mat-card-header><mat-card-title>Avisar de una incidencia</mat-card-title></mat-card-header>
      <mat-card-content class="form">
        <mat-form-field>
          <mat-label>Tipo</mat-label>
          <mat-select [formControl]="incidentType">
            @for (t of incidentTypes; track t) { <mat-option [value]="t">{{ t | rxLabel }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field><mat-label>Qué ocurre</mat-label><textarea matInput [formControl]="incidentText" rows="2"></textarea></mat-form-field>
        <button mat-flat-button color="warn" (click)="report()" [disabled]="incidentText.invalid">Enviar a tráfico</button>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-header>
        <mat-card-title>Conectar un cliente MCP</mat-card-title>
        <mat-card-subtitle>Token personal para usar el asistente desde Claude u otro cliente compatible</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        @if (created(); as c) {
          <div class="secret">
            <p><b>Copia el token ahora:</b> no se volverá a mostrar.</p>
            <code>{{ c.token }}</code>
            <button mat-button (click)="copy(c.token)"><mat-icon>content_copy</mat-icon> Copiar</button>
            <p class="small">Servidor MCP: <code>{{ origin }}/mcp/sse</code> · Cabecera <code>Authorization: Bearer &lt;token&gt;</code></p>
          </div>
        }
        <div class="row">
          <mat-form-field><mat-label>Nombre del token</mat-label><input matInput [formControl]="tokenName" /></mat-form-field>
          <button mat-stroked-button (click)="createToken()" [disabled]="tokenName.invalid">Crear</button>
        </div>
        <mat-list>
          @for (t of tokens(); track t.id) {
            <mat-list-item>
              <span matListItemTitle>{{ t.name }} <code>{{ t.prefix }}…</code></span>
              <span matListItemLine>{{ t.active ? 'Activo' : 'Revocado' }} · último uso {{ (t.lastUsedAt | date: 'dd/MM HH:mm') ?? 'nunca' }}</span>
              @if (t.active) { <button matListItemMeta mat-icon-button (click)="revoke(t)" aria-label="Revocar"><mat-icon>delete</mat-icon></button> }
            </mat-list-item>
          }
        </mat-list>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-content>
        <p>{{ auth.user()?.email }}</p>
        <p class="small">GPS: {{ gps.active() ? 'activo' : 'inactivo' }} · pendientes de sincronizar: {{ queue.pending() }}</p>
        <button mat-stroked-button (click)="queue.sync()">Sincronizar ahora</button>
        <button mat-flat-button (click)="logout()">Cerrar sesión</button>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    :host { display: grid; gap: 12px; }
    .form { display: grid; gap: 8px; }
    .row { display: flex; gap: 8px; align-items: center; }
    .row mat-form-field { flex: 1; }
    .secret { padding: 12px; border-radius: 10px; background: #fff8e1; word-break: break-all; margin-bottom: 8px; }
    .small { font-size: .8rem; color: var(--mat-sys-on-surface-variant); }
    mat-card-content button { margin: 4px 4px 0 0; }
  `,
})
export class MorePage {
  protected readonly auth = inject(AuthService);
  protected readonly gps = inject(GeolocationService);
  protected readonly queue = inject(OfflineQueueService);
  private readonly account = inject(AccountApi);
  private readonly delivery = inject(DeliveryApi);
  private readonly snack = inject(MatSnackBar);

  protected readonly origin = window.location.origin;
  protected readonly tokens = signal<PersonalToken[]>([]);
  protected readonly created = signal<PersonalTokenCreated | null>(null);
  protected readonly tokenName = new FormControl('Claude', { nonNullable: true, validators: Validators.required });
  protected readonly incidentType = new FormControl<IncidentType>('DELAY', { nonNullable: true });
  protected readonly incidentText = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(1000)] });
  protected readonly incidentTypes: IncidentType[] = ['DELAY', 'TRAFFIC', 'ROAD_CLOSED', 'BREAKDOWN', 'ACCIDENT', 'DAMAGED_PARCEL', 'OTHER'];

  constructor() {
    this.loadTokens();
  }

  private loadTokens(): void {
    this.account.tokens().subscribe((t) => this.tokens.set(t));
  }

  protected createToken(): void {
    this.account.createToken(this.tokenName.value, 30).subscribe({
      next: (c) => {
        this.created.set(c);
        this.loadTokens();
      },
      error: (e) => this.snack.open(problemMessage(e), 'OK', { duration: 5000 }),
    });
  }

  protected revoke(t: PersonalToken): void {
    this.account.revokeToken(t.id).subscribe(() => this.loadTokens());
  }

  protected copy(text: string): void {
    void navigator.clipboard?.writeText(text);
    this.snack.open('Copiado', 'OK', { duration: 2000 });
  }

  protected async report(): Promise<void> {
    const position = await this.gps.current();
    this.delivery.reportIncident(this.incidentType.value, this.incidentText.value, position).subscribe({
      next: () => {
        this.incidentText.reset();
        this.snack.open('Incidencia enviada a tráfico', 'OK', { duration: 3000 });
      },
      error: (e) => this.snack.open(problemMessage(e), 'OK', { duration: 5000 }),
    });
  }

  protected logout(): void {
    this.gps.stop();
    this.auth.logout();
  }
}
