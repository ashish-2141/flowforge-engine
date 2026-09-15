# Problem Discovery

## 1. Problem statement

FlowForge is a reusable workflow orchestration platform for long-running workflows made of dependent tasks. The platform must preserve correct workflow progress while workers, application processes, networks, databases, and message delivery fail or become delayed.

The problem is not simply starting jobs. The problem is deciding which work is eligible, recording durable progress, coordinating competing workers, recovering incomplete work, and ensuring that a failure does not create a skipped task, an invalid transition, or an unintended duplicate logical effect.

## 2. Intended users and dependents

### Intended users

- Application teams that need durable orchestration for multi-step business processes.
- Platform engineers who operate workers and the orchestration service.
- Developers who define workflow versions and task capabilities.
- Operators and support staff who investigate stalled or failed workflows.

### Systems that depend on FlowForge output

- Workflow clients that submit commands and query execution state.
- Domain applications such as VoltOps that define business workflows on top of FlowForge.
- Workers that execute domain-specific task handlers.
- Downstream systems that consume workflow lifecycle and task events.
- Operational tooling that needs durable task history and failure information.

## 3. Representative use cases

### Electrical-maintenance example

VoltOps may represent an outage-aware maintenance workflow where approval, parts, assignment, inspection, testing, and restoration are dependent tasks.

### Non-electrical example: invoice approval

An invoice workflow could contain:

```text
Validate Invoice
       |
   +---+---+
   |       |
Fraud   Budget
Check    Check
   |       |
   +---+---+
       |
   v
 Approve
```

FlowForge should orchestrate this workflow without knowing what an invoice, fraud check, or approval means.

Other valid domains include document review, account onboarding, procurement, and compliance workflows.

## 4. Why a normal scheduler is insufficient

A basic scheduler answers when a process should start. It does not establish durable dependency-aware execution across multiple workers, represent task ownership, recover abandoned work, preserve attempt history, coordinate state changes with event publication, or protect against duplicate logical requests.

The missing capability is durable distributed workflow coordination rather than time-based triggering.

## 5. Why long-running distributed workflows are difficult

Workflow state outlives individual processes and often outlives the network session through which a worker interacts with the engine.

A worker can stop making progress without the engine immediately knowing why. The process might have crashed, the network might be partitioned, the worker might be overloaded, or the worker might still be running but no longer hold valid ownership.

The engine therefore has to reason about observable progress and durable ownership rather than assuming process death is directly detectable.

Other difficulties include:

- two workers observing the same eligible task concurrently
- incomplete work after worker or orchestrator restart
- duplicate command or event delivery
- delayed or missing progress signals
- task timeouts where the original operation might still be physically running
- external side effects whose final outcome is uncertain
- changes to workflow definitions while instances are already executing

## 6. Business consequences of incorrect execution

Incorrect orchestration has concrete consequences.

- Duplicate execution may create duplicate approvals, reservations, notifications, charges, or records.
- Skipped execution may leave a business process incomplete while the system incorrectly reports progress.
- Stalled execution may delay customer commitments, maintenance operations, or internal approvals.
- Lost events may leave downstream systems with an incorrect view of committed workflow state.
- Incorrect recovery may cause two workers to continue from conflicting assumptions about ownership.

The engine therefore needs explicit correctness properties instead of relying on eventual operator inspection.

## 7. Core correctness invariants

The platform must never violate these invariants:

1. A task has at most one authoritative current owner.
2. A task result is accepted only for the current ownership generation and valid task state.
3. A terminal task is not rerun during normal recovery.
4. A workflow cannot complete while a required predecessor has not successfully completed.
5. A dependency cycle cannot become an executable workflow instance.
6. A committed engine state change that requires an event has a durable event record.
7. Repeated processing of the same logical request must not create more than one protected logical outcome.
8. A retry policy does not silently change for an already-running workflow instance.
9. Historical task attempts remain auditable after retries or reassignment.

## 8. Idempotency, reconciliation, and compensation

These concepts address different problems.

Idempotency means repeated requests for the same logical operation produce the same protected business outcome rather than applying the operation multiple times.

Reconciliation means querying an external system or durable status source to determine what actually happened after an uncertain operation, for example when a worker completed an external action and crashed before reporting success.

Compensation means performing a defined corrective action when an earlier operation cannot simply be repeated safely, for example reversing or cancelling a previously committed business action.

Not every external operation supports all three mechanisms. The integration contract must state which mechanism applies.

## 9. Assumptions

- A durable transactional relational database is available to the engine.
- Workers expose a session identity for each running process session. Session identity is not assumed to survive a process restart.
- Workers provide periodic progress or heartbeat signals while they hold ownership.
- Message delivery is allowed to be at least once.
- Workflow definitions are versioned and immutable after execution instances reference a version.
- Domain integrations document how their external side effects are made idempotent, reconciled, or compensated.

## 10. Non-goals for Week 1

Week 1 does not implement:

- production workflow execution
- real worker lease handling
- retry execution logic
- production outbox publishing
- external side-effect adapters
- frontend or dashboard
- metrics infrastructure
- Kubernetes configuration
- cloud deployment
- horizontal production scaling
- authentication and authorization design beyond future integration points

## 11. Unresolved design questions

The Week 1 design still requires implementation-level decisions for:

- the exact SQL claim strategy and locking clause
- worker-session registration and expiry semantics
- lease duration and heartbeat intervals
- exact retry backoff calculation
- task timeout enforcement and executor interruption behavior
- the ownership of retry-policy evaluation between the coordinator and retry component
- outbox publisher batch size and polling interval
- outbox publication claim strategy
- external side-effect integration contracts
- retention periods for idempotency and historical execution data

These are identified explicitly so implementation decisions remain reviewable rather than being hidden assumptions.

## 12. Success criteria

The completed platform should support durable workflow definitions and instances, dependency-aware task execution, multiple capable workers, safe reassignment, stale-owner rejection, duplicate-safe logical operations, restart recovery, retry and dead-letter behavior, and reconstructable execution history.
