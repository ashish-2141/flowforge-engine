# Definition Lifecycle

Workflow definitions have three lifecycle states:

```text
DRAFT -> PUBLISHED -> RETIRED
  ^
  |
DRAFT -> DRAFT
```

## DRAFT

A draft can be created and updated. Structural validation runs on create and update so invalid definitions are rejected early.

## PUBLISHED

Publishing runs full DAG validation before the status changes. After publication:

- task definitions cannot be modified
- dependencies cannot be changed
- retry-policy configuration cannot be silently changed
- the `(workflowKey, version)` identity cannot be overwritten

Publishing is a database state transition protected by a row lock. Two callers cannot both publish different contents of the same definition version.

## RETIRED

A published definition may be retired. New workflow instances must not select a retired version. Existing future runtime instances remain associated with the immutable version they reference.

## Versioning

`UNIQUE(workflow_key, version)` is enforced by PostgreSQL. Application validation provides a friendly error, but the database constraint is the final concurrency guard.

Example:

```text
workflowKey = document-approval
version 1 = PUBLISHED
version 2 = DRAFT
```

Version 2 is a separate `workflow_definition` row. Publishing version 2 does not change version 1.

## Transaction rules

Definition creation stores the workflow row, retry policies, task definitions, and dependency edges inside one transaction.

Definition update locks the draft, replaces its child definition records, and commits the replacement atomically.

If any insert fails, the transaction rolls back so a definition cannot exist with only part of its tasks or dependencies.

## Database versus Java validation

Java validation provides immediate domain-specific error messages. PostgreSQL constraints remain necessary because concurrent requests, direct SQL, application bugs, and future code paths can bypass Java-level assumptions.
