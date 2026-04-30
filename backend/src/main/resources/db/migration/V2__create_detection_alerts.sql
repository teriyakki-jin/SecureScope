CREATE TABLE detection_alerts (
    id               BIGSERIAL    PRIMARY KEY,
    alert_type       VARCHAR(30)  NOT NULL,
    severity         VARCHAR(10)  NOT NULL,
    source_ip        VARCHAR(45)  NOT NULL,
    detail           TEXT,
    trigger_event_id BIGINT REFERENCES security_events(id),
    detected_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_detection_alerts_source_ip   ON detection_alerts (source_ip);
CREATE INDEX idx_detection_alerts_detected_at ON detection_alerts (detected_at DESC);
CREATE INDEX idx_detection_alerts_alert_type  ON detection_alerts (alert_type);
CREATE INDEX idx_detection_alerts_severity    ON detection_alerts (severity);
