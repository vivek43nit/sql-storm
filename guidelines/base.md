# Base Guidelines (All Projects)

## Test-Driven Development (Non-Negotiable)

- Write the failing test FIRST. No exceptions.
- Red → Green → Refactor. Commit at Green.
- Test behavior, not implementation. Mock at boundaries only (external APIs, DB, filesystem).
- Minimum 80% line coverage enforced in CI. Aim for 90%+ on business logic.
- Name tests: `test_<what>_<when>_<expected>` (Python) or `describe/it` (JS/TS).
- One assertion per test where possible. Multiple assertions = multiple tests.

## Commit Standards (Conventional Commits)

Format: `<type>(<scope>): <description>`

Types: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `ci`, `perf`

Examples:
- `feat(auth): add JWT refresh token endpoint`
- `fix(cart): prevent duplicate item addition`
- `test(user): add edge cases for email validation`

Rules:
- Commit after every Green (passing test). Small, frequent commits.
- Never commit commented-out code.
- Never commit secrets, credentials, or API keys.
- Breaking changes: add `!` after type — `feat!: rename user endpoint`

## Design Patterns

- **SOLID**: Single responsibility, Open/closed, Liskov substitution, Interface segregation, Dependency inversion.
- **YAGNI**: Don't build what isn't needed today.
- **DRY**: Three occurrences → extract. Two → wait and see.
- Prefer composition over inheritance.
- Depend on interfaces/abstractions, not concrete implementations.
- Keep functions/methods under 30 lines. If longer, extract.
- Max function parameters: 4. More → use a config object/struct.

## Security Principles

- Never hardcode secrets. Use environment variables or secret managers.
- Validate all input at system boundaries (user input, external APIs, file uploads).
- Use parameterised queries. No string interpolation in SQL.
- Principle of least privilege: request only permissions needed.
- Log security events (auth failures, permission denials). Never log secrets or PII.
- Dependencies: pin versions in lockfiles. Run vulnerability scans in CI.

## Code Review Checklist

Before marking a PR ready:
- [ ] All tests pass locally
- [ ] New code has tests (TDD followed)
- [ ] No hardcoded secrets or credentials
- [ ] No commented-out code
- [ ] Functions are under 30 lines
- [ ] Error cases are handled at boundaries
- [ ] CI is green

## Pull Request Standards

- One concern per PR. Large features → feature flags or stack of small PRs.
- PR description must explain *why*, not just *what*.
- Link to ticket/issue.
- Self-review before requesting review.
