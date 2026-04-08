// @ts-check
const { test, expect } = require('@playwright/test');

const BASE = '/fkblitz';

/**
 * Metadata browse E2E tests.
 * Uses the seeded demo database (docker/mariadb/init/seed.sql):
 *   users, orders, order_items, products
 *
 * Nav is driven by <select> dropdowns (#nav-group-select, #nav-db-select).
 * Tables are rendered as plain <div> elements in the sidebar.
 */
test.describe('Metadata Browse', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE}/`);
    await expect(page.locator('#nav-group-select')).toBeVisible({ timeout: 10_000 });
  });

  test('selecting a group populates the database dropdown', async ({ page }) => {
    await page.selectOption('#nav-group-select', 'demo');

    // Wait for databases API to respond and populate the select with a real option
    await expect(page.locator('#nav-db-select option[value="demo"]')).toBeAttached({ timeout: 8_000 });
    const options = await page.locator('#nav-db-select option').allTextContents();
    expect(options.some(o => o.trim().length > 0 && o !== '— select —')).toBeTruthy();
  });

  test('selecting a database populates the table list', async ({ page }) => {
    await page.selectOption('#nav-group-select', 'demo');
    await expect(page.locator('#nav-db-select option[value="demo"]')).toBeAttached({ timeout: 8_000 });
    await page.selectOption('#nav-db-select', 'demo');

    // At least one table div should appear in the sidebar
    await expect(page.locator('[data-testid="table-item-users"]')).toBeVisible({ timeout: 10_000 });
  });

  test('seeded tables are visible in the sidebar', async ({ page }) => {
    await page.selectOption('#nav-group-select', 'demo');
    await page.selectOption('#nav-db-select', 'demo');

    await page.waitForTimeout(1_000); // allow tables to render
    const sidebarText = await page.locator('div[style*="sidebar"], nav, aside, div').allTextContents();
    const combined = sidebarText.join(' ').toLowerCase();

    expect(combined).toContain('users');
    expect(combined).toContain('orders');
  });

  test('clicking a table loads column grid', async ({ page }) => {
    await page.selectOption('#nav-group-select', 'demo');
    await expect(page.locator('#nav-db-select option[value="demo"]')).toBeAttached({ timeout: 8_000 });
    await page.selectOption('#nav-db-select', 'demo');

    // Click "users" table in the sidebar
    await expect(page.locator('[data-testid="table-item-users"]')).toBeVisible({ timeout: 8_000 });
    await page.locator('[data-testid="table-item-users"]').click();

    // Table grid must appear with at least one column header
    await expect(page.locator('table th').first()).toBeVisible({ timeout: 10_000 });
  });
});
