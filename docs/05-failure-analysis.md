# Failure Analysis

## 1. Concurrent task claiming

Detection: two or more worker sessions poll for the same READY task concurrently.

Risk: duplicate ownership or inconsistent execution state.

Recovery: one atomic database claim succeeds. The winning claim records the worker session, lease, and next fencing generation. Losing claim attempts return no ownership.

Testing: create one READY task and issue ten concurrent claim attempts. Assert exactly one successful claim and one active lease.

## 2. Worker crash before execution acknowledgement

Detection: the engine observes missed heartbeats and eventual lease expiry. The crash itself is not directly detectable.

Risk: the worker might have received the task but failed before reporting execution start.

Recovery: after the lease expires, record a `LEASE_LOST` attempt outcome and pass the task through retry-policy evaluation. Do not jump directly to READY.

Testing: claim a task, terminate the worker before its start acknowledgement, allow the lease to expire, then verify attempt history and retry backoff.

## 3. Worker crash during execution

Detection: progress or heartbeat stops and the lease eventually expires. A timeout or lease expiry does not prove the original operation physically stopped.

Risk: the old worker might still be executing while another worker receives a later attempt. An external side effect might already have happened.

Recovery: record `LEASE_LOST`, apply retry policy, and allow reassignment only after the lease expires. Fencing protects FlowForge-owned writes. External effects require integration-specific idempotency, reconciliation, compensation, or manual review.

Testing: start a task, stop heartbeats during execution, allow reassignment, then submit a late result from the old session and assert rejection.

## 4. Worker returns after reassignment

Detection: result submission contains ownership metadata from a previous worker session or previous lease generation.

Risk: stale work overwrites the newer owner's result.

Recovery: atomically validate task ID, worker ID, worker session ID, lease ID, fencing generation, current task state, and lease validity. Reject stale ownership.

Testing: expire lease generation 7, reassign with generation 8, then submit generation-7 result. Assert no task state or engine-owned business state changes.

## 5. Duplicate message delivery

Detection: a consumer receives the same message more than once.

Risk: downstream processing applies the same logical event twice.

Recovery: consumers deduplicate using a stable message/event identity or an application-specific idempotency key.

Testing: publish the same logical event multiple times and assert one protected logical outcome.

Important distinction: broker redelivery is not a FlowForge task retry. Broker redelivery does not itself create a new task attempt.

## 6. Database update succeeds while event publication fails

Detection: the state transaction commits and the outbox event remains unpublished.

Risk: downstream consumers do not receive a committed state change promptly.

Recovery: durable outbox records remain available for retry. Broker recovery allows later publication.

Testing: commit task state and outbox row, make RabbitMQ unavailable, verify the row remains durable, restore RabbitMQ, and verify eventual publication.

## 7. Outbox publisher crashes after successful publish

Detection: RabbitMQ accepted the event but the publisher crashed before recording `published_at`.

Risk: the same outbox record is published again after restart.

Recovery: republish is allowed. Consumers must process duplicate publication idempotently.

Testing: inject a crash between broker acknowledgement and database marking. Restart publisher and verify the consumer receives a duplicate without creating a duplicate logical effect.

## 8. Broker unavailability

Detection: publisher cannot connect, publish, or confirm the broker operation.

Risk: outbox backlog grows and event latency increases.

Recovery: retain records durably, retry with bounded backoff, and expose backlog age/count for later observability.

RabbitMQ delivery never grants task ownership. Task ownership is decided by PostgreSQL/API polling.

## 9. Orchestrator restart

Detection: process termination or deployment causes the application to restart.

Risk: in-memory execution context disappears.

Recovery: rebuild execution decisions from PostgreSQL. Do not steal work with unexpired leases. Expired leases move through `LEASE_LOST` and retry-policy evaluation. Terminal tasks remain terminal. Unpublished outbox events remain publishable.

Testing: restart the application during an in-flight workflow with a mix of READY, RUNNING, RETRY_WAIT, and SUCCEEDED tasks. Verify only eligible non-terminal work resumes.

## 10. Task timeout

Detection: the configured execution duration is exceeded.

Risk: a task remains logically active even though the engine considers the attempt unsuccessful. The original operation might still be physically running.

Recovery: record a `TIMED_OUT` attempt outcome and apply retry-policy evaluation. Do not claim that the original execution has been forcibly stopped unless the worker runtime explicitly confirms cancellation.

Testing: execute a deliberately slow handler, trigger timeout, verify attempt outcome and retry backoff, then submit a late result and assert stale/invalid ownership rejection where applicable.

## 11. Retry exhaustion

Detection: the number of attempts reaches the immutable retry-policy limit.

Risk: infinite retries, silent task loss, or an unclear workflow terminal state.

Recovery: transition the task to `DEAD_LETTERED` with durable diagnostic information. A required dead-lettered task causes the workflow to become `FAILED`. Dependent tasks that require its success become `SKIPPED` or `BLOCKED_FAILED` according to the chosen naming rule.

Testing: repeatedly fail one required task and verify exact attempt count, terminal task state, workflow failure, and blocked dependents.

## 12. Uncertain external side effect

Scenario: a worker performs an external operation, the external system accepts it, and the worker crashes before reporting success.

Risk: retrying blindly could duplicate the external effect. The engine cannot infer the external system's final state from the worker crash alone.

Recovery options depend on the integration contract:

1. External idempotency key: repeat the request with the same logical key and receive the existing outcome.
2. Status reconciliation: query the external system to determine whether the operation already happened.
3. Compensation: perform a documented corrective operation when duplicate execution is unavoidable and reversible.
4. Manual review: stop automated progression when no safe automatic recovery exists.

Fencing protects FlowForge's own database state. It cannot undo an external side effect already accepted elsewhere.

Testing: simulate the external operation succeeding, terminate the worker before result submission, then verify the chosen idempotency, reconciliation, compensation, or manual-review path.

## 13. Concurrent recovery schedulers

Scenario: multiple recovery schedulers identify the same expired task.

Risk: two recovery processes both try to reschedule or reassign the task.

Detection: each scheduler reads the task as expired and eligible.

Recovery: recovery uses a conditional transaction against the current task state, lease generation, and expiry. Only one scheduler wins the transition. The other observes zero rows affected and performs no second transition.

Testing: run multiple recovery workers against the same expired task and assert one successful recovery transition and one new authoritative ownership generation.

## 14. Invariants checked across failures

- An unexpired lease is never stolen by recovery.
- A stale worker session cannot renew or complete a task.
- Every execution attempt has a durable attempt record.
- Retryable failures pass through retry-policy evaluation and backoff.
- Terminal tasks are not rerun solely because of restart recovery.
- A committed engine state change has its corresponding outbox record.
- Duplicate event publication does not create duplicate consumer-side logical effects.
- A task marked `DEAD_LETTERED` cannot return to READY.
- A required permanently failed task prevents successful workflow completion.

## 15. Three greatest technical risks

1. Uncertain external side effects. Fencing protects engine-owned state but cannot reverse an external operation. Integration contracts need idempotency, reconciliation, compensation, or manual review.
2. State and event divergence. A missing transaction boundary could commit workflow state without a durable publication record. The transactional outbox must be applied consistently.
3. Recovery races. Concurrent claim and recovery paths must use conditional database transitions. Unit tests alone are insufficient; PostgreSQL-backed concurrency tests are required.
