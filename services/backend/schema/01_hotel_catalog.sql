CREATE SCHEMA IF NOT EXISTS hotel_catalog;

CREATE TABLE IF NOT EXISTS hotel_catalog.hotels (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(160) NOT NULL,
    city varchar(100) NOT NULL,
    district varchar(100) NOT NULL,
    address varchar(240) NOT NULL,
    country varchar(100) NOT NULL,
    summary varchar(600) NOT NULL,
    image_path varchar(240) NOT NULL,
    image_alt varchar(240) NOT NULL,
    rating numeric(3, 1) NOT NULL CHECK (rating BETWEEN 0 AND 10),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS hotel_catalog.room_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    hotel_id uuid NOT NULL REFERENCES hotel_catalog.hotels(id),
    name varchar(120) NOT NULL,
    details varchar(240) NOT NULL,
    max_guests integer NOT NULL CHECK (max_guests > 0),
    total_inventory integer NOT NULL CHECK (total_inventory > 0),
    active boolean NOT NULL DEFAULT true
);

WITH hotel_seed(seed_key, id, name, city, district, address, country, summary, image_path, image_alt, rating) AS (
    VALUES
        (1, '11111111-1111-1111-1111-111111111111'::uuid, 'Four Seasons Hotel Ritz Lisbon', 'Lisbon', 'Avenidas Novas', 'Rua Rodrigo da Fonseca 88', 'Portugal', 'A landmark city address near Parque Eduardo VII, with broad views over the rooftops and avenues.', '/images/four-seasons-ritz-lisboa.webp', 'Four Seasons Hotel Ritz Lisbon above the surrounding garden trees.', 9.5),
        (2, '22222222-2222-2222-2222-222222222222'::uuid, 'Pestana Palace Lisboa', 'Lisbon', 'Alcântara', 'Rua Jau 54', 'Portugal', 'A restored palace setting in Alcântara, with gardens and a quieter pace than the central avenues.', '/images/pestana-palace-lisboa.webp', 'The pale green historic facade of Pestana Palace Lisboa.', 9.2)
), saved_hotels AS (
    INSERT INTO hotel_catalog.hotels (id, name, city, district, address, country, summary, image_path, image_alt, rating)
    SELECT id, name, city, district, address, country, summary, image_path, image_alt, rating
    FROM hotel_seed
    ON CONFLICT (id) DO NOTHING
    RETURNING id
), room_seed(id, hotel_key, name, details, max_guests, total_inventory) AS (
    VALUES
        ('10000000-0000-0000-0000-000000000101'::uuid, 1, 'Deluxe King', 'King bed · 2 guests · 35 m²', 2, 12),
        ('10000000-0000-0000-0000-000000000102'::uuid, 1, 'Executive Suite', 'King bed · 2 guests · 50 m²', 2, 8),
        ('20000000-0000-0000-0000-000000000201'::uuid, 2, 'Palace Deluxe', 'Queen bed · 2 guests · 30 m²', 2, 10),
        ('20000000-0000-0000-0000-000000000202'::uuid, 2, 'Garden Suite', 'King bed · 2 guests · 46 m²', 2, 6)
)
INSERT INTO hotel_catalog.room_types (id, hotel_id, name, details, max_guests, total_inventory)
SELECT room_seed.id, hotel_seed.id, room_seed.name, room_seed.details, room_seed.max_guests, room_seed.total_inventory
FROM room_seed
JOIN hotel_seed ON hotel_seed.seed_key = room_seed.hotel_key
ON CONFLICT (id) DO NOTHING;
