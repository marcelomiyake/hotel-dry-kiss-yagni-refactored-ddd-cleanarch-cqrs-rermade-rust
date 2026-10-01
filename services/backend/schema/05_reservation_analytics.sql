CREATE SCHEMA IF NOT EXISTS reservation_analytics;

CREATE TABLE IF NOT EXISTS reservation_analytics.journeys (
    journey_id uuid PRIMARY KEY,
    started_at timestamptz NOT NULL DEFAULT now(),
    last_activity_at timestamptz NOT NULL DEFAULT now(),
    last_screen varchar(16) NOT NULL CHECK (last_screen IN (
        'search', 'results', 'details', 'checkout', 'confirmation', 'bookings', 'staff'
    )),
    last_sequence integer NOT NULL CHECK (last_sequence > 0),
    completed_at timestamptz
);

CREATE TABLE IF NOT EXISTS reservation_analytics.journey_events (
    id bigserial PRIMARY KEY,
    journey_id uuid NOT NULL REFERENCES reservation_analytics.journeys(journey_id),
    sequence integer NOT NULL CHECK (sequence > 0),
    event_type varchar(16) NOT NULL CHECK (event_type IN ('started', 'screen_viewed', 'completed')),
    screen varchar(16) NOT NULL CHECK (screen IN (
        'search', 'results', 'details', 'checkout', 'confirmation', 'bookings', 'staff'
    )),
    occurred_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (journey_id, sequence)
);

CREATE INDEX IF NOT EXISTS journey_events_occurred_at_idx
    ON reservation_analytics.journey_events (occurred_at DESC);

CREATE OR REPLACE VIEW reservation_analytics.journey_outcomes AS
SELECT journey.journey_id,
       journey.started_at,
       journey.last_activity_at,
       journey.last_screen,
       CASE
           WHEN journey.completed_at IS NOT NULL OR booking.payment_id IS NOT NULL THEN 'COMPLETED'
           WHEN booking.status = 'PAYMENT_FAILED' THEN 'PAYMENT_FAILED'
           WHEN journey.last_activity_at <= now() - interval '30 minutes' THEN 'ABANDONED'
           ELSE 'IN_PROGRESS'
       END AS status,
       CASE
           WHEN journey.completed_at IS NULL
             AND booking.payment_id IS NULL
             AND booking.status IS DISTINCT FROM 'PAYMENT_FAILED'
             AND journey.last_activity_at <= now() - interval '30 minutes'
           THEN journey.last_activity_at + interval '30 minutes'
           ELSE NULL
       END AS abandoned_at
FROM reservation_analytics.journeys AS journey
LEFT JOIN reservations.bookings AS booking ON booking.id = journey.journey_id;
