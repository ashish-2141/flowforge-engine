# Repository Bootstrap

## Purpose

This document records the Week 1 repository and development-environment baseline. It contains no production workflow-execution logic.

## Repository structure

```text
flowforge-engine/
├── flowforge-engine/          # reusable domain-independent engine library
│   ├── pom.xml
│   └── src/main/java/...
├── voltops-reference/         # domain-specific reference library / adapter
│   ├── pom.xml
│   └── src/main/java/...
├── flowforge-application/     # sole executable Spring Boot application
│   ├── pom.xml
│   └── src/main/java/...
├── docs/                      # Week 1 design and defence documents
├── .github/workflows/         # CI and boundary checks
├── docker-compose.yml         # PostgreSQL and RabbitMQ
├── mvnw                       # portable Maven wrapper
├── pom.xml                    # Maven parent and module declarations
├── README.md
└── LICENSE
```

## Module separation

The root Maven project declares three modules:

- `flowforge-engine`: reusable, domain-independent orchestration library. It has no Spring Boot executable entry point.
- `voltops-reference`: domain-specific reference workflows and adapters. It depends on `flowforge-engine` and has no independent executable entry point.
- `flowforge-application`: sole executable Spring Boot application. It depends on both library modules.

Dependency direction:

```text
voltops-reference -----> flowforge-engine

flowforge-application -----> flowforge-engine
        |
        +--------------------> voltops-reference
```

`flowforge-engine` must never depend on VoltOps.

## Deployment model

The initial deployment is a modular monolith:

```text
flowforge-application
        |
        +---- PostgreSQL
        +---- RabbitMQ

worker-1 -----> flowforge-application
worker-2 -----> flowforge-application
worker-3 -----> flowforge-application
```

There is one executable Spring Boot process. Workers remain separate processes because execution is distributed. VoltOps is a domain library/adapter, not a second executable application.

## Spring Boot baseline

- Java 21
- Spring Boot 4.1.1
- Maven 3.9.9 through `mvnw`

Only `flowforge-application` uses the Spring Boot Maven packaging plugin.

## Supporting services

Docker Compose provides:

- PostgreSQL for durable orchestration state.
- RabbitMQ for asynchronous workflow and domain events.

RabbitMQ does not grant task ownership. Workers acquire work through capability-based API polling backed by PostgreSQL.

PostgreSQL and RabbitMQ both have health checks.

## Clean-clone verification

From a fresh clone:

```bash
./mvnw clean verify
docker compose up -d --wait
docker compose ps
```

The Maven wrapper uses an installed Maven binary when present and otherwise bootstraps Maven 3.9.9.

Run the application with:

```bash
./mvnw -pl flowforge-application spring-boot:run
```

Stop dependencies with:

```bash
docker compose down -v
```

## CI verification

GitHub Actions runs on pushes to `main` and pull requests targeting `main`.

CI checks:

1. Java 21 is configured.
2. Module dependency direction is enforced.
3. Only `flowforge-application` has the Spring Boot packaging plugin.
4. `flowforge-engine` is scanned for forbidden electrical-domain terms.
5. `./mvnw -B clean verify` runs the build and tests.
6. Docker Compose starts with `--wait` so configured health checks must pass.
7. Container state is displayed with `docker compose ps`.
8. Dependencies are cleaned up with `docker compose down -v` even after a failure.

## Evidence status

The revision includes the repository-side evidence mechanisms requested by the review checklist:

- three-module Maven structure
- executable Maven wrapper
- module dependency-direction check
- executable-boundary check
- forbidden-domain-term check
- full `clean verify` build
- Docker health checks and CI startup validation
- explicit Week 1 scope exclusions
- separate design-defence guide

The fresh-clone command and Docker startup must still be run in an environment with Docker and outbound network access. The current model environment cannot perform that external clone itself.

The repository has a reviewable commit history with separate documentation, CI, infrastructure, and architecture/module-structure changes.

## Week 1 scope boundary

Included:

- Git repository
- three-module Maven structure
- Spring Boot application baseline
- Docker Compose
- README and setup instructions
- seven Week 1 deliverables
- design-defence guide
- CI boundary and build checks

Excluded:

- production task execution
- worker lease implementation
- retry implementation
- production outbox publishing
- frontend dashboard
- metrics infrastructure
- Kubernetes deployment
- cloud deployment

Implementation remains behind the design-review gate.
