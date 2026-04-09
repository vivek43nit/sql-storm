# Relation Refresh 1-Second Buffer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the millisecond-boundary race in `RelationRowDbLoader` by querying only rows with `updated_at <= NOW() - 1 second`, guaranteeing no in-flight writes are missed.

**Architecture:** Both `SQL_MAX_UPDATED_AT` and `SQL_LOAD_RELATIONS` receive a single `cutoff` timestamp computed once per `load()`/`refresh()` invocation. Using the same cutoff for both queries ensures the MAX check and the row fetch are consistent with each other. Rows written within the last second are invisible until the next poll, adding at most `REFRESH_BUFFER_SECONDS` of extra detection latency — acceptable vs. silently losing a write.

**Tech Stack:** Java 17, HikariCP, `java.sql.Timestamp`, `java.time.Instant`, JUnit 5, Testcontainers MariaDB.

---

## The Race (background for implementors)

```
Time T:  SELECT MAX(updated_at) FROM relation_mapping  → returns T
         (concurrent write begins at T, not yet committed)
         SELECT * FROM relation_mapping WHERE is_active=1  → misses the write

Time T+poll: SELECT MAX(updated_at) → still T (write committed at T)
             T == lastMaxUpdatedAt → "no change" → write is LOST forever
```

**Fix:** both queries add `WHERE updated_at <= ?` with `cutoff = NOW() - 1s`. Any write at time T is invisible until `T + 1s`, by which point it is fully committed and consistently visible in both queries.

---

## File Map

```
backend/src/main/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoader.java
    — add REFRESH_BUFFER_SECONDS constant
    — change SQL_MAX_UPDATED_AT and SQL_LOAD_RELATIONS to use cutoff parameter
    — update load() and refresh() to compute cutoff once
    — update fetchMaxUpdatedAt(Timestamp) and fetchAndAssemble(Timestamp) signatures

backend/src/test/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoaderMariaDbTest.java
    — add insertNow() helper (current timestamp, for buffer boundary test)
    — change insert() to use NOW() - INTERVAL 2 SECOND (safely outside buffer)
    — update refresh_afterSoftDelete test to add a second sleep after UPDATE
    — add new test: refresh_rowInsertedWithinBuffer_notVisibleUntilBufferExpires

SPEC.md
    — update Known Limitations: millisecond race is now fixed
```

---

## Task 1: Write the failing buffer boundary test

This test documents the expected behaviour and will FAIL before the fix (row is immediately visible without the buffer).

**Files:**
- Modify: `backend/src/test/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoaderMariaDbTest.java`

- [ ] **Step 1: Add `insertNow()` helper after the existing `insert()` method (line 102)**

```java
/** Inserts a row with updated_at = NOW() — within the 1-second buffer. */
private void insertNow(String db, String tbl, String col,
                       String refDb, String refTbl, String refCol,
                       String condJson, boolean active) throws Exception {
  try (Connection conn = DriverManager.getConnection(
      MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
       Statement st = conn.createStatement()) {
    String cond = condJson == null ? "NULL" : "'" + condJson + "'";
    st.execute(String.format(
        "INSERT INTO %s (database_name,table_name,column_name," +
        "ref_database_name,ref_table_name,ref_column_name,conditions_json,is_active) " +
        "VALUES ('%s','%s','%s','%s','%s','%s',%s,%d)",
        TABLE, db, tbl, col, refDb, refTbl, refCol, cond, active ? 1 : 0));
  }
}
```

- [ ] **Step 2: Add the buffer boundary test at the end of the test class (before the closing `}`)**

