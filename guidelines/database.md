# Database Guidelines

One bad migration can take down production. One missing index can collapse under load. These rules exist because someone learned them the hard way.

## Migration Safety (Zero-Downtime)

**The cardinal rule:** Every migration must be deployable without stopping the application.

**Safe operations (can do any time):**
- Add a nullable column
- Add an index (`CONCURRENTLY` in Postgres)
- Add a new table
- Add a new foreign key to a new column

**Dangerous operations (require two-step deploy):**

| Operation | Safe approach |
|-----------|--------------|
| Remove a column | Step 1: deploy code that ignores the column. Step 2 (next release): drop the column. |
| Rename a column | Step 1: add new column + backfill + dual-write. Step 2: migrate reads. Step 3: drop old column. |
| Change column type | Step 1: add new column. Step 2: backfill + dual-write. Step 3: swap. Step 4: drop old. |
| Add NOT NULL constraint | Step 1: add as nullable + backfill nulls. Step 2: add constraint with `NOT VALID`. Step 3: `VALIDATE CONSTRAINT` (non-blocking). |
| Add index | Always use `CREATE INDEX CONCURRENTLY` (Postgres). Never plain `CREATE INDEX` on live tables. |

**Before every migration:**
- [ ] Test on a copy of production data (row counts matter — 10M rows ≠ 10 rows)
- [ ] Estimate lock duration — anything > 1s needs a plan
- [ ] Write the rollback migration before writing the forward migration
- [ ] Review with a second engineer if the table has > 1M rows

## N+1 Query Prevention

N+1 kills production under load. Never load related records inside a loop.

```python
# BAD — N+1: 1 query for orders + N queries for users
orders = Order.objects.all()
for order in orders:
    print(order.user.email)  # separate query each time

# GOOD — 2 queries total
orders = Order.objects.select_related('user').all()
for order in orders:
    print(order.user.email)
```

**Rule:** Any code that accesses a relationship inside a loop must use eager loading / JOIN / batch fetch. Add this to your code review checklist.

## Connection Pooling

- Never open a raw database connection per request in a web handler
- Always use a connection pool (PgBouncer, HikariCP, SQLAlchemy pool, pgxpool)
- Pool size formula: `(num_cores * 2) + num_disk_spindles` — start with `10` if unsure
- Set `connection_timeout` and `pool_timeout` — never let a request hang forever waiting for a connection
- Monitor pool exhaustion — it presents as sudden latency spikes, not errors

## Query Guidelines

- Always add an index before adding a foreign key constraint
- Queries that appear in hot paths (>10 req/s) must have `EXPLAIN ANALYZE` reviewed
- Avoid `SELECT *` — select only columns you use
- Use pagination on all list queries — never return unbounded result sets
- Parameterised queries only. No string interpolation. Ever.

## Rollback Procedure

Every migration deployment must have a written rollback plan documented in the PR:

```
Rollback steps:
1. Run: `<tool> db downgrade <version>`
2. Verify: `<tool> db current` shows previous version
3. Check: application health endpoint returns 200
4. Confirm: no error spike in logs
```

If the migration is irreversible (e.g. data transformation), document the data recovery procedure instead.

## Per-Language Tooling

| Language | Migration tool | ORM / Query builder |
|----------|---------------|---------------------|
| Python | Alembic (SQLAlchemy) | SQLAlchemy, Django ORM |
| TypeScript | Prisma Migrate, TypeORM | Prisma, TypeORM, Drizzle |
| Go | `golang-migrate` | sqlc, GORM, sqlx |
| Java | Flyway or Liquibase | Hibernate, jOOQ |
| Kotlin | Flyway or Liquibase | Exposed, Hibernate |
| Rust | `sqlx` migrations | sqlx, Diesel |
