CREATE SCHEMA IF NOT EXISTS crm;
CREATE SCHEMA IF NOT EXISTS telemetry;
CREATE SCHEMA IF NOT EXISTS reporting;

CREATE TABLE IF NOT EXISTS crm.customers (
    keycloak_user_id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(128) NOT NULL,
    full_name VARCHAR(256) NOT NULL,
    email VARCHAR(256) NOT NULL,
    prosthesis_model VARCHAR(128) NOT NULL,
    prosthesis_serial VARCHAR(64) NOT NULL,
    assigned_at DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS telemetry.sensor_events (
    event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keycloak_user_id VARCHAR(64) NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    steps_count INTEGER NOT NULL,
    grip_cycles INTEGER NOT NULL,
    battery_level NUMERIC(5, 2) NOT NULL,
    load_kg NUMERIC(6, 2) NOT NULL,
    error_code VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS reporting.user_report_mart (
    keycloak_user_id VARCHAR(64) NOT NULL,
    report_period_start DATE NOT NULL,
    report_period_end DATE NOT NULL,
    processed_until TIMESTAMPTZ NOT NULL,
    username VARCHAR(128) NOT NULL,
    full_name VARCHAR(256) NOT NULL,
    email VARCHAR(256) NOT NULL,
    prosthesis_model VARCHAR(128) NOT NULL,
    prosthesis_serial VARCHAR(64) NOT NULL,
    assigned_at DATE NOT NULL,
    telemetry_events INTEGER NOT NULL,
    total_steps INTEGER NOT NULL,
    total_grip_cycles INTEGER NOT NULL,
    avg_battery_level NUMERIC(5, 2) NOT NULL,
    min_battery_level NUMERIC(5, 2) NOT NULL,
    max_load_kg NUMERIC(6, 2) NOT NULL,
    error_events INTEGER NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (keycloak_user_id, report_period_start, report_period_end)
);

CREATE INDEX IF NOT EXISTS idx_sensor_events_user_time
    ON telemetry.sensor_events (keycloak_user_id, event_time);

CREATE INDEX IF NOT EXISTS idx_user_report_mart_user_period
    ON reporting.user_report_mart (keycloak_user_id, report_period_start, report_period_end);

INSERT INTO crm.customers (
    keycloak_user_id,
    username,
    full_name,
    email,
    prosthesis_model,
    prosthesis_serial,
    assigned_at
) VALUES
    ('111a0f6e-15f9-4378-a4c7-e68513b1f566', 'afjord', 'Андрей Фёдоров', 'afjord@yandex.ru', 'BionicPRO Arm X2', 'BP-AF-2026-001', DATE '2026-01-15'),
    ('caf4e1cf-a30e-4faf-bb44-75cd9fac0bb9', 'alex.johnson', 'Alex Johnson', 'alex.johnson@example.com', 'BionicPRO Knee K1', 'BP-AJ-2025-014', DATE '2025-11-03'),
    ('936ac57d-482d-46a4-9d04-84fe379aa68a', 'jane.smith', 'Jane Smith', 'jane.smith@example.com', 'BionicPRO Arm X1', 'BP-JS-2025-021', DATE '2025-09-18'),
    ('62c2a0b2-1ab7-4f9e-9417-fbd5b7ffd9f3', 'john.doe', 'John Doe', 'john.doe@example.com', 'BionicPRO Knee K1', 'BP-JD-2025-018', DATE '2025-10-11')
ON CONFLICT (keycloak_user_id) DO UPDATE SET
    username = EXCLUDED.username,
    full_name = EXCLUDED.full_name,
    email = EXCLUDED.email,
    prosthesis_model = EXCLUDED.prosthesis_model,
    prosthesis_serial = EXCLUDED.prosthesis_serial,
    assigned_at = EXCLUDED.assigned_at;

INSERT INTO telemetry.sensor_events (
    keycloak_user_id,
    event_time,
    steps_count,
    grip_cycles,
    battery_level,
    load_kg,
    error_code
) VALUES
    ('111a0f6e-15f9-4378-a4c7-e68513b1f566', TIMESTAMPTZ '2026-06-01 09:00:00+03', 1240, 320, 91.0, 3.8, NULL),
    ('111a0f6e-15f9-4378-a4c7-e68513b1f566', TIMESTAMPTZ '2026-06-01 18:00:00+03', 1680, 410, 68.0, 5.4, NULL),
    ('111a0f6e-15f9-4378-a4c7-e68513b1f566', TIMESTAMPTZ '2026-06-02 12:00:00+03', 1430, 385, 73.0, 4.9, NULL),
    ('111a0f6e-15f9-4378-a4c7-e68513b1f566', TIMESTAMPTZ '2026-06-03 17:30:00+03', 970, 250, 42.0, 6.1, 'LOW_BATTERY'),
    ('caf4e1cf-a30e-4faf-bb44-75cd9fac0bb9', TIMESTAMPTZ '2026-06-01 10:00:00+03', 2200, 0, 87.0, 78.0, NULL),
    ('caf4e1cf-a30e-4faf-bb44-75cd9fac0bb9', TIMESTAMPTZ '2026-06-02 10:00:00+03', 1980, 0, 75.0, 82.0, NULL),
    ('936ac57d-482d-46a4-9d04-84fe379aa68a', TIMESTAMPTZ '2026-06-01 11:00:00+03', 0, 520, 84.0, 4.2, NULL),
    ('936ac57d-482d-46a4-9d04-84fe379aa68a', TIMESTAMPTZ '2026-06-02 11:00:00+03', 0, 610, 71.0, 4.8, NULL),
    ('62c2a0b2-1ab7-4f9e-9417-fbd5b7ffd9f3', TIMESTAMPTZ '2026-06-01 08:00:00+03', 1750, 0, 80.0, 76.0, NULL),
    ('62c2a0b2-1ab7-4f9e-9417-fbd5b7ffd9f3', TIMESTAMPTZ '2026-06-03 08:00:00+03', 1620, 0, 66.0, 84.0, 'OVERLOAD')
ON CONFLICT DO NOTHING;
