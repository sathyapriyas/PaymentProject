# Wex Purchase Service — Design Document

**Version:** 1.2  
**Date:** May 2026 (updated for Log4j2, retry, and LoadingCache policy)  
**Audience:** Engineering leadership, hiring managers, technical reviewers  
**Repository:** [sathyapriyas/WEXProject](https://github.com/sathyapriyas/WEXProject)

---

## 1. Executive Summary

The **Wex Purchase Service** is a production-style Java application that stores USD purchase transactions and retrieves them converted into foreign currencies using official exchange rates from the [U.S. Treasury Reporting Rates of Exchange API](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange).

The solution prioritizes **financial accuracy**, **clear business rules** (6-month lookback for rates), **developer portability** (no external database required to run locally), **operational resilience** (retry and cache for the Treasury API), **structured logging** (Log4j2), and **automated test coverage** suitable for a production deployment baseline.

| Dimension | Choice |
|-----------|--------|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.4 |
| Build | Maven |
| Persistence | Embedded H2 (in-memory) |
| External integration | Treasury Fiscal Data REST API |
| API style | REST (JSON) |
| Logging | Log4j2 (`log4j2-spring.xml`) |
| Treasury resilience | Spring Retry + Caffeine `LoadingCache` |

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
| Resilience | Retry with exponential backoff on transient Treasury failures |
| Performance (reads) | Caffeine `LoadingCache`: 1h expiry, 15m background refresh, 5000 entry cap |
| Observability | Log4j2 at INFO/WARN/DEBUG across controller, service, client layers |

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
     │ (embedded) │   │ (Fiscal Data)│   │  Actuator metrics)│
     └────────────┘   │ + Retry/Cache│   └──────────────────┘
                      └──────────────┘
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

### 3.3 Treasury Integration Resilience (Implemented)

Treasury rate lookups are wrapped in two cross-cutting concerns:

| Concern | Technology | Purpose |
|---------|------------|---------|
| **Retry** | Spring Retry (`@Retryable`) | Recover from transient network or 5xx errors |
| **Cache** | Caffeine `LoadingCache` (`TreasuryRateCache`) | Auto-load, refresh, and evict Treasury rate responses |

Call chain:

```
TreasuryExchangeRateClient  (facade)
        ↓
TreasuryRateCache           (LoadingCache — hit / refresh / load)
        ↓ loader invokes on miss or background refresh
TreasuryHttpClient          (@Retryable)
        ↓
RestClient → Treasury API
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
┌───────────────┐  ┌─────────────────────────┐  ┌──────────────────┐
│ Repository    │  │ Treasury Integration    │  │ Exception Handler │
│ (JPA/H2)      │  │ LoadingCache + Retry    │  │ (RFC 7807-style)  │
└───────────────┘  │ HTTP client (RestClient)│  └──────────────────┘
                   └─────────────────────────┘
```

### 4.2 Component Responsibilities

| Component | Responsibility |
|-----------|------------------|
| `PurchaseController` | HTTP mapping, input validation triggers, status codes; request/response logging |
| `PurchaseService` | Store/retrieve workflows, coordinates persistence + conversion |
| `CurrencyConversionService` | Business rules for rate selection and amount calculation |
| `TreasuryExchangeRateClient` | Facade delegating to `TreasuryRateCache` |
| `TreasuryRateCache` | Caffeine `LoadingCache` with expiry, refresh, stats, and auto-loader |
| `TreasuryRateCacheKey` | Cache key record (`currency`, `purchaseDate`, `earliestAllowedDate`) |
| `TreasuryHttpClient` | HTTP calls with retry on transient failures (`@Retryable`) |
| `CacheRetryConfig` | Enables Spring Retry; retry listener for operational logging |
| `TreasuryProperties` | Externalized retry and cache settings |
| `PurchaseTransactionRepository` | CRUD via Spring Data JPA |
| `PurchaseTransaction` | Persistence entity |
| DTOs (`CreatePurchaseRequest`, `*Response`) | API contract separation from persistence |
| `ApiExceptionHandler` | Consistent error responses (400, 404, 422, 503) |
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
      → TreasuryExchangeRateClient.findRates() [cache lookup]
          → cache hit: return cached rates (no HTTP)
          → cache miss: TreasuryHttpClient.fetchRates() [with retry]
              → Treasury API: filter by currency + date window, sort desc
      → Select first rate on or before purchase date in window
      → convertedAmount = amountUsd × exchangeRate (round to 2 dp)
  → 200 OK + ConvertedPurchaseResponse
      OR 422 if no rate in window
      OR 503 if Treasury API unavailable after retries
