# Problem Discovery

## Problem
FlowForge is a reusable workflow orchestration engine for long-running workflows made of dependent tasks. It must keep workflow state correct while workers, processes, networks, and message delivery fail.

Required failure conditions include competing workers, worker crashes, duplicate delivery, task timeout, task failure, orchestrator restart, delayed event publication, and workers returning after ownership has expired.

## Why cron is insufficient
Cron answers when a process starts. It does not provide dependency-aware DAG execution, durable task state, worker ownership, leases, fencing, retry policy, dead-letter handling, idempotency, or coordinated database and event publication.

## Why distributed workflows are difficult
Workflow state outlives a worker process. A worker can lose its lease while still running, then return after another worker has taken ownership. The system therefore needs leases plus monotonically increasing fencing tokens. Duplicate commands and events must be expected, so logical operations need idempotency keys.

## Main risks
Concurrent claims must have one winner. Crashed work must become safely reassigned. Stale results must be rejected. Database changes must not lose their corresponding events. Duplicate delivery must not duplicate logical business effects.

## Assumptions
- Durable transactional relational database.
- Stable worker identities during execution.
- Worker heartbeats for lease renewal.
- At-least-once broker delivery.
- Idempotent or compensatable business handlers.

## Limitations
Week 1 excludes production execution, lease implementation, retry implementation, outbox publisher implementation, metrics, frontend, Kubernetes, and cloud deployment. The design targets at-least-once execution with idempotent committed effects rather than arbitrary exactly-once execution.

## Success criteria
The completed platform must support workflow validation, dependent task execution, multiple capable workers, safe reassignment, duplicate-safe requests, restart recovery, stale-worker rejection, and lifecycle reconstruction.
