CREATE TABLE businesses (
    id                    UUID         PRIMARY KEY,
    owner_id              UUID         NOT NULL REFERENCES users (id),
    name                  VARCHAR(120) NOT NULL,
    slug                  VARCHAR(60)  NOT NULL,
    time_zone             VARCHAR(64)  NOT NULL,
    contact_email         VARCHAR(254),
    contact_phone         VARCHAR(40),
    min_notice_minutes    INT          NOT NULL DEFAULT 120 CHECK (min_notice_minutes >= 0),
    max_advance_days      INT          NOT NULL DEFAULT 60 CHECK (max_advance_days > 0),
    cancel_cutoff_minutes INT          NOT NULL DEFAULT 1440 CHECK (cancel_cutoff_minutes >= 0),
    slot_interval_minutes INT          NOT NULL DEFAULT 15 CHECK (slot_interval_minutes > 0),
    created_at            TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX uq_businesses_slug ON businesses (slug);
CREATE INDEX ix_businesses_owner ON businesses (owner_id);

CREATE TABLE staff (
    id          UUID         PRIMARY KEY,
    business_id UUID         NOT NULL REFERENCES businesses (id),
    user_id     UUID         REFERENCES users (id),
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(254),
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_staff_business ON staff (business_id);
CREATE INDEX ix_staff_user ON staff (user_id);

CREATE TABLE working_hours (
    id          UUID        PRIMARY KEY,
    staff_id    UUID        NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    day_of_week VARCHAR(9)  NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    CHECK (start_time < end_time)
);
CREATE INDEX ix_working_hours_staff ON working_hours (staff_id);

-- kind OFF = unavailable (vacation, closure); EXTRA = additional working window on top of weekly hours
CREATE TABLE time_off (
    id        UUID         PRIMARY KEY,
    staff_id  UUID         NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    kind      VARCHAR(5)   NOT NULL DEFAULT 'OFF' CHECK (kind IN ('OFF', 'EXTRA')),
    starts_at TIMESTAMPTZ  NOT NULL,
    ends_at   TIMESTAMPTZ  NOT NULL,
    reason    VARCHAR(200),
    CHECK (starts_at < ends_at)
);
CREATE INDEX ix_time_off_staff_range ON time_off (staff_id, starts_at, ends_at);

CREATE TABLE offerings (
    id               UUID          PRIMARY KEY,
    business_id      UUID          NOT NULL REFERENCES businesses (id),
    name             VARCHAR(120)  NOT NULL,
    duration_minutes INT           NOT NULL CHECK (duration_minutes > 0),
    price            NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    currency         VARCHAR(3)    NOT NULL,
    buffer_before    INT           NOT NULL DEFAULT 0 CHECK (buffer_before >= 0),
    buffer_after     INT           NOT NULL DEFAULT 0 CHECK (buffer_after >= 0),
    active           BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_offerings_business ON offerings (business_id);

CREATE TABLE staff_offerings (
    staff_id    UUID NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    offering_id UUID NOT NULL REFERENCES offerings (id) ON DELETE CASCADE,
    PRIMARY KEY (staff_id, offering_id)
);
CREATE INDEX ix_staff_offerings_offering ON staff_offerings (offering_id);
