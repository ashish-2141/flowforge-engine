# DAG Validation

## Algorithm

FlowForge uses Kahn's topological-sort test to determine whether the dependency graph is acyclic. The validator also runs an iterative depth-first walk when Kahn detects a cycle, so the API can report one concrete cycle path without relying on recursive call-stack depth.

For each task definition, the graph contains an edge from the task to each prerequisite listed in `dependsOn`.

## Pseudocode

```text
validate(tasks):
  validate task keys, dependencies and local fields
  if any structural error exists: return errors

  build adjacency map task -> prerequisites
  compute indegree for every node
  enqueue every node with indegree 0

  processed = 0
  while queue is not empty:
      node = dequeue()
      processed++
      for prerequisite in adjacency[node]:
          indegree[prerequisite]--
          if indegree[prerequisite] == 0:
              enqueue(prerequisite)

  if processed == number_of_nodes:
      accept graph
  else:
      cycle = iterativeDfsCyclePath(adjacency)
      reject with WORKFLOW_CYCLE_DETECTED and cycle
```

## Complexity

Let V be the number of task definitions and E be the number of dependency edges.

- Building the adjacency structure: O(V + E).
- Kahn's topological-sort pass: O(V + E).
- Iterative DFS path recovery when a cycle exists: O(V + E).
- Total worst-case time: O(V + E).
- Space: O(V + E).

The two passes remain linear. The maximum-task limit is 1000 in Week 2, so the graph is bounded.

## Why Kahn works

A directed acyclic graph always has at least one node with indegree zero. Removing such nodes repeatedly eventually removes every node. If some nodes remain, they belong to a cyclic dependency region.

FlowForge validates missing dependencies and self-dependencies before the cycle pass. Multiple root tasks are valid.

## Cycle reporting

Example:

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

The returned path starts at a repeated node and ends at the same node, making the cycle visible to the caller.

## Invalid examples

Direct cycle:

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

Other graph errors are reported before cycle analysis:

- duplicate task key
- missing dependency
- self-dependency

## Valid examples

Linear:

```text
A -> B -> C
```

Parallel branches:

```text
      -> B ->
A              D
      -> C ->
```

Multiple roots are accepted:

```text
A     B
 \   /
   C
```

## Recursion risk

Cycle path reporting uses an explicit stack rather than recursive DFS. A workflow with many tasks therefore does not consume the Java thread stack during cycle reporting.
