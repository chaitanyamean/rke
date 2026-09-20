# RKE Incident Dataset

Formal dataset of controlled incident scenarios for RCA Agent testing.

Each incident is deterministic and reproducible.
All scenarios execute through the **real RKE application path** and produce **real OpenTelemetry telemetry**.

---

## How to Start the Stack

```bash
docker compose up
```

Wait until all services are healthy:
- PostgreSQL: `pg_isready`
- Jaeger: `wget http://localhost:16686/`
- OTEL Collector: `wget http://localhost:13133/health`
- Backend: `GET http://localhost:8000/api/health`

The backend will not start until the full telemetry pipeline is ready.

---

## INC-001 — PostgreSQL Connection Pool Exhaustion

| Field | Value |
|---|---|
| **Incident ID** | `INC-001` |
| **Scenario** | `database_pool_exhaustion` |
| **Trigger endpoint** | `POST /api/test/incidents/db-pool-exhaustion` |
| **Affected component** | HikariCP connection pool |
| **Expected service** | `rke-backend` |
| **Known root cause** | HikariCP pool exhausted: 4 threads hold connections simultaneously, probe connection times out after 3 000 ms |
| **Category** | Infrastructure |

### Trigger

```bash
curl -s -X POST http://localhost:8000/api/test/incidents/db-pool-exhaustion
# → HTTP 500, ~3 s delay
```

### Expected Trace Pattern

```
POST /api/test/incidents/db-pool-exhaustion  [ERROR, ~3000ms]
└── DbPoolExhaustionScenario.run()
    ├── HikariCP holder threads × 4  [concurrent]
    └── Probe connection attempt  [ERROR: timeout after 3000ms]
```

- Root span status: `ERROR`
- `otel.status_code = ERROR`
- `otel.status_description` contains: `Database connection pool exhausted — all 4 connections held`
- Duration: ~3 000 ms (probe timeout)
- No JDBC child spans (pool exhausted before any query executes)

### Expected Log Pattern

```json
{"level":"WARN","message":"Pool exhaustion scenario starting: saturating connection pool with 4 holders","incidentId":"INC-001"}
{"level":"WARN","message":"Probe connection timed out as expected: ...","incidentId":"INC-001"}
{"level":"ERROR","message":"[INC-001] Database connection pool exhausted — all 4 connections held by concurrent requests..."}
```

### How to Reproduce

1. `POST /api/test/incidents/db-pool-exhaustion`
2. Observe Jaeger: service `rke-backend`, operation `POST /api/test/incidents/db-pool-exhaustion`
3. Confirm root span is red (ERROR)
4. Reset: `DELETE /api/test/incidents/reset`

### Evidence Expected in RCA Agent

- HTTP 500 on the trigger endpoint
- Span duration ~3 s (anomalous vs normal ~10 ms)
- Error message mentioning `HikariPool` and `timed out`
- Log entries with `traceId` matching the Jaeger trace

---

## INC-002 — Slow PostgreSQL Query

| Field | Value |
|---|---|
| **Incident ID** | `INC-002` |
| **Scenario** | `slow_query` |
| **Trigger endpoint** | `POST /api/test/incidents/slow-query` |
| **Affected component** | PostgreSQL / JDBC layer |
| **Expected service** | `rke-backend` |
| **Known root cause** | `SELECT pg_sleep(5)` executed against PostgreSQL, causing 5-second query latency followed by a simulated timeout exception |
| **Category** | Database / Latency |

### Trigger

```bash
curl -s -X POST http://localhost:8000/api/test/incidents/slow-query
# → HTTP 500, ~5 s delay
```

### Expected Trace Pattern

```
POST /api/test/incidents/slow-query  [ERROR, ~5000ms]
└── SELECT pg_sleep(?)  [JDBC span, ~5000ms]
    db.system = postgresql
    db.statement = SELECT pg_sleep(?)
    db.operation = SELECT
```

- Root span status: `ERROR`
- JDBC child span visible: `SELECT pg_sleep(?)`
- JDBC span duration: ~5 000 ms — visually distinguishable from normal spans (~1 ms)
- Error: `Simulated query timeout: database operation took N ms`

### Expected Log Pattern

```json
{"level":"WARN","message":"Slow query scenario starting: executing pg_sleep(5) against database","incidentId":"INC-002"}
{"level":"WARN","message":"Slow query completed in 5012ms — simulated DB latency spike","incidentId":"INC-002"}
{"level":"ERROR","message":"[INC-002] Simulated query timeout: database operation took 5012ms ..."}
```

### How to Reproduce

1. `POST /api/test/incidents/slow-query`
2. Observe Jaeger: look for JDBC child span `SELECT pg_sleep(?)` spanning ~5 s
3. Reset: `DELETE /api/test/incidents/reset`

---

## INC-003 — Backend Application Exception / HTTP 500

| Field | Value |
|---|---|
| **Incident ID** | `INC-003` |
| **Scenario** | `backend_exception` |
| **Trigger endpoint** | `POST /api/test/incidents/backend-error` |
| **Affected component** | Price calculation service path |
| **Expected service** | `rke-backend` |
| **Known root cause** | `ArithmeticException: integer overflow` in price calculation (`Math.addExact(Integer.MAX_VALUE, 1)`) wrapped in `SimulationException` |
| **Category** | Code bug |

