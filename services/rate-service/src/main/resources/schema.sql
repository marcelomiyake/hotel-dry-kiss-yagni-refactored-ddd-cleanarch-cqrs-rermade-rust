CREATE SCHEMA IF NOT EXISTS nightly_rates;

CREATE TABLE IF NOT EXISTS nightly_rates.rates (
    room_type_id uuid NOT NULL,
    rate_date date NOT NULL,
    amount numeric(12, 2) NOT NULL CHECK (amount > 0),
    PRIMARY KEY (room_type_id, rate_date)
);

INSERT INTO nightly_rates.rates (room_type_id, rate_date, amount)
SELECT room.room_type_id,
       rate_date::date,
       round(room.base_rate * CASE WHEN extract(isodow FROM rate_date) IN (5, 6) THEN 1.15 ELSE 1 END, 2)
FROM (VALUES
    ('10000000-0000-0000-0000-000000000101'::uuid, 620.00::numeric),
    ('10000000-0000-0000-0000-000000000102'::uuid, 810.00::numeric),
    ('20000000-0000-0000-0000-000000000201'::uuid, 410.00::numeric),
    ('20000000-0000-0000-0000-000000000202'::uuid, 535.00::numeric)
) AS room(room_type_id, base_rate)
CROSS JOIN generate_series(current_date, current_date + 730, interval '1 day') AS rate_date
ON CONFLICT (room_type_id, rate_date) DO NOTHING;