```

#### Flow C — Treasury API with LoadingCache & Retry

```
findRates(currency, purchaseDate, earliestAllowed)
  → key = TreasuryRateCacheKey(currency, purchaseDate, earliestAllowed)
  → exchangeRateCache.get(key)

  [CACHE HIT — fresh]
      → return cached List<RateRecord> immediately (no HTTP)

  [CACHE HIT — eligible for refresh, age > 15 min]
      → return cached value immediately (low latency)
      → background loader calls TreasuryHttpClient.fetchRates() (with retry)

  [CACHE MISS or expired — age > 1 hour]
      → synchronous loader: TreasuryHttpClient.fetchRates()
            → Attempt 1: RestClient GET
            → on failure: backoff 500ms → Attempt 2 → backoff 1s → Attempt 3
            → all failed: TreasuryApiUnavailableException → HTTP 503
      → store result in cache, return rates
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
| D7 | **RestClient for Treasury** | Modern Spring 6+ HTTP client; simple for GET-only integration | Retry/cache layered separately for clarity |
| D8 | **Server-side 6-month filter + client-side validation** | Defense in depth if API returns edge-case rows | Redundant checks; acceptable for correctness |
| D9 | **HTTP 422 for conversion failures** | Distinguishes “resource exists but business rule blocks action” from 404 | Alternative: 404 or 400 — 422 chosen for semantic clarity |
| D10 | **DTOs separate from entity** | Stable API contract if persistence changes | Extra mapping boilerplate |
| D11 | **Mock Treasury in tests** | Deterministic CI; no network flakiness | Does not catch Treasury API schema changes |
| D12 | **Log4j2 (replace default Logback)** | Explicit logging framework; configurable appenders and levels | Requires excluding `spring-boot-starter-logging` from starters |
| D13 | **Spring Retry on `TreasuryHttpClient`** | Handles transient outages without custom retry loops | Requires Spring AOP; not applied to private/self-invoked methods |
| D14 | **Caffeine `LoadingCache` (not Spring `@Cacheable`)** | Supports `refreshAfterWrite`, `recordStats`, and explicit loader; matches financial-services caching patterns | More code than annotation-driven cache |
| D15 | **1h expire + 15m refresh** | Hard stop on stale data; background refresh avoids blocking reads | Slightly more Treasury traffic than long TTL |
| D16 | **Split cache, HTTP client, and facade** | Loader calls retried HTTP client; cache independent of Spring AOP | Three classes instead of one |

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
| Treasury API down after retries | 503 | `TreasuryApiUnavailableException` → `ApiExceptionHandler` |

### 6.5 Logging Strategy (Log4j2)

| Area | Level | What is logged |
|------|-------|----------------|
| `PurchaseController` | INFO | Incoming store/retrieve requests; created purchase IDs |
| `PurchaseService` | INFO / DEBUG | Persisted amounts; conversion orchestration |
| `CurrencyConversionService` | INFO / WARN | Selected exchange rate; missing rate in window |
| `TreasuryRateCache` | INFO / DEBUG | Loader invocations; hit rate and eviction stats at DEBUG |
| `TreasuryExchangeRateClient` | DEBUG | Delegating to `TreasuryRateCache` |
| `TreasuryHttpClient` | DEBUG / WARN | API call parameters; empty responses |
| `ApiExceptionHandler` | WARN / ERROR | Validation, 404, 422, 503 errors |
| `TreasuryRetry` (listener) | WARN | Failed attempt number before retry |

**Configuration files:**
- `log4j2-spring.xml` — console appender, pattern layout, package levels
- `application.yml` — `logging.config`, `com.wex.purchase` level (default `info`)

### 6.6 Retry Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `treasury.retry.max-attempts` | 3 | Total attempts including first call |
| `treasury.retry.initial-delay-ms` | 500 | Delay before second attempt |
| `treasury.retry.multiplier` | 2.0 | Exponential backoff multiplier |

**Retried exceptions:** `RestClientException`, `ResourceAccessException`, `HttpServerErrorException`

**Backoff sequence (default):** 500ms → 1000ms → (attempt 3, no further delay before fail)

### 6.7 Treasury API Caching Policy

Treasury rate lookups use a **Caffeine `LoadingCache`** configured for financial workloads: bounded memory, a hard freshness limit, proactive background refresh, and operational metrics.

#### 6.7.1 Implementation

