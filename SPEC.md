# FkBlitz — Technical Specification

## 1. Purpose

FkBlitz is a self-hosted, browser-based MySQL/MariaDB client that enables fast navigation through relational data by following foreign key relationships — both schema-defined and custom-defined. It is designed for engineers and data teams who need to trace rows across tables without writing JOINs.

---

## 2. Functional Requirements

### 2.1 Connection Management

- **FR-CM-01**: The system must support multiple database connections, each identified by a `GROUP` (environment) and `DB_NAME`.
- **FR-CM-02**: Connections must be pooled and reused across requests until they expire (`CONNECTION_EXPIRY_TIME`, default 3,600,000 ms).
- **FR-CM-03**: Expired or failed connections must be recreated automatically, up to `MAX_RETRY_COUNT` (default 10) times.
- **FR-CM-04**: Each connection must be independently flagged as `UPDATABLE` and/or `DELETABLE`.
- **FR-CM-05**: Each connection must support a configurable row limit for unindexed column scans (`NON_INDEXED_SEARCHABLE_ROW_LIMIT`).
- **FR-CM-06**: Connection configuration must be loadable from XML or JSON files.

### 2.2 Metadata Discovery

- **FR-MD-01**: The system must discover all tables via JDBC `DatabaseMetaData.getTables()`.
- **FR-MD-02**: For each table: column names, data types, nullability.
- **FR-MD-03**: Primary keys, indexes, and unique constraints per table.
- **FR-MD-04**: Foreign key relationships via `getImportedKeys()`; build a bidirectional graph (`referTo` / `referencedBy`).
- **FR-MD-05**: Metadata must be loaded lazily (on first access) and cached for the application lifetime.

### 2.3 Custom Relationships

- **FR-CR-01**: Users must be able to define relationships not in the database schema via `custom_mapping.json`.
- **FR-CR-02**: Custom relations must support optional row-level conditions.
- **FR-CR-03**: Custom relations must support cross-database references.
- **FR-CR-04**: Junction/mapping tables must be supported with types: `ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_MANY`.

### 2.4 Query Execution

- **FR-QE-01**: Users must be able to execute arbitrary SQL SELECT queries.
- **FR-QE-02**: Results must be returned as structured JSON (`ResultSetDTO`): columns, rows, PK, relation label.
- **FR-QE-03**: Result range (start row, end row) must be configurable per query.
- **FR-QE-04**: If `UPDATABLE=true`, users must be able to add and edit rows via the UI.
- **FR-QE-05**: If `DELETABLE=true`, users must be able to delete rows via the UI.

### 2.5 Relationship Navigation

- **FR-RN-01**: From any result row, clicking a FK value must navigate to the referenced row(s).
- **FR-RN-02**: From any result row, the user must be able to see all rows in other tables that reference it.
- **FR-RN-03**: Navigation must respect conditions defined in custom mappings.
- **FR-RN-04**: Many-to-many relationships through mapping tables must resolve to the final target rows.
- **FR-RN-05**: Trace must follow all FK relationships for a row and return results grouped by table.
- **FR-RN-06**: The number of related rows fetched must be configurable per request (`refRowLimit`, default 100).

### 2.6 Data Type Conversion

- **FR-DT-01**: Column values must be transformable by registered type handlers.
- **FR-DT-02**: Integer IP addresses must render as dotted IPv4.
- **FR-DT-03**: Epoch ms timestamps must render in short or long date format.
- **FR-DT-04**: Additional handlers must be registerable without modifying existing code.

### 2.7 Authentication & Security

- **FR-SEC-01**: All API endpoints must require an authenticated session.
- **FR-SEC-02**: Unauthenticated API requests must receive `401 JSON` — no HTML redirect.
- **FR-SEC-03**: Login and logout must be handled via Spring Security form login.
- **FR-SEC-04**: CRUD operations must be gated by per-connection `UPDATABLE`/`DELETABLE` flags server-side.

### 2.8 Utility Features

- **FR-UF-01**: A converter panel must support IP ↔ integer, date ↔ epoch ms conversions, and a live server clock.
- **FR-UF-02**: Admin pages must display known FK relations and suggest potential custom relations per database.
- **FR-UF-03**: Result columns must support per-column inline text filtering and clickable sort.

---