### Trigger

```bash
curl -s -X POST http://localhost:8000/api/test/incidents/backend-error
# → HTTP 500, < 50 ms
```

### Expected Trace Pattern

```
POST /api/test/incidents/backend-error  [ERROR, <50ms]
```

- Root span status: `ERROR` — very fast (no DB operation)
- `otel.status_description` contains: `Internal error during price calculation for order correlationId=...`
- Exception cause: `java.lang.ArithmeticException: integer overflow`
- Short duration clearly rules out latency/network as cause

### Expected Log Pattern

```json
{"level":"INFO","message":"Processing order — starting inventory check correlationId=abc123","incidentId":"INC-003"}
{"level":"INFO","message":"Inventory check passed: inStock=true correlationId=abc123"}
{"level":"ERROR","message":"Price calculation failed: integer overflow correlationId=abc123","exception":"java.lang.ArithmeticException","traceId":"..."}
{"level":"ERROR","message":"[INC-003] Internal error during price calculation for order correlationId=abc123..."}
```

### How to Reproduce

1. `POST /api/test/incidents/backend-error`
2. Observe Jaeger: error span, short duration, check exception details in span logs tab
3. Correlate `correlationId` in Jaeger span description with the same `correlationId` in backend logs
4. Reset: `DELETE /api/test/incidents/reset`

---

## INC-004 — Configuration Regression

| Field | Value |
|---|---|
| **Incident ID** | `INC-004` |
| **Scenario** | `config_regression` |
| **Trigger endpoint** | `POST /api/test/incidents/config-regression` |
| **Affected component** | Order validation configuration |
| **Expected service** | `rke-backend` |
| **Known root cause** | `simulation.config-regression.max-items-per-order = 0` (set in `application-simulation.yml`) causes all order validations to fail |
| **Category** | Configuration change |

### Trigger (requires simulation profile)

```bash
# Step 1: start with the simulation profile active
SPRING_PROFILES_ACTIVE=dev,simulation ./mvnw spring-boot:run -pl backend

# Step 2: trigger
curl -s -X POST http://localhost:8000/api/test/incidents/config-regression
# → HTTP 500 (regression active)
```

### Git-Visible Change

The file `backend/src/main/resources/application-simulation.yml` contains `max-items-per-order: 0`.
The diff between `application.yml` (value: 50) and `application-simulation.yml` (value: 0) is the change the RCA Agent should correlate against the incident.

### Expected Trace Pattern

```
POST /api/test/incidents/config-regression  [ERROR, <50ms]
```

- Status: `ERROR`
- Error message: `CONFIGURATION REGRESSION DETECTED: max-items-per-order=0`
- No DB spans (validation fails before any DB work)

### Expected Log Pattern

```json
{"level":"WARN","message":"Config regression check: simulation.config-regression.max-items-per-order=0","incidentId":"INC-004"}
{"level":"ERROR","message":"CONFIGURATION REGRESSION DETECTED: max-items-per-order=0 (expected > 0). Orders will be rejected as 'empty'. Check recent Git changes to application-simulation.yml.","traceId":"..."}
```

### How to Reproduce

1. Start app with `SPRING_PROFILES_ACTIVE=dev,simulation`
2. `POST /api/test/incidents/config-regression`
3. Observe the `CONFIGURATION REGRESSION DETECTED` error in Jaeger and logs
4. Correlate with the `application-simulation.yml` Git diff

---

## INC-005 — Cascading Failure

| Field | Value |
|---|---|
| **Incident ID** | `INC-005` |
| **Scenario** | `cascade_failure` |
| **Trigger endpoint** | `POST /api/test/incidents/cascade` |
| **Affected component** | PricingService → RatingEngine dependency chain |
| **Expected service** | `rke-backend` |
| **Known root cause** | Three-layer dependency chain: IncidentController → PricingService → RatingEngine → `IOException` (simulated network timeout). Each layer logs its failure independently. |
| **Category** | Dependency failure / Cascade |

### Trigger

```bash
curl -s -X POST http://localhost:8000/api/test/incidents/cascade
# → HTTP 500, ~1.5 s delay
```

### Expected Trace Pattern

```
POST /api/test/incidents/cascade  [ERROR, ~1500ms]
```

- 3 × 500 ms sleep layers visible in total duration (~1.5 s)
- Root cause chain in exception: `SimulationException` → `RuntimeException([PricingService])` → `IOException([RatingEngine])`

### Expected Log Pattern

Three nested ERROR log lines showing cascade propagation:

```json
{"level":"ERROR","message":"[INC-005] [RatingEngine] Connection to external rate feed timed out after 500ms","traceId":"..."}
{"level":"ERROR","message":"[INC-005] [PricingService] Downstream failure from RatingEngine: Connection timeout reaching external rate feed (simulated)","traceId":"..."}
{"level":"ERROR","message":"[INC-005] [IncidentController] Upstream failure: PricingService unavailable — order cannot be processed","traceId":"..."}
```

