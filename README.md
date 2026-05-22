# Wex Purchase Service

Java/Spring Boot application that stores USD purchase transactions and retrieves them converted to currencies supported by the [U.S. Treasury Reporting Rates of Exchange API](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange).

## Requirements implemented

| Requirement | Implementation |
|-------------|----------------|
| Store purchase (description, date, USD amount) | `POST /api/purchases` |
| Unique identifier | UUID assigned on create |
| Description ≤ 50 chars | Bean Validation `@Size(max = 50)` |
| Positive USD amount, nearest cent | `BigDecimal` + `HALF_UP` to 2 decimals |
| Retrieve with currency conversion | `GET /api/purchases/{id}?currency={country_currency_desc}` |
| Exchange rate ≤ purchase date, within 6 months | Treasury API filter + service validation |
| Conversion error when no rate | HTTP 422 with explanatory message |
| Plug-and-play (no external DB) | Embedded H2 in-memory database |

## Tech stack

- Java 21 (LTS; compatible with JDK 25 per spec)
- Maven
- Spring Boot 3.4
- Spring Data JPA + H2
- RestClient for Treasury API
- Log4j2 for application logging
- Spring Retry + Caffeine cache for Treasury API resilience

## Prerequisites

- JDK 21+ ([Adoptium](https://adoptium.net/) or `brew install openjdk@21`)
- Maven 3.9+ (`brew install maven`)

## Quick start

```bash
cd "Wex project"
mvn spring-boot:run
```

Application listens on **http://localhost:8080**.

Logging uses **Log4j2** (`log4j2-spring.xml`). Set `com.wex.purchase` to `debug` in `application.yml` for verbose Treasury and conversion traces.

### Treasury API resilience

| Feature | Behavior |
|---------|----------|
| **Retry** | Up to 3 attempts with exponential backoff (500ms → 1s → 2s) on network/5xx errors |
| **Cache** | Caffeine `LoadingCache`: max 5000 keys, 1h expiry, 15m background refresh, `recordStats` |
| **Failure** | After retries exhausted → HTTP **503** Treasury API Unavailable |

Configure in `application.yml` under `treasury.retry` and `treasury.cache`.

## API examples

### Store a purchase

```bash
curl -s -X POST http://localhost:8080/api/purchases \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Office supplies",
    "transactionDate": "2024-06-15",
    "purchaseAmountUsd": 100.00
  }'
```

Response (201):

```json
{
  "identifier": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 100.00
}
```

### Retrieve with conversion

Use a `country_currency_desc` value from the Treasury dataset (e.g. `Canada-Dollar`, `Japan-Yen`, `Euro`).

```bash
curl -s "http://localhost:8080/api/purchases/{identifier}?currency=Canada-Dollar"
```

Response (200):

```json
{
  "identifier": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "originalAmountUsd": 100.00,
  "exchangeRate": 1.36,
  "targetCurrency": "Canada-Dollar",
  "convertedAmount": 135.50
}
```

## Run tests

```bash
mvn test
```

Tests cover monetary rounding, 6-month conversion rules, REST validation, and end-to-end store/retrieve flows (Treasury API mocked).

## Project layout

```
src/main/java/com/wex/purchase/
  controller/     REST endpoints
  service/        Business logic & conversion
  client/         Treasury API integration
  entity/         JPA persistence model
  repository/     Data access
  dto/            Request/response & API models
  exception/      Error handling
  util/           BigDecimal helpers
```

## Currency codes

The `currency` query parameter must match Treasury `country_currency_desc` values exactly (format: `Country-Currency`, e.g. `Canada-Dollar`). Browse available values via the [Treasury dataset](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange).
