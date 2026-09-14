# FlowForge Engine

Fault-tolerant distributed workflow orchestration platform built with Java 21 and Spring Boot.

FlowForge is a domain-independent orchestration engine. VoltOps is the reference application used to demonstrate the engine with an outage-aware electrical-maintenance workflow.

## Week 1 status

Week 1 is a discovery and system-design phase. Production workflow execution is intentionally not implemented yet.

Deliverables:

1. Problem discovery
2. Initial architecture proposal
3. Initial data-model proposal
4. Workflow and task state-machine proposal
5. Failure analysis
6. Technology decision note
7. Repository bootstrap

## Planned modules

```text
flowforge-engine/
├── flowforge-engine/       # Domain-independent engine
├── voltops-reference/      # Domain-specific reference application
├── docs/                   # Week 1 design artifacts
├── .github/workflows/      # CI checks
├── docker-compose.yml      # Local supporting services
├── pom.xml                # Maven multi-module build
└── README.md
```

## Design boundary

The `flowforge-engine` module owns workflow definitions, DAG validation, task state, worker coordination, leases, fencing, retries, idempotency, outbox delivery, and crash recovery.

The `voltops-reference` module owns electrical-maintenance concepts such as transformer maintenance, shutdown approvals, spare parts, technician assignment, inspections, testing, and power restoration.

The engine module must not contain electrical-domain terms. CI enforces this boundary.

## Technology

- Java 21
- Spring Boot 4.1.1
- Maven
- PostgreSQL
- RabbitMQ
- Docker Compose

## Run locally

```bash
mvn clean verify

docker compose up -d

mvn -pl flowforge-engine spring-boot:run
```

The Week 1 application is intentionally minimal. Supporting services are included for later implementation stages.

## Failure scenarios

The design must handle concurrent task claiming, worker crashes, zombie workers, duplicate delivery, orchestrator restart, broker unavailability, task timeout, and retry exhaustion.

## License

Apache License 2.0. See `LICENSE`.
