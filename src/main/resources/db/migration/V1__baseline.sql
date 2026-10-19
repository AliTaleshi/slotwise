-- Needed for the booking exclusion constraint (equality on uuid + range overlap in one GiST index).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Required by ShedLock so scheduled jobs run once across several app instances.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
