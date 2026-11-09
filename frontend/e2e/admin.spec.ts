import { expect, test } from '@playwright/test';

const PASSWORD = process.env['DEMO_PASSWORD'] ?? 'RutaExpress-demo-2026';

test('tráfico entra en el panel y ve los paquetes de la demo', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('trafico@rutaexpress.local');
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page.getByRole('heading', { name: 'Hoy' })).toBeVisible();
  await page.getByRole('link', { name: 'Paquetes' }).click();
  await expect(page.locator('table tr.mat-mdc-row').first()).toBeVisible();
});

test('el seguimiento público no necesita sesión', async ({ page }) => {
  await page.goto('/seguimiento');
  await page.getByLabel('Código de seguimiento').fill('RXNOEXISTE1');
  await page.getByRole('button', { name: 'Buscar' }).click();
  await expect(page.getByText('No encontramos ningún paquete')).toBeVisible();
});
