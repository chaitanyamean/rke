# RKE

A monorepo containing a Spring Boot backend and a React + Vite frontend, wired
together with Docker Compose and PostgreSQL.

```
rke/
├── backend/          # Spring Boot 3.x (Java 21, Maven)
├── frontend/         # React + TypeScript (Vite, Tailwind, React Router, TanStack Query, Axios)
├── docker-compose.yml
├── Makefile
├── README.md
└── ARCHITECTURE.md
```

There is no business logic yet. The backend exposes a placeholder health check
at `GET /api/health`, and the frontend calls it and displays **"Backend
connected"** when it succeeds.

## Prerequisites

- **Java 21** (only for running the backend manually)
- **Node 20+** (only for running the frontend manually)
- **Docker** with the Compose plugin (for the quick start)

## Ports

| Service        | Container port | Host port |
|----------------|----------------|-----------|
| frontend       | 80             | **3001**  |
| backend        | 8080           | **8000**  |
| postgres       | 5432           | **5433**  |
| otel-collector | 4317 / 4318    | 4317 / 4318 |

> The frontend is mapped to host port **3001** because **3000** is commonly
> occupied by other local dev servers. Adjust the mappings in
> `docker-compose.yml` if these clash on your machine.

## Quick start (Docker Compose)

```bash
cp .env.example .env        # optional; sensible defaults are already baked in
make docker-up              # builds and starts postgres, backend, frontend
```

Then open:

- Frontend: http://localhost:3001 → should show **"Backend connected"**
- Backend health: http://localhost:8000/api/health

Useful commands:

```bash
make docker-logs            # follow logs from all services
make docker-down            # stop and remove the services
```

### Observability

The stack ships with OpenTelemetry wired end to end. The backend is
auto-instrumented by the OpenTelemetry Java agent (HTTP requests, JPA/Hibernate,
Postgres queries) and sends traces/metrics/logs via OTLP to an `otel-collector`
service, which prints them to its console for now.

Hit the health endpoint and watch traces arrive:

```bash
curl http://localhost:8000/api/health
docker compose logs -f otel-collector    # look for a "GET /api/health" span
```

Backend logs are structured JSON with `trace_id` / `span_id` on every line:

```bash
docker compose logs backend | grep trace_id | tail -1
```

See `ARCHITECTURE.md` for what is auto-instrumented vs. what needs manual spans,
and how to point the collector at a real backend (Grafana/Tempo/Loki, Honeycomb,
etc.) without touching application code.

## Manual start (per service)

First install dependencies and create local env files:

```bash
make setup
```

### 1. Database

Start just PostgreSQL from compose (or use your own local instance):

```bash
docker compose up -d postgres
```

### 2. Backend

```bash
cd backend
export DATABASE_URL=jdbc:postgresql://localhost:5433/rke   # 5433 if using the compose postgres
export DB_USERNAME=rke
export DB_PASSWORD=rke
./mvnw spring-boot:run          # or: make dev  (from repo root)
```

The API is available at http://localhost:8080/api/health when run directly
(port 8080), or http://localhost:8000/api/health when run via Docker Compose.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev                     # Vite dev server on http://localhost:5173
```

Set `VITE_API_BASE_URL` in `frontend/.env` to point at the backend
(defaults to `http://localhost:8000`). When running the backend directly on
port 8080, use `VITE_API_BASE_URL=http://localhost:8080`.

## Make targets

| Target             | Description                                        |
|--------------------|----------------------------------------------------|
| `make setup`       | Copy env files and install deps in both projects   |
| `make dev`         | Run the backend with hot reload                    |
| `make test`        | Run backend tests                                  |
| `make docker-up`   | Build and start all services                       |
| `make docker-down` | Stop and remove all services                       |
| `make docker-logs` | Follow logs from all services                      |
| `make clean`       | Remove build artifacts from both projects          |

## Environment variables

Defined in `.env.example` (root):

- `DATABASE_URL` — JDBC URL for the backend
- `DB_USERNAME` — database user
- `DB_PASSWORD` — database password
- `POSTGRES_DB` — database name created by the postgres container

The frontend reads `VITE_API_BASE_URL` (see `frontend/.env.example`).
