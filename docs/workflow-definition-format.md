# Workflow Definition Format

## Purpose

Week 2 registers reusable workflow definitions. A definition describes tasks, retry rules, input schema, and dependencies. It does not represent a running workflow instance.

## Request format

```json
{
  "workflowKey": "document-approval",
  "version": 1,
  "name": "Document Approval Workflow",
  "description": "Reviews and approves a submitted document",
  "inputSchema": {
    "type": "object",
    "required": ["documentId"]
  },
  "tasks": [
    {
      "taskKey": "validate-document",
      "name": "Validate Document",
      "taskType": "AUTOMATED",
      "requiredCapabilities": ["DOCUMENT_VALIDATION"],
      "timeoutSeconds": 60,
      "retryPolicy": {
        "maximumAttempts": 3,
        "initialDelaySeconds": 5,
        "backoffMultiplier": 2,
        "maximumDelaySeconds": 60,
        "jitterEnabled": true
      },
      "configuration": {},
      "dependsOn": []
    },
    {
      "taskKey": "review-document",
      "name": "Review Document",
      "taskType": "WORKER",
      "requiredCapabilities": ["DOCUMENT_REVIEW"],
      "timeoutSeconds": 300,
      "retryPolicy": {
        "maximumAttempts": 2,
        "initialDelaySeconds": 10,
        "backoffMultiplier": 2,
        "maximumDelaySeconds": 60,
        "jitterEnabled": false
      },
      "configuration": {},
      "dependsOn": ["validate-document"]
    }
  ]
}
```

## Validation rules implemented

Workflow-level rules:

- `workflowKey` is required and must not be blank.
- `version` must be greater than zero.
- `name` is required.
- At least one task is required.
- Maximum tasks: 1000.
- Maximum workflow key length: 128 characters.
- Maximum description length: 2000 characters.
- `inputSchema` is limited to 64 KiB after UTF-8 serialization.

Task-level rules:

- `taskKey` is required and limited to 128 characters.
- Task keys must be unique within a definition.
- `taskType` must be `AUTOMATED` or `WORKER`.
- `timeoutSeconds` must be greater than zero.
- At least one non-blank required capability is required.
- `configuration` is limited to 64 KiB after UTF-8 serialization.
- Retry policy must be complete.
- `maximumAttempts` must be at least 1.
- `initialDelaySeconds` must be non-negative.
- `backoffMultiplier` must be at least 1.0.
- `maximumDelaySeconds` must be greater than or equal to `initialDelaySeconds`.
- Duplicate dependency edges are rejected.
- Dependencies must reference existing task keys.
- A task cannot depend on itself.
- The dependency graph must be acyclic.

These rules are implemented by `WorkflowDefinitionValidator` and `DagValidator`.

## Persistence mapping

The PostgreSQL migration creates four definition-related tables:

```text
workflow_definition
    |
    +-- task_definition
    |      |
    |      +-- retry_policy
    |
    +-- task_dependency
```

`workflow_definition` stores the workflow key, version, name, description, lifecycle status, JSON input schema, creation time, and publication time.

`task_definition` stores each task and belongs to one workflow definition through a foreign key. Required capabilities and configuration are stored as JSONB.

`task_dependency` stores dependency edges. Composite foreign keys ensure both endpoints belong to the same workflow definition. The primary key prevents duplicate edges, and a database check prevents self-dependencies.

`retry_policy` stores retry values referenced by task definitions. The current schema does not contain a retry-policy version column. Policies created while replacing a draft therefore behave as child records rather than named policy versions.

## Versioning and immutability

`UNIQUE(workflow_key, version)` is enforced by PostgreSQL. Version 2 is stored as a separate `workflow_definition` row, so changes to version 2 do not modify version 1.

Published definitions are protected by the service and repository lifecycle rules. Updates are permitted only for DRAFT definitions. Publication and retirement use row locking and transactional state changes.

## Assignment alignment notes

The assignment lists `created_at` on task definitions. The current migration does not store `task_definition.created_at`. This is a schema-level deviation and should be addressed before the final Week 2 submission if the field is required by review.

The assignment uses a singular `required_capability` field, while the implemented request and database model use `requiredCapabilities` as a list. The implemented list matches the assignment's example request and supports multiple capabilities.

The definition input record normalizes a null `inputSchema` to an empty map and a null task list to an empty list. Validation then rejects an empty task list.
