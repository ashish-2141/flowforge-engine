# Definition Lifecycle

## State model

```text
DRAFT -> PUBLISHED -> RETIRED
  |
  +----> DRAFT
```

The implemented valid transitions are DRAFT to PUBLISHED and PUBLISHED to RETIRED. A DRAFT remains editable through the update endpoint.

## DRAFT

Creation stores a workflow definition with status `DRAFT`. Structural validation runs before persistence through `WorkflowDefinitionValidator`.

A DRAFT may be updated. The update service acquires a row lock, verifies the current status, deletes and replaces the child task and dependency records, then commits the replacement inside one transaction.

## PUBLISHED

Publishing acquires a row lock, reconstructs the definition from PostgreSQL, runs the full validator again, and changes the status only after validation succeeds.

Once published, the update operation rejects modification with `PUBLISHED_DEFINITION_IMMUTABLE`. The database identity `(workflow_key, version)` cannot be overwritten because PostgreSQL enforces a unique constraint.

The published row is therefore the immutable definition version future workflow instances will reference.

## RETIRED

Retirement also acquires a row lock and changes only a PUBLISHED definition to `RETIRED`. A retirement attempt against another state receives `INVALID_DEFINITION_TRANSITION` from the repository.

Retired definitions remain retrievable. Future workflow-instance creation, which is outside Week 2, must exclude retired definitions.

## Versioning

PostgreSQL enforces:

```text
UNIQUE(workflow_key, version)
```

Application code also converts a duplicate insert into `WORKFLOW_VERSION_EXISTS`. This matters for concurrency because two requests can pass Java validation before either reaches the database. The unique constraint is the final guard.

Example:

```text
workflowKey = document-approval
version 1 = PUBLISHED
version 2 = DRAFT
```

Version 2 is a separate `workflow_definition` row with separate task, dependency, and retry-policy records.

## Transaction rules

Definition creation is transactional. The workflow header, retry policies, task definitions, and dependency edges are inserted within one transaction. A failure during child insertion causes the transaction to roll back.

Draft replacement is also transactional. Existing dependency and task rows are replaced before the new child records are inserted. This prevents a partial update from becoming visible as a committed definition.

## Database constraints versus Java validation

Java validation provides domain-specific errors before persistence. PostgreSQL constraints still enforce invariants at the storage boundary. Current examples include positive versions, valid lifecycle status, positive timeouts, retry-policy ranges, unique workflow versions, unique task keys, unique dependency edges, same-workflow dependency endpoints, and no self-dependency.

This two-layer design protects the model from concurrent requests and from future code paths or direct SQL that bypass Java validation.

## Known implementation detail

Retry policies are separate rows referenced by task definitions, but the current schema does not give a retry policy its own version number. Updating a DRAFT creates new retry-policy rows for its replacement task definitions. Superseded rows are not deleted by the current replacement code, so retention or cleanup remains a later design task.
