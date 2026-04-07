# Architecture Decision Records (ADRs)

An ADR documents a significant architectural decision: what was decided, why, and what the trade-offs are. The goal is to make future engineers (including yourself in 6 months) understand the *why*, not just the *what*.

## When to Write an ADR

Write an ADR when:
- The decision is hard to reverse (database choice, auth approach, event vs REST)
- The decision affects more than one team or service
- Reasonable engineers could disagree on the right choice
- You find yourself explaining the same decision in multiple PR reviews
- The decision has significant security, performance, or cost implications

Do NOT write an ADR for:
- Library version bumps
- Code style preferences (covered by guidelines)
- Reversible implementation details

## Location & Numbering

Store in: `docs/adr/<NNNN>-<short-title>.md`

Number sequentially starting from `0001`. Never reuse or delete numbers — if a decision is superseded, mark the old ADR as superseded and write a new one.

Examples:
- `docs/adr/0001-use-adrs-for-architectural-decisions.md`
- `docs/adr/0002-use-postgresql-as-primary-database.md`
- `docs/adr/0003-use-jwt-for-api-authentication.md`

## ADR Template

```markdown
# <NNNN>. <Title>

**Date:** YYYY-MM-DD
**Status:** Proposed | Accepted | Deprecated | Superseded by [NNNN](./NNNN-title.md)
**Deciders:** @person1, @person2

## Context

What is the situation that forced this decision? What constraints exist?
Include: team size, traffic volume, existing systems, time pressure, non-functional requirements.

## Decision

What was decided? State it clearly in one paragraph.

## Alternatives Considered

| Option | Pros | Cons | Reason rejected |
|--------|------|------|-----------------|
| Option A | ... | ... | ... |
| Option B | ... | ... | ... |

## Consequences

**Positive:**
- ...

**Negative / trade-offs:**
- ...

**Risks:**
- ...

## Follow-up Actions

- [ ] Action item with owner @person
```

## Review Process

- ADR is written as part of the design phase, before implementation
- Reviewed in the design PR (not the implementation PR)
- Must be approved by at least one senior engineer
- Link the ADR from the implementation PR description
