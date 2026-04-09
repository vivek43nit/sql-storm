# claude-code-kit Audit Report
Generated: 2026-04-07

## Summary
| Phase | ✗ Missing / Failing | ~ Outdated / Issues | ✓ Ok |
|-------|---------------------|---------------------|------|
| Setup | 0 | 1 (fixed) | 23 |
| Code compliance | 1 | 3 | 2 |

## Phase 1 — Setup Findings
| Item | Status | Notes |
|------|--------|-------|
| .claude/hooks/detect-languages.sh | ✓ | File present |
| .claude/hooks/security-scan.sh | ✓ | File present |
| .claude/settings.json — SessionStart hook | ✓ | Was `UserPromptSubmit` — fixed to `SessionStart` |
| .claude/settings.json — PreToolUse hook | ✓ | Correctly configured with `Write\|Edit` matcher |
| CLAUDE.md — @guidelines/active.md import | ✓ | Present on line 8 |
| .gitignore — guidelines/active.md | ✓ | Present on line 7 |
| guidelines/base.md | ✓ | Matches remote |
| guidelines/observability.md | ✓ | Matches remote |
| guidelines/testing.md | ✓ | Matches remote |
| guidelines/branching.md | ✓ | Matches remote |
| guidelines/dependencies.md | ✓ | Matches remote |
| guidelines/adr.md | ✓ | Matches remote |
| guidelines/api-design.md | ✓ | Matches remote |
| guidelines/database.md | ✓ | Matches remote |
| guidelines/feature-flags.md | ✓ | Matches remote |
| guidelines/incidents.md | ✓ | Matches remote |
| guidelines/accessibility.md | ✓ | Matches remote |
| guidelines/python.md | ✓ | Matches remote |
| guidelines/typescript.md | ✓ | Matches remote |
| guidelines/javascript.md | ✓ | Matches remote |
| guidelines/go.md | ✓ | Matches remote |
| guidelines/java.md | ✓ | Matches remote |
| guidelines/kotlin.md | ✓ | Matches remote |
| guidelines/rust.md | ✓ | Matches remote |

## Phase 2 — Code Compliance Findings
| Area | Status | Findings |
|------|--------|---------|
| Testing | ~ | 4 Java test files + 3 frontend test files exist. Unit tests (`CustomRelationConfigTest`, `UserServiceTest`) and integration tests (`AuthControllerTest`, `RowMutationControllerTest`) are present with correct naming convention (`method_whenCondition_outcome`). Mocking is used appropriately at the `DatabaseManager` boundary. However: no E2E tests, no JaCoCo coverage threshold enforced in CI, and test count is low relative to codebase size — 80% coverage requirement is likely unmet. |
| Observability | ~ | Structured JSON logging is correctly configured for prod via `logback-spring.xml` (LogstashEncoder). `service` field and `requestId` MDC field are present on every log line. Spring Actuator `probes.enabled: true` provides `/actuator/health/liveness` and `/actuator/health/readiness`. Missing: no `trace_id` / `span_id` fields on log lines, and no OpenTelemetry tracing instrumentation (no `opentelemetry-java` dependency wired). |
| Security | ✗ | **SQL injection** in `QueryController.java:150–151`: user-supplied `value` is directly interpolated into a SQL string via `String.format("select * from %s where %s='%s'", ..., value)` — must use a `PreparedStatement` with `?` placeholder instead. Secondary issue: `application.yml:74` sets `admin-password: ${FKBLITZ_ADMIN_PASSWORD:changeme}` — the `changeme` fallback is a weak default that may reach production if the env var is not set. `DbConfigLoader.java:105` concatenates table/column names from application config (lower risk, not user input, but still violates the parameterised-queries rule). |
| Dependencies | ✓ | `renovate.json` is present and well-configured (patch auto-merge, minor/major require review, Spring ecosystem grouped). `package-lock.json` committed. Maven `pom.xml` committed. |
| Branching & Commits | ~ | Recent commits do not follow conventional commit format. Examples: "Update DOCUMENTATION and SPEC for Spring Boot + React architecture", "Add open source project scaffolding", "Add demo GIF, SEO-optimised README…". These should be `docs(spec):`, `chore:`, `docs(readme):`, etc. Branch names also deviate: `docs/update-documentation-and-spec` should be `docs/<ticket>-<short-desc>` per guidelines. |
| API Design | ✓ | Liveness and readiness endpoints available via Spring Actuator probes. `GlobalExceptionHandler` returns a consistent `{"error": {"code", "message", "requestId"}}` shape. SpringDoc/Swagger configured at `/v3/api-docs`. `RateLimitFilter` implements per-user rate limiting on mutation endpoints. |

## Migration Command

Run this to generate a step-by-step migration plan:

```bash
claude "Read .claude/audit-report.md. Write a numbered migration plan in two sections:
1) Setup fixes — for every ✗ or ~ item in Phase 1, the exact change needed.
2) Code compliance fixes — for every ✗ or ~ item in Phase 2, the exact change needed.
Show the full plan and ask me to confirm before making any changes."
```
