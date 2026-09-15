# FlowForge Engine

Fault-tolerant distributed workflow orchestration platform built with Java 21 and Spring Boot.

FlowForge is a domain-independent orchestration engine. VoltOps is a domain-specific reference module used to demonstrate FlowForge with an outage-aware electrical-maintenance workflow.

## Week 1 status

Week 1 is a discovery and system-design phase. Production workflow execution is intentionally not implemented.

The design focuses on durable state, atomic task claiming, worker sessions, leases, fencing generations, idempotency, retry semantics, transactional outbox delivery, and restart recovery.

## Deliverables

1. [Problem discovery](docs/01-problem-discovery.md)
2. [Initial architecture proposal](docs/02-architecture-proposal.md)
3. [Initial data-model proposal](docs/03-data-model-proposal.md)
4. [Workflow and task state-machine proposal](docs/04-state-machine-proposal.md)
5. [Failure analysis](docs/05-failure-analysis.md)
6. [Technology decision note](docs/06-technology-decisions.md)
7. [Repository bootstrap](docs/07-repository-bootstrap.md)
8. [Design defence guide](docs/08-design-defence.md)

## Repository structure

```text
flowforge-engine/
├── flowforge-engine/          # reusable domain-independent library
├── voltops-reference/         # domain-specific library / adapter
├── flowforge-application/     # sole executable Spring Boot application
├── docs/                      # design and review material
├── .github/workflows/         # CI checks
├── docker-compose.yml         # PostgreSQL and RabbitMQ
├── mvnw                       # portable Maven wrapper
├── pom.xml                    # Maven multi-module parent
└── README.md
```

## Deployment model

The initial architecture is a modular monolith with one executable Spring Boot process:

```text
flowforge-application
    |\
    | +--> PostgreSQL
    |
    +----> RabbitMQ

worker-1 -----> flowforge-application
worker-2 -----> flowforge-application
worker-3 -----> flowforge-application
```

`flowforge-engine` is a reusable library. `voltops-reference` is a domain library/adapter. `flowforge-application` is the sole executable module.

## Design boundary

The engine owns domain-independent orchestration semantics such as workflow definitions, task definitions and instances, dependency validation, worker sessions, leases, fencing, retries, idempotency, outbox records, and recovery.

VoltOps owns electrical-maintenance concepts and remains outside the reusable engine module.

The engine module must not contain electrical-domain terms. CI enforces this boundary.

## Ownership model

Workers acquire tasks through capability-based API polling backed by PostgreSQL.

RabbitMQ is not the ownership mechanism. It carries asynchronous workflow and domain events after durable state changes.

A task result is accepted only when task ID, worker session ID, lease ID, fencing generation, current task state, and lease validity match current durable ownership.

## Failure model

The design explicitly handles:

- concurrent claims
- worker failure before or during execution
- lease expiry and reassignment
- stale worker results
- duplicate message delivery
- uncertain external side effects
- outbox publish-before-mark crashes
- broker unavailability
- orchestrator restart
- task timeout
- retry exhaustion
- concurrent recovery schedulers

## Technology

- Java 21
- Spring Boot 4.1.1
- Maven 3.9.9
- PostgreSQL
- RabbitMQ
- Docker Compose

## Run locally

```bash
./mvnw clean verify
docker compose up -d --wait
```

Run the executable application with:

```bash
./mvnw -pl flowforge-application spring-boot:run
```

Stop supporting services with:

```bash
docker compose down -v
```

## CI

GitHub Actions runs on pushes to `main` and pull requests targeting `main`.

CI performs:

1. Java 21 setup.
2. Module dependency-direction enforcement.
3. Executable-boundary enforcement.
4. Forbidden-domain-term check inside `flowforge-engine`.
5. `./mvnw -B clean verify`.
6. Docker Compose startup with health checks.
7. Docker Compose cleanup.

## Current validation

The current revision has passed the CI Maven verification phase, module dependency-direction check, executable-boundary check, and FlowForge domain-boundary check. The Docker infrastructure-health stage is included in the final CI gate.

A fresh local clone should still be used for reviewer evidence. The current execution environment has no outbound Git access, so the local clone itself was not run here.

## Week 1 scope

Week 1 excludes production task execution, worker lease implementation, retry implementation, production outbox publishing, frontend dashboard, metrics infrastructure, Kubernetes, and cloud deployment.

Implementation begins only after the design-review gate is approved.

## Review preparation

Use [the design defence guide](docs/08-design-defence.md) for the closed-book review. Be prepared to explain task claiming, lease expiry, fencing, duplicate delivery, external side effects, outbox crash windows, concurrent recovery, definition-versus-instance modeling, timeout handling, cancellation, and the PostgreSQL/RabbitMQ responsibility boundary.

## License

Apache License 2.0. See `LICENSE`.
