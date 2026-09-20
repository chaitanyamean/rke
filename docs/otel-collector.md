# OpenTelemetry Collector — RKE

This document covers the Phase 3 OpenTelemetry Collector setup for the RKE local development environment.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Docker Compose network                                          │
│                                                                  │
│  ┌─────────────────────┐   OTLP/gRPC      ┌──────────────────┐ │
│  │  RKE Spring Boot    │  port 4317  ───►  │  OTEL Collector  │ │
│  │  rke-backend        │                   │  rke-otel-       │ │
│  │                     │  OTLP/HTTP  ───►  │  collector       │ │
│  │  Micrometer Tracing │  port 4318        │                  │ │
│  │  + JDBC spans       │                   │  debug exporter  │ │
│  └─────────────────────┘                   │  → stdout        │ │
│                                            └──────────────────┘ │
│  ┌─────────────────────┐                                        │
│  │  PostgreSQL 16      │                                        │
│  │  rke-postgres       │                                        │
│  └─────────────────────┘                                        │
└─────────────────────────────────────────────────────────────────┘

Host access:
  Backend API   http://localhost:8000
  Collector gRPC  localhost:4317
  Collector HTTP  localhost:4318
  Collector health  http://localhost:13133/health
  Collector zpages  http://localhost:55679/debug/tracez
  Collector metrics http://localhost:8888/metrics
```

The collector currently exports received telemetry to its own stdout (the `debug` exporter). A trace backend (Jaeger) will be added in Phase 4 by uncommenting the `otlp/jaeger` exporter in `otel-collector-config.yaml`.

---

## Collector Image

```
otel/opentelemetry-collector-contrib:0.155.0
```

The `contrib` distribution is used because:
- It ships the Jaeger OTLP exporter needed for Phase 4, avoiding an image change.
- It includes all community processors (not needed now but available).
- It is the standard choice for most real-world deployments.

---

## Collector Configuration (`otel-collector-config.yaml`)

### Receivers

| Protocol | Port | Description |
|---|---|---|
| OTLP/gRPC | 4317 | Primary ingest — used by RKE |
| OTLP/HTTP | 4318 | Secondary — curl testing |

### Processors

Applied in order: `memory_limiter` → `batch` → `resource`

| Processor | Purpose |
|---|---|
| `memory_limiter` | Drops telemetry if RSS exceeds 256 MiB; backpressure at 192 MiB |
| `batch` | Groups spans into 512-span batches, flushed every 5 s |
| `resource` | Stamps `telemetry.environment=development` on all telemetry |

`memory_limiter` must always be first in the pipeline.

### Exporters

| Exporter | Status | Description |
|---|---|---|
| `debug` | **Active** | Prints full span/metric/log data to collector stdout |
| `otlp/jaeger` | Commented | Phase 4 — uncomment when Jaeger is added |

### Pipelines

All three signals (traces, metrics, logs) run through the same processor chain and export to `debug`.

### Extensions

| Extension | Port | Description |
|---|---|---|
| `health_check` | 13133 | `GET /health` — used by Docker healthcheck |
| `zpages` | 55679 | Browser-based trace/pipeline inspector |
| self-metrics | 8888 | Prometheus-format `/metrics` for collector internals |

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | _(blank)_ | Set to `http://otel-collector:4317` in Docker Compose |
| `OTEL_SERVICE_NAME` | `rke-backend` | Service name on every span |
| `DEPLOYMENT_ENVIRONMENT` | `development` | Stamped on spans by the resource processor |
| `OTEL_SAMPLING_PROBABILITY` | `1.0` | 100 % in dev; lower for production |

The RKE backend reads `OTEL_EXPORTER_OTLP_ENDPOINT` via `management.otlp.tracing.endpoint` in `application.yml`. No collector address is hardcoded in application code.

---

## Starting the Stack

### Full stack (recommended)

```bash
# Copy and customise env vars if needed
cp .env.example .env

# Start everything: postgres + otel-collector + backend + frontend
docker compose up
```

The backend will not start until the collector passes its health check (`service_healthy`).

### Collector only (for testing the config)

```bash
docker compose up otel-collector
```

### Backend without collector (local JVM dev)

When running outside Docker Compose, leave `OTEL_EXPORTER_OTLP_ENDPOINT` blank.  
The backend starts normally; `traceId`/`spanId` still appear in logs; no spans are exported.

```bash
cd backend
./mvnw spring-boot:run
```

---

## Verifying the Collector is Running

### 1. Health endpoint

```bash
curl -s http://localhost:13133/health
# → {"status":"Server available"}
```

### 2. Docker healthcheck

```bash
docker compose ps
# otel-collector should show "healthy" in the STATUS column
```

### 3. zpages UI

