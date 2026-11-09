import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra el sistema completo levantado con Docker Compose (http://localhost).
 *   npx playwright install chromium
 *   npm run e2e
 */
export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env['BASE_URL'] ?? 'http://localhost',
    trace: 'retain-on-failure',
    locale: 'es-ES',
  },
  projects: [
    { name: 'panel', use: { ...devices['Desktop Chrome'] }, testMatch: /admin\.spec\.ts/ },
    { name: 'repartidor', use: { ...devices['Pixel 7'] }, testMatch: /driver\.spec\.ts/ },
  ],
});
