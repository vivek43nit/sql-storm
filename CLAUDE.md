# Claude Code — Company Base Guidelines

This is the canonical base configuration for all projects. Language-specific guidelines
are auto-selected based on detected languages in the project.

## Active Language Guidelines

@guidelines/active.md
@guidelines/java.md

## Universal Guidelines

@guidelines/base.md

---

## Plugins Active in This Project

| Plugin | Purpose |
|--------|---------|
| **superpowers** | TDD workflows, plans, code review, parallel agents |
| **code-review** | PR review skill (`/code-review`) |
| **code-simplifier** | Refactoring and simplification (`/simplify`) |
| **security-guidance** | Security analysis — auto-invoked for sensitive areas (see Security Review below) |
| **context7** | Fetch live library docs — use for any library/SDK question |
| **ralph-loop** | Recurring task loops (`/ralph-loop`) |
| **claude-code-setup** | Automation recommendations for new projects |
| **claude-md-management** | Audit and improve CLAUDE.md files |

## When to Use Plan Mode vs Respond Directly

Before responding to any request, classify it:

| Signal | Mode | Action |
|--------|------|--------|
| New feature, new module, new service | **Plan** | Use `superpowers:writing-plans` |
| Change touches 3+ files | **Plan** | Use `superpowers:writing-plans` |
| Unclear scope or ambiguous requirements | **Clarify** | Ask one focused question, then plan |
| Bug fix in 1–2 files (first 2 attempts) | **Direct** | Respond and fix immediately |
| Same bug fix attempted 2+ times without resolution | **Plan** | Use `superpowers:systematic-debugging` then `superpowers:writing-plans` |
| Question, explanation, code review | **Direct** | Respond immediately |
| Refactor of existing code | **Plan** | Use `superpowers:writing-plans` |
| Single config change or typo fix | **Direct** | Respond immediately |
| Auth, payments, or external API integration | **Plan + Security** | Use `superpowers:writing-plans`, then invoke `security-guidance` before writing code |
| DB schema design, migrations, or ORM modeling | **Plan + Security** | Use `superpowers:writing-plans`, then invoke `security-guidance` before writing code |
| App config, env vars, secrets management, infrastructure | **Plan + Security** | Use `superpowers:writing-plans`, then invoke `security-guidance` before writing code |
| Any UI component or frontend feature | **Plan + Accessibility** | Use `superpowers:writing-plans`; run axe-core check after implementation |

**Rule of thumb:** If you cannot describe the full change in one sentence without saying "and", use plan mode.

**Bug escalation rule:** If the same error persists after 2 fix attempts, stop and say: *"This bug is proving harder to isolate — I'll use systematic debugging and write a plan before the next attempt."* Then use `superpowers:systematic-debugging` to root-cause first, and `superpowers:writing-plans` before touching code again.

> When in doubt, ask: *"This looks like a multi-step task — should I write a plan first, or do you want me to proceed directly?"*

## Security Review (When to Invoke security-guidance)

**Always** invoke the `security-guidance` plugin before writing or reviewing code in these areas:

| Area | Examples |
|------|---------|
| Authentication & authorisation | Login, JWT, OAuth, session management, RBAC, API keys |
| Payments & financial data | Checkout, billing, transactions, PII storage |
| External API integration | Third-party webhooks, outbound HTTP with credentials |
| Database modeling | Schema design, migrations, raw queries, ORM relationships |
| Configuration & secrets | `.env` files, secret managers, environment variables, infra config |

**Workflow for Plan + Security tasks:**
1. Clarify scope if needed
2. Use `superpowers:writing-plans` to write the implementation plan
3. Invoke `security-guidance` — review the plan for security issues before any code is written
4. Implement following the plan
5. After implementation, invoke `security-guidance` again as part of final review

**For code review:** Always invoke `security-guidance` when reviewing PRs that touch any of the areas above, in addition to the standard `superpowers:requesting-code-review` flow.

## How Language Detection Works

When a session starts, `.claude/hooks/detect-languages.sh` scans the project root for
language indicators (file extensions, manifest files) and writes `guidelines/active.md`.
This file is then imported above, so Claude receives the correct language guidelines
without manual configuration.

**Supported languages:** Python, TypeScript, JavaScript, Go, Java, Kotlin, Rust

To add a new language: create `guidelines/<lang>.md` and add detection logic in
`.claude/hooks/detect-languages.sh`.

## Security Hook

`.claude/hooks/security-scan.sh` runs before every Write/Edit operation, scanning
content for hardcoded secrets, private keys, and AWS credentials. It warns but does
not block by default.

> **To make security scanning blocking** (recommended for team enforcement):
> Edit `.claude/hooks/security-scan.sh` and change the last line from `exit 0` to `exit 2`.
> With `exit 2`, Claude will be blocked from writing the file until the issue is resolved.

## README Maintenance Rule

**Any change to installer behaviour, user-facing output, or project structure must include a README update in the same PR/commit.**

This applies to changes in:
- `install.sh` or `remote-install.sh` — new flags, new output, changed behaviour
- `guidelines/` — new files, renamed files, removed files
- `.claude/hooks/` — new hooks or changed hook behaviour
- CI templates in `ci/`
- Any new user-facing feature or option

When writing a plan that touches any of the above, always include a README task. When executing inline, always check README before marking work complete.

---

## For New Projects

Run from this repo:
```bash
bash install.sh /path/to/your/project
```

Then copy the appropriate CI template:
- GitHub Actions: `ci/github/quality-gates.yml` → `.github/workflows/quality-gates.yml`
- GitLab CI: `ci/gitlab/quality-gates.yml` → `.gitlab-ci.yml`
