# 验收记录 / Validation

Date: 2026-10-14. Local Windows, JDK 17, five independent Spring Boot processes, four H2 file databases, default HTTP event mode. No live Nacos/RabbitMQ/Redis or real external logistics/payment providers were used.

## Automated business tests

16 tests across common and four business services: signature tampering; shipment request-key idempotency/mismatch; tenant isolation; forbidden state transitions and cancellation compensation; transactional event presence; duplicate reservation and release; release-before-reserve; insufficient capacity; role rejection; tracking duplicate/conflicting versions; out-of-order sort; ownership; invoice uniqueness/amount conflict; signed-only invoicing; repeated payment receipt; customer payment ownership.

Run: `mvn verify`. Test reports are generated under each module's `target/surefire-reports` and are intentionally not committed. Tests use isolated memory databases and disable scheduled business workers.

## Five-process gateway acceptance

Run: `./ops/smoke.ps1`.

- Four business services reachable through gateway status checks.
- Repeat create request returns same shipment.
- Merchant B does not see merchant A's shipment.
- Asynchronous capacity reservation and shipment callback complete.
- Dispatcher pickup and delivery, customer name confirmation complete.
- Exactly one invoice for the signed shipment.
- Repeated simulated payment remains PAID.
- Five ordered tracking versions become available.
- Dispatch assignment is released after signing.

PowerShell's non-enumerated REST arrays were corrected in the acceptance helper; the full script subsequently passed. Default HTTP delivery used actual OpenFeign and Spring Cloud Gateway, not an in-process substitute.

## Service outage and recovery

`./ops/recovery.ps1` passed against the five-process default HTTP deployment: the owned dispatch process was temporarily stopped; a new shipment remained durable, its dispatch outbox event entered FAILED; after dispatch restarted with the same database and shared secret, that original shipment automatically reached ASSIGNED. The script restored dispatch and checked its PID belonged to CargoFlow before stopping it.

## Remaining integration scope

Container execution, Nacos registration/config integration, MQ publisher-confirm/consumer/DLQ behavior, Redis failover, horizontally scaled workers, production SQL dialects, stress/HA and real provider interfaces remain unverified. CI results must be checked from the actual GitHub Actions run, not inferred from its YAML.
