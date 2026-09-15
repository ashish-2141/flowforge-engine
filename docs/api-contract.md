# Workflow Definition API Contract

## Base path

`/api/v1/workflow-definitions`

## Create draft

`POST /api/v1/workflow-definitions`

Request body: `WorkflowDefinitionInput`.

The service validates the workflow and creates a DRAFT definition. The response is `201 Created` and includes a `Location` header for the stored version.

Example location:

```text
/api/v1/workflow-definitions/document-approval/versions/1
```

## Update draft

`PUT /api/v1/workflow-definitions/{workflowKey}/versions/{version}`

Only DRAFT definitions may be changed. The path workflow key and version must match the request body. The operation revalidates the complete request before replacement.

Response: `200 OK`.

## Publish

`POST /api/v1/workflow-definitions/{workflowKey}/versions/{version}/publish`

The definition is locked with `SELECT ... FOR UPDATE`, reconstructed from PostgreSQL, fully revalidated, and then changed from DRAFT to PUBLISHED.

Response: `200 OK`.

## Retrieve

`GET /api/v1/workflow-definitions/{workflowKey}/versions/{version}`

Returns the complete stored definition, including task definitions, retry configuration, capabilities, configuration, and dependency lists.

Response: `200 OK`. Missing definitions return `DEFINITION_NOT_FOUND` with HTTP 404.

## List versions

`GET /api/v1/workflow-definitions/{workflowKey}/versions`

Returns every stored version for the workflow key, ordered by numeric version.

Response: `200 OK`. When no versions exist, the current service returns `DEFINITION_NOT_FOUND` with HTTP 404.

## Retire

`POST /api/v1/workflow-definitions/{workflowKey}/versions/{version}/retire`

Transitions only a PUBLISHED definition to RETIRED. The repository uses a status-qualified update, so DRAFT and already RETIRED definitions are rejected with `INVALID_DEFINITION_TRANSITION`.

Response: `200 OK`.

## Error response

Every handled API exception uses the same top-level shape:

```json
{
  "timestamp": "2026-09-15T10:30:00Z",
  "status": 400,
  "code": "MISSING_DEPENDENCY",
  "message": "Task 'publish-result' depends on an unknown task.",
  "details": [
    {
      "code": "MISSING_DEPENDENCY",
      "message": "Task 'publish-result' depends on an unknown task.",
      "details": {
        "taskKey": "publish-result",
        "missingTaskKey": "review-document"
      }
    }
  ],
  "correlationId": "f2f6bb52-27ad-4cc1-9e95-e0f8a1c042f7"
}
```

Validation failures return HTTP 400. Lifecycle and duplicate-version failures return their service-defined 409 responses. Missing definitions return 404. Unexpected exceptions return HTTP 500 with `INTERNAL_ERROR`.

`X-Correlation-ID` is accepted from the request. When it is absent or blank, the application generates a UUID for the response.

## Required error codes

```text
INVALID_WORKFLOW_DEFINITION
DUPLICATE_TASK_KEY
MISSING_DEPENDENCY
SELF_DEPENDENCY
WORKFLOW_CYCLE_DETECTED
INVALID_TIMEOUT
INVALID_RETRY_POLICY
WORKFLOW_VERSION_EXISTS
DEFINITION_NOT_FOUND
PUBLISHED_DEFINITION_IMMUTABLE
INVALID_DEFINITION_TRANSITION
```

The validator currently reports unsupported task types, missing names, capability errors, oversized payloads, and duplicate dependency edges using `INVALID_WORKFLOW_DEFINITION` rather than introducing additional codes.

## Validation ownership

- JSON request parsing: Spring MVC and Jackson.
- Definition rules and DAG validation: `WorkflowDefinitionValidator` and `DagValidator`.
- Uniqueness and referential integrity: PostgreSQL constraints.
- Lifecycle checks and concurrency-sensitive operations: `WorkflowDefinitionService` and `WorkflowDefinitionRepository` using transactions and row locks.

## Scope boundary

Week 2 registers definitions only. The current API does not create workflow instances, claim tasks, assign workers, renew leases, publish outbox events, or execute business handlers.

## Current test evidence

The repository contains a controller test for POST creation, including `201 Created`, the `Location` header, and the returned DRAFT status. The integration suite covers creation, duplicate versions, draft update, published immutability, retrieval after retirement, version separation, rollback, and concurrent duplicate-version creation.
