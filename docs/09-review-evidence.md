# Week 1 Review Evidence

## Purpose

This file records the evidence available for the Week 1 revision review. It separates verified repository evidence from implementation work intentionally deferred until after design approval.

## Current repository evidence

### Repository structure

The root Maven build contains three modules:

```text
flowforge-engine
voltops-reference
flowforge-application
```

`flowforge-engine` is the reusable domain-independent library. `voltops-reference` is the domain library/adapter. `flowforge-application` is the sole executable Spring Boot module.

### Domain boundary

CI checks `flowforge-engine` for forbidden electrical-domain terms and fails the build if they are found.

### Module dependency direction

CI verifies:

```text
voltops-reference -> flowforge-engine
flowforge-application -> flowforge-engine
flowforge-application -> voltops-reference
flowforge-engine -/-> voltops-reference
```

### Executable boundary

CI verifies that only `flowforge-application` owns the Spring Boot Maven packaging plugin.

### Build verification

The latest GitHub Actions run on 2026-09-15 completed successfully.

Run: `CI #49`

Commit: `b63797eff89f1ceccf33dbabdafaf5c24ab8621f`

The workflow performed a clean repository checkout, module-boundary checks, domain-boundary checks, and:

```bash
./mvnw -B clean verify
```

The Maven reactor verified all four projects:

```text
FlowForge Platform       SUCCESS
FlowForge Engine         SUCCESS
VoltOps Reference        SUCCESS
FlowForge Application    SUCCESS
```

At Week 1 there are no production execution tests yet, so `verify` reports no tests to run. This is intentional and matches the Week 1 scope boundary.

### Docker verification

The same CI run executed:

```bash
docker compose up -d --wait
docker compose ps
docker compose down -v
```

PostgreSQL and RabbitMQ both reached `healthy` status before the health verification step completed.

### Maven wrapper

The repository contains an executable `mvnw` wrapper pinned to Maven 3.9.9. CI invokes the wrapper after setting executable permissions.

## Design evidence

The Week 1 documents now explicitly cover:

- problem framing before mechanism selection
- intended users and dependents
- non-electrical use cases
- business consequences
- correctness invariants
- definition/instance separation
- task definition/instance separation
- task-attempt history
- worker-session identity
- lease history and fencing generation
- immutable workflow and retry-policy versions
- cancellation semantics
- retry backoff
- timeout ambiguity
- concurrent recovery
- outbox crash windows
- uncertain external side effects
- PostgreSQL/API ownership versus RabbitMQ event delivery
- JDBC concurrency rationale
- polling trade-offs
- modular-monolith deployment model

## Deferred implementation evidence

The following remain intentionally unimplemented until after the design review:

- production task execution
- worker lease implementation
- retry execution implementation
- production outbox publisher
- external-side-effect adapters
- frontend/dashboard
- metrics infrastructure
- Kubernetes/cloud deployment

This prevents implementation decisions from silently changing the approved Week 1 model.

## Reviewer proof points

The design defence should be able to demonstrate verbally and later in code:

1. ten concurrent claim attempts result in exactly one successful owner
2. an unexpired lease is not stolen
3. an expired lease creates a `LEASE_LOST` attempt outcome before retry evaluation
4. a stale worker session cannot commit using an older fencing generation
5. duplicate event delivery does not create duplicate logical effects
6. the publish-before-mark outbox crash window permits duplicate publication but not duplicate logical processing
7. a worker restart creates a new session identity
8. a workflow instance keeps its original immutable definition and retry-policy versions
9. a dead-lettered required task fails the workflow and blocks dependent success
10. orchestrator restart resumes only durable, eligible non-terminal work

## Remaining evidence after implementation starts

The next phase must add PostgreSQL-backed integration tests for claim races, lease expiry, stale-result rejection, recovery races, idempotency, outbox recovery, retry exhaustion, and orchestrator restart.
