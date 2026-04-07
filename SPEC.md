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
| NFR-01 | Connection pool must handle concurrent requests without race conditions (`ConcurrentHashMap`). |
| NFR-02 | Config file parsing must be thread-safe and result cached after first parse. |
| NFR-03 | Metadata loading for one database must not block requests to other databases. |
| NFR-04 | The backend must run on Java 17+. |
| NFR-05 | The backend must start via `mvn spring-boot:run` or as a self-contained JAR. |
| NFR-06 | The frontend must build to a static bundle embeddable in the JAR. |
| NFR-07 | Connection credentials must never appear in application code; read from external config. |
| NFR-08 | The system must be deployable as a single Docker image via `docker compose up --build`. |

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
│  │               Spring Security                    │    │
│  │  (form login, session, 401 on API fail)          │    │
│  └──────────────────────┬───────────────────────────┘    │
│                         │                                 │
│  ┌──────────┬───────────▼──────────┬──────────────────┐  │
│  │ MetaData │  ExecuteController   │ RowMutation      │  │
│  │Controller│  /api/execute        │ Controller       │  │
│  │/api/     │  /api/references     │ /api/row/add     │  │
│  │groups    │  /api/dereferences   │ /api/row/edit    │  │
│  │/databases│  /api/trace          │ /api/row         │  │
│  │/tables   │                      │ (DELETE)         │  │
│  └──────────┴──────────┬───────────┴──────────────────┘  │
│                        │                                  │
│  ┌─────────────────────▼─────────────────────────────┐   │
│  │              DatabaseManager (Facade)             │   │
│  │  ┌────────────────────┐  ┌──────────────────────┐ │   │
│  │  │ ConnectionManager  │  │ MetaDataManager      │ │   │
│  │  │ (JDBC pool)        │  │ (schema + FK cache)  │ │   │
│  │  └─────────┬──────────┘  └──────────────────────┘ │   │
│  └────────────┼──────────────────────────────────────┘   │
└───────────────┼───────────────────────────────────────────┘
                │ JDBC
    ┌───────────▼──────────────┐
    │   MySQL / MariaDB         │
    └───────────────────────────┘
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

| Limitation | Detail |
|------------|--------|
| No unit tests | No automated test suite; all testing is manual against a live database. |
| Single connection per (group, db) | One JDBC connection cached per pair; not a true pool. High-concurrency deployments should sit behind a connection pool proxy. |
| Plaintext credentials | Passwords stored as plaintext in config XML/JSON. Restrict file permissions (`chmod 600`). |
| MySQL/MariaDB only | PostgreSQL and other databases are not tested. `INFORMATION_SCHEMA` behaviour may differ. |
| No HTTPS | TLS must be terminated at a reverse proxy (nginx, Caddy). |
| Session-based auth only | No OAuth2, LDAP, or API key support. |

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
