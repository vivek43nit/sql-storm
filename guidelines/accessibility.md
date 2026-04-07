# Accessibility Guidelines

**Standard:** WCAG 2.1 Level AA (minimum). This is also the legal requirement in the EU (EN 301 549), UK (PSBAR), and US (Section 508).

## Core Principles (POUR)

- **Perceivable** — users can perceive all content (alt text, captions, contrast)
- **Operable** — users can operate all UI (keyboard nav, no seizure-triggering content)
- **Understandable** — users can understand content and UI (clear labels, predictable behaviour)
- **Robust** — content works with current and future assistive technologies

## Required Checks Before Every PR

- [ ] All images have descriptive `alt` text (`alt=""` for decorative images)
- [ ] All form inputs have associated `<label>` elements
- [ ] Color is never the only way to convey information
- [ ] Focus order is logical and visible (never `outline: none` without a custom focus style)
- [ ] All interactive elements are keyboard accessible (Tab, Enter, Space, Arrow keys)
- [ ] Page has a single `<h1>`, headings are hierarchical (h1 → h2 → h3)
- [ ] All interactive elements have accessible names (visible label or `aria-label`)

## Color Contrast

| Context | Minimum ratio |
|---------|--------------|
| Normal text (< 18px) | 4.5:1 |
| Large text (≥ 18px or ≥ 14px bold) | 3:1 |
| UI components (borders, icons) | 3:1 |
| Decorative elements | None required |

Check with browser DevTools accessibility panel or the WebAIM Contrast Checker.

## Semantic HTML First

Use the right element before reaching for ARIA. ARIA supplements HTML — it doesn't replace it.

```html
<!-- Bad — div soup with ARIA bolted on -->
<div role="button" tabindex="0" aria-label="Submit" onclick="submit()">Submit</div>

<!-- Good — semantic HTML, accessible by default -->
<button type="submit">Submit</button>
```

Use `<nav>`, `<main>`, `<aside>`, `<header>`, `<footer>`, `<article>`, `<section>` as landmarks.

## ARIA — When to Use

Use ARIA only when HTML semantics are insufficient:

```html
<!-- Indicating loading state -->
<div aria-live="polite" aria-atomic="true">
  <span aria-busy="true">Loading results...</span>
</div>

<!-- Custom combobox (when <select> doesn't meet design requirements) -->
<div role="combobox" aria-expanded="true" aria-haspopup="listbox">...</div>
```

Never use `role="presentation"` on interactive elements, or ARIA that conflicts with native semantics.

## Keyboard Navigation

All interactive functionality must be operable by keyboard alone:

- `Tab` / `Shift+Tab` — navigate between focusable elements
- `Enter` / `Space` — activate buttons and links
- `Arrow keys` — navigate within composite widgets (menus, tabs, listboxes)
- `Escape` — close modals, menus, tooltips

Modal dialogs must trap focus within the modal while open and return focus to the trigger on close.

## Testing

**Automated (add to CI):**
- `axe-core` via `jest-axe` or Playwright — catches ~30-40% of WCAG issues automatically

```typescript
import { axe, toHaveNoViolations } from 'jest-axe';
expect.extend(toHaveNoViolations);

test('CheckoutForm has no accessibility violations', async () => {
  const { container } = render(<CheckoutForm />);
  const results = await axe(container);
  expect(results).toHaveNoViolations();
});
```

**Manual (before each release):**
- Keyboard-only navigation of all critical flows
- Screen reader test with VoiceOver (Mac) or NVDA (Windows) on at least one flow
- Zoom to 200% — layout must not break or lose content
