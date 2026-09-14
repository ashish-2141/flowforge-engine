# FlowForge Engine

Fault-tolerant distributed workflow orchestration platform built with Java 21 and Spring Boot.

FlowForge is a domain-independent orchestration engine. VoltOps is the reference application used to demonstrate the engine with an outage-aware electrical-maintenance workflow.

## Week 1 status

Week 1 is a discovery and system-design phase. Production workflow execution is intentionally not implemented yet.

The Week 1 design is built around durable state, atomic task claiming, leases, fencing tokens, idempotency, transactional outbox delivery, and restart recovery.

## Deliverables

1. [Problem discovery](docs/01-problem-discovery.md)
2. [Initial architecture proposal](docs/02-architecture-proposal.md)
3. [Initial data-model proposal](docs/03-data-model-proposal.md)
4. [Workflow and task state-machine proposal](docs/04-state-machine-proposal.md)
5. [Failure analysis](docs/05-failure-analysis.md)
6. [Technology decision note](docs/06-technology-decisions.md)
7. [Repository bootstrap](docs/07-repository-bootstrap.md)

## Repository structure

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

The FlowForge module must remain domain-independent. CI checks the module for forbidden electrical-domain terms.

## Architecture at a glance

```text
Workflow Client / VoltOps
          |
          v
    FlowForge API
          |
    Workflow Coordinator
          |
   +------+------+
   |             |
   v             v
PostgreSQL    Workers
   |
   +--> Outbox Publisher --> RabbitMQ
```

PostgreSQL is the durable source of truth. RabbitMQ provides delivery. Duplicate delivery is expected and handled through idempotency. Stale worker results are rejected through fencing-token validation.

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

To stop supporting services:

```bash
docker compose down
```

## CI

GitHub Actions runs on pushes to `main` and pull requests targeting `main`.

CI performs:

1. Java 21 setup.
2. FlowForge domain-boundary check.
3. Full Maven verification with tests.

## Failure scenarios

The design addresses:

- concurrent task claiming
- worker crash before execution
- worker crash during execution
- stale worker returning after reassignment
- duplicate message delivery
- database commit followed by broker failure
- broker unavailability
- orchestrator restart
- task timeout
- retry exhaustion

## Week 1 scope

Week 1 excludes production task execution, lease implementation, retry implementation, outbox publisher implementation, frontend dashboard, metrics infrastructure, Kubernetes, and cloud deployment.

Implementation begins only after the design review gate is approved.

## License

Apache License 2.0. See `LICENSE`.
