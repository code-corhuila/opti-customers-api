# opti-customers-api

Patients and their optical formulas (prescriptions). Owns the customers domain; other services reach patients only through this API (for example the worker, to flag overdue controls, and the workflow, to check a patient).

Part of the OptiView distributed system (team `opti`). Governance and documentation live in
[`opti-docs`](https://github.com/code-corhuila/opti-docs).

## Architecture

Hexagonal, in three Maven modules so the rule is enforced by the compiler:

| Module | Contents | Depends on |
|---|---|---|
| `customers-core` | domain, ports, use cases. **No framework dependency** | nothing |
| `customers-adapters` | HTTP inbound adapter, PostgreSQL outbound adapter | core |
| `customers-app` | composition root: wires everything and declares every limit (`application.yml`) | adapters |

The database schema is **not** here: it lives in [`opti-customers-db`](https://github.com/code-corhuila/opti-customers-db).
Other domains are reached only through their published API, never through their database.

## Public contract

Base path `/api/v1`. JSON in `camelCase`, UUID ids, money in cents, dates RFC 3339 UTC. Every
error uses `{"error", "message", "details"?, "traceId"}`. Full specification: [`openapi/openapi.yaml`](openapi/openapi.yaml).

| Method and path | Roles | Answers |
|---|---|---|
| `POST /api/v1/patients` | ADMIN, SELLER, OPTOMETRIST | `201` + `Location`, `200` on retry, `400`, `422` duplicate document |
| `GET /api/v1/patients` | any | page of patients; filters `q`, `status`, `controlDueBefore` |
| `GET /api/v1/patients/{id}` | any | patient, `400` bad id, `404` |
| `PUT /api/v1/patients/{id}/contact` | ADMIN, SELLER, OPTOMETRIST | updated patient |
| `POST /api/v1/patients/{id}/control-overdue` | ADMIN, SERVICE | flag (idempotent), used by the worker |
| `POST /api/v1/patients/{id}/formulas` | ADMIN, OPTOMETRIST | new formula becomes the current one |
| `GET /api/v1/patients/{id}/formulas` / `/current` | any | history page / current formula |
| `GET /health` | none | `200` |

Cross-cutting rules (numeral 5.3): the JWT (RS256) is validated by this service, creations require
`Idempotency-Key` (8-128 chars), listings are paginated (`page` from 1, `limit` 1-100, default 20,
newest first) and every response carries `X-Correlation-Id`, also written in each JSON log line.

## Run

The whole platform is started from `opti-infra` (see its README). To work on this service alone:

```bash
cp .env.example .env            # fill in the values
mvn -B verify                   # unit + HTTP tests (+ persistence tests if TEST_DATABASE_URL is set)
docker compose --env-file .env -f deploy/compose.yml build
```

Configuration (all from the environment, see `.env.example`): `DATABASE_URL`, `DATABASE_USER`,
`DATABASE_PASSWORD`, `JWT_PUBLIC_KEY` or `JWT_PUBLIC_KEY_FILE`.

## Explicit limits (numeral 5.3.10)

Declared in `customers-app/src/main/resources/application.yml`: header/read timeout 5 s, idle keep-alive
60 s, connection pool 10, wait for a connection 5 s, statement timeout 5 s, graceful shutdown 20 s.

## Depends on

`opti-customers-db` (its own PostgreSQL instance) and the public key of the identity service.
