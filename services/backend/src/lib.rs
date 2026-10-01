mod errors;
mod hotel;
mod models;
mod payments;
mod rates;
mod reservations;

use axum::{
    Router,
    response::IntoResponse,
    routing::{get, post, put},
};
use sqlx::PgPool;
use subtle::ConstantTimeEq;

pub use errors::ApiError;
pub use models::*;

#[derive(Clone)]
pub struct AppState {
    pub(crate) pool: PgPool,
    pub(crate) admin_key: String,
}

impl AppState {
    pub fn new(pool: PgPool, admin_key: impl Into<String>) -> Self {
        Self {
            pool,
            admin_key: admin_key.into(),
        }
    }
}

pub fn app(state: AppState) -> Router {
    let admin = Router::new()
        .route("/api/admin/hotels", post(hotel::create_hotel))
        .route(
            "/api/admin/hotels/{id}",
            put(hotel::update_hotel).delete(hotel::remove_hotel),
        )
        .route(
            "/api/admin/hotels/{hotel_id}/room-types",
            post(hotel::create_room_type),
        )
        .route(
            "/api/admin/room-types/{id}",
            put(hotel::update_room_type).delete(hotel::remove_room_type),
        )
        .route("/api/admin/rates", put(rates::set_rate))
        .route("/api/admin/rates/schedule", post(rates::create_schedule))
        .route("/api/admin/inventory", put(reservations::change_inventory))
        .route_layer(axum::middleware::from_fn_with_state(
            state.clone(),
            admin_auth,
        ));

    Router::new()
        .route("/health", get(api::health))
        .route("/api/hotels", get(hotel::find_hotels))
        .route("/api/hotels/{id}", get(hotel::find_hotel))
        .route("/api/rates/quote", get(rates::quote))
        .route("/api/payments", post(payments::charge))
        .route(
            "/api/payments/{reservation_id}/refund",
            put(payments::refund),
        )
        .route("/api/search", get(reservations::search))
        .route(
            "/api/reservations",
            get(reservations::history).post(reservations::book),
        )
        .route(
            "/api/reservations/{id}",
            get(reservations::find).delete(reservations::cancel),
        )
        .merge(admin)
        .with_state(state)
}

async fn admin_auth(
    axum::extract::State(state): axum::extract::State<AppState>,
    request: axum::extract::Request,
    next: axum::middleware::Next,
) -> axum::response::Response {
    use axum::http::header::HeaderName;
    let header = HeaderName::from_static("x-admin-key");
    let supplied = request
        .headers()
        .get(header)
        .and_then(|value| value.to_str().ok());
    let expected = state.admin_key.as_bytes();
    let valid = supplied.is_some_and(|value| {
        let bytes = value.as_bytes();
        expected.len() == bytes.len() && bool::from(expected.ct_eq(bytes))
    });
    if !valid {
        return ApiError::unauthorized("Staff key required.").into_response();
    }
    next.run(request).await
}

pub async fn initialize(pool: &PgPool) -> Result<(), sqlx::Error> {
    for schema in [
        include_str!("../schema/01_hotel_catalog.sql"),
        include_str!("../schema/02_rates.sql"),
        include_str!("../schema/03_payments.sql"),
        include_str!("../schema/04_reservations.sql"),
    ] {
        sqlx::raw_sql(schema).execute(pool).await?;
    }
    Ok(())
}

mod api {
    use axum::Json;
    use serde::Serialize;

    #[derive(Serialize)]
    pub(crate) struct Health {
        status: &'static str,
    }

    pub(crate) async fn health() -> Json<Health> {
        Json(Health { status: "UP" })
    }
}
