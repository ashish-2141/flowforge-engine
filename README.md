# FlowForge Engine

Fault-tolerant distributed workflow orchestration platform built with Java 21 and Spring Boot.

FlowForge is a domain-independent orchestration engine. VoltOps is a domain-specific reference module used to demonstrate FlowForge with an outage-aware electrical-maintenance workflow.

## Current milestone: Week 2

Week 2 implements the workflow-definition layer. The system accepts definition requests, validates task dependencies as a DAG, persists valid versioned definitions in PostgreSQL, and exposes lifecycle APIs.

Workflow execution, workers, leases, retries, fencing-token execution, outbox publishing, workflow instances, and VoltOps business logic remain out of scope.

## Documentation

### Week 1

1. [Problem discovery](docs/01-problem-discovery.md)
2. [Initial architecture proposal](docs/02-architecture-proposal.md)
3. [Initial data-model proposal](docs/03-data-model-proposal.md)
4. [Workflow and task state-machine proposal](docs/04-state-machine-proposal.md)
5. [Failure analysis](docs/05-failure-analysis.md)
6. [Technology decision note](docs/06-technology-decisions.md)
7. [Repository bootstrap](docs/07-repository-bootstrap.md)
8. [Design defence guide](docs/08-design-defence.md)
9. [Week 1 review evidence](docs/09-review-evidence.md)

### Week 2

10. [Workflow definition format](docs/workflow-definition-format.md)
11. [DAG validation](docs/dag-validation.md)
12. [Definition lifecycle](docs/definition-lifecycle.md)
13. [API contract](docs/api-contract.md)
14. [Week 2 review](docs/week-02-review.md)

## Repository structure

```text
flowforge-engine/
├── flowforge-engine/          # reusable domain-independent library
├── voltops-reference/         # domain-specific library / adapter
├── flowforge-application/     # sole executable Spring Boot application
├── docs/                      # design and review material
├── docker-compose.yml         # PostgreSQL and RabbitMQ
├── mvnw                       # portable Maven wrapper
├── pom.xml                    # Maven multi-module parent
└── README.md
```

## Week 2 architecture boundary

```text
Workflow Client
      |
      v
flowforge-application
      |
      +---- PostgreSQL
      |
      +---- flowforge-engine validator
```

RabbitMQ is not used for definition persistence or task ownership. It remains reserved for the asynchronous event path planned for later runtime work.

## Definition APIs

```text
POST /api/v1/workflow-definitions
PUT  /api/v1/workflow-definitions/{workflowKey}/versions/{version}
POST /api/v1/workflow-definitions/{workflowKey}/versions/{version}/publish
GET  /api/v1/workflow-definitions/{workflowKey}/versions/{version}
GET  /api/v1/workflow-definitions/{workflowKey}/versions
POST /api/v1/workflow-definitions/{workflowKey}/versions/{version}/retire
```

Definitions use immutable `(workflowKey, version)` identities. Drafts are editable. Published definitions are immutable. Retired versions remain retrievable.

## DAG validation

The engine validates:

- missing or blank workflow fields
- duplicate task keys
- unsupported task types
- invalid timeouts
- invalid retry policies
- missing dependencies
- self-dependencies
- duplicate dependency edges
- direct, two-task, and indirect cycles
- documented safety limits

Cycle detection uses Kahn's topological-sort test and an iterative DFS for a useful cycle path. The algorithm is O(V + E) time and O(V + E) space.

## Local development

Start PostgreSQL and RabbitMQ:

```bash
docker compose up -d --wait
```

Run all unit and PostgreSQL/Testcontainers integration tests:

```bash
./mvnw clean verify
```

Run the application:

```bash
./mvnw -pl flowforge-application spring-boot:run
```

The API listens on port 8080 by default.

Stop supporting services:

```bash
docker compose down -v
```

## CI

GitHub Actions runs on pushes to `main` and pull requests targeting `main`.

CI checks Java 21, Maven module dependency direction, executable-module boundaries, the FlowForge domain boundary, full Maven tests, PostgreSQL-backed Testcontainers tests, Docker Compose health, and cleanup.

## Week 2 exit target

The milestone is complete when the repository demonstrates valid definition persistence, all required definition and DAG rejections, draft update, publication immutability, independent version creation, PostgreSQL integration tests, consistent error responses, and no premature workflow execution logic.

## License

Apache License 2.0. See `LICENSE`.
