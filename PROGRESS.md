# Progress

## Environment notes
- No Docker daemon in the cloud session: tests use zonky embedded-postgres (real PostgreSQL 16).
- `./mvnw verify` runs Spotless check, tests, JaCoCo. Run `./mvnw spotless:apply` first.

## Milestones
- [x] 1. Foundation
- [x] 2. Auth
- [x] 3. Business setup
- [x] 4. Availability
- [x] 5. Booking
- [x] 6. Notifications
- [x] 7. Webhooks
- [ ] 8. Owner and staff tools
- [ ] 9. Polish

## Known issues
- `docker compose up` / Dockerfile are written but not run here (no Docker daemon).
- Buffers: appointment must sit in working hours and clear of time off; appointment+buffers must clear other bookings (stored ranges include buffers).
- Booking: holds and bookings are separate tables, each with a GiST exclusion constraint on a generated `time_range` (appointment + buffers). Hold-vs-booking overlap is checked after the hold insert; the bookings constraint is the final guard.
- Domain events (`BookingEvents`) are published in-transaction; milestone 6 listens with AFTER_COMMIT.
- Notifications: emails via AFTER_COMMIT + @Async listeners; reminders use windows (24h: 2h grace, 1h: 30min grace) and a `reminders_sent` PK claim for exactly-once. Jobs are ShedLock-guarded and disabled in tests (`slotwise.scheduling.enabled=false`).
- Webhooks: transactional outbox (rows written in the booking tx, delivered after commit + by retry job), HMAC-SHA256 `X-Slotwise-Signature: t=..,v1=..`, 6 attempts with backoff, per-attempt log, SSRF guard (`UrlGuard`; private targets only with WEBHOOKS_ALLOW_PRIVATE_TARGETS=true).
