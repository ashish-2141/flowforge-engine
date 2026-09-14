# Technology Decision Note

## Kafka vs RabbitMQ

Recommendation: RabbitMQ for the initial implementation. The workload needs work distribution, acknowledgements, retries, and straightforward local operation. Kafka is stronger for high-throughput event streams and replay-oriented architectures, but it adds operational and conceptual weight for the first implementation. The outbox still remains the durable source of publishable events.

## JPA vs JDBC

Recommendation: JDBC for concurrency-sensitive commands, with JPA where object mapping improves ordinary reads. Task claiming, lease renewal, and fenced result updates benefit from explicit SQL and predictable transaction boundaries. JPA remains useful for less timing-sensitive domain access.

## Polling vs push-based assignment

Recommendation: polling. Workers request work using their capability set. Polling makes ownership, lease acquisition, backpressure, and failure recovery explicit without requiring a broker-side dispatcher to track worker state. A later version can add push delivery when measured latency requirements justify it.

## Database coordination vs external coordination

Recommendation: database coordination. The same transactional database owns workflow state, task state, leases, fencing tokens, idempotency records, and the outbox. This avoids introducing a second consistency system before there is a measured need for one. A dedicated coordination service would increase operational complexity.

## Modular monolith vs microservices

Recommendation: modular monolith for the first implementation. FlowForge and VoltOps stay separate Maven modules, while the engine remains a single deployable service. This simplifies transactions, local development, debugging, and design review. Components can split into services later when scale or ownership boundaries demand it.

## At-least-once vs other delivery approaches

Recommendation: at-least-once delivery plus idempotency. Exactly-once delivery is difficult to guarantee across databases, brokers, workers, and arbitrary external side effects. At-least-once is more practical when duplicates are expected and the logical operation is protected by an idempotency key. Fencing prevents stale task owners from committing results after reassignment.

## Decision summary

| Decision | Recommendation | Main reason |
|---|---|---|
| Broker | RabbitMQ | Simple work distribution and acknowledgements |
| Persistence API | JDBC for critical writes | Explicit concurrency control |
| Worker assignment | Polling | Clear ownership and recovery semantics |
| Coordination | PostgreSQL | One durable consistency boundary |
| Architecture | Modular monolith | Lower initial complexity |
| Delivery | At least once | Reliable with idempotency and fencing |
