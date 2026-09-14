# Technology Decision Note

## Decision principles

Each technology choice is evaluated against the actual FlowForge requirements: concurrency control, durable recovery, duplicate handling, operational simplicity, local development, and the ability to explain and test the design during the Week 1 review.

## 1. Kafka vs RabbitMQ

| Criterion | Kafka | RabbitMQ | Decision |
|---|---|---|---|
| Work distribution | Strong, but queue-like patterns need more design | Direct queue and acknowledgement model | RabbitMQ |
| Event streaming | Excellent | Good | Kafka |
| Replay model | Native log retention and replay | Consumer acknowledgement based | Kafka |
| Initial operational complexity | Higher | Lower | RabbitMQ |
| Local development for this project | More components/concepts | Straightforward broker setup | RabbitMQ |
| Fit for FlowForge worker acquisition | Good | Strong | RabbitMQ |

Recommendation: RabbitMQ for the initial implementation. FlowForge needs straightforward work distribution, acknowledgements, retries, and a simple local environment. Kafka is stronger for high-throughput event streams and replay-oriented architectures, so it remains a future option if event-stream requirements grow.

The transactional outbox remains the durable source of publishable events regardless of broker choice.

## 2. JPA vs JDBC for concurrency-sensitive operations

| Criterion | JPA | JDBC | Decision |
|---|---|---|---|
| CRUD/read productivity | Strong | More explicit code | JPA |
| SQL control | Indirect | Direct | JDBC |
| Atomic task claim | Possible, but less explicit | Clear SQL and transaction control | JDBC |
| Locking/concurrency semantics | Must understand ORM behavior | Explicit | JDBC |
| Mapping domain objects | Strong | Manual | JPA |

Recommendation: JDBC for task claiming, lease renewal, fenced result updates, and other concurrency-sensitive commands. JPA remains suitable for ordinary reads and less timing-sensitive access if it improves maintainability.

## 3. Polling vs push-based assignment

| Criterion | Polling | Push | Decision |
|---|---|---|---|
| Ownership semantics | Explicit in claim request | Dispatcher must track assignment state | Polling |
| Failure recovery | Simple lease expiry and retry | Requires dispatcher recovery | Polling |
| Backpressure | Worker controls demand | Broker/dispatcher controls demand | Polling |
| Broker coupling | Low | Higher | Polling |
| Latency | Depends on poll interval | Lower | Push |
| Initial implementation complexity | Lower | Higher | Polling |

Recommendation: polling. Workers request compatible work using their capability set. This keeps ownership and lease recovery explicit. A push model can be evaluated later if measured latency requirements justify it.

## 4. Database coordination vs external coordination

| Criterion | PostgreSQL coordination | External coordination service |
|---|---|---|
| Number of consistency systems | One | Two or more |
| Lease and workflow state alignment | Same transaction boundary | Cross-system coordination required |
| Operational complexity | Lower | Higher |
| Failure modes | Concentrated in DB | Additional network and service failures |
| Scalability | Adequate for initial target | Better for some high-scale coordination workloads |

Recommendation: database coordination. Workflow state, leases, fencing tokens, idempotency records, and outbox records remain inside one durable consistency boundary. A dedicated coordination service is deferred until scale or topology requires it.

## 5. Modular monolith vs microservices

| Criterion | Modular monolith | Microservices |
|---|---|---|
| Deployment complexity | Lower | Higher |
| Transaction boundaries | Simple | Distributed |
| Debugging | Easier | More difficult |
| Independent scaling | Limited | Strong |
| Initial development speed | Faster | Slower |
| Failure surface | Smaller | Larger |

Recommendation: modular monolith for the first implementation. FlowForge and VoltOps remain separate Maven modules, while the engine remains one deployable application. Service boundaries can be introduced after observing real scaling or ownership requirements.

## 6. At-least-once vs other delivery approaches

| Approach | Strength | Main problem for FlowForge | Decision |
|---|---|---|---|
| At-most-once | Fewer duplicates | Messages can be lost | Reject |
| At-least-once | Durable redelivery | Duplicates are expected | Choose |
| Exactly-once | Appears simple conceptually | Hard to guarantee across DB, broker, workers, and external effects | Reject for initial design |

Recommendation: at-least-once delivery plus idempotency.

FlowForge should assume a message, command, or event can be delivered more than once. The logical operation is protected with an idempotency key. Fencing protects task ownership after lease expiry and reassignment.

This is a correctness model based on durable state and duplicate-safe effects rather than a claim of arbitrary exactly-once execution.

## 7. Decision summary

| Area | Decision | Primary reason |
|---|---|---|
| Broker | RabbitMQ | Simple work distribution and acknowledgements |
| Persistence API | JDBC for critical writes | Explicit concurrency control |
| Worker assignment | Polling | Clear ownership and recovery semantics |
| Coordination | PostgreSQL | One durable consistency boundary |
| Architecture | Modular monolith | Lower initial complexity |
| Delivery | At least once + idempotency | Reliable while making duplicates safe |

## 8. Revisit triggers

These decisions should be reconsidered only when evidence changes the requirements.

- Move from RabbitMQ if event-stream replay or throughput requirements dominate work distribution.
- Add push assignment if measured polling latency becomes unacceptable.
- Split services if independent scaling, team ownership, or fault isolation becomes necessary.
- Introduce external coordination only when database-based coordination becomes a demonstrated bottleneck.
