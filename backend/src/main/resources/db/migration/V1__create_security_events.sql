CREATE TYPE event_type AS ENUM (
    'LOGIN_SUCCESS',
    'LOGIN_FAIL',
    'UNAUTHORIZED_ACCESS',
    'PORT_SCAN'
);

CREATE TABLE security_events (
    id          BIGSERIAL PRIMARY KEY,
    event_type  event_type  NOT NULL,
    source_ip   VARCHAR(45) NOT NULL,
    mac_address VARCHAR(17),
    target_port INTEGER,
    user_id     VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_events_source_ip    ON security_events (source_ip);
CREATE INDEX idx_security_events_occurred_at  ON security_events (occurred_at DESC);
CREATE INDEX idx_security_events_event_type   ON security_events (event_type);
