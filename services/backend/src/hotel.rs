use axum::{
    Json,
    extract::{Path, Query, State},
    http::{HeaderMap, HeaderValue, StatusCode},
    response::IntoResponse,
};
use rust_decimal::Decimal;
use serde::Deserialize;
use sqlx::{FromRow, PgPool};
use uuid::Uuid;

use crate::{ApiError, AppState, Hotel, HotelDraft, RoomType, RoomTypeDraft};

#[derive(FromRow)]
struct HotelRow {
    id: Uuid,
    name: String,
    city: String,
    district: String,
    address: String,
    country: String,
    summary: String,
    image_path: String,
    image_alt: String,
    rating: Decimal,
}

#[derive(FromRow)]
struct RoomRow {
    id: Uuid,
    hotel_id: Uuid,
    name: String,
    details: String,
    max_guests: i32,
    total_inventory: i32,
}

#[derive(Deserialize)]
pub(crate) struct HotelFilter {
    destination: Option<String>,
}

pub(crate) async fn find_hotels(
    State(state): State<AppState>,
    Query(filter): Query<HotelFilter>,
) -> Result<Json<Vec<Hotel>>, ApiError> {
    let hotels = find_hotels_in(&state.pool, filter.destination.as_deref()).await?;
    Ok(Json(hotels))
}

