# Wex Purchase Service — Design Document

**Version:** 1.0  
**Date:** May 2026  
**Audience:** Engineering leadership, hiring managers, technical reviewers  
**Repository:** [sathyapriyas/WEXProject](https://github.com/sathyapriyas/WEXProject)

---

## 1. Executive Summary

The **Wex Purchase Service** is a production-style Java application that stores USD purchase transactions and retrieves them converted into foreign currencies using official exchange rates from the [U.S. Treasury Reporting Rates of Exchange API](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange).

The solution prioritizes **financial accuracy**, **clear business rules** (6-month lookback for rates), **developer portability** (no external database required to run locally), and **automated test coverage** suitable for a production deployment baseline.

| Dimension | Choice |
|-----------|--------|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.4 |
| Build | Maven |
| Persistence | Embedded H2 (in-memory) |
| External integration | Treasury Fiscal Data REST API |
| API style | REST (JSON) |

---

## 2. Business Context & Requirements

### 2.1 Problem Statement

Organizations need to record purchase transactions in USD and later report or analyze those purchases in other currencies using **authoritative government exchange rates**, not ad hoc or stale rates.

### 2.2 Functional Requirements

| ID | Requirement | Acceptance Criteria |
|----|-------------|---------------------|
| **R1** | Store a purchase transaction | Accept description, transaction date, USD amount; persist; return unique ID |
| **R2** | Retrieve with currency conversion | Return ID, description, date, original USD amount, exchange rate used, converted amount |
| **R3** | Field validation | Description ≤ 50 chars; valid date; positive USD amount rounded to nearest cent |
| **R4** | Exchange rate selection | Use rate with `record_date ≤ purchase date` within 6-month lookback |
| **R5** | Conversion failure | Return explicit error if no qualifying rate exists |
| **R6** | Production readiness | Automated functional tests; clean error handling; maintainable structure |

### 2.3 Non-Functional Requirements (Interpreted)

| NFR | How Addressed |
|-----|----------------|
| Financial rigor | `BigDecimal`, explicit rounding mode |
| Plug-and-play setup | H2 in-memory; single `mvn spring-boot:run` |
| No external DB for local dev | Embedded H2 (requirement constraint) |
| Maintainability | Layered architecture, separation of concerns |
| Testability | Unit + integration tests; Treasury API mocked in tests |

---

## 3. System Context

### 3.1 Context Diagram

```
                    ┌─────────────────────┐
                    │   API Consumer      │
                    │ (client / reviewer) │
                    └──────────┬──────────┘
                               │ HTTPS JSON
                               ▼
                    ┌─────────────────────┐
                    │  Wex Purchase       │
                    │  Service            │
                    │  (Spring Boot)      │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
     ┌────────────┐   ┌──────────────┐   ┌──────────────────┐
     │ H2 Database│   │ Treasury API │   │ (Future: Auth,   │
     │ (embedded) │   │ (Fiscal Data)│   │  metrics, cache) │
     └────────────┘   └──────────────┘   └──────────────────┘
```

### 3.2 External Dependency: Treasury API

- **Endpoint:** `v1/accounting/od/rates_of_exchange`
- **Documentation:** [Fiscal Data API](https://fiscaldata.treasury.gov/api-documentation/)
- **Key fields:** `country_currency_desc`, `exchange_rate`, `record_date`
- **Rate semantics:** Units of foreign currency per one USD (standard Treasury reporting format)

Example query pattern:

```
filter=country_currency_desc:eq:Canada-Dollar,
       record_date:lte:2024-06-15,
       record_date:gte:2023-12-15
sort=-record_date
```

---

## 4. Architecture Overview

### 4.1 Layered Architecture

The application follows a **three-tier, Spring-aligned** structure:

```
┌─────────────────────────────────────────────────────────┐
│  Presentation Layer                                      │
│  PurchaseController — REST, validation, HTTP status    │
└────────────────────────────┬────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────┐
│  Business Layer                                          │
│  PurchaseService — orchestration, transactions           │
│  CurrencyConversionService — 6-month rule, math          │
└────────────────────────────┬────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌───────────────┐  ┌─────────────────┐  ┌──────────────────┐
│ Repository    │  │ Treasury Client │  │ Exception Handler │
│ (JPA/H2)      │  │ (RestClient)    │  │ (RFC 7807-style)  │
└───────────────┘  └─────────────────┘  └──────────────────┘
```

### 4.2 Component Responsibilities

| Component | Responsibility |
|-----------|------------------|
| `PurchaseController` | HTTP mapping, input validation triggers, status codes |
| `PurchaseService` | Store/retrieve workflows, coordinates persistence + conversion |
| `CurrencyConversionService` | Business rules for rate selection and amount calculation |
| `TreasuryExchangeRateClient` | HTTP integration, query construction, response mapping |
| `PurchaseTransactionRepository` | CRUD via Spring Data JPA |
| `PurchaseTransaction` | Persistence entity |
| DTOs (`CreatePurchaseRequest`, `*Response`) | API contract separation from persistence |
| `ApiExceptionHandler` | Consistent error responses |
| `MoneyUtils` | Centralized monetary rounding |

### 4.3 Request Flows

#### Flow A — Store Purchase (Requirement #1)

```
POST /api/purchases
  → Bean Validation (description, date, amount)
  → PurchaseService.store()
      → Round amount to 2 decimals (HALF_UP)
      → Generate UUID
      → Save to H2 via JPA
  → 201 Created + StoredPurchaseResponse
```

#### Flow B — Retrieve with Conversion (Requirement #2)

```
GET /api/purchases/{id}?currency=Canada-Dollar
  → Load transaction by UUID (404 if missing)
  → CurrencyConversionService.convert()
      → Compute earliestAllowed = transactionDate - 6 months
      → Treasury API: filter by currency + date window, sort desc
      → Select first rate on or before purchase date in window
      → convertedAmount = amountUsd × exchangeRate (round to 2 dp)
  → 200 OK + ConvertedPurchaseResponse
      OR 422 if no rate in window
```

---

## 5. Data Design

### 5.1 Domain Model

**PurchaseTransaction** (persisted)

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | UUID | Primary key, server-generated |
| `description` | String(50) | Not null |
| `transactionDate` | LocalDate | Not null |
| `amountUsd` | BigDecimal(19,2) | Not null, stored rounded |

### 5.2 API Contracts

**Create (request body)**

```json
{
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 100.00
}
```

**Store (response)**

```json
{
  "identifier": "uuid",
  "description": "...",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 100.00
}
```

**Retrieve with conversion (response)**

```json
{
  "identifier": "uuid",
  "description": "...",
  "transactionDate": "2024-06-15",
  "originalAmountUsd": 100.00,
  "exchangeRate": 1.355,
  "targetCurrency": "Canada-Dollar",
  "convertedAmount": 135.50
}
```

### 5.3 Currency Identifier

Clients pass Treasury `country_currency_desc` values (e.g. `Canada-Dollar`, `Japan-Yen`) as the `currency` query parameter. This aligns directly with the Treasury dataset and avoids maintaining a separate currency code mapping table in v1.

---

## 6. Key Design Decisions

### 6.1 Decision Record Summary

| # | Decision | Rationale | Trade-off |
|---|----------|-----------|-----------|
| D1 | **Spring Boot** | Industry standard for Java REST services; fast bootstrap; strong ecosystem | Heavier than plain Java for a tiny scope |
| D2 | **Java 21 LTS** | Long-term support, modern language features; spec mentioned JDK 25 but 21 is widely deployable | May differ from spec’s JDK 25.0.3 wording |
| D3 | **BigDecimal for money** | Avoids floating-point errors; required for financial rigor | Slightly more verbose than `double` |
| D4 | **HALF_UP rounding to 2 decimals** | Matches “nearest cent” requirement for stored and converted amounts | Must document for auditors; consistent with US currency norms |
| D5 | **Embedded H2** | Satisfies “plug and play” / no external DB; zero install friction for reviewers | Data lost on restart; not suitable for multi-instance production as-is |
| D6 | **UUID primary keys** | Globally unique, no DB sequence coordination, safe for distributed IDs later | Larger index size vs. `BIGINT` |
| D7 | **RestClient for Treasury** | Modern Spring 6+ HTTP client; simple for GET-only integration | No built-in retry/circuit breaker in v1 |
| D8 | **Server-side 6-month filter + client-side validation** | Defense in depth if API returns edge-case rows | Redundant checks; acceptable for correctness |
| D9 | **HTTP 422 for conversion failures** | Distinguishes “resource exists but business rule blocks action” from 404 | Alternative: 404 or 400 — 422 chosen for semantic clarity |
| D10 | **DTOs separate from entity** | Stable API contract if persistence changes | Extra mapping boilerplate |
| D11 | **Mock Treasury in tests** | Deterministic CI; no network flakiness | Does not catch Treasury API schema changes |

### 6.2 Financial Precision Strategy

```
Stored amount:     purchaseAmountUsd.setScale(2, HALF_UP)
Conversion:        converted = (amountUsd × exchangeRate).setScale(2, HALF_UP)
Exchange rate:     Preserved at Treasury precision (e.g. 1.355) in response
```

**Why not round the exchange rate to 2 decimals before multiply?**  
Rounding only the final converted amount preserves maximum accuracy and matches the requirement that explicitly calls out converted amount precision. The exchange rate in the response reflects the authoritative Treasury value.

### 6.3 Exchange Rate Selection Algorithm

```
Input:  transactionDate, targetCurrency
Window: [transactionDate - 6 months, transactionDate]

1. Query Treasury API with:
   - country_currency_desc = targetCurrency
   - record_date <= transactionDate
   - record_date >= earliestAllowed
   - sort by record_date descending

2. Take first result (most recent qualifying rate)

3. If none → CurrencyConversionException → HTTP 422
```

This implements: *“rate less than or equal to purchase date from within the last 6 months.”*

### 6.4 Error Handling Strategy

| Scenario | HTTP Status | Mechanism |
|----------|-------------|-----------|
| Validation failure (length, null, non-positive amount) | 400 | `MethodArgumentNotValidException` → `ProblemDetail` |
| Purchase ID not found | 404 | `PurchaseNotFoundException` |
| No Treasury rate in window | 422 | `CurrencyConversionException` |
| Treasury API down / timeout | 500 (default) | Uncaught `RestClientException` — improvement area |

---

## 7. Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Runtime | OpenJDK | 21 |
| Framework | Spring Boot | 3.4.5 |
| Web | Spring Web (REST) | (via Boot) |
| Persistence | Spring Data JPA, Hibernate | (via Boot) |
| Database | H2 | Runtime embedded |
| Validation | Jakarta Bean Validation | (via Boot) |
| HTTP Client | Spring RestClient | (via Boot) |
| Testing | JUnit 5, Mockito, MockMvc | (via Boot Test) |
| Build | Maven | 3.9+ |

---

## 8. Security & Operations (Current State)

### 8.1 Security (v1 — Minimal by Design)

| Area | Current State | Production Expectation |
|------|---------------|------------------------|
| Authentication | None | OAuth2 / API keys / mTLS |
| Authorization | None | Role-based access per tenant |
| Input sanitization | Validation only | WAF, rate limiting |
| Secrets | N/A (no Treasury API key required) | Secret manager for any future keys |
| HTTPS | Local HTTP | TLS termination at gateway |

The Treasury Fiscal Data API is **public read-only**; no API key is required for v1.

### 8.2 Observability (v1)

| Capability | Status |
|------------|--------|
| Structured logging | Spring Boot defaults |
| Metrics (Micrometer) | Not configured |
| Distributed tracing | Not configured |
| Health endpoints | Spring Boot Actuator not added |

### 8.3 Deployment Model (Recommended Path)

```
Developer laptop / CI
    → mvn package → JAR
    → Container image (optional)
    → Runtime with JAVA_HOME=21
    → Replace H2 with PostgreSQL for production
    → Add load balancer + TLS
```

---

## 9. Testing Strategy

### 9.1 Test Pyramid

| Level | Tests | Purpose |
|-------|-------|---------|
| Unit | `MoneyUtilsTest`, `CurrencyConversionServiceTest`, `PurchaseServiceTest` | Rounding, 6-month rule, service logic |
| Integration | `PurchaseControllerIntegrationTest` | Full HTTP stack with mocked Treasury |

### 9.2 Coverage Highlights

- Nearest-cent rounding (`10.125` → `10.13`)
- Rate selection uses most recent rate on or before purchase date
- Rates older than 6 months rejected
- Empty Treasury response → 422
- Description > 50 chars → 400
- Unknown purchase ID → 404
- End-to-end store + retrieve with mocked rate `1.355` → `135.50` converted

### 9.3 Gaps (Improvement Opportunities)

- No contract test against live Treasury API (WireMock or recorded fixtures)
- No resilience tests (timeout, 503 from Treasury)
- No performance/load tests (explicitly out of scope per requirements)

---

## 10. Opportunities for Improvement

Prioritized for discussion with engineering leadership.

### 10.1 High Priority (Production Hardening)

| Improvement | Benefit | Effort |
|-------------|---------|--------|
| **PostgreSQL (or managed SQL) instead of H2** | Durability, multi-instance deployment | Low–Medium |
| **Flyway/Liquibase migrations** | Repeatable schema, audit trail | Low |
| **Resilience4j / retry on Treasury client** | Handles transient API failures | Medium |
| **Cache exchange rates** (Caffeine + TTL) | Reduces Treasury load; faster reads | Medium |
| **Spring Boot Actuator** (`/health`, `/metrics`) | Operational visibility | Low |
| **OpenAPI (Swagger) documentation** | Easier consumer onboarding | Low |

### 10.2 Medium Priority (API & UX)

| Improvement | Benefit | Effort |
|-------------|---------|--------|
| **Currency lookup endpoint** | List valid `country_currency_desc` values | Medium |
| **ISO 4217 alias mapping** | Accept `CAD` instead of `Canada-Dollar` | Medium |
| **Pagination / list purchases** | Audit and reporting use cases | Medium |
| **Idempotency keys on POST** | Safe retries for store operation | Medium |
| **Include `rateRecordDate` in API response** | Transparency for auditors (already computed internally) | Low |

### 10.3 Medium Priority (Quality & Compliance)

| Improvement | Benefit | Effort |
|-------------|---------|--------|
| **Audit log table** | Who stored/retrieved what and when | Medium |
| **Explicit validation for future transaction dates** | Prevent backdating fraud | Low |
| **Contract tests** (Treasury API schema) | Early detection of upstream changes | Medium |
| **ArchUnit / Checkstyle** | Enforce layering rules in CI | Low |

### 10.4 Lower Priority (Scale & Platform)

| Improvement | Benefit | Effort |
|-------------|---------|--------|
| **Async conversion / batch export** | High-volume reporting | High |
| **Event-driven architecture** (Kafka) | Downstream analytics | High |
| **Multi-tenancy** | SaaS-style isolation | High |
| **Kubernetes Helm chart** | Standardized deployment | Medium |

### 10.5 Known Limitations (v1)

1. **In-memory H2** — data does not survive process restart.  
2. **Synchronous Treasury call on every GET** — latency tied to external API.  
3. **No authentication** — acceptable for assessment; not for open production.  
4. **Currency parameter is free-text** — typos fail at Treasury lookup, not validation.  
5. **Single-page Treasury fetch (`page[size]=100`)** — sufficient for 6-month windows; edge cases with dense rate history may need pagination.  
6. **No CI/CD pipeline in repo** — GitHub Actions would demonstrate delivery maturity.

---

## 11. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Treasury API unavailable | Medium | High | Retry, cache, fallback error messaging |
| Treasury schema/filter syntax change | Low | High | Contract tests, monitoring |
| Incorrect rate interpretation | Low | Critical | Document rate semantics; unit tests with known examples |
| H2 data loss on restart | High (by design) | Medium | PostgreSQL for production |
| Mockito/JDK version mismatch in CI | Medium | Low | Pin Java 21 in CI matrix |

---

## 12. Roadmap Suggestion (Phased)

```
Phase 1 (Current)     Assessment-ready: core requirements, tests, README
Phase 2 (1–2 weeks)   PostgreSQL, Flyway, Actuator, GitHub Actions CI
Phase 3 (2–4 weeks)   Caching, resilience, OpenAPI, currency alias API
Phase 4 (Ongoing)     AuthN/Z, observability, performance baseline
```

---

## 13. Appendix

### A. API Summary

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/purchases` | Store purchase |
| GET | `/api/purchases/{id}?currency={desc}` | Retrieve with conversion |

### B. Project Structure

```
com.wex.purchase/
├── WexPurchaseApplication.java
├── config/AppConfig.java
├── controller/PurchaseController.java
├── service/PurchaseService.java
├── service/CurrencyConversionService.java
├── client/TreasuryExchangeRateClient.java
├── repository/PurchaseTransactionRepository.java
├── entity/PurchaseTransaction.java
├── dto/*.java
├── exception/*.java
└── util/MoneyUtils.java
```

### C. References

- [Treasury Reporting Rates of Exchange Dataset](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange)
- [Fiscal Data API Documentation](https://fiscaldata.treasury.gov/api-documentation/)
- [GitHub Repository](https://github.com/sathyapriyas/WEXProject)

---

*This document describes the implementation as of the initial delivery. It is intended to support technical review, hiring assessment, and planning for production evolution.*
