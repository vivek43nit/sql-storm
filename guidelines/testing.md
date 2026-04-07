# Testing Guidelines — The Testing Pyramid

Unit tests alone are not enough. Production failures happen at integration points, not inside isolated functions.

## The Pyramid

```
        /\
       /E2E\        10% — Critical user journeys only
      /------\
     /  Integ  \    20% — Service boundaries, real DB/cache
    /------------\
   /     Unit     \  70% — Business logic, fast, isolated
  /----------------\
```

**Rule:** If your test suite is >90% unit tests, you're testing implementation, not behaviour.

## Unit Tests (70%)

- Scope: a single function, method, or class in isolation
- Speed: < 10ms each, no I/O
- Mocking: mock at system boundaries only (DB, HTTP, filesystem, clock)
- What to test: business logic, edge cases, error paths, validation
- What NOT to unit test: framework glue code, simple getters/setters, generated code

```python
# Good unit test — tests logic, mocks boundary
def test_order_total_applies_discount_when_customer_is_vip():
    customer = Customer(tier="vip")
    items = [Item(price=100), Item(price=50)]
    total = calculate_order_total(items, customer)
    assert total == 135.0  # 10% VIP discount

# Bad — testing the ORM, not your logic
def test_save_user():
    user = User(email="a@b.com")
    db.save(user)
    assert db.get(user.id) is not None
```

## Integration Tests (20%)

- Scope: one service + its real infrastructure (DB, cache, message queue)
- Speed: < 2s each, uses real connections
- Use a test DB that is reset between test suites (not between each test)
- Test: data persistence, query correctness, transaction behaviour, migration correctness
- Do NOT mock the database in integration tests

```python
# Good integration test — real DB, tests actual persistence
def test_create_user_persists_to_database(db_session):
    user_service.create(db_session, email="a@b.com", name="Alice")
    result = db_session.query(User).filter_by(email="a@b.com").first()
    assert result is not None
    assert result.name == "Alice"
```

## End-to-End Tests (10%)

- Scope: full system through the public API or UI
- Speed: can be slow (seconds), run in CI on PR to main only
- Cover: critical user journeys ONLY (login → checkout → confirmation)
- Do NOT duplicate unit/integration coverage in E2E
- Max 20 E2E tests for a typical service — if you have more, convert to integration tests

## Contract Tests (for microservices)

When service A calls service B, both must agree on the contract.

- Use Pact or similar consumer-driven contract testing
- Consumer (service A) defines the contract
- Provider (service B) verifies it in CI
- Prevents "it works in isolation but breaks when deployed" failures

## Performance Tests

- Run before every production release on staging
- Baseline: capture p50/p95/p99 latency at expected load
- Gate: p99 must not exceed 2x the baseline from the previous release
- Tools: k6, Locust, Gatling, JMeter
- Minimum scenario: 10 min ramp-up → 30 min sustained load at peak → 5 min ramp-down

## Test Data Management

- Never use production data in tests
- Use factories/builders to create test data — not hand-crafted fixtures
- Reset state between test suites (not between tests — too slow)
- Seed scripts for E2E environments must be idempotent

## CI Test Strategy

| Stage | Tests run | Gate |
|-------|-----------|------|
| Pre-commit hook | Unit tests for changed files | Must pass |
| PR | Unit + Integration + Contract | Must pass, 80% coverage |
| Merge to main | All above + E2E | Must pass |
| Pre-release | All above + Performance | p99 ≤ 2x baseline |
