# Jaeger Trace Visualization — RKE

This document covers Phase 4: Jaeger as the local distributed trace backend.

---

## Core Concepts

Before using Jaeger it helps to understand what each term means in the RKE context.

| Term | Definition | RKE Example |
|---|---|---|
| **Trace** | The complete record of a single request as it moves through every service and component | One `POST /api/sales/cash` call from entry to database write |
| **Span** | A single named, timed operation within a trace | `sale.create`, `SELECT farmers`, `POST /api/sales/cash` |
| **Log** | A timestamped text event emitted by the application | `INFO Sale created: billNumber=SALE-001 traceId=4bf9...` |
| **Incident** | A real or simulated production failure | INC-001 pool exhaustion, INC-003 arithmetic exception |
| **RCA** | Root Cause Analysis — the process of investigating why an incident occurred | Correlating a Jaeger error span → stack trace → code change |

A **trace** is made of **spans**. A **log** line carries the same `traceId` as the span that produced it, linking logs to traces. An **incident** produces one or more traces that a future **RCA agent** will analyse.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Docker Compose network                                                   │
│                                                                           │
│  ┌─────────────────┐   OTLP/gRPC    ┌──────────────────┐               │
│  │  RKE Backend    │  port 4317  ──► │  OTEL Collector  │               │
│  │  rke-backend    │                 │  rke-otel-       │               │
│  │                 │                 │  collector       │               │
│  │  Micrometer     │                 │                  │               │
│  │  + JDBC spans   │                 │  processors:     │               │
│  └─────────────────┘                 │  memory_limiter  │               │
│                                      │  batch           │               │
│  ┌─────────────────┐                 │  resource        │               │
│  │  PostgreSQL 16  │                 │                  │               │
│  │  rke-postgres   │                 │  exporters:      │               │
│  └─────────────────┘                 │  debug (stdout)  │               │
│                                      │  otlp/jaeger ───►│──────────┐   │
│                                      └──────────────────┘          │   │
│                                                                     │   │
│                                      ┌──────────────────┐          │   │
│                                      │  Jaeger          │◄─────────┘   │
│                                      │  rke-jaeger      │ OTLP/gRPC    │
│                                      │  (all-in-one)    │ port 4317    │
│                                      │                  │ (internal)   │
│                                      └──────────────────┘              │
└─────────────────────────────────────────────────────────────────────────┘

Host access:
  Backend API      http://localhost:8000
  Jaeger UI        http://localhost:16686
  Collector health http://localhost:13133/health
  Collector zpages http://localhost:55679/debug/tracez
```

Jaeger's OTLP/gRPC port (4317) is **not published to the host**. Only the OTEL Collector can reach it, within the Docker network. This enforces the architecture: `RKE → Collector → Jaeger`. Direct RKE → Jaeger connections are structurally impossible.

---

## Starting the Stack

```bash
# Copy and customise variables if needed
cp .env.example .env

# Start all services: postgres, jaeger, otel-collector, backend, frontend
docker compose up

# Or start specific services for trace testing only
docker compose up postgres jaeger otel-collector backend
```

### Startup order (enforced by healthchecks)

```
postgres       (healthy: pg_isready)
    ↓
jaeger         (healthy: wget /  on port 16686)
    ↓
otel-collector (healthy: wget /health on port 13133)
    ↓
backend        (starts after collector is healthy)
```

The backend will not start until the full telemetry pipeline is ready.

---

## Jaeger UI

Open **http://localhost:16686** in a browser.

### Searching for traces

1. **Service** dropdown → select `rke-backend`
2. Optionally filter by **Operation** (e.g. `sale.create`, `POST /api/sales/cash`)
3. Set **Lookback** to `Last 15 minutes` (or wider for older traces)
4. Click **Find Traces**

Traces appear as horizontal bars, ordered newest-first.  
The bar width represents total trace duration. Wider bars indicate slower requests.

### Interpreting a trace

Click any trace to open the span waterfall view.

```
POST /api/sales/cash   [15 ms]  ← HTTP server span (Spring MVC)
└── sale.create        [13 ms]  ← Manual span (SalesService)
    ├── SELECT farmers  [2 ms]  ← JDBC span (datasource-micrometer)
    ├── SELECT bill_number_types [1 ms]
    ├── SELECT next_bill_number() [2 ms]
    ├── INSERT transactions [3 ms]
    └── INSERT transaction_items [2 ms]
