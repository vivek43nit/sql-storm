# FkBlitz — Foreign Key Database Browser

### _Blitz through your database by following foreign keys._

[![CI](https://github.com/vivek43nit/fkblitz/actions/workflows/ci.yml/badge.svg)](https://github.com/vivek43nit/fkblitz/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/Frontend-React%2018-61DAFB)](https://react.dev/)
[![MySQL](https://img.shields.io/badge/DB-MySQL%20%7C%20MariaDB-4479A1)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

**FkBlitz** is a self-hosted, browser-based MySQL/MariaDB client built for navigating relational data fast. Instead of writing JOINs, click a foreign key value and instantly see the referenced row — then keep clicking to traverse your entire data graph.

![FkBlitz demo — navigating foreign key relationships across MySQL tables](docs/screenshots/demo.gif)

> **Why FkBlitz?** FK = Foreign Key. Blitz = fast. No JOINs, no context switching, no SQL spelunking.

---

## Who Is This For?

- **Backend engineers** debugging production data and tracing rows across tables
- **Support & data teams** looking up related records without writing SQL
- **DBAs** exploring unfamiliar schemas and discovering undocumented relationships

---

## Features

- **FK navigation** — click to follow a foreign key forward; ↙ to see all rows that reference a value
- **Trace** — one click expands the full FK chain across all related tables
- **Custom relationships** — define soft references and cross-database joins not in the schema
- **Many-to-many support** — navigate through junction tables automatically
- **Multi-environment** — group connections by env (prod, staging, local) and switch in one click
- **Inline CRUD** — Add, Edit, Delete rows directly from the result grid
- **Column filters & sorting** — per-column filter inputs and clickable sort headers
- **Converter utility** — IP ↔ integer, date ↔ epoch ms, live server clock
- **RBAC auth** — ADMIN / READ_WRITE / READ_ONLY roles with multiple user-store backends
- **OAuth2/OIDC** — sign in with Google, GitHub, or any OIDC provider (optional)
- **Observability** — Prometheus metrics, structured JSON logging, Grafana dashboard
- **Kubernetes-ready** — Helm chart with HPA, health probes, and optional Redis

---

## Quick Start

### Docker Compose

```sh
git clone https://github.com/vivek43nit/fkblitz.git
cd fkblitz
docker compose up --build
```

Open **[http://localhost:9044/fkblitz/](http://localhost:9044/fkblitz/)** — default login: `admin` / `changeme`.

The stack starts FkBlitz, a sample MariaDB, Redis, Prometheus, and Grafana together.

### Point it at your own database

Edit `backend/src/main/resources/DatabaseConnection.xml`:

```xml
<CONNECTIONS CONNECTION_EXPIRY_TIME="3600000" MAX_RETRY_COUNT="10">
    <CONNECTION
        ID="1"
        GROUP="myenv"
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

Then `docker compose up --build` again.

### Local dev (without Docker)

**Requirements:** Java 17+, Maven 3.6+, Node.js 20+, a running MySQL or MariaDB.

```sh
# Backend
cd backend && mvn spring-boot:run

# Frontend (separate terminal)
cd frontend && npm install && npm run dev
```

Frontend dev server: [http://localhost:5173/](http://localhost:5173/)  
Production build served by Spring Boot: [http://localhost:9044/fkblitz/](http://localhost:9044/fkblitz/)

---

## Project Structure

```
fkblitz/
├── backend/          # Spring Boot 3 REST API (Java 17)
├── frontend/         # React 18 SPA (Vite + TanStack Table)
├── helm/fkblitz/     # Kubernetes Helm chart
├── docker/           # Prometheus config + Grafana dashboards
├── docker-compose.yml
└── Dockerfile        # Multi-arch (linux/amd64, linux/arm64)
```

---

## Production Deployment

For team deployments, authentication setup, Kubernetes, Redis, Prometheus, and all other production concerns — see the **[Enterprise Configuration Guide](docs/ENTERPRISE.md)**.

---

## Contributing

1. Fork and create a branch: `feat/my-feature`, `fix/my-bug`
2. Commit using [conventional commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, etc.
3. Open a focused PR — one concern per PR

---

## License

MIT License. See [LICENSE](LICENSE) for details.
