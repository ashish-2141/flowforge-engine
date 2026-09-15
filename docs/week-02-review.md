# Week 2 Review

## Work completed

Week 2 implements the workflow-definition layer without executing workflows or assigning tasks to workers.

Completed:

- reusable workflow/task definition models in `flowforge-engine`
- PostgreSQL definition schema
- Flyway migration
- workflow definition repository using `JdbcTemplate`
- DAG validation and cycle-path reporting
- draft, publish, retrieve, list, update and retire APIs
- validation error contract with correlation IDs
- PostgreSQL/Testcontainers integration coverage
- unit coverage for linear, parallel, multiple-root and invalid DAGs
- concurrent duplicate-version test
- transaction rollback test
- workflow-definition, DAG, lifecycle and API documentation

## Design changes from Week 1

The Week 1 model was refined into definition-level records before runtime implementation:

```text
workflow_definition
    -> task_definition
        -> task_dependency
        -> retry_policy
```

Execution concepts remain separate and are not implemented in Week 2.

The Week 2 repository also follows the Week 1 decision that concurrency-sensitive database operations use explicit JDBC SQL.

## Problems encountered

The main design problem was preserving a clean boundary between reusable definitions and future execution instances. Treating a task definition as an execution record would make versioning and future retries ambiguous.

Another issue was cycle reporting. A boolean acyclic check is insufficient for useful API errors, so Kahn's linear-time test is followed by iterative path recovery when a cycle exists.

A third issue was concurrent version creation. Java validation alone is insufficient because two requests can validate the same `(workflowKey, version)` before either inserts. PostgreSQL uniqueness is therefore the final guard.

## Rejected alternatives

- H2 for integration tests: rejected because Week 2 requires PostgreSQL-specific constraint and concurrency evidence.
- Recursive DFS only: rejected for cycle path reporting because an explicit stack avoids recursion-depth risk.
- Mutable published definitions: rejected because existing future instances must remain attached to the exact definition version they reference.
- RabbitMQ for definition persistence: rejected because definition registration requires transactional relational constraints and is not task ownership.

## Known limitations

- Retry-policy rows are versioned but definition updates can leave superseded policy records for retention cleanup.
- API authentication and authorization are out of scope.
- JSON Schema is stored as JSON but not semantically validated as a full standards-compliant schema in Week 2.
- Maximum definition limits are fixed safety defaults for this phase.
- No workflow instances or worker execution are implemented yet.

## Unresolved questions

- exact JSON Schema validation library and supported schema dialect
- retention policy for superseded retry policies
- API pagination for large version histories
- metrics and audit-event design
- authentication and tenancy model
- definition diff tooling

## Plan for Week 3

1. Implement workflow-instance creation from immutable published definitions.
2. Materialize task instances from task definitions and dependency edges.
3. Implement initial task-readiness evaluation.
4. Add lifecycle-aware instance persistence and API coverage.
5. Add PostgreSQL integration tests for instance creation and definition immutability.
6. Preserve the Week 1 ownership/fencing design without moving worker execution into Week 2 code.

## Exit evidence

The Week 2 exit checkpoint is satisfied by demonstrating:

- valid definition accepted
- duplicate task key rejected
- missing dependency rejected
- self-dependency rejected
- direct cycle rejected with cycle path
- indirect cycle rejected
- draft updated
- published definition rejected on modification
- version 2 stored independently of version 1
- PostgreSQL-backed integration tests pass
- no workflow execution, worker, lease or VoltOps business logic added
