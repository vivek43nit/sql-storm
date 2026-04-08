// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/**
 * FkBlitz Playwright E2E configuration.
 *
 * Prerequisites:
 *   1. Start the full stack:  docker compose up -d
 *   2. Install browsers:      npx playwright install chromium
 *   3. Run tests:             npx playwright test
 *
 * The tests log in once and store session state in auth.json (gitignored).
 * Subsequent tests reuse the session — login happens only in globalSetup.
 */

module.exports = defineConfig({
  testDir: './specs',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
  ],

  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:9044',
    // Re-use stored auth state (populated by globalSetup)
    storageState: 'auth.json',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'off',
  },

  globalSetup: require.resolve('./global-setup.js'),

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
