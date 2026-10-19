CREATE TABLE audit_log (
    id          UUID         PRIMARY KEY,
    actor_id    UUID,
    business_id UUID         NOT NULL,
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   UUID         NOT NULL,
    before      JSONB,
    after       JSONB,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_audit_business_time ON audit_log (business_id, created_at DESC);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id);