```java
LoadingCache<TreasuryRateCacheKey, List<TreasuryRateRecord>> exchangeRateCache =
    Caffeine.newBuilder()
        // 1. Memory safety net
        .maximumSize(5000)
        // 2. Financial hard stop (evict rates older than 1 hour)
        .expireAfterWrite(Duration.ofHours(1))
        // 3. Low-latency performance (background updates every 15 minutes)
        .refreshAfterWrite(Duration.ofMinutes(15))
        // 4. Operational visibility
        .recordStats()
        // 5. Auto-loader — delegates to TreasuryHttpClient (with retry)
        .build(key -> treasuryHttpClient.fetchRates(
            key.targetCurrency(), key.purchaseDate(), key.earliestAllowedDate()));
```

**Class:** `TreasuryRateCache`  
**Access:** `TreasuryExchangeRateClient.findRates()` → `rateCache.get(...)`

#### 6.7.2 Policy Rationale

| Policy | Value | Why |
|--------|-------|-----|
| **maximumSize(5000)** | 5000 keys | Caps heap use for many currency/date-window combinations |
| **expireAfterWrite(1 hour)** | Hard eviction | Financial hard stop — rates older than one hour are never served |
| **refreshAfterWrite(15 min)** | Background reload | After 15 minutes, reads return cached data immediately while refresh runs asynchronously |
| **recordStats()** | Hit rate, loads, evictions | Supports debugging and future Actuator/metrics exposure |
| **LoadingCache loader** | Calls `TreasuryHttpClient` | Single code path for initial load and refresh; retry applied inside HTTP client |

#### 6.7.3 Cache Key

Keys are **`TreasuryRateCacheKey`** records (not currency pair alone), because Treasury rates depend on the purchase date and 6-month lookback window:

| Field | Example |
|-------|---------|
| `targetCurrency` | `Canada-Dollar` |
| `purchaseDate` | `2024-06-15` |
| `earliestAllowedDate` | `2023-12-15` |

**String form:** `Canada-Dollar|2024-06-15|2023-12-15`

#### 6.7.4 Configuration Properties

| Property | Default | Maps to |
|----------|---------|---------|
| `treasury.cache.maximum-size` | `5000` | `.maximumSize()` |
| `treasury.cache.expire-after-write-hours` | `1` | `.expireAfterWrite()` |
| `treasury.cache.refresh-after-write-minutes` | `15` | `.refreshAfterWrite()` |

Example `application.yml`:

```yaml
treasury:
  cache:
    maximum-size: 5000
    expire-after-write-hours: 1
    refresh-after-write-minutes: 15
```

#### 6.7.5 Read Behavior Summary

| Situation | Client experience | Treasury HTTP call |
|-----------|-------------------|-------------------|
| First request for key | Waits for loader | Yes (with retry) |
| Repeat within 15 min | Immediate cached response | No |
| Repeat after 15 min, before 1 hour | Immediate cached response; refresh in background | Yes (async refresh) |
| Repeat after 1 hour | Waits for loader (entry expired) | Yes (with retry) |

#### 6.7.6 Operational Notes

- **In-process only:** cache is local to the JVM; multi-instance deployments need a distributed cache (e.g. Redis) for shared state.
- **Stats access:** `TreasuryRateCache.stats()` returns Caffeine `CacheStats`; DEBUG logging emits hit rate, load count, and eviction count.
- **Tests:** `TreasuryExchangeRateClientCacheTest` verifies one HTTP call per distinct key; `invalidateAll()` clears cache between tests.

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
| Logging | Log4j2 | Via `spring-boot-starter-log4j2` |
| Caching | Caffeine `LoadingCache` | `caffeine` library (direct, no Spring Cache abstraction) |
| Resilience | Spring Retry + Spring AOP | `spring-retry`, `spring-boot-starter-aop` |
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
| Application logging | **Log4j2** — console output, configurable levels per package |
| Retry attempt logging | `RetryListenerSupport` bean logs failed attempts |
| Cache statistics | Caffeine `recordStats()` enabled (JMX/programmatic access possible) |
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
| Integration | `PurchaseControllerIntegrationTest` | Full HTTP stack with mocked Treasury facade |
| Cache | `TreasuryExchangeRateClientCacheTest` | Verifies `LoadingCache` hit/miss per key |

**Total automated tests:** 13 (all passing)

### 9.2 Coverage Highlights

