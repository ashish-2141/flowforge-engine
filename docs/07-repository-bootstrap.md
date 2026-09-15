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

The Maven wrapper falls back to Maven 3.9.9 when a system Maven binary is unavailable.

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

## Current evidence

The latest CI run has passed the Maven verification phase plus module dependency, executable-boundary, and FlowForge domain-boundary checks. The infrastructure-health portion is executed afterward.

Local clean-clone evidence should be captured from a real fresh checkout because the execution environment used for design work does not have outbound Git access. The repository itself contains the wrapper, CI checks, and health-check configuration needed for that verification.

The commit history contains separate changes for documentation, CI, infrastructure, and the module-structure refactor, providing a reviewable implementation trail.

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