```java
// ── Buffer boundary ───────────────────────────────────────────────────────

@Test
void refresh_rowInsertedWithinBuffer_notVisibleUntilBufferExpires() throws Exception {
  // Establish baseline with a settled (past) row so lastMaxUpdatedAt > 0
  insert("db1", "orders", "user_id", "db1", "users", "id", null, true);
  loader.load();

  // Insert a row with current timestamp — within the 1-second buffer
  insertNow("db1", "payments", "order_id", "db1", "orders", "id", null, true);

  AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
  loader.setChangeListener(received::set);

  // Immediately refresh — new row must NOT be visible (within buffer)
  loader.refresh();
  assertThat(received.get())
      .as("row inserted within 1-second buffer must not trigger a reload")
      .isNull();

  // Wait for buffer to expire
  Thread.sleep(1200);

  // Refresh again — row must now be visible
  loader.refresh();
  assertThat(received.get())
      .as("row must be visible after buffer expires")
      .isNotNull();
  assertThat(received.get().getDatabases().get("db1").getRelations()).hasSize(2);
}
```

- [ ] **Step 3: Run the new test to confirm it fails (row IS visible immediately before the fix)**

```bash
cd backend
mvn -B test -Pintegration-tests \
  -Dtest="RelationRowDbLoaderMariaDbTest#refresh_rowInsertedWithinBuffer_notVisibleUntilBufferExpires" \
  --no-transfer-progress
```

Expected: **FAIL** — `received.get()` is not null (listener IS called immediately, no buffer exists yet).

---

## Task 2: Implement the buffer fix in RelationRowDbLoader

**Files:**
- Modify: `backend/src/main/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoader.java`

- [ ] **Step 4: Add `REFRESH_BUFFER_SECONDS` constant and update the two SQL constants (lines 56–64)**

Replace:
```java
private static final String SQL_MAX_UPDATED_AT =
        "SELECT MAX(updated_at) FROM %s";

private static final String SQL_LOAD_RELATIONS =
        "SELECT database_name, table_name, column_name, " +
        "       ref_database_name, ref_table_name, ref_column_name, conditions_json " +
        "FROM %s " +
        "WHERE is_active = 1 " +
        "ORDER BY database_name";
```

With:
```java
/**
 * Rows written within this window are excluded from change detection and loading.
 * Prevents the millisecond-boundary race where a write committed after our MAX query
 * but within the same timestamp second would be silently missed.
 */
static final int REFRESH_BUFFER_SECONDS = 1;

private static final String SQL_MAX_UPDATED_AT =
        "SELECT MAX(updated_at) FROM %s WHERE updated_at <= ?";

private static final String SQL_LOAD_RELATIONS =
        "SELECT database_name, table_name, column_name, " +
        "       ref_database_name, ref_table_name, ref_column_name, conditions_json " +
        "FROM %s " +
        "WHERE is_active = 1 AND updated_at <= ? " +
        "ORDER BY database_name";
```

- [ ] **Step 5: Update `load()` to compute cutoff and pass it to both helpers**

Replace:
```java
@Override
public CustomRelationConfig load() throws ConfigLoadException {
    long maxTs = fetchMaxUpdatedAt();
    CustomRelationConfig config = fetchAndAssemble();
    cachedConfig.set(config);
    lastMaxUpdatedAt.set(maxTs);
    log.info("Loaded {} relation(s) from table '{}'",
            config.getDatabases().values().stream()
                  .mapToInt(db -> db.getRelations().size()).sum(),
            table);
    return config;
}
```

With:
```java
@Override
public CustomRelationConfig load() throws ConfigLoadException {
    Timestamp cutoff = Timestamp.from(Instant.now().minusSeconds(REFRESH_BUFFER_SECONDS));
    long maxTs = fetchMaxUpdatedAt(cutoff);
    CustomRelationConfig config = fetchAndAssemble(cutoff);
    cachedConfig.set(config);
    lastMaxUpdatedAt.set(maxTs);
    log.info("Loaded {} relation(s) from table '{}'",
            config.getDatabases().values().stream()
                  .mapToInt(db -> db.getRelations().size()).sum(),
            table);
    return config;
}
```

- [ ] **Step 6: Update `refresh()` to compute cutoff and pass it to both helpers**

