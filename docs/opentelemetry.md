# OpenTelemetry Instrumentation — RKE Backend

This document describes the Phase 2 OpenTelemetry instrumentation added to the RKE Spring Boot backend.  
The goal is to generate real distributed traces that the [`rca-agent`](https://github.com/chaitanyamean/rca-agent) can later consume for Root Cause Analysis.

> **Phase scope**: trace generation and export only.  
> The OTEL Collector and Jaeger are not part of this phase (see Phase 3).

---

## Instrumentation Approach

RKE uses **Micrometer Tracing** as its tracing facade, wired to the **OpenTelemetry SDK** via the `micrometer-tracing-bridge-otel` bridge.  
This is the standard Spring Boot 3.x observability pattern — it does not require a Java agent.

```
HTTP Request
    │
    ▼
Spring MVC (auto-instrumented via Micrometer Observation)
    │
    ├── Manual span: sale.create  (SalesService)
    │       └── span attributes: sale.type, sale.farmer_id, sale.line_items, ...
    │
    ├── Manual span: farmer.create / farmer.update  (FarmerService)
    │       └── span attributes: farmer.id, farmer.village_id
    │
    └── JDBC spans (datasource-micrometer-spring-boot)
            └── One child span per DataSource operation
                    Attributes: db.system, db.statement, db.operation, db.name
```

### Libraries

| Library | Version | Role |
|---|---|---|
| `micrometer-tracing-bridge-otel` | 1.3.2 (BOM) | Bridges Micrometer Tracing API to OTEL SDK |
| `opentelemetry-sdk` | 1.37.0 (BOM) | Core OTEL SDK (via transitive dep) |
| `opentelemetry-exporter-otlp` | 1.37.0 (BOM) | OTLP/gRPC span exporter |
| `opentelemetry-sdk-extension-autoconfigure` | 1.37.0 (BOM) | SDK configuration from env vars |
| `datasource-micrometer-spring-boot` | 1.5.0 | JDBC DataSource observation auto-config |
| `datasource-micrometer-opentelemetry` | 1.5.0 | OTel semantic conventions for JDBC spans |

> `datasource-micrometer` **1.x** is required for Spring Boot 3.x.  
> Do **not** upgrade to 2.x until the application migrates to Spring Boot 4.

---

## Configuration

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | _(blank)_ | OTLP/gRPC collector endpoint (e.g. `http://otel-collector:4317`). Leave blank to disable export. |
| `OTEL_SERVICE_NAME` | `rke-backend` | Service name embedded in every span and log line. |
| `DEPLOYMENT_ENVIRONMENT` | `development` | Value of the `deployment.environment` resource attribute. |
| `OTEL_SAMPLING_PROBABILITY` | `1.0` | Fraction of requests to sample (0.0–1.0). Use `0.1` in high-traffic production. |

### application.yml Properties

```yaml
management:
  tracing:
    sampling:
      probability: ${OTEL_SAMPLING_PROBABILITY:1.0}
    propagation:
      type: W3C
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:}

otel:
  service:
    name: ${OTEL_SERVICE_NAME:rke-backend}
  deployment:
    environment: ${DEPLOYMENT_ENVIRONMENT:development}

jdbc:
  datasource-proxy:
    enabled: true
    include-parameter-values: false   # set true locally to see SQL values in spans
```

---

## SDK Initialisation (OpenTelemetryConfig)

`OpenTelemetryConfig.java` builds the SDK at startup:

- **Endpoint configured** → `OtlpGrpcSpanExporter` + `BatchSpanProcessor` — spans are exported over gRPC to the collector.
- **Endpoint blank** → SDK is initialised with no exporter — spans are created and logged locally but nothing is sent over the network.  
  The application starts and runs correctly in both modes.

Resource attributes set on every span:
- `service.name` — from `OTEL_SERVICE_NAME`
- `deployment.environment` — from `DEPLOYMENT_ENVIRONMENT`

### Interaction with the Java Agent

If the OpenTelemetry Java agent (`-javaagent:opentelemetry-javaagent.jar`) is attached at JVM startup, it registers itself as `GlobalOpenTelemetry`. Spring Boot's `@ConditionalOnMissingBean` skips the manual SDK and the agent's pipeline is used instead. The agent and the SDK never run simultaneously.

---

## Trace Propagation

**W3C TraceContext** (primary) + **W3C Baggage** + **B3 single-header** (compatibility).

Incoming requests with a `traceparent` header are automatically joined to the existing trace.  
Outgoing requests from any Spring `RestTemplate` or `WebClient` will carry the `traceparent` header if the app is later extended with those clients.

---

## JDBC Spans

`datasource-micrometer-spring-boot` wraps the HikariCP `DataSource` in an observation proxy at startup.  
Every connection acquisition and SQL statement execution produces a child span under the active request trace.

**Span attributes (OpenTelemetry semantic conventions):**

| Attribute | Example Value |
|---|---|
| `db.system` | `postgresql` |
| `db.operation` | `SELECT`, `INSERT`, `UPDATE` |
| `db.statement` | `select f1_0.id, f1_0.name ... from farmers f1_0` |
| `db.name` | `rke` |

SQL statement text is included in spans by default.  
Set `jdbc.datasource-proxy.include-parameter-values: false` (already the default) to exclude bind parameter values from the `db.statement` attribute in production.

---

## Manual Spans

### SalesService — `sale.create`

Wraps `createSale()` with a child span named `sale.create`.

| Span Attribute | Value |
|---|---|
| `sale.type` | `CASH_SALE` or `CREDIT_SALE` |
| `sale.farmer_id` | UUID of the farmer |
| `sale.line_items` | Number of line items in the request |
| `tenant.id` | UUID of the active tenant |
| `sale.bill_number` | Generated bill number (set after DB write) |
| `sale.transaction_id` | UUID of the created transaction |

Exceptions are recorded on the span (`span.error(e)`) before re-throwing, so errors appear in the trace UI.

### FarmerService — `farmer.create` / `farmer.update`

Wraps `create()` and `update()` with child spans.

| Span Attribute | Value |
|---|---|
| `farmer.id` | UUID of the farmer (set after save) |
| `farmer.village_id` | UUID of the village |

---

## Log Correlation

Every log line emitted inside an active span carries the trace context in the JSON output:

```json
{
  "timestamp": "2026-09-20T09:00:00.000Z",
  "level":     "INFO",
  "logger":    "c.r.b.service.SalesService",
  "message":   "Sale created: type=CASH_SALE billNumber=SALE-001 ...",
  "service":   "rke-backend",
  "traceId":   "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":    "00f067aa0ba902b7",
  "sampled":   "true"
}
```

**How it works:**  
`micrometer-tracing-bridge-otel` writes `traceId`, `spanId`, and `sampled` into SLF4J's MDC for every log statement inside a span.  
`LogstashEncoder` (`logback-spring.xml`) includes all MDC keys as top-level JSON fields automatically (`includeMdc=true`).  
`MdcJsonProvider` additionally promotes `traceId`, `spanId`, and `sampled` to the JSON root for compatibility with log aggregation tools that expect them there (Grafana Loki, OpenSearch, etc.).

**Using log correlation before the collector is running:**  
Even without an OTEL Collector, `traceId` appears in every log line. You can grep logs by `traceId` to see all log lines for a single request:

```bash
docker compose logs backend | python3 -c "
import sys, json
for line in sys.stdin:
    try:
        d = json.loads(line)
        if d.get('traceId') == '4bf92f3577b34da6a3ce929d0e0e4736':
            print(d.get('timestamp',''), d.get('level',''), d.get('message',''))
    except: pass
"
```

---

## Expected Trace Structure

A POST to `/api/sales/cash` produces a trace conceptually like:

```
POST /api/sales/cash  [HTTP server span — Spring MVC]
│   duration: ~15 ms
│   http.method: POST
│   http.route: /api/sales/cash
│   http.status_code: 201
│
├── sale.create  [manual span — SalesService]
│   │   sale.type: CASH_SALE
│   │   sale.farmer_id: <uuid>
│   │   sale.line_items: 2
│   │   tenant.id: <uuid>
│   │   sale.bill_number: SALE-001
│   │
│   ├── SELECT farmers  [JDBC span — datasource-micrometer]
│   │       db.system: postgresql
│   │       db.operation: SELECT
│   │
│   ├── SELECT bill_number_types  [JDBC span]
│   │
│   ├── SELECT items  [JDBC span — per item]
│   │
│   ├── SELECT next_bill_number()  [JDBC span — native query]
│   │
│   ├── INSERT transactions  [JDBC span]
│   │
│   └── INSERT transaction_items  [JDBC span — per item]
```

---

## Disabling Telemetry

### Disable export only (spans created, not sent)

Leave `OTEL_EXPORTER_OTLP_ENDPOINT` unset or empty (default behaviour locally).  
Spans are still created and `traceId`/`spanId` appear in logs — only the network export is skipped.

### Disable sampling (no spans created)

```bash
OTEL_SAMPLING_PROBABILITY=0.0
```

Or in `application.yml`:
```yaml
management:
  tracing:
    sampling:
      probability: 0.0
```

### Disable JDBC instrumentation

```yaml
jdbc:
  datasource-proxy:
    enabled: false
```

This removes the datasource-micrometer proxy from the `DataSource` bean.  
Normal application behaviour is unchanged.

---

## Running Locally

Start without a collector (traces logged, not exported):

```bash
./mvnw spring-boot:run -pl backend
# OTEL_EXPORTER_OTLP_ENDPOINT is blank by default — safe to run without a collector.
```

Start with a collector (e.g. Docker Compose when Phase 3 is implemented):

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
OTEL_SERVICE_NAME=rke-backend \
DEPLOYMENT_ENVIRONMENT=development \
./mvnw spring-boot:run -pl backend
```

Docker Compose already sets `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317` in the backend service environment — no code change is needed for Compose.

---

## Running Tests

Instrumentation does not affect test execution.  
Tests use Mockito mocks for `Tracer` and `Span` — no OTEL infrastructure is required to run tests.

```bash
./mvnw test -pl backend
```

Expected: all simulation tests pass (71), all OTEL-related tests pass.  
One pre-existing failure unrelated to OTEL: `SalesServiceTest.createSale_invalidItemInMiddleOfList_nothingPersisted`.

---

## File Inventory

```
backend/pom.xml
  + opentelemetry-exporter-otlp           (OTLP/gRPC span exporter)
  + opentelemetry-sdk-extension-autoconfigure  (env-var SDK config)
  + datasource-micrometer-spring-boot:1.5.0   (JDBC span autoconfigure)
  + datasource-micrometer-opentelemetry:1.5.0  (OTel semantic conventions)

backend/src/main/java/com/rke/backend/config/OpenTelemetryConfig.java
  Builds the OpenTelemetry SDK with conditional OTLP exporter.
  Resource attributes: service.name, deployment.environment.
  Propagators: W3C TraceContext + W3C Baggage + B3 single-header.

backend/src/main/resources/application.yml
  management.tracing.sampling.probability
  management.tracing.propagation.type
  management.otlp.tracing.endpoint
  otel.service.name / otel.deployment.environment
  jdbc.datasource-proxy.enabled

backend/src/main/resources/logback-spring.xml
  LogstashEncoder + includeMdc=true (JSON structured logs with traceId/spanId).
  MdcJsonProvider promoting traceId, spanId, sampled to JSON root.

backend/src/main/java/com/rke/backend/service/SalesService.java
  Tracer injected. sale.create manual span with sale attributes.

backend/src/main/java/com/rke/backend/service/FarmerService.java
  Tracer injected. farmer.create / farmer.update manual spans.

backend/src/test/java/com/rke/backend/service/SalesServiceTest.java
  Tracer + Span mocked to support the new constructor signature.
```

---

## What This Phase Does NOT Include

- OTEL Collector configuration (Phase 3)
- Jaeger, Grafana Tempo, or any trace backend (Phase 3)
- Metrics export (separate concern — `micrometer-registry-otlp` is available in the BOM)
- Log export via OTEL (logs are written to stdout; collection is an infra concern)
- RCA logic or incident memory (separate `rca-agent` repository)
