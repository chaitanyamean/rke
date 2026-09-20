# Incident Simulation — RKE

This document describes the controlled incident simulation framework built into the RKE backend.  
The framework exists solely to support the [`rca-agent`](https://github.com/chaitanyamean/rca-agent) — an independent AI-powered Root Cause Analysis platform that uses RKE as its target application.

---

## Overview

The simulation framework lets you intentionally generate realistic production-like failures inside RKE without touching production configuration, breaking normal application behaviour, or introducing random chaos.

Every scenario is:

| Property | Detail |
|---|---|
| **Deterministic** | Same trigger → same failure, every time |
| **Repeatable** | Can be triggered as many times as needed |
| **Isolated** | Active only while the trigger endpoint is being served |
| **Safe** | Normal application endpoints are completely unaffected |
| **Dev/test only** | Endpoints do not exist in production |
| **Easy to trigger** | Single HTTP POST |
| **Easy to reset** | Single HTTP DELETE |

---

## Prerequisites

The simulation endpoints are only registered when the `dev` Spring profile is active.

**Start the application in dev mode (default):**

```bash
# Local development — dev is the default profile
./mvnw spring-boot:run -pl backend

# Explicitly set the profile
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run -pl backend

# Docker Compose (dev is the default; override to prod to hide endpoints)
docker compose up
```

**Confirm the endpoints are available:**

```bash
curl -s http://localhost:8080/api/test/incidents/status
# → {"activeSimulations":[],"count":0,"timestamp":"..."}
```

If you get a 404 the `dev` profile is not active.

---

## Profile Guard

| Profile | Endpoints available | Notes |
|---|---|---|
| `dev` (default) | ✅ Yes | Controller and security bean are registered |
| `prod` | ❌ No | Controller bean is never created; path does not exist |

To run in production mode locally:

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run -pl backend
# GET /api/test/incidents/status → 404 Not Found
```

---

## API Reference

Base path: `POST /api/test/incidents/{scenario}`

All trigger endpoints return HTTP 500 (the simulated failure), except when the scenario is configured to show a known-good state (INC-004 with good config → HTTP 200).

| Method | Path | Incident | Description |
|---|---|---|---|
| `POST` | `/api/test/incidents/db-pool-exhaustion` | INC-001 | PostgreSQL connection pool exhaustion |
| `POST` | `/api/test/incidents/slow-query` | INC-002 | Slow PostgreSQL query / timeout |
| `POST` | `/api/test/incidents/backend-error` | INC-003 | Backend application exception |
| `POST` | `/api/test/incidents/config-regression` | INC-004 | Configuration regression |
| `POST` | `/api/test/incidents/cascade` | INC-005 | Cascading dependency failure |
| `POST` | `/api/test/incidents/historical` | INC-006 | Historical similar incident (pool exhaustion variant) |
| `GET` | `/api/test/incidents/status` | — | List currently active simulations |
| `DELETE` | `/api/test/incidents/reset` | — | Reset all active simulations → HTTP 204 |

### Response envelope (trigger endpoints on failure)

```json
{
  "timestamp": "2026-09-20T09:00:00.000Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "[INC-001] Database connection pool exhausted — all 4 connections held by concurrent requests. Pool acquisition timed out after 3000 ms.",
  "incidentId": "INC-001",
  "simulatedFailure": true
}
```

### Response envelope (trigger endpoints — scenario ran without throwing)

```json
{
  "incidentId": "INC-004",
  "scenario": "config_regression",
  "status": "triggered",
  "description": "Configuration is within known-good range: max-items-per-order=50",
  "triggeredAt": "2026-09-20T09:00:00.000Z"
}
```

---

## Scenario Reference

---

### INC-001 — PostgreSQL Connection Pool Exhaustion

**Trigger:**
```bash
curl -s -X POST http://localhost:8080/api/test/incidents/db-pool-exhaustion
```

**Mechanism:**  
Spawns 4 threads that each hold a JDBC connection from the HikariCP pool for 4 seconds.  
While those connections are held, a 5th connection is attempted with a 3-second timeout.  
The timeout fires, producing a `HikariPool-1 - Connection is not available` exception.

**Expected behaviour:**

| Observable | Detail |
|---|---|
| HTTP status | 500 |
| Response time | ~3 s (probe timeout) |
| Log level | WARN/ERROR |
| Log content | `Pool exhaustion scenario starting`, `Probe connection timed out` |
| Exception | `SimulationException` wrapping `java.sql.SQLException` |
| Response body | `simulatedFailure: true`, `incidentId: "INC-001"` |

**Evidence for RCA agent:**
- Multiple `acquiring connection` WARN entries in quick succession
- `Connection is not available, request timed out after 3000ms` in the exception message
- `incidentId: INC-001` in the 500 response body

**Reset:**
```bash
curl -s -X DELETE http://localhost:8080/api/test/incidents/reset
```
Pool returns to normal immediately once the probe attempt completes and holders are released.

---

### INC-002 — Slow PostgreSQL Query

**Trigger:**
```bash
curl -s -X POST http://localhost:8080/api/test/incidents/slow-query
```

**Mechanism:**  
Executes `SELECT pg_sleep(5)` against the live PostgreSQL database.  
After the sleep completes, a simulated timeout exception is thrown.

**Expected behaviour:**

| Observable | Detail |
|---|---|
| HTTP status | 500 |
| Response time | ~5 s |
| Log level | WARN (start), WARN (completion), ERROR (exception) |
| Log content | `Slow query scenario starting`, `Slow query completed in N ms`, `Simulated query timeout` |
| Exception | `SimulationException` with `timeout` in message |
| Response body | `simulatedFailure: true`, `incidentId: "INC-002"` |

**Evidence for RCA agent:**
- Long DB operation duration (~5 000 ms) clearly distinguishable from normal queries
- Log line: `Simulated DB latency spike`
- Exception: `Simulated query timeout: database operation took N ms`

**Notes:**  
`SIMULATE_TIMEOUT` is `true` by default — the endpoint returns HTTP 500 after the sleep.  
To observe the slow query with a 200 response (latency only, no error), set `SIMULATE_TIMEOUT = false` in `SlowQueryScenario.java`.

**Reset:**
```bash
curl -s -X DELETE http://localhost:8080/api/test/incidents/reset
```
No persistent state — the next request through any normal endpoint is unaffected.

---

### INC-003 — Backend Application Exception / HTTP 500

**Trigger:**
```bash
curl -s -X POST http://localhost:8080/api/test/incidents/backend-error
```

**Mechanism:**  
Simulates a realistic multi-step service path:
1. Generates a request `correlationId` (UUID).
2. Runs a fake inventory check (succeeds).
3. Runs a fake price-calculation step that calls `Math.addExact(Integer.MAX_VALUE, 1)`.
4. The `ArithmeticException` ("integer overflow") is caught and wrapped in a `SimulationException`.
5. `GlobalExceptionHandler` maps it to HTTP 500.

**Expected behaviour:**

| Observable | Detail |
|---|---|
| HTTP status | 500 |
| Response time | < 50 ms |
| Log level | INFO (inventory step), ERROR (exception) |
| Log content | `Processing order`, `Inventory check passed`, `Price calculation failed`, `integer overflow` |
| Exception | `SimulationException` → cause: `ArithmeticException` |
| Response body | `simulatedFailure: true`, `incidentId: "INC-003"`, `correlationId` in message |

**Evidence for RCA agent:**
- Stack trace shows `ArithmeticException: integer overflow` as the root cause
- Log lines show the partial-success path before failure (inventory OK → calculation failed)
- `correlationId` appears in both the exception message and the INFO log lines, enabling log correlation

**Reset:**
```bash
curl -s -X DELETE http://localhost:8080/api/test/incidents/reset
```

---

### INC-004 — Configuration Regression

**Trigger (regression active):**
```bash
# Step 1: Start the app with the simulation profile to load the bad config
SPRING_PROFILES_ACTIVE=dev,simulation ./mvnw spring-boot:run -pl backend

# Step 2: Trigger the scenario
curl -s -X POST http://localhost:8080/api/test/incidents/config-regression
```

**Trigger (known-good config, for comparison):**
```bash
# Start without the simulation profile
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run -pl backend

curl -s -X POST http://localhost:8080/api/test/incidents/config-regression
# → HTTP 200, description mentions max-items-per-order=50
```

**Mechanism:**  
The property `simulation.config-regression.max-items-per-order` controls how many items are allowed per order:

| State | Profile | Value | Behaviour |
|---|---|---|---|
| Known-good | `dev` (no simulation profile) | `50` | Endpoint returns HTTP 200 |
| Regression | `dev,simulation` | `0` (from `application-simulation.yml`) | Endpoint returns HTTP 500 |

`application-simulation.yml` is committed to Git with `max-items-per-order: 0`.  
The diff between `application.yml` (50) and `application-simulation.yml` (0) is the Git-visible change the RCA agent correlates against the incident.

**Expected behaviour (regression active):**

| Observable | Detail |
|---|---|
| HTTP status | 500 |
| Response time | < 50 ms |
| Log level | ERROR |
| Log content | `CONFIGURATION REGRESSION DETECTED`, `max-items-per-order=0`, `Check recent Git changes to application-simulation.yml` |
| Response body | `simulatedFailure: true`, `incidentId: "INC-004"`, message mentions known-good value (50) |

**Evidence for RCA agent:**
- ERROR log: `CONFIGURATION REGRESSION DETECTED: max-items-per-order=0 (expected > 0)`
- Git diff: `application-simulation.yml` changed `max-items-per-order` from `50` to `0`
- Active Spring profile `simulation` visible in actuator `/actuator/env`

**Reset (runtime flag only):**
```bash
curl -s -X DELETE http://localhost:8080/api/test/incidents/reset
```

**Full reset (back to known-good config):**  
Restart the application without the `simulation` profile:
```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run -pl backend
```

---

### INC-005 — Cascading Failure

**Trigger:**
```bash
curl -s -X POST http://localhost:8080/api/test/incidents/cascade
```

**Mechanism:**  
Simulates a three-layer internal dependency chain — no external services required:

```
HTTP Client
    ↓
IncidentSimulationController  [upstream — receives the API call]
    ↓
CascadeFailureScenario         [application layer]
    ↓
PricingService (stub)          [downstream internal dependency]
    ↓
RatingEngine (stub)            [deep dependency]
    ↓
IOException                    [simulated network timeout]
```

Each layer sleeps for 500 ms to simulate I/O wait, catches the downstream exception, logs it at ERROR, and re-wraps it with additional context before propagating upward.

**Expected behaviour:**

| Observable | Detail |
|---|---|
| HTTP status | 500 |
| Response time | ~1.5 s (3 × 500 ms layers) |
| Log level | ERROR (three separate log lines) |
| Log content | `[RatingEngine] Connection timeout`, `[PricingService] Downstream failure from RatingEngine`, `[IncidentController] Upstream failure: PricingService unavailable` |
| Exception chain | `SimulationException` → `RuntimeException ([PricingService])` → `IOException ([RatingEngine])` |
| Response body | `simulatedFailure: true`, `incidentId: "INC-005"`, message mentions `PricingService` and root cause |

**Evidence for RCA agent:**
- Three nested ERROR log lines in sequence, each naming a different "service"
- Exception cause chain in the stack trace shows full cascade depth
- Increased response latency (~1.5 s) vs normal API calls (~10 ms)

**Reset:**
```bash
curl -s -X DELETE http://localhost:8080/api/test/incidents/reset
```

---

### INC-006 — Historical Similar Incident

**Purpose:**  
INC-006 is a second occurrence of a connection-pool exhaustion scenario, deliberately similar to INC-001 but not byte-for-byte identical. Its purpose is to test the RCA agent's ability to recall a prior incident from memory.

Expected RCA agent behaviour on INC-006:
```
INC-006 triggered
    ↓
RCA agent analyses telemetry
    ↓
Agent queries incident memory
    ↓
Finds INC-001 as a historical similar incident
    ↓
Reuses INC-001's resolution evidence in the report
```

**Differences from INC-001:**

| Parameter | INC-001 | INC-006 |
|---|---|---|
| Concurrent holders | 4 | 3 |
| Hold duration | 4 000 ms | 6 000 ms |
| Probe timeout | 3 000 ms | 2 500 ms |
| SimulationState flag | `DB_POOL_EXHAUSTION` | `DB_POOL_EXHAUSTION_V2` |
| Log wording | `pool exhaustion scenario starting` | `historical pool exhaustion variant (INC-006)` |

**Trigger:**
```bash
curl -s -X POST http://localhost:8080/api/test/incidents/historical
```

**Expected behaviour:**

| Observable | Detail |
|---|---|
| HTTP status | 500 |
| Response time | ~2.5 s (probe timeout) |
| Log content | `historical pool exhaustion variant (INC-006)`, `historical variant: N holders active`, `historical probe timed out` |
| Response body | `simulatedFailure: true`, `incidentId: "INC-006"`, message contains `Similar to INC-001` |

**Evidence for RCA agent:**
- Same symptom class as INC-001: connection pool exhaustion, probe timeout
- Message explicitly says `Similar to INC-001 — see historical incident memory`
- Different log fingerprint (`INC-006`, `historical`) distinguishes this from INC-001 in the logs

**Reset:**
```bash
curl -s -X DELETE http://localhost:8080/api/test/incidents/reset
```

---

## Status Endpoint

At any time, check which simulations are active:

```bash
curl -s http://localhost:8080/api/test/incidents/status
```

```json
{
  "activeSimulations": ["SLOW_QUERY", "BACKEND_EXCEPTION"],
  "count": 2,
  "timestamp": "2026-09-20T09:00:00.000Z"
}
```

Normally `activeSimulations` is empty between runs.  
During a running scenario it reflects the active flag — useful for debugging race conditions or unexpected state.

---

## Reset

A single endpoint clears all active simulation flags:

```bash
curl -s -X DELETE http://localhost:8080/api/test/incidents/reset
# → HTTP 204 No Content
```

This is safe to call:
- When no simulations are active (idempotent)
- After any scenario, whether it completed normally or threw an exception
- From a test teardown or CI cleanup script

After reset, all normal application endpoints behave exactly as in production.

---

## How Simulations Are Isolated

Simulations do **not** affect normal application endpoints.

The isolation mechanism:

1. The trigger endpoint enables a `SimulationState` flag.
2. The scenario's `run()` method checks that flag first — if it is not set, `run()` is a no-op in < 1 ms.
3. The flag is cleared in a `finally` block after the scenario completes or throws.
4. No scenario modifies any JPA entity, database record, or application bean state.
5. The `SimulationState` registry is an in-memory `CopyOnWriteArraySet` — a restart always begins clean.

This means:
- You can run a simulation and immediately call a normal endpoint — it will be unaffected.
- Crash-restarting the application always clears any active simulation state.

---

## Running the Full Acceptance Check

Use this sequence to verify the framework end-to-end before a test run:

```bash
# 1. Start RKE in dev mode
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run -pl backend &
sleep 10

# 2. Confirm normal behaviour
curl -sf http://localhost:8080/api/health && echo "OK"

# 3. Confirm status shows no active simulations
curl -s http://localhost:8080/api/test/incidents/status

# 4. Trigger each incident and confirm the expected HTTP status

echo "INC-001:"
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/test/incidents/db-pool-exhaustion
# Expected: 500

echo "INC-002:"
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/test/incidents/slow-query
# Expected: 500 (after ~5 s)

echo "INC-003:"
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/test/incidents/backend-error
# Expected: 500

echo "INC-004 (known-good config):"
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/test/incidents/config-regression
# Expected: 200 (no regression when simulation profile is inactive)

echo "INC-005:"
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/test/incidents/cascade
# Expected: 500 (after ~1.5 s)

echo "INC-006:"
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/test/incidents/historical
# Expected: 500

# 5. Reset
curl -s -X DELETE http://localhost:8080/api/test/incidents/reset

# 6. Confirm status is clear
curl -s http://localhost:8080/api/test/incidents/status
# Expected: {"activeSimulations":[],"count":0,...}

# 7. Confirm normal endpoint still works
curl -sf http://localhost:8080/api/health && echo "Normal behaviour confirmed"
```

---

## Running the Unit Tests

```bash
# Run all simulation tests
./mvnw test -pl backend -Dtest="com.rke.backend.simulation.*,com.rke.backend.simulation.scenario.*,com.rke.backend.exception.GlobalExceptionHandlerSimulationTest"

# Run the full test suite (71 simulation tests + existing tests)
./mvnw test -pl backend
```

Expected simulation test counts:

| Test class | Tests |
|---|---|
| `SimulationStateTest` | 9 |
| `IncidentSimulationControllerTest` | 24 |
| `SimulationExceptionTest` | 3 |
| `IncidentResponseTest` | 2 |
| `ProfileGuardTest` | 6 |
| `BackendExceptionScenarioTest` | 5 |
| `ConfigRegressionScenarioTest` | 7 |
| `CascadeFailureScenarioTest` | 5 |
| `SlowQueryScenarioTest` | 5 |
| `GlobalExceptionHandlerSimulationTest` | 5 |
| **Total** | **71** |

---

## File Inventory

```
backend/src/main/java/com/rke/backend/
└── simulation/
    ├── IncidentId.java                   # INC-001..INC-006 constants
    ├── IncidentResponse.java             # Response record returned by trigger endpoints
    ├── IncidentSimulationController.java # REST controller (@Profile("dev"))
    ├── SimulationException.java          # Exception propagated through GlobalExceptionHandler
    ├── SimulationSecurityConfig.java     # Security config that permits /api/test/** (@Profile("dev"))
    ├── SimulationState.java              # In-memory flag registry (enum + CopyOnWriteArraySet)
    └── scenario/
        ├── BackendExceptionScenario.java   # INC-003
        ├── CascadeFailureScenario.java     # INC-005
        ├── ConfigRegressionScenario.java   # INC-004 (@ConfigurationProperties)
        ├── DbPoolExhaustionScenario.java   # INC-001
        ├── HistoricalIncidentScenario.java # INC-006
        └── SlowQueryScenario.java          # INC-002

backend/src/main/resources/
├── application.yml                       # Default config (known-good: max-items-per-order=50)
└── application-simulation.yml           # Regression config (broken: max-items-per-order=0)

backend/src/main/java/com/rke/backend/
├── BackendApplication.java               # @EnableConfigurationProperties(ConfigRegressionScenario.class)
└── exception/GlobalExceptionHandler.java # Extended to handle SimulationException → HTTP 500

backend/src/test/java/com/rke/backend/
├── simulation/
│   ├── IncidentResponseTest.java
│   ├── IncidentSimulationControllerTest.java
│   ├── ProfileGuardTest.java
│   ├── SimulationExceptionTest.java
│   ├── SimulationStateTest.java
│   └── scenario/
│       ├── BackendExceptionScenarioTest.java
│       ├── CascadeFailureScenarioTest.java
│       ├── ConfigRegressionScenarioTest.java
│       └── SlowQueryScenarioTest.java
└── exception/
    └── GlobalExceptionHandlerSimulationTest.java
```

---

## What This Framework Does NOT Do

This is phase 1 (failure simulation only). The following are intentionally out of scope:

- OpenTelemetry / distributed tracing instrumentation
- OTEL Collector or Jaeger configuration
- AI-powered RCA logic (belongs in the separate `rca-agent` repository)
- Persistent incident storage in the database
- Real microservice dependencies
- Random or non-deterministic failure injection
- Chaos engineering (fault injection in production)

---

## Scenario-to-RCA-Capability Matrix

| Scenario | RCA Capability Being Tested |
|---|---|
| INC-001 | Database resource exhaustion diagnosis |
| INC-002 | Slow query / latency spike identification |
| INC-003 | Exception root cause tracing through stack |
| INC-004 | Configuration change correlation (Git diff ↔ incident) |
| INC-005 | Cascading failure attribution across service layers |
| INC-006 | Historical incident recall and pattern matching |
