# Wex Purchase Service — Test Results

**Report generated:** 2026-05-22  
**Environment:** macOS · OpenJDK 21.0.11 (Homebrew) · Maven · Spring Boot 3.4.5  
**Project:** `purchase-service` 1.0.0-SNAPSHOT

---

## Executive Summary

| Test suite | Tests | Passed | Failed | Skipped | Result |
|------------|-------|--------|--------|---------|--------|
| Automated (`mvn test`) | 11 | 11 | 0 | 0 | **PASS** |
| Manual API (live app on `:8080`) | 7 | 7 | 0 | 0 | **PASS** |

**Overall status: PASS**

---

## 1. Automated Tests (Maven / JUnit 5)

### 1.1 Command

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
mvn test
```

### 1.2 Build Result

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 4.337 s
Finished at: 2026-05-22T10:54:28-04:00
```

### 1.3 Test Cases by Class

#### `MoneyUtilsTest` (2 tests)

| Test | Status | Time |
|------|--------|------|
| `roundsToNearestCentHalfUp` | PASS | 0.001 s |
| `keepsTwoDecimalPlaces` | PASS | 0.012 s |

**Validates:** USD amounts rounded to nearest cent using `HALF_UP`.

---

#### `CurrencyConversionServiceTest` (3 tests)

| Test | Status | Time |
|------|--------|------|
| `convertsUsingMostRecentRateOnOrBeforePurchaseDate` | PASS | 0.001 s |
| `throwsWhenNoRateWithinSixMonthWindow` | PASS | 0.019 s |
| `ignoresRatesOlderThanSixMonths` | PASS | 0.001 s |

**Validates:** 6-month lookback rule; selects rate `1.355` on `2024-03-31` for purchase `2024-06-15`; converted amount `135.50` for $100 USD.

---

#### `PurchaseServiceTest` (2 tests)

| Test | Status | Time |
|------|--------|------|
| `storePersistsRoundedAmount` | PASS | 0.005 s |
| `retrieveDelegatesToConversionService` | PASS | 0.092 s |

**Validates:** Input `4.995` stored as `5.00`; retrieve orchestration with conversion service.

---

#### `PurchaseControllerIntegrationTest` (4 tests)

| Test | Status | Time | Notes |
|------|--------|------|-------|
| `storeAndRetrievePurchaseWithConversion` | PASS | 0.011 s | End-to-end HTTP; Treasury **mocked** |
| `rejectsInvalidDescriptionLength` | PASS | 0.044 s | Description > 50 chars → **400** |
| `returns422WhenConversionUnavailable` | PASS | 0.054 s | Mocked empty Treasury response → **422** |
| `returns404ForUnknownPurchase` | PASS | 0.114 s | Unknown UUID → **404** |

**Profile:** `test` (H2 test DB; Treasury client mocked)

---

## 2. Manual API Tests (Running Application)

### 2.1 Preconditions