- Nearest-cent rounding (`10.125` → `10.13`)
- Rate selection uses most recent rate on or before purchase date
- Rates older than 6 months rejected
- Empty Treasury response → 422
- Description > 50 chars → 400
- Unknown purchase ID → 404
- End-to-end store + retrieve with mocked rate `1.355` → `135.50` converted
- `LoadingCache`: second identical lookup does not call `TreasuryHttpClient` again
- `LoadingCache`: different currency/date keys invoke separate loader calls

### 9.3 Gaps (Improvement Opportunities)

- No contract test against live Treasury API (WireMock or recorded fixtures)
- No integration test simulating retry exhaustion → 503 (mocked HTTP failures)
- No performance/load tests (explicitly out of scope per requirements)

---

## 10. Opportunities for Improvement

Prioritized for discussion with engineering leadership.

### 10.1 High Priority (Production Hardening)

| Improvement | Benefit | Effort |
|-------------|---------|--------|
| **PostgreSQL (or managed SQL) instead of H2** | Durability, multi-instance deployment | Low–Medium |
| **Flyway/Liquibase migrations** | Repeatable schema, audit trail | Low |
| **Spring Boot Actuator** (`/health`, `/metrics`) | Operational visibility; expose cache stats | Low |
| **OpenAPI (Swagger) documentation** | Easier consumer onboarding | Low |
| **Circuit breaker** (Resilience4j) | Fail fast when Treasury is persistently down | Medium |

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

### 10.5 Known Limitations (Current)

1. **In-memory H2** — data does not survive process restart.  
2. **Synchronous Treasury call on cache miss** — repeat lookups are fast; first lookup still blocks on HTTP.  
3. **Cache is in-process** — not shared across multiple application instances (use Redis for clustered cache).  
4. **No authentication** — acceptable for assessment; not for open production.  
5. **Currency parameter is free-text** — typos fail at Treasury lookup, not validation.  
6. **Single-page Treasury fetch (`page[size]=100`)** — sufficient for 6-month windows; edge cases with dense rate history may need pagination.  
7. **No CI/CD pipeline in repo** — GitHub Actions would demonstrate delivery maturity.  
8. **No circuit breaker** — retries help transient errors; sustained outages still attempt 3 calls before 503.

---

## 11. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Treasury API unavailable | Medium | High | **Implemented:** retry (3×), cache, HTTP 503 with clear message |
| Stale cached exchange rates | Low | High | **Implemented:** 1h `expireAfterWrite` hard stop; 15m `refreshAfterWrite` background reload |
| Treasury schema/filter syntax change | Low | High | Contract tests, monitoring |
| Incorrect rate interpretation | Low | Critical | Document rate semantics; unit tests with known examples |
| H2 data loss on restart | High (by design) | Medium | PostgreSQL for production |
| Mockito/JDK version mismatch in CI | Medium | Low | Pin Java 21 in CI matrix |

---

## 12. Roadmap Suggestion (Phased)

```
Phase 1 (Done)        Core requirements, tests, README, design docs
Phase 2 (Done)        Log4j2, Treasury retry, LoadingCache (1h/15m/5000), 503 handling
Phase 3 (1–2 weeks)   PostgreSQL, Flyway, Actuator, GitHub Actions CI
Phase 4 (2–4 weeks)   Circuit breaker, OpenAPI, currency alias API, contract tests
Phase 5 (Ongoing)     AuthN/Z, distributed cache, performance baseline
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
├── config/
│   ├── AppConfig.java
│   ├── CacheRetryConfig.java
│   └── TreasuryProperties.java
├── controller/PurchaseController.java
├── service/PurchaseService.java
├── service/CurrencyConversionService.java
├── client/
│   ├── TreasuryExchangeRateClient.java   (facade)
│   ├── TreasuryRateCache.java            (LoadingCache)
│   ├── TreasuryRateCacheKey.java           (cache key)
│   └── TreasuryHttpClient.java           (@Retryable)
├── repository/PurchaseTransactionRepository.java
├── entity/PurchaseTransaction.java
├── dto/*.java
├── exception/*.java
└── util/MoneyUtils.java

src/main/resources/
├── application.yml
└── log4j2-spring.xml
```

### C. References

- [Treasury Reporting Rates of Exchange Dataset](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange)
- [Fiscal Data API Documentation](https://fiscaldata.treasury.gov/api-documentation/)
- [GitHub Repository](https://github.com/sathyapriyas/WEXProject)

---

*This document describes the implementation including Log4j2 logging, Treasury API retry, and the Caffeine `LoadingCache` policy (1h expiry, 15m refresh, 5000 entry cap). It is intended to support technical review, hiring assessment, and planning for production evolution.*
