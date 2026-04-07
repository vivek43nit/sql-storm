# FkBlitz Documentation

## Overview

FkBlitz is a self-hosted, browser-based MySQL/MariaDB client built for navigating relational data by following foreign key relationships. It consists of a **Spring Boot 3 REST API** (backend) and a **React 18 SPA** (frontend).

---

## Table of Contents

1. [Architecture](#architecture)
2. [Backend Modules](#backend-modules)
3. [REST API](#rest-api)
4. [Frontend Components](#frontend-components)
5. [Configuration](#configuration)
6. [Data Flow](#data-flow)
7. [Data Type Handlers](#data-type-handlers)
8. [Security](#security)
9. [Dependencies](#dependencies)
10. [Build & Deployment](#build--deployment)

---

## Architecture

```
Browser (React SPA)
        │  HTTP / JSON
┌───────▼──────────────────────────────────────────────┐
│           Spring Boot 3 (port 9044, /fkblitz)        │
│                                                      │
│  Spring Security ──► REST Controllers                │
│                         │                            │
│                   DatabaseManager (Facade)           │
│                    /              \                  │
│   DatabaseConnectionManager   DatabaseMetaDataManager│
│          │                           │               │
│    JDBC Connections            Metadata Cache        │
│    (per group/db)         (tables, columns, FKs)     │
└───────────────────────────────┬──────────────────────┘
                                │ JDBC
                    ┌───────────▼───────────┐
                    │  MySQL / MariaDB       │
                    └───────────────────────┘
```

- **Presentation**: React SPA served from `frontend/` (dev: Vite on `:5173` with proxy; prod: static files embedded in the Spring Boot JAR)
- **API layer**: Spring MVC REST controllers under `/fkblitz/api/`
- **Business logic**: `DatabaseManager` facade, backed by connection and metadata managers
- **Config**: XML/JSON files resolved from `/etc/fkblitz/`, `~/.fkblitz/`, or classpath

---

## Backend Modules

### `com.vivek.SqlStormApplication`

Spring Boot entry point. Starts the embedded Tomcat on port `9044` with context path `/fkblitz`.

---

### `com.vivek.controller`

#### `MetaDataController`

Serves schema metadata to the frontend.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/groups` | Returns list of connection group names |
| GET | `/api/databases?group=` | Returns databases for a group |
| GET | `/api/tables?group=&database=` | Returns tables with PK info |
| GET | `/api/admin/relations?group=&database=&table=` | Returns FK relations for a table |
| GET | `/api/admin/suggestions?group=` | Returns suggested custom relations |

#### `ExecuteController`

Handles query execution and FK navigation.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/execute?group=` | Execute a SQL query; returns `ResultSetDTO` |
| GET | `/api/references` | Fetch rows referenced by a FK value (referTo) |
| GET | `/api/dereferences` | Fetch rows that reference a given row (referencedBy) |
| GET | `/api/trace` | Trace all FK relationships for a row |

#### `RowMutationController`

Handles CRUD operations. Only active when the connection has `UPDATABLE`/`DELETABLE` set.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/row/add?group=&database=&table=` | Insert a new row |
| PUT | `/api/row/edit?group=&database=&table=&pk=&pkValue=` | Update an existing row |
| DELETE | `/api/row?group=&database=&table=&pk=&pkValue=` | Delete a row |

---

### `com.vivek.sqlstorm`

#### `DatabaseManager`

Central singleton facade. All controllers call through here.

| Method | Description |
|--------|-------------|
| `getConnection(group, db)` | Returns a live JDBC connection |
| `getMetaData(group, db)` | Returns cached `DatabaseDTO` |
| `getTables(group, db)` | Returns all tables |
| `isUpdatableConnection(group, db)` | True if row edits are permitted |
| `isDeletableConnection(group, db)` | True if row deletes are permitted |

---

### `com.vivek.sqlstorm.connection`

#### `DatabaseConnectionManager`

Manages JDBC connection lifecycle.

- Connections keyed by `(group, dbName)`
- Reused until `CONNECTION_EXPIRY_TIME` ms elapses (default 1 hour)
- On expiry or failure, reconnects up to `MAX_RETRY_COUNT` times (default 10)
- Login timeout: 10 seconds per attempt

---

### `com.vivek.sqlstorm.metadata`

#### `DatabaseMetaDataManager`

Caches schema metadata per database.

- Lazily loaded on first access via `lazyLoadFromDb()`
- Fetches tables, columns, indexes, and foreign keys via JDBC `DatabaseMetaData`
- Merges custom relations from `custom_mapping.json`
- Builds bidirectional relationship graph: `ColumnDTO.referTo` and `ColumnDTO.referencedBy`

---

### `com.vivek.sqlstorm.config`

#### Connection Configuration

| Class | Description |
|-------|-------------|
| `ConnectionConfig` | List of `ConnectionDTO` objects plus expiry/retry settings |
| `ConnectionDTO` | One database connection: driver, URL, credentials, flags |
| `DatabaseConfigXmlParser` | Parses `DatabaseConnection.xml` using JDK built-in XML |
| `DatabaseConfigJsonParser` | Parses `DatabaseConnection.json` |

#### Custom Relation Configuration

| Class | Description |
|-------|-------------|
| `CustomRelationConfig` | Maps database names → `DatabaseConfig` |
| `DatabaseConfig` | Relations list, mapping tables, auto-resolve columns per database |
| `CustomRelationConfigJsonParser` | Parses `custom_mapping.json` |

---

### `com.vivek.sqlstorm.dto`

| DTO | Key Fields |
|-----|------------|
| `DatabaseDTO` | `group`, `dbName`, `tables` (map), `loaded` flag |
| `TableDTO` | `tableName`, `columns` (ordered map), `primaryKey`, `remark` |
| `ColumnDTO` | `name`, `dataType`, `nullable`, `indexed`, `primaryKey`, `referTo`, `referencedBy` |
| `ReferenceDTO` | `db`, `table`, `column`, `refDb`, `refTable`, `refColumn`, `conditions`, `source` |
| `ColumnPath` | `database.table.column` qualified reference with conditions and source |
| `MappingTableDto` | `type` (ONE_TO_ONE/ONE_TO_MANY/MANY_TO_MANY), `from`, `to`, `includeSelf` |
| `ResultSetDTO` | Query results: `columns`, `rows`, `info`, `relation`, `primaryKey` |
| `ExecuteRequest` | `query`, `queryType` (S/D/U), `database`, `info`, `relation` |

---

### `com.vivek.sqlstorm.utils`

#### `DBHelper`

JDBC utility with static methods:

| Method | Description |
|--------|-------------|
| `getTables(conn)` | All table names |
| `getColumns(conn, table)` | Column metadata |
| `getAllIndexedColumns(conn, table)` | Index information |
| `getAllForeignKeys(conn, table)` | FK constraints |
| `isReferToConditionMatch(conditions, data)` | Validates row against conditions |
| `getExecuteRequestsForReferedByReq(req, metaData)` | Generates SQL for reverse FK navigation |

---

### `com.vivek.sqlstorm.datahandler`

| Class | Description |
|-------|-------------|
| `DataManager` | Registry mapping type names → `DataHandler` implementations |
| `DataHandler` | Interface: `String convert(String value)` |
| `IpDataHandler` | `long` → dotted IPv4 (e.g., `2130706433` → `127.0.0.1`) |
| `ShortDateDataHandler` | epoch ms → `dd MMM yyyy` |
| `LongDateDataHandler` | epoch ms → `dd MMM yyyy HH:mm:ss` |

---

### `com.vivek.utils`

#### `ConfigParserFactory`

Thread-safe factory (`ConcurrentHashMap`). Parsers registered by target class type.

#### `ConfigParser<T>`

Wraps multiple `ConfigParserInterface` instances keyed by file extension. On `parse()`:

1. Resolves file via `ResourceFinder` (see priority order below)
2. Tries each registered extension
3. Caches result after first successful parse

#### `ResourceFinder`

Config file resolution order:

1. `/etc/fkblitz/<filename>`
2. `~/.fkblitz/<filename>`
3. `~/<filename>`
4. Classpath resource `<filename>`

---

## REST API

### Authentication

All endpoints except `/api/login` require an active session. Login via:

```
POST /fkblitz/api/login
Content-Type: application/x-www-form-urlencoded

username=admin&password=changeme
```

Returns `{"status":"ok","user":"admin"}` on success, `{"error":"Invalid credentials"}` on failure.

Logout:
```
POST /fkblitz/api/logout
```

### Response Format

All endpoints return JSON. Errors return:

```json
{"error": "message"}
```

Results from `/api/execute`, `/api/references`, `/api/dereferences`, `/api/trace` return a list of `ResultSetDTO`:

```json
[
  {
    "info": "fkblitz-test.orders",
    "relation": "self",
    "primaryKey": "id",
    "columns": ["id", "customer_id", "status", "total"],
    "rows": [
      {"id": "1", "customer_id": "1", "status": "delivered", "total": "1339.98"}
    ]
  }
]
```

---

## Frontend Components

| File | Description |
|------|-------------|
| `App.jsx` | Root component — session check, login gate, routing |
| `pages/LoginPage.jsx` | Login form |
| `pages/MainPage.jsx` | Main layout: sidebar + query bar + result grid |
| `pages/AdminRelationsPage.jsx` | View FK relations per table |
| `pages/AdminSuggestionsPage.jsx` | View suggested custom relations |
| `components/NavPanel.jsx` | Sidebar: group/database/table selectors |
| `components/TableGrid.jsx` | Result table with FK click-through, ↙ back-refs, filters, sort, CRUD buttons |
| `components/RowModal.jsx` | Add / Edit / Delete modal form |
| `components/ConverterPanel.jsx` | IP ↔ long, date ↔ epoch, live clock |
| `components/QueryBar.jsx` | SQL input, Run button, range/ref-limit controls |
| `api/client.js` | Axios instance with all API calls |

### FK Navigation Flow (TableGrid)

1. `↗` marker on a column header → column has outbound FKs
2. Click a cell value in that column → calls `GET /api/references` → renders referenced row in a new result panel
3. `↙` marker on a column header → column is referenced by other tables
4. Click `↙` button on a row → calls `GET /api/dereferences` → renders all referencing rows grouped by table
5. Click `Trace` on a row → calls `GET /api/trace` → expands the full FK chain

---

## Configuration

### `DatabaseConnection.xml`

```xml
<CONNECTIONS CONNECTION_EXPIRY_TIME="3600000" MAX_RETRY_COUNT="10">
    <CONNECTION
        ID="1"
        GROUP="production"
        DB_NAME="mydb"
        DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver"
        DATABASE_URL="jdbc:mysql://localhost:3306/mydb?useInformationSchema=true"
        USER_NAME="dbuser"
        PASSWORD="dbpass"
        UPDATABLE="false"
        DELETABLE="false"
        NON_INDEXED_SEARCHABLE_ROW_LIMIT="10000"
    />
</CONNECTIONS>
```

| Attribute | Required | Default | Description |
|-----------|----------|---------|-------------|
| `CONNECTION_EXPIRY_TIME` | No | 3600000 | Connection TTL (ms) |
| `MAX_RETRY_COUNT` | No | 10 | Reconnect attempts |
| `ID` | Yes | — | Unique identifier |
| `GROUP` | Yes | — | Environment label |
| `DB_NAME` | Yes | — | Database display name |
| `DRIVER_CLASS_NAME` | Yes | — | JDBC driver class |
| `DATABASE_URL` | Yes | — | JDBC URL (`useInformationSchema=true` required for FK discovery) |
| `USER_NAME` | Yes | — | Database username |
| `PASSWORD` | Yes | — | Database password |
| `UPDATABLE` | No | false | Enable Add/Edit UI |
| `DELETABLE` | No | false | Enable Delete UI |
| `NON_INDEXED_SEARCHABLE_ROW_LIMIT` | No | — | Max rows for unindexed scans |

### `custom_mapping.json`

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
      }
    }
  }
}
```

### `application.yml`

```yaml
server:
  port: 9044
  servlet:
    context-path: /fkblitz

spring:
  security:
    user:
      name: admin
      password: changeme
  session:
    timeout: 30m
```

---

## Data Flow

### Startup

1. Spring Boot starts; `DatabaseManager` singleton initialises.
2. `DatabaseConnectionManager` reads `DatabaseConnection.xml`.
3. `DatabaseMetaDataManager` reads `custom_mapping.json`; creates empty `DatabaseDTO` per connection.

### First Table Access (Lazy Load)

1. Frontend calls `GET /api/tables?group=X&database=Y`.
2. `DatabaseMetaDataManager.getMetaData()` runs `lazyLoadFromDb()`:
   - Fetches table list via `DatabaseMetaData.getTables()`
   - Per table: fetches columns, indexes, FKs
   - Merges custom relations
   - Builds bidirectional FK graph
3. Result cached for all subsequent requests.

### FK Navigation

1. User clicks a `↗` FK value in `TableGrid`.
2. Frontend calls `GET /api/references?group=&database=&table=&column=&row=`.
3. Backend looks up `ColumnDTO.referTo`, generates `SELECT * FROM refTable WHERE refCol = ? LIMIT n`.
4. Returns `ResultSetDTO` list; frontend renders new result panel below.

### Row Mutation

1. User clicks Edit/Delete/Add Row → `RowModal` opens.
2. On confirm: `PUT /api/row/edit` or `DELETE /api/row` or `POST /api/row/add`.
3. Backend verifies `isUpdatableConnection()`/`isDeletableConnection()` before executing.

---

## Data Type Handlers

Custom rendering for specific column types. Register in `DataManager` by type name string.

| Handler | Type Name | Conversion |
|---------|-----------|------------|
| `IpDataHandler` | `ip` | `long` → `"a.b.c.d"` |
| `ShortDateDataHandler` | `short_date` | epoch ms → `"dd MMM yyyy"` |
| `LongDateDataHandler` | `long_date` | epoch ms → `"dd MMM yyyy HH:mm:ss"` |

To add a handler:
```java
DataManager.register("my_type", new MyHandler());
```

---

## Security

FkBlitz uses **Spring Security** with form-based session authentication.

- All `/api/**` endpoints except `/api/login` require an authenticated session.
- Unauthenticated API requests receive `401 {"error":"Unauthorized"}` — no redirect.
- CSRF is disabled (session cookie is `HttpOnly`; intended for same-origin browser use).
- CORS is configured to allow `http://localhost:5173` (Vite dev server) only.
- `UPDATABLE`/`DELETABLE` flags per connection control mutation exposure.
- Credentials in `DatabaseConnection.xml` are plaintext — restrict file permissions and use `/etc/fkblitz/` in production.

For team deployments: run behind a reverse proxy (nginx, Caddy) with your existing SSO/VPN layer. Do not expose directly to the internet.

---

## Dependencies

### Backend

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 3.3.x | Web framework, security, embedded Tomcat |
| spring-boot-starter-security | 3.3.x | Session auth |
| spring-boot-starter-actuator | 3.3.x | Health endpoints |
| mysql-connector-j | 9.1.0 | MySQL JDBC driver |
| mariadb-java-client | 3.4.x | MariaDB JDBC driver |
| org.json | 20240303 | JSON parsing for config |

### Frontend

| Library | Version | Purpose |
|---------|---------|---------|
| React | 18.x | UI framework |
| Vite | 8.x | Build tool and dev server |
| @vitejs/plugin-react | 6.x | React plugin for Vite |
| @tanstack/react-table | 8.x | Headless table with sorting/filtering |
| axios | 1.x | HTTP client |

---

## Build & Deployment

### Development

```sh
# Backend
cd backend && mvn spring-boot:run

# Frontend (separate terminal)
cd frontend && npm install && npm run dev
```

Frontend dev server at `http://localhost:5173` — proxies `/fkblitz` to `:9044`.

### Production (embedded)

```sh
cd frontend && npm run build   # outputs to frontend/dist/
cd ../backend && mvn package   # copies dist/ into JAR via maven-resources-plugin
java -jar target/*.jar
```

Single JAR serves both API and frontend at `http://localhost:9044/fkblitz/`.

### Docker

```sh
docker compose up --build
```

See `Dockerfile` (multi-stage: Node build → Maven build → JRE runtime) and `docker-compose.yml`.

### Config in production

```sh
sudo mkdir -p /etc/fkblitz
sudo cp DatabaseConnection.xml /etc/fkblitz/
sudo cp custom_mapping.json /etc/fkblitz/   # optional
```

Override credentials via environment variables:

```sh
SPRING_SECURITY_USER_NAME=admin
SPRING_SECURITY_USER_PASSWORD=<strong-password>
```
