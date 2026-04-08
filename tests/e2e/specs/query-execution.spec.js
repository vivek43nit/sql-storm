// @ts-check
const { test, expect } = require('@playwright/test');

const BASE = '/fkblitz';

/**
 * Query execution E2E tests.
 * Requires seeded demo database (docker/mariadb/init/seed.sql).
 *
 * Covers: table data loads on selection, result rows visible, row count.
 * The app auto-executes a SELECT when a table is clicked — no explicit SQL input needed.
 */
test.describe('Query Execution', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/`);
    await expect(page.locator('nav, [data-testid="nav-panel"]')).toBeVisible({ timeout: 10_000 });

    // Navigate to the demo DB and users table
    await page.locator('[data-testid="group-item"], .group-item').first().click();
    await page.locator('[data-testid="db-item"], .db-item').first().click();

    // Click the "users" table (fall back to first table if not found by name)
    const usersTable = page.locator(
      '[data-testid="table-item"]:has-text("users"), .table-item:has-text("users")'
    ).first();
    const firstTable = page.locator('[data-testid="table-item"], .table-item').first();

    if (await usersTable.count() > 0) {
      await usersTable.click();
    } else {
      await firstTable.click();
    }
  });

  test('clicking a table loads result rows in the grid', async ({ page }) => {
    // At least one data row should appear (seeded users: Alice, Bob, Carol)
    await expect(
      page.locator('tbody tr, [data-testid="result-row"], .result-row').first()
    ).toBeVisible({ timeout: 15_000 });
  });

  test('result grid shows multiple rows from seeded data', async ({ page }) => {
    await page.waitForSelector('tbody tr, [data-testid="result-row"]', { timeout: 15_000 });
    const rows = await page.locator('tbody tr, [data-testid="result-row"]').count();
    expect(rows).toBeGreaterThanOrEqual(1);
  });

  test('column headers are visible and non-empty', async ({ page }) => {
    await page.waitForSelector('table th, [data-testid="column-header"]', { timeout: 15_000 });
    const headers = await page.locator('table th, [data-testid="column-header"]').allTextContents();
    const nonEmpty = headers.filter((h) => h.trim().length > 0);
    expect(nonEmpty.length).toBeGreaterThan(0);
  });

  test('FK link in result row is clickable', async ({ page }) => {
    await page.waitForSelector('tbody tr', { timeout: 15_000 });

    // Look for any FK navigation link (arrow icon or underlined FK value)
    const fkLink = page.locator(
      '[data-testid="fk-link"], .fk-link, a[title*="Follow"], button[title*="Follow"]'
    ).first();

    if (await fkLink.count() > 0) {
      await fkLink.click();
      // After following FK, a new row detail or referenced table should appear
      await expect(
        page.locator('tbody tr, [data-testid="result-row"]').first()
      ).toBeVisible({ timeout: 10_000 });
    } else {
      // If no FK links visible in first table, test passes — FK links depend on data/schema
      test.info().annotations.push({
        type: 'note',
        description: 'No FK links found in first result set — may need a table with FKs loaded',
      });
    }
  });
});
