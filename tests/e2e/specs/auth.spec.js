// @ts-check
const { test, expect } = require('@playwright/test');

const BASE = '/fkblitz';

/**
 * Auth flow E2E tests.
 * Covers: login success, login failure, unauthorized access, logout.
 */

test.describe('Authentication', () => {
  // Fresh unauthenticated context for each test in this block
  test.use({ storageState: { cookies: [], origins: [] } });

  test('valid credentials show the dashboard', async ({ page }) => {
    await page.goto(`${BASE}/`);
    await page.fill('#login-username', 'admin');
    await page.fill('#login-password', 'changeme');
    await page.click('button[type="submit"]');

    // Group select appears after successful login
    await expect(page.locator('#nav-group-select')).toBeVisible({ timeout: 15_000 });
  });

  test('wrong password shows error message', async ({ page }) => {
    await page.goto(`${BASE}/`);
    await page.fill('#login-username', 'admin');
    await page.fill('#login-password', 'wrong-password');
    await page.click('button[type="submit"]');

    await expect(page.locator('[data-testid="login-error"]')).toBeVisible({ timeout: 5_000 });
    // Still on login page
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('accessing app without session shows login form', async ({ page }) => {
    await page.goto(`${BASE}/`);
    await expect(page.locator('#login-password')).toBeVisible({ timeout: 10_000 });
  });
});

test.describe('Authenticated session', () => {
  // Fresh unauthenticated context — login fresh so logout doesn't kill the shared session
  test.use({ storageState: { cookies: [], origins: [] } });

  test('logout clears session and returns to login', async ({ page }) => {
    await page.goto(`${BASE}/`);
    await page.fill('#login-username', 'admin');
    await page.fill('#login-password', 'changeme');
    await page.click('button[type="submit"]');
    await expect(page.locator('#nav-group-select')).toBeVisible({ timeout: 15_000 });

    await page.click('button:has-text("Logout")');

    await expect(page.locator('#login-password')).toBeVisible({ timeout: 10_000 });
  });
});
