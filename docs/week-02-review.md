# Week 2 Review

## Review basis

This document was updated against the current `main` branch implementation and test source. The repository contains the workflow-definition module, PostgreSQL migration, DAG validator, REST controller/service, unit tests, integration tests, and the five Week 2 documentation files.

A fresh test execution was not performed during this review because the container could not resolve GitHub for cloning. Therefore this document distinguishes code and test evidence present in the repository from tests independently executed during this review.

## Work completed

The current repository contains:

- workflow-definition input and stored-definition models
- workflow, task, dependency, and retry-policy persistence tables
- Flyway migration for the PostgreSQL definition schema
- JDBC repository for transactional persistence and retrieval
- workflow definition validator
- Kahn-based DAG validation with iterative cycle-path recovery
- DRAFT, PUBLISHED, and RETIRED lifecycle operations
- create, update, publish, retrieve, list, and retire REST endpoints
- consistent API error handling with correlation IDs
- unit tests for the required DAG and validation scenarios
- PostgreSQL Testcontainers integration tests
- workflow-definition, DAG, lifecycle, API, and review documentation

The assignment explicitly requires tasks and dependencies to be persisted together, concurrent duplicate-version creation to result in one success, and PostgreSQL rather than H2 for the concurrency and constraint tests. The repository's integration test implements a PostgreSQL Testcontainers database and a ten-request concurrent version-creation test. fileciteturn0file0L257-L261

## Design changes from Week 1

The Week 1 proposal was refined into definition-level persistence:

```text
workflow_definition
    |
    +-- task_definition
    |       |
    |       +-- retry_policy
    |
    +-- task_dependency
```

Execution concepts remain outside the Week 2 implementation. The API boundary therefore registers reusable definitions rather than creating workflow instances.

The implementation uses explicit JDBC SQL for concurrency-sensitive persistence operations. Lifecycle operations use transactions and row locks through `SELECT ... FOR UPDATE`. fileciteturn13file0L2-L6 fileciteturn17file0L2-L6

## Problems encountered and resulting decisions

### Definition versus execution

A reusable task definition must not be treated as an execution record. Keeping the definition separate gives each workflow version a stable structure for future workflow instances.

### Cycle reporting

A boolean acyclic check does not provide a useful API diagnostic. Kahn's algorithm performs the linear cycle test. When a cycle remains, iterative DFS reconstructs one concrete cycle path. The explicit stack avoids recursive call-stack depth risk. fileciteturn10file0L2-L6

### Concurrent version creation

Application validation cannot prevent two simultaneous requests from both observing an unused version. PostgreSQL therefore enforces `UNIQUE(workflow_key, version)`, while the service translates the resulting duplicate-key exception into `WORKFLOW_VERSION_EXISTS`. fileciteturn12file0L2-L6 fileciteturn13file0L2-L6

### Atomic persistence

Workflow creation inserts the workflow header, retry policies, task definitions, and dependency edges under one transaction. The integration suite includes a failure-after-header scenario and verifies that the workflow and tasks do not remain after rollback. fileciteturn16file0L2-L6

## Rejected alternatives

- H2 integration testing was rejected because the assignment requires PostgreSQL evidence for constraint and concurrency behaviour.
- Recursive DFS only was rejected because iterative traversal avoids Java recursion-depth risk for deep workflows.
- Mutable published definitions were rejected because version identity must remain stable for future workflow instances.
- Execution and worker concepts were kept out of Week 2 because the assignment explicitly excludes workflow execution, task claiming, workers, leases, retry execution, and VoltOps logic. fileciteturn0file0L13-L14

## Test coverage present in the repository

### Unit tests

`WorkflowDefinitionValidatorTest` covers:

- valid linear DAG
- valid parallel and multiple-root DAG
- empty workflow
- duplicate task key
- missing dependency
- self-dependency
- two-task cycle with asserted cycle path
- indirect cycle
- unsupported task type
- invalid timeout
- invalid retry policy

The test source is present in the engine module. fileciteturn15file0L2-L6

### Integration tests

`WorkflowDefinitionIntegrationTest` covers:

- valid draft persistence and retrieval
- duplicate workflow key and version
- draft update
- published immutability
- retirement
- version 2 independence from version 1
- retrieval after retirement
- transactional rollback after partial persistence failure
- ten concurrent creation requests producing one stored version

The test uses PostgreSQL Testcontainers rather than H2. fileciteturn16file0L2-L6

### API test coverage

`WorkflowDefinitionControllerTest` currently verifies the create endpoint, including HTTP 201, the `Location` header, returned workflow key, and DRAFT status. Additional endpoint-level MockMvc tests for update, publish, retrieve, list, retire, and error responses would strengthen the suite before submission. fileciteturn20file0L2-L6

## Known limitations and assignment gaps

### Task-definition created timestamp

The assignment lists `created_at` on Task Definition. The current PostgreSQL migration does not contain `task_definition.created_at`. fileciteturn0file0L43-L48 fileciteturn12file0L2-L6

This is the clearest schema-level mismatch and should be fixed if the reviewer expects every listed field.

### Retry-policy versioning

Each `retry_policy_id` is treated as an immutable policy version. Policy content is never updated in place. A policy change creates a new row and policy identity for future task definitions. Draft replacement now removes the orphaned policy rows belonging to the replaced draft.

### Full JSON Schema validation

`inputSchema` is stored as JSONB and size-limited, but the current validator does not validate the document as a complete standards-compliant JSON Schema. This is outside the mandatory Week 2 field validation unless a stronger schema contract is chosen later.

### Direct cycle test naming

The unit suite explicitly tests self-dependency, a two-task cycle, and an indirect cycle. The assignment separately lists a direct cycle test. The self-dependency test covers the one-node direct cycle case, but a separately named assertion would make the mapping to the checkpoint clearer. fileciteturn0file0L233-L246

### Fresh test execution

A fresh repository checkout and a passing post-fix GitHub Actions run are required before the Week 2 checkpoint is described as fully verified.

### Documentation history

The Week 2 documentation was aligned with the implementation in separate commits for the workflow format, DAG validation, lifecycle, and API contract. This also makes the documentation changes easy to review in Git history. fileciteturn27file0L2-L2

## Unresolved questions

- Should task definitions include `created_at` in the database schema?
- Should retry policies have explicit version or ownership metadata?
- Should superseded retry policies be deleted, retained, or garbage-collected?
- Which JSON Schema dialect should future semantic validation support?
- Should version listing gain pagination for large histories?
- What authentication, authorization, and tenancy model will apply to definition APIs?
- How should definition diffs and audit history be represented?

## Plan for Week 3

1. Introduce workflow-instance creation from an immutable published definition.
2. Keep each instance attached to the exact workflow definition version selected at creation time.
3. Materialize task instances from task definitions and dependency edges.
4. Implement initial task-readiness evaluation.
5. Add PostgreSQL integration coverage for instance creation and definition-version immutability.
6. Preserve the Week 1 ownership and fencing design while keeping worker execution outside the definition layer.

## Week 2 exit assessment

The codebase contains the core Week 2 implementation and the requested test categories. The remaining submission action is to record a current green CI run after the audit fixes. The task `created_at` schema mismatch and API-level coverage gaps have been addressed in the audit changes.

The assignment's final review also expects the implementation to be defensible without notes, including why definitions are separate from instances, why published versions are immutable, how cycle detection works, how concurrent version creation is prevented, how partial persistence rolls back, and why PostgreSQL constraints remain necessary alongside Java validation. fileciteturn0file0L332-L345
