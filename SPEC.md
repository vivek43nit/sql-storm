# SQL-Storm Technical Specification

## 1. Purpose

SQL-Storm is a web application that provides a browser-based interface for exploring and querying MySQL and MariaDB databases. It supports multi-environment connection management, automated foreign key relationship discovery, custom relationship definitions, and optional row-level edit/delete operations.

---

## 2. Functional Requirements

### 2.1 Connection Management

- **FR-CM-01**: The system must support multiple database connections, each identified by a `GROUP` (environment) and `DB_NAME`.
- **FR-CM-02**: Connections must be pooled and reused across requests until they expire.
- **FR-CM-03**: Expired or failed connections must be recreated automatically, up to a configurable maximum retry count.
- **FR-CM-04**: Each connection must be configurable with separate `UPDATABLE` and `DELETABLE` flags.
- **FR-CM-05**: Each connection must support a configurable row limit for queries on non-indexed columns (`NON_INDEXED_SEARCHABLE_ROW_LIMIT`).
- **FR-CM-06**: Connection configuration must be loadable from XML or JSON files.

### 2.2 Metadata Discovery

- **FR-MD-01**: The system must automatically discover all tables in a connected database via JDBC `DatabaseMetaData`.
- **FR-MD-02**: For each table, the system must discover: column names, data types, sizes, nullability.
- **FR-MD-03**: The system must discover primary keys, indexes, and unique constraints per table.
- **FR-MD-04**: The system must discover foreign key relationships (`getImportedKeys`) and build a bidirectional relationship graph.
- **FR-MD-05**: Metadata must be loaded lazily (on first access per database) and cached for subsequent requests.

### 2.3 Custom Relationships

- **FR-CR-01**: Users must be able to define relationships not captured by database foreign keys via `custom_mapping.json`.
- **FR-CR-02**: Custom relations must support optional conditions (e.g., only follow the relationship when a column equals a specific value).
- **FR-CR-03**: Custom relations must support cross-database references.
- **FR-CR-04**: The system must support junction/mapping tables with types: `ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_MANY`.
- **FR-CR-05**: The system must support auto-resolve column lists per table.

### 2.4 Query Execution

- **FR-QE-01**: Users must be able to execute arbitrary SQL `SELECT` queries against any configured database.
- **FR-QE-02**: If `UPDATABLE=true`, users must be able to execute `UPDATE` statements and edit rows via the UI.
- **FR-QE-03**: If `DELETABLE=true`, users must be able to execute `DELETE` statements and delete rows via the UI.
- **FR-QE-04**: Query results must be paginated or limited.

### 2.5 Relationship Navigation

- **FR-RN-01**: From any result row, the user must be able to navigate to rows in related tables (via `referTo` foreign keys).
- **FR-RN-02**: From any result row, the user must be able to navigate to rows that reference the current row (via `referencedBy` reverse relationships).
- **FR-RN-03**: Relationship navigation must respect conditions defined in custom mappings.
- **FR-RN-04**: Many-to-many relationships navigated through mapping tables must resolve to the final target rows.
- **FR-RN-05**: The number of related rows fetched per relationship must be configurable (default 100, constant `DEFAULT_REFERENCES_ROWS_LIMIT`).

### 2.6 Data Type Conversion

- **FR-DT-01**: The system must support custom rendering of column values based on a registered type name.
- **FR-DT-02**: Integer IP addresses must be convertible to dotted IPv4 notation.
- **FR-DT-03**: Epoch millisecond timestamps must be convertible to human-readable date strings in two formats: short (`dd MMM yyyy`) and long (`dd MMM yyyy HH:mm:ss`).
- **FR-DT-04**: Additional data type handlers must be registerable without modifying existing code.

### 2.7 Security

- **FR-SEC-01**: The application must support optional session-based authentication via a configurable servlet filter.
- **FR-SEC-02**: When authentication is enabled, unauthenticated requests must be redirected to a configurable login URL.
- **FR-SEC-03**: Login page access must be restrictable to a specific IP/host.

---

## 3. Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| NFR-01 | Connection pool must handle concurrent requests without race conditions. |
| NFR-02 | Config file parsing must be thread-safe and cached. |
| NFR-03 | Metadata loading must not block other requests to different databases. |
| NFR-04 | The application must run on Java 1.7+. |
| NFR-05 | The application must deploy as a WAR on Tomcat 7+. |
| NFR-06 | Log output must go to `/var/log/sql-storm.log` at INFO level by default. |
| NFR-07 | Connection credentials must be kept out of application code; read from external config files. |

---

## 4. System Components

### 4.1 Component Diagram

