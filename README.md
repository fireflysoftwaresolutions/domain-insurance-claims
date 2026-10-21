<a href="https://fireflysoftware.io/openplatform/">
<picture>
  <source media="(max-width: 640px) and (prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/fireflysoftwaresolutions/.github/main/profile/assets/banner-narrow-dark.svg">
  <source media="(max-width: 640px)" srcset="https://raw.githubusercontent.com/fireflysoftwaresolutions/.github/main/profile/assets/banner-narrow-light.svg">
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/fireflysoftwaresolutions/.github/main/profile/assets/banner-dark.svg">
  <img src="https://raw.githubusercontent.com/fireflysoftwaresolutions/.github/main/profile/assets/banner-light.svg" alt="Firefly OpenPlatform - Domain Insurance - Claims" width="100%">
</picture>
</a>

# Firefly OpenPlatform - Domain Insurance - Claims

Developed by the Firefly Software Foundation. [dev@fireflysoftware.io](mailto:dev@fireflysoftware.io)

[Website](https://fireflysoftware.io/) · [OpenPlatform](https://fireflysoftware.io/openplatform/) · [Module catalogue](https://fireflysoftware.io/openplatform/modules/)

Domain layer microservice for insurance claim records within the Firefly platform. This service orchestrates create, status change, and query against `core-insurance-claims` with compensating saga steps.

Repository: [https://github.com/fireflysoftwaresolutions/domain-insurance-claims](https://github.com/fireflysoftwaresolutions/domain-insurance-claims)

---

## Overview

`domain-insurance-claims` sits between experience services and `core-insurance-claims`. Write operations run through the FireflyFramework saga engine. Queries use the CQRS query bus. The domain depends on the core SDK only. It does not import another core's internal packages. Identifiers that point at parties, contracts, products, or policies stay UUIDs owned by those cores.

It records claim status through the claims core. It does not settle a claim, post a recovery, set a reserve, call a sanctions provider, or certify IFRS 17 or Solvency II.

### Key features

- **Create** -- register a claim through `core-insurance-claims`
- **Status change** -- read the current record and update its status through the core The create and status-change sagas also append a status history row on the same core.
- **Query** -- read one claim by identifier
- **Compensation** -- a failed later step asks the core to undo the work already accepted
- **CQRS** -- commands and queries travel on separate buses
- **Reactive I/O** -- Spring WebFlux with virtual threads enabled

## Architecture

### Module structure

| Module | Purpose |
|--------|---------|
| `domain-insurance-claims-core` | Saga workflows, commands, handlers, and the application service |
| `domain-insurance-claims-interfaces` | Request contract for a status change |
| `domain-insurance-claims-infra` | SDK client factory and downstream base-path configuration |
| `domain-insurance-claims-web` | Spring Boot application, REST API, actuator, and OpenAPI |
| `domain-insurance-claims-sdk` | Java client generated from the OpenAPI document |

### Dependency flow

```
web --> interfaces --> core --> infra --> core-insurance-claims-sdk
sdk (generated from openapi.yml)
```

### Saga map

| Saga | Core calls | Compensation |
|------|------------|--------------|
| `RegisterClaimSaga` | `createClaim` on `ClaimsApi`, then `createClaimStatusHistory` on `ClaimStatusHistoryApi` | Deletes the status history row, then deletes the claim |
| `UpdateClaimStatusSaga` | Reads the current record, updates its status through `updateClaim`, then appends status history | Deletes the new history row and writes the previous status back through the core |
| Query | `getClaimById` on `ClaimsApi` | None. A read has nothing to undo. |

Status values accepted by the core: `NOTIFIED`, `OPEN`, `UNDER_ASSESSMENT`, `RESERVED`, `SETTLED`, `CLOSED`, `REOPENED`, `DENIED`.

## Setup

### Prerequisites

- Java 25
- Apache Maven 3.9+
- `core-insurance-claims-sdk` 26.10.3
- Apache Kafka on `localhost:9092` when event publishing is left at the default

### Environment

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_ADDRESS` | `localhost` | Bind address |
| `SERVER_PORT` | `8072` | HTTP port |
| `ENDPOINT_CORE_INSURANCE_CLAIMS` | `http://localhost:8080` | Base URL of `core-insurance-claims` |

### Build and test

```bash
mvn clean verify
```

### Run

```bash
ENDPOINT_CORE_INSURANCE_CLAIMS=http://localhost:8080 mvn spring-boot:run -pl domain-insurance-claims-web
```

Swagger UI (non-production): [http://localhost:8072/swagger-ui.html](http://localhost:8072/swagger-ui.html)

## API

| Method | Path | Behavior |
|--------|------|----------|
| `POST` | `/api/v1/claims` | Runs `RegisterClaimSaga` |
| `GET` | `/api/v1/claims/{claimId}` | Queries the core |
| `PUT` | `/api/v1/claims/{claimId}/status` | Runs `UpdateClaimStatusSaga` |

The status request body is `status` plus an optional `statusReason`.
