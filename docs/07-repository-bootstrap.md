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

- `flowforge-engine`: reusable domain-independent orchestration library, no executable Spring Boot entry point.
- `voltops-reference`: domain-specific workflow library/adapter, no independent executable entry point.
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

Docker Compose provides PostgreSQL for durable orchestration state and RabbitMQ for asynchronous workflow/domain events. RabbitMQ does not grant task ownership. PostgreSQL-backed API polling owns task acquisition.

Both services have health checks.

## Clean-clone verification

From a fresh clone:

```bash
./mvnw clean verify
docker compose up -d --wait
docker compose ps
docker compose down -v
```

Run the executable application with:

```bash
./mvnw -pl flowforge-application spring-boot:run
```

The Maven wrapper uses a system Maven binary when available and otherwise bootstraps Maven 3.9.9.

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

The repository now contains the review-requested evidence mechanisms: three-module Maven structure, executable Maven wrapper, module and executable-boundary checks, forbidden-term enforcement, full Maven verification, Docker health checks, explicit Week 1 exclusions, and a design-defence guide.

Fresh-clone execution still needs to be performed in an environment with Docker and outbound network access. The current execution environment cannot perform an external Git clone.

The repository history contains separate documentation, CI, infrastructure, and module-structure commits for review.

## Week 1 scope boundary

Included: repository bootstrap, three-module Maven structure, Spring Boot application baseline, Docker Compose, README/setup instructions, the seven required Week 1 deliverables, design-defence material, and CI checks.

Excluded: production task execution, worker lease implementation, retry implementation, production outbox publishing, frontend dashboard, metrics infrastructure, Kubernetes, and cloud deployment.
