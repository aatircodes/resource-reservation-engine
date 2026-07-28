# Resource Reservation Engine

A domain-agnostic reservation system for booking any limited-capacity resource, built to demonstrate correct handling of concurrent access at the database level. Two clients booking the last available slot on the same resource at the same instant is the core problem this project solves — not a specific booking UI or a specific kind of resource.

## Problem

Naive booking systems that check availability and then write a reservation as two separate steps are vulnerable to race conditions: two simultaneous requests can both read "1 slot left," both pass the check, and both write a booking — overbooking the resource. This project addresses that directly with optimistic locking, safe retry via idempotency keys, and a race-free waitlist promotion path, rather than papering over it with application-level locks or hoping traffic never collides.

## Tech Stack

- Java 21
- Spring Boot 4.1
- Spring Data JPA / Hibernate
- Spring Security 7 (JWT-based, stateless)
- MySQL
- React (frontend, deferred — see Status below)
- springdoc-openapi (Swagger UI)

## Project Structure

resource-reservation-engine/
├── backend/ Spring Boot application
├── frontend/ React app (not yet started)
├── docs/
│ └── design-decisions.md
└── README.md


## Status

Currently in active development. Completed so far:

- Project scaffolding (Spring Boot, MySQL, JWT security)
- Full authentication module: registration, login, JWT issuance, role-based access (ADMIN/USER), seeded admin account on startup

Not yet built: resource and booking domain logic, optimistic locking, idempotency handling, waitlist promotion, load testing, deployment, frontend.

## Progress Tracker

| Phase | Description | Status |
|---|---|---|
| 0 | Setup — scaffold, MySQL, JWT config, base structure | Done |
| — | Authentication — register, login, JWT, role-based access, seeded admin | Done |
| 1 | Core domain + CRUD (happy path, no concurrency) | Not started |
| 2 | Optimistic locking + conflict handling | Not started |
| 3 | Idempotency keys | Not started |
| 4 | Waitlist + transactional promotion | Not started |
| 5 | Testing — concurrency, idempotency, waitlist, load test | Not started |
| 6 | Swagger/OpenAPI | Not started |
| 7 | React frontend (if time allows) | Not started |
| 8 | Deployment | Not started |
| 9 | Documentation — README + design-decisions.md | Ongoing |

## Authentication

Authentication is email- and password-based, returning a JWT on login. There are two fixed roles:

- **ADMIN** — manages resources. Seeded automatically on application startup via a `CommandLineRunner`; not created through self-registration.
- **USER** — registers via `/api/auth/register`, can book and cancel their own reservations.

Registration is open to anyone, but always creates a `USER`-role account — any role field in a registration request is ignored, since the DTO doesn't expose one at all.

### Endpoints (auth)

| Method | Path | Auth required | Description |
|---|---|---|---|
| POST | `/api/auth/register` | No | Create a new USER account |
| POST | `/api/auth/login` | No | Authenticate and receive a JWT |

Full request/response schemas are available via Swagger UI once the application is running (see below) — this README intentionally doesn't duplicate that reference.

## Running Locally

### Prerequisites

- Java 21
- Maven
- MySQL running locally (or update `application.yml` datasource config to point elsewhere)

### Environment Variables

The following can be set to override local-dev defaults in `application.yml`. None are required to run locally, but all should be set explicitly before any non-local deployment:

- `DB_USERNAME`, `DB_PASSWORD` — MySQL credentials
- `JWT_SECRET` — signing key for JWTs; the default is a placeholder and must not be used outside local development
- `ADMIN_EMAIL`, `ADMIN_PASSWORD` — credentials for the seeded admin account

### Steps

```bash
cd backend
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080`. Swagger UI will be available at `http://localhost:8080/swagger-ui.html` once springdoc-openapi is added (Phase 6 — not yet integrated as of this writing).

## API Documentation

Live, interactive API documentation is served via Swagger UI directly from the running application rather than maintained as a separate static reference file — this avoids the two going out of sync as endpoints change. This README covers architecture, setup, and rationale; Swagger covers exact request/response shapes.

## Design Decisions

See [`docs/design-decisions.md`](docs/design-decisions.md) for the reasoning behind key architectural choices — optimistic locking over pessimistic, idempotency key handling, waitlist promotion, and known limitations.