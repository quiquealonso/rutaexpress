// Proxy de `ng serve` hacia el backend. Puerto configurable: BACKEND_URL=http://localhost:8082 npm run start:admin
const target = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default {
  '/api': { target, secure: false },
  '/public': { target, secure: false },
  '/ws': { target, secure: false, ws: true },
  '/mcp': { target, secure: false },
};