All three log lines carry the **same `traceId`** — this is the key RCA evidence showing that the three errors belong to the same request.

### How to Reproduce

1. `POST /api/test/incidents/cascade`
2. Observe Jaeger: ~1.5 s duration, ERROR status
3. Look at the backend logs filtered by `traceId` — all three `[RatingEngine]`, `[PricingService]`, `[IncidentController]` errors should share the same trace ID
4. Reset: `DELETE /api/test/incidents/reset`

---

## INC-006 — Historical Similar Incident (Pool Exhaustion Variant)

| Field | Value |
|---|---|
| **Incident ID** | `INC-006` |
| **Scenario** | `db_pool_exhaustion_v2` |
| **Trigger endpoint** | `POST /api/test/incidents/historical` |
| **Affected component** | HikariCP connection pool |
| **Expected service** | `rke-backend` |
| **Known root cause** | Same failure class as INC-001 but with different parameters: 3 holders (not 4), 6 000 ms hold duration (not 4 000 ms), 2 500 ms probe timeout (not 3 000 ms) |
| **Category** | Infrastructure |

### Purpose

INC-006 exists to test the RCA Agent's historical incident recall:

```
INC-006 triggered
    ↓
RCA Agent analyses current evidence (pool exhaustion, 2500ms timeout)
    ↓
Memory search finds INC-001 (similarity score ≈ 0.6)
    ↓
INC-001 used as historical corroboration
    ↓
Current evidence remains primary — INC-001 is supporting context, not proof
```

### Trigger

```bash
curl -s -X POST http://localhost:8000/api/test/incidents/historical
# → HTTP 500, ~2.5 s delay
```

### How INC-006 Differs from INC-001

| Parameter | INC-001 | INC-006 |
|---|---|---|
| Concurrent holders | 4 | 3 |
| Hold duration | 4 000 ms | 6 000 ms |
| Probe timeout | 3 000 ms | 2 500 ms |
| Log wording | `pool exhaustion scenario` | `historical pool exhaustion variant (INC-006)` |
| `SimulationState` flag | `DB_POOL_EXHAUSTION` | `DB_POOL_EXHAUSTION_V2` |

### Expected Log Pattern

```json
{"level":"WARN","message":"[INC-006] Historical pool exhaustion variant (INC-006): saturating pool with 3 holders for up to 6000ms","traceId":"..."}
{"level":"WARN","message":"[INC-006] historical variant: 3 holders active, attempting probe...","traceId":"..."}
{"level":"WARN","message":"[INC-006] historical probe timed out as expected: ...","traceId":"..."}
{"level":"ERROR","message":"[INC-006] historical variant: database connection pool exhausted... Similar to INC-001 — see historical incident memory."}
```

---

## Incident Traceability

For every incident:

1. Trigger the incident endpoint
2. The HTTP response body (HTTP 500) contains `"incidentId": "INC-00X"`
3. The backend logs contain `traceId` in every log line for that request
4. Open Jaeger at `http://localhost:16686`
5. Search: Service `rke-backend`, time window = last 5 minutes
6. Find the error trace (red bar)
7. The trace ID in Jaeger = the `traceId` in the backend logs

### Example correlation

```bash
# 1. Trigger INC-003
curl -s -X POST http://localhost:8000/api/test/incidents/backend-error

# 2. Read the traceId from backend logs
docker compose logs backend 2>/dev/null | python3 -c "
import sys, json
for line in sys.stdin:
    try:
        d = json.loads(line)
        if d.get('traceId') and 'INC-003' in d.get('message',''):
            print('traceId:', d['traceId'])
            break
    except: pass
"
# → traceId: 4bf92f3577b34da6a3ce929d0e0e4736

# 3. Open Jaeger and search for that trace ID
open http://localhost:16686
# → Paste trace ID into the search box
```

---

## Reset

After any incident:

```bash
curl -X DELETE http://localhost:8000/api/test/incidents/reset
# → HTTP 204 No Content
```

This immediately restores normal application behaviour.

---

## RCA-Agent Compatibility

RKE telemetry is compatible with the `JaegerTraceProvider` in the rca-agent:

| Property | RKE Value | JaegerTraceProvider Expectation |
|---|---|---|
| `service.name` | `rke-backend` (from `OTEL_SERVICE_NAME`) | Used in `GET /api/traces?service=rke-backend` |
| Trace ID format | 32 hex chars (W3C 128-bit) | Parsed from `traceID` field in Jaeger JSON |
| Span ID format | 16 hex chars | Parsed from `spanID` field |
| Error status | `otel.status_code=ERROR` tag | `_derive_status()` checks this tag first |
| Error message | `otel.status_description` tag | `span.status_message` populated from this |
| Timestamps | Microseconds since Unix epoch | `Span` validator converts to UTC datetime |
| Parent/child | Jaeger `CHILD_OF` references | `parent_span_id` populated from `CHILD_OF` ref |

See `docs/opentelemetry.md` for the full instrumentation documentation.
