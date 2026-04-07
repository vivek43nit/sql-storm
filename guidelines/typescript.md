# TypeScript Guidelines

Source: [Google TypeScript Style Guide](https://google.github.io/styleguide/tsguide.html)

## Style

- **Indentation:** 2 spaces.
- **Line length:** 80 characters max.
- **Semicolons:** Required.
- **Quotes:** Single quotes for strings. Template literals for interpolation.
- **Trailing commas:** Required in multi-line structures.
- **Naming:**
  - `ClassName` (PascalCase)
  - `functionName`, `variableName` (camelCase)
  - `CONSTANT_VALUE` (SCREAMING_SNAKE_CASE for true constants)
  - `InterfaceName` — no `I` prefix
  - `TypeAlias` — PascalCase
  - Files: `kebab-case.ts`

## Types

- **Always** explicit return types on exported functions.
- Never use `any`. Use `unknown` and narrow it.
- Prefer `interface` for object shapes, `type` for unions/intersections.
- Enable `strict: true` in `tsconfig.json`.

```typescript
// Good
interface UserRepository {
  findById(id: string): Promise<User | null>;
  save(user: User): Promise<void>;
}

async function getUser(id: string, repo: UserRepository): Promise<User> {
  const user = await repo.findById(id);
  if (!user) throw new UserNotFoundError(id);
  return user;
}

// Bad
async function getUser(id: any, repo: any) {
  return repo.findById(id);
}
```

## TDD in TypeScript

- Test framework: **Jest** or **Vitest** (prefer Vitest for new projects)
- Coverage: minimum 80%, `--coverage` flag in CI
- Mocking: `vi.mock()` / `jest.mock()` — mock at module boundaries only

```typescript
// Naming convention: describe + it
describe('getUser', () => {
  it('throws UserNotFoundError when user does not exist', async () => {
    const repo = { findById: vi.fn().mockResolvedValue(null) };
    await expect(getUser('missing-id', repo)).rejects.toThrow(UserNotFoundError);
  });

  it('returns user when found', async () => {
    const user = { id: '1', email: 'a@b.com' };
    const repo = { findById: vi.fn().mockResolvedValue(user) };
    const result = await getUser('1', repo);
    expect(result).toEqual(user);
  });
});
```

## Error Handling

- Typed error classes for domain errors.
- Never swallow errors silently.

```typescript
class UserNotFoundError extends Error {
  constructor(userId: string) {
    super(`User not found: ${userId}`);
    this.name = 'UserNotFoundError';
  }
}
```

## Tooling

- Linter: **ESLint** with `@typescript-eslint` + Google config
- Formatter: **Prettier**
- Type check: `tsc --noEmit`
- Test: **Vitest** or **Jest**
