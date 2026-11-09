import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { SESSION_STORAGE_KEY } from '../config';
import { authInterceptor } from './auth.interceptor';

const KEY = 'test.session';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem(
      KEY,
      JSON.stringify({
        accessToken: 'abc',
        refreshToken: 'r1',
        expiresAt: Date.now() + 600_000,
        user: { id: '1', email: 'a@b.c', role: 'ADMIN', enabled: true },
      }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: SESSION_STORAGE_KEY, useValue: KEY },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  it('añade el token Bearer a las peticiones de la API', () => {
    http.get('/api/parcels').subscribe();
    const req = backend.expectOne('/api/parcels');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc');
    req.flush({});
  });

  it('no añade el token al seguimiento público', () => {
    http.get('/public/track/RX123456').subscribe();
    const req = backend.expectOne('/public/track/RX123456');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
