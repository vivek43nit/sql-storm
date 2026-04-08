// @ts-check
const { chromium } = require('@playwright/test');
const path = require('path');

/**
 * Global setup: logs in as admin and saves session state to auth.json.
 * All E2E spec files reuse this session — no repeated logins.
 */
async function globalSetup() {
  const BASE_URL = process.env.BASE_URL || 'http://localhost:9044';
  const USERNAME = process.env.E2E_USERNAME || 'admin';
  const PASSWORD = process.env.E2E_PASSWORD || 'changeme';

  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto(`${BASE_URL}/fkblitz/`);

  // Fill and submit the login form
  await page.fill('input[name="username"], input[placeholder*="user" i]', USERNAME);
  await page.fill('input[name="password"], input[type="password"]', PASSWORD);
  await page.click('button[type="submit"]');

  // Wait for the app shell to be visible (confirms successful login)
  await page.waitForSelector('[data-testid="nav-panel"], .nav-panel, nav', { timeout: 15_000 });

  // Save session state (cookies + localStorage)
  await context.storageState({ path: path.join(__dirname, 'auth.json') });
  await browser.close();
}

module.exports = globalSetup;
