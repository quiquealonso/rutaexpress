import { expect, test } from '@playwright/test';

const PASSWORD = process.env['DEMO_PASSWORD'] ?? 'RutaExpress-demo-2026';

/**
 * Flujo completo del repartidor: escanear → optimizar → entregar.
 * Los códigos se introducen a mano (en CI no hay cámara). Necesita la base de datos de demo recién creada.
 */
test('escanear la carga, calcular la ruta y entregar la primera parada', async ({ page, request }) => {
  await page.goto('/app/login');
  await page.getByLabel('Email').fill('lucia@rutaexpress.local');
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Entrar' }).click();

  await page.getByRole('button', { name: /Empezar la carga/ }).click();
  await expect(page).toHaveURL(/\/app\/scan/);

  // Códigos pendientes de la carga (los da la propia API)
  const session = JSON.parse((await page.evaluate(() => localStorage.getItem('rutaexpress.driver.session'))) ?? '{}');
  const load = await (await request.get('/api/loads/current', { headers: { Authorization: `Bearer ${session.accessToken}` } })).json();
  for (const missing of load.missing as { trackingCode: string }[]) {
    await page.getByLabel('Código manual').fill(missing.trackingCode);
    await page.getByRole('button', { name: 'Añadir' }).click();
    await expect(page.getByText(missing.trackingCode).first()).toBeVisible();
  }

  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: /Cerrar carga/ }).click();
  await expect(page.getByRole('button', { name: /Iniciar reparto/ })).toBeVisible({ timeout: 30_000 });
  await page.getByRole('button', { name: /Iniciar reparto/ }).click();

  await page.getByRole('link', { name: /Siguiente parada/ }).click();
  await page.getByRole('button', { name: /Entregar/ }).click();
  await page.getByText('Sin prueba').click();
  await page.getByLabel('Quién recoge').fill('Portero');
  await page.getByRole('button', { name: 'Confirmar entrega' }).click();
  await expect(page.getByText('Entregado')).toBeVisible();
});
