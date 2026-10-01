CREATE SCHEMA IF NOT EXISTS payments;

CREATE TABLE IF NOT EXISTS payments.transactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id uuid NOT NULL UNIQUE,
    amount numeric(12, 2) NOT NULL CHECK (amount > 0),
    guest_email varchar(254) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('PAID', 'REFUNDED')),
    created_at timestamptz NOT NULL DEFAULT now()
);
