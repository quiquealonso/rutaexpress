import { Injectable, inject, signal } from '@angular/core';
import { GeoPoint, PositionSample, TrackingApi } from '@rutaexpress/shared';
import { OfflineQueueService, isNetworkError } from './offline-queue.service';

/**
 * GPS mientras el reparto está activo: acumula posiciones y las envía en lote cada 10 s.
 * Sin cobertura, el lote va a la cola offline.
 */
@Injectable({ providedIn: 'root' })
export class GeolocationService {
  private readonly tracking = inject(TrackingApi);
  private readonly queue = inject(OfflineQueueService);
  private watchId: number | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;
  private buffer: PositionSample[] = [];

  readonly position = signal<GeoPoint | null>(null);
  readonly speedKmh = signal<number | null>(null);
  readonly active = signal(false);
  readonly error = signal<string | null>(null);
  /** true si el usuario bloqueó la ubicación: el navegador ya no vuelve a preguntar, hay que activarla en ajustes. */
  readonly denied = signal(false);

  start(): void {
    if (this.watchId !== null || !('geolocation' in navigator)) return;
    this.active.set(true);
    this.watchId = navigator.geolocation.watchPosition(
      (p) => {
        const sample: PositionSample = {
          lat: p.coords.latitude,
          lon: p.coords.longitude,
          speedKmh: p.coords.speed != null ? p.coords.speed * 3.6 : undefined,
          heading: p.coords.heading ?? undefined,
          accuracyMeters: p.coords.accuracy,
          recordedAt: new Date(p.timestamp).toISOString(),
        };
        this.position.set({ lat: sample.lat, lon: sample.lon });
        this.speedKmh.set(sample.speedKmh ?? null);
        this.error.set(null);
        this.denied.set(false);
        this.buffer.push(sample);
      },
      (e) => {
        this.denied.set(e.code === e.PERMISSION_DENIED);
        this.error.set(e.code === e.PERMISSION_DENIED ? 'Ubicación desactivada' : 'GPS sin señal');
      },
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 },
    );
    this.timer = setInterval(() => this.flush(), 10_000);
  }

  /** Vuelve a pedir la posición (tras activar el permiso o recuperar señal). */
  retry(): void {
    this.stop();
    this.start();
  }

  stop(): void {
    if (this.watchId !== null) navigator.geolocation.clearWatch(this.watchId);
    if (this.timer) clearInterval(this.timer);
    this.watchId = null;
    this.timer = null;
    this.active.set(false);
    this.flush();
  }

  /** Posición puntual (para adjuntar a una entrega aunque el seguimiento no esté activo). */
  current(): Promise<GeoPoint | undefined> {
    const known = this.position();
    if (known) return Promise.resolve(known);
    return new Promise((resolve) =>
      navigator.geolocation?.getCurrentPosition(
        (p) => resolve({ lat: p.coords.latitude, lon: p.coords.longitude }),
        () => resolve(undefined),
        { enableHighAccuracy: true, timeout: 5000 },
      ) ?? resolve(undefined),
    );
  }

  private flush(): void {
    if (this.buffer.length === 0) return;
    const batch = this.buffer.splice(0, 200);
    this.tracking.sendPositions(batch).subscribe({
      error: (e) => {
        if (isNetworkError(e)) void this.queue.enqueue({ kind: 'positions', positions: batch });
      },
    });
  }
}