Replace:
```java
@Override
public void refresh() {
    try {
        long newMaxTs = fetchMaxUpdatedAt();
        if (newMaxTs <= lastMaxUpdatedAt.get()) {
            return; // no change
        }
        CustomRelationConfig newConfig = fetchAndAssemble();
        cachedConfig.set(newConfig);
        lastMaxUpdatedAt.set(newMaxTs);
        log.info("Relation mapping change detected in '{}' — reloading", table);

        Consumer<CustomRelationConfig> listener = changeListener;
        if (listener != null) {
            listener.accept(newConfig);
        }
        publishToRedis();
    } catch (Exception e) {
        log.warn("RelationRowDbLoader refresh from '{}' failed — retaining previous config: {}",
                table, e.getMessage());
    }
}
```

With:
```java
@Override
public void refresh() {
    try {
        Timestamp cutoff = Timestamp.from(Instant.now().minusSeconds(REFRESH_BUFFER_SECONDS));
        long newMaxTs = fetchMaxUpdatedAt(cutoff);
        if (newMaxTs <= lastMaxUpdatedAt.get()) {
            return; // no change
        }
        CustomRelationConfig newConfig = fetchAndAssemble(cutoff);
        cachedConfig.set(newConfig);
        lastMaxUpdatedAt.set(newMaxTs);
        log.info("Relation mapping change detected in '{}' — reloading", table);

        Consumer<CustomRelationConfig> listener = changeListener;
        if (listener != null) {
            listener.accept(newConfig);
        }
        publishToRedis();
    } catch (Exception e) {
        log.warn("RelationRowDbLoader refresh from '{}' failed — retaining previous config: {}",
                table, e.getMessage());
    }
}
```

- [ ] **Step 7: Update `fetchMaxUpdatedAt` to accept and bind the cutoff parameter**

Replace:
```java
private long fetchMaxUpdatedAt() throws ConfigLoadException {
    String sql = String.format(SQL_MAX_UPDATED_AT, table);
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
            Timestamp ts = rs.getTimestamp(1);
            return ts != null ? ts.getTime() : 0L;
        }
        return 0L;
    } catch (Exception e) {
        throw new ConfigLoadException("Failed to query MAX(updated_at) from '" + table + "'", e);
    }
}
```

With:
```java
private long fetchMaxUpdatedAt(Timestamp cutoff) throws ConfigLoadException {
    String sql = String.format(SQL_MAX_UPDATED_AT, table);
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setTimestamp(1, cutoff);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp(1);
                return ts != null ? ts.getTime() : 0L;
            }
            return 0L;
        }
    } catch (Exception e) {
        throw new ConfigLoadException("Failed to query MAX(updated_at) from '" + table + "'", e);
    }
}
```

- [ ] **Step 8: Update `fetchAndAssemble` to accept and bind the cutoff parameter**

Replace:
```java
private CustomRelationConfig fetchAndAssemble() throws ConfigLoadException {
    String sql = String.format(SQL_LOAD_RELATIONS, table);
    Map<String, List<ReferenceDTO>> byDatabase = new HashMap<>();

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
```

With:
```java
private CustomRelationConfig fetchAndAssemble(Timestamp cutoff) throws ConfigLoadException {
    String sql = String.format(SQL_LOAD_RELATIONS, table);
    Map<String, List<ReferenceDTO>> byDatabase = new HashMap<>();

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setTimestamp(1, cutoff);
        try (ResultSet rs = ps.executeQuery()) {
```

Close the inner try-with-resources around the ResultSet iteration. The rest of the method body (the `while (rs.next())` loop and the assembly block) is unchanged — just move the closing `}` to close the `try (ResultSet rs ...)` block, then close the outer try, then the catch. Full method:

