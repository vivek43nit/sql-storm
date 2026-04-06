# Migration Gaps: JSP/Servlet → Spring Boot + React SPA

Analysis of features present in the old JSP-based app (`a5d17b5^`) that are missing or incomplete in the current React frontend after the migration commit (`d097a1e`).

---

## Backend — Fully Ported

All core backend logic was successfully migrated. The three REST controllers cover everything the old JSPs handled server-side:

| Old JSP | New Endpoint |
|---|---|
| `execute.jsp` | `POST /api/execute` |
| `getReferences.jsp` | `GET /api/references` |
| `getDeReferences.jsp` | `GET /api/dereferences` |
| `traceRow.jsp` | `GET /api/trace` |
| `addRow.jsp` | `POST /api/row/add` |
| `editRow.jsp` | `PUT /api/row/edit` |
| `deleteRow.jsp` | `DELETE /api/row` |
| `groups.jsp` | `GET /api/groups` |
| `databases.jsp` | `GET /api/databases` |
| `tables.jsp` | `GET /api/tables` |
| `admin/relation/viewRelation.jsp` | `GET /api/admin/relations` |
| `admin/relation/viewSuggestions.jsp` | `GET /api/admin/suggestions` |

---

## Missing Frontend Features

### 1. Row CRUD — APIs exist, UI absent

**Old behaviour:** Each result table had per-row Edit and Delete buttons, plus an Add Row button in the table header. All three opened a modal dialog (field-per-column form for add/edit; confirmation prompt for delete).

**Current state:** `TableGrid.jsx` renders only a **Trace** button in the Actions column. `client.js` exports `addRow`, `editRow`, `deleteRow` but nothing in the UI calls them.

**What needs building:**
- Edit button per row → opens modal with pre-filled column inputs → calls `editRow(group, database, table, pk, pkValue, data)`
- Delete button per row → opens confirmation dialog → calls `deleteRow(group, database, table, pk, pkValue)`
- Add Row button in grid header → opens same modal with blank inputs → calls `addRow(group, database, table, data)`
- Primary key is already available via `resultSet` (populated by `enrichWithFkMetadata` on the backend)

---

### 2. Pagination Controls — Hardcoded, Not Configurable

**Old behaviour:** Top bar had two inputs — "Range: start → end" — which were passed as `LIMIT start, (end-start)` in every table-select query.

**Current state:** `handleTableSelect` in `MainPage.jsx` hardcodes `LIMIT 100`. `buildQuery` accepts a `limit` parameter but it is never exposed to the user.

**What needs building:**
- Two numeric inputs (default `0` / `100`) in the top controls bar
- Wire into `handleTableSelect` initial query and `buildQuery` re-queries

---

### 3. Configurable Reference Row Limit — Hardcoded at 100

**Old behaviour:** Top bar had a "References Rows Limit" input. Its value was passed as `refRowLimit` to `traceRow.jsp`, `getReferences.jsp`, and `getDeReferences.jsp`.

**Current state:** All three navigate calls in `TableGrid.jsx` and the corresponding `client.js` functions default to `refRowLimit = 100` with no way for the user to change it.

**What needs building:**
- A numeric input (default `100`) in the top controls bar
- Pass the value down through `MainPage` → `TableGrid` → the three `navigate` calls

---

### 4. Converter Utility Panel — Absent

**Old behaviour:** A "Converter" tile was embedded in the modal/dialog area, always accessible. It provided:
- IP string ↔ long (bidirectional, updated on `blur`)
- Date string (local time) ↔ epoch milliseconds UTC (bidirectional, updated on `blur`)
- Live server-time display (updated every second, paused on hover)

**Current state:** Not present anywhere in the new frontend.

**What needs building:**
- A collapsible panel or popover button in the header/toolbar
- IP ↔ long converter (client-side arithmetic, no API call needed)
- Date ↔ epoch converter (client-side, `new Date(...)`)
- Server time: either poll `Date.now()` adjusted by a server-client clock delta, or add a `GET /api/time` endpoint

---

### 5. Admin Pages — APIs exist, No Frontend

**Old behaviour:** A "View Relations" link in the top bar opened `admin/relation/viewRelation.jsp` (filterable table of FK column mappings per table) and a separate `viewSuggestions.jsp` page.

**Current state:** `GET /api/admin/relations` and `GET /api/admin/suggestions` are implemented in `MetaDataController.java` but there are no React pages or navigation links for them.

**What needs building:**
- `AdminRelationsPage.jsx` — group / database / table selectors + table of `{ table, column, referTo[], referencedBy[] }` rows
- `AdminSuggestionsPage.jsx` — group selector + nested list of suggested FK relations per database
- Link(s) in the top bar or sidebar (old app put "View Relations" as a right-aligned link in the top bar)

---

## New Features Not in the Old App

For reference, the migration also introduced things that did not exist before:

| Feature | Notes |
|---|---|
| Login / Logout | Old app used a servlet `SessionFilter`; there was no standalone login page |
| Per-column inline filters | Old app used a separate checkbox-based filter panel with a Search button |
| Per-column sort (click header) | Old app had a single global "Order By" text field + ASC/DESC dropdown |
| FK chip labels on result tables | Relation direction shown as `table.col → table.col` chip in grid header |
| FK column indicators (`↗` / `↙`) | Visual markers in column headers showing which columns have FK links |
