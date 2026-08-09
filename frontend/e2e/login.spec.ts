import { test, expect } from '@playwright/test';

const baseURL = process.env['E2E_BASE_URL'];

test.describe('Login smoke', () => {
  test.skip(!baseURL, 'E2E_BASE_URL not set — skipping smoke login');

  test('login page renders DeviceManager branding', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('DeviceManager')).toBeVisible();
    await expect(page.getByLabel('Identifiant')).toBeVisible();
    await expect(page.getByLabel('Mot de passe')).toBeVisible();
  });
});
