# ADR-0001: Service and DDD boundaries

Status: Accepted

Octopus retains AcuCloud's Maven aggregation, centralized dependency management,
independently reusable commons, and contract-only service integration. It changes one
important rule: database entities, mappers, and service implementations are not API
models. `octopus-api` contains stable wire contracts only.

Each service is a bounded context and deployable unit. Within a service, domain code has
no Spring, MyBatis, Kafka, Redis, or InfluxDB dependency. Application services orchestrate
use cases through ports. Infrastructure supplies adapters. Interfaces expose HTTP or
consume messages. Cross-context writes are events or explicit APIs, never shared tables.

