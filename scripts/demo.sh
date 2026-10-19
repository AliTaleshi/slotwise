#!/usr/bin/env bash
# End-to-end walkthrough against a running instance with the dev seed data:
# availability -> hold -> book -> view -> reschedule -> cancel.
# Usage: scripts/demo.sh [base-url]      (needs curl and jq)
set -euo pipefail
BASE="${1:-http://localhost:8080}"
SLUG=demo-clinic
step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }

# next Wednesday and the Wednesday after (always bookable, Mon-Fri hours, > 24h away)
day() { date -d "$1" +%F 2>/dev/null || date -v"$2" +%F; }
D1=$(day "next wednesday" +wed)
D2=$(day "$D1 + 7 days" +7d)

step "Public profile"
PROFILE=$(curl -fsS "$BASE/v1/businesses/$SLUG/public")
SERVICE=$(echo "$PROFILE" | jq -r '.services[] | select(.name=="Consultation") | .id')
echo "$PROFILE" | jq '{name, timeZone, services: [.services[].name], staff: [.staff[].name]}'

step "1. Available slots on $D1"
SLOTS=$(curl -fsS "$BASE/v1/businesses/$SLUG/availability?serviceId=$SERVICE&from=$D1&to=$D1")
echo "$SLOTS" | jq -c '.days[0].slots[0:4][] | {start, staffIds}'
START=$(echo "$SLOTS" | jq -r '.days[0].slots[2].start')

step "2. Hold the slot $START for 10 minutes"
HOLD=$(curl -fsS -X POST "$BASE/v1/holds" -H 'Content-Type: application/json' \
  -d "{\"serviceId\":\"$SERVICE\",\"start\":\"$START\"}")
echo "$HOLD" | jq .
HOLD_ID=$(echo "$HOLD" | jq -r .holdId)

STAFF_ID=$(echo "$HOLD" | jq -r .staffId)
step "   Someone else asking for the same staff member and time gets 409 SLOT_UNAVAILABLE"
curl -sS -X POST "$BASE/v1/holds" -H 'Content-Type: application/json' \
  -d "{\"serviceId\":\"$SERVICE\",\"staffId\":\"$STAFF_ID\",\"start\":\"$START\"}" | jq -c '{status, code, detail}'

step "3. Confirm the booking as a guest (Idempotency-Key makes retries safe)"
BOOKING=$(curl -fsS -X POST "$BASE/v1/bookings" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: demo-$(date +%s)" \
  -d "{\"holdId\":\"$HOLD_ID\",\"guestName\":\"Sam Guest\",\"guestEmail\":\"sam@example.com\"}")
echo "$BOOKING" | jq '{id, status, start, end, staffName, serviceName, price, currency}'
ID=$(echo "$BOOKING" | jq -r .id)
TOKEN=$(echo "$BOOKING" | jq -r .manageToken)

step "4. View with the manage-link token"
curl -fsS "$BASE/v1/bookings/$ID?token=$TOKEN" | jq '{status, start, guestEmail}'

step "5. Reschedule to a slot on $D2"
SLOTS2=$(curl -fsS "$BASE/v1/businesses/$SLUG/availability?serviceId=$SERVICE&from=$D2&to=$D2")
NEW=$(echo "$SLOTS2" | jq -r '.days[0].slots[4].start')
curl -fsS -X POST "$BASE/v1/bookings/$ID/reschedule" -H 'Content-Type: application/json' \
  -H "X-Manage-Token: $TOKEN" -d "{\"newStart\":\"$NEW\"}" | jq '{status, start, end}'

step "6. Cancel"
curl -fsS -X POST "$BASE/v1/bookings/$ID/cancel" -H "X-Manage-Token: $TOKEN" | jq '{status}'

step "Done. Emails (confirmation with .ics, reschedule, cancellation) are in Mailpit: http://localhost:8025"
