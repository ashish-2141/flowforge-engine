# Workflow Definition Format

Week 2 introduces a definition-only contract. A workflow definition describes reusable structure. It does not represent an execution.

## Example

```json
{
  "workflowKey": "document-approval",
  "version": 1,
  "name": "Document Approval Workflow",
  "description": "Reviews and approves a submitted document",
  "inputSchema": { "type": "object", "required": ["documentId"] },
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
      "dependsOn": ["validate-document"]
    }
  ]
}
```

## Limits

- Maximum tasks: 1000.
- Maximum workflow key length: 128 characters.
- Maximum task key length: 128 characters.
- Maximum description length: 2000 characters.
- Maximum input-schema or task-configuration serialized size: 64 KiB.
- Supported task types: `AUTOMATED`, `WORKER`.
- `timeoutSeconds` must be greater than zero.
- Retry `maximumAttempts` must be at least 1.
- Retry `initialDelaySeconds` must be non-negative.
- Retry `backoffMultiplier` must be at least 1.0.
- Retry `maximumDelaySeconds` must not be less than the initial delay.

These limits keep definition validation predictable and prevent accidentally oversized definitions. They are implementation-stage safety limits and can be revisited with evidence.

## Storage mapping

- `workflow_definition` stores workflow-level fields and lifecycle state.
- `task_definition` stores task fields and points to an immutable retry policy version.
- `task_dependency` stores edges between task definitions inside the same workflow definition.
- `retry_policy` stores retry configuration.

A published definition is immutable. A later version creates a new definition record rather than modifying the old version.
