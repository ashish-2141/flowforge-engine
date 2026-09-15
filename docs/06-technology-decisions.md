# Technology Decision Note

## Decision principles

Each choice is evaluated against FlowForge requirements: concurrency control, durable recovery, duplicate handling, operational simplicity, local development, and testability.

## 1. Kafka vs RabbitMQ

| Criterion | Kafka | RabbitMQ | Decision |
|---|---|---|---|
| Work distribution | Possible, but requires additional consumer/partition design | Direct queue and acknowledgement model | RabbitMQ |
| Event streaming | Excellent | Good | Kafka |
| Replay model | Strong log retention and replay | More acknowledgement-oriented | Kafka |
| Initial operational complexity | Higher | Lower | RabbitMQ |
| Local setup | More concepts to configure | Straightforward | RabbitMQ |
| FlowForge event volume assumption | More capacity than initially required | Sufficient for initial design | RabbitMQ |

Decision: RabbitMQ for asynchronous workflow and domain events.

Important boundary: RabbitMQ does not assign task ownership. Worker acquisition is capability-based API polling backed by PostgreSQL. RabbitMQ carries events after durable state changes.

Kafka becomes a candidate when replay-heavy event streams, partition-oriented throughput, or long retention becomes a demonstrated requirement.

## 2. JPA vs JDBC

The JDBC choice means Spring's `JdbcTemplate` for concurrency-sensitive commands. JPA is not required for the first implementation and should not be mixed casually with direct SQL over the same entities.

| Criterion | JPA | JdbcTemplate | Decision |
|---|---|---|---|
| Standard CRUD | Strong | More explicit code | JPA |
| Exact SQL control | Indirect through ORM | Direct | JdbcTemplate |
| Atomic claim query | Possible | Explicit | JdbcTemplate |
| Locking behavior | ORM behavior must be understood | SQL is visible | JdbcTemplate |
| Mapping productivity | Strong | Manual | JPA |
| Reviewability of concurrency commands | Lower | Higher | JdbcTemplate |

Decision: use `JdbcTemplate` for task claim, lease renewal, recovery transitions, fenced result acceptance, and other concurrency-critical writes.

If JPA is introduced later, entity ownership and transaction boundaries must remain explicit. The main risk of mixing JPA and JDBC is stale persistence-context state, unexpected flush ordering, and different assumptions about locking or isolation.

## 3. Polling vs push-based assignment

| Criterion | Polling | Push | Decision |
|---|---|---|---|
| Ownership semantics | Explicit claim transaction | Dispatcher must track assignment | Polling |
| Failure recovery | Lease-based and database-driven | Dispatcher recovery required | Polling |
| Backpressure | Worker requests only when ready | Dispatcher controls delivery | Polling |
| Broker coupling | Low | Higher | Polling |
| Latency | Poll interval adds delay | Lower | Push |
| Database load | Repeated empty queries | Lower polling load | Push |
| Thundering herd risk | Present with many workers | Lower | Push |
| Fairness | Requires deterministic ordering | Dispatcher can schedule centrally | Push |
| Initial complexity | Lower | Higher | Polling |

Decision: capability-based API polling.

Known disadvantages are accepted for the initial design: empty queries, database load, polling latency, thundering herd behavior, and worker fairness concerns. Mitigations such as bounded poll intervals, indexed eligibility queries, jitter, batch limits, and deterministic ordering belong to implementation.

## 4. Database coordination vs external coordination

| Criterion | PostgreSQL coordination | External coordination service |
|---|---|---|
| Consistency systems | One | Multiple |
| Workflow and lease alignment | Same database | Cross-system coordination |
| Operational complexity | Lower | Higher |
| Failure surface | Smaller | Larger |
| High-scale coordination | Adequate for initial target | Potentially stronger | Depends on scale |

Decision: PostgreSQL owns workflow state, task state, worker sessions, leases, fencing generations, idempotency records, and outbox records.

An external coordination service is deferred until a measured scaling or topology requirement justifies it.

## 5. Modular monolith vs microservices

| Criterion | Modular monolith | Microservices |
|---|---|---|
| Deployment complexity | Lower | Higher |
| Transaction boundaries | Simple | Distributed |
| Debugging | Easier | Harder |
| Independent scaling | Limited | Strong |
| Initial delivery | Faster | Slower |
| Failure surface | Smaller | Larger |

Decision: modular monolith.

The deployable boundary is one `flowforge-application` Spring Boot process. `flowforge-engine` is a reusable library, and `voltops-reference` is a domain library/adapter. Workers remain independent processes.

This structure avoids the contradiction of having multiple executable Spring Boot modules while calling the system a single deployable application.

## 6. At-least-once vs other delivery approaches

| Approach | Strength | Main problem | Decision |
|---|---|---|---|
| At-most-once | Limits duplicates | Messages can be lost | Reject |
| At-least-once | Redelivery is supported | Duplicates are expected | Choose |
| Exactly-once | Stronger end-to-end promise in narrow systems | Not an end-to-end guarantee for arbitrary external effects | Not claimed |

Decision: at-least-once delivery with idempotent consumers and logical operations.

FlowForge does not claim end-to-end exactly-once execution. Instead, it guarantees the properties implemented inside the engine's consistency boundary and defines explicit strategies for external side effects.

## 7. RabbitMQ responsibility boundary

```text
Worker --> FlowForge API --> PostgreSQL
          claim ownership
          heartbeat
          result submission

FlowForge --> Outbox --> RabbitMQ --> Event Consumers
```

The worker claim path does not depend on RabbitMQ. A RabbitMQ redelivery therefore cannot itself cause a new FlowForge task attempt.

## 8. Transaction boundary rule

Only engine-owned records participate in the PostgreSQL transaction described by FlowForge.

A single transaction may contain workflow/task state, attempt history, lease state, fencing generation, idempotency records, and outbox records.

External calls do not become atomic merely because they occur near the database transaction. Their outcome requires an explicit integration protocol.

## 9. Revisit triggers

- RabbitMQ should be reconsidered if event-stream replay or throughput becomes dominant.
- Polling should be reconsidered if measured task-start latency or database load becomes unacceptable.
- JPA should be expanded into write paths only if it can preserve explicit concurrency semantics.
- Microservices should be introduced only for observed scaling, ownership, or fault-isolation requirements.
- External coordination should be considered only after PostgreSQL coordination becomes a demonstrated bottleneck.
