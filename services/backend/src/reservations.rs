use axum::{
    Json,
    extract::{Path, Query, State},
    http::StatusCode,
};
use chrono::{NaiveDate, Utc};
use rust_decimal::Decimal;
use serde::Deserialize;
use sqlx::{FromRow, PgPool, Postgres, Transaction};
use uuid::Uuid;

use crate::{
    ApiError, AppState, Hotel, InventoryDraft, PaymentRequest, Reservation, ReservationRequest,
    ReservationStatus, RoomOffer, SearchResponse, SearchStay, hotel, payments, rates,
};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SearchQuery {
    destination: String,
    check_in: NaiveDate,
    check_out: NaiveDate,
    #[serde(default = "default_guests")]
    guests: i32,
}

#[derive(FromRow)]
struct ReservationRow {
    id: Uuid,
    hotel_id: Uuid,
    room_type_id: Uuid,
    hotel_name: String,
    city: String,
    district: String,
    image_path: String,
    image_alt: String,
    room_type_name: String,
    check_in: NaiveDate,
    check_out: NaiveDate,
    rooms: i32,
    guests: i32,
    guest_name: String,
    guest_email: String,
    total: Decimal,
    status: String,
    payment_id: Option<Uuid>,
    created_at: chrono::DateTime<Utc>,
}

#[derive(FromRow)]
struct InventoryRow {
    inventory_date: NaiveDate,
    total_inventory: i32,
    total_reserved: i32,
    version: i64,
}

pub(crate) async fn search(
    State(state): State<AppState>,
    Query(query): Query<SearchQuery>,
) -> Result<Json<SearchResponse>, ApiError> {
    Ok(Json(search_stays(&state.pool, query).await?))
}

pub(crate) async fn history(
    State(state): State<AppState>,
    Query(query): Query<EmailQuery>,
) -> Result<Json<Vec<Reservation>>, ApiError> {
    let reservations = sqlx::query_as::<_, ReservationRow>(
        "SELECT id,hotel_id,room_type_id,hotel_name,city,district,image_path,image_alt,room_type_name,check_in,check_out, \
         rooms,guests,guest_name,guest_email,total,status,payment_id,created_at FROM reservations.bookings \
         WHERE lower(guest_email)=lower($1) ORDER BY created_at DESC"
    ).bind(query.email.trim()).fetch_all(&state.pool).await?;
    reservations
        .into_iter()
        .map(reservation_from_row)
        .collect::<Result<Vec<_>, _>>()
        .map(Json)
}

#[derive(Deserialize)]
pub(crate) struct EmailQuery {
    email: String,
}

