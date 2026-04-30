CREATE TABLE mac_whitelist (
    id          BIGSERIAL PRIMARY KEY,
    mac_address VARCHAR(17) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 테스트용 허용 MAC 주소
INSERT INTO mac_whitelist (mac_address, description) VALUES
    ('AA:BB:CC:DD:EE:01', 'Admin workstation'),
    ('AA:BB:CC:DD:EE:02', 'Dev server'),
    ('AA:BB:CC:DD:EE:03', 'CI/CD server');