```
┌─────────────────────────────────────────────────────┐
│                    Web Browser                      │
└────────────────────────┬────────────────────────────┘
                         │ HTTP
┌────────────────────────▼────────────────────────────┐
│              Tomcat Servlet Container                │
│  ┌──────────────────────────────────────────────┐   │
│  │            SessionFilter (optional)          │   │
│  └──────────────────────┬───────────────────────┘   │
│                         │                           │
│  ┌──────────────────────▼───────────────────────┐   │
│  │              JSP Pages (mysql/*.jsp)         │   │
│  └──────────────────────┬───────────────────────┘   │
│                         │                           │
│  ┌──────────────────────▼───────────────────────┐   │
│  │             DatabaseManager (Facade)         │   │
│  │  ┌──────────────────┐  ┌───────────────────┐ │   │
│  │  │ ConnectionManager│  │ MetaDataManager   │ │   │
│  │  └────────┬─────────┘  └────────┬──────────┘ │   │
│  └───────────┼────────────────────┼─────────────┘   │
│              │                    │                  │
│  ┌───────────▼────────────────────▼─────────────┐   │
│  │              DBHelper (SQL generation)       │   │
│  └──────────────────────┬───────────────────────┘   │
└─────────────────────────┼───────────────────────────┘
                          │ JDBC
              ┌───────────▼───────────┐
              │  MySQL / MariaDB DB   │
              └───────────────────────┘
```

### 4.2 Configuration Loading

Config files are resolved via `ResorceFinder` and `ConfigParser` in priority order:

```
/etc/sql-storm/<filename>.<ext>
  → ~/.sql-storm/<filename>.<ext>
    → ~/<filename>.<ext>
      → classpath:resources/<filename>.<ext>
```

Two config files are consumed:
- `DatabaseConnection` — database credentials and connection settings
- `custom_mapping` — custom relationship definitions

Both support XML and JSON formats (format detected by file extension).

---

## 5. Data Models

### 5.1 Database Connection Config

```
ConnectionConfig
  └── connection_expiry_time : long  (ms, default 3,600,000)
  └── max_retry_count        : int   (default 10)
  └── connections[]
        └── ConnectionDTO
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
  tables   : Map<tableName, TableDTO>
  loaded   : boolean

TableDTO
  tableName    : String
  remark       : String
  primaryKey   : String
  columns      : LinkedHashMap<columnName, ColumnDTO>  (insertion order preserved)

ColumnDTO
  name         : String
  dataType     : String
  size         : int
  nullable     : boolean
  indexed      : boolean
  primaryKey   : boolean
  unique       : boolean
  referTo      : List<ColumnPath>       (FK targets)
  referencedBy : List<ColumnPath>       (reverse FK sources)
```

### 5.3 Relationship Model

```
ReferenceDTO
  db           : String    (source database)
  table        : String    (source table)
  column       : String    (source column)
  refDb        : String    (target database)
  refTable     : String    (target table)
  refColumn    : String    (target column)
  conditions   : Map<String, String>
  source       : Source enum { DB, CUSTOM, NEW }

ColumnPath
  database     : String
  table        : String
  column       : String
  conditions   : Map<String, String>
  source       : Source
  getPathString() → "database.table.column"

MappingTableDto
  type         : MappingType { ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY }
  from         : String
  to           : String
  includeSelf  : boolean
```

### 5.4 Custom Relation Config

```
CustomRelationConfig
  databases : Map<dbName, DatabaseConfig>

DatabaseConfig
  relations     : List<ReferenceDTO>
  jointTables   : Map<tableName, MappingTableDto>
  autoResolve   : Map<tableName, List<columnName>>
```

---

## 6. Key Algorithms

### 6.1 Connection Acquisition

```
getConnection(group, db):
  key = (group, db)
  info = connectionCache[key]
  if info != null AND info.connection is valid AND NOT expired:
    return info.connection
  for attempt in 1..maxRetryCount:
    set DriverManager.loginTimeout = 10s
    conn = DriverManager.getConnection(url, user, pass)
    if conn is valid:
      connectionCache[key] = new ConnectionInfo(conn, now)
      return conn
  throw ConnectionDetailNotFound
```

### 6.2 Metadata Lazy Load

```
lazyLoadFromDb(group, db):
  conn = getConnection(group, db)
  tables = DBHelper.getTables(conn)
  for each table:
    dto = new TableDTO(table)
    columns = DBHelper.getColumns(conn, table)
    for each column:
      dto.addColumn(new ColumnDTO(column))
    indexes = DBHelper.getAllIndexedColumns(conn, table)
    for each index:
      dto.setIndexingInfo(index)
    foreignKeys = DBHelper.getAllForeignKeys(conn, table)
    for each fk:
      sourceCol.addReferTo(new ColumnPath(fk.target))
      targetCol.addReferencedBy(new ColumnPath(fk.source))
  merge custom relations from CustomRelationConfig
  dbDTO.loaded = true
```

### 6.3 Related Row Query Generation

```
getExecuteRequestsForReferedByReq(req):
  column = metaData.getColumn(req.table, req.column)
  results = []
  for each path in column.referencedBy:
    if conditions not satisfied by req.data: skip
    if path is mapping table:
      sql = "SELECT t.* FROM targetTable t
             JOIN mappingTable m ON t.id = m.to_col
             WHERE m.from_col = ?"
    else:
      sql = "SELECT * FROM refTable WHERE refCol = ?"
      if conditions: sql += " AND <conditions>"
      sql += " LIMIT " + refRowLimit
    results.add(new ExecuteRequest(sql, ...))
  return results
```

