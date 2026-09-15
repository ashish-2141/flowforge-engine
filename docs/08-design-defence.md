# Week 1 Design Defence Guide

This guide is for the closed-book design defence. It captures the reasoning expected from the Week 1 architecture and the questions most likely to expose inconsistencies.

## 1. Core architecture in one minute

FlowForge is a domain-independent workflow orchestration engine. A workflow definition is immutable and produces many workflow instances. A workflow definition contains task definitions and dependency edges. Each instance gets task instances, and each task execution gets a durable attempt record.

PostgreSQL is the durable consistency boundary for engine-owned state. Workers acquire ownership through capability-based API polling. A task lease is time-bounded. Each ownership generation gets a monotonic fencing generation stored on the task instance. RabbitMQ carries asynchronous workflow and domain events through a transactional outbox. RabbitMQ does not grant task ownership.

## 2. Explain task claiming

Two workers may ask for the same READY task. The database performs one atomic claim. One worker session gets the ownership generation and lease. The other receives no claim.

Key statement:

> The database decides ownership. The broker does not.

## 3. Explain lease expiry

A lease records time-bounded ownership. The worker sends heartbeats. The engine cannot directly observe a worker crash, so it observes missed progress and an expired lease.

An expired lease does not mean the original code physically stopped. The engine records a `LEASE_LOST` attempt outcome and evaluates the retry policy.

## 4. Explain fencing

Suppose worker session S1 owns a task at fencing generation 7. Its lease expires. S2 receives generation 8. If S1 later submits generation 7, the engine rejects it because the authoritative generation is now 8.

Fencing protects engine-controlled writes. It does not reverse an external side effect already accepted by another system.

## 5. Explain duplicate delivery

At-least-once delivery means duplicates are expected. A duplicate event is not the same thing as a task retry. Consumers deduplicate events using stable message identity or an application idempotency key. A FlowForge task retry occurs only after an execution attempt has failed, timed out, or lost ownership and the retry policy allows another attempt.

## 6. Explain the outbox crash window

The task state change and outbox record commit in one transaction. After commit, the publisher sends the outbox event to RabbitMQ.

If the publisher succeeds at RabbitMQ and crashes before recording `published_at`, the same event is sent again. This is acceptable because consumers must be idempotent.

## 7. Explain external side effects

Idempotency, reconciliation, and compensation are different:

- Idempotency makes repeating the same logical operation safe.
- Reconciliation checks the external system to learn what happened after an uncertain outcome.
- Compensation performs a corrective business operation after a prior effect cannot simply be repeated.

FlowForge does not assume every downstream system supports all three.

## 8. Explain the definition/instance split

A workflow definition describes structure and behavior. A workflow instance is one execution of one immutable definition version.

Likewise, a task definition describes a logical unit of work, while a task instance represents that task within one workflow execution.

Dependencies belong to task definitions because the graph is part of the workflow definition. Attempts and leases belong to task instances because they describe execution history.

## 9. Explain retry and timeout

A failure is an attempt outcome, not a resting task state.

Correct path:

```text
RUNNING
  -> attempt outcome
  -> retry-policy evaluation
  -> RETRY_WAIT
  -> READY
```

or:

```text
RUNNING
  -> attempt outcome
  -> retry-policy evaluation
  -> DEAD_LETTERED
```

Timeout records `TIMED_OUT`. Lease loss records `LEASE_LOST`.

## 10. Explain orchestrator restart

The application rebuilds decisions from PostgreSQL. It does not trust in-memory execution context. A task with an unexpired lease remains owned. An expired lease enters the recovery path. A task already marked `SUCCEEDED` remains terminal.

## 11. Explain concurrent recovery

Multiple recovery schedulers may observe the same expired task. The recovery transition is conditional on the current state, expiry, and ownership generation. Only one transition changes the task. Other schedulers see no successful update and stop.

## 12. Explain cancellation

Cancellation is not assumed to stop an executing external operation immediately. The workflow enters `CANCELLING`. New work is prevented. Existing work is resolved under the cancellation policy. The workflow reaches `CANCELLED` only after the defined convergence conditions are met.

## 13. Questions that require precise answers

Why polling?

Because ownership remains explicit in the API/database path, and worker demand controls backpressure. The trade-offs are polling latency, empty queries, database load, thundering herd, and fairness.

Why PostgreSQL coordination?

Because workflow state, ownership, fencing, attempts, idempotency, and outbox state need one durable consistency boundary.

Why JdbcTemplate?

Because the claim, renewal, recovery, and fenced-result paths require explicit SQL and predictable concurrency semantics.

Why modular monolith?

Because the first implementation needs simple transactions and debugging. The architecture still separates reusable engine and domain modules and keeps workers independent.

Why not claim exactly-once?

Because end-to-end exactly-once execution is not guaranteed across databases, brokers, worker crashes, retries, and arbitrary external side effects. The design guarantees specific engine invariants and uses idempotency and recovery strategies for external boundaries.

## 14. Live-change checklist

A reviewer may introduce a new requirement. During the change, identify its impact on:

1. workflow/task definition
2. workflow/task instance
3. task state transitions
4. attempt history
5. claim condition
6. worker-session model
7. lease/fencing rule
8. retry policy
9. outbox events
10. tests needed to prove the new invariant
