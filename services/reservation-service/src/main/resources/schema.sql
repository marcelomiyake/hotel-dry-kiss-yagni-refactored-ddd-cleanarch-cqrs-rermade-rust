CREATE SCHEMA IF NOT EXISTS reservations;

CREATE TABLE IF NOT EXISTS reservations.room_inventory (
    hotel_id uuid NOT NULL,
    room_type_id uuid NOT NULL,
    inventory_date date NOT NULL,
    total_inventory integer NOT NULL CHECK (total_inventory > 0),
    total_reserved integer NOT NULL DEFAULT 0 CHECK (total_reserved >= 0),
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (hotel_id, room_type_id, inventory_date),
    CHECK (total_reserved <= floor(total_inventory * 1.10))
);

CREATE TABLE IF NOT EXISTS reservations.bookings (
    id uuid PRIMARY KEY,
    hotel_id uuid NOT NULL,
    room_type_id uuid NOT NULL,
    hotel_name varchar(160) NOT NULL,
    city varchar(100) NOT NULL,
    district varchar(100) NOT NULL,
    image_path varchar(240) NOT NULL,
    image_alt varchar(240) NOT NULL,
    room_type_name varchar(120) NOT NULL,
    check_in date NOT NULL,
    check_out date NOT NULL,
    rooms integer NOT NULL CHECK (rooms > 0),
    guests integer NOT NULL CHECK (guests > 0),
    guest_name varchar(160) NOT NULL,
    guest_email varchar(254) NOT NULL,
    total numeric(12, 2) NOT NULL CHECK (total > 0),
    status varchar(24) NOT NULL CHECK (status IN ('PAYMENT_PENDING', 'CONFIRMED', 'CANCELLED', 'PAYMENT_FAILED')),
    payment_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (check_out > check_in)
);

CREATE INDEX IF NOT EXISTS bookings_email_created_idx
    ON reservations.bookings (lower(guest_email), created_at DESC);

INSERT INTO reservations.room_inventory (hotel_id, room_type_id, inventory_date, total_inventory)
SELECT inventory.hotel_id, inventory.room_type_id, stay_date::date, inventory.total_inventory
FROM (VALUES
    ('11111111-1111-1111-1111-111111111111'::uuid, '10000000-0000-0000-0000-000000000101'::uuid, 12),
    ('11111111-1111-1111-1111-111111111111'::uuid, '10000000-0000-0000-0000-000000000102'::uuid, 8),
    ('22222222-2222-2222-2222-222222222222'::uuid, '20000000-0000-0000-0000-000000000201'::uuid, 10),
    ('22222222-2222-2222-2222-222222222222'::uuid, '20000000-0000-0000-0000-000000000202'::uuid, 6)
) AS inventory(hotel_id, room_type_id, total_inventory)
CROSS JOIN generate_series(current_date, current_date + 730, interval '1 day') AS stay_date
ON CONFLICT (hotel_id, room_type_id, inventory_date) DO NOTHING;
