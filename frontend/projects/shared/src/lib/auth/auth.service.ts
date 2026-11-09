import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, map, shareReplay, tap } from 'rxjs';
import { API_BASE_URL, SESSION_STORAGE_KEY } from '../config';
import { Role, TokenResponse, UserResponse } from '../models';

interface StoredSession {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  user: UserResponse;
}

/**
 * Sesión del usuario con signals. El access token dura poco (15 min); el refresh token es rotatorio:
 * cada refresco devuelve uno nuevo y el anterior queda invalidado en el servidor.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  // HttpBackend evita los interceptores: login y refresh no deben pasar por el de autenticación
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly router = inject(Router);
  private readonly base = inject(API_BASE_URL);
  private readonly storageKey = inject(SESSION_STORAGE_KEY);

  private readonly session = signal<StoredSession | null>(this.restore());
  private refreshInFlight: Observable<string> | null = null;

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly role = computed(() => this.session()?.user.role ?? null);

  get accessToken(): string | null {
    return this.session()?.accessToken ?? null;
  }

  get isAccessTokenExpired(): boolean {
    const s = this.session();
    return !s || Date.now() > s.expiresAt - 30_000;
  }

  hasRole(...roles: Role[]): boolean {
    const r = this.role();
    return r !== null && roles.includes(r);
  }

  login(email: string, password: string): Observable<UserResponse> {
    return this.http
      .post<TokenResponse>(`${this.base}/api/auth/login`, { email, password })
      .pipe(
        tap((t) => this.store(t)),
        map((t) => t.user),
      );
  }

  /** Refresca el token. Si ya hay un refresco en curso, todas las peticiones esperan al mismo. */
  refresh(): Observable<string> {
    const current = this.session();
    if (!current) {
      throw new Error('No hay sesión');
    }
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http
        .post<TokenResponse>(`${this.base}/api/auth/refresh`, { refreshToken: current.refreshToken })
        .pipe(
          tap((t) => this.store(t)),
          map((t) => t.accessToken),
          finalize(() => (this.refreshInFlight = null)),
          shareReplay(1),
        );
    }
    return this.refreshInFlight;
  }

  logout(redirect = true): void {
    const current = this.session();
    if (current) {
      this.http
        .post(`${this.base}/api/auth/logout`, { refreshToken: current.refreshToken })
        .subscribe({ error: () => undefined });
    }
    this.clear();
    if (redirect) {
      this.router.navigate(['/login']);
    }
  }

  clear(): void {
    this.session.set(null);
    try {
      localStorage.removeItem(this.storageKey);
    } catch {
      /* almacenamiento no disponible */
    }
  }

  private store(t: TokenResponse): void {
    const s: StoredSession = {
      accessToken: t.accessToken,
      refreshToken: t.refreshToken,
      expiresAt: Date.now() + t.expiresIn * 1000,
      user: t.user,
    };
    this.session.set(s);
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(s));
    } catch {
      /* modo privado: la sesión vive solo en memoria */
    }
  }

  private restore(): StoredSession | null {
    try {
      const raw = localStorage.getItem(this.storageKey);
      return raw ? (JSON.parse(raw) as StoredSession) : null;
    } catch {
      return null;
    }
  }
}
