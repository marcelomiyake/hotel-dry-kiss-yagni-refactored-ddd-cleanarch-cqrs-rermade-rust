use axum::{
    Json,
    extract::{Path, State},
};
use rust_decimal::Decimal;
use sqlx::{FromRow, PgPool};
use uuid::Uuid;

use crate::{ApiError, AppState, Payment, PaymentRequest, PaymentStatus};

#[derive(FromRow)]
struct PaymentRow {
    id: Uuid,
    reservation_id: Uuid,
    amount: Decimal,
    status: String,
    created_at: chrono::DateTime<chrono::Utc>,
}

pub(crate) async fn charge(
    State(state): State<AppState>,
    Json(request): Json<PaymentRequest>,
) -> Result<Json<Payment>, ApiError> {
    validate_request(&request)?;
    let payment = charge_for(&state.pool, &request).await?;
    Ok(Json(payment))
}

pub(crate) async fn refund(
    State(state): State<AppState>,
    Path(reservation_id): Path<Uuid>,
) -> Result<Json<Payment>, ApiError> {
    Ok(Json(refund_reservation(&state.pool, reservation_id).await?))
}

pub(crate) async fn refund_reservation(
    pool: &PgPool,
    reservation_id: Uuid,
) -> Result<Payment, ApiError> {
    let changed = sqlx::query(
        "UPDATE payments.transactions SET status='REFUNDED' WHERE reservation_id=$1 AND status='PAID'"
    ).bind(reservation_id).execute(pool).await?.rows_affected();
    let payment = find_payment(pool, reservation_id).await?;
    if changed == 0 && payment.status != PaymentStatus::Refunded {
        return Err(ApiError::conflict(
            "payment_not_refundable",
            "The payment cannot be refunded.",
        ));
    }
    Ok(payment)
}

pub(crate) async fn charge_for(
    pool: &PgPool,
    request: &PaymentRequest,
) -> Result<Payment, ApiError> {
    validate_request(request)?;
    sqlx::query(
        "INSERT INTO payments.transactions (reservation_id,amount,guest_email,status) \
         VALUES ($1,$2,$3,'PAID') ON CONFLICT (reservation_id) DO NOTHING",
    )
    .bind(request.reservation_id)
    .bind(request.amount)
    .bind(request.guest_email.trim().to_lowercase())
    .execute(pool)
    .await?;
    let payment = find_payment(pool, request.reservation_id).await?;
    if payment.amount != request.amount {
        return Err(ApiError::conflict(
            "payment_conflict",
            "The reservation already has a different payment amount.",
        ));
    }
    Ok(payment)
}

pub(crate) async fn find_payment(pool: &PgPool, reservation_id: Uuid) -> Result<Payment, ApiError> {
    let row = sqlx::query_as::<_, PaymentRow>(
        "SELECT id,reservation_id,amount,status,created_at FROM payments.transactions WHERE reservation_id=$1"
    ).bind(reservation_id).fetch_optional(pool).await?
        .ok_or_else(|| ApiError::not_found("payment_not_found", "Payment not found."))?;
    payment_from_row(row)
}

fn validate_request(request: &PaymentRequest) -> Result<(), ApiError> {
    if request.reservation_id.is_nil()
        || request.amount <= Decimal::ZERO
        || !email_is_valid(&request.guest_email)
    {
        return Err(ApiError::bad_request(
            "invalid_request",
            "Check the request values and try again.",
        ));
    }
    Ok(())
}

pub(crate) fn email_is_valid(email: &str) -> bool {
    let mut parts = email.trim().split('@');
    matches!((parts.next(), parts.next(), parts.next()), (Some(local), Some(domain), None)
        if !local.is_empty() && domain.contains('.') && !domain.starts_with('.') && !domain.ends_with('.'))
}

fn payment_from_row(row: PaymentRow) -> Result<Payment, ApiError> {
    let status = match row.status.as_str() {
        "PAID" => PaymentStatus::Paid,
        "REFUNDED" => PaymentStatus::Refunded,
        _ => return Err(ApiError::internal()),
    };
    Ok(Payment {
        id: row.id,
        reservation_id: row.reservation_id,
        amount: row.amount,
        status,
        created_at: row.created_at,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn payment_validation_requires_positive_amount_and_email() {
        let mut request = PaymentRequest {
            reservation_id: Uuid::new_v4(),
            amount: Decimal::ONE,
            guest_email: "guest@example.com".into(),
        };
        assert!(validate_request(&request).is_ok());
        request.amount = Decimal::ZERO;
        assert!(validate_request(&request).is_err());
        request.amount = Decimal::ONE;
        request.guest_email = "not-an-email".into();
        assert!(validate_request(&request).is_err());
    }

    #[test]
    fn email_validation_and_status_names_match_the_contract() {
        assert!(email_is_valid(" a@hotel.pt "));
        assert!(!email_is_valid("a@.pt"));
        assert_eq!(
            serde_json::to_value(PaymentStatus::Paid).unwrap(),
            serde_json::json!("PAID")
        );
    }
}
