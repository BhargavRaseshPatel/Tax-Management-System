# Business Flow

User → Owner → Property → Assessment → Tax Account → Tax Rate → Tax Calculation → Tax Bill → Payment → Outstanding Balance.
Demo rules only; all values fictional. Payments are simulated records — no money moves.

## Calculation ([TaxCalculator](../backend/src/main/java/com/propertytax/taxbill/TaxCalculator.java))
`Annual tax = Assessed Value × Applicable Tax Rate`, rounded to cents **half-up**. The rate is looked up by
(tax year, property class).

| Bill | Amount | Inputs referenced |
|---|---|---|
| INTERIM | 50% of the **previous** year's annual tax | previous year's assessment + rate |
| FINAL | this year's annual tax − live interim (if any) | this year's assessment + rate |

Example: 2025 = 620,000 × 0.0072 = 4,464.00 → interim 2026 = 2,232.00;
2026 = 650,000 × 0.0075 = 4,875.00 → final 2026 = 2,643.00.

## Rules
- One live bill per account / year / type; interim must precede final; account must be ACTIVE.
- Missing assessment or rate → 409 naming what's missing. Final < 0 (interim exceeded annual) → 409 (no refunds in scope).
- An assessment or rate used by a live bill is frozen (409); cancel the bill, correct, then reissue.
- Bill numbers: `<account>-<year>-<I|F>-<n>`; `n` increments when a cancelled bill is reissued.

## Bill status
```
ISSUED ──payment──► PARTIALLY_PAID ──payment (reaches amount)──► PAID
   ▲                    │   ▲                                      │
   └──reversal──────────┘   └──────────────reversal────────────────┘
ISSUED ──cancel (no recorded payments)──► CANCELLED
```
Status is recomputed from the sum of RECORDED payments after every payment or reversal. A 0.00 bill is PAID on issue.

## Payments
- 0 < amount ≤ outstanding on the bill; overpayment → 409. Bill must not be PAID or CANCELLED.
- `referenceNumber` unique → duplicate recording rejected (service check + DB unique constraint).
- Recorder must be an active TAX_OFFICER or ADMIN (temporary `recordedByUserId`; Stage 8 uses the JWT user).
- Corrections: `RECORDED → REVERSED`; payments are never deleted.

## Outstanding balance
Derived on read, never stored: `Σ live bill amounts − Σ RECORDED payments` per account (and per bill).

## Transactions & concurrency
Each operation is one `@Transactional` service call; any rejection rolls back fully (tested: rejected payments leave no row).
Row locks (`SELECT … FOR UPDATE`): the **account** while generating a bill, the **bill** while paying, reversing or
cancelling — so concurrent requests can't double-issue or jointly overpay. The DB constraints remain the final guard.
