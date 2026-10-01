use axum::{
    Json,
    extract::{Query, State},
    http::StatusCode,
};
use chrono::{NaiveDate, Utc};
use rust_decimal::Decimal;
use serde::Deserialize;
use sqlx::{FromRow, PgPool};
use uuid::Uuid;

use crate::{ApiError, AppState, NightlyRate, RateDraft, RateQuote, RateSchedule};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct QuoteQuery {
    room_type_id: Uuid,
    check_in: NaiveDate,
    check_out: NaiveDate,
}

#[derive(FromRow)]
struct RateRow {
    date: NaiveDate,
    amount: Decimal,
}

pub(crate) async fn quote(
    State(state): State<AppState>,
    Query(query): Query<QuoteQuery>,
) -> Result<Json<RateQuote>, ApiError> {
    Ok(Json(
        quote_rates(
            &state.pool,
            query.room_type_id,
            query.check_in,
            query.check_out,
        )
        .await?,
    ))
}

pub(crate) async fn set_rate(
    State(state): State<AppState>,
    Json(draft): Json<RateDraft>,
) -> Result<Json<NightlyRate>, ApiError> {
    if draft.amount <= Decimal::ZERO {
        return Err(invalid_rate());
    }
    sqlx::query(
        "INSERT INTO nightly_rates.rates (room_type_id,rate_date,amount) VALUES ($1,$2,$3) \
         ON CONFLICT (room_type_id,rate_date) DO UPDATE SET amount=excluded.amount",
    )
    .bind(draft.room_type_id)
    .bind(draft.date)
    .bind(draft.amount)
    .execute(&state.pool)
    .await?;
    Ok(Json(NightlyRate {
        date: draft.date,
        amount: draft.amount,
    }))
}

pub(crate) async fn create_schedule(
    State(state): State<AppState>,
    Json(schedule): Json<RateSchedule>,
) -> Result<StatusCode, ApiError> {
    if schedule.base_rate <= Decimal::ZERO {
        return Err(invalid_rate());
    }
    sqlx::query(
        "INSERT INTO nightly_rates.rates (room_type_id,rate_date,amount) \
         SELECT $1, rate_date::date, round($2 * CASE WHEN extract(isodow FROM rate_date) IN (5,6) \
         THEN 1.15 ELSE 1 END, 2) \
         FROM generate_series(current_date, current_date + 730, interval '1 day') AS rate_date \
         ON CONFLICT (room_type_id,rate_date) DO UPDATE SET amount=excluded.amount",
    )
    .bind(schedule.room_type_id)
    .bind(schedule.base_rate)
    .execute(&state.pool)
    .await?;
    Ok(StatusCode::NO_CONTENT)
}

pub(crate) async fn quote_rates(
    pool: &PgPool,
    room_type_id: Uuid,
    check_in: NaiveDate,
    check_out: NaiveDate,
) -> Result<RateQuote, ApiError> {
    let nights = validate_period(check_in, check_out)?;
    let rates = sqlx::query_as::<_, RateRow>(
        "SELECT rate_date AS date,amount FROM nightly_rates.rates \
         WHERE room_type_id=$1 AND rate_date >= $2 AND rate_date < $3 ORDER BY rate_date",
    )
    .bind(room_type_id)
    .bind(check_in)
    .bind(check_out)
    .fetch_all(pool)
    .await?;
    if rates.len() != nights as usize {
        return Err(ApiError::not_found(
            "rate_not_found",
            "No rate is available for every night.",
        ));
    }
    let nightly_rates: Vec<NightlyRate> = rates
        .into_iter()
        .map(|rate| NightlyRate {
            date: rate.date,
            amount: rate.amount,
        })
        .collect();
    let total = sum_rates(&nightly_rates);
    Ok(RateQuote {
        room_type_id,
        nights: nightly_rates,
        total,
    })
}

pub(crate) fn validate_period(check_in: NaiveDate, check_out: NaiveDate) -> Result<i64, ApiError> {
    let nights = check_out.signed_duration_since(check_in).num_days();
    if !(1..=365).contains(&nights) {
        return Err(ApiError::bad_request(
            "invalid_dates",
            "Choose a stay between 1 and 365 nights.",
        ));
    }
    Ok(nights)
}

fn sum_rates(rates: &[NightlyRate]) -> Decimal {
    rates
        .iter()
        .fold(Decimal::ZERO, |total, rate| total + rate.amount)
}

fn invalid_rate() -> ApiError {
    ApiError::bad_request("invalid_request", "Check the request values and try again.")
}

pub(crate) fn search_date_limit() -> NaiveDate {
    Utc::now()
        .date_naive()
        .checked_add_months(chrono::Months::new(24))
        .unwrap_or(NaiveDate::MAX)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn periods_must_have_one_to_365_nights() {
        let date = NaiveDate::from_ymd_opt(2026, 1, 1).expect("valid date");
        assert_eq!(
            validate_period(date, date.succ_opt().expect("next date")).expect("one night"),
            1
        );
        assert_eq!(
            validate_period(date, date + chrono::Days::new(365)).expect("max nights"),
            365
        );
        assert_eq!(
            validate_period(date, date).err().map(|error| error.code),
            Some("invalid_dates")
        );
        assert_eq!(
            validate_period(date, date + chrono::Days::new(366))
                .err()
                .map(|error| error.code),
            Some("invalid_dates")
        );
    }

    #[test]
    fn totals_are_decimal_sums_without_float_rounding() {
        let rates = vec![
            NightlyRate {
                date: NaiveDate::from_ymd_opt(2026, 1, 1).unwrap(),
                amount: Decimal::new(101, 2),
            },
            NightlyRate {
                date: NaiveDate::from_ymd_opt(2026, 1, 2).unwrap(),
                amount: Decimal::new(202, 2),
            },
        ];
        assert_eq!(sum_rates(&rates), Decimal::new(303, 2));
    }
}
