import '@testing-library/jest-dom';
import { configureAxe, toHaveNoViolations } from 'jest-axe';
import { expect } from 'vitest';

expect.extend(toHaveNoViolations);

// Configure axe to ignore known cosmetic issues from inline styles
// (color-contrast checks require computed style which jsdom doesn't fully support)
export const axe = configureAxe({
  rules: {
    'color-contrast': { enabled: false },
  },
});
