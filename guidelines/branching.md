# Branching & Release Strategy

## Branching Model — Trunk-Based Development (Default)

All developers commit to `main` (trunk) at least once per day. Long-lived feature branches are a symptom of integration fear, not a solution to it.

**Rules:**
- `main` is always deployable
- Feature branches live < 2 days — if longer, use a feature flag
- No direct commits to `main` — always via PR
- PRs require 1 approval minimum (2 for security/payment/infra changes)
- Delete branches after merge

**Branch naming:**

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feat/<ticket>-<short-desc>` | `feat/PROJ-123-add-checkout` |
| Bug fix | `fix/<ticket>-<short-desc>` | `fix/PROJ-456-cart-total` |
| Hotfix | `hotfix/<ticket>-<short-desc>` | `hotfix/PROJ-789-payment-crash` |
| Chore | `chore/<short-desc>` | `chore/update-dependencies` |
| Release | `release/v<semver>` | `release/v2.3.0` |

## Semantic Versioning

Format: `MAJOR.MINOR.PATCH` — e.g. `v2.3.1`

| Increment | When |
|-----------|------|
| MAJOR | Breaking API change — clients must update |
| MINOR | New feature, backward-compatible |
| PATCH | Bug fix, backward-compatible |

Pre-release: `v2.3.0-beta.1`, `v2.3.0-rc.1`

## Release Process

1. Create `release/vX.Y.Z` branch from `main`
2. Bump version in manifest file (`package.json`, `pyproject.toml`, `go.mod`, etc.)
3. Generate changelog: `git-cliff` or `conventional-changelog`
4. PR → merge to `main`
5. Tag: `git tag -s vX.Y.Z -m "Release vX.Y.Z"`
6. Push tag — CI builds and publishes the release artifact
7. Create GitHub/GitLab Release with the changelog

## Changelog

Use conventional commits to auto-generate changelogs. Every release must have a `CHANGELOG.md` entry.

Format:
```markdown
## [2.3.0] - 2026-04-06

### Added
- feat(auth): add OAuth2 PKCE flow

### Fixed
- fix(cart): prevent duplicate item on rapid double-click

### Security
- fix(auth): rotate session token on privilege escalation
```

Tool: `git-cliff` — configure in `cliff.toml` at repo root.

## Hotfix Process

For P0/P1 production incidents:

1. Branch from the release tag: `git checkout -b hotfix/PROJ-789 v2.3.0`
2. Fix, test, commit
3. PR → merge to `main`
4. Cherry-pick to current release branch if needed
5. Tag: `v2.3.1`

Never hotfix directly on `main` without a branch + PR.

## Environment Promotion

```
developer machine → staging → production
```

- `main` auto-deploys to staging
- Production deploys are manually triggered (or on release tag push)
- No code skips staging
