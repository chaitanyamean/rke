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

- Root `.env.example` documents all environment variables; `.env` is git-ignored.
- The frontend's build-time variable lives in `frontend/.env.example`.
- Compose provides sensible defaults so `make docker-up` works with zero
  manual configuration for local development.
- For production, secrets should come from a secret manager rather than `.env`,
  and the frontend API URL should be provided at build/deploy time per
  environment.

---

## Multi-tenancy & isolation

### How tenant isolation is enforced

Three layers work together, each catching what the previous might miss:

1. **Hibernate tenant filter (primary guard)** — A `@FilterDef` in
   `domain/package-info.java` declares a named filter
   (`tenantFilter : tenant_id = :tenantId`). Every tenant-scoped JPA entity
   carries `@Filter(name = "tenantFilter")`. `TenantFilterAspect` activates the
   filter before any repository call by reading the current `TenantContext`
   (a per-request `ThreadLocal`). With the filter active, every SELECT Hibernate
   generates silently appends `AND tenant_id = ?`, so cross-tenant data leaks via
   accidental queries are structurally prevented.

2. **Service-level ownership checks (secondary guard)** — `findById` bypasses
   the Hibernate filter (it goes straight to a PK lookup). Every service method
   that calls `findById` manually verifies that the result's `tenant_id` matches
   the session tenant and throws `NotFoundException` otherwise (e.g.
   `VillageService.get()`, `FarmerService.get()`).

3. **`TenantScopeInterceptor` (HTTP-layer defence in depth)** — A Spring
   `HandlerInterceptor` registered on all `/api/**` paths (excluding auth and
   health). It enforces two rules before any handler is invoked:
   - `/api/admin/**` is rejected for any non-super_admin role (belt-and-suspenders
     on top of `@PreAuthorize`).
   - Any other path where `TenantContext.getTenantId()` is null (i.e. a
     super_admin without an active impersonation session trying to hit
     tenant-scoped endpoints) is rejected with 403 and a helpful message.

### Tenant context flow per request

```
Session cookie
  → Spring Security restores SecurityContext (StaffUserPrincipal)
    → TenantContextFilter (OncePerRequestFilter)
        - regular user    → TenantContext.setTenantId(principal.tenantId)
        - super_admin
            with session[IMPERSONATED_TENANT_ID] → setTenantId(impersonated)
            without                              → TenantContext stays null
      → TenantScopeInterceptor validates access
        → Controller → @Transactional Service
          → TenantFilterAspect activates Hibernate filter
            → Repository query with implicit tenant_id filter
```

---

## Tenant onboarding (end-to-end)

1. Super_admin logs in (username `superadmin`, no tenant slug needed).
2. Navigate to **⚙ Tenants → New Tenant**.
3. Fill name, slug (auto-generated), primary color, active toggle.
4. Optionally upload a logo (requires S3 credentials configured — see
   `S3_*` env vars in `.env.example`).
5. On save: a new row is inserted into `tenants`. No migration needed.
6. Add staff users for the new tenant (future: staff management UI; currently
   via direct DB insert with a bcrypt-hashed password or a seed migration).
7. Enable features via **Tenants → Features** toggle panel.

---

## Feature flags

### How a feature is gated

**Backend guard (`@RequiresFeature`):**
```java
@GetMapping("/api/cotton-lots")
@RequiresFeature("cotton_procurement")   // ← annotation on controller method
public List<CottonLot> list() { ... }
```
`FeatureGuardAspect` fires before the method, reads `TenantContext.getTenantId()`,
queries `tenant_features` (via `TenantFeatureService.isEnabled()`), and throws
403 if absent or disabled. Super_admin without impersonation bypasses this check.

**Frontend guard (UI visibility):**
```tsx
const { hasFeature } = useAuth()
{hasFeature('cotton_procurement') && <NavLink to="/cotton">Cotton</NavLink>}
```
`AuthContext` fetches `GET /api/features/mine` on login and page load, storing
the enabled key list in React state. `hasFeature(key)` is a simple
`Array.includes` check. The backend 403 is still the authoritative enforcement —
hiding the UI is defence in depth only.

### Adding a new feature key

1. No schema change needed — the `tenant_features` table stores arbitrary strings.
2. Add the entry to `KNOWN_FEATURES` in `TenantFeaturesPage.tsx` (label +
   description) so it appears in the super_admin toggle panel.
3. Annotate the backend endpoint(s) with `@RequiresFeature("your_key")`.
4. Add `{hasFeature('your_key') && ...}` in `Layout.tsx` nav and any route guards.

---

## Tenant branding (runtime theming)

The frontend uses a single static build that adapts to each tenant's branding
at runtime via CSS custom properties — no per-tenant build step required.

**Flow:**
1. After login, `AuthContext` fetches `GET /api/tenants/current` which returns
   the authenticated user's tenant (logo URL, primary color).
2. `applyBranding()` sets `document.documentElement.style.setProperty('--color-brand', color)`.
3. The Layout header uses `style={{ backgroundColor: 'var(--color-brand)' }}`.
4. Tailwind is extended with `colors: { brand: 'var(--color-brand)' }` so
   utility classes like `bg-brand` and `text-brand` reference the same variable.
5. The logo `<img>` in the header renders when `tenant.logoUrl` is set.

For a super_admin without impersonation, the default slate-800 color is applied.

---

## Super admin impersonation

Super_admin can "impersonate" a tenant to access and administer its data:

```
POST /api/admin/tenants/{id}/impersonate
```

This sets `session[IMPERSONATED_TENANT_ID] = tenantId` and logs the event to
`audit_log` (target tenant's context, action `update`, table `tenant_impersonation`).
Subsequent requests from that session behave exactly as if a tenant admin logged
in — the Hibernate filter is activated, all writes are scoped to that tenant.

```
DELETE /api/admin/impersonate
```

Clears the session attribute and logs an exit event.

The frontend shows an amber banner on the Tenant List page when impersonation is
active, with an "Exit impersonation" button.

---

## Object storage (logo uploads)

Logo files are uploaded to any S3-compatible bucket (AWS S3, Cloudflare R2, MinIO).
Configure via environment variables:

| Variable            | Description                                         |
|---------------------|-----------------------------------------------------|
| `S3_ENDPOINT`       | Custom endpoint URL (blank = use AWS default)       |
| `S3_REGION`         | AWS region (default: `us-east-1`)                   |
| `S3_ACCESS_KEY`     | Access key ID                                       |
| `S3_SECRET_KEY`     | Secret access key                                   |
| `S3_BUCKET`         | Bucket name (default: `rke-assets`)                 |
| `S3_PUBLIC_URL_BASE`| Base URL prepended to the object key for the public logo URL |

When `S3_ACCESS_KEY` is blank, the logo upload endpoint returns 501. The rest of
the application works normally.

## Notes

- JPA `ddl-auto` is set to `none`; schema changes go through Flyway migrations.
- The `next_bill_number(tenant_id, category_id)` Postgres function atomically
  increments the per-tenant sequence and returns a formatted bill number. It will
  be called from `TransactionService` in Phase 3.
