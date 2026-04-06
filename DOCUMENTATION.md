# SQL-Storm Documentation

## Overview

SQL-Storm is a web-based MySQL/MariaDB database browser and client. It allows users to browse multiple databases across environments, navigate foreign key relationships, view table metadata, execute queries, and optionally edit or delete rows.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Module Breakdown](#module-breakdown)
3. [Configuration](#configuration)
4. [Data Flow](#data-flow)
5. [API / Request Objects](#api--request-objects)
6. [Data Type Handlers](#data-type-handlers)
7. [Security](#security)
8. [Frontend Pages](#frontend-pages)
9. [Dependencies](#dependencies)
10. [Build & Deployment](#build--deployment)

---

## Architecture

```
Browser (JSP + JS)
       |
  Servlet/JSP Layer  (webapp/mysql/*.jsp)
       |
  DatabaseManager  (Facade)
      /         \
DatabaseConnection  DatabaseMetaDataManager
    Manager
       |                  |
  JDBC Connections   Metadata Cache
  (per group/db)    (tables, columns, relations)
       |
  MySQL / MariaDB
```

SQL-Storm uses a layered architecture:

- **Presentation Layer**: JSP pages + JavaScript/CSS served at `/sql-storm/mysql/`
- **Business Logic**: `DatabaseManager` as the central facade, backed by `DatabaseConnectionManager` and `DatabaseMetaDataManager`
- **Configuration Layer**: XML and JSON config files parsed via a pluggable parser framework
- **Data Access**: Direct JDBC to MySQL/MariaDB with metadata introspection via `DatabaseMetaData`

---

## Module Breakdown

### `com.vivek.sqlstorm`

#### `DatabaseManager`
The main facade singleton. All external code goes through this class.

| Method | Description |
|--------|-------------|
| `getConnection(group, db)` | Returns a live JDBC connection for the given group and database |
| `getMetaData(group, db)` | Returns cached `DatabaseDTO` for the given group and database |
| `getTables(group, db)` | Returns all tables for a database |
| `isUpdatableConnection(group, db)` | Returns true if rows can be updated |
| `isDeletableConnection(group, db)` | Returns true if rows can be deleted |

---

### `com.vivek.sqlstorm.connection`

#### `DatabaseConnectionManager`
Manages JDBC connection pooling and lifecycle.

- Connections are keyed by `(group, dbName)`.
- Connections are reused until they expire (`CONNECTION_EXPIRY_TIME`, default 3,600,000 ms = 1 hour).
- On expiry or failure, a new connection is created up to `MAX_RETRY_COUNT` (default 10) times.
- Login timeout per attempt: 10 seconds.

Inner class `ConnectionInfo` tracks creation time and connection validity.

---

### `com.vivek.sqlstorm.metadata`

#### `DatabaseMetaDataManager`
Singleton that manages and caches database schema metadata.

- On startup, initializes empty `DatabaseDTO` objects per connection.
- On first access, calls `lazyLoadFromDb()` to fetch tables, columns, indexes, and foreign keys via JDBC `DatabaseMetaData`.
- Merges custom relationships from `custom_mapping.json`.
- Builds bidirectional relationship graph: each column tracks `referTo` (foreign keys pointing out) and `referencedBy` (foreign keys pointing in).

---

### `com.vivek.sqlstorm.config`

#### Connection Configuration

| Class | Description |
|-------|-------------|
| `ConnectionConfig` | Holds list of `ConnectionDTO` objects plus expiry and retry settings |
| `ConnectionDTO` | One database connection: driver, URL, credentials, group, dbName, updatable/deletable flags, row limit |
| `DatabaseConfigXmlParser` | Parses `DatabaseConnection.xml` using JDOM2 |
| `DatabaseConfigJsonParser` | Parses `DatabaseConnection.json` |

#### Custom Relation Configuration

| Class | Description |
|-------|-------------|
| `CustomRelationConfig` | Maps database names to `DatabaseConfig` objects |
| `DatabaseConfig` | Per-database: relations list, mapping tables, auto-resolve columns |
| `CustomRelationConfigJsonParser` | Parses `custom_mapping.json` |

---

### `com.vivek.sqlstorm.dto`

| DTO | Key Fields |
|-----|------------|
| `DatabaseDTO` | `group`, `dbName`, `tables` (map), loaded flag |
| `TableDTO` | `tableName`, `columns` (ordered map), `primaryKey`, `remark` |
| `ColumnDTO` | `name`, `dataType`, `size`, `nullable`, `indexed`, `primaryKey`, `unique`, `referTo`, `referencedBy` |
| `ReferenceDTO` | `db`, `table`, `column`, `refDb`, `refTable`, `refColumn`, `conditions`, `source` |
| `ColumnPath` | `database`, `table`, `column`, `conditions`, `source` — produces `db.table.column` string |
| `IndexInfo` | `columnName`, `primaryKey`, `unique` |
| `MappingTableDto` | `type` (ONE_TO_ONE/ONE_TO_MANY/MANY_TO_MANY), `from`, `to`, `includeSelf` |
| `SessionDTO` | `group` — holds session group info |
| `ExecuteRequest` | `query`, `queryType` (S/D/U), `database`, `info`, `append`, `relation` |
| `GetRelationsRequest` | `database`, `table`, `column`, `data`, `append`, `includeSelf`, `refRowLimit` (default 100) |

---

### `com.vivek.sqlstorm.utils`

#### `DBHelper`
JDBC utility class with static methods:

| Method | Description |
|--------|-------------|
| `getTables(conn)` | Returns all table names |
| `getColumns(conn, table)` | Returns column metadata for a table |
| `getAllIndexedColumns(conn, table)` | Returns index information for a table |
| `getAllForeignKeys(conn, table)` | Returns foreign key constraints |
| `isReferToConditionMatch(conditions, data)` | Validates conditions against a row's data |
| `getWhereQueryFromConditions(conditions)` | Builds SQL WHERE clause from conditions map |
| `getExecuteRequestsForReferedByReq(req, metaData)` | Generates SQL queries to fetch related rows |

`getExecuteRequestsForReferedByReq()` handles three cases:
1. Simple foreign key navigation
2. Mapping table (junction) navigation for many-to-many
3. Auto-resolve column navigation

---

### `com.vivek.sqlstorm.datahandler`

| Class | Description |
|-------|-------------|
| `DataManager` | Registry; maps data type names to `DataHandler` implementations |
| `DataHandler` | Interface: `convert(value)` |
| `IpDataHandler` | Converts `long` integer to dotted IPv4 (e.g., `2130706433` → `127.0.0.1`) |
| `ShortDateDataHandler` | Converts epoch ms to `dd MMM yyyy` |
| `LongDateDataHandler` | Converts epoch ms to `dd MMM yyyy HH:mm:ss` |

---

### `com.vivek.utils`

#### `ConfigParserFactory`
Thread-safe factory (uses `ConcurrentHashMap`). Parsers are registered by class type. `getParser(Class)` throws `NoParserRegistered` if none is registered.

#### `ConfigParser<T>`
Wraps multiple `ConfigParserInterface` instances keyed by file extension. On `parse()`:
1. Looks for config file in: `/etc/sql-storm/`, `~/sql-storm/`, `~/`, classpath resources
2. Tries each registered extension in order
3. Caches result after first successful parse

#### `ResorceFinder`
Resolves config file path given a filename. Priority:
1. `/etc/sql-storm/<filename>`
2. `~/.sql-storm/<filename>` (i.e., `~/sql-storm/.`)
3. `~/<filename>`
4. Classpath resource `<filename>`

---

### `com.vivek.filter`

#### `SessionFilter`
Optional servlet filter for session-based access control. Configurable via `web.xml` init params:

| Param | Description |
|-------|-------------|
| `loginUrl` | Redirect target for unauthenticated requests |
| `sessionAttributeName` | Session attribute to check |
| `loginAllowedFrom` | IP or host allowed to access login page |
| `loginSubmitUrl` | URL for login form submission |

Currently disabled in `web.xml` (commented out).

---

## Configuration

### Database Connections — `DatabaseConnection.xml`

Located at (in search order):
1. `/etc/sql-storm/DatabaseConnection.xml`
2. `~/.sql-storm/DatabaseConnection.xml`
3. `~/DatabaseConnection.xml`
4. Classpath `resources/DatabaseConnection.xml`

```xml
<CONNECTIONS CONNECTION_EXPIRY_TIME="3600000" MAX_RETRY_COUNT="10">
    <CONNECTION
        ID="1"
        GROUP="localhost"
        DB_NAME="mydb"
        DRIVER_CLASS_NAME="com.mysql.jdbc.Driver"
        DATABASE_URL="jdbc:mysql://localhost:3306/mydb"
        USER_NAME="root"
        PASSWORD="secret"
        UPDATABLE="true"
        DELETABLE="false"
        NON_INDEXED_SEARCHABLE_ROW_LIMIT="10000"
    />
</CONNECTIONS>
```

Also supports JSON format (`DatabaseConnection.json`):
```json
{
  "connection_expiry_time": 3600000,
  "max_retry_count": 10,
  "connections": [
    {
      "id": "1",
      "group": "localhost",
      "db_name": "mydb",
      "driver_class_name": "com.mysql.jdbc.Driver",
      "database_url": "jdbc:mysql://localhost:3306/mydb",
      "user_name": "root",
      "password": "secret",
      "updatable": true,
      "deletable": false,
      "non_indexed_searchable_row_limit": 10000
    }
  ]
}
```

### Custom Relationships — `custom_mapping.json`

Located at the same search paths as the connection config.

```json
{
  "databases": {
    "mydb": {
      "relations": [
        {
          "table_name": "orders",
          "table_column": "customer_id",
          "referenced_table_name": "customers",
          "referenced_column_name": "id",
          "referenced_database_name": "mydb",
          "conditions": { "status": "active" }
        }
      ],
      "mapping_tables": {
        "order_tags": {
          "type": "MANY_TO_MANY",
          "from": "order_id",
          "to": "tag_id",
          "include-self": true
        }
      },
      "auto_resolve": {
        "orders": ["customer_id", "product_id"]
      }
    }
  }
}
```

**Relation types for mapping tables:**

| Type | Description |
|------|-------------|
| `ONE_TO_ONE` | Direct 1:1 link through junction table |
| `ONE_TO_MANY` | One side links to many through junction |
| `MANY_TO_MANY` | Both sides are multi-valued |

### Logging — `log4j.properties`

```properties
log4j.rootLogger=INFO, A1
log4j.appender.A1=org.apache.log4j.FileAppender
log4j.appender.A1.File=/var/log/sql-storm.log
log4j.appender.A1.layout=org.apache.log4j.PatternLayout
log4j.appender.A1.layout.ConversionPattern=%d{DATE} %-5p %F|%L : %m%n
```

---

## Data Flow

### Startup

1. `DatabaseManager` is instantiated (singleton).
2. `DatabaseConnectionManager` reads `DatabaseConnection.xml` or `.json`.
3. `DatabaseMetaDataManager` reads `custom_mapping.json` and builds empty `DatabaseDTO` objects per connection.

### First Metadata Access (Lazy Load)

1. `DatabaseMetaDataManager.getMetaData(group, db)` is called.
2. If not yet loaded, `lazyLoadFromDb()` runs:
   - Fetches table list via `DatabaseMetaData.getTables()`
   - For each table: fetches columns, indexes, and foreign keys
   - Builds `TableDTO` and `ColumnDTO` objects
   - Merges custom relations from config
   - Builds bidirectional graph: `ColumnDTO.referTo` and `ColumnDTO.referencedBy`

### Query Execution

1. User submits an `ExecuteRequest` with SQL and metadata.
2. For relationship navigation, user submits a `GetRelationsRequest`.
3. `DBHelper.getExecuteRequestsForReferedByReq()` generates SQL:
   - For direct FK: `SELECT * FROM ref_table WHERE ref_col = ? [AND conditions] LIMIT n`
   - For mapping tables: nested SELECT through junction
4. Each generated `ExecuteRequest` is executed in sequence.
5. Results are returned to JSP for rendering.

### Data Rendering

- Column values are passed through `DataManager.convert(type, value)`.
- Registered handlers convert IP integers and epoch timestamps to human-readable strings.

---

## API / Request Objects

### `ExecuteRequest`

| Field | Type | Description |
|-------|------|-------------|
| `query` | `String` | SQL statement |
| `queryType` | `String` | `S` (select), `D` (delete), `U` (update) |
| `database` | `String` | Target database name |
| `info` | `String` | Display label for result set |
| `append` | `boolean` | Append result to existing output |
| `relation` | `String` | One of `self`, `referTo`, `referedBy` |

### `GetRelationsRequest`

| Field | Type | Description |
|-------|------|-------------|
| `database` | `String` | Source database |
| `table` | `String` | Source table |
| `column` | `String` | Source column |
| `data` | `JSONObject` | Row data (column → value map) |
| `append` | `boolean` | Append mode |
| `includeSelf` | `boolean` | Include source row in results |
| `refRowLimit` | `int` | Max related rows to fetch (default 100) |

---

## Data Type Handlers

Custom handlers extend `DataHandler` and are registered in `DataManager` by type name string.

| Handler | Trigger Type Name | Conversion |
|---------|-------------------|------------|
| `IpDataHandler` | `ip` | `long` integer → `"a.b.c.d"` IPv4 string |
| `ShortDateDataHandler` | `short_date` | epoch ms → `"dd MMM yyyy"` |
| `LongDateDataHandler` | `long_date` | epoch ms → `"dd MMM yyyy HH:mm:ss"` |

To add a new handler: implement `DataHandler`, register in `DataManager`.

---

## Security

### Session Filter (Optional)

`SessionFilter` intercepts all requests and checks for a valid session attribute. If the session is missing or the attribute is absent, the request is redirected to `loginUrl`.

To enable, uncomment the filter mapping in `web.xml` and set the init parameters:

```xml
<filter>
  <filter-name>SessionFilter</filter-name>
  <filter-class>com.vivek.filter.SessionFilter</filter-class>
  <init-param><param-name>loginUrl</param-name><param-value>/login.jsp</param-value></init-param>
  <init-param><param-name>sessionAttributeName</param-name><param-value>user</param-value></init-param>
</filter>
<filter-mapping>
  <filter-name>SessionFilter</filter-name>
  <url-pattern>/*</url-pattern>
</filter-mapping>
```

### Notes

- Credentials in `DatabaseConnection.xml` are stored in plaintext. Use filesystem permissions to restrict access.
- The `UPDATABLE` and `DELETABLE` flags per connection control whether row mutations are exposed in the UI.
- `NON_INDEXED_SEARCHABLE_ROW_LIMIT` limits full-table scans on unindexed columns.

---

## Frontend Pages

All pages live under `webapp/mysql/`.

| Page | Description |
|------|-------------|
| `groups.jsp` | Lists available database groups (environments) |
| `databases.jsp` | Lists databases within a group |
| `tables.jsp` | Lists tables within a database |
| `viewResultSet.jsp` | Displays query results in a table |
| `getReferences.jsp` | Fetches and displays rows related via foreign keys (referTo) |
| `getDeReferences.jsp` | Fetches rows that reference the current row (referencedBy) |
| `traceRow.jsp` | Traces a row through all its relationships |
| `execute.jsp` | Free-form SQL execution |
| `editRow.jsp` | Edit a row (if `UPDATABLE=true`) |
| `addRow.jsp` | Add a new row (if `UPDATABLE=true`) |
| `deleteRow.jsp` | Delete a row (if `DELETABLE=true`) |
| `admin/relation/` | Admin pages for relationship inspection |

JavaScript: `mysql.js`, `loader.js`
CSS: `mysql.css`

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Lombok | 1.18.10 | Boilerplate reduction (`@Data`, `@Getter`, etc.) |
| javax.servlet-api | 4.0.1 | Servlet API |
| log4j | 1.2.17 | Logging |
| org.json | 20231013 | JSON parsing |
| org.jdom | 2.0.2 | XML parsing |
| mysql-connector-java | 8.0.28 | MySQL JDBC driver |
| org.mariadb.jdbc | 2.1.2 | MariaDB JDBC driver |

---

## Build & Deployment

**Build tool:** Maven

```bash
mvn clean install
```

**Run locally with Tomcat 7 plugin:**

```bash
mvn tomcat7:run
```

Runs on port **9044** at path `/sql-storm/`.

**WAR deployment:** Copy the generated `target/sql-storm.war` to a Tomcat `webapps/` directory. The context path is `/sql-storm` (configured in `META-INF/context.xml`).

**Java version:** 1.7+ (configured in `maven-compiler-plugin`)

**Log output:** `/var/log/sql-storm.log` — ensure the Tomcat process has write permission.

**Config file placement:** Drop `DatabaseConnection.xml` and `custom_mapping.json` into `/etc/sql-storm/` for production deployments. Fallback is `~/` or the classpath.
