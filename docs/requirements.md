# Requirements (steering file)

Demo **Municipal Property Tax Management System**: shows how a legacy Oracle Forms/PL/SQL-style
app can be modernized into a secure, testable, cloud-native Spring Boot + React application.
All tax data is fictional. Sample rates are **not** official City of Mississauga rates.

## Workflow
User → Owner → Property → Assessment → Tax Account → Tax Rate → Tax Calculation → Tax Bill → Payment → Outstanding Balance

Formula: `Assessed Value × Applicable Tax Rate = Tax Amount`, rounded to cents half-up. Interim = 50% of prior-year tax;
final = annual − interim; overpayments rejected (decided in Stage 5).

## Roles
| Role | Can do |
|---|---|
| `RESIDENT` | View only their own properties, accounts, bills, payments |
| `TAX_OFFICER` | Manage properties, assessments, tax rates, tax bills, payments |
| `ADMIN` | Manage users and system configuration |

## Data (PostgreSQL) — exactly nine tables
`users`, `owners`, `properties`, `property_owners`, `assessments`, `tax_rates`, `tax_accounts`, `tax_bills`, `payments`

## Hard constraints
- No `tax_adjustments` table, entity, API, or module.
- Ownership is simulated; no marketplace, no real payments. A recorded payment is never a real transaction.
- DynamoDB only for `report_jobs` (Stage 12+), never for relational business data.
- Work one stage at a time; wait for approval between stages.

## Stage status
| Stage | Status |
|---|---|
| 1 Foundation | Done |
| 2 PostgreSQL + Flyway schema | Done |
| 3 JPA entities + repositories | Done |
| 4 REST APIs | Done |
| 5 Business logic | Done. Rules in [business-flow.md](business-flow.md) |
| 6 Backend testing | Done. Authorization tests deferred to Stage 8 |
| 7 React frontend | Done |
| 8 Auth & RBAC | Done — awaiting approval. Local dev JWT issuer stands in for Cognito (no AWS account yet) |
| 9–19 | Not started |
