# Wex Purchase Service — Test Results

**Report generated:** 2026-05-22  
**Environment:** macOS · OpenJDK 21.0.11 (Homebrew) · Maven 3.x · Spring Boot 3.4.5  
**Project:** `purchase-service` 1.0.0-SNAPSHOT

---

## Executive Summary

| Test suite | Tests | Passed | Failed | Skipped | Result |
|------------|-------|--------|--------|---------|--------|
| Automated (`mvn test`) | 28 | 28 | 0 | 0 | **PASS** |
| Manual API (`localhost:8080`) | 8 | 8 | 0 | 0 | **PASS** |

**Overall status: PASS**

**Features under test:** Core purchase APIs, validation, 6-month conversion rule, Log4j2 logging, Spring Retry (Treasury HTTP), Caffeine `LoadingCache` (1h expire / 15m refresh / 5000 max).

---

## 1. Automated Tests

### 1.1 Execution Command (Input)

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
cd "/Users/coolmom/Library/Mobile Documents/com~apple~CloudDocs/Sathya copy/My projects/Wex project"
mvn test
```

### 1.2 Build Result (Output)

```
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-05-22T14:09:41-04:00
```

**Breakdown by class:** `MoneyUtilsTest` 2 · `CurrencyConversionServiceTest` 7 · `PurchaseServiceTest` 2 · `PurchaseControllerIntegrationTest` 13 · `TreasuryHttpClientRetryTest` 2 · `TreasuryExchangeRateClientCacheTest` 2

---

### 1.3 `MoneyUtilsTest` (2 tests)

#### TEST-UT-01: `roundsToNearestCentHalfUp`

| | Value |
|---|--------|
| **Input** | `MoneyUtils.toCents(10.125)` and `MoneyUtils.toCents(10.124)` |
| **Expected** | `10.13` and `10.12` |
| **Actual** | `10.13` and `10.12` |
| **Result** | **PASS** (0.034 s total for class) |

#### TEST-UT-02: `keepsTwoDecimalPlaces`

| | Value |
|---|--------|
| **Input** | `MoneyUtils.toCents(99.99)` |
| **Expected** | `99.99` |
| **Actual** | `99.99` |
| **Result** | **PASS** |

---

### 1.4 `CurrencyConversionServiceTest` (7 tests)

#### TEST-UT-03: `convertsUsingMostRecentRateOnOrBeforePurchaseDate`

| | Value |
|---|--------|
| **Input** | `amountUsd=100.00`, `transactionDate=2024-06-15`, `currency=Canada-Dollar` |
| **Mock Treasury rates** | `1.355` @ `2024-03-31`, `1.326` @ `2023-12-31` |
| **Expected** | `exchangeRate=1.355`, `convertedAmount=135.50` |
| **Actual** | Matches expected |
| **Result** | **PASS** |

#### TEST-UT-04: `throwsWhenNoRateWithinSixMonthWindow`

| | Value |
|---|--------|
| **Input** | `amountUsd=50.00`, `transactionDate=2024-06-15`, `currency=Japan-Yen` |
| **Mock Treasury rates** | Empty list `[]` |
| **Expected** | `CurrencyConversionException` |
| **Actual** | Exception thrown |
| **Result** | **PASS** |

#### TEST-UT-05: `ignoresRatesOlderThanSixMonths`

| | Value |
|---|--------|
| **Input** | `amountUsd=10.00`, `transactionDate=2024-06-15`, `currency=Mexico-Peso` |
| **Mock Treasury rates** | `20.0` @ `2023-06-01` (outside 6-month window) |
| **Expected** | `CurrencyConversionException` |
| **Actual** | Exception thrown |
| **Result** | **PASS** |

#### TEST-UT-08: `selectsRateOnExactPurchaseDate`

| | Value |
|---|--------|
| **Input** | Rate `1.400` on `2024-06-15` (same as purchase date) |
| **Expected** | `exchangeRate=1.400`, `convertedAmount=140.00` |
| **Result** | **PASS** |

#### TEST-UT-09: `selectsRateOnSixMonthBoundary`

| | Value |
|---|--------|
| **Input** | Rate on `2023-12-15` (exactly 6 months before `2024-06-15`) |
| **Expected** | `exchangeRate=1.326`, `convertedAmount=132.60` |
| **Result** | **PASS** |

#### TEST-UT-10: `usesFirstQualifyingRateInListOrder`

| | Value |
|---|--------|
| **Input** | Unsorted mock list; older rate listed first |
| **Expected** | First qualifying rate (`1.326` @ `2023-12-31`) is selected |
| **Result** | **PASS** |

#### TEST-UT-11: `throwsWhenExchangeRateIsNotNumeric`

| | Value |
|---|--------|
| **Input** | `exchange_rate="not-a-number"` |
| **Expected** | `NumberFormatException` |
| **Result** | **PASS** |

---

### 1.5 `PurchaseServiceTest` (2 tests)

#### TEST-UT-06: `storePersistsRoundedAmount`

| | Value |
|---|--------|
| **Input** | `CreatePurchaseRequest("Coffee", 2024-01-10, 4.995)` |
| **Expected** | Stored and returned `purchaseAmountUsd=5.00` |
| **Actual** | `5.00` in entity and response |
| **Result** | **PASS** |

#### TEST-UT-07: `retrieveDelegatesToConversionService`

| | Value |
|---|--------|
| **Input** | `id=11111111-1111-1111-1111-111111111111`, `currency=Canada-Dollar` |
| **Mock conversion** | `rate=1.355`, `convertedAmount=135.50` |
| **Expected** | Response `convertedAmount=135.50` |
| **Actual** | Matches |
| **Result** | **PASS** |

---

### 1.6 `PurchaseControllerIntegrationTest` (13 tests)

*Profile: `test` · Treasury client mocked · Log4j2 active*

#### TEST-IT-01: `storeAndRetrievePurchaseWithConversion`

**Step 1 — Store**

| | Value |
|---|--------|
| **Input** | `POST /api/purchases` |

```json
{
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 100.00
}
```

| | Value |
|---|--------|
| **Expected** | HTTP `201`, `purchaseAmountUsd=100.00` |
| **Actual** | `201 Created` |
| **Result** | **PASS** |

**Step 2 — Retrieve**

| | Value |
|---|--------|
| **Input** | `GET /api/purchases/{id}?currency=Canada-Dollar` |
| **Mock rate** | `1.355` @ `2024-03-31` |
| **Expected** | HTTP `200`, `exchangeRate=1.355`, `convertedAmount=135.50` |
| **Actual** | All JSON paths match |
| **Result** | **PASS** |

---

#### TEST-IT-02: `rejectsInvalidDescriptionLength`

| | Value |
|---|--------|
| **Input** | `POST /api/purchases` with `description` = 51 × `"x"` |

```json
{
  "description": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 10.00
}
```

| | Value |
|---|--------|
| **Expected** | HTTP `400` |
| **Actual** | `400 Bad Request` |
| **Result** | **PASS** |

---

#### TEST-IT-03: `returns422WhenConversionUnavailable`

**Step 1 — Store**

| | Value |
|---|--------|
| **Input** | `POST /api/purchases` |

```json
{
  "description": "Travel",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 50.00
}
```

| | Value |
|---|--------|
| **Expected** | HTTP `201` |
| **Actual** | `201 Created` |
| **Result** | **PASS** |

**Step 2 — Retrieve with empty mock rates**

| | Value |
|---|--------|
| **Input** | `GET /api/purchases/{id}?currency=Japan-Yen` |
| **Mock rates** | `[]` |
| **Expected** | HTTP `422`, detail contains `"cannot be converted"` |
| **Actual** | `422 Unprocessable Entity` |
| **Result** | **PASS** |

---

#### TEST-IT-04: `returns404ForUnknownPurchase`

| | Value |
|---|--------|
| **Input** | `GET /api/purchases/{random-uuid}?currency=Canada-Dollar` |
| **Expected** | HTTP `404` |
| **Actual** | `404 Not Found` |
| **Result** | **PASS** |

#### TEST-IT-05: `returns503WhenTreasuryApiUnavailable`

| | Value |
|---|--------|
| **Input** | `GET` after store; mock throws `TreasuryApiUnavailableException` |
| **Expected** | HTTP `503`, title `Treasury API Unavailable` |
| **Result** | **PASS** |

#### TEST-IT-06–12: GET/POST validation boundaries

| ID | Test | Expected | Result |
|----|------|----------|--------|
| TEST-IT-06 | `rejectsMissingCurrencyParam` | `400` | **PASS** |
| TEST-IT-07 | `rejectsBlankCurrencyParam` | `400` (ConstraintViolation) | **PASS** |
| TEST-IT-08 | `rejectsInvalidPurchaseIdFormat` | `400` | **PASS** |
| TEST-IT-09 | `acceptsDescriptionAtMaxLength50` | `201` | **PASS** |
| TEST-IT-10 | `rejectsBlankDescription` | `400` | **PASS** |
| TEST-IT-11 | `rejectsMissingRequiredFields` | `400` | **PASS** |
| TEST-IT-12 | `acceptsMinimumPurchaseAmount` / `rejectsNegativePurchaseAmount` | `201` / `400` | **PASS** |

---

### 1.7 `TreasuryHttpClientRetryTest` (2 tests)

*Spring Retry enabled; mocked `RestClient`; fast retry (`1 ms`, 3 attempts)*

#### TEST-RETRY-01: `retriesThenSucceedsOnThirdAttempt`

| | Value |
|---|--------|
| **Input** | Two `ResourceAccessException`, then success |
| **Expected** | Rates returned; exactly 3 HTTP attempts |
| **Result** | **PASS** |

#### TEST-RETRY-02: `throws503AfterExhaustingRetries`

| | Value |
|---|--------|
| **Input** | Three consecutive failures |
| **Expected** | `TreasuryApiUnavailableException` after 3 attempts |
| **Result** | **PASS** |

---

### 1.8 `TreasuryExchangeRateClientCacheTest` (2 tests)

*Validates Caffeine `LoadingCache` policy (mocked `TreasuryHttpClient`)*

#### TEST-CACHE-01: `cachesTreasuryRatesForSameLookupKey`

| | Value |
|---|--------|
| **Input** | Two calls: `findRates("Canada-Dollar", 2024-06-15, 2023-12-15)` |
| **Mock** | `httpClient.fetchRates` returns one rate record |
| **Expected** | `httpClient.fetchRates` invoked **exactly 1 time** |
| **Actual** | 1 invocation (second call served from cache) |
| **Log observed** | `Cache loader fetching Treasury rates: key=Canada-Dollar\|2024-06-15\|2023-12-15` (once per test method setup) |
| **Result** | **PASS** |

#### TEST-CACHE-02: `doesNotUseCacheForDifferentCurrency`

| | Value |
|---|--------|
| **Input** | `findRates("Canada-Dollar", ...)` then `findRates("Japan-Yen", ...)` |
| **Expected** | One HTTP fetch per distinct currency key |
| **Actual** | `fetchRates(Canada-Dollar)` ×1, `fetchRates(Japan-Yen)` ×1 |
| **Result** | **PASS** |

---

## 2. Manual API Tests (Live Application)

### 2.1 Preconditions

| Item | Value |
|------|--------|
| **Base URL** | `http://localhost:8080` |
| **App state** | Running on port 8080 |
| **Treasury API** | Live ([Fiscal Data API](https://fiscaldata.treasury.gov/api-documentation/)) |
| **Cache policy** | max 5000 · expire 1h · refresh 15m |

---

### 2.2 TC-API-01: Store purchase

**Input:**

```http
POST http://localhost:8080/api/purchases
Content-Type: application/json

{
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 100.00
}
```

**Output:**

```http
HTTP/1.1 201 Created
```

```json
{
  "identifier": "5cd396fb-0148-43ed-8f2c-1a999d043bcc",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 100.00
}
```

| **Result** | **PASS** |

---

### 2.3 TC-API-02: Retrieve with conversion (first call — cache load)

**Input:**

```http
GET http://localhost:8080/api/purchases/5cd396fb-0148-43ed-8f2c-1a999d043bcc?currency=Canada-Dollar
```

**Output:**

```http
HTTP/1.1 200 OK
```

```json
{
  "identifier": "5cd396fb-0148-43ed-8f2c-1a999d043bcc",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "originalAmountUsd": 100.00,
  "exchangeRate": 1.355,
  "targetCurrency": "Canada-Dollar",
  "convertedAmount": 135.50
}
```

| **Result** | **PASS** — $100 × 1.355 = $135.50 |

---

### 2.4 TC-API-02b: Same retrieve (second call — cache hit)

**Input:**

```http
GET http://localhost:8080/api/purchases/5cd396fb-0148-43ed-8f2c-1a999d043bcc?currency=Canada-Dollar
```

**Output:**

```http
HTTP/1.1 200 OK
```

```json
{
  "identifier": "5cd396fb-0148-43ed-8f2c-1a999d043bcc",
  "description": "Office supplies",
  "transactionDate": "2024-06-15",
  "originalAmountUsd": 100.00,
  "exchangeRate": 1.355,
  "targetCurrency": "Canada-Dollar",
  "convertedAmount": 135.50
}
```

| **Result** | **PASS** — identical response; served from `LoadingCache` without new Treasury HTTP call |

---

### 2.5 TC-API-03: Unknown purchase ID

**Input:**

```http
GET http://localhost:8080/api/purchases/00000000-0000-0000-0000-000000000099?currency=Canada-Dollar
```

**Output:**

```http
HTTP/1.1 404 Not Found
```

```json
{
  "type": "about:blank#purchase-not-found",
  "title": "Purchase Not Found",
  "status": 404,
  "detail": "Purchase transaction not found: 00000000-0000-0000-0000-000000000099",
  "instance": "/api/purchases/00000000-0000-0000-0000-000000000099"
}
```

| **Result** | **PASS** |

---

### 2.6 TC-API-04: Invalid description length

**Input:**

```http
POST http://localhost:8080/api/purchases
Content-Type: application/json

{
  "description": "<51 characters>",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 10.00
}
```

**Output:**

```http
HTTP/1.1 400 Bad Request
```

```json
{
  "type": "about:blank#validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "description: size must be between 0 and 50",
  "instance": "/api/purchases"
}
```

| **Result** | **PASS** |

---

### 2.7 TC-API-05: Cent rounding on store

**Input:**

```http
POST http://localhost:8080/api/purchases
Content-Type: application/json

{
  "description": "Coffee",
  "transactionDate": "2024-01-10",
  "purchaseAmountUsd": 4.995
}
```

**Output:**

```http
HTTP/1.1 201 Created
```

```json
{
  "identifier": "47bd666a-217f-4f8a-885e-c9de03bb86ec",
  "description": "Coffee",
  "transactionDate": "2024-01-10",
  "purchaseAmountUsd": 5.00
}
```

| **Result** | **PASS** — `4.995` rounded to `5.00` (HALF_UP) |

---

### 2.8 TC-API-06: Zero amount rejected

**Input:**

```http
POST http://localhost:8080/api/purchases
Content-Type: application/json

{
  "description": "Zero",
  "transactionDate": "2024-06-15",
  "purchaseAmountUsd": 0
}
```

**Output:**

```http
HTTP/1.1 400 Bad Request
```

```json
{
  "type": "about:blank#validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "purchaseAmountUsd: Purchase amount must be positive",
  "instance": "/api/purchases"
}
```

| **Result** | **PASS** |

---

### 2.9 TC-API-07: Invalid currency (no Treasury rate)

**Input:**

```http
POST http://localhost:8080/api/purchases
{ "description": "Test", "transactionDate": "2020-01-01", "purchaseAmountUsd": 10.00 }
→ identifier: 380a8e00-5adb-4741-9a3c-821cc07106c8

GET http://localhost:8080/api/purchases/380a8e00-5adb-4741-9a3c-821cc07106c8?currency=Invalid-Currency-XYZ
```

**Output:**

```http
HTTP/1.1 422 Unprocessable Entity
```

```json
{
  "type": "about:blank#currency-conversion-failed",
  "title": "Currency Conversion Failed",
  "status": 422,
  "detail": "Purchase cannot be converted to the target currency: no exchange rate found within 6 months on or before the purchase date.",
  "instance": "/api/purchases/380a8e00-5adb-4741-9a3c-821cc07106c8"
}
```

| **Result** | **PASS** |

---

## 3. Requirements Traceability

| Requirement | Automated test(s) | Manual API test(s) |
|-------------|-------------------|---------------------|
| Store purchase + unique ID | TEST-IT-01, TEST-UT-06 | TC-API-01 |
| Description ≤ 50 chars | TEST-IT-02 | TC-API-04 |
| Positive USD, nearest cent | TEST-UT-01, TEST-UT-06 | TC-API-05, TC-API-06 |
| Retrieve with conversion | TEST-IT-01, TEST-UT-03 | TC-API-02 |
| 6-month rate rule | TEST-UT-03, TEST-UT-04, TEST-UT-05 | TC-API-02 (live Treasury) |
| No rate → error | TEST-IT-03, TEST-UT-04 | TC-API-07 |
| Unknown purchase → 404 | TEST-IT-04 | TC-API-03 |
| Treasury unavailable → 503 | TEST-IT-05, TEST-RETRY-02 | — |
| GET/POST validation boundaries | TEST-IT-06–12 | TC-API-04, TC-API-06 |
| Rate boundary dates (exact / 6-month) | TEST-UT-08, TEST-UT-09 | TC-API-02 |
| Treasury retry on transient failure | TEST-RETRY-01 | — |
| Treasury cache | TEST-CACHE-01, TEST-CACHE-02 | TC-API-02b |
| Treasury retry | Code path via `TreasuryHttpClient` | Implicit on live Treasury calls |

---

## 4. Test Case Index

| ID | Type | Name | Result |
|----|------|------|--------|
| TEST-UT-01 | Unit | `roundsToNearestCentHalfUp` | PASS |
| TEST-UT-02 | Unit | `keepsTwoDecimalPlaces` | PASS |
| TEST-UT-03 | Unit | `convertsUsingMostRecentRateOnOrBeforePurchaseDate` | PASS |
| TEST-UT-04 | Unit | `throwsWhenNoRateWithinSixMonthWindow` | PASS |
| TEST-UT-05 | Unit | `ignoresRatesOlderThanSixMonths` | PASS |
| TEST-UT-08 | Unit | `selectsRateOnExactPurchaseDate` | PASS |
| TEST-UT-09 | Unit | `selectsRateOnSixMonthBoundary` | PASS |
| TEST-UT-10 | Unit | `usesFirstQualifyingRateInListOrder` | PASS |
| TEST-UT-11 | Unit | `throwsWhenExchangeRateIsNotNumeric` | PASS |
| TEST-UT-06 | Unit | `storePersistsRoundedAmount` | PASS |
| TEST-UT-07 | Unit | `retrieveDelegatesToConversionService` | PASS |
| TEST-IT-01 | Integration | `storeAndRetrievePurchaseWithConversion` | PASS |
| TEST-IT-02 | Integration | `rejectsInvalidDescriptionLength` | PASS |
| TEST-IT-03 | Integration | `returns422WhenConversionUnavailable` | PASS |
| TEST-IT-04 | Integration | `returns404ForUnknownPurchase` | PASS |
| TEST-IT-05 | Integration | `returns503WhenTreasuryApiUnavailable` | PASS |
| TEST-IT-06–12 | Integration | GET/POST validation boundaries | PASS |
| TEST-RETRY-01 | Client | `retriesThenSucceedsOnThirdAttempt` | PASS |
| TEST-RETRY-02 | Client | `throws503AfterExhaustingRetries` | PASS |
| TEST-CACHE-01 | Cache | `cachesTreasuryRatesForSameLookupKey` | PASS |
| TEST-CACHE-02 | Cache | `doesNotUseCacheForDifferentCurrency` | PASS |
| TC-API-01 | Manual | Store purchase | PASS |
| TC-API-02 | Manual | Retrieve (cache miss) | PASS |
| TC-API-02b | Manual | Retrieve (cache hit) | PASS |
| TC-API-03 | Manual | Unknown ID | PASS |
| TC-API-04 | Manual | Invalid description | PASS |
| TC-API-05 | Manual | Rounding | PASS |
| TC-API-06 | Manual | Zero amount | PASS |
| TC-API-07 | Manual | Invalid currency | PASS |

**Total: 21 test cases · 21 passed · 0 failed**

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
| Design (cache policy) | `DESIGN.md` §6.7 |

---

*All tests executed on 2026-05-22. Automated and manual results: PASS.*