---

## 7. Configuration Reference

### 7.1 `DatabaseConnection.xml` Attributes

| Attribute | Required | Default | Description |
|-----------|----------|---------|-------------|
| `CONNECTION_EXPIRY_TIME` | No | 3600000 | Connection TTL in ms |
| `MAX_RETRY_COUNT` | No | 10 | Max reconnection attempts |
| `ID` | Yes | — | Unique connection identifier |
| `GROUP` | Yes | — | Environment label (e.g., `production`) |
| `DB_NAME` | Yes | — | Display name for the database |
| `DRIVER_CLASS_NAME` | Yes | — | JDBC driver class |
| `DATABASE_URL` | Yes | — | JDBC connection URL |
| `USER_NAME` | Yes | — | Database username |
| `PASSWORD` | Yes | — | Database password |
| `UPDATABLE` | No | false | Allow UPDATE operations |
| `DELETABLE` | No | false | Allow DELETE operations |
| `NON_INDEXED_SEARCHABLE_ROW_LIMIT` | No | — | Row limit for unindexed column searches |

### 7.2 `custom_mapping.json` Schema

```json
{
  "databases": {
    "<db_name>": {
      "relations": [
        {
          "table_name": "<string>",
          "table_column": "<string>",
          "referenced_table_name": "<string>",
          "referenced_column_name": "<string>",
          "referenced_database_name": "<string> (optional)",
          "conditions": { "<column>": "<value>" }
        }
      ],
      "mapping_tables": {
        "<junction_table_name>": {
          "type": "ONE_TO_ONE | ONE_TO_MANY | MANY_TO_MANY",
          "from": "<column>",
          "to": "<column>",
          "include-self": true
        }
      },
      "auto_resolve": {
        "<table_name>": ["<column1>", "<column2>"]
      }
    }
  }
}
```

### 7.3 `web.xml` Context Parameters

| Parameter | Description |
|-----------|-------------|
| `logoutTime` | Session inactivity timeout in minutes (default 30) |
| `serverPort` | Application server port (default 8080) |

---

## 8. Extension Points

### 8.1 Adding a New Data Type Handler

1. Implement `com.vivek.sqlstorm.datahandler.DataHandler`:
   ```java
   public class MyHandler implements DataHandler {
       public String convert(String value) { ... }
   }
   ```
2. Register in `DataManager`:
   ```java
   DataManager.register("my_type_name", new MyHandler());
   ```
3. In the database or custom config, tag the column with `my_type_name` as its type.

### 8.2 Adding a New Config Format

1. Implement `com.vivek.utils.parser.ConfigParserInterface<T>`:
   ```java
   public class MyFormatParser implements ConfigParserInterface<ConnectionConfig> {
       public String getApplicationName() { return "sql-storm"; }
       public String getSupportedExtension() { return "yaml"; }
       public ConnectionConfig parse(File f) { ... }
   }
   ```
2. Register in `ConfigParserFactory`:
   ```java
   ConfigParserFactory.registerParser(ConnectionConfig.class, new MyFormatParser());
   ```

### 8.3 Supporting Additional Databases

1. Add the JDBC driver as a Maven dependency.
2. Set `DRIVER_CLASS_NAME` and `DATABASE_URL` in the connection config for the new driver.
3. Verify `DatabaseMetaData` methods return correct results for the new database (no code changes expected for standard JDBC-compliant drivers).

---

## 9. Known Limitations

| Limitation | Details |
|------------|---------|
| No unit tests | The project has no automated test suite. All testing is manual. |
| Java 1.7 target | Compiled for Java 7; cannot use Java 8+ language features (lambdas, streams). |
| Single connection per (group, db) | No true pool; one connection cached per (group, db) pair. |
| Plaintext credentials | Database passwords stored as plaintext in config files. |
| `SessionFilter` disabled | Authentication is commented out in `web.xml` by default. |
| Deprecated classes | `GetReferenceDAO`, `CustomRelationHandler`, and `TableMetaData` exist but are fully commented out. |
| Log4j 1.x | Uses end-of-life Log4j 1.2.17; should be upgraded to Log4j 2.x or SLF4J+Logback. |

---

## 10. Glossary

| Term | Definition |
|------|------------|
| `GROUP` | An environment label for a set of connections (e.g., `production`, `staging`, `localhost`) |
| `referTo` | A foreign key pointing from the current column to another table's column |
| `referencedBy` | A reverse foreign key — another column that points to the current column |
| Mapping table | A junction table that implements a many-to-many relationship |
| Auto-resolve | A column list that the system automatically resolves relationships for |
| Lazy load | Metadata is fetched from the database only on first access, then cached |
| `ColumnPath` | A fully qualified column reference: `database.table.column` |
| `Source` | Origin of a relationship: `DB` (native FK), `CUSTOM` (from config), or `NEW` |
