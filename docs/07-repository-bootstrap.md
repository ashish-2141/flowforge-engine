# Repository Bootstrap

## Purpose

This document records the Week 1 repository and development-environment baseline. It intentionally contains no production workflow-execution logic.

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
├── mvnw                       # Maven wrapper entry point
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
          ^
          |
flowforge-application -----> flowforge-engine
        |
        +--------------------> voltops-reference
```

The engine module must never depend on VoltOps.

## Deployment model

The initial system is a modular monolith:

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

This removes the earlier contradiction between multiple Spring Boot entry points and the single-deployment modular-monolith decision.

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

Both services include container health checks.

## Local setup

On a clean clone:

```bash
chmod +x mvnw
./mvnw clean verify
docker compose up -d
```

Run the executable application with:

```bash
./mvnw -pl flowforge-application spring-boot:run
```

The Week 1 baseline does not execute production workflows.

## CI verification

GitHub Actions runs on pushes to `main` and pull requests targeting `main`.

CI checks:

1. Java 21 is configured.
2. Module dependency direction is enforced.
3. Only `flowforge-application` has the Spring Boot packaging plugin.
4. The entire `flowforge-engine` module is scanned for forbidden electrical-domain terms.
5. `./mvnw -B clean verify` runs compilation and tests.
6. Docker Compose syntax is validated.

## Evidence to retain for design review

The reviewer-requested evidence is:

- clean-clone build using `./mvnw clean verify`
- successful Docker Compose startup with healthy PostgreSQL and RabbitMQ containers
- successful GitHub Actions execution
- demonstrated module dependency enforcement
- demonstrated forbidden-domain-term check
- meaningful commit history
- confirmation that no production orchestration logic was introduced before approval

The design-review environment should record the command output or CI run URL for each item. This repository contains the checks and commands; external execution evidence must come from the actual environment in which the clone is tested.

## Week 1 scope boundary

Included:

- Git repository
- Maven multi-module structure
- Spring Boot application baseline
- Docker Compose
- README and setup instructions
- Problem discovery
- Architecture proposal
- Data-model proposal
- State-machine proposal
- Failure analysis
- Technology decisions
- Repository bootstrap
- CI boundary and build checks

Excluded:

- production task execution
- worker lease implementation
- retry implementation
- production outbox publisher implementation
- frontend dashboard
- metrics infrastructure
- Kubernetes deployment
- cloud deployment

These exclusions preserve the design-review gate.
