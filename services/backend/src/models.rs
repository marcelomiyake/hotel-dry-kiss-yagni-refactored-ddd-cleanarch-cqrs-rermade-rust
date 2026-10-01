use chrono::{DateTime, NaiveDate, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use uuid::Uuid;

mod decimal_number {
    use super::{Decimal, Deserializer, Serializer};
    use rust_decimal::prelude::{FromPrimitive, ToPrimitive};
    use serde::Deserialize;

    pub(super) fn serialize<S>(value: &Decimal, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_f64(value.to_f64().unwrap_or_default())
    }

    pub(super) fn deserialize<'de, D>(deserializer: D) -> Result<Decimal, D::Error>
    where
        D: Deserializer<'de>,
    {
        let value = f64::deserialize(deserializer)?;
        Decimal::from_f64(value)
            .ok_or_else(|| serde::de::Error::custom("amount must be a finite number"))
    }
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Hotel {
    pub id: Uuid,
    pub name: String,
    pub city: String,
    pub district: String,
    pub address: String,
    pub country: String,
    pub summary: String,
    pub image_path: String,
    pub image_alt: String,
    #[serde(with = "decimal_number")]
    pub rating: Decimal,
    pub room_types: Vec<RoomType>,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct RoomType {
    pub id: Uuid,
    pub hotel_id: Uuid,
    pub name: String,
    pub details: String,
    pub max_guests: i32,
    pub total_inventory: i32,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HotelDraft {
    pub name: String,
    pub city: String,
    pub district: String,
    pub address: String,
    pub country: String,
    pub summary: String,
    pub image_path: String,
    pub image_alt: String,
    #[serde(with = "decimal_number")]
    pub rating: Decimal,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RoomTypeDraft {
    pub name: String,
    pub details: String,
    pub max_guests: i32,
    pub total_inventory: i32,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct NightlyRate {
    pub date: NaiveDate,
    #[serde(with = "decimal_number")]
    pub amount: Decimal,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct RateQuote {
    pub room_type_id: Uuid,
    pub nights: Vec<NightlyRate>,
    #[serde(with = "decimal_number")]
    pub total: Decimal,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RateDraft {
    pub room_type_id: Uuid,
    pub date: NaiveDate,
    #[serde(with = "decimal_number")]
    pub amount: Decimal,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RateSchedule {
    pub room_type_id: Uuid,
    #[serde(with = "decimal_number")]
    pub base_rate: Decimal,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Payment {
    pub id: Uuid,
    pub reservation_id: Uuid,
    #[serde(with = "decimal_number")]
    pub amount: Decimal,
    pub status: PaymentStatus,
    pub created_at: DateTime<Utc>,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum PaymentStatus {
    Paid,
    Refunded,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PaymentRequest {
    pub reservation_id: Uuid,
    #[serde(with = "decimal_number")]
    pub amount: Decimal,
    pub guest_email: String,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ReservationStatus {
    PaymentPending,
    Confirmed,
    Cancelled,
    PaymentFailed,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Reservation {
    pub id: Uuid,
    pub hotel_id: Uuid,
    pub room_type_id: Uuid,
    pub hotel_name: String,
    pub city: String,
    pub district: String,
    pub image_path: String,
    pub image_alt: String,
    pub room_type_name: String,
    pub check_in: NaiveDate,
    pub check_out: NaiveDate,
    pub rooms: i32,
    pub guests: i32,
    pub guest_name: String,
    pub guest_email: String,
    #[serde(with = "decimal_number")]
    pub total: Decimal,
    pub status: ReservationStatus,
    pub payment_id: Option<Uuid>,
    pub created_at: DateTime<Utc>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReservationRequest {
    pub reservation_id: Uuid,
    pub hotel_id: Uuid,
    pub room_type_id: Uuid,
    pub check_in: NaiveDate,
    pub check_out: NaiveDate,
    pub rooms: i32,
    pub guests: i32,
    pub guest_name: String,
    pub guest_email: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InventoryDraft {
    pub hotel_id: Uuid,
    pub room_type_id: Uuid,
    pub total_inventory: i32,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct SearchResponse {
    pub stays: Vec<SearchStay>,
    pub check_in: NaiveDate,
    pub check_out: NaiveDate,
    pub guests: i32,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct SearchStay {
    pub id: Uuid,
    pub name: String,
    pub city: String,
    pub district: String,
    pub address: String,
    pub summary: String,
    pub image_path: String,
    pub image_alt: String,
    #[serde(with = "decimal_number")]
    pub rating: Decimal,
    pub rooms: Vec<RoomOffer>,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct RoomOffer {
    pub id: Uuid,
    pub name: String,
    pub details: String,
    pub max_guests: i32,
    pub available_rooms: i32,
    pub nightly_rates: Vec<NightlyRate>,
    #[serde(with = "decimal_number")]
    pub total_price: Decimal,
}
