# Changelog

All notable changes to FkBlitz are documented here.

## [Unreleased]

## [1.0.0] — 2026-04-07

### Added
- React SPA frontend (migrated from JSP)
- FK navigation in both directions (follow FK forward, show referencing rows)
- Trace view: expand full FK chain for a row across all related tables
- Per-row Add, Edit, Delete with modal form (requires `UPDATABLE`/`DELETABLE` on connection)
- Configurable result range and reference row limit in the top bar
- Converter panel: IP ↔ integer, date ↔ epoch ms, live server clock
- Admin pages: view and suggest FK relations per database
- Custom relationship definitions via `custom_mapping.json`
- Many-to-many junction table support
- Multi-environment connection groups
- Session authentication via Spring Security
- Demo GIF and SEO-optimised README
- Docker + docker-compose support
- CI workflow (GitHub Actions)

### Changed
- Renamed project from sql-storm to FkBlitz
- Migrated backend to Spring Boot 3 REST API

### Security
- Fixed XXE vulnerability in XML config parser
- Upgraded protobuf-java (CVE-2024-7254)
- Upgraded logback (CVE)