```java
private CustomRelationConfig fetchAndAssemble(Timestamp cutoff) throws ConfigLoadException {
    String sql = String.format(SQL_LOAD_RELATIONS, table);
    Map<String, List<ReferenceDTO>> byDatabase = new HashMap<>();

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setTimestamp(1, cutoff);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String dbName       = rs.getString("database_name");
                String tableName    = rs.getString("table_name");
                String columnName   = rs.getString("column_name");
                String refDbName    = rs.getString("ref_database_name");
                String refTableName = rs.getString("ref_table_name");
                String refColName   = rs.getString("ref_column_name");
                String condJson     = rs.getString("conditions_json");

                ReferenceDTO ref = new ReferenceDTO();
                ref.setDatabaseName(dbName);
                ref.setTableName(tableName);
                ref.setColumnName(columnName);
                ref.setReferenceDatabaseName(refDbName);
                ref.setReferenceTableName(refTableName);
                ref.setReferenceColumnName(refColName);
                ref.setSource(ReferenceDTO.Source.CUSTOM);
                if (condJson != null && !condJson.isBlank()) {
                    ref.setConditions(new JSONObject(condJson));
                }

                byDatabase.computeIfAbsent(dbName, k -> new ArrayList<>()).add(ref);
            }
        }
    } catch (Exception e) {
        throw new ConfigLoadException("Failed to load relations from '" + table + "'", e);
    }

    Map<String, DatabaseConfig> databases = new HashMap<>();
    for (Map.Entry<String, List<ReferenceDTO>> entry : byDatabase.entrySet()) {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setRelations(entry.getValue());
        dbConfig.setJointTables(Collections.emptyMap());
        dbConfig.setAutoResolve(Collections.emptyMap());
        databases.put(entry.getKey(), dbConfig);
    }

    return new CustomRelationConfig(databases);
}
```

- [ ] **Step 9: Compile to catch any errors**

```bash
cd backend
mvn -B compile --no-transfer-progress
```

Expected: `BUILD SUCCESS` with no errors.

---

## Task 3: Fix broken existing tests and verify all pass

Existing tests use `insert()` which writes `updated_at = NOW()` (default). With the buffer, `load()` now ignores these rows. Fix: make `insert()` write with `NOW() - INTERVAL 2 SECOND`, safely outside the buffer.

**Files:**
- Modify: `backend/src/test/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoaderMariaDbTest.java`

- [ ] **Step 10: Update `insert()` to use a past timestamp**

Replace the `st.execute(String.format(...))` inside `insert()`:

```java
private void insert(String db, String tbl, String col,
                    String refDb, String refTbl, String refCol,
                    String condJson, boolean active) throws Exception {
  try (Connection conn = DriverManager.getConnection(
      MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
       Statement st = conn.createStatement()) {
    String cond = condJson == null ? "NULL" : "'" + condJson + "'";
    // Use a timestamp 2 seconds in the past — safely outside the 1-second refresh buffer.
    st.execute(String.format(
        "INSERT INTO %s (database_name,table_name,column_name," +
        "ref_database_name,ref_table_name,ref_column_name,conditions_json,is_active,updated_at) " +
        "VALUES ('%s','%s','%s','%s','%s','%s',%s,%d, NOW() - INTERVAL 2 SECOND)",
        TABLE, db, tbl, col, refDb, refTbl, refCol, cond, active ? 1 : 0));
  }
}
```

- [ ] **Step 11: Update `refresh_afterSoftDelete_excludesInactiveRow` to sleep after the UPDATE**

The test already has `Thread.sleep(1100)` before the UPDATE to advance MariaDB's second boundary. With the buffer, the update itself also needs to be outside the buffer before `refresh()` is called. Add a second `Thread.sleep(1100)` after the UPDATE:

Replace in `refresh_afterSoftDelete_excludesInactiveRow`:
```java
    Thread.sleep(1100); // must cross DATETIME precision boundary BEFORE the update
    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      // MariaDB ON UPDATE CURRENT_TIMESTAMP bumps updated_at automatically
      st.execute("UPDATE " + TABLE + " SET is_active = 0 WHERE table_name = 'payments'");
    }

    AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
    loader.setChangeListener(received::set);
    loader.refresh();
```