- Application reachable at `http://localhost:8080` (instance already running on test host)
- Live calls to [U.S. Treasury Fiscal Data API](https://fiscaldata.treasury.gov/api-documentation/) for currency conversion

### 2.2 Test Cases

#### TC-API-01: Store purchase (Requirement #1)

**Request:**

```http
POST /api/purchases
Content-Type: application/json

{
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 100.00
}
```

**Response:** `201 Created`

```json
{
  "identifier": "b7c5d6b4-ae63-43c7-b90f-6ed097b1a7bd",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 100.00
}
```

**Result:** PASS — UUID assigned; fields persisted as expected.

---

#### TC-API-02: Retrieve with Canada-Dollar conversion (Requirement #2)

**Request:**

```http
GET /api/purchases/b7c5d6b4-ae63-43c7-b90f-6ed097b1a7bd?currency=Canada-Dollar
```

**Response:** `200 OK`

```json
{
  "identifier": "b7c5d6b4-ae63-43c7-b90f-6ed097b1a7bd",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "originalAmountUsd": 100.00,
  "exchangeRate": 1.355,
  "targetCurrency": "Canada-Dollar",
  "convertedAmount": 135.50
}
```

**Result:** PASS — Treasury rate applied; $100 × 1.355 = $135.50 (rounded to cent).

---

#### TC-API-03: Unknown purchase ID

**Request:**

```http
GET /api/purchases/00000000-0000-0000-0000-000000000099?currency=Canada-Dollar
```

**Response:** `404 Not Found`

```json
{
  "type": "about:blank#purchase-not-found",
  "title": "Purchase Not Found",
  "status": 404,
  "detail": "Purchase transaction not found: 00000000-0000-0000-0000-000000000099"
}
```

**Result:** PASS

---

#### TC-API-04: Invalid description length (> 50 characters)

**Request:** `POST /api/purchases` with 51-character description

**Response:** `400 Bad Request`

```json
{
  "type": "about:blank#validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "description: size must be between 0 and 50"
}
```

**Result:** PASS

---

#### TC-API-05: Cent rounding on store

**Request:**

```http
POST /api/purchases
{ "description": "Coffee", "transactionDate": "2024-01-10", "purchaseAmountUsd": 4.995 }
```

**Response:** `201 Created` — `"purchaseAmountUsd": 5.00`

**Result:** PASS — nearest cent rounding (`HALF_UP`).

---

#### TC-API-06: Zero / non-positive amount rejected

**Request:** `POST /api/purchases` with `purchaseAmountUsd: 0`

**Response:** `400 Bad Request`

```json
{
  "detail": "purchaseAmountUsd: Purchase amount must be positive"
}
```

**Result:** PASS

---

#### TC-API-07: Conversion failure — invalid currency

**Request:**

```http
GET /api/purchases/{id}?currency=Invalid-Currency-XYZ
```

(Purchase dated `2020-01-01`, no Treasury rate for bogus currency)

**Response:** `422 Unprocessable Entity`

```json
{
  "type": "about:blank#currency-conversion-failed",
  "title": "Currency Conversion Failed",
  "status": 422,
  "detail": "Purchase cannot be converted to the target currency: no exchange rate found within 6 months on or before the purchase date."
}
```

**Result:** PASS

---

#### TC-API-08: Japan-Yen conversion (live Treasury)

**Request:** Store travel purchase; `GET ...?currency=Japan-Yen`

**Response:** `200 OK`

```json
{
  "identifier": "670cce46-a5da-4393-97ca-313a9ef96349",
  "description": "Travel",
  "transactionDate": "2024-06-15",
  "originalAmountUsd": 50.00,
  "exchangeRate": 151.34,
  "targetCurrency": "Japan-Yen",
  "convertedAmount": 7567.00
}
```

**Result:** PASS — Live Treasury returned a valid rate (differs from unit test mock scenario where Japan-Yen returns 422).

---

## 3. Requirements Traceability

| Requirement | Automated test coverage | Manual API verification |
|-------------|-------------------------|-------------------------|
| Store purchase + unique ID | `PurchaseServiceTest`, integration store | TC-API-01 |
| Description ≤ 50 chars | `rejectsInvalidDescriptionLength` | TC-API-04 |
| Positive USD, nearest cent | `MoneyUtilsTest`, `storePersistsRoundedAmount` | TC-API-05, TC-API-06 |
| Retrieve with conversion | `storeAndRetrievePurchaseWithConversion` | TC-API-02, TC-API-08 |
| 6-month rate rule | `CurrencyConversionServiceTest` (3 tests) | TC-API-02 (live Treasury) |
| Error when no rate | `returns422WhenConversionUnavailable` | TC-API-07 |
| Unknown purchase | `returns404ForUnknownPurchase` | TC-API-03 |

---

## 4. Notes & Observations

1. **Java version:** Use JDK **21** for `mvn test`. JDK 26 on this machine caused Mockito failures in an earlier run.
2. **Treasury API:** Manual tests depend on network access to `api.fiscaldata.treasury.gov`.
3. **H2 in-memory:** Data from manual API tests exists only while the application process is running.
4. **Port 8080:** A second `spring-boot:run` failed with “port already in use” — manual tests used the existing running instance.
5. **zsh URL tip:** Do not wrap UUIDs in `{curly braces}`; quote URLs containing `?`.

---

## 5. How to Reproduce

```bash
# Automated tests
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
cd "/path/to/Wex project"
mvn test

# Manual API (start app first)
mvn spring-boot:run

curl -X POST http://localhost:8080/api/purchases \
  -H "Content-Type: application/json" \
  -d '{"description":"Office supplies","transactionDate":"2024-06-15","purchaseAmountUsd":100.00}'

curl "http://localhost:8080/api/purchases/YOUR-UUID-HERE?currency=Canada-Dollar"
```

---

## 6. Artifacts

| Artifact | Location |
|----------|----------|
| Surefire XML reports | `target/surefire-reports/` |
| This report | `TEST_RESULTS.md` |

---

*All automated and manual tests executed on 2026-05-22 passed successfully.*
