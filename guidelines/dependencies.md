# Dependency Management Policy

Dependencies are attack surface. Every dependency you add is code you didn't write and can't fully control.

## Adding New Dependencies

Before adding any dependency, ask:
1. Is this functionality already available in the standard library?
2. Is this dependency actively maintained (commits in last 6 months)?
3. What is its license? (see License Policy below)
4. How many transitive dependencies does it add?
5. Has it had critical CVEs in the last 12 months?

**Rule:** Any new production dependency requires a brief justification comment in the PR.

## Automated Updates — Renovate (Required)

Every repo must have a `renovate.json` at root:

```json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": ["config:base"],
  "schedule": ["every weekend"],
  "automerge": false,
  "packageRules": [
    {
      "matchUpdateTypes": ["patch"],
      "automerge": true,
      "matchPackagePatterns": ["*"]
    },
    {
      "matchUpdateTypes": ["minor", "major"],
      "automerge": false,
      "reviewers": ["team:backend"]
    }
  ]
}
```

- Patch updates: auto-merge if CI passes
- Minor updates: require 1 approval
- Major updates: require team discussion

## Lock Files

- Lock files (`package-lock.json`, `poetry.lock`, `go.sum`, `Cargo.lock`) must be committed
- Never run CI without a lock file
- Never use `--no-lockfile` in production builds

## License Policy

| License | Commercial use | Action |
|---------|---------------|--------|
| MIT, Apache 2.0, BSD | ✅ Allowed | No action needed |
| ISC, Unlicense | ✅ Allowed | No action needed |
| LGPL | ⚠️ Check | Allowed if dynamically linked — verify |
| GPL, AGPL | ❌ Blocked | Do not use in commercial products |
| Commercial / proprietary | ⚠️ Approve | Requires legal + finance approval |

Run license scanning in CI to catch violations automatically:
- Node: `license-checker`
- Python: `pip-licenses`
- Rust: `cargo-license`
- Go: `go-licenses`

## Vulnerability Scanning

- CI must run a vulnerability scan on every PR (Trivy, already configured in quality-gates.yml)
- HIGH and CRITICAL CVEs block merge
- MEDIUM CVEs: create a tracking ticket and fix within 30 days
- LOW CVEs: fix in next scheduled dependency update cycle

## Dependency Review on PRs

Add to `ci/github/quality-gates.yml` for GitHub projects:

```yaml
dependency-review:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/dependency-review-action@v4
      with:
        fail-on-severity: high
        deny-licenses: GPL-2.0, GPL-3.0, AGPL-3.0
```
