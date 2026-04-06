# FkBlitz

### _Blitz through your database by following foreign keys._

![Java](https://img.shields.io/badge/Java-11%2B-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F)
![React](https://img.shields.io/badge/Frontend-React%2018-61DAFB)
![Maven](https://img.shields.io/badge/Build-Maven-red)
![MySQL](https://img.shields.io/badge/DB-MySQL%20%7C%20MariaDB-4479A1)
![License](https://img.shields.io/badge/License-MIT-green)

A browser-based MySQL/MariaDB client that lets you navigate between tables by following foreign key relationships — both database-defined and custom-defined.

Instead of writing JOINs manually, FkBlitz builds a relationship graph from your schema and lets you click through related rows across tables and databases.

> **Why FkBlitz?** FK = Foreign Key — the core of what this tool navigates. Blitz = fast. No JOINs, no context switching, no SQL spelunking. Just click a value and blitz through your data relationships instantly.

---

## Features

- Browse multiple databases grouped by environment (production, staging, local, etc.)
- Navigate foreign key relationships in both directions (referTo / referencedBy)
- Define custom relationships not captured by DB foreign keys
- Support for many-to-many relationships through junction/mapping tables
- Per-row Edit, Delete, and Add Row with modal form (requires `UPDATABLE`/`DELETABLE` on connection)
- Configurable result range (start/end row pagination)
- Configurable reference row limit for FK navigation
- Converter utility: IP ↔ long, date ↔ epoch milliseconds, live clock
- Admin pages: view FK relations and suggested relations per database
- Per-column inline filters and sortable columns
- Session-based authentication via Spring Security

---

## Requirements

- Java 11+
- Maven 3.6+
- Node.js 18+ (for frontend development only)
- MySQL or MariaDB

---

## Quick Start

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
    />
</CONNECTIONS>
```

**2. Run the backend**

```sh
cd backend
mvn spring-boot:run
```

The API is available at [http://localhost:8080/fkblitz/api/](http://localhost:8080/fkblitz/api/)

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
| `UPDATABLE` | No | Set to `"true"` to allow row add and edit via the UI |
| `DELETABLE` | No | Set to `"true"` to allow row deletes via the UI |
| `NON_INDEXED_SEARCHABLE_ROW_LIMIT` | No | Row limit for searches on non-indexed columns |

### Custom Relationships — `custom_mapping.json` (optional)

Define relationships not captured by foreign keys in your schema. Useful for soft references, cross-database joins, or conditional relations.

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
          "referenced_column_name": "id",
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

**Mapping table types:** `ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_MANY`

### Authentication

FkBlitz uses Spring Security. Default credentials are configured in `application.properties`:

```properties
spring.security.user.name=admin
spring.security.user.password=secret
```

---

## Project Structure

```
fkblitz/
├── backend/                          # Spring Boot application
│   └── src/main/java/com/vivek/
│       ├── controller/               # REST controllers
│       │   ├── ExecuteController     # POST /api/execute
│       │   ├── MetaDataController    # groups, databases, tables, admin
│       │   └── RowMutationController # add, edit, delete row
│       ├── sqlstorm/
│       │   ├── DatabaseManager.java  # Main facade
│       │   ├── connection/           # Connection pooling
│       │   ├── metadata/             # Schema & relationship discovery
│       │   ├── config/               # Config parsing (XML/JSON)
│       │   └── dto/                  # Data transfer objects
│       └── utils/
└── frontend/                         # React SPA (Vite + TanStack Table)
    └── src/
        ├── api/client.js             # Axios API client
        ├── pages/
        │   ├── MainPage.jsx          # Main layout
        │   ├── LoginPage.jsx
        │   ├── AdminRelationsPage.jsx
        │   └── AdminSuggestionsPage.jsx
        └── components/
            ├── TableGrid.jsx         # Table with FK navigation & CRUD
            ├── RowModal.jsx          # Add/Edit/Delete modal
            ├── ConverterPanel.jsx    # IP↔long, date↔epoch, clock
            ├── NavPanel.jsx          # Sidebar
            └── QueryBar.jsx          # SQL editor bar
```

---

## Extending FkBlitz

**Add a custom data type renderer** — implement `DataHandler` and register it in `DataManager`:

```java
public class MyHandler implements DataHandler {
    public String convert(String value) {
        return ...; // transform the raw value for display
    }
}

DataManager.register("my_type_name", new MyHandler());
```

Built-in handlers: `ip` (integer → IPv4), `short_date`, `long_date` (epoch ms → human-readable).

**Add a new config format** — implement `ConfigParserInterface<T>` and register it in `ConfigParserFactory`.

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
