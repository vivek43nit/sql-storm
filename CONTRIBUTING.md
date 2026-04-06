# Contributing to FkBlitz

Thanks for taking the time to contribute!

## Getting Started

1. Fork the repo and clone it locally
2. Set up the backend: `cd backend && mvn spring-boot:run`
3. Set up the frontend: `cd frontend && npm install && npm run dev`
4. Open [http://localhost:5173](http://localhost:5173) — you'll need a MySQL/MariaDB instance and a `DatabaseConnection.xml` (see README)

## Ways to Contribute

- **Bug reports** — use the bug report issue template
- **Feature requests** — use the feature request issue template
- **Code** — open a PR (see below)
- **Documentation** — README improvements, better examples, typos

## Pull Request Guidelines

- One feature or fix per PR — keep diffs small and reviewable
- Test against a real MySQL or MariaDB database before opening
- Update README.md if you change any user-facing behaviour
- Match the existing code style (no formatter config enforced, just be consistent)

## Project Structure

```
backend/   Spring Boot REST API (Java 17, Maven)
frontend/  React SPA (Vite, TanStack Table, Axios)
```

Backend entry point: `backend/src/main/java/com/vivek/SqlStormApplication.java`  
Frontend entry point: `frontend/src/main.jsx`

## Running Tests

```sh
# Backend
cd backend && mvn verify

# Frontend (build check)
cd frontend && npm run build
```

## Commit Messages

Use plain imperative sentences: `Add PostgreSQL driver support`, `Fix FK detection on views`, `Update README quick start`.
