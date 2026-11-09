// Prueba de carga: 50 repartidores enviando posiciones GPS y consultando su ruta a la vez.
//   k6 run -e BASE_URL=http://localhost -e PASSWORD=RutaExpress-demo-2026 load-tests/k6/drivers.js
// Usa los repartidores de la demo (lucia@ y javier@) repartidos entre los usuarios virtuales.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost';
const PASSWORD = __ENV.PASSWORD || 'RutaExpress-demo-2026';
const DRIVERS = (__ENV.DRIVERS || 'lucia@rutaexpress.local,javier@rutaexpress.local').split(',');

export const options = {
  scenarios: {
    drivers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:positions}': ['p(95)<300'],
    'http_req_duration{name:route}': ['p(95)<500'],
  },
};

export function setup() {
  const tokens = {};
  for (const email of DRIVERS) {
    const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password: PASSWORD }), {
      headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'login ok': (r) => r.status === 200 });
    tokens[email] = res.json('accessToken');
  }
  return { tokens };
}

export default function (data) {
  const email = DRIVERS[__VU % DRIVERS.length];
  const headers = { Authorization: `Bearer ${data.tokens[email]}`, 'Content-Type': 'application/json' };
  // Lote de 5 posiciones alrededor de Valencia
  const now = Date.now();
  const positions = Array.from({ length: 5 }, (_, i) => ({
    lat: 39.4699 + (Math.random() - 0.5) * 0.02,
    lon: -0.3763 + (Math.random() - 0.5) * 0.02,
    speedKmh: Math.random() * 40,
    accuracyMeters: 8,
    recordedAt: new Date(now - (5 - i) * 2000).toISOString(),
  }));
  const pos = http.post(`${BASE}/api/tracking/positions`, JSON.stringify({ positions }), { headers, tags: { name: 'positions' } });
  check(pos, { 'posiciones aceptadas': (r) => r.status === 202 });

  const route = http.get(`${BASE}/api/routes/current`, { headers, tags: { name: 'route' } });
  check(route, { 'ruta 200/404': (r) => r.status === 200 || r.status === 404 });

  sleep(5);
}
