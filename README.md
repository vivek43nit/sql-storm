# SQL-Storm

![Java](https://img.shields.io/badge/Java-11%2B-blue)
![Maven](https://img.shields.io/badge/Build-Maven-red)
![Tomcat](https://img.shields.io/badge/Server-Tomcat%209-yellow)
![MySQL](https://img.shields.io/badge/DB-MySQL%20%7C%20MariaDB-4479A1)
![License](https://img.shields.io/badge/License-MIT-green)

A browser-based MySQL/MariaDB client that lets you navigate between tables by following foreign key relationships — both database-defined and custom-defined.

Instead of writing JOINs manually, SQL-Storm builds a relationship graph from your schema and lets you click through related rows across tables and databases.

---

## Features

- Browse multiple databases grouped by environment (production, staging, local, etc.)
- Navigate foreign key relationships in both directions (referTo / referencedBy)
- Define custom relationships not captured by DB foreign keys
- Support for many-to-many relationships through junction/mapping tables
- Optional row-level edit and delete per connection
- Custom data type rendering (IP addresses, timestamps)
- Optional session-based authentication

---

## Requirements

- Java 11+
- Maven 3.6+
- MySQL or MariaDB
- Apache Tomcat 9 (or use the embedded Tomcat via Maven)

---

## Quick Start

```sh
git clone https://github.com/vivek43nit/sql-storm.git
cd sql-storm
```

**1. Configure your database connections**

Copy the sample config and add your connections:

```sh
sudo mkdir -p /etc/sql-storm
sudo cp src/main/resources/DatabaseConnection.xml /etc/sql-storm/DatabaseConnection.xml
```

Edit `/etc/sql-storm/DatabaseConnection.xml`:

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

**2. Run with embedded Tomcat**

```sh
mvn tomcat7:run
```

Open [http://localhost:9044/sql-storm/](http://localhost:9044/sql-storm/)

---

## Configuration

### Database Connections — `DatabaseConnection.xml`

SQL-Storm looks for this file in the following order:

1. `/etc/sql-storm/DatabaseConnection.xml`
2. `~/.sql-storm/DatabaseConnection.xml`
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
| `UPDATABLE` | No | Set to `"true"` to allow row edits via the UI |
| `DELETABLE` | No | Set to `"true"` to allow row deletes via the UI |
| `NON_INDEXED_SEARCHABLE_ROW_LIMIT` | No | Row limit for searches on non-indexed columns |

### Custom Relationships — `custom_mapping.json` (optional)

Define relationships that aren't captured by foreign keys in your schema. Useful for soft references, cross-database joins, or conditional relations.

```sh
sudo cp src/main/resources/custom_mapping.json /etc/sql-storm/custom_mapping.json
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

### Authentication (optional)

By default SQL-Storm is open. To enable session-based authentication, uncomment and configure the `SessionFilter` in `src/main/webapp/WEB-INF/web.xml`:

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

---

## Deployment

**Build a WAR file:**

```sh
mvn clean install
```

Deploy `target/sql-storm.war` to your Tomcat `webapps/` directory. The app will be available at `/sql-storm/`.

See the [Tomcat deployment guide](https://tomcat.apache.org/tomcat-9.0-doc/deployer-howto.html) for details.

**Logging:**

Logs are written to `/var/log/sql-storm.log` by default. Ensure the Tomcat process has write permission, or override by placing a `logback.xml` on the classpath.

---

## Project Structure

```
src/main/
├── java/com/vivek/
│   ├── filter/              # Session authentication filter
│   ├── sqlstorm/
│   │   ├── DatabaseManager.java          # Main facade
│   │   ├── connection/                   # Connection pooling
│   │   ├── metadata/                     # Schema & relationship discovery
│   │   ├── config/                       # Config parsing (XML/JSON)
│   │   ├── datahandler/                  # Custom data type converters
│   │   └── dto/                          # Data transfer objects
│   └── utils/                            # Shared utilities
├── resources/
│   ├── DatabaseConnection.xml            # Connection config template
│   ├── custom_mapping.json               # Custom relation config template
│   └── logback.xml                       # Logging config
└── webapp/mysql/                         # JSP pages + JS/CSS
```

---

## Extending SQL-Storm

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
