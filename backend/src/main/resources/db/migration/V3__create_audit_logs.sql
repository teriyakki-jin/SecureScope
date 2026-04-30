CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    alert_id     BIGINT REFERENCES detection_alerts(id) NOT NULL,
    data         TEXT    NOT NULL,
    prev_hash    CHAR(64),
    current_hash CHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_audit_logs_current_hash ON audit_logs (current_hash);
