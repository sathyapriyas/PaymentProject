# Wex Purchase Service — Slide Deck Outline

**Audience:** Engineering manager / hiring panel  
**Duration:** 15–20 minutes (+ 5 min Q&A)  
**Presenter notes:** Pair with live demo (`mvn spring-boot:run` + curl) on slides 10–11 if time allows.

---

## Slide 1 — Title

**Title:** Wex Purchase Service — Technical Design Review  
**Subtitle:** USD purchase storage with U.S. Treasury currency conversion  
**Footer:** Your name · Date · [GitHub: sathyapriyas/WEXProject](https://github.com/sathyapriyas/WEXProject)

**Speaker notes:** Set context — assessment project built to production-style standards, not a prototype.

---

## Slide 2 — Agenda

1. Problem & business requirements  
2. Solution overview  
3. Architecture & data flow  
4. Key design decisions  
5. Exchange rate logic (core business rule)  
6. API & data model  
7. Quality: testing & error handling  
8. Current limitations  
9. Roadmap & improvement opportunities  
10. Demo (optional)  
11. Q&A  

---

## Slide 3 — Problem Statement

**Headline:** Why this service exists  

**Bullets:**
- Organizations record purchases in **USD** but must report or analyze spend in **other currencies**
- Exchange rates must come from an **authoritative source** — not manual spreadsheets
- Rules matter: which rate applies on which date, and what to do when no rate exists

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

**Speaker notes:** All six are implemented; call out 6-month rule as the most nuanced requirement.

---

## Slide 5 — Solution at a Glance

**Headline:** One service, two capabilities  

```
POST /api/purchases          → Store transaction (UUID)
GET  /api/purchases/{id}     → Retrieve + convert (?currency=Canada-Dollar)
```

**Tech stack (footer bar):**  
Java 21 · Spring Boot 3.4 · Maven · H2 · Treasury REST API

**Speaker notes:** Emphasize REST + JSON, stateless API, embedded DB for reviewer convenience.

---

## Slide 6 — System Context

**Headline:** Who talks to whom  

**Diagram (draw or paste):**
```
[Client] ──HTTP JSON──► [Wex Purchase Service] ──► [H2 Database]
                              │
                              └──► [U.S. Treasury Fiscal Data API]
```

**Bullets:**
- Treasury API is **public read-only** (no API key in v1)
- H2 is **in-memory** — zero install for developers and reviewers

---

## Slide 7 — Layered Architecture

**Headline:** Separation of concerns  

| Layer | Components | Responsibility |
|-------|------------|----------------|
| Presentation | `PurchaseController` | HTTP, validation, status codes |
| Business | `PurchaseService`, `CurrencyConversionService` | Rules, orchestration, math |
| Data | `PurchaseTransactionRepository`, JPA entity | Persistence |
| Integration | `TreasuryExchangeRateClient` | External API |

**Speaker notes:** Standard Spring layering — easy to test, easy to extend (e.g. swap H2 for PostgreSQL).

---

## Slide 8 — Design Decisions (What & Why)

**Headline:** Deliberate choices, not defaults  

| Decision | Rationale |
|----------|-----------|
| `BigDecimal` for money | Eliminates floating-point errors |
| UUID primary keys | Globally unique; distributed-ready |
| DTOs vs. entity | Stable API if persistence changes |
| HTTP **422** for conversion failure | Purchase exists; business rule blocks conversion |
| Mock Treasury in tests | Deterministic CI, no network flakiness |
| Java 21 LTS | Supportability (spec cited JDK 25; 21 is deployable today) |

**Speaker notes:** Pick 2–3 to expand if asked in Q&A.

---

## Slide 9 — Exchange Rate Logic (Deep Dive)

**Headline:** The 6-month lookback rule  

**Algorithm (bullets):**
1. Window: `[purchaseDate − 6 months, purchaseDate]`
2. Query Treasury: matching `country_currency_desc`, `record_date` in window
3. Sort by date **descending** → take **most recent** rate on or before purchase date
4. `convertedAmount = USD × rate`, round to **2 decimals** (nearest cent)
5. If no rate → **422** with explicit error message

**Example:** Purchase 2024-06-15, Canada-Dollar → rate 2024-03-31 @ 1.355 → $100 → **$135.50**

**Speaker notes:** This slide proves you understood the hardest requirement.

---

## Slide 10 — API & Data Model

**Headline:** Contracts are explicit  

**Store response fields:** identifier, description, transactionDate, purchaseAmountUsd  

**Retrieve response fields:** identifier, description, transactionDate, originalAmountUsd, exchangeRate, targetCurrency, convertedAmount  

**Persistence:** `PurchaseTransaction` — UUID, description (50), date, amountUsd (19,2)

**Speaker notes:** Currency param = Treasury `country_currency_desc` (e.g. `Canada-Dollar`).

---

## Slide 11 — Quality & Reliability

**Headline:** Built for production baseline  

**Testing (11 tests):**
- Unit: rounding, 6-month rule, service logic  
- Integration: full HTTP store + retrieve (Treasury mocked)  

**Error handling:**
| Case | HTTP |
|------|------|
| Invalid input | 400 |
| Unknown purchase | 404 |
| No rate in window | 422 |

**Speaker notes:** Non-functional tests (load/perf) explicitly out of scope per requirements.

---

## Slide 12 — Live Demo (Optional)

**Headline:** End-to-end in under 2 minutes  

1. `mvn spring-boot:run`  
2. `POST /api/purchases` — store $100 office supplies  
3. `GET /api/purchases/{id}?currency=Canada-Dollar` — show conversion  
4. (Optional) Invalid currency or date → show 422/400  

**Fallback if no network:** Show test output `mvn test` — all green.

---

## Slide 13 — Current Limitations (Honest Assessment)

**Headline:** Known gaps in v1 — by design  

- H2 in-memory → **data lost on restart**  
- **Synchronous** Treasury call on every GET → latency + availability coupling  
- **No authentication** — fine for assessment; required for production  
- Currency is free-text — typos fail at lookup  
- No CI/CD pipeline in repo yet  

**Speaker notes:** Framing limitations as a roadmap input shows maturity.

---

## Slide 14 — Improvement Roadmap

**Headline:** Path to production  

| Phase | Focus | Examples |
|-------|-------|----------|
| **1 ✓ Done** | Core requirements + tests | Current delivery |
| **2** (1–2 wks) | Hardening | PostgreSQL, Flyway, Actuator, GitHub Actions |
| **3** (2–4 wks) | Resilience & UX | Rate cache, retry, OpenAPI, currency aliases |
| **4** | Enterprise | AuthN/Z, metrics, audit log, scale |

---

## Slide 15 — Risk Register (Summary)

| Risk | Mitigation |
|------|------------|
| Treasury API down | Retry + cache + clear errors |
| Treasury schema change | Contract tests, monitoring |
| Financial miscalculation | BigDecimal + unit tests with known rates |
| Data loss (H2) | PostgreSQL for production |

---

## Slide 16 — Summary & Ask

**Headline:** What we delivered  

✅ Both functional requirements  
✅ Financial precision (`BigDecimal`, cent rounding)  
✅ Documented 6-month conversion rule  
✅ Automated tests + design documentation  
✅ Clear upgrade path to production  

**Ask:** Questions? Happy to deep-dive on architecture, testing, or roadmap.

---

## Slide 17 — Appendix / Backup Slides

*Hide unless Q&A needs detail*

- **A1:** Full project structure (`com.wex.purchase.*`)  
- **A2:** Treasury API filter syntax example  
- **A3:** Maven commands & Java 21 setup  
- **A4:** GitHub repo link & README  
- **A5:** Reference to `DESIGN.md` for full technical spec  

---

## Appendix — Timing Guide

| Slides | Minutes |
|--------|---------|
| 1–5 | 4 |
| 6–9 | 6 |
| 10–11 | 4 |
| 12 (demo) | 3 |
| 13–16 | 4 |
| Q&A | 5 |

---

## Appendix — Demo Script (copy-paste)

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
mvn spring-boot:run

curl -X POST http://localhost:8080/api/purchases \
  -H "Content-Type: application/json" \
  -d '{"description":"Office supplies","transactionDate":"2024-06-15","purchaseAmountUsd":100.00}'

curl "http://localhost:8080/api/purchases/{UUID}?currency=Canada-Dollar"
```

---

*See `DESIGN.md` for the complete technical design document.*
