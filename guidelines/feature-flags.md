# Feature Flags & Safe Deployments

Deploy code independently of releasing features. This is the single most effective way to reduce deployment risk.

## When to Use a Feature Flag

| Situation | Use flag? |
|-----------|-----------|
| Risky feature going to 100% of users at once | ✅ Yes |
| Feature that needs A/B testing | ✅ Yes |
| Feature touching auth, payments, or DB schema | ✅ Yes |
| Large refactor that can't be done atomically | ✅ Yes |
| Simple bug fix with clear rollback via revert | ❌ No |
| UI copy change | ❌ No |
| Internal tool or admin-only feature | ❌ Optional |

## Flag Types

| Type | Description | Example |
|------|-------------|---------|
| Release flag | Gates a new feature, removed after full rollout | `new-checkout-flow` |
| Experiment flag | A/B test with metrics | `checkout-cta-text-v2` |
| Ops flag | Kill switch for a feature under load | `disable-recommendations` |
| Permission flag | Feature for specific users/plans | `enterprise-sso` |

## Rules

1. **Default OFF** — new flags must default to `false`/disabled
2. **Short-lived** — release flags must be removed within 30 days of full rollout
3. **One owner** — every flag has a named owner responsible for cleanup
4. **Review stale flags** — sprint retro checklist includes "any flags older than 30 days?"
5. **Never nest flags** — `if flagA && flagB` is a maintenance nightmare

## Gradual Rollout Pattern

```
1%  → monitor error rate and latency for 1h
10% → monitor for 24h
50% → monitor for 24h
100% → remove flag in next sprint
```

## Tooling Options

| Tool | Self-hosted | Cost | Best for |
|------|-------------|------|----------|
| **Unleash** | ✅ Yes | Free (self-hosted) | Teams wanting control |
| **LaunchDarkly** | ❌ No | Paid | Enterprise, complex targeting |
| **Flagsmith** | ✅ Yes | Free tier | Simpler setups |
| **Env vars** | ✅ Yes | Free | Simple on/off per environment |

For simple on/off per environment, an env var is sufficient:

```python
ENABLE_NEW_CHECKOUT = os.getenv("ENABLE_NEW_CHECKOUT", "false").lower() == "true"
```

## Cleanup Checklist (before removing a flag)

- [ ] Flag is at 100% for all environments
- [ ] No errors or anomalies in the past 7 days at 100%
- [ ] Remove flag from code AND flag management tool
- [ ] Remove any fallback/old code paths
- [ ] PR description links to the original flag creation PR
