# Progress

## Environment notes
- No Docker daemon in the cloud session: tests use zonky embedded-postgres (real PostgreSQL 16).
- `./mvnw verify` runs Spotless check, tests, JaCoCo. Run `./mvnw spotless:apply` first.

## Milestones
- [x] 1. Foundation
- [ ] 2. Auth
- [ ] 3. Business setup
- [ ] 4. Availability
- [ ] 5. Booking
- [ ] 6. Notifications
- [ ] 7. Webhooks
- [ ] 8. Owner and staff tools
- [ ] 9. Polish

## Known issues
- `docker compose up` / Dockerfile are written but not run here (no Docker daemon).
