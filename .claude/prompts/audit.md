# claude-code-kit Audit Prompt

You are auditing this project against claude-code-kit standards.
Follow these steps in order without skipping any.

---

## PHASE 1 — SETUP AUDIT

Check each item below and mark ✓ (ok), ✗ (missing), or ~ (outdated/misconfigured).

### Structural checks (read local files only)

| Item | Check |
|------|-------|
| `.claude/hooks/detect-languages.sh` | File exists? |
| `.claude/hooks/security-scan.sh` | File exists? |
| `.claude/settings.json` | Contains UserPromptSubmit hook running `bash .claude/hooks/detect-languages.sh`? |
| `.claude/settings.json` | Contains PreToolUse hook running `bash .claude/hooks/security-scan.sh`? |
| `CLAUDE.md` | Contains `@guidelines/active.md`? |
| `.gitignore` | Contains `guidelines/active.md`? |

### Content checks (fetch latest from GitHub and diff)

For each file below, fetch `https://raw.githubusercontent.com/vivek43nit/claude-code-kit/main/<path>`
and compare to the local copy. Mark ~ if local differs meaningfully from latest, ✗ if file is missing.

- `guidelines/base.md`
- `guidelines/observability.md`
- `guidelines/testing.md`
- `guidelines/branching.md`
- `guidelines/dependencies.md`
- `guidelines/adr.md`
- `guidelines/api-design.md`
- `guidelines/database.md`
- `guidelines/feature-flags.md`
- `guidelines/incidents.md`
- `guidelines/accessibility.md`
- `guidelines/python.md`
- `guidelines/typescript.md`
- `guidelines/javascript.md`
- `guidelines/go.md`
- `guidelines/java.md`
- `guidelines/kotlin.md`
- `guidelines/rust.md`

Print the Phase 1 summary table, then ask the user:

> **Phase 1 complete — X setup issue(s) found.**
>
> How would you like to proceed?
>
> **[1]** Fix setup issues first, then run the code audit (recommended)
> **[2]** Skip to code audit now with the current setup
>
> Enter 1 or 2:

Wait for the user's response before continuing.

If the user chose **[1]**, fix all ✗ and ~ setup items now (same behaviour as running the installer — append to CLAUDE.md if missing imports, merge hooks into settings.json with jq if available, download missing/outdated guideline files). Then proceed to Phase 2.

If the user chose **[2]**, proceed directly to Phase 2 without fixing anything.

---

## PHASE 2 — CODE COMPLIANCE AUDIT

Read the project's source files. For each area below, check compliance with the
active guidelines and report findings. Skip areas with no applicable code.

### Testing
- Are there test files?
- Do tests roughly follow the pyramid (≈70% unit, 20% integration, 10% e2e)?
- Are mocks used only at system boundaries (DB, HTTP, filesystem, clock)?
- Are test names descriptive (`test_<what>_<when>_<expected>` or `describe/it`)?

### Observability
- Is logging structured (JSON or structured format)?
- Do log lines include required fields: `timestamp`, `level`, `service`, `trace_id`?
- Are secrets, passwords, or PII ever logged?
- Are there health endpoints (`/health/live`, `/health/ready`)?

### Security
- Any hardcoded secrets, API keys, or credentials in source files?
- Any SQL string interpolation (vs parameterised queries)?
- Is input validated at system boundaries (user input, external APIs, file uploads)?

### Dependencies
- Is there a `renovate.json` for automated dependency updates?
- Are lock files (`package-lock.json`, `poetry.lock`, `go.sum`, `Cargo.lock`, etc.) committed?

### Branching & Commits
- Do recent git commits follow conventional commit format (`feat:`, `fix:`, `chore:`, `docs:`, etc.)?

### API Design (if server/API code detected)
- Are there `/health/live` and `/health/ready` endpoints?
- Do error responses follow a consistent shape?

---

## REPORT

Write the complete report to `.claude/audit-report.md` using this exact format:

```
# claude-code-kit Audit Report
Generated: <today's date>

## Summary
| Phase | ✗ Missing / Failing | ~ Outdated / Issues | ✓ Ok |
|-------|---------------------|---------------------|------|
| Setup | | | |
| Code compliance | | | |

## Phase 1 — Setup Findings
| Item | Status | Notes |
|------|--------|-------|
| .claude/hooks/detect-languages.sh | | |
| .claude/hooks/security-scan.sh | | |
| .claude/settings.json — UserPromptSubmit hook | | |
| .claude/settings.json — PreToolUse hook | | |
| CLAUDE.md — @guidelines/active.md import | | |
| .gitignore — guidelines/active.md | | |
| guidelines/base.md | | |
| guidelines/observability.md | | |
| guidelines/testing.md | | |
| guidelines/branching.md | | |
| guidelines/dependencies.md | | |
| guidelines/adr.md | | |
| guidelines/api-design.md | | |
| guidelines/database.md | | |
| guidelines/feature-flags.md | | |
| guidelines/incidents.md | | |
| guidelines/accessibility.md | | |
| guidelines/python.md | | |
| guidelines/typescript.md | | |
| guidelines/javascript.md | | |
| guidelines/go.md | | |
| guidelines/java.md | | |
| guidelines/kotlin.md | | |
| guidelines/rust.md | | |

## Phase 2 — Code Compliance Findings
| Area | Status | Findings |
|------|--------|---------|
| Testing | | |
| Observability | | |
| Security | | |
| Dependencies | | |
| Branching & Commits | | |
| API Design | | |

## Migration Command

Run this to generate a step-by-step migration plan:

\`\`\`bash
claude "Read .claude/audit-report.md. Write a numbered migration plan in two sections:
1) Setup fixes — for every ✗ or ~ item in Phase 1, the exact change needed.
2) Code compliance fixes — for every ✗ or ~ item in Phase 2, the exact change needed.
Show the full plan and ask me to confirm before making any changes."
\`\`\`
```

After writing the file, print:

> **Audit complete — report saved to `.claude/audit-report.md`**
>
> Run the migration command at the bottom of the report to generate a
> step-by-step plan with confirmation before any changes are made.
