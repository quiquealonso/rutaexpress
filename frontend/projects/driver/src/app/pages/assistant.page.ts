import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { API_BASE_URL, AuthService } from '@rutaexpress/shared';
import { firstValueFrom } from 'rxjs';
import { speak } from '../core/feedback';
import { GeolocationService } from '../core/geolocation.service';

interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
}

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  start(): void;
  stop(): void;
  onresult: ((e: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onend: (() => void) | null;
  onerror: (() => void) | null;
}

/**
 * Chat con el asistente IA (que por dentro usa las herramientas MCP). Entrada por voz (Web Speech API)
 * y lectura en voz alta de las respuestas. Por seguridad vial, con el vehículo en marcha solo se permite la voz.
 */
@Component({
  selector: 'app-assistant',
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatInputModule, MatSlideToggleModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!enabled()) {
      <p class="muted">El asistente no está disponible en este momento.</p>
    } @else {
      @if (moving()) {
        <div class="moving">Vehículo en marcha: usa solo la voz. El chat completo se muestra con el vehículo parado.</div>
      }
      <section class="chat" [class.hidden]="moving()" #chat>
        @for (t of turns(); track $index) {
          <div class="bubble" [class]="t.role">{{ t.content }}</div>
        } @empty {
          <div class="hint">Prueba: «¿Cuál es la siguiente parada?», «¿Cuánto me queda?», «¿Dónde va el paquete de García?», «Marca la parada como entregada».</div>
        }
        @if (streaming()) { <div class="bubble assistant typing">{{ partial() || '…' }}</div> }
      </section>

      <div class="controls">
        <button mat-fab extended class="mic" [class.listening]="listening()" (click)="toggleMic()" [disabled]="!speechSupported || streaming()">
          <mat-icon>{{ listening() ? 'stop' : 'mic' }}</mat-icon> {{ listening() ? 'Escuchando…' : 'Hablar' }}
        </button>
        @if (!moving()) {
          <form class="input" (ngSubmit)="send(text.value)">
            <mat-form-field><mat-label>Escribe tu pregunta</mat-label><input matInput [formControl]="text" /></mat-form-field>
            <button mat-icon-button [disabled]="!text.value || streaming()" aria-label="Enviar"><mat-icon>send</mat-icon></button>
          </form>
        }
        <mat-slide-toggle [checked]="voiceOut()" (change)="voiceOut.set($event.checked)">Leer respuestas en voz alta</mat-slide-toggle>
      </div>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100%; }
    .chat { flex: 1; overflow: auto; display: flex; flex-direction: column; gap: 8px; padding-bottom: 8px; }
    .chat.hidden { display: none; }
    .bubble { max-width: 85%; padding: 10px 14px; border-radius: 16px; white-space: pre-wrap; }
    .user { align-self: flex-end; background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }
    .assistant { align-self: flex-start; background: var(--mat-sys-surface-container-high); }
    .typing { opacity: .8; }
    .hint, .muted { color: var(--mat-sys-on-surface-variant); }
    .moving { padding: 16px; border-radius: 12px; background: var(--rx-warn-bg); color: var(--rx-warn-fg); font-size: 1.05rem; margin-bottom: 12px; }
    .controls { display: grid; gap: 8px; }
    .mic { width: 100%; height: 64px; font-size: 1.1rem; }
    .mic.listening { background: var(--rx-bad) !important; }
    .input { display: flex; align-items: center; gap: 4px; }
    .input mat-form-field { flex: 1; }
  `,
})
export class AssistantPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = inject(API_BASE_URL);
  private readonly gps = inject(GeolocationService);
  private readonly chatEl = viewChild<ElementRef<HTMLElement>>('chat');

  protected readonly enabled = signal(true);
  protected readonly turns = signal<ChatTurn[]>([]);
  protected readonly partial = signal('');
  protected readonly streaming = signal(false);
  protected readonly listening = signal(false);
  protected readonly voiceOut = signal(true);
  protected readonly serverStationary = signal(true);
  protected readonly text = new FormControl('', { nonNullable: true });
  // En marcha si el GPS del móvil o el servidor (última posición) indican velocidad
  protected readonly moving = computed(() => (this.gps.speedKmh() ?? 0) > 5 || !this.serverStationary());

  protected readonly speechSupported = 'webkitSpeechRecognition' in window || 'SpeechRecognition' in window;
  private recognition?: SpeechRecognitionLike;
  private abort?: AbortController;

  constructor() {
    this.http.get<{ enabled: boolean; stationary: boolean }>(`${this.base}/api/assistant/status`).subscribe({
      next: (s) => {
        this.enabled.set(s.enabled);
        this.serverStationary.set(s.stationary);
      },
      error: () => this.enabled.set(false),
    });
    effect(() => {
      this.turns();
      this.partial();
      queueMicrotask(() => this.chatEl()?.nativeElement.scrollTo({ top: 1e6 }));
    });
    inject(DestroyRef).onDestroy(() => {
      this.abort?.abort();
      this.recognition?.stop();
      window.speechSynthesis?.cancel();
    });
  }

  protected toggleMic(): void {
    if (this.listening()) {
      this.recognition?.stop();
      return;
    }
    const Ctor = (window as unknown as Record<string, new () => SpeechRecognitionLike>)['SpeechRecognition']
      ?? (window as unknown as Record<string, new () => SpeechRecognitionLike>)['webkitSpeechRecognition'];
    if (!Ctor) return;
    const rec = new Ctor();
    rec.lang = 'es-ES';
    rec.interimResults = false;
    rec.onresult = (e) => {
      const transcript = e.results[0]?.[0]?.transcript ?? '';
      if (transcript) void this.send(transcript);
    };
    rec.onend = () => this.listening.set(false);
    rec.onerror = () => this.listening.set(false);
    this.recognition = rec;
    this.listening.set(true);
    window.speechSynthesis?.cancel();
    rec.start();
  }

  /** Envía la pregunta y lee la respuesta en streaming (Server-Sent Events sobre fetch). */
  protected async send(message: string): Promise<void> {
    const question = message.trim();
    if (!question || this.streaming()) return;
    this.text.reset();
    const history = this.turns();
    this.turns.set([...history, { role: 'user', content: question }]);
    this.streaming.set(true);
    this.partial.set('');
    let answer = '';
    try {
      if (this.auth.isAccessTokenExpired) await firstValueFrom(this.auth.refresh());
      this.abort = new AbortController();
      const response = await fetch(`${this.base}/api/assistant/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          Authorization: `Bearer ${this.auth.accessToken}`,
        },
        body: JSON.stringify({ message: question, history: history.slice(-12) }),
        signal: this.abort.signal,
      });
      if (!response.ok || !response.body) {
        throw new Error(response.status === 429 ? 'Demasiadas preguntas seguidas, espera un minuto.' : 'El asistente no ha podido responder.');
      }
      const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
      let buffer = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += value;
        const events = buffer.split('\n\n');
        buffer = events.pop() ?? '';
        for (const raw of events) {
          const lines = raw.split('\n');
          const event = lines.find((l) => l.startsWith('event:'))?.slice(6).trim();
          const data = lines.filter((l) => l.startsWith('data:')).map((l) => l.slice(5)).join('\n');
          if (event === 'token' || event === 'error') {
            answer += data;
            this.partial.set(answer);
          }
        }
      }
    } catch (e) {
      if ((e as Error).name !== 'AbortError') answer = (e as Error).message;
    } finally {
      this.streaming.set(false);
      this.partial.set('');
      if (answer) {
        this.turns.update((t) => [...t, { role: 'assistant', content: answer }]);
        if (this.voiceOut()) speak(answer);
      }
    }
  }
}
