# Progress

## Environment notes
- No Docker daemon in the cloud session: tests use zonky embedded-postgres (real PostgreSQL 16).
- `./mvnw verify` runs Spotless check, tests, JaCoCo. Run `./mvnw spotless:apply` first.

## Milestones
- [x] 1. Foundation
- [x] 2. Auth
- [x] 3. Business setup
- [x] 4. Availability
- [ ] 5. Booking
- [ ] 6. Notifications
- [ ] 7. Webhooks
- [ ] 8. Owner and staff tools
- [ ] 9. Polish

## Known issues
- `docker compose up` / Dockerfile are written but not run here (no Docker daemon).
- Availability: `BusyTimeSource` is the seam between the pure calculator and bookings. `NoBusyTimeSource` is a placeholder removed in milestone 5.
- Buffers: appointment must sit in working hours and clear of time off; appointment+buffers must clear other bookings (stored ranges include buffers).
