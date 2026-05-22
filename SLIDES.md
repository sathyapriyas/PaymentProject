# Wex Purchase Service — Slide Deck Outline

**Audience:** Engineering manager / hiring panel  
**Duration:** 18–22 minutes (+ 5 min Q&A)  
**Presenter notes:** Pair with live demo (`mvn spring-boot:run` + curl) on slides 11–12 if time allows.

---

## Slide 1 — Title

**Title:** Wex Purchase Service — Technical Design Review  
**Subtitle:** USD purchase storage with U.S. Treasury currency conversion  
**Footer:** Your name · Date · [GitHub: sathyapriyas/WEXProject](https://github.com/sathyapriyas/WEXProject)

**Speaker notes:** Production-style assessment project — core features plus logging, retry, and caching.

---

## Slide 2 — Agenda

1. Problem & business requirements  
2. Solution overview  
3. Architecture & data flow  
4. Treasury integration: retry, cache, logging  
5. Key design decisions  
6. Exchange rate logic (core business rule)  
7. API & data model  
8. Quality: testing & error handling  
9. Current limitations  
10. Roadmap  
11. Demo (optional)  
12. Q&A  

---

## Slide 3 — Problem Statement

**Headline:** Why this service exists  

**Bullets:**
- Organizations record purchases in **USD** but must report or analyze spend in **other currencies**
- Exchange rates must come from an **authoritative source** — not manual spreadsheets
- Rules matter: which rate applies on which date, and what to do when no rate exists
- Production systems need **resilience** when the rate provider is slow or unavailable

**Visual:** Simple before/after — “$100 USD purchase” → “135.50 CAD (using Treasury rate)”

---

## Slide 4 — Requirements Summary

**Headline:** What we committed to deliver  

| # | Requirement |
|---|-------------|
| 1 | Store purchase: description (≤50), date, USD amount → unique ID |
| 2 | Retrieve purchase converted to a target currency |
| 3 | Use Treasury rates: on/before purchase date, within **6 months** |
| 4 | Clear error if conversion impossible |
| 5 | Production-ready code + automated tests |
| 6 | Plug-and-play: no external database to run locally |

**Speaker notes:** All six implemented; enhanced with operational hardening (logging, retry, cache).

---

## Slide 5 — Solution at a Glance

**Headline:** One service, two capabilities  

```
POST /api/purchases          → Store transaction (UUID)
GET  /api/purchases/{id}     → Retrieve + convert (?currency=Canada-Dollar)
```

**Tech stack (footer bar):**  
Java 21 · Spring Boot 3.4 · Maven · H2 · Treasury API · **Log4j2** · **Spring Retry** · **Caffeine**

**Speaker notes:** REST + JSON, stateless API, embedded DB; Treasury calls are cached and retried.

---

## Slide 6 — System Context

**Headline:** Who talks to whom  

**Diagram:**
```
[Client] ──HTTP JSON──► [Wex Purchase Service] ──► [H2 Database]
                              │
                              └──► [U.S. Treasury API]
                                    (retry + 24h cache)
```

**Bullets:**
- Treasury API is **public read-only** (no API key)
- H2 is **in-memory** — zero install for reviewers
- Repeat currency lookups served from **in-memory cache**

---

## Slide 7 — Layered Architecture

**Headline:** Separation of concerns  

| Layer | Components | Responsibility |
|-------|------------|----------------|
| Presentation | `PurchaseController` | HTTP, validation, logging |
| Business | `PurchaseService`, `CurrencyConversionService` | Rules, orchestration, math |
| Data | `PurchaseTransactionRepository` | Persistence |
| Integration | `TreasuryExchangeRateClient`, `TreasuryHttpClient` | Cached + retried Treasury access |
| Cross-cutting | `CacheRetryConfig`, `ApiExceptionHandler` | Cache/retry config, error mapping |

**Speaker notes:** Treasury split into cached facade + retryable HTTP client for correct Spring AOP behavior.

---

## Slide 8 — Treasury Resilience & Logging (NEW)

**Headline:** Production-oriented external API integration  

### Retry (Spring Retry)
| Setting | Value |
|---------|-------|
| Max attempts | 3 |
| Backoff | 500ms → 1s → 2s (exponential) |
| Retries on | Network errors, 5xx |
| Exhausted | HTTP **503** Treasury API Unavailable |

### Cache (Caffeine)
| Setting | Value |
|---------|-------|
| Key | `currency \| purchaseDate \| windowStart` |
| TTL | 24 hours |
| Max entries | 500 |
| Benefit | Repeat GETs avoid Treasury HTTP call |

### Logging (Log4j2)
- **INFO:** store/retrieve requests, persisted IDs, conversion results
- **WARN:** validation errors, missing rates, retry failures
- **DEBUG:** cache misses, Treasury API parameters (enable via config)

**Call chain:**
```
@Cacheable TreasuryExchangeRateClient → @Retryable TreasuryHttpClient → RestClient
```

**Speaker notes:** This slide answers “how do you handle Treasury being down or slow?”

---

## Slide 9 — Design Decisions (What & Why)

**Headline:** Deliberate choices, not defaults  

| Decision | Rationale |
|----------|-----------|
| `BigDecimal` for money | Eliminates floating-point errors |
| UUID primary keys | Globally unique; distributed-ready |
| HTTP **422** for conversion failure | Purchase exists; business rule blocks conversion |
| HTTP **503** after retry exhaustion | Distinguishes upstream outage from bad input |
| **Log4j2** over default Logback | Explicit, configurable production logging |
| **Cache + retry as separate beans** | Correct AOP ordering; testable in isolation |
| Mock Treasury in tests | Deterministic CI (13 tests) |
| Java 21 LTS | Widely deployable today |

---

## Slide 10 — Exchange Rate Logic (Deep Dive)

**Headline:** The 6-month lookback rule  

**Algorithm:**
1. Window: `[purchaseDate − 6 months, purchaseDate]`
2. Resolve rates via **cache** (or Treasury API on miss, with **retry**)
3. Sort by date **descending** → take **most recent** qualifying rate
4. `convertedAmount = USD × rate`, round to **2 decimals**
5. If no rate → **422** | If Treasury down → **503**

**Example:** Purchase 2024-06-15, Canada-Dollar → rate 2024-03-31 @ 1.355 → $100 → **$135.50**

---

## Slide 11 — API & Data Model

**Headline:** Contracts are explicit  

**Store response:** identifier, description, transactionDate, purchaseAmountUsd  

**Retrieve response:** identifier, description, transactionDate, originalAmountUsd, exchangeRate, targetCurrency, convertedAmount  

**Error responses (ProblemDetail JSON):** 400, 404, 422, **503**

**Speaker notes:** Currency param = Treasury `country_currency_desc` (e.g. `Canada-Dollar`).

---

## Slide 12 — Quality & Reliability

**Headline:** Built and verified for production baseline  

**Testing (13 tests — all passing):**
| Category | Tests |
|----------|-------|
| Unit | Rounding, 6-month rule, service logic |
| Integration | Full HTTP store + retrieve (Treasury mocked) |
| Cache | Verifies single HTTP call for duplicate lookups |

**Error handling:**
| Case | HTTP |
|------|------|
| Invalid input | 400 |
| Unknown purchase | 404 |
| No rate in window | 422 |
| Treasury unavailable (after retries) | **503** |

**Operational:** Log4j2 logs at controller, service, and client layers.

---

## Slide 13 — Live Demo (Optional)

**Headline:** End-to-end in under 2 minutes  

1. `mvn spring-boot:run` — observe Log4j2 startup logs  
2. `POST /api/purchases` — store $100 office supplies  
3. `GET ...?currency=Canada-Dollar` — first call hits Treasury (with retry if needed)  
4. Repeat same GET — **cache hit** (faster; check DEBUG logs)  
5. (Optional) Invalid currency → 422  

**Fallback:** `mvn test` — 13 tests green.

---

## Slide 14 — Current Limitations (Honest Assessment)

**Headline:** Remaining gaps  

- H2 in-memory → **data lost on restart**  
- Cache is **per-instance** — not shared in multi-pod deployment (use Redis later)  
- **No circuit breaker** — sustained Treasury outage still attempts 3 retries  
- **No authentication** — required for production  
- Currency is free-text — typos fail at lookup  
- No CI/CD pipeline in repo yet  

**Speaker notes:** Retry/cache/logging address the biggest operational gaps from v1.

---

## Slide 15 — Roadmap

**Headline:** Evolution path  

| Phase | Status | Focus |
|-------|--------|-------|
| **1** | ✓ Done | Core requirements, tests, docs |
| **2** | ✓ Done | Log4j2, Treasury retry, Caffeine cache, 503 handling |
| **3** | Next | PostgreSQL, Flyway, Actuator, GitHub Actions |
| **4** | Planned | Circuit breaker, OpenAPI, currency aliases, contract tests |
| **5** | Future | AuthN/Z, distributed cache, performance baseline |

---

## Slide 16 — Risk Register (Summary)

| Risk | Mitigation |
|------|------------|
| Treasury API down | **Retry (3×) + cache + HTTP 503** |
| Treasury schema change | Contract tests, monitoring (planned) |
| Stale cached rates | 24h TTL; Treasury data is quarterly |
| Financial miscalculation | BigDecimal + unit tests |
| Data loss (H2) | PostgreSQL (planned) |

---

## Slide 17 — Summary & Ask

**Headline:** What we delivered  

✅ Both functional requirements  
✅ Financial precision (`BigDecimal`, cent rounding)  
✅ 6-month conversion rule with clear errors  
✅ **Log4j2** structured logging  
✅ **Spring Retry** + **Caffeine cache** for Treasury API  
✅ **13 automated tests** + design documentation  
✅ Clear upgrade path to production  

**Ask:** Questions? Happy to deep-dive on retry/cache design, logging, or roadmap.

---

## Slide 18 — Appendix / Backup Slides

*Hide unless Q&A needs detail*

- **A1:** Full project structure (`com.wex.purchase.*`)  
- **A2:** Treasury API filter syntax example  
- **A3:** `application.yml` — retry & cache settings  
- **A4:** `log4j2-spring.xml` — log levels and pattern  
- **A5:** Maven commands & Java 21 setup  
- **A6:** GitHub repo · `DESIGN.md` · `TEST_RESULTS.md`  

---

## Appendix — Timing Guide

| Slides | Minutes |
|--------|---------|
| 1–5 | 4 |
| 6–8 | 5 |
| 9–11 | 5 |
| 12 (demo) | 3 |
| 13–17 | 4 |
| Q&A | 5 |

---

## Appendix — Demo Script (copy-paste)

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
mvn spring-boot:run

# Store
curl -X POST http://localhost:8080/api/purchases \
  -H "Content-Type: application/json" \
  -d '{"description":"Office supplies","transactionDate":"2024-06-15","purchaseAmountUsd":100.00}'

# Convert (replace UUID — no curly braces in zsh)
curl "http://localhost:8080/api/purchases/YOUR-UUID-HERE?currency=Canada-Dollar"

# Same request again — cache hit (faster second time)
curl "http://localhost:8080/api/purchases/YOUR-UUID-HERE?currency=Canada-Dollar"
```

**Enable debug logging** (optional): set `com.wex.purchase: debug` in `application.yml`.

---

*See `DESIGN.md` (v1.1) for the complete technical design including logging, retry, and caching.*
