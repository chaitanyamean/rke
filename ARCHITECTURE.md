# Architecture

## Overview

RKE is a monorepo with a clear split between an API backend and a browser
frontend, backed by a relational database. Everything is orchestrated locally
with Docker Compose.

```
┌──────────────┐        HTTP (GET /api/health)        ┌──────────────┐
│   Frontend   │  ─────────────────────────────────▶  │   Backend    │
│ React + Vite │      http://localhost:8000            │ Spring Boot  │
│  (nginx)     │  ◀─────────────────────────────────  │  (Java 21)   │
│  host :3001  │                                       │  host :8000  │
└──────────────┘                                       └──────┬───────┘
                                                              │ JDBC
                                                              ▼
                                                       ┌──────────────┐
                                                       │  PostgreSQL  │
                                                       │  host :5433  │
                                                       └──────────────┘
```

## Components

### Backend (`/backend`)

- **Spring Boot 3.3.x** on **Java 21**, built with **Maven** (wrapper included).
- Dependencies: Spring Web, Spring Data JPA, PostgreSQL driver, Flyway,
  Lombok, Spring Validation, Spring Boot DevTools.
- Configuration in `application.yml` reads `DATABASE_URL`, `DB_USERNAME`, and
  `DB_PASSWORD` from the environment (with local-friendly defaults).
- **Flyway** runs on startup and applies migrations from
  `src/main/resources/db/migration`. `V1__init.sql` creates a placeholder
  table to prove the migration pipeline works.
- `GET /api/health` returns a small JSON payload (`status: "UP"`). CORS is
  enabled for `/api/**` so the browser frontend can call it cross-origin.
- Packaged as a fat JAR and shipped in a multi-stage Docker image
  (Maven build stage → slim JRE runtime, non-root user).
- Listens on `8080` inside the container, published on host `8000`.

### Frontend (`/frontend`)

- **React + TypeScript** scaffolded with **Vite**.
- **Tailwind CSS** for styling, **React Router** for routing, **TanStack
  Query** for server-state/data fetching, **Axios** for HTTP.
- `src/lib/api.ts` is the shared Axios client. Its base URL comes from
  `VITE_API_BASE_URL` (build-time), mapping to the documented `API_BASE_URL`.
- `HealthStatus` uses TanStack Query to call `/api/health` and renders
  **"Backend connected"** on success.
- Built to static assets and served by **nginx** in a multi-stage Docker image.
  Because the app is static and the calls originate in the browser, the API
  URL is baked in at build time via a Docker build arg.
- Published on host `3001` (avoids the common `3000` clash).

### Database (`postgres`)

- Official `postgres:16-alpine` image.
- Data persisted in a named Docker volume (`postgres_data`).
- A healthcheck (`pg_isready`) gates backend startup via
  `depends_on: condition: service_healthy`.

## Startup ordering

Docker Compose enforces the dependency chain:

1. `postgres` starts and becomes healthy.
2. `backend` starts, connects via JDBC, and runs Flyway migrations.
3. `frontend` starts and serves the static bundle.

## Configuration & secrets

- Root `.env.example` documents the database variables; `.env` is git-ignored.
- The frontend's build-time variable lives in `frontend/.env.example`.
- Compose provides sensible defaults so `make docker-up` works with zero
  manual configuration for local development.

## Notes / future work

- No domain/business logic yet — this is scaffolding only.
- JPA `ddl-auto` is set to `none`; schema changes should go through Flyway
  migrations rather than Hibernate auto-DDL.
- For production, secrets should come from a secret manager rather than `.env`,
  and the frontend API URL should be provided at build/deploy time per
  environment.
