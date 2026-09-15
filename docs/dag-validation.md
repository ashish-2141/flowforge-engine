# DAG Validation

## Goal

A workflow definition is valid only when every dependency points to an existing task, no task depends on itself, duplicate dependency edges are absent, and the directed graph contains no cycle.

## Implemented algorithm

FlowForge uses Kahn's topological-sort test for the acyclic check. When Kahn's pass detects a cycle, the implementation runs an iterative depth-first traversal to recover one concrete cycle path for the API error.

The graph is represented as `task -> prerequisites`, matching the `dependsOn` field in the request model. Because the implementation treats prerequisites as outgoing graph edges, Kahn's indegree bookkeeping removes nodes whose prerequisite-side count reaches zero. This still gives the required cycle test because every edge is processed exactly once.

## Pseudocode

```text
validate(tasks):
  validate task keys, dependencies and local fields
  reject structural errors before DAG analysis

  graph = task -> list of dependencies
  indegree[node] = number of incoming graph edges
  enqueue every node with indegree 0

  processed = 0
  while queue is not empty:
      node = dequeue()
      processed++
      for dependency in graph[node]:
          indegree[dependency]--
          if indegree[dependency] == 0:
              enqueue(dependency)

  if processed == number of nodes:
      accept graph
  else:
      cycle = iterative DFS cycle-path recovery
      reject with WORKFLOW_CYCLE_DETECTED and cycle
```

## Complexity

Let V be the number of tasks and E be the number of dependency edges.

- Graph construction: O(V + E).
- Kahn pass: O(V + E).
- Iterative DFS path recovery: O(V + E) in the cycle case.
- Overall worst-case time: O(V + E).
- Additional graph, queue, color, stack, and path storage: O(V + E).

The validator also limits a workflow to 1000 tasks.

## Why Kahn detects a cycle

In an acyclic graph, repeated removal of zero-indegree nodes eventually processes every node. When processing stops early, at least one cyclic region remains. The implementation then recovers a concrete cycle path instead of returning only a boolean failure.

Missing dependencies and self-dependencies are validated before cycle analysis. Multiple roots are allowed.

## Cycle reporting

Example response details:

```json
{
  "code": "WORKFLOW_CYCLE_DETECTED",
  "message": "The workflow contains a dependency cycle.",
  "details": {
    "cycle": [
      "validate-document",
      "review-document",
      "publish-result",
      "validate-document"
    ]
  }
}
```

The iterative DFS uses a color state of `0 = unvisited`, `1 = active`, and `2 = completed`. Finding an edge to an active node identifies a back edge. The active path is sliced from the repeated node and closed by appending the repeated node again.

## Invalid examples

Self-dependency:

```text
A -> A
```

Two-task cycle:

```text
A depends on B
B depends on A
```

Indirect cycle:

```text
A depends on C
B depends on A
C depends on B
```

Other structural DAG errors:

```text
A depends on missing-task
```

```text
A depends on B
A depends on B
```

## Valid examples

Linear DAG:

```text
A
|
v
B
|
v
C
```

Parallel branches:

```text
    B
   /
  A
   \
    C
```

Multiple roots are valid:

```text
A     B
 \   /
   C
```

## Recursion depth

Cycle-path recovery is iterative. It uses an explicit stack of frames, so a long workflow does not consume the Java call stack through recursive DFS.

## Test coverage in the repository

`WorkflowDefinitionValidatorTest` covers linear DAG acceptance, parallel and multiple-root acceptance, missing dependencies, self-dependencies, a two-task cycle with an asserted cycle path, and an indirect cycle. fileciteturn15file0L2-L6

The repository also contains the Week 2 integration test suite using PostgreSQL Testcontainers. Test execution was not run in this environment because the repository could not be cloned through the container network, so the documentation records test presence rather than claiming a fresh local pass. fileciteturn16file0L2-L6
