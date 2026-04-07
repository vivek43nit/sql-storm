# JavaScript Guidelines

Source: [Google JavaScript Style Guide](https://google.github.io/styleguide/jsguide.html)

> Prefer TypeScript for new projects. Use this guide only for pure JS codebases.

## Style

- **Indentation:** 2 spaces.
- **Semicolons:** Required.
- **Quotes:** Single quotes. Template literals for interpolation.
- **var:** Never. Use `const` by default, `let` when reassignment is needed.
- **Arrow functions:** Prefer for callbacks. Named functions for top-level declarations.
- **Naming:** Same as TypeScript (camelCase functions/vars, PascalCase classes).

## Modules

- Always use ES modules (`import`/`export`). No CommonJS `require()` in new code.
- One class or logical group per file.

```javascript
// Good
import { calculateTotal } from './cart.js';
export function applyDiscount(total, rate) { ... }

// Bad
const { calculateTotal } = require('./cart');
module.exports = { applyDiscount };
```

## TDD in JavaScript

- Test framework: **Jest** or **Vitest**
- Same naming and structure as TypeScript guidelines above.

## Tooling

- Linter: **ESLint** with Google config (`eslint-config-google`)
- Formatter: **Prettier**
- Test: **Vitest** or **Jest**
