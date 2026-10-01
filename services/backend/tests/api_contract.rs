use axum::{
    Router,
    body::{Body, to_bytes},
    http::{Method, Request, StatusCode},
};
use chrono::Utc;
use serde_json::{Value, json};
use sqlx::postgres::PgPoolOptions;
use stays_backend::{AppState, app, initialize};
use std::env;
use tower::ServiceExt;
use uuid::Uuid;

const ADMIN_KEY: &str = "integration-test-staff-key";

async fn call(
    app: &Router,
    method: Method,
    uri: &str,
    body: Option<Value>,
    key: Option<&str>,
) -> (StatusCode, Value) {
    let mut request = Request::builder().method(method).uri(uri);
    if body.is_some() {
        request = request.header("content-type", "application/json");
    }
    if let Some(key) = key {
        request = request.header("x-admin-key", key);
    }
    let payload = body.map_or_else(Body::empty, |value| Body::from(value.to_string()));
    let response = app
        .clone()
        .oneshot(request.body(payload).expect("request builds"))
        .await
        .expect("router responds");
    let status = response.status();
    let bytes = to_bytes(response.into_body(), usize::MAX)
        .await
        .expect("response body reads");
    let value = serde_json::from_slice(&bytes)
        .unwrap_or_else(|_| json!({"raw": String::from_utf8_lossy(&bytes)}));
    (status, value)
}