pub(crate) async fn find_hotel(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<Hotel>, ApiError> {
    Ok(Json(require_hotel(&state.pool, id).await?))
}

pub(crate) async fn create_hotel(
    State(state): State<AppState>,
    Json(draft): Json<HotelDraft>,
) -> Result<impl IntoResponse, ApiError> {
    validate_hotel_draft(&draft)?;
    let id: Uuid = sqlx::query_scalar(
        "INSERT INTO hotel_catalog.hotels (name, city, district, address, country, summary, image_path, image_alt, rating) \
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING id"
    )
    .bind(clean(&draft.name)).bind(clean(&draft.city)).bind(clean(&draft.district))
    .bind(clean(&draft.address)).bind(clean(&draft.country)).bind(clean(&draft.summary))
    .bind(clean(&draft.image_path)).bind(clean(&draft.image_alt)).bind(draft.rating)
    .fetch_one(&state.pool).await?;
    let hotel = require_hotel(&state.pool, id).await?;
    let location =
        HeaderValue::from_str(&format!("/api/hotels/{id}")).map_err(|_| ApiError::internal())?;
    let mut headers = HeaderMap::new();
    headers.insert("location", location);
    Ok((StatusCode::CREATED, headers, Json(hotel)))
}

pub(crate) async fn update_hotel(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Json(draft): Json<HotelDraft>,
) -> Result<Json<Hotel>, ApiError> {
    validate_hotel_draft(&draft)?;
    let changed = sqlx::query(
        "UPDATE hotel_catalog.hotels SET name=$1, city=$2, district=$3, address=$4, country=$5, summary=$6, \
         image_path=$7, image_alt=$8, rating=$9 WHERE id=$10 AND active=true"
    )
    .bind(clean(&draft.name)).bind(clean(&draft.city)).bind(clean(&draft.district))
    .bind(clean(&draft.address)).bind(clean(&draft.country)).bind(clean(&draft.summary))
    .bind(clean(&draft.image_path)).bind(clean(&draft.image_alt)).bind(draft.rating).bind(id)
    .execute(&state.pool).await?.rows_affected();
    require_changed(changed, "hotel_not_found", "Hotel not found.")?;
    Ok(Json(require_hotel(&state.pool, id).await?))
}

pub(crate) async fn remove_hotel(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    let changed =
        sqlx::query("UPDATE hotel_catalog.hotels SET active=false WHERE id=$1 AND active=true")
            .bind(id)
            .execute(&state.pool)
            .await?
            .rows_affected();
    require_changed(changed, "hotel_not_found", "Hotel not found.")?;
    Ok(StatusCode::NO_CONTENT)
}

pub(crate) async fn create_room_type(
    State(state): State<AppState>,
    Path(hotel_id): Path<Uuid>,
    Json(draft): Json<RoomTypeDraft>,
) -> Result<impl IntoResponse, ApiError> {
    validate_room_draft(&draft)?;
    ensure_hotel_exists(&state.pool, hotel_id).await?;
    let room = insert_room_type(&state.pool, hotel_id, &draft).await?;
    let mut headers = HeaderMap::new();
    headers.insert(
        "location",
        HeaderValue::from_str(&format!("/api/hotels/{hotel_id}"))
            .map_err(|_| ApiError::internal())?,
    );
    Ok((StatusCode::CREATED, headers, Json(room)))
}

pub(crate) async fn update_room_type(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Json(draft): Json<RoomTypeDraft>,
) -> Result<Json<RoomType>, ApiError> {
    validate_room_draft(&draft)?;
    let changed = sqlx::query(
        "UPDATE hotel_catalog.room_types SET name=$1, details=$2, max_guests=$3, total_inventory=$4 \
         WHERE id=$5 AND active=true"
    )
    .bind(clean(&draft.name)).bind(clean(&draft.details)).bind(draft.max_guests)
    .bind(draft.total_inventory).bind(id).execute(&state.pool).await?.rows_affected();
    require_changed(changed, "room_type_not_found", "Room type not found.")?;
    Ok(Json(require_room_type(&state.pool, id).await?))
}

pub(crate) async fn remove_room_type(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    let changed =
        sqlx::query("UPDATE hotel_catalog.room_types SET active=false WHERE id=$1 AND active=true")
            .bind(id)
            .execute(&state.pool)
            .await?
            .rows_affected();
    require_changed(changed, "room_type_not_found", "Room type not found.")?;
    Ok(StatusCode::NO_CONTENT)
}

pub(crate) async fn find_hotels_in(
    pool: &PgPool,
    destination: Option<&str>,
) -> Result<Vec<Hotel>, ApiError> {
    let filter = format!("%{}%", destination.unwrap_or_default().trim());
    let rows = sqlx::query_as::<_, HotelRow>(
        "SELECT id,name,city,district,address,country,summary,image_path,image_alt,rating \
         FROM hotel_catalog.hotels WHERE active=true AND (lower(name) LIKE lower($1) OR lower(city) LIKE lower($1)) \
         ORDER BY name"
    ).bind(filter).fetch_all(pool).await?;
    let mut hotels = Vec::with_capacity(rows.len());
    for row in rows {
        let room_types = list_room_types(pool, row.id).await?;
        hotels.push(hotel_from_row(row, room_types));
    }
    Ok(hotels)
}

pub(crate) async fn require_hotel(pool: &PgPool, id: Uuid) -> Result<Hotel, ApiError> {
    let row = sqlx::query_as::<_, HotelRow>(
        "SELECT id,name,city,district,address,country,summary,image_path,image_alt,rating \
         FROM hotel_catalog.hotels WHERE id=$1 AND active=true",
    )
    .bind(id)
    .fetch_optional(pool)
    .await?
    .ok_or_else(|| ApiError::not_found("hotel_not_found", "Hotel not found."))?;
    let room_types = list_room_types(pool, id).await?;
    Ok(hotel_from_row(row, room_types))
}

pub(crate) async fn list_room_types(
    pool: &PgPool,
    hotel_id: Uuid,
) -> Result<Vec<RoomType>, ApiError> {
    let rows = sqlx::query_as::<_, RoomRow>(
        "SELECT id,hotel_id,name,details,max_guests,total_inventory FROM hotel_catalog.room_types \
         WHERE hotel_id=$1 AND active=true ORDER BY name",
    )
    .bind(hotel_id)
    .fetch_all(pool)
    .await?;
    Ok(rows.into_iter().map(room_from_row).collect())
}

pub(crate) async fn require_room_type(pool: &PgPool, id: Uuid) -> Result<RoomType, ApiError> {
    let row = sqlx::query_as::<_, RoomRow>(
        "SELECT id,hotel_id,name,details,max_guests,total_inventory FROM hotel_catalog.room_types \
         WHERE id=$1 AND active=true",
    )
    .bind(id)
    .fetch_optional(pool)
    .await?
    .ok_or_else(|| ApiError::not_found("room_type_not_found", "Room type not found."))?;
    Ok(room_from_row(row))
}

pub(crate) async fn ensure_hotel_exists(pool: &PgPool, hotel_id: Uuid) -> Result<(), ApiError> {
    let exists: bool = sqlx::query_scalar(
        "SELECT EXISTS(SELECT 1 FROM hotel_catalog.hotels WHERE id=$1 AND active=true)",
    )
    .bind(hotel_id)
    .fetch_one(pool)
    .await?;
    if !exists {
        return Err(ApiError::not_found("hotel_not_found", "Hotel not found."));
    }
    Ok(())
}

pub(crate) async fn insert_room_type(
    pool: &PgPool,
    hotel_id: Uuid,
    draft: &RoomTypeDraft,
) -> Result<RoomType, ApiError> {
    let id: Uuid = sqlx::query_scalar(
        "INSERT INTO hotel_catalog.room_types (hotel_id,name,details,max_guests,total_inventory) \
         VALUES ($1,$2,$3,$4,$5) RETURNING id",
    )
    .bind(hotel_id)
    .bind(clean(&draft.name))
    .bind(clean(&draft.details))
    .bind(draft.max_guests)
    .bind(draft.total_inventory)
    .fetch_one(pool)
    .await?;
    require_room_type(pool, id).await
}

fn hotel_from_row(row: HotelRow, room_types: Vec<RoomType>) -> Hotel {
    Hotel {
        id: row.id,
        name: row.name,
        city: row.city,
        district: row.district,
        address: row.address,
        country: row.country,
        summary: row.summary,
        image_path: row.image_path,
        image_alt: row.image_alt,
        rating: row.rating,
        room_types,
    }
}

fn room_from_row(row: RoomRow) -> RoomType {
    RoomType {
        id: row.id,
        hotel_id: row.hotel_id,
        name: row.name,
        details: row.details,
        max_guests: row.max_guests,
        total_inventory: row.total_inventory,
    }
}

fn validate_hotel_draft(draft: &HotelDraft) -> Result<(), ApiError> {
    for (name, value) in [
        ("name", &draft.name),
        ("city", &draft.city),
        ("district", &draft.district),
        ("address", &draft.address),
        ("country", &draft.country),
        ("summary", &draft.summary),
        ("imagePath", &draft.image_path),
        ("imageAlt", &draft.image_alt),
    ] {
        if value.trim().is_empty() {
            return Err(invalid_field(name));
        }
    }
    if draft.rating < Decimal::ZERO || draft.rating > Decimal::TEN {
        return Err(invalid_field("rating"));
    }
    Ok(())
}

fn validate_room_draft(draft: &RoomTypeDraft) -> Result<(), ApiError> {
    if draft.name.trim().is_empty() {
        return Err(invalid_field("name"));
    }
    if draft.details.trim().is_empty() {
        return Err(invalid_field("details"));
    }
    if draft.max_guests < 1 {
        return Err(invalid_field("maxGuests"));
    }
    if draft.total_inventory < 1 {
        return Err(invalid_field("totalInventory"));
    }
    Ok(())
}

fn invalid_field(_field: &str) -> ApiError {
    ApiError::bad_request("invalid_request", "Check the request values and try again.")
}

fn clean(value: &str) -> String {
    value.trim().to_owned()
}

fn require_changed(
    changed: u64,
    code: &'static str,
    message: &'static str,
) -> Result<(), ApiError> {
    if changed == 0 {
        return Err(ApiError::not_found(code, message));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn hotel_response_keeps_camel_case_api_fields_and_numeric_rating() {
        let hotel = Hotel {
            id: Uuid::nil(),
            name: "Stay".into(),
            city: "Lisbon".into(),
            district: "Center".into(),
            address: "1 Main Street".into(),
            country: "Portugal".into(),
            summary: "A stay".into(),
            image_path: "/image.webp".into(),
            image_alt: "Building".into(),
            rating: Decimal::new(95, 1),
            room_types: vec![],
        };
        let value = serde_json::to_value(hotel).expect("hotel serializes");
        assert_eq!(value["imagePath"], "/image.webp");
        assert_eq!(value["rating"], json!(9.5));
        assert!(value.get("roomTypes").is_some());
    }

    #[test]
    fn drafts_require_nonblank_fields_positive_counts_and_valid_ratings() {
        let mut hotel = HotelDraft {
            name: "Name".into(),
            city: "Lisbon".into(),
            district: "D".into(),
            address: "A".into(),
            country: "PT".into(),
            summary: "S".into(),
            image_path: "/x".into(),
            image_alt: "X".into(),
            rating: Decimal::TEN,
        };
        assert!(validate_hotel_draft(&hotel).is_ok());
        hotel.rating = Decimal::new(101, 1);
        assert!(validate_hotel_draft(&hotel).is_err());
        let mut room = RoomTypeDraft {
            name: "Room".into(),
            details: "Details".into(),
            max_guests: 2,
            total_inventory: 1,
        };
        assert!(validate_room_draft(&room).is_ok());
        room.total_inventory = 0;
        assert!(validate_room_draft(&room).is_err());
    }

    #[test]
    fn update_helpers_treat_zero_rows_as_missing() {
        assert!(require_changed(1, "missing", "missing").is_ok());
        assert_eq!(
            require_changed(0, "missing", "missing")
                .err()
                .map(|e| e.code),
            Some("missing")
        );
        assert_eq!(clean("  Hotel  "), "Hotel");
    }
}