## 3. Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| NFR-01 | Each database connection must be backed by a HikariCP pool; concurrent requests must not race on pool acquisition. |
| NFR-02 | Metadata snapshot swap must be lock-free (`AtomicReference`); readers must never block writers. |
| NFR-03 | Metadata loading for one database must not block requests to other databases. |
| NFR-04 | The backend must run on Java 17+. |
| NFR-05 | The backend must start via `mvn spring-boot:run` or as a self-contained JAR. |
| NFR-06 | The frontend must build to a static bundle embeddable in the JAR. |
| NFR-07 | Connection credentials must never appear in application code; read from external config. |
| NFR-08 | The system must be deployable as a single Docker image via `docker compose up --build`. |
| NFR-09 | All nodes in a multi-replica deployment must converge to the same relation config within `refresh-interval-seconds` (without Redis) or sub-second (with Redis pub/sub). |
| NFR-10 | The system must expose Prometheus metrics at `/actuator/prometheus` including per-pool HikariCP metrics and application-level query counters. |
| NFR-11 | Minimum 80% JaCoCo line coverage on business-logic classes, enforced in CI. |

---

## 4. System Architecture

### 4.1 Component Diagram

```
┌───────────────────────────────────────────────────────────┐
│                     Web Browser                           │
│            React 18 SPA (Vite, TanStack Table)            │
└──────────────────────────┬────────────────────────────────┘
                           │ HTTP/JSON (session cookie)
┌──────────────────────────▼────────────────────────────────┐
│         Spring Boot 3  —  port 9044  —  /fkblitz          │
│                                                           │
│  ┌──────────────────────────────────────────────────┐    │
│  │        Spring Security + RBAC                    │    │
│  │  form login / OAuth2 / OIDC — ADMIN/RW/RO roles  │    │
│  │  rate limiting (Bucket4j), sensitive col masking │    │
│  └──────────────────────┬───────────────────────────┘    │
│                         │                                 │
│  ┌──────────┬───────────▼──────────┬──────────────────┐  │
│  │ MetaData │  ExecuteController   │ RowMutation      │  │
│  │Controller│  /api/execute        │ Controller       │  │
│  │/api/     │  /api/references     │ /api/row/add     │  │
│  │groups    │  /api/dereferences   │ /api/row/edit    │  │
│  │/databases│  /api/trace          │ /api/row (DEL)   │  │
│  │/tables   │                      │                  │  │
│  └──────────┴──────────┬───────────┴──────────────────┘  │
│                        │                                  │
│  ┌─────────────────────▼─────────────────────────────┐   │
│  │              DatabaseManager (Facade)             │   │
│  │  ┌──────────────────────┐  ┌────────────────────┐ │   │
│  │  │ DatabaseConnection   │  │ MetaDataManager    │ │   │
│  │  │ Manager (HikariCP    │  │ (schema + FK cache │ │   │
│  │  │  pool per db)        │  │  snapshotRef swap) │ │   │
│  │  └──────────┬───────────┘  └────────────────────┘ │   │
│  └─────────────┼──────────────────────────────────────┘  │
│                │                                          │
│  ┌─────────────▼──────────────────────────────────────┐  │
│  │          Config Loader Layer                       │  │
│  │  DbConfigLoader  RelationRowDbLoader  FileLoader   │  │
│  │  (connection cfg)  (relation_mapping  (XML/JSON)   │  │
│  │                     table, HikariCP)               │  │
│  └─────────────────────────┬──────────────────────────┘  │
└─────────────────────────────┼──────────────────────────────┘
                              │ JDBC (HikariCP pools)
              ┌───────────────▼──────────────┐
              │      MySQL / MariaDB          │
              └───────────────────────────────┘
                              │
              ┌───────────────▼──────────────┐
              │  Redis (optional)             │
              │  sessions + pub/sub           │
              │  fkblitz:config-changed       │
              └───────────────────────────────┘
```

### 4.2 Config Resolution

```
/etc/fkblitz/<file>.<ext>
  → ~/.fkblitz/<file>.<ext>
    → ~/<file>.<ext>
      → classpath:<file>.<ext>
```

Two config files:
- `DatabaseConnection` — connection credentials and settings
- `custom_mapping` — custom relationship definitions

Both support XML and JSON (extension determines parser).

---

## 5. Data Models

### 5.1 Connection Config

```
ConnectionConfig
  connection_expiry_time : long    (ms, default 3,600,000)
  max_retry_count        : int     (default 10)
  connections[]
    ConnectionDTO
      id                              : String
      group                           : String
      db_name                         : String
      driver_class_name               : String
      database_url                    : String
      user_name                       : String
      password                        : String
      updatable                       : boolean (default false)
      deletable                       : boolean (default false)
      non_indexed_searchable_row_limit: int
```

### 5.2 Metadata Model

```
DatabaseDTO
  group    : String
  dbName   : String
  tables   : Map<String, TableDTO>   (keyed by table name)
  loaded   : boolean

TableDTO
  tableName  : String
  remark     : String
  primaryKey : String
  columns    : LinkedHashMap<String, ColumnDTO>   (insertion order = schema order)

ColumnDTO
  name         : String
  dataType     : String
  size         : int
  nullable     : boolean
  indexed      : boolean
  primaryKey   : boolean
  unique       : boolean
  referTo      : List<ColumnPath>    (FK targets — this column points to)
  referencedBy : List<ColumnPath>    (reverse FKs — other columns point here)
```

