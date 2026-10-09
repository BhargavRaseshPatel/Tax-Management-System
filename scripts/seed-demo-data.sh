#!/usr/bin/env bash
# Loads FICTIONAL demo data through the public REST API (so every business rule applies).
# Names, addresses, roll numbers and tax rates are invented; rates are not official municipal rates.
#
# Usage:  scripts/seed-demo-data.sh                 # API at http://localhost:8080
#         API=http://localhost:8081 scripts/seed-demo-data.sh
# Needs:  curl, jq, the backend running with DEV_AUTH=true (local sign-in), and an EMPTY database
#         (reset with: docker compose down -v && docker compose up -d --wait, then restart the backend).
set -euo pipefail

API="${API:-http://localhost:8080}"
command -v jq >/dev/null || { echo "jq is required (brew install jq)"; exit 1; }

TOKEN=""
call() { # call METHOD PATH [JSON]   (sends $TOKEN as the bearer token)
  local out
  if ! out=$(curl -sS --fail-with-body -X "$1" "$API$2" -H 'Content-Type: application/json' \
      ${TOKEN:+-H "Authorization: Bearer $TOKEN"} ${3:+-d "$3"}); then
    echo "✗ $1 $2 failed: $out" >&2
    exit 1
  fi
  printf '%s' "$out"
}
id() { jq -r '.id'; }
sign_in() { TOKEN=$(call POST /api/dev/token "{\"email\":\"$1\"}" | jq -r .accessToken); }

existing=$(call GET /api/dev/users | jq length) || { echo "Is the backend running with DEV_AUTH=true?"; exit 1; }
if [[ "$existing" != "1" && "${FORCE:-0}" != "1" ]]; then
  echo "Database already has $existing user(s). This script expects a fresh database (only the bootstrap admin):"
  echo "  docker compose down -v && docker compose up -d --wait   (then restart the backend)"
  exit 1
fi

echo "→ Users (as the bootstrap admin Sarah Chen)"
sign_in s.chen@civictax.demo
call POST /api/users '{"email":"m.vance@civictax.demo","fullName":"Marcus Vance","role":"TAX_OFFICER"}' >/dev/null
call POST /api/users '{"email":"d.lin@civictax.demo","fullName":"David Lin","role":"TAX_OFFICER"}' >/dev/null
ELENA_U=$(call POST /api/users '{"email":"elena.rostova@example.ca","fullName":"Elena Rostova","role":"RESIDENT"}' | id)
ARIS_U=$(call POST /api/users '{"email":"a.thorne@example.ca","fullName":"Aris Thorne","role":"RESIDENT"}' | id)
BELL=$(call POST /api/users '{"email":"j.bell@civictax.demo","fullName":"Jordan Bell","role":"ADMIN"}' | id)
call PUT "/api/users/$BELL" '{"fullName":"Jordan Bell","role":"ADMIN","active":false}' >/dev/null

sign_in m.vance@civictax.demo # tax data is managed by a tax officer

echo "→ Owners"
ELENA=$(call POST /api/owners "{\"userId\":$ELENA_U,\"fullName\":\"Elena Rostova\",\"email\":\"elena.rostova@example.ca\",\"phone\":\"(519) 555-0194\",\"mailingAddress\":\"742 Evergreen Terr, Springfield ON N2L 3G1\"}" | id)
MARCUS_R=$(call POST /api/owners '{"fullName":"Marcus Rostova","email":"m.rostova@example.ca","phone":"(519) 555-0812","mailingAddress":"18 King St S, Suite 402, Springfield ON N2J 1P8"}' | id)
HIGHLAND=$(call POST /api/owners '{"fullName":"Highland Industrial Holdings Ltd.","email":"contact@highland.example","phone":"(519) 555-8900","mailingAddress":"450 Northfield Dr, Springfield ON N2V 2C5"}' | id)
ARIS=$(call POST /api/owners "{\"userId\":$ARIS_U,\"fullName\":\"Dr. Aris Thorne\",\"email\":\"a.thorne@example.ca\",\"phone\":\"(519) 555-4321\",\"mailingAddress\":\"88 Erb St E, Springfield ON N2J 1L7\"}" | id)

echo "→ Tax rates (fictional)"
for rate in '2025 RESIDENTIAL 0.0075842' '2025 COMMERCIAL 0.0121100' '2025 INDUSTRIAL 0.0145000' \
            '2026 RESIDENTIAL 0.0078241' '2026 COMMERCIAL 0.0123700' '2026 INDUSTRIAL 0.0149850'; do
  read -r year cls value <<<"$rate"
  call POST /api/tax-rates "{\"taxYear\":$year,\"propertyClass\":\"$cls\",\"rate\":$value,\"description\":\"Demo $year $(tr "[:upper:]" "[:lower:]" <<<"$cls") rate (fictional)\"}" >/dev/null
