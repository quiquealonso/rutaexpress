import { SelectionModel } from '@angular/cdk/collections';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  CatalogApi,
  Depot,
  ImportResult,
  LabelPipe,
  ParcelApi,
  ParcelListItem,
  ParcelQuery,
  problemMessage,
} from '@rutaexpress/shared';
import { debounceTime, distinctUntilChanged, merge } from 'rxjs';
import { openBlob } from '../../core/files';

@Component({
  selector: 'app-parcel-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatProgressBarModule,
    MatTooltipModule,
    LabelPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="head">
      <div><h1>Paquetes</h1><p class="rx-muted">Alta, importación y seguimiento de todos los envíos.</p></div>
      <span class="spacer"></span>
      <button mat-stroked-button (click)="fileInput.click()" [disabled]="!depotId.value"><mat-icon>upload</mat-icon> Importar CSV</button>
      <input #fileInput type="file" accept=".csv" hidden (change)="importCsv($event)" />
      <button mat-stroked-button (click)="printLabels()" [disabled]="selection.isEmpty()"><mat-icon>qr_code</mat-icon> Etiquetas ({{ selection.selected.length }})</button>
      <a mat-flat-button routerLink="/parcels/new"><mat-icon>add</mat-icon> Nuevo</a>
    </header>

    <section class="filters">
      <mat-form-field><mat-label>Buscar</mat-label><input matInput [formControl]="q" placeholder="Código, destinatario, calle…" /></mat-form-field>
      <mat-form-field>
        <mat-label>Estado</mat-label>
        <mat-select [formControl]="status">
          <mat-option [value]="''">Todos</mat-option>
          @for (s of statuses; track s) { <mat-option [value]="s">{{ s | rxLabel }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label>Almacén</mat-label>
        <mat-select [formControl]="depotId">
          @for (d of depots(); track d.id) { <mat-option [value]="d.id">{{ d.name }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <mat-checkbox [formControl]="review">Solo direcciones a revisar</mat-checkbox>
    </section>

    @if (importResult(); as r) {
      <div class="import" role="status">
        Importados {{ r.created }} · con error {{ r.failed }} · a revisar {{ r.needsReview }}
        @for (e of r.errors.slice(0, 5); track e.row) { <div class="err">Fila {{ e.row }}: {{ e.message }}</div> }
        <button mat-button (click)="importResult.set(null)">Cerrar</button>
      </div>
    }

    @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
    <div class="table-wrap">
      <table mat-table [dataSource]="rows()" matSort (matSortChange)="sortBy($event)">
        <ng-container matColumnDef="select">
          <th mat-header-cell *matHeaderCellDef>
            <mat-checkbox (change)="toggleAll()" [checked]="allSelected()" [indeterminate]="selection.hasValue() && !allSelected()" />
          </th>
          <td mat-cell *matCellDef="let p"><mat-checkbox (click)="$event.stopPropagation()" (change)="selection.toggle(p.id)" [checked]="selection.isSelected(p.id)" /></td>
        </ng-container>
        <ng-container matColumnDef="trackingCode">
          <th mat-header-cell *matHeaderCellDef mat-sort-header>Código</th>
          <td mat-cell *matCellDef="let p"><a [routerLink]="['/parcels', p.id]" class="code">{{ p.trackingCode }}</a></td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef mat-sort-header>Estado</th>
          <td mat-cell *matCellDef="let p"><span class="chip" [class]="p.status">{{ p.status | rxLabel }}</span></td>
        </ng-container>
        <ng-container matColumnDef="recipient">
          <th mat-header-cell *matHeaderCellDef>Destinatario</th>
          <td mat-cell *matCellDef="let p">{{ p.recipientName }}</td>
        </ng-container>
        <ng-container matColumnDef="address">
          <th mat-header-cell *matHeaderCellDef>Dirección</th>
          <td mat-cell *matCellDef="let p">
            {{ p.addressLine }}
            @if (p.needsReview) { <mat-icon class="warn" matTooltip="Geocodificación dudosa: revisar">warning</mat-icon> }
          </td>
        </ng-container>
        <ng-container matColumnDef="weight">
          <th mat-header-cell *matHeaderCellDef>Peso</th>
          <td mat-cell *matCellDef="let p">{{ p.weightKg }} kg</td>
        </ng-container>
        <ng-container matColumnDef="createdAt">
          <th mat-header-cell *matHeaderCellDef mat-sort-header>Alta</th>
          <td mat-cell *matCellDef="let p">{{ p.createdAt | date: 'short' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns; sticky: true"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
    <mat-paginator [length]="total()" [pageSize]="query.size" [pageIndex]="query.page" [pageSizeOptions]="[25, 50, 100]" (page)="paginate($event)" />
  `,
  styles: `
    .head { display: flex; align-items: flex-end; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
    .spacer { flex: 1; }
    .filters { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; margin: 12px 0; }
    .table-wrap { overflow: auto; max-height: calc(100vh - 330px); }
    .code { font-family: 'JetBrains Mono', ui-monospace, monospace; font-weight: 500; letter-spacing: .02em; }
    .warn { color: var(--rx-warn); vertical-align: middle; font-size: 18px; }
    .import { padding: 12px 16px; border-radius: var(--rx-radius-sm); background: var(--rx-info-bg); color: var(--rx-info-fg); margin-bottom: 12px; }
    .err { font-size: 0.85rem; color: var(--mat-sys-error); }
  `,
})
export class ParcelListPage {
  private readonly api = inject(ParcelApi);
  private readonly catalog = inject(CatalogApi);
  private readonly snack = inject(MatSnackBar);

  protected readonly columns = ['select', 'trackingCode', 'status', 'recipient', 'address', 'weight', 'createdAt'];
  protected readonly statuses = ['REGISTERED', 'LOADED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED_ATTEMPT', 'RETURNED', 'CANCELLED'];
  protected readonly rows = signal<ParcelListItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly depots = signal<Depot[]>([]);
  protected readonly importResult = signal<ImportResult | null>(null);
  protected readonly selection = new SelectionModel<string>(true, []);

  protected readonly q = new FormControl('', { nonNullable: true });
  /** Admite ?status=... para llegar filtrado desde el dashboard. */
  protected readonly status = new FormControl(inject(ActivatedRoute).snapshot.queryParamMap.get('status') ?? '', {
    nonNullable: true,
  });
  protected readonly depotId = new FormControl('', { nonNullable: true });
  protected readonly review = new FormControl(false, { nonNullable: true });
  protected query: ParcelQuery = { page: 0, size: 25, sort: 'createdAt,desc' };

  constructor() {
    this.catalog.depots().subscribe((d) => {
      this.depots.set(d);
      if (d.length === 1) this.depotId.setValue(d[0].id, { emitEvent: false });
    });
    merge(
      this.q.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
      this.status.valueChanges,
      this.depotId.valueChanges,
      this.review.valueChanges,
    )
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => {
        this.query.page = 0;
        this.load();
      });
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api
      .list({
        ...this.query,
        q: this.q.value,
        status: this.status.value,
        depotId: this.depotId.value || undefined,
        needsReview: this.review.value ? true : undefined,
      })
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
          this.loading.set(false);
        },
        error: (e) => {
          this.loading.set(false);
          this.snack.open(problemMessage(e), 'Cerrar', { duration: 5000 });
        },
      });
  }

  protected paginate(e: PageEvent): void {
    this.query = { ...this.query, page: e.pageIndex, size: e.pageSize };
    this.load();
  }

  protected sortBy(s: Sort): void {
    this.query = { ...this.query, sort: s.direction ? `${s.active},${s.direction}` : 'createdAt,desc' };
    this.load();
  }

  protected allSelected(): boolean {
    return this.rows().length > 0 && this.rows().every((r) => this.selection.isSelected(r.id));
  }

  protected toggleAll(): void {
    if (this.allSelected()) {
      this.selection.clear();
    } else {
      this.selection.select(...this.rows().map((r) => r.id));
    }
  }

  protected printLabels(): void {
    this.api.labels(this.selection.selected).subscribe({
      next: (blob) => openBlob(blob),
      error: (e) => this.snack.open(problemMessage(e), 'Cerrar', { duration: 5000 }),
    });
  }

  protected importCsv(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.loading.set(true);
    this.api.importCsv(file, this.depotId.value).subscribe({
      next: (r) => {
        this.importResult.set(r);
        this.load();
      },
      error: (e) => {
        this.loading.set(false);
        this.snack.open(problemMessage(e), 'Cerrar', { duration: 6000 });
      },
    });
  }
}