Open in a browser: [http://localhost:55679/debug/tracez](http://localhost:55679/debug/tracez)

This page shows live trace samples received by the collector, grouped by service and span name.

### 4. Collector stdout (debug exporter)

```bash
docker compose logs -f otel-collector
```

When RKE receives an HTTP request, you should see output like:

```
ScopeSpans #0
ScopeSpans SchemaURL:
InstrumentationScope io.micrometer.tracing 1.3.2
Span #0
    Trace ID       : 4bf92f3577b34da6a3ce929d0e0e4736
    Parent ID      :
    ID             : 00f067aa0ba902b7
    Name           : POST /api/sales/cash
    Kind           : Server
    Start time     : 2026-09-20 09:00:00.000000000 +0000 UTC
    End time       : 2026-09-20 09:00:00.015000000 +0000 UTC
    ...
```

---

## Verifying RKE Sends Traces

### 1. Make a real API request

```bash
# Login first
curl -s -c cookies.txt -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"your-password"}'

# Health endpoint (no auth required)
curl -s http://localhost:8000/api/health
```

### 2. Watch collector logs for spans

```bash
docker compose logs -f otel-collector | grep -E "Trace ID|Span #|Name\s+:"
```

You should see spans from the `rke-backend` service including:
- HTTP server spans from Spring MVC
- `sale.create` / `farmer.create` manual spans
- JDBC child spans from datasource-micrometer

### 3. Check backend logs for trace IDs

```bash
docker compose logs backend | python3 -c "
import sys, json
for line in sys.stdin:
    try:
        d = json.loads(line)
        if d.get('traceId'):
            print(f\"{d['timestamp']} [{d['level']}] traceId={d['traceId']} spanId={d['spanId']} {d['message'][:80]}\")
    except: pass
" 2>/dev/null | head -20
```

---

## Testing Controlled Failures with Tracing

All Phase 1 incident simulations still work with tracing active. Each simulated failure generates a trace that shows the failure path.

```bash
# INC-003 — backend exception (produces a trace with an error span)
curl -s -X POST http://localhost:8000/api/test/incidents/backend-error | python3 -m json.tool

# Then in the collector logs you should see a span with:
#   Status code : Error
#   Status message : [INC-003] Internal error during price calculation...
```

The `simulatedFailure: true` field in the error response and the `incidentId` tag on the span make simulation traces easily distinguishable from real failures.

### Reset

```bash
curl -s -X DELETE http://localhost:8000/api/test/incidents/reset
```

---

## Application Resilience — Collector Unavailability

The RKE backend uses a `BatchSpanProcessor` with an OTLP exporter. If the collector is unreachable:

- The exporter retries with exponential back-off (built into the OTLP exporter).
- Failed export attempts produce a WARN log — not an error or exception.
- Application requests continue to be served normally.
- Spans are still created and `traceId`/`spanId` appear in logs.
- No HTTP request fails or slows down due to a telemetry export failure.

To test this:

```bash
# Stop the collector while the backend is running
docker compose stop otel-collector

# Make a request — should succeed normally
curl -s http://localhost:8000/api/health

# Backend logs show a WARN about export failure, not an ERROR affecting requests
docker compose logs backend | grep -i "otlp\|export\|warn" | tail -5

# Restart the collector — export resumes automatically
docker compose start otel-collector
```

---

## Phase 4 Preview — Adding Jaeger

When Jaeger is added (Phase 4), the only changes required are:

1. Add Jaeger to `docker-compose.yml`.
2. Uncomment the `otlp/jaeger` exporter in `otel-collector-config.yaml`.
3. Add `otlp/jaeger` to the `traces` pipeline exporters list.
4. No application code changes are needed.

The commented Jaeger exporter block is already in `otel-collector-config.yaml`:

```yaml
# otlp/jaeger:
#   endpoint: jaeger:4317
#   tls:
#     insecure: true
```

---

## File Inventory

```
otel-collector-config.yaml        Collector configuration (receivers, processors, exporters, pipelines)
docker-compose.yml                 Updated: collector healthcheck, ports, backend OTEL env vars
.env.example                       Updated: OTEL_EXPORTER_OTLP_ENDPOINT and related vars documented
docs/otel-collector.md             This document
```

---

## Quick Reference

| Task | Command |
|---|---|
| Start full stack | `docker compose up` |
| View collector logs | `docker compose logs -f otel-collector` |
| Check collector health | `curl http://localhost:13133/health` |
| Browse trace samples | Open `http://localhost:55679/debug/tracez` |
| View collector self-metrics | `curl http://localhost:8888/metrics` |
| Trigger a test incident | `curl -X POST http://localhost:8000/api/test/incidents/backend-error` |
| Reset simulations | `curl -X DELETE http://localhost:8000/api/test/incidents/reset` |
| Stop collector only | `docker compose stop otel-collector` |
| Restart collector | `docker compose start otel-collector` |
