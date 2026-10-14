# CargoFlow · Logistics Microservices Portfolio

[简体中文](README.md) | [English](README.en.md)

A runnable Java portfolio connecting shipment requests, vehicle capacity, fulfillment tracking and settlement. Includes a PC operations console with customer, dispatcher, finance and owner demo roles, across two isolated merchant tenants.

![Actual CargoFlow operations console](docs/overview.png)

**Commercial-scenario engineering demo:** payments are simulated, signatures are typed names and vehicles are demo records. Real payment providers, GPS, driver verification, electronic proof of delivery and production operations are outside the validated scope.

## Business workflow

Create shipment → reserve vehicle capacity → pick up → deliver → sign → create invoice → simulate payment → release capacity.

- Request-key idempotency and conflicting-payload rejection.
- Tenant isolation, customer ownership and role-based actions.
- Durable transactional outbox and dispatch callback records.
- Retry with backoff, cancellation compensation and release-before-reserve markers.
- Versioned tracking with duplicate detection and out-of-order delivery support.
- Unique invoice per shipment and one simulated payment receipt.
- Operational console for shipments, fleet, tracking, invoices, service health and failed events.

## Technology and validation scope

| Technology                        | Purpose                                                     | Status                                                         |
| --------------------------------- | ----------------------------------------------------------- | -------------------------------------------------------------- |
| Java 17, Spring Boot 4.0.8        | Five independently runnable applications                    | Local build and runtime verified                               |
| Spring Cloud 2025.1.3 Gateway MVC | API routing and signed principal forwarding                 | Real gateway workflow verified                                 |
| OpenFeign, Resilience4j           | Signed service calls, timeouts and circuit breakers         | HTTP calls verified; circuit breaker integrated                |
| JDBC, independent H2 stores       | Service-owned persistence and local transactions            | Business tests verified                                        |
| Outbox, durable callbacks         | Eventual consistency, retries and compensation              | Default HTTP mode verified                                     |
| RabbitMQ                          | Durable queues, confirms, signed envelopes and dead letters | Optional adapter; broker integration not verified              |
| Nacos, Alibaba 2025.1.0.0         | Discovery, instance selection and optional config import    | Optional integration; cluster not verified                     |
| Redis                             | Authorized versioned tracking cache with fallback           | Optional integration; Redis runtime not verified               |
| Docker Compose, GitHub Actions    | Deployment template and automated tests                     | Compose not runtime-verified; CI status depends on actual runs |

This implements eventual consistency, not global ACID, exactly-once delivery or Seata transactions. Broker confirmation does not prove successful business consumption.

## Services

| Application      | Port | Responsibility                                      |
| ---------------- | ---- | --------------------------------------------------- |
| gateway          | 8100 | Console, demo sessions, API routes                  |
| shipment-service | 8101 | Orders, state transitions, outbox                   |
| dispatch-service | 8102 | Capacity reservation and release, durable callbacks |
| tracking-service | 8103 | Versioned fulfillment timeline                      |
| billing-service  | 8104 | Invoices and simulated payment receipts             |

Each business service has its own database; no cross-service SQL joins.

## Run locally

Requirements: JDK 17+, Maven 3.9+, PowerShell. Initial startup may take approximately 30 seconds.

```powershell
$env:JAVA_HOME='path to JDK 17'
mvn verify
./ops/start.ps1 -JavaHome $env:JAVA_HOME
./ops/smoke.ps1
```

Visit **http://127.0.0.1:8100** and select a demo account in the top-right corner. Demo login requires no password and must stay on a local interface. The launcher generates a random shared key under ignored `.local/`; databases and logs are also ignored.

Stop with `./ops/stop.ps1` before rebuilding on Windows. Optional middleware, a Windows socket workaround and production gaps are documented in [Operations](docs/operations.md). See [Validation](docs/validation.md) and [Business/API](docs/business.md) for acceptance scope and business constraints.

Version references: [Spring Cloud](https://spring.io/projects/spring-cloud/), [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba), [Gateway MVC Java Routes](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/java-routes-api.html).

## Author

[wang2000222](https://github.com/wang2000222) · Java backend developer with 3 years of experience. Available for business systems, web applications, mini-program backends and API development.
