# FkBlitz — Foreign Key Database Browser

### _Blitz through your database by following foreign keys._

[![CI](https://github.com/vivek43nit/fkblitz/actions/workflows/ci.yml/badge.svg)](https://github.com/vivek43nit/fkblitz/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-11%2B-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/Frontend-React%2018-61DAFB)](https://react.dev/)
[![Maven](https://img.shields.io/badge/Build-Maven-red)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/DB-MySQL%20%7C%20MariaDB-4479A1)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

**FkBlitz** is a self-hosted, browser-based MySQL/MariaDB client built for navigating relational data fast. Instead of writing JOINs, you click a foreign key value and instantly see the referenced row — then keep clicking to traverse your entire data graph.

It supports both schema-defined foreign keys and custom-defined relationships, so it works even when your FKs aren't enforced at the database level.

![FkBlitz demo — navigating foreign key relationships across MySQL tables](docs/screenshots/demo.gif)

> **Why FkBlitz?** FK = Foreign Key. Blitz = fast. No JOINs, no context switching, no SQL spelunking. Click a value, follow the relationship, done.

---

## Who Is This For?

- **Backend engineers** debugging production data and tracing rows across tables
- **Support & data teams** who need to look up related records without writing SQL
- **DBAs** exploring unfamiliar schemas and discovering undocumented relationships
- **Developers** building on top of legacy databases with complex FK graphs

---

## Features

- **FK navigation in both directions** — click a value to follow a foreign key forward; click ↙ to see all rows that reference a given row
- **Trace** — one click expands the full FK chain for a row across all related tables
- **Custom relationships** — define soft references and cross-database joins that aren't in the schema
- **Many-to-many support** — navigate through junction/mapping tables automatically
- **Multi-environment** — group connections by environment (production, staging, local) and switch in one click
- **Inline CRUD** — Add, Edit, and Delete rows directly from the result grid (per-connection opt-in)
- **Configurable pagination** — set start/end range and reference row limit from the top bar
- **Column filters & sorting** — per-column filter inputs and clickable sort headers
- **Converter utility** — IP address ↔ integer, date ↔ epoch ms, live server clock
- **Admin pages** — inspect and suggest FK relations per database
- **Session auth** — Spring Security session login; easy to put behind a VPN or reverse proxy

---

## vs. Other MySQL GUI Clients

| Feature | FkBlitz | DBeaver | TablePlus | DataGrip |
|---|---|---|---|---|
| FK click-through navigation | ✅ | ❌ | ❌ | ❌ |
| Custom / soft relationships | ✅ | ❌ | ❌ | ❌ |
| Web-based (shareable URL) | ✅ | ❌ | ❌ | ❌ |
| Self-hosted | ✅ | ✅ | ❌ | ❌ |
| Free & open source | ✅ | ✅ (CE) | ❌ | ❌ |
| No desktop install needed | ✅ | ❌ | ❌ | ❌ |

FkBlitz is purpose-built for **data exploration via relationships**. If you spend time writing `SELECT * FROM orders WHERE customer_id = ?` and then `SELECT * FROM customers WHERE id = ?`, FkBlitz eliminates that entirely.

---

## Quick Start

### Docker (recommended)

```sh
git clone https://github.com/vivek43nit/fkblitz.git
cd fkblitz
docker compose up --build
```

Open [http://localhost:8080/fkblitz/](http://localhost:8080/fkblitz/) — default login: `admin` / `changeme`.

Edit `backend/src/main/resources/DatabaseConnection.xml` to point at your database, then restart.

### Manual

```sh
git clone https://github.com/vivek43nit/fkblitz.git
cd fkblitz
```

**1. Configure your database connections**

```sh
sudo mkdir -p /etc/fkblitz
sudo cp backend/src/main/resources/DatabaseConnection.xml /etc/fkblitz/DatabaseConnection.xml
```

Edit `/etc/fkblitz/DatabaseConnection.xml`:

```xml
<CONNECTIONS CONNECTION_EXPIRY_TIME="3600000" MAX_RETRY_COUNT="10">
    <CONNECTION
        ID="1"
        GROUP="localhost"
        DB_NAME="mydb"
        DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver"
        DATABASE_URL="jdbc:mysql://localhost:3306/mydb?useInformationSchema=true"
        USER_NAME="root"
        PASSWORD="secret"
        UPDATABLE="true"
        DELETABLE="true"
    />
</CONNECTIONS>
```

**2. Run the backend**

```sh
cd backend
mvn spring-boot:run
```

**3. Run the frontend (development)**

```sh
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173/](http://localhost:5173/)

**4. Production build**

```sh
cd frontend && npm run build
cd ../backend && mvn spring-boot:run
```

The built frontend is served by Spring Boot at [http://localhost:8080/fkblitz/](http://localhost:8080/fkblitz/)

---

## Requirements

- Java 11+
- Maven 3.6+
- MySQL or MariaDB
- Node.js 18+ (frontend development only)

---

## Configuration

### Database Connections — `DatabaseConnection.xml`

FkBlitz looks for this file in the following order:

1. `/etc/fkblitz/DatabaseConnection.xml`
2. `~/.fkblitz/DatabaseConnection.xml`
3. `~/DatabaseConnection.xml`
4. Classpath fallback (bundled sample)

| Attribute | Required | Description |
|-----------|----------|-------------|
| `ID` | Yes | Unique identifier for this connection |
| `GROUP` | Yes | Environment label shown in the UI (e.g. `production`, `localhost`) |
| `DB_NAME` | Yes | Display name for the database |
| `DRIVER_CLASS_NAME` | Yes | JDBC driver class (`com.mysql.cj.jdbc.Driver` or `org.mariadb.jdbc.Driver`) |
| `DATABASE_URL` | Yes | JDBC connection URL |
| `USER_NAME` | Yes | Database username |
| `PASSWORD` | Yes | Database password |
| `UPDATABLE` | No | `"true"` to allow Add Row and Edit via the UI |
| `DELETABLE` | No | `"true"` to allow Delete via the UI |
| `NON_INDEXED_SEARCHABLE_ROW_LIMIT` | No | Row limit for searches on non-indexed columns |

### Custom Relationships — `custom_mapping.json` (optional)

Define relationships not captured by foreign keys in your schema — soft references, cross-database joins, or conditional relations.

```sh
sudo cp backend/src/main/resources/custom_mapping.json /etc/fkblitz/custom_mapping.json
```

```json
{
  "databases": {
    "mydb": {
      "relations": [
        {
          "table_name": "orders",
          "table_column": "customer_id",
          "referenced_table_name": "customers",
          "referenced_column_name": "id"
        }
      ],
      "mapping_tables": {
        "order_tags": {
          "type": "MANY_TO_MANY",
          "from": "order_id",
          "to": "tag_id"
        }
      }
    }
  }
}
```

**Mapping table types:** `ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_MANY`

### Authentication

Default credentials are set in `application.yml`:

```yaml
spring:
  security:
    user:
      name: admin
      password: changeme
```

Change before deploying. For team use, put FkBlitz behind a reverse proxy with your existing SSO.

---

## Project Structure

```
fkblitz/
├── backend/                          # Spring Boot REST API
│   └── src/main/java/com/vivek/
│       ├── controller/
│       │   ├── ExecuteController     # POST /api/execute
│       │   ├── MetaDataController    # groups, databases, tables, admin
│       │   └── RowMutationController # add, edit, delete row
│       └── sqlstorm/
│           ├── DatabaseManager.java  # Main facade
│           ├── connection/           # Connection pooling
│           ├── metadata/             # Schema & FK discovery
│           └── config/               # XML/JSON config parsing
└── frontend/                         # React SPA (Vite + TanStack Table)
    └── src/
        ├── api/client.js             # Axios API client
        ├── pages/
        │   ├── MainPage.jsx          # Main layout
        │   ├── LoginPage.jsx
        │   ├── AdminRelationsPage.jsx
        │   └── AdminSuggestionsPage.jsx
        └── components/
            ├── TableGrid.jsx         # FK navigation, CRUD, filters
            ├── RowModal.jsx          # Add/Edit/Delete modal
            ├── ConverterPanel.jsx    # IP↔long, date↔epoch, clock
            ├── NavPanel.jsx          # Sidebar
            └── QueryBar.jsx          # SQL editor bar
```

---

## Frequently Asked Questions

**Does it work with databases that don't enforce foreign keys?**
Yes. FkBlitz reads FK metadata via `INFORMATION_SCHEMA`, but you can also define any relationship manually in `custom_mapping.json`. It works fine on tables with no FKs at all.

**Is it safe to expose to the internet?**
FkBlitz has session auth but is not hardened for public internet exposure. For team use, run it on a private network or behind a VPN/reverse proxy with your own auth layer.

**Can I connect to multiple databases at once?**
Yes. Add multiple `<CONNECTION>` entries with different `GROUP` values. Switch between them from the sidebar.

**Does it support PostgreSQL or SQLite?**
Not yet. MySQL and MariaDB are currently supported. PostgreSQL support would require adding a JDBC driver and testing `INFORMATION_SCHEMA` compatibility.

**Can I use it read-only?**
Yes. Omit `UPDATABLE` and `DELETABLE` from your connection config (or set them to `false`) and the Add/Edit/Delete buttons will not appear.

---

## Extending FkBlitz

**Add a custom data type renderer** — implement `DataHandler` and register it in `DataManager`:

```java
public class MyHandler implements DataHandler {
    public String convert(String value) {
        return ...; // transform raw value for display
    }
}
DataManager.register("my_type_name", new MyHandler());
```

Built-in handlers: `ip` (integer → IPv4), `short_date`, `long_date` (epoch ms → human-readable).

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Open a pull request

Please keep PRs focused — one feature or fix per PR.

---

## License

MIT License. See [LICENSE](LICENSE) for details.
