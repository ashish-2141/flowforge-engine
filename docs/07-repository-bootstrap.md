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
├── docs/                      # Week 1 design documents
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
- `flowforge-application`: the sole executable Spring Boot application. It depends on both library modules.

Dependency direction:

```text
voltops-reference -----> flowforge-engine

flowforge-application -----> flowforge-engine
        |
        +--------------------> voltops-reference
```

The engine module must never depend on VoltOps.

## Deployment model

The initial design is a modular monolith:

```text
flowforge-application
        |
        +---- PostgreSQL
        +---- RabbitMQ

worker-1 -----> flowforge-application
worker-2 -----> flowforge-application
worker-3 -----> flowforge-application
```

There is one executable Spring Boot process. Workers remain separate processes because task execution is distributed. VoltOps is loaded as a domain library/adapter, not deployed as a second application.

## Spring Boot baseline

- Java 21
- Spring Boot 4.1.1
- Maven 3.9.9 through the repository wrapper

Only `flowforge-application` uses the Spring Boot Maven packaging plugin.

## Supporting services

Docker Compose provides:

- PostgreSQL for durable orchestration state.
- RabbitMQ for asynchronous workflow and domain events.

RabbitMQ is not the task-ownership mechanism. Workers acquire ownership through capability-based API polling backed by PostgreSQL.

Both services have health checks.

## Local clean-clone verification

From a fresh clone:

```bash
./mvnw clean verify
docker compose up -d --wait
docker compose ps
```

The Maven wrapper has executable mode in Git. It uses an installed Maven binary when present and otherwise bootstraps Maven 3.9.9.

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
5. `./mvnw -B clean verify` runs the multi-module build and tests.
6. Docker Compose starts with `--wait` and health status is displayed.
7. Docker Compose is torn down even after failures.

## Review evidence

The design-review checklist asks for clean-clone build evidence, healthy supporting services, CI execution, module-dependency enforcement, forbidden-term enforcement, meaningful history, and proof that production orchestration logic has not started.

Repository evidence now includes:

- three-module Maven structure
- executable wrapper
- CI module-dependency check
- CI executable-boundary check
- CI forbidden-domain-term check
- CI `clean verify`
- Docker health checks and CI startup verification
- explicit Week 1 scope exclusions

The exact CI run URL and local command output should be attached to the design-review notes after the current revision's CI run completes.

## Week 1 scope boundary

Included:

- Git repository
- Maven multi-module structure
- Spring Boot application baseline
- Docker Compose
- README and setup instructions
- seven Week 1 design/bootstrap artifacts
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
