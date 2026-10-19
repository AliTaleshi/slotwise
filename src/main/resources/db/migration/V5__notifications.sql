-- One row per (booking, reminder type) that has been sent; the primary key makes "exactly once" a
-- database guarantee even if two app instances race.
CREATE TABLE reminders_sent (
    booking_id UUID        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    type       VARCHAR(20) NOT NULL CHECK (type IN ('REMINDER_24H', 'REMINDER_1H')),
    sent_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (booking_id, type)
);
