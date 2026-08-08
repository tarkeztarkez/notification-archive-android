CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID UNIQUE NOT NULL,
    device_id TEXT NOT NULL,
    device_name TEXT,
    package_name TEXT NOT NULL,
    app_name TEXT,
    notification_key TEXT,
    notification_id INTEGER,
    notification_tag TEXT,
    title TEXT,
    body TEXT,
    expanded_text TEXT,
    subtext TEXT,
    category TEXT,
    channel_id TEXT,
    group_key TEXT,
    posted_at TIMESTAMPTZ,
    captured_at TIMESTAMPTZ NOT NULL,
    removed_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type TEXT NOT NULL CHECK (event_type IN ('posted', 'updated', 'removed')),
    raw_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload_version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS notifications_captured_at_idx ON notifications (captured_at DESC);
CREATE INDEX IF NOT EXISTS notifications_package_idx ON notifications (package_name, captured_at DESC);
CREATE INDEX IF NOT EXISTS notifications_device_idx ON notifications (device_id, captured_at DESC);
CREATE INDEX IF NOT EXISTS notifications_event_type_idx ON notifications (event_type, captured_at DESC);
CREATE INDEX IF NOT EXISTS notifications_search_idx ON notifications USING GIN (
    to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(body, '') || ' ' || coalesce(expanded_text, ''))
);

