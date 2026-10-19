-- blocked_from/blocked_to = appointment widened by the service buffers. time_range is generated from
-- them so the application cannot forget to keep it in sync; the exclusion constraints use it.

CREATE TABLE holds (
    id            UUID         PRIMARY KEY,
    business_id   UUID         NOT NULL REFERENCES businesses (id),
    staff_id      UUID         NOT NULL REFERENCES staff (id),
    offering_id   UUID         NOT NULL REFERENCES offerings (id),
    starts_at     TIMESTAMPTZ  NOT NULL,
    ends_at       TIMESTAMPTZ  NOT NULL,
    blocked_from  TIMESTAMPTZ  NOT NULL,
    blocked_to    TIMESTAMPTZ  NOT NULL,
    time_range    TSTZRANGE    GENERATED ALWAYS AS (tstzrange(blocked_from, blocked_to, '[)')) STORED,
    expires_at    TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CHECK (starts_at < ends_at AND blocked_from <= starts_at AND blocked_to >= ends_at),
    CONSTRAINT holds_no_overlap EXCLUDE USING gist (staff_id WITH =, time_range WITH &&)
);
CREATE INDEX ix_holds_expires_at ON holds (expires_at);

CREATE TABLE bookings (
    id              UUID          PRIMARY KEY,
    business_id     UUID          NOT NULL REFERENCES businesses (id),
    staff_id        UUID          NOT NULL REFERENCES staff (id),
    offering_id     UUID          NOT NULL REFERENCES offerings (id),
    customer_id     UUID          REFERENCES users (id),
    guest_name      VARCHAR(120)  NOT NULL,
    guest_email     VARCHAR(254)  NOT NULL,
    guest_phone     VARCHAR(40),
    starts_at       TIMESTAMPTZ   NOT NULL,
    ends_at         TIMESTAMPTZ   NOT NULL,
    blocked_from    TIMESTAMPTZ   NOT NULL,
    blocked_to      TIMESTAMPTZ   NOT NULL,
    time_range      TSTZRANGE     GENERATED ALWAYS AS (tstzrange(blocked_from, blocked_to, '[)')) STORED,
    status          VARCHAR(12)   NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    price           NUMERIC(10,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(100),
    hold_id         UUID,
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL,
    CHECK (starts_at < ends_at AND blocked_from <= starts_at AND blocked_to >= ends_at),
    -- The guarantee: one staff member can never have two non-cancelled bookings overlapping in time.
    CONSTRAINT bookings_no_overlap
        EXCLUDE USING gist (staff_id WITH =, time_range WITH &&) WHERE (status <> 'CANCELLED')
);
CREATE UNIQUE INDEX uq_bookings_idempotency ON bookings (business_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
-- a hold can be turned into at most one booking, even under racing confirm requests
CREATE UNIQUE INDEX uq_bookings_hold ON bookings (hold_id) WHERE hold_id IS NOT NULL;
CREATE INDEX ix_bookings_business_start ON bookings (business_id, starts_at);
CREATE INDEX ix_bookings_staff_start ON bookings (staff_id, starts_at);
CREATE INDEX ix_bookings_customer ON bookings (customer_id) WHERE customer_id IS NOT NULL;
CREATE INDEX ix_bookings_guest_email ON bookings (lower(guest_email));