```

**Span tags** — click any span to see its attributes:
- `sale.type`, `sale.farmer_id`, `sale.line_items` (on `sale.create`)
- `db.system=postgresql`, `db.operation=SELECT`, `db.statement=...` (on JDBC spans)
- `http.method`, `http.route`, `http.status_code` (on the HTTP span)
- `telemetry.environment=development` (stamped by the resource processor)

### Inspecting failed spans

Failed spans appear in **red** in the Jaeger UI.

Click a red span to see:
- `otel.status_code = ERROR`
- `otel.status_description` — the exception message
- **Logs tab** inside the span — shows the stack trace if the span recorded an error event

---

## Controlled Failure Traces

Use the Phase 1 incident simulations to generate realistic failure traces.

### Prerequisites

```bash
# Ensure the full stack is running
docker compose up postgres jaeger otel-collector backend
```

---

### INC-001 — PostgreSQL Connection Pool Exhaustion

**Trigger:**
```bash
curl -s -X POST http://localhost:8000/api/test/incidents/db-pool-exhaustion
# → HTTP 500 after ~3 s
```

**What to look for in Jaeger:**
1. Search `rke-backend` → Operation `POST /api/test/incidents/db-pool-exhaustion`
2. Open the trace — the top-level HTTP span will be red (error)
3. Inside the span, look for `otel.status_code = ERROR`
4. The `otel.status_description` contains: `[INC-001] Database connection pool exhausted`
5. The span duration is ~3 s (the probe timeout)

**RCA evidence this trace provides:**
- Duration spike (3 s vs normal <50 ms) signals connection pool saturation
- Error message identifies pool exhaustion explicitly
- The absence of JDBC child spans shows the failure happened before any DB work could begin

---

### INC-002 — Slow PostgreSQL Query

**Trigger:**
```bash
curl -s -X POST http://localhost:8000/api/test/incidents/slow-query
# → HTTP 500 after ~5 s
```

**What to look for in Jaeger:**
1. Search `rke-backend` → filter by duration > 4 s
2. Open the trace — the span waterfall shows a ~5 s total duration
3. Identify the JDBC span for `SELECT pg_sleep(?)` — it spans nearly the entire duration
4. The parent span (`POST /api/test/incidents/slow-query`) shows `ERROR` status

**RCA evidence this trace provides:**
- A single JDBC span accounting for >99 % of total request time
- `db.statement = SELECT pg_sleep(?)` identifies the exact query
- Duration clearly distinguishable from normal JDBC spans (ms vs seconds)

---

### INC-003 — Backend Application Exception

**Trigger:**
```bash
curl -s -X POST http://localhost:8000/api/test/incidents/backend-error
# → HTTP 500 immediately
```

**What to look for in Jaeger:**
1. Search `rke-backend` → Operation `POST /api/test/incidents/backend-error`
2. The HTTP span is red
3. Click the span → **Logs** tab shows the recorded error event
4. The error message includes: `[INC-003] Internal error during price calculation`
5. The `correlationId` in the message matches the log lines in `docker compose logs backend`

**RCA evidence this trace provides:**
- `otel.status_code = ERROR` on the outer span
- Exception message with `correlationId` enabling cross-referencing to application logs
- `ArithmeticException: integer overflow` in the cause chain
- Short duration (<50 ms) rules out latency/network as the cause — pure code failure

---

### Correlating a Trace to Application Logs

Every log line emitted by RKE inside an active span carries `traceId` and `spanId` as JSON fields.

1. Find a trace in Jaeger, copy the **Trace ID** (e.g. `4bf92f3577b34da6a3ce929d0e0e4736`)
2. Search the backend logs:

```bash
docker compose logs backend | python3 -c "
import sys, json
for line in sys.stdin:
    try:
        d = json.loads(line)
        if d.get('traceId') == '4bf92f3577b34da6a3ce929d0e0e4736':
            print(f\"{d['timestamp']} [{d['level']}] {d.get('message','')}\")
    except: pass
"
```

This cross-reference — trace in Jaeger + logs filtered by traceId — is the foundation of what the future RCA agent will use to investigate incidents.

---

### Reset all simulations

```bash
curl -s -X DELETE http://localhost:8000/api/test/incidents/reset
# → HTTP 204 No Content
```

---

## Verifying the Full Pipeline

```bash
# 1. Check Jaeger is healthy
curl -s http://localhost:16686/
# → HTML response (200 OK)

# 2. Check collector is healthy
curl -s http://localhost:13133/health
# → {"status":"Server available"}

# 3. Make a normal request to generate a trace
curl -s http://localhost:8000/api/health
# → {"status":"UP","db":"reachable",...}

# 4. Open Jaeger UI
# http://localhost:16686
# Service: rke-backend → Find Traces
# You should see a trace for GET /api/health with a JDBC child span (SELECT 1)
```

---

## Trace Retention

Jaeger `all-in-one` uses **in-memory storage** (badger).  
Traces are lost when the container restarts — this is intentional for local development.

Default retention: the last ~10 000 traces (configurable via `--memory.max-traces`).

To keep traces across restarts, add a named volume to the jaeger service in `docker-compose.yml`:

```yaml
jaeger:
  ...
  volumes:
    - jaeger_data:/tmp
volumes:
  jaeger_data:
```

---

## Quick Reference

| Task | Command / URL |
|---|---|
| Start full stack | `docker compose up` |
| Open Jaeger UI | http://localhost:16686 |
| Search rke-backend traces | UI → Service: rke-backend → Find Traces |
| Trigger INC-001 | `curl -X POST http://localhost:8000/api/test/incidents/db-pool-exhaustion` |
| Trigger INC-002 | `curl -X POST http://localhost:8000/api/test/incidents/slow-query` |
| Trigger INC-003 | `curl -X POST http://localhost:8000/api/test/incidents/backend-error` |
| Trigger INC-004 | Start with `SPRING_PROFILES_ACTIVE=dev,simulation`, then trigger |
| Trigger INC-005 | `curl -X POST http://localhost:8000/api/test/incidents/cascade` |
| Trigger INC-006 | `curl -X POST http://localhost:8000/api/test/incidents/historical` |
| Reset simulations | `curl -X DELETE http://localhost:8000/api/test/incidents/reset` |
| View collector logs | `docker compose logs -f otel-collector` |
| View backend logs | `docker compose logs -f backend` |
| Collector zpages | http://localhost:55679/debug/tracez |
| Collector health | http://localhost:13133/health |
| Stop stack | `docker compose down` |

---

## What This Phase Does NOT Include

- Persistent Jaeger storage (Cassandra, Elasticsearch, OpenSearch)
- Jaeger agent (deprecated; OTLP via collector is the current standard)
- Metrics visualization (Prometheus/Grafana — separate concern)
- RCA agent integration (separate `rca-agent` repository)
- Alert rules or notification routing