pub(crate) async fn find(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<Reservation>, ApiError> {
    Ok(Json(find_reservation(&state.pool, id).await?))
}

pub(crate) async fn book(
    State(state): State<AppState>,
    Json(request): Json<ReservationRequest>,
) -> Result<Json<Reservation>, ApiError> {
    Ok(Json(book_reservation(&state.pool, &request).await?))
}

pub(crate) async fn cancel(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<Reservation>, ApiError> {
    let reservation = find_reservation(&state.pool, id).await?;
    if reservation.status == ReservationStatus::Cancelled {
        return Ok(Json(reservation));
    }
    if reservation.status == ReservationStatus::Confirmed {
        payments::refund_reservation(&state.pool, id).await?;
    }
    let mut transaction = state.pool.begin().await?;
    release_inventory(
        &mut transaction,
        reservation.hotel_id,
        reservation.room_type_id,
        reservation.check_in,
        reservation.check_out,
        reservation.rooms,
    )
    .await?;
    sqlx::query(
        "UPDATE reservations.bookings SET status='CANCELLED' WHERE id=$1 AND status<>'CANCELLED'",
    )
    .bind(id)
    .execute(&mut *transaction)
    .await?;
    transaction.commit().await?;
    Ok(Json(find_reservation(&state.pool, id).await?))
}

pub(crate) async fn change_inventory(
    State(state): State<AppState>,
    Json(draft): Json<InventoryDraft>,
) -> Result<StatusCode, ApiError> {
    if draft.total_inventory < 1 {
        return Err(invalid_request());
    }
    let mut transaction = state.pool.begin().await?;
    let count: i64 = sqlx::query_scalar(
        "SELECT count(*) FROM reservations.room_inventory WHERE room_type_id=$1 AND inventory_date>=current_date"
    ).bind(draft.room_type_id).fetch_one(&mut *transaction).await?;
    if count == 0 {
        ensure_inventory_rows_in(
            &mut transaction,
            draft.hotel_id,
            draft.room_type_id,
            draft.total_inventory,
        )
        .await?;
    } else {
        let changed = sqlx::query(
            "UPDATE reservations.room_inventory SET total_inventory=$1,version=version+1 \
             WHERE hotel_id=$2 AND room_type_id=$3 AND inventory_date>=current_date \
             AND total_reserved<=floor($1 * 1.10)",
        )
        .bind(draft.total_inventory)
        .bind(draft.hotel_id)
        .bind(draft.room_type_id)
        .execute(&mut *transaction)
        .await?
        .rows_affected();
        if changed != count as u64 {
            return Err(ApiError::conflict(
                "inventory_below_reservations",
                "Inventory cannot be lower than existing reservations.",
            ));
        }
    }
    transaction.commit().await?;
    Ok(StatusCode::NO_CONTENT)
}

async fn search_stays(pool: &PgPool, query: SearchQuery) -> Result<SearchResponse, ApiError> {
    validate_search(&query)?;
    let hotels = hotel::find_hotels_in(pool, Some(&query.destination)).await?;
    let mut stays = Vec::new();
    for item in hotels {
        let mut rooms = Vec::new();
        for room in item
            .room_types
            .iter()
            .filter(|room| room.max_guests >= query.guests)
        {
            ensure_inventory_rows(pool, item.id, room.id, room.total_inventory).await?;
            let quote = rates::quote_rates(pool, room.id, query.check_in, query.check_out).await?;
            let available =
                inventory_available(pool, item.id, room.id, query.check_in, query.check_out)
                    .await?;
            if available > 0 {
                rooms.push(RoomOffer {
                    id: room.id,
                    name: room.name.clone(),
                    details: room.details.clone(),
                    max_guests: room.max_guests,
                    available_rooms: available,
                    nightly_rates: quote.nights,
                    total_price: quote.total,
                });
            }
        }
        if !rooms.is_empty() {
            stays.push(search_stay(item, rooms));
        }
    }
    Ok(SearchResponse {
        stays,
        check_in: query.check_in,
        check_out: query.check_out,
        guests: query.guests,
    })
}

fn search_stay(item: Hotel, rooms: Vec<RoomOffer>) -> SearchStay {
    SearchStay {
        id: item.id,
        name: item.name,
        city: item.city,
        district: item.district,
        address: item.address,
        summary: item.summary,
        image_path: item.image_path,
        image_alt: item.image_alt,
        rating: item.rating,
        rooms,
    }
}

pub(crate) async fn book_reservation(
    pool: &PgPool,
    request: &ReservationRequest,
) -> Result<Reservation, ApiError> {
    validate_reservation_request(request)?;
    let hotel = hotel::require_hotel(pool, request.hotel_id).await?;
    let room = hotel
        .room_types
        .iter()
        .find(|room| room.id == request.room_type_id)
        .ok_or_else(|| ApiError::not_found("room_type_not_found", "Room type not found."))?;
    let quote = rates::quote_rates(pool, room.id, request.check_in, request.check_out).await?;
    let total = quote.total * Decimal::from(request.rooms);

    let mut reservation = match create_pending(pool, request, &hotel, room, total).await {
        Ok(reservation) => reservation,
        Err(error) if error.status == StatusCode::CONFLICT && error.code == "already_exists" => {
            find_reservation(pool, request.reservation_id).await?
        }
        Err(error) => return Err(error),
    };
    require_same_request(&reservation, request)?;
    if reservation.status == ReservationStatus::Confirmed {
        return Ok(reservation);
    }
    if reservation.status != ReservationStatus::PaymentPending {
        return Err(ApiError::conflict(
            "reservation_closed",
            "This reservation cannot be completed.",
        ));
    }

    let payment = payments::charge_for(
        pool,
        &PaymentRequest {
            reservation_id: request.reservation_id,
            amount: reservation.total,
            guest_email: request.guest_email.clone(),
        },
    )
    .await?;
    let _ = sqlx::query(
        "UPDATE reservations.bookings SET status='CONFIRMED',payment_id=$1 \
         WHERE id=$2 AND status='PAYMENT_PENDING'",
    )
    .bind(payment.id)
    .bind(request.reservation_id)
    .execute(pool)
    .await?;
    reservation = find_reservation(pool, request.reservation_id).await?;
    Ok(reservation)
}

async fn create_pending(
    pool: &PgPool,
    request: &ReservationRequest,
    hotel: &Hotel,
    room: &crate::RoomType,
    total: Decimal,
) -> Result<Reservation, ApiError> {
    if let Some(existing) = find_optional_reservation(pool, request.reservation_id).await? {
        require_same_request(&existing, request)?;
        return Ok(existing);
    }
    let mut transaction = pool.begin().await?;
    reserve_inventory(
        &mut transaction,
        request.hotel_id,
        request.room_type_id,
        request.check_in,
        request.check_out,
        request.rooms,
    )
    .await?;
    let insert = sqlx::query(
        "INSERT INTO reservations.bookings \
         (id,hotel_id,room_type_id,hotel_name,city,district,image_path,image_alt,room_type_name,check_in,check_out, \
          rooms,guests,guest_name,guest_email,total,status) \
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,'PAYMENT_PENDING')"
    ).bind(request.reservation_id).bind(request.hotel_id).bind(request.room_type_id)
        .bind(&hotel.name).bind(&hotel.city).bind(&hotel.district).bind(&hotel.image_path).bind(&hotel.image_alt)
        .bind(&room.name).bind(request.check_in).bind(request.check_out).bind(request.rooms).bind(request.guests)
        .bind(request.guest_name.trim()).bind(request.guest_email.trim().to_lowercase()).bind(total)
        .execute(&mut *transaction).await;
    match insert {
        Ok(_) => {
            transaction.commit().await?;
        }
        Err(sqlx::Error::Database(database_error))
            if database_error.code().as_deref() == Some("23505") =>
        {
            transaction.rollback().await?;
            return Err(ApiError::conflict(
                "already_exists",
                "Reservation already exists.",
            ));
        }
        Err(error) => return Err(error.into()),
    }
    find_reservation(pool, request.reservation_id).await
}

async fn reserve_inventory(
    transaction: &mut Transaction<'_, Postgres>,
    hotel_id: Uuid,
    room_type_id: Uuid,
    check_in: NaiveDate,
    check_out: NaiveDate,
    count: i32,
) -> Result<(), ApiError> {
    let nights = rates::validate_period(check_in, check_out)?;
    let rows = inventory_rows(transaction, hotel_id, room_type_id, check_in, check_out).await?;
    if rows.len() as i64 != nights {
        return Err(not_available());
    }
    for row in rows {
        if row.available() < count {
            return Err(not_available());
        }
        let changed = sqlx::query(
            "UPDATE reservations.room_inventory SET total_reserved=total_reserved+$1,version=version+1 \
             WHERE hotel_id=$2 AND room_type_id=$3 AND inventory_date=$4 AND version=$5 \
             AND total_reserved+$1<=floor(total_inventory * 1.10)"
        ).bind(count).bind(hotel_id).bind(room_type_id).bind(row.inventory_date).bind(row.version)
            .execute(&mut **transaction).await?.rows_affected();
        if changed != 1 {
            return Err(ApiError::conflict(
                "inventory_changed",
                "Availability changed. Search again before booking.",
            ));
        }
    }
    Ok(())
}

async fn release_inventory(
    transaction: &mut Transaction<'_, Postgres>,
    hotel_id: Uuid,
    room_type_id: Uuid,
    check_in: NaiveDate,
    check_out: NaiveDate,
    count: i32,
) -> Result<(), ApiError> {
    sqlx::query(
        "UPDATE reservations.room_inventory SET total_reserved=total_reserved-$1,version=version+1 \
         WHERE hotel_id=$2 AND room_type_id=$3 AND inventory_date >= $4 AND inventory_date < $5 AND total_reserved >= $1"
    ).bind(count).bind(hotel_id).bind(room_type_id).bind(check_in).bind(check_out)
        .execute(&mut **transaction).await?;
    Ok(())
}

async fn inventory_rows(
    connection: &mut sqlx::PgConnection,
    hotel_id: Uuid,
    room_type_id: Uuid,
    check_in: NaiveDate,
    check_out: NaiveDate,
) -> Result<Vec<InventoryRow>, ApiError> {
    Ok(sqlx::query_as::<_, InventoryRow>(
        "SELECT inventory_date,total_inventory,total_reserved,version FROM reservations.room_inventory \
         WHERE hotel_id=$1 AND room_type_id=$2 AND inventory_date >= $3 AND inventory_date < $4 ORDER BY inventory_date"
    ).bind(hotel_id).bind(room_type_id).bind(check_in).bind(check_out).fetch_all(connection).await?)
}

async fn inventory_available(
    pool: &PgPool,
    hotel_id: Uuid,
    room_type_id: Uuid,
    check_in: NaiveDate,
    check_out: NaiveDate,
) -> Result<i32, ApiError> {
    let mut connection = pool.acquire().await?;
    let rows = inventory_rows(&mut connection, hotel_id, room_type_id, check_in, check_out).await?;
    if rows.len() as i64 != check_out.signed_duration_since(check_in).num_days() {
        return Ok(0);
    }
    Ok(rows.iter().map(InventoryRow::available).min().unwrap_or(0))
}

async fn ensure_inventory_rows(
    pool: &PgPool,
    hotel_id: Uuid,
    room_type_id: Uuid,
    total_inventory: i32,
) -> Result<(), ApiError> {
    let count: i64 = sqlx::query_scalar(
        "SELECT count(*) FROM reservations.room_inventory WHERE room_type_id=$1",
    )
    .bind(room_type_id)
    .fetch_one(pool)
    .await?;
    if count == 0 {
        let mut transaction = pool.begin().await?;
        ensure_inventory_rows_in(&mut transaction, hotel_id, room_type_id, total_inventory).await?;
        transaction.commit().await?;
    }
    Ok(())
}

async fn ensure_inventory_rows_in(
    transaction: &mut Transaction<'_, Postgres>,
    hotel_id: Uuid,
    room_type_id: Uuid,
    total_inventory: i32,
) -> Result<(), ApiError> {
    sqlx::query(
        "INSERT INTO reservations.room_inventory (hotel_id,room_type_id,inventory_date,total_inventory) \
         SELECT $1,$2,stay_date::date,$3 FROM generate_series(current_date,current_date+730,interval '1 day') AS stay_date \
         ON CONFLICT (hotel_id,room_type_id,inventory_date) DO NOTHING"
    ).bind(hotel_id).bind(room_type_id).bind(total_inventory).execute(&mut **transaction).await?;
    Ok(())
}

async fn find_optional_reservation(
    pool: &PgPool,
    id: Uuid,
) -> Result<Option<Reservation>, ApiError> {
    let row = sqlx::query_as::<_, ReservationRow>(
        "SELECT id,hotel_id,room_type_id,hotel_name,city,district,image_path,image_alt,room_type_name,check_in,check_out, \
         rooms,guests,guest_name,guest_email,total,status,payment_id,created_at FROM reservations.bookings WHERE id=$1"
    ).bind(id).fetch_optional(pool).await?;
    row.map(reservation_from_row).transpose()
}

pub(crate) async fn find_reservation(pool: &PgPool, id: Uuid) -> Result<Reservation, ApiError> {
    find_optional_reservation(pool, id)
        .await?
        .ok_or_else(|| ApiError::not_found("reservation_not_found", "Reservation not found."))
}

fn reservation_from_row(row: ReservationRow) -> Result<Reservation, ApiError> {
    let status = match row.status.as_str() {
        "PAYMENT_PENDING" => ReservationStatus::PaymentPending,
        "CONFIRMED" => ReservationStatus::Confirmed,
        "CANCELLED" => ReservationStatus::Cancelled,
        "PAYMENT_FAILED" => ReservationStatus::PaymentFailed,
        _ => return Err(ApiError::internal()),
    };
    Ok(Reservation {
        id: row.id,
        hotel_id: row.hotel_id,
        room_type_id: row.room_type_id,
        hotel_name: row.hotel_name,
        city: row.city,
        district: row.district,
        image_path: row.image_path,
        image_alt: row.image_alt,
        room_type_name: row.room_type_name,
        check_in: row.check_in,
        check_out: row.check_out,
        rooms: row.rooms,
        guests: row.guests,
        guest_name: row.guest_name,
        guest_email: row.guest_email,
        total: row.total,
        status,
        payment_id: row.payment_id,
        created_at: row.created_at,
    })
}

fn validate_search(query: &SearchQuery) -> Result<(), ApiError> {
    let today = Utc::now().date_naive();
    if query.destination.trim().is_empty() {
        return Err(ApiError::bad_request(
            "destination_required",
            "Enter a destination to search.",
        ));
    }
    if query.check_in < today
        || query.check_out <= query.check_in
        || query.check_out > rates::search_date_limit()
    {
        return Err(ApiError::bad_request(
            "invalid_dates",
            "Choose valid future dates within two years.",
        ));
    }
    if !(1..=4).contains(&query.guests) {
        return Err(ApiError::bad_request(
            "invalid_guest_count",
            "Choose between 1 and 4 guests.",
        ));
    }
    Ok(())
}

fn validate_reservation_request(request: &ReservationRequest) -> Result<(), ApiError> {
    if request.reservation_id.is_nil()
        || request.hotel_id.is_nil()
        || request.room_type_id.is_nil()
        || request.rooms < 1
        || request.guests < 1
        || request.guest_name.trim().is_empty()
        || !payments::email_is_valid(&request.guest_email)
    {
        return Err(invalid_request());
    }
    rates::validate_period(request.check_in, request.check_out)?;
    Ok(())
}

fn require_same_request(
    reservation: &Reservation,
    request: &ReservationRequest,
) -> Result<(), ApiError> {
    let same = reservation.hotel_id == request.hotel_id
        && reservation.room_type_id == request.room_type_id
        && reservation.check_in == request.check_in
        && reservation.check_out == request.check_out
        && reservation.rooms == request.rooms
        && reservation.guests == request.guests
        && reservation.guest_name == request.guest_name.trim()
        && reservation
            .guest_email
            .eq_ignore_ascii_case(request.guest_email.trim());
    if !same {
        return Err(ApiError::conflict(
            "idempotency_key_reused",
            "Use a new reservation ID for different booking details.",
        ));
    }
    Ok(())
}

fn default_guests() -> i32 {
    2
}
fn invalid_request() -> ApiError {
    ApiError::bad_request("invalid_request", "Check the request values and try again.")
}
fn not_available() -> ApiError {
    ApiError::conflict(
        "not_available",
        "The selected room is no longer available for every night.",
    )
}

impl InventoryRow {
    fn available(&self) -> i32 {
        ((self.total_inventory * 11) / 10 - self.total_reserved).max(0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::NightlyRate;

    fn request(id: Uuid) -> ReservationRequest {
        ReservationRequest {
            reservation_id: id,
            hotel_id: Uuid::new_v4(),
            room_type_id: Uuid::new_v4(),
            check_in: NaiveDate::from_ymd_opt(2026, 5, 1).unwrap(),
            check_out: NaiveDate::from_ymd_opt(2026, 5, 3).unwrap(),
            rooms: 1,
            guests: 2,
            guest_name: "Guest Name".into(),
            guest_email: "guest@example.com".into(),
        }
    }

    #[test]
    fn search_rejects_missing_destination_dates_and_guest_counts() {
        let today = Utc::now().date_naive();
        let valid = SearchQuery {
            destination: "Lisbon".into(),
            check_in: today + chrono::Days::new(2),
            check_out: today + chrono::Days::new(3),
            guests: 2,
        };
        assert!(validate_search(&valid).is_ok());
        let mut query = SearchQuery {
            destination: "".into(),
            ..valid
        };
        assert_eq!(
            validate_search(&query).err().map(|error| error.code),
            Some("destination_required")
        );
        query.destination = "Lisbon".into();
        query.guests = 5;
        assert_eq!(
            validate_search(&query).err().map(|error| error.code),
            Some("invalid_guest_count")
        );
        query.guests = 1;
        query.check_in = today - chrono::Days::new(1);
        assert_eq!(
            validate_search(&query).err().map(|error| error.code),
            Some("invalid_dates")
        );
    }

    #[test]
    fn reservation_requests_are_validated_and_idempotency_requires_matching_fields() {
        let id = Uuid::new_v4();
        let valid = request(id);
        assert!(validate_reservation_request(&valid).is_ok());
        assert!(
            require_same_request(
                &Reservation {
                    id,
                    hotel_id: valid.hotel_id,
                    room_type_id: valid.room_type_id,
                    hotel_name: "Hotel".into(),
                    city: "Lisbon".into(),
                    district: "Center".into(),
                    image_path: "/image.webp".into(),
                    image_alt: "Hotel".into(),
                    room_type_name: "Room".into(),
                    check_in: valid.check_in,
                    check_out: valid.check_out,
                    rooms: valid.rooms,
                    guests: valid.guests,
                    guest_name: valid.guest_name.clone(),
                    guest_email: valid.guest_email.to_lowercase(),
                    total: Decimal::ONE,
                    status: ReservationStatus::PaymentPending,
                    payment_id: None,
                    created_at: Utc::now(),
                },
                &valid
            )
            .is_ok()
        );
        let mut changed = valid.clone();
        changed.guests = 1;
        let reservation = Reservation {
            id,
            hotel_id: valid.hotel_id,
            room_type_id: valid.room_type_id,
            hotel_name: "Hotel".into(),
            city: "Lisbon".into(),
            district: "Center".into(),
            image_path: "/image.webp".into(),
            image_alt: "Hotel".into(),
            room_type_name: "Room".into(),
            check_in: valid.check_in,
            check_out: valid.check_out,
            rooms: valid.rooms,
            guests: valid.guests,
            guest_name: valid.guest_name,
            guest_email: valid.guest_email,
            total: Decimal::ONE,
            status: ReservationStatus::PaymentPending,
            payment_id: None,
            created_at: Utc::now(),
        };
        assert_eq!(
            require_same_request(&reservation, &changed)
                .err()
                .map(|error| error.code),
            Some("idempotency_key_reused")
        );
    }

    #[test]
    fn overbooking_calculates_capacity_with_integer_floor() {
        let row = InventoryRow {
            inventory_date: NaiveDate::from_ymd_opt(2026, 5, 1).unwrap(),
            total_inventory: 12,
            total_reserved: 3,
            version: 0,
        };
        assert_eq!(row.available(), 10);
        let small = InventoryRow {
            total_inventory: 1,
            total_reserved: 0,
            ..row
        };
        assert_eq!(small.available(), 1);
    }

    #[test]
    fn search_response_keeps_the_contract_names() {
        let item = Hotel {
            id: Uuid::nil(),
            name: "Stay".into(),
            city: "Lisbon".into(),
            district: "Center".into(),
            address: "1".into(),
            country: "PT".into(),
            summary: "S".into(),
            image_path: "/x".into(),
            image_alt: "X".into(),
            rating: Decimal::new(90, 1),
            room_types: vec![],
        };
        let stay = search_stay(
            item,
            vec![RoomOffer {
                id: Uuid::nil(),
                name: "Room".into(),
                details: "Details".into(),
                max_guests: 2,
                available_rooms: 3,
                nightly_rates: vec![NightlyRate {
                    date: NaiveDate::from_ymd_opt(2026, 5, 1).unwrap(),
                    amount: Decimal::ONE,
                }],
                total_price: Decimal::ONE,
            }],
        );
        let value = serde_json::to_value(stay).unwrap();
        assert_eq!(value["imagePath"], "/x");
        assert_eq!(value["rooms"][0]["availableRooms"], 3);
        assert_eq!(value["rooms"][0]["totalPrice"], 1.0);
    }
}
