# Repository Bootstrap

## Purpose

This document records the Week 1 repository and development-environment baseline. It contains no production workflow-execution logic.

## Repository structure

```text
flowforge-engine/
├── flowforge-engine/          # reusable domain-independent engine library
├── voltops-reference/         # domain-specific reference library / adapter
├── flowforge-application/     # sole executable Spring Boot application
├── docs/                      # Week 1 design and defence documents
├── .github/workflows/         # CI and boundary checks
├── docker-compose.yml         # PostgreSQL and RabbitMQ
├── mvnw                       # portable Maven wrapper
├── pom.xml                    # Maven parent and module declarations
├── README.md
└── LICENSE
```

## Module separation

The root Maven project has three modules:

- `flowforge-engine`: reusable domain-independent orchestration library, with no executable Spring Boot entry point.
- `voltops-reference`: domain-specific workflow library/adapter, with no independent executable entry point.
- `flowforge-application`: sole executable Spring Boot application, depending on both libraries.

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

Docker Compose provides PostgreSQL for durable orchestration state and RabbitMQ for asynchronous workflow and domain events.

RabbitMQ does not grant task ownership. Workers acquire work through capability-based API polling backed by PostgreSQL.

Both services have health checks.

## Clean-clone verification

From a fresh clone:

```bash
./mvnw clean verify
docker compose up -d --wait
docker compose ps
docker compose down -v
```

The Maven wrapper uses a system Maven binary when available and otherwise bootstraps Maven 3.9.9.

Run the executable application with:

```bash
./mvnw -pl flowforge-application spring-boot:run
```

## CI verification

GitHub Actions runs on pushes to `main` and pull requests targeting `main`.

CI checks:

1. Java 21 setup.
2. Maven module dependency direction.
3. Executable-module boundary.
4. Forbidden electrical-domain terms inside `flowforge-engine`.
5. `./mvnw -B clean verify`.
6. Docker Compose startup with `--wait`.
7. Container health output.
8. Cleanup with `docker compose down -v`.

## Evidence status

The repository contains the mechanisms requested by the revision checklist: three-module Maven structure, executable Maven wrapper, module dependency enforcement, executable-boundary enforcement, forbidden-domain-term enforcement, full Maven verification, Docker health checks, explicit Week 1 exclusions, and design-defence material.

The revision cycle has already produced a successful CI verification stage for Maven, module dependency direction, executable boundary, and FlowForge domain-boundary checks. Docker health verification is included in the same workflow.

Fresh-clone execution should still be recorded from an environment with outbound Git access and Docker installed.

The repository history contains separate documentation, architecture, CI, infrastructure, and module-structure changes, providing a reviewable trail.

## Week 1 scope boundary

Included:

- Git repository
- three-module Maven structure
- Spring Boot application baseline
- Docker Compose
- README and setup instructions
- seven required Week 1 deliverables
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
