# Workflow Definition API Contract

Base path: `/api/v1/workflow-definitions`

## Create draft

`POST /api/v1/workflow-definitions`

Creates a DRAFT definition after structural and DAG validation.

Response: `201 Created` with a `Location` header for the definition.

## Update draft

`PUT /api/v1/workflow-definitions/{workflowKey}/versions/{version}`

Updates only a DRAFT definition. Path and body identity must match.

Response: `200 OK`.

## Publish

`POST /api/v1/workflow-definitions/{workflowKey}/versions/{version}/publish`

Locks the definition, revalidates its complete graph, then transitions DRAFT to PUBLISHED.

Response: `200 OK`.

## Retrieve

`GET /api/v1/workflow-definitions/{workflowKey}/versions/{version}`

Returns the full stored definition, including task definitions and dependency lists.

## List versions

`GET /api/v1/workflow-definitions/{workflowKey}/versions`

Returns all stored versions ordered by numeric version.

## Retire

`POST /api/v1/workflow-definitions/{workflowKey}/versions/{version}/retire`

Transitions PUBLISHED to RETIRED. The historical definition remains retrievable.

## Error format

Every API error uses one shape:

```json
{
  "timestamp": "2026-09-14T10:30:00Z",
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

Required validation codes:

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

## Ownership of validation

- JSON/request parsing: Spring MVC/Jackson.
- Definition and DAG rules: FlowForge engine validator.
- Uniqueness and referential integrity: PostgreSQL constraints.
- Lifecycle rules: workflow definition service with transactional row locking.

## Scope boundary

These APIs register definitions only. They do not create workflow instances, claim tasks, assign workers, renew leases, publish outbox events, or execute business handlers in Week 2.
