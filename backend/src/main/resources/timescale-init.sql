CREATE EXTENSION IF NOT EXISTS timescaledb;
SELECT create_hypertable('ts_metric', 'event_time', if_not_exists => TRUE, migrate_data => TRUE);
CREATE INDEX IF NOT EXISTS idx_ts_metric_device_metric_event_desc
    ON ts_metric (device_id, metric_code, event_time DESC);
