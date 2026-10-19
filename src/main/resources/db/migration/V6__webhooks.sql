CREATE TABLE webhook_endpoints (
    id          UUID         PRIMARY KEY,
    business_id UUID         NOT NULL REFERENCES businesses (id),
    url         VARCHAR(500) NOT NULL,
    secret      VARCHAR(100) NOT NULL,
    events      VARCHAR(200) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_webhook_endpoints_business ON webhook_endpoints (business_id) WHERE active;

-- Outbox: rows are written in the same transaction as the booking change, then delivered after commit.
CREATE TABLE webhook_deliveries (
    id              UUID        PRIMARY KEY,
    endpoint_id     UUID        NOT NULL REFERENCES webhook_endpoints (id) ON DELETE CASCADE,
    event_type      VARCHAR(40) NOT NULL,
    payload         TEXT        NOT NULL,
    status          VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    attempts        INT         NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    delivered_at    TIMESTAMPTZ
);
CREATE INDEX ix_webhook_deliveries_due ON webhook_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_webhook_deliveries_endpoint ON webhook_deliveries (endpoint_id, created_at DESC);

CREATE TABLE webhook_delivery_attempts (
    id           UUID        PRIMARY KEY,
    delivery_id  UUID        NOT NULL REFERENCES webhook_deliveries (id) ON DELETE CASCADE,
    attempted_at TIMESTAMPTZ NOT NULL,
    status_code  INT,
    error        VARCHAR(500),
    duration_ms  BIGINT      NOT NULL
);
CREATE INDEX ix_webhook_attempts_delivery ON webhook_delivery_attempts (delivery_id, attempted_at);