With:
```java
    Thread.sleep(1100); // cross DATETIME precision boundary before the update
    try (Connection conn = DriverManager.getConnection(
        MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword());
         Statement st = conn.createStatement()) {
      // MariaDB ON UPDATE CURRENT_TIMESTAMP bumps updated_at automatically
      st.execute("UPDATE " + TABLE + " SET is_active = 0 WHERE table_name = 'payments'");
    }
    Thread.sleep(1100); // wait for the 1-second refresh buffer to expire after the UPDATE

    AtomicReference<CustomRelationConfig> received = new AtomicReference<>();
    loader.setChangeListener(received::set);
    loader.refresh();
```

- [ ] **Step 12: Run all integration tests and verify they all pass**

```bash
cd backend
mvn -B test -Pintegration-tests \
  -Dtest="RelationRowDbLoaderMariaDbTest" \
  --no-transfer-progress
```

Expected output:
```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

(7 original tests + 1 new buffer boundary test)

- [ ] **Step 13: Run the full integration test suite to check for regressions**

```bash
cd backend
mvn -B verify -Pintegration-tests --no-transfer-progress
```

Expected: `BUILD SUCCESS`, all integration tests pass.

---

## Task 4: Update SPEC.md Known Limitations

**Files:**
- Modify: `SPEC.md`

- [ ] **Step 14: Mark the millisecond race as fixed in Known Limitations**

In `SPEC.md` under `### Relation Refresh (RelationRowDbLoader)`, replace:

```markdown
| Millisecond-boundary race on change detection | `refresh()` queries `MAX(updated_at)` and then fetches all active rows. If a row is written in the same millisecond as the `MAX` query, the write may complete *after* the SELECT and be silently missed until the next poll cycle. **Mitigation:** add a 1-second buffer — query `MAX(updated_at) WHERE updated_at < NOW() - INTERVAL 1 SECOND` so only fully-settled writes are picked up. This adds at most 1s of extra latency to detection but eliminates the race. |
```

With:

```markdown
| Detection latency increased by buffer | `refresh()` and `load()` query only rows with `updated_at <= NOW() - 1s`. Rows written within the last second are invisible until the next poll. This eliminates the millisecond-boundary race at the cost of at most `REFRESH_BUFFER_SECONDS` (1s) of additional detection latency. Maximum end-to-end latency = `refresh-interval-seconds + 1s`. |
```

- [ ] **Step 15: Commit everything**

```bash
git add \
  backend/src/main/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoader.java \
  backend/src/test/java/com/vivek/sqlstorm/config/loader/RelationRowDbLoaderMariaDbTest.java \
  SPEC.md
git commit -m "fix(config): add 1-second buffer to relation refresh change detection

Both SQL_MAX_UPDATED_AT and SQL_LOAD_RELATIONS now filter with
'updated_at <= cutoff' where cutoff = NOW() - REFRESH_BUFFER_SECONDS (1s).
The cutoff is computed once per load()/refresh() call and passed to both
helpers, guaranteeing they operate on the same consistent window.

Without the buffer, a write committed after the MAX query but within the
same millisecond was permanently missed — MAX on the next poll returned
the same value so change detection skipped it.

Trade-off: detection latency increases by at most 1 second.
Maximum end-to-end propagation = refresh-interval-seconds + 1s.

Tests: updated insert() to use NOW()-2s (outside buffer); added sleep
after soft-delete UPDATE; added new buffer boundary test."
```

---

## Self-Review

**Spec coverage:**
- ✅ Both SQL queries use the cutoff parameter
- ✅ Cutoff computed once per invocation — both queries see the same window
- ✅ `REFRESH_BUFFER_SECONDS` is a named constant (not magic number)
- ✅ Existing tests updated so they still test their original intent
- ✅ New test specifically validates the buffer boundary in both directions (not visible within, visible after)
- ✅ SPEC.md updated

**Placeholder scan:** None found.

**Type consistency:** `Timestamp cutoff` flows from `load()`/`refresh()` → `fetchMaxUpdatedAt(Timestamp)` → `fetchAndAssemble(Timestamp)` consistently throughout.