### 5.3 Relationship Model

```
ReferenceDTO
  db         : String    source database
  table      : String    source table
  column     : String    source column
  refDb      : String    target database
  refTable   : String    target table
  refColumn  : String    target column
  conditions : Map<String, String>
  source     : enum { DB, CUSTOM, NEW }

ColumnPath
  database   : String
  table      : String
  column     : String
  conditions : Map<String, String>
  source     : Source
  getPathString() → "database.table.column"

MappingTableDto
  type        : enum { ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY }
  from        : String    (source column in junction table)
  to          : String    (target column in junction table)
  includeSelf : boolean
```

### 5.4 API Response — ResultSetDTO

```json
{
  "info":       "fkblitz-test.orders",
  "relation":   "self | referTo | referencedBy",
  "primaryKey": "id",
  "columns":    ["id", "customer_id", "status"],
  "rows":       [{"id":"1","customer_id":"1","status":"delivered"}]
}
```

---

## 6. Key Algorithms

### 6.1 Connection Acquisition

```
getConnection(group, db):
  key = (group, db)
  info = cache[key]
  if info exists AND connection valid AND NOT expired:
    return info.connection
  for attempt in 1..maxRetryCount:
    DriverManager.loginTimeout = 10s
    conn = DriverManager.getConnection(url, user, pass)
    if conn valid:
      cache[key] = ConnectionInfo(conn, now())
      return conn
  throw ConnectionDetailNotFound
```

### 6.2 Metadata Lazy Load

```
lazyLoadFromDb(group, db):
  conn = getConnection(group, db)
  for each table in DatabaseMetaData.getTables():
    dto = TableDTO(table)
    for each column in getColumns(table):
      dto.addColumn(ColumnDTO(column))
    for each index in getIndexInfo(table):
      dto.applyIndex(index)
    for each fk in getImportedKeys(table):
      sourceCol.referTo.add(ColumnPath(fk.target))
      targetCol.referencedBy.add(ColumnPath(fk.source))
  merge(customRelationConfig)
  dbDTO.loaded = true
```

### 6.3 FK Navigation Query Generation

```
getReferences(group, db, table, column, rowData, refRowLimit):
  col = metaData.getColumn(table, column)
  for each path in col.referTo:
    if conditions not satisfied by rowData: skip
    emit: SELECT * FROM {path.table} WHERE {path.column} = ? LIMIT {refRowLimit}

getDereferences(group, db, table, column, rowData, refRowLimit):
  col = metaData.getColumn(table, column)
  for each path in col.referencedBy:
    if conditions not satisfied by rowData: skip
    if path is mapping table:
      emit: SELECT t.* FROM {target} t
            JOIN {junction} j ON t.{to} = j.{to}
            WHERE j.{from} = ? LIMIT {refRowLimit}
    else:
      emit: SELECT * FROM {path.table} WHERE {path.column} = ?
            [AND conditions] LIMIT {refRowLimit}
```

---

## 7. API Reference

### Authentication

| Endpoint | Method | Auth required | Description |
|----------|--------|---------------|-------------|
| `/api/login` | POST | No | Form login (`username`, `password` form params) |
| `/api/logout` | POST | Yes | Invalidate session |

### Metadata

| Endpoint | Method | Params | Description |
|----------|--------|--------|-------------|
| `/api/groups` | GET | — | List of group names |
| `/api/databases` | GET | `group` | Databases in a group |
| `/api/tables` | GET | `group`, `database` | Tables with PK info |
| `/api/admin/relations` | GET | `group`, `database`, `table` | FK relations for a table |
| `/api/admin/suggestions` | GET | `group` | Suggested custom relations |

### Query & Navigation

| Endpoint | Method | Params | Description |
|----------|--------|--------|-------------|
| `/api/execute` | POST | `group` (query) | Execute SQL; body: `{query, database, queryType, info, relation}` |
| `/api/references` | GET | `group`, `database`, `table`, `column`, `row`, `refRowLimit`, `includeSelf` | Follow FK forward |
| `/api/dereferences` | GET | `group`, `database`, `table`, `column`, `row`, `refRowLimit` | Show referencing rows |
| `/api/trace` | GET | `group`, `database`, `table`, `row`, `refRowLimit` | Trace all FK relationships |

### Row Mutations

