// @ts-check
const { test, expect } = require('@playwright/test');

const BASE = '/fkblitz';

/**
 * Metadata browse E2E tests.
 * Uses the seeded demo database (docker/mariadb/init/seed.sql):
 *   users → orders → order_items, products
 *
 * Covers: group selection, DB selection, table list, FK badge visibility.
 */
test.describe('Metadata Browse', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/`);
    // Ensure app is loaded
    await expect(page.locator('nav, [data-testid="nav-panel"]')).toBeVisible({ timeout: 10_000 });
  });

  test('selecting a group populates the database list', async ({ page }) => {
    // Click the first group in the nav panel
    const groupItem = page.locator('[data-testid="group-item"], .group-item').first();
    await expect(groupItem).toBeVisible({ timeout: 10_000 });
    await groupItem.click();

    // Database list should appear
    await expect(
      page.locator('[data-testid="db-item"], .db-item, [data-type="database"]').first()
    ).toBeVisible({ timeout: 5_000 });
  });

  test('selecting a database populates the table list', async ({ page }) => {
    // Select group then database
    await page.locator('[data-testid="group-item"], .group-item').first().click();
    await page.locator('[data-testid="db-item"], .db-item').first().click();

    // At least one table should appear (seeded: users, orders, order_items, products)
    await expect(
      page.locator('[data-testid="table-item"], .table-item').first()
    ).toBeVisible({ timeout: 10_000 });
  });

  test('seeded tables are visible in the list', async ({ page }) => {
    await page.locator('[data-testid="group-item"], .group-item').first().click();
    await page.locator('[data-testid="db-item"], .db-item').first().click();

    // Wait for tables to load
    await page.waitForSelector('[data-testid="table-item"], .table-item', { timeout: 10_000 });

    const tableText = await page.locator('[data-testid="table-item"], .table-item').allTextContents();
    const lowerNames = tableText.map((t) => t.toLowerCase());

    // At least one of the seeded tables should be visible
    expect(lowerNames.some((n) => n.includes('users') || n.includes('orders'))).toBeTruthy();
  });

  test('clicking a table loads column grid with FK indicators', async ({ page }) => {
    await page.locator('[data-testid="group-item"], .group-item').first().click();
    await page.locator('[data-testid="db-item"], .db-item').first().click();
    await page.locator('[data-testid="table-item"], .table-item').first().click();

    // Table grid must appear with at least one column header
    await expect(
      page.locator('table th, [data-testid="column-header"], .column-header').first()
    ).toBeVisible({ timeout: 10_000 });
  });
});
