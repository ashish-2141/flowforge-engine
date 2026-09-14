# Repository Bootstrap

## Purpose

This document records the Week 1 repository and development-environment baseline. It intentionally contains no production workflow-execution logic.

## Repository structure

```text
flowforge-engine/
├── flowforge-engine/       # Domain-independent orchestration engine
│   ├── pom.xml
│   └── src/main/java/...
├── voltops-reference/      # Domain-specific reference application
│   ├── pom.xml
│   └── src/main/java/...
├── docs/                   # Week 1 design documents
├── .github/workflows/      # CI and boundary checks
├── docker-compose.yml      # PostgreSQL and RabbitMQ
├── pom.xml                # Maven parent and module declarations
├── README.md
└── LICENSE
```

## Module separation

The root Maven project declares two modules:

- `flowforge-engine`: reusable, domain-independent engine.
- `voltops-reference`: reference application that depends on FlowForge.

The dependency direction is one-way:

```text
voltops-reference
       |
       v
flowforge-engine
```

FlowForge must not depend on VoltOps.

## Spring Boot baseline

Each module contains a minimal Spring Boot application entry point so the development environment is executable before production orchestration logic is introduced.

Current baseline:

- Java 21
- Spring Boot 4.1.1
- Maven

## Supporting services

Docker Compose provides:

- PostgreSQL for durable orchestration state.
- RabbitMQ for later task/event delivery work.

The services are development dependencies only during Week 1. No production workflow execution is implemented yet.

## Local setup

```bash
mvn clean verify

docker compose up -d
```

The engine application can be started with:

```bash
mvn -pl flowforge-engine spring-boot:run
```

## CI baseline

GitHub Actions performs these checks on pushes to `main` and pull requests targeting `main`:

1. Checkout source.
2. Configure Java 21.
3. Search the entire `flowforge-engine` module for forbidden domain-specific terms.
4. Run `mvn verify`.

The boundary check prevents electrical-maintenance concepts from leaking into the reusable engine module.

## Week 1 scope boundary

Included:

- Git repository
- Maven multi-module structure
- Spring Boot baseline
- Docker Compose
- README and setup instructions
- Six design documents plus this bootstrap record
- CI boundary and build verification

Excluded:

- Production task execution
- Worker lease implementation
- Retry implementation
- Outbox publisher implementation
- Frontend dashboard
- Metrics infrastructure
- Kubernetes deployment
- Cloud deployment

These exclusions follow the Week 1 assignment and keep implementation behind the design-review gate.

## Verification checklist

The bootstrap is considered ready when:

- `mvn verify` succeeds locally.
- Docker Compose starts PostgreSQL and RabbitMQ.
- Both Maven modules are recognized by the root build.
- FlowForge and VoltOps have separate packages and application entry points.
- CI rejects forbidden domain terms inside the FlowForge module.
- No production orchestration logic has been introduced during Week 1.