done

# property ROLL ADDRESS POSTAL CLASS VALUE_2025 VALUE_2026 → prints tax account id
property() {
  local pid
  pid=$(call POST /api/properties "{\"rollNumber\":\"$1\",\"streetAddress\":\"$2\",\"city\":\"Springfield\",\"postalCode\":\"$3\",\"propertyClass\":\"$4\"}" | id)
  call POST "/api/properties/$pid/assessments" "{\"taxYear\":2025,\"assessedValue\":$5,\"assessmentDate\":\"2024-11-04\"}" >/dev/null
  call POST "/api/properties/$pid/assessments" "{\"taxYear\":2026,\"assessedValue\":$6,\"assessmentDate\":\"2025-11-03\"}" >/dev/null
  echo "$pid"
}
own() { call POST "/api/properties/$1/owners" "{\"ownerId\":$2,\"ownershipPercent\":$3}" >/dev/null; }
account() { call POST /api/tax-accounts "{\"propertyId\":$1}" | id; }
bill() { # bill ACCOUNT TYPE ISSUE DUE → prints bill JSON
  call POST "/api/tax-accounts/$1/bills" "{\"taxYear\":2026,\"billType\":\"$2\",\"issueDate\":\"$3\",\"dueDate\":\"$4\"}"
}
pay() { # pay BILL AMOUNT DATE METHOD REF → prints payment id (recorded as the signed-in officer)
  call POST "/api/tax-bills/$1/payments" "{\"amount\":$2,\"paymentDate\":\"$3\",\"method\":\"$4\",\"referenceNumber\":\"$5\"}" | id
}

echo "→ Properties, ownership, assessments, accounts"
P1=$(property 1904-020-040-12300 '742 Evergreen Terr' 'N2L 3G1' RESIDENTIAL 650000.00 685000.00); own "$P1" "$ELENA" 100
P2=$(property 1904-035-012-99400 '18 King St S, Suite 402' 'N2J 1P8' RESIDENTIAL 402000.00 420000.00); own "$P2" "$ELENA" 50; own "$P2" "$MARCUS_R" 50
P3=$(property 1904-010-008-54200 '450 Northfield Dr' 'N2V 2C5' INDUSTRIAL 3330000.00 3450000.00); own "$P3" "$HIGHLAND" 100
P4=$(property 1904-040-022-77100 '88 Erb St E' 'N2J 1L7' COMMERCIAL 1215000.00 1280000.00); own "$P4" "$ARIS" 100
property 1904-051-003-10500 '124 Columbia St W' 'N2L 3K8' RESIDENTIAL 512000.00 530000.00 >/dev/null # on roll, no account yet
A1=$(account "$P1"); A2=$(account "$P2"); A3=$(account "$P3"); A4=$(account "$P4")

echo "→ 2026 bills and simulated payments"
n=0
for acct in "$A1" "$A2" "$A3" "$A4"; do
  n=$((n + 1))
  interim=$(bill "$acct" INTERIM 2026-02-01 2026-03-31)
  final=$(bill "$acct" FINAL 2026-06-15 2026-09-30)
  i_id=$(jq -r .id <<<"$interim"); i_amt=$(jq -r .amount <<<"$interim")
  f_id=$(jq -r .id <<<"$final"); f_amt=$(jq -r .amount <<<"$final")
  case $n in
    1) pay "$i_id" "$i_amt" 2026-03-12 BANK_TRANSFER "PAY-2026-0412" >/dev/null
       pay "$f_id" 1000.00 2026-09-20 BANK_TRANSFER "PAY-2026-0913" >/dev/null ;;          # final partly paid
    2) pay "$i_id" "$i_amt" 2026-03-31 PRE_AUTHORIZED "PAY-2026-0688" >/dev/null ;;        # final unpaid
    3) pay "$i_id" "$i_amt" 2026-03-20 CHEQUE "PAY-2026-0501" >/dev/null
       pay "$f_id" "$f_amt" 2026-09-25 BANK_TRANSFER "PAY-2026-0977" >/dev/null ;;          # fully paid
    4) wrong=$(pay "$i_id" 500.00 2026-03-02 COUNTER "PAY-2026-0333")
       call POST "/api/payments/$wrong/reverse" >/dev/null                                  # correction example
       pay "$i_id" 1000.00 2026-03-28 CHEQUE "PAY-2026-0344" >/dev/null ;;                  # interim partly paid
  esac
done

echo "✓ Demo data loaded. Sign in at http://localhost:5173 as Elena Rostova (resident), Marcus Vance (officer), or Sarah Chen (admin)."
