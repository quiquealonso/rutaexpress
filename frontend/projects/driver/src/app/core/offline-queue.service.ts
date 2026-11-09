import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import {
  DeliveryApi,
  FailReason,
  GeoPoint,
  LoadApi,
  PositionSample,
  ProofType,
  TrackingApi,
} from '@rutaexpress/shared';
import { DBSchema, IDBPDatabase, openDB } from 'idb';
import { firstValueFrom } from 'rxjs';

/** Operaciones que se pueden hacer sin cobertura y se sincronizan después. */
export type QueuedOperation =
  | { kind: 'scan'; loadId: string; code: string; clientScanId: string; scannedAt: string }
  | { kind: 'deliver'; stopId: string; proofType: ProofType; receiverName?: string; file?: Blob; position?: GeoPoint }
  | { kind: 'fail'; stopId: string; reason: FailReason; note?: string; retryLater: boolean; position?: GeoPoint }
  | { kind: 'positions'; positions: PositionSample[] };

interface QueueDb extends DBSchema {
  queue: { key: number; value: { id?: number; op: QueuedOperation; createdAt: number; attempts: number } };
}

/**
 * Cola persistente en IndexedDB. Si no hay conexión, los escaneos, entregas y posiciones se guardan aquí
 * y se envían al recuperar cobertura. Los endpoints son idempotentes (clientScanId, entrega repetida),
 * así que reintentar nunca duplica nada.
 */
@Injectable({ providedIn: 'root' })
export class OfflineQueueService {
  private readonly loads = inject(LoadApi);
  private readonly delivery = inject(DeliveryApi);
  private readonly tracking = inject(TrackingApi);
  private dbPromise: Promise<IDBPDatabase<QueueDb>> | null = null;
  private syncing = false;

  readonly pending = signal(0);
  readonly online = signal(typeof navigator === 'undefined' ? true : navigator.onLine);

  constructor() {
    window.addEventListener('online', () => {
      this.online.set(true);
      void this.sync();
    });
    window.addEventListener('offline', () => this.online.set(false));
    // Reintento periódico por si el evento "online" no llega (redes inestables)
    setInterval(() => void this.sync(), 30_000);
    void this.refreshCount();
  }

  private db(): Promise<IDBPDatabase<QueueDb>> {
    this.dbPromise ??= openDB<QueueDb>('rutaexpress-offline', 1, {
      upgrade(db) {
        db.createObjectStore('queue', { keyPath: 'id', autoIncrement: true });
      },
    });
    return this.dbPromise;
  }

  async enqueue(op: QueuedOperation): Promise<void> {
    const db = await this.db();
    await db.add('queue', { op, createdAt: Date.now(), attempts: 0 });
    await this.refreshCount();
  }

  async sync(): Promise<void> {
    if (this.syncing || !navigator.onLine) return;
    this.syncing = true;
    try {
      const db = await this.db();
      const items = await db.getAll('queue');
      for (const item of items) {
        try {
          await this.send(item.op);
          await db.delete('queue', item.id!);
        } catch (e) {
          if (e instanceof HttpErrorResponse && e.status >= 400 && e.status < 500 && e.status !== 401 && e.status !== 429) {
            // Error de negocio (p. ej. parada ya cerrada): reintentar no lo arreglará
            console.warn('Operación descartada', item.op, e.error);
            await db.delete('queue', item.id!);
          } else {
            await db.put('queue', { ...item, attempts: item.attempts + 1 });
            break; // sin red de nuevo: se para y se reintentará
          }
        }
      }
    } finally {
      this.syncing = false;
      await this.refreshCount();
    }
  }

  private send(op: QueuedOperation): Promise<unknown> {
    switch (op.kind) {
      case 'scan':
        return firstValueFrom(this.loads.scan(op.loadId, op.code, op.clientScanId, op.scannedAt));
      case 'deliver':
        return firstValueFrom(this.delivery.deliver(op.stopId, op.proofType, op.receiverName, op.file, op.position));
      case 'fail':
        return firstValueFrom(this.delivery.fail(op.stopId, op.reason, op.note, op.retryLater, op.position));
      case 'positions':
        return firstValueFrom(this.tracking.sendPositions(op.positions));
    }
  }

  private async refreshCount(): Promise<void> {
    const db = await this.db();
    this.pending.set(await db.count('queue'));
  }
}

/** ¿El error es de red (sin cobertura) y no del servidor? */
export function isNetworkError(e: unknown): boolean {
  return e instanceof HttpErrorResponse && (e.status === 0 || e.status === 502 || e.status === 503 || e.status === 504);
}
