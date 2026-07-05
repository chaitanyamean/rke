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

### Collector (`otel-collector`)

- `otel/opentelemetry-collector-contrib`, configured by
  `otel-collector-config.yaml`.
- Receives OTLP over gRPC (`4317`) and HTTP (`4318`).
- Currently exports everything to its own console via the `debug` exporter —
  no external observability backend is chosen yet.

## Startup ordering

Docker Compose enforces the dependency chain:

1. `postgres` starts and becomes healthy; `otel-collector` starts.
2. `backend` starts, attaches the OTel agent, connects via JDBC, and runs
   Flyway migrations.
3. `frontend` starts and serves the static bundle.

## Observability (OpenTelemetry)

Tracing, metrics and logs are wired so that **every later phase gets
instrumentation for free** — no per-endpoint or per-query work is required.

### How it fits together

```
  backend JVM                          otel-collector                 (later)
┌──────────────────────┐   OTLP/gRPC  ┌──────────────┐   OTLP/HTTP   ┌────────────────┐
│ OTel Java agent      │ ───────────▶ │  otlp        │ ────────────▶ │ Grafana Cloud /│
│  + Micrometer bridge │  :4317       │  receiver    │  (swap in)    │ Tempo / Loki / │
│  + JSON logs (MDC)   │              │  → debug     │               │ Honeycomb ...  │
└──────────────────────┘              └──────────────┘               └────────────────┘
```

### Instrumented automatically (zero code) — via the OTel Java agent

The agent is attached in the backend image (`-javaagent`, wired through
`JAVA_TOOL_OPTIONS`). It instruments, at the bytecode level:

- **Inbound HTTP** — every Spring MVC request becomes a `SERVER` span
  (e.g. `GET /api/health` with `http.route`, status, etc.).
- **JPA / Hibernate** and the **JDBC / PostgreSQL** driver — every query
  becomes a `CLIENT` span (`SELECT ...`), nested under the request span.
- **trace_id / span_id in logs** — the agent injects `trace_id`, `span_id` and
  `trace_flags` into the SLF4J MDC, which `logback-spring.xml` renders as JSON
  fields. Any log line can therefore be correlated to the exact trace/request
  that produced it.

New controllers and repositories added in future phases are picked up
automatically — nothing to configure.

### Needs a manual span (opt-in) — via Micrometer Tracing

Auto-instrumentation covers the transport and persistence layers but not
*business* operations. For the money-handling flows (sales, payments, returns)
we want a named span around each `@Transactional` service method, so the ledger
write and everything it touches sits under one clearly named trace.

That is what `spring-boot-starter-actuator` + `micrometer-tracing-bridge-otel`
provide. `OpenTelemetryConfig` exposes the agent's `GlobalOpenTelemetry` as the
Spring `OpenTelemetry` bean, so Micrometer's `Tracer` uses the **agent's**
pipeline. Manual spans then:

- share trace context with the auto-instrumented HTTP/DB spans, and
- are exported by the agent (no second SDK, so no duplicate spans).

Example (added in a later phase, not now):

```java
// io.micrometer.tracing.annotation.NewSpan  — or an Observation / tracer.span()
@NewSpan("sale.record")
@Transactional
public Sale recordSale(SaleRequest request) { ... }
```

When run without the agent (e.g. `make dev`), `GlobalOpenTelemetry.get()`
returns a no-op, so these spans become cheap no-ops rather than failing.

### Configuration

Set on the backend service in `docker-compose.yml` (read natively by the agent):

| Variable                      | Value (placeholder)             | Purpose                        |
|-------------------------------|---------------------------------|--------------------------------|
| `OTEL_SERVICE_NAME`           | `rk-enterprises-backend`        | `service.name` on all telemetry|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4317`    | Where to send OTLP             |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc`                          | OTLP transport                 |
| `OTEL_{TRACES,METRICS,LOGS}_EXPORTER` | `otlp`                  | Enable all three signals       |

### Plugging in a real backend later

The application only ever speaks OTLP to the collector, so switching backends is
a **collector-only** change (no app rebuild):

1. Add an exporter in `otel-collector-config.yaml` (e.g. `otlphttp/grafana`,
   `otlp/honeycomb`) with its endpoint/credentials.
2. Add that exporter to the relevant pipeline's `exporters:` list (keep or drop
   `debug`).

A commented `otlphttp/grafana` example is already in the config as a starting
point.

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
