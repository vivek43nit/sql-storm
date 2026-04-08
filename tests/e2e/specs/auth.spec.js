// @ts-check
const { test, expect } = require('@playwright/test');

const BASE = '/fkblitz';

/**
 * Auth flow E2E tests.
 * Covers: login success, login failure, unauthorized access, logout.
 */

test.describe('Authentication', () => {
  // These tests need a fresh (unauthenticated) context — override storageState
  test.use({ storageState: { cookies: [], origins: [] } });

  test('valid credentials show the dashboard', async ({ page }) => {
    await page.goto(`${BASE}/`);
    await page.fill('input[name="username"], input[type="text"]', 'admin');
    await page.fill('input[type="password"]', 'changeme');
    await page.click('button[type="submit"]');

    // Dashboard / nav panel must be visible
    await expect(page.locator('nav, [data-testid="nav-panel"], .nav-panel')).toBeVisible({
      timeout: 15_000,
    });
  });

  test('wrong password shows error message', async ({ page }) => {
    await page.goto(`${BASE}/`);
    await page.fill('input[name="username"], input[type="text"]', 'admin');
    await page.fill('input[type="password"]', 'wrong-password');
    await page.click('button[type="submit"]');

    // Error message should appear (not redirect to dashboard)
    await expect(
      page.locator('[role="alert"], .error, [data-testid="login-error"]')
    ).toBeVisible({ timeout: 5_000 });
    // Still on login page
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('accessing protected route without session redirects to login', async ({ page }) => {
    await page.goto(`${BASE}/`);
    // Without auth, app should show login form
    await expect(page.locator('input[type="password"]')).toBeVisible({ timeout: 10_000 });
  });
});

test.describe('Authenticated session', () => {
  // Uses the admin session from globalSetup (auth.json)

  test('logout clears session and returns to login', async ({ page }) => {
    await page.goto(`${BASE}/`);
    // Verify we are logged in
    await expect(page.locator('nav, [data-testid="nav-panel"]')).toBeVisible();

    // Click logout button
    await page.click('[data-testid="logout-btn"], button:has-text("Logout"), button:has-text("Sign out")');

    // Should return to login screen
    await expect(page.locator('input[type="password"]')).toBeVisible({ timeout: 10_000 });
  });
});
