# Incident Management

How you respond to incidents defines your team's reliability culture. Clear process reduces MTTR (Mean Time To Recover).

## Severity Levels

| Severity | Definition | Response time | Examples |
|----------|------------|--------------|---------|
| **P0** | Total outage — service is down for all users | Immediate (< 5 min) | Site down, all payments failing, data loss |
| **P1** | Major degradation — core feature broken for many users | < 30 min | Login broken, checkout errors > 5%, API p99 > 10s |
| **P2** | Minor degradation — feature broken for some users | < 4h | Slow search, broken filter, partial API errors |
| **P3** | Cosmetic / low impact | Next business day | UI misalignment, typo, non-critical feature broken |

## Incident Response Process

1. **Detect** — alert fires or user report received
2. **Acknowledge** — on-call acknowledges within SLA
3. **Communicate** — post in incident channel: `🔴 [P0] Checkout service down — investigating`
4. **Investigate** — use runbook if available; check logs, metrics, recent deploys
5. **Mitigate** — restore service (rollback, feature flag off, scale up) — imperfect is fine
6. **Resolve** — confirm service restored; update status page
7. **Post-mortem** — required for P0/P1 within 48h of resolution

## Communication Template

Post in incident channel at each stage:

```
🔴 INCIDENT [P0] - [Short title]
Status: Investigating | Mitigating | Resolved
Impact: [Who is affected and how]
Start time: [HH:MM UTC]
On-call: @person
Next update in: 15 minutes
```

## On-Call Handoff Checklist

When handing off an ongoing incident:
- [ ] Current status and what has been tried
- [ ] All relevant runbook / docs links
- [ ] Active monitoring dashboards
- [ ] Last known-good deployment SHA
- [ ] Any temporary mitigations in place (feature flags, rate limits)
- [ ] Stakeholders who have been notified

## Post-Mortem Requirements

- Required for: all P0 and P1 incidents
- Due: within 48 hours of resolution
- Format: see `docs/templates/postmortem.md`
- Blameless — focus on systems and processes, not individuals
- Action items must have owners and due dates

## Runbooks

Every critical service path must have a runbook. Store in `docs/runbooks/`.
Format: see `docs/templates/runbook.md`