#[tokio::test]
async fn preserves_routes_json_fields_booking_rules_and_admin_security() {
    let database_url =
        env::var("DATABASE_URL").expect("DATABASE_URL points to a disposable test database");
    let pool = PgPoolOptions::new()
        .max_connections(8)
        .connect(&database_url)
        .await
        .expect("Postgres is available");
    initialize(&pool).await.expect("schema is installed");
    let router = app(AppState::new(pool.clone(), ADMIN_KEY));

    let (status, health) = call(&router, Method::GET, "/health", None, None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(health["status"], "UP");

    let (status, denied) = call(
        &router,
        Method::POST,
        "/api/admin/hotels",
        Some(json!({})),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    assert_eq!(denied["code"], "unauthorized");

    let (status, invalid_hotel) = call(&router, Method::POST, "/api/admin/hotels", Some(json!({
        "name":" ", "city":"Lisbon", "district":"Test", "address":"1 Rua Nova", "country":"Portugal",
        "summary":"Test hotel", "imagePath":"/images/pestana-palace-lisboa.webp", "imageAlt":"Hotel exterior", "rating":9.1
    })), Some(ADMIN_KEY)).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(invalid_hotel["code"], "invalid_request");

    let hotel_id = Uuid::new_v4();
    let (status, hotel) = call(&router, Method::POST, "/api/admin/hotels", Some(json!({
        "id": hotel_id, "name":"Rusty Lisbon House", "city":"Lisbon", "district":"Graça", "address":"1 Rua Nova",
        "country":"Portugal", "summary":"A small test stay.", "imagePath":"/images/pestana-palace-lisboa.webp",
        "imageAlt":"Hotel exterior", "rating":9.1
    })), Some(ADMIN_KEY)).await;
    assert_eq!(status, StatusCode::CREATED);
    let hotel_id = hotel["id"].as_str().expect("created hotel id").to_owned();
    assert_eq!(hotel["imagePath"], "/images/pestana-palace-lisboa.webp");
    assert!(hotel.get("roomTypes").is_some());

    let (status, hotels) = call(
        &router,
        Method::GET,
        "/api/hotels?destination=Rusty",
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert!(
        hotels
            .as_array()
            .unwrap()
            .iter()
            .any(|result| result["id"].as_str() == Some(hotel_id.as_str()))
    );
    let (status, hotel_detail) = call(
        &router,
        Method::GET,
        &format!("/api/hotels/{hotel_id}"),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(hotel_detail["city"], "Lisbon");

    let (status, room) = call(
        &router,
        Method::POST,
        &format!("/api/admin/hotels/{hotel_id}/room-types"),
        Some(json!({
            "name":"Courtyard King", "details":"King bed · 32 m²", "maxGuests":2, "totalInventory":2
        })),
        Some(ADMIN_KEY),
    )
    .await;
    assert_eq!(status, StatusCode::CREATED);
    let room_id = room["id"].as_str().expect("created room id").to_owned();
    assert_eq!(room["hotelId"], hotel_id);

    let updated_hotel = call(&router, Method::PUT, &format!("/api/admin/hotels/{hotel_id}"), Some(json!({
        "name":"Rusty Lisbon House", "city":"Lisbon", "district":"Graça", "address":"2 Rua Nova",
        "country":"Portugal", "summary":"A small test stay.", "imagePath":"/images/pestana-palace-lisboa.webp",
        "imageAlt":"Hotel exterior", "rating":9.2
    })), Some(ADMIN_KEY)).await;
    assert_eq!(updated_hotel.0, StatusCode::OK);
    assert_eq!(updated_hotel.1["address"], "2 Rua Nova");

    let updated_room = call(&router, Method::PUT, &format!("/api/admin/room-types/{room_id}"), Some(json!({
        "name":"Courtyard King", "details":"Updated room details", "maxGuests":2, "totalInventory":2
    })), Some(ADMIN_KEY)).await;
    assert_eq!(updated_room.0, StatusCode::OK);
    assert_eq!(updated_room.1["details"], "Updated room details");

    let (status, schedule) = call(
        &router,
        Method::POST,
        "/api/admin/rates/schedule",
        Some(json!({
            "roomTypeId":room_id, "baseRate":200.00
        })),
        Some(ADMIN_KEY),
    )
    .await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    assert_eq!(schedule["raw"], "");

    let check_in = Utc::now().date_naive() + chrono::Days::new(20);
    let check_out = check_in + chrono::Days::new(2);
    let (status, nightly_rate) = call(
        &router,
        Method::PUT,
        "/api/admin/rates",
        Some(json!({
            "roomTypeId":room_id, "date":check_in.to_string(), "amount":250.00
        })),
        Some(ADMIN_KEY),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(nightly_rate["amount"], 250.0);

    let quote_uri =
        format!("/api/rates/quote?roomTypeId={room_id}&checkIn={check_in}&checkOut={check_out}");
    let (status, quote) = call(&router, Method::GET, &quote_uri, None, None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(quote["roomTypeId"], room_id);
    assert_eq!(quote["nights"].as_array().unwrap().len(), 2);
    assert_eq!(quote["nights"][0]["amount"], 250.0);

    let search_uri =
        format!("/api/search?destination=Rusty&checkIn={check_in}&checkOut={check_out}&guests=2");
    let (status, search) = call(&router, Method::GET, &search_uri, None, None).await;
    assert_eq!(status, StatusCode::OK);
    let result = search["stays"]
        .as_array()
        .unwrap()
        .iter()
        .find(|stay| stay["id"].as_str() == Some(hotel_id.as_str()))
        .expect("new hotel is returned by search");
    assert_eq!(result["rooms"][0]["availableRooms"], 2);
    assert_eq!(search["checkIn"], check_in.to_string());

    let reservation_id = Uuid::new_v4();
    let booking = json!({
        "reservationId":reservation_id, "hotelId":hotel_id, "roomTypeId":room_id,
        "checkIn":check_in, "checkOut":check_out, "rooms":2, "guests":2,
        "guestName":"Test Guest", "guestEmail":"Guest@example.com"
    });
    let (status, reservation) = call(
        &router,
        Method::POST,
        "/api/reservations",
        Some(booking.clone()),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(reservation["status"], "CONFIRMED");
    assert_eq!(reservation["guestEmail"], "guest@example.com");
    assert_eq!(reservation["total"], quote["total"].as_f64().unwrap() * 2.0);
    assert!(reservation["paymentId"].is_string());

    let (status, replay) = call(
        &router,
        Method::POST,
        "/api/reservations",
        Some(booking.clone()),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(replay["id"], reservation_id.to_string());
    let mut changed_booking = booking.clone();
    changed_booking["guests"] = json!(1);
    let (status, conflict) = call(
        &router,
        Method::POST,
        "/api/reservations",
        Some(changed_booking),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::CONFLICT);
    assert_eq!(conflict["code"], "idempotency_key_reused");

    let (status, history) = call(
        &router,
        Method::GET,
        "/api/reservations?email=guest%40example.com",
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert!(
        history
            .as_array()
            .unwrap()
            .iter()
            .any(|item| item["id"] == reservation_id.to_string())
    );
    let (status, fetched) = call(
        &router,
        Method::GET,
        &format!("/api/reservations/{reservation_id}"),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(fetched["roomTypeName"], "Courtyard King");

    let (status, payment) = call(&router, Method::POST, "/api/payments", Some(json!({
        "reservationId":reservation_id, "amount":reservation["total"], "guestEmail":"guest@example.com"
    })), None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(payment["status"], "PAID");
    let (status, refund) = call(
        &router,
        Method::PUT,
        &format!("/api/payments/{reservation_id}/refund"),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(refund["status"], "REFUNDED");

    let (status, reduced_inventory) = call(
        &router,
        Method::PUT,
        "/api/admin/inventory",
        Some(json!({
            "hotelId":hotel_id, "roomTypeId":room_id, "totalInventory":1
        })),
        Some(ADMIN_KEY),
    )
    .await;
    assert_eq!(status, StatusCode::CONFLICT);
    assert_eq!(reduced_inventory["code"], "inventory_below_reservations");

    let (status, cancelled) = call(
        &router,
        Method::DELETE,
        &format!("/api/reservations/{reservation_id}"),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(cancelled["status"], "CANCELLED");
    let (status, cancelled_again) = call(
        &router,
        Method::DELETE,
        &format!("/api/reservations/{reservation_id}"),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(cancelled_again["status"], "CANCELLED");

    let (status, invalid_search) = call(
        &router,
        Method::GET,
        "/api/search?destination=Rusty&checkIn=2026-01-01&checkOut=2026-01-02&guests=5",
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(invalid_search["code"], "invalid_dates");

    let (status, unknown_hotel) = call(
        &router,
        Method::GET,
        &format!("/api/hotels/{}", Uuid::new_v4()),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(unknown_hotel["code"], "hotel_not_found");

    let (status, no_rates) = call(
        &router,
        Method::GET,
        &format!(
            "/api/rates/quote?roomTypeId={}&checkIn={check_in}&checkOut={check_out}",
            Uuid::new_v4()
        ),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(no_rates["code"], "rate_not_found");

    let (status, no_payment) = call(
        &router,
        Method::PUT,
        &format!("/api/payments/{}/refund", Uuid::new_v4()),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(no_payment["code"], "payment_not_found");

    let (status, _removed_room) = call(
        &router,
        Method::DELETE,
        &format!("/api/admin/room-types/{room_id}"),
        None,
        Some(ADMIN_KEY),
    )
    .await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    let (status, _removed_hotel) = call(
        &router,
        Method::DELETE,
        &format!("/api/admin/hotels/{hotel_id}"),
        None,
        Some(ADMIN_KEY),
    )
    .await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    let (status, inactive_hotel) = call(
        &router,
        Method::GET,
        &format!("/api/hotels/{hotel_id}"),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(inactive_hotel["code"], "hotel_not_found");
}