| Endpoint | Method | Params | Body | Description |
|----------|--------|--------|------|-------------|
| `/api/row/add` | POST | `group`, `database`, `table` | JSON row object | Insert row |
| `/api/row/edit` | PUT | `group`, `database`, `table`, `pk`, `pkValue` | JSON row object | Update row |
| `/api/row` | DELETE | `group`, `database`, `table`, `pk`, `pkValue` | — | Delete row |

All mutation endpoints verify `isUpdatableConnection()`/`isDeletableConnection()` and return `403` if not permitted.

---

## 8. Extension Points

### 8.1 New Data Type Handler

```java
public class MyHandler implements DataHandler {
    public String convert(String value) { return ...; }
}
DataManager.register("my_type_name", new MyHandler());
```

### 8.2 New Config Format

```java
public class YamlParser implements ConfigParserInterface<ConnectionConfig> {
    public String getSupportedExtension() { return "yaml"; }
    public ConnectionConfig parse(File f) { ... }
}
ConfigParserFactory.registerParser(ConnectionConfig.class, new YamlParser());
```

### 8.3 Additional Database Support

1. Add JDBC driver to `pom.xml`
2. Set `DRIVER_CLASS_NAME` and `DATABASE_URL` in `DatabaseConnection.xml`
3. Verify `useInformationSchema=true` equivalent for FK discovery

---

## 9. Known Limitations

### Config & Credentials

| Limitation | Detail |
|------------|--------|
| Plaintext credentials in config files | Passwords in `DatabaseConnection.xml` and `custom_mapping.json` are stored as plaintext. Restrict file permissions (`chmod 600`) or use the `db`/`api` config source with environment-variable injection. |
| MySQL/MariaDB only | PostgreSQL and other databases are not supported. `INFORMATION_SCHEMA` structure and FK discovery (`getImportedKeys`) behaviour differs across vendors. |
| No HTTPS termination | TLS must be terminated at a reverse proxy (nginx, Caddy, or Kubernetes ingress). The application has no built-in TLS. |

### Relation Refresh (RelationRowDbLoader)

| Limitation | Detail |
|------------|--------|
| Millisecond-boundary race on change detection | `refresh()` queries `MAX(updated_at)` and then fetches all active rows. If a row is written in the same millisecond as the `MAX` query, the write may complete *after* the SELECT and be silently missed until the next poll cycle. **Mitigation:** add a 1-second buffer — query `MAX(updated_at) WHERE updated_at < NOW() - INTERVAL 1 SECOND` so only fully-settled writes are picked up. This adds at most 1s of extra latency to detection but eliminates the race. |
| Redis pub/sub is fire-and-forget | If a replica is down when a `fkblitz:config-changed` message is published, it misses the notification and falls back to polling at `refresh-interval-seconds`. There is no message persistence or replay. |
| `RelationRowDbLoader` does not merge with JSON custom relations | Relations loaded from the DB table and relations from `custom_mapping.json` are used by different code paths. If both sources are configured simultaneously, `mapping_tables` and `auto_resolve` from the JSON file are not available to the DB-sourced config. |

### Custom Relations (custom_mapping.json)

| Limitation | Detail |
|------------|--------|
| `conditions` supports exact-match and IN-list only | No range queries, regex, NULL checks, or multi-column compound conditions. Keys are ANDed — OR logic is not supported. |
| `ONE_TO_ONE` / `ONE_TO_MANY` mapping table types | These types exist in the enum but behave identically to `MANY_TO_MANY` in the current navigation logic — the type field is not used to change join strategy. |
| Cross-group relations not supported | `referenced_database_name` must be a database accessible within the same FkBlitz `GROUP`. There is no mechanism to navigate across groups. |
| No depth limit on `auto_resolve` | Deep or circular `auto_resolve` chains can cause wide fan-out on `Trace` requests. No cycle detection is implemented. |

### Security & Auth

| Limitation | Detail |
|------------|--------|
| Rate limits are per-node, in-memory | Bucket4j buckets are not shared across replicas. A user can exceed the rate limit by distributing requests across nodes. |
| Session invalidation is local when Redis is disabled | Without Redis, logging out on one node does not invalidate sessions on other nodes. |

---

## 10. Glossary

| Term | Definition |
|------|------------|
| `GROUP` | Environment label for a set of connections (e.g., `production`, `staging`) |
| `referTo` | FK pointing from the current column outward to another table |
| `referencedBy` | Reverse FK — another column in another table points to this column |
| Mapping table | Junction table implementing a many-to-many relationship |
| Lazy load | Metadata fetched from the DB on first access, cached thereafter |
| `ColumnPath` | Fully qualified column reference: `database.table.column` |
| `Source` | Origin of a relationship: `DB` (native FK), `CUSTOM` (from config), `NEW` |
| Trace | Expanding the full FK chain for a single row across all related tables |
