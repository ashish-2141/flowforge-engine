# Failure Analysis

## Concurrent task claiming
Detection: two workers attempt the same eligible task. Risk: duplicate execution. Recovery: perform an atomic claim transaction with one winning owner and fencing token. Test: start two workers against one task and assert one claim succeeds.

## Worker crash before execution
Detection: lease expires while the task remains claimed. Risk: unnecessary delay or lost work. Recovery: mark the task eligible again after lease expiry. Test: kill the worker before handler execution and verify reassignment.

## Worker crash during execution
Detection: heartbeat stops and lease expires. Risk: partial external side effect. Recovery: allow reassignment while requiring business idempotency. Test: kill a worker mid-task and verify a healthy worker completes without a duplicate committed effect.

## Worker returns after reassignment
Detection: result carries an old fencing token. Risk: stale worker corrupts the new owner's outcome. Recovery: reject the stale result transactionally and log the rejection. Test: expire a lease, reassign the task, then submit the old worker's result.

## Duplicate message delivery
Detection: same logical command or event idempotency key appears again. Risk: duplicate business effect. Recovery: persist the key and return the prior outcome for repeats. Test: deliver the same logical request multiple times.

## Database update succeeds but event publication fails
Detection: outbox row exists with no published timestamp. Risk: downstream systems miss a committed state change. Recovery: retry publication from the durable outbox. Test: commit state, force broker failure, restore broker, verify eventual publication.

## Broker unavailability
Detection: publisher cannot publish or acknowledge an event. Risk: growing event backlog. Recovery: retain events durably and retry with backoff. Duplicate publication is allowed, so consumers remain idempotent.

## Orchestrator restart
Detection: process termination or deployment. Risk: in-memory execution context is lost. Recovery: reconstruct unfinished workflows from PostgreSQL and resume only non-terminal tasks. Test: restart during an in-flight workflow.

## Task timeout
Detection: execution exceeds configured timeout. Risk: task hangs forever. Recovery: record timeout as a task failure and apply retry policy. Test: execute a deliberately slow handler and verify timeout handling.

## Retry exhaustion
Detection: failure count reaches the configured maximum. Risk: infinite retry or silent loss. Recovery: transition to DEAD_LETTERED with a durable reason and retain diagnostic information. Test: fail a task until attempts are exhausted.

## Three greatest technical risks

1. Stale-worker side effects are hard to control once execution leaves the database boundary. Fencing and idempotent handlers are required.
2. Incorrect transaction boundaries can produce state/event divergence. The outbox pattern must be enforced consistently.
3. Recovery races can create duplicate work. Lease expiry, atomic claiming, and fencing must be tested under concurrency rather than only by unit tests.
