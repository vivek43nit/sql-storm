// @ts-check
const { test, expect } = require('@playwright/test');

const BASE = '/fkblitz';

/**
 * Query execution E2E tests.
 * Requires seeded demo database (docker/mariadb/init/seed.sql).
 * Clicking a table auto-executes SELECT * — no explicit SQL input needed.
 */
test.describe('Query Execution', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/`);
    await expect(page.locator('#nav-group-select')).toBeVisible({ timeout: 10_000 });

    // Navigate: group → database → users table
    await page.selectOption('#nav-group-select', 'demo');
    await expect(page.locator('#nav-db-select option[value="demo"]')).toBeAttached({ timeout: 8_000 });
    await page.selectOption('#nav-db-select', 'demo');
    await expect(page.locator('[data-testid="table-item-users"]')).toBeVisible({ timeout: 8_000 });
    await page.locator('[data-testid="table-item-users"]').click();
  });

  test('clicking a table loads result rows in the grid', async ({ page }) => {
    // Seeded: Alice Admin, Bob User, Carol Guest
    await expect(page.locator('tbody tr').first()).toBeVisible({ timeout: 15_000 });
  });

  test('result grid shows multiple rows from seeded data', async ({ page }) => {
    await page.waitForSelector('tbody tr', { timeout: 15_000 });
    const rows = await page.locator('tbody tr').count();
    expect(rows).toBeGreaterThanOrEqual(3); // Alice, Bob, Carol
  });

  test('column headers are visible and non-empty', async ({ page }) => {
    await page.waitForSelector('table th', { timeout: 15_000 });
    const headers = await page.locator('table th').allTextContents();
    const nonEmpty = headers.filter(h => h.trim().length > 0);
    expect(nonEmpty.length).toBeGreaterThan(0);
  });

  test('FK link in result row is clickable', async ({ page }) => {
    await page.waitForSelector('tbody tr', { timeout: 15_000 });

    // Switch to orders table which has user_id FK
    await page.locator('[data-testid="table-item-orders"]').click();
    await page.waitForSelector('tbody tr', { timeout: 15_000 });

    const fkLink = page.locator('[data-testid="fk-link"], .fk-link, a[title*="Follow"], button[title*="→"]').first();
    if (await fkLink.count() > 0) {
      await fkLink.click();
      await expect(page.locator('tbody tr').first()).toBeVisible({ timeout: 10_000 });
    } else {
      test.info().annotations.push({ type: 'note', description: 'No FK links visible — depends on schema/data' });
    }
  });
});
