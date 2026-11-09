import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, Subject, filter, share } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { WS_URL } from '../config';
import { RealtimeMessage } from '../models';

/**
 * Cliente STOMP sobre WebSocket. Se autentica en el CONNECT con el JWT y se reconecta solo.
 * Cada suscripción devuelve un Observable; al desuscribirse del último, se cancela en el broker.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly auth = inject(AuthService);
  private readonly url = inject(WS_URL);
  private client: Client | null = null;
  private readonly topics = new Map<string, Observable<RealtimeMessage>>();
  private readonly subjects = new Map<string, Subject<RealtimeMessage>>();
  private readonly stompSubs = new Map<string, StompSubscription>();

  readonly connected = signal(false);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.disconnect());
  }

  private ensureClient(): Client {
    if (this.client) {
      return this.client;
    }
    const client = new Client({
      brokerURL: this.url,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: async () => {
        // Antes de cada (re)conexión, un token vigente
        if (this.auth.isAccessTokenExpired && this.auth.isAuthenticated()) {
          await new Promise<void>((resolve) => this.auth.refresh().subscribe({ next: () => resolve(), error: () => resolve() }));
        }
        client.connectHeaders = { Authorization: `Bearer ${this.auth.accessToken ?? ''}` };
      },
      onConnect: () => {
        this.connected.set(true);
        // Re-suscribir tras una reconexión
        for (const destination of this.subjects.keys()) {
          this.subscribeOnBroker(destination);
        }
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: (frame) => console.warn('STOMP error', frame.headers['message']),
    });
    client.activate();
    this.client = client;
    return client;
  }

  /** Mensajes de un destino, p. ej. '/topic/dispatch', '/topic/positions' o '/topic/drivers/{id}'. */
  watch<T = unknown>(destination: string): Observable<RealtimeMessage<T>> {
    let topic = this.topics.get(destination);
    if (!topic) {
      const subject = new Subject<RealtimeMessage>();
      this.subjects.set(destination, subject);
      topic = new Observable<RealtimeMessage>((subscriber) => {
        const inner = subject.subscribe(subscriber);
        const client = this.ensureClient();
        if (client.connected) {
          this.subscribeOnBroker(destination);
        }
        return () => inner.unsubscribe();
      }).pipe(
        share({
          resetOnRefCountZero: () => {
            this.stompSubs.get(destination)?.unsubscribe();
            this.stompSubs.delete(destination);
            this.subjects.delete(destination);
            this.topics.delete(destination);
            return new Subject<RealtimeMessage>();
          },
        }),
      );
      this.topics.set(destination, topic);
    }
    return topic as Observable<RealtimeMessage<T>>;
  }

  watchType<T = unknown>(destination: string, ...types: RealtimeMessage['type'][]): Observable<RealtimeMessage<T>> {
    return this.watch<T>(destination).pipe(filter((m) => types.includes(m.type)));
  }

  private subscribeOnBroker(destination: string): void {
    if (!this.client?.connected || this.stompSubs.has(destination)) {
      return;
    }
    const sub = this.client.subscribe(destination, (msg: IMessage) => {
      try {
        this.subjects.get(destination)?.next(JSON.parse(msg.body) as RealtimeMessage);
      } catch {
        /* mensaje no JSON: se ignora */
      }
    });
    this.stompSubs.set(destination, sub);
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.stompSubs.clear();
    this.connected.set(false);
  }
}
