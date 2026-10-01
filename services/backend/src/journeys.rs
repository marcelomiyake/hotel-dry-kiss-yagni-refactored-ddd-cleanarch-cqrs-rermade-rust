use axum::{Json, extract::State, http::StatusCode};
use serde::Deserialize;
use sqlx::PgPool;
use uuid::Uuid;

use crate::{ApiError, AppState};

#[derive(Clone, Copy, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
enum JourneyEventType {
    Started,
    ScreenViewed,
    Completed,
}

impl JourneyEventType {
    fn as_str(self) -> &'static str {
        match self {
            Self::Started => "started",
            Self::ScreenViewed => "screen_viewed",
            Self::Completed => "completed",
        }
    }
}

#[derive(Clone, Copy, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
enum JourneyScreen {
    Search,
    Results,
    Details,
    Checkout,
    Confirmation,
    Bookings,
    Staff,
}

impl JourneyScreen {
    fn as_str(self) -> &'static str {
        match self {
            Self::Search => "search",
            Self::Results => "results",
            Self::Details => "details",
            Self::Checkout => "checkout",
            Self::Confirmation => "confirmation",
            Self::Bookings => "bookings",
            Self::Staff => "staff",
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct JourneyEventRequest {
    journey_id: Uuid,
    sequence: i32,
    event_type: JourneyEventType,
    screen: JourneyScreen,
}

pub(crate) async fn record(
    State(state): State<AppState>,
    Json(request): Json<JourneyEventRequest>,
) -> Result<StatusCode, ApiError> {
    record_event(&state.pool, &request).await?;
    Ok(StatusCode::NO_CONTENT)
}

async fn record_event(pool: &PgPool, request: &JourneyEventRequest) -> Result<(), ApiError> {
    validate_event(request)?;
    let mut transaction = pool.begin().await?;

    if request.event_type == JourneyEventType::Completed {
        let paid: bool = sqlx::query_scalar(
            "SELECT EXISTS (SELECT 1 FROM reservations.bookings \
             WHERE id=$1 AND status='CONFIRMED' AND payment_id IS NOT NULL)",
        )
        .bind(request.journey_id)
        .fetch_one(&mut *transaction)
        .await?;
        if !paid {
            return Err(ApiError::conflict(
                "reservation_not_paid",
                "A journey can be completed only after payment succeeds.",
            ));
        }
    }

    let changed = match request.event_type {
        JourneyEventType::Started => {
            sqlx::query_scalar::<_, Uuid>(
                "INSERT INTO reservation_analytics.journeys \
             (journey_id,started_at,last_activity_at,last_screen,last_sequence) \
             VALUES ($1,now(),now(),$2,$3) \
             ON CONFLICT (journey_id) DO UPDATE SET \
                 last_activity_at=now(),last_screen=EXCLUDED.last_screen, \
                 last_sequence=EXCLUDED.last_sequence \
             WHERE reservation_analytics.journeys.completed_at IS NULL \
               AND reservation_analytics.journeys.last_sequence < EXCLUDED.last_sequence \
             RETURNING journey_id",
            )
            .bind(request.journey_id)
            .bind(request.screen.as_str())
            .bind(request.sequence)
            .fetch_optional(&mut *transaction)
            .await?
        }
        JourneyEventType::ScreenViewed | JourneyEventType::Completed => {
            sqlx::query_scalar::<_, Uuid>(
                "UPDATE reservation_analytics.journeys SET \
                     last_activity_at=now(),last_screen=$2,last_sequence=$3, \
                     completed_at=CASE WHEN $4 THEN now() ELSE completed_at END \
                 WHERE journey_id=$1 AND completed_at IS NULL AND last_sequence < $3 \
                 RETURNING journey_id",
            )
            .bind(request.journey_id)
            .bind(request.screen.as_str())
            .bind(request.sequence)
            .bind(request.event_type == JourneyEventType::Completed)
            .fetch_optional(&mut *transaction)
            .await?
        }
    };

    if changed.is_none() {
        let exists: bool = sqlx::query_scalar(
            "SELECT EXISTS (SELECT 1 FROM reservation_analytics.journeys WHERE journey_id=$1)",
        )
        .bind(request.journey_id)
        .fetch_one(&mut *transaction)
        .await?;
        if !exists {
            return Err(ApiError::conflict(
                "reservation_journey_not_started",
                "Start a reservation journey before recording its progress.",
            ));
        }
        transaction.commit().await?;
        return Ok(());
    }

    sqlx::query(
        "INSERT INTO reservation_analytics.journey_events \
         (journey_id,sequence,event_type,screen) VALUES ($1,$2,$3,$4) \
         ON CONFLICT (journey_id,sequence) DO NOTHING",
    )
    .bind(request.journey_id)
    .bind(request.sequence)
    .bind(request.event_type.as_str())
    .bind(request.screen.as_str())
    .execute(&mut *transaction)
    .await?;
    transaction.commit().await?;
    Ok(())
}

pub(crate) async fn mark_paid_reservation_completed(
    pool: &PgPool,
    reservation_id: Uuid,
) -> Result<(), ApiError> {
    let mut transaction = pool.begin().await?;
    let sequence = sqlx::query_scalar::<_, i32>(
        "UPDATE reservation_analytics.journeys SET \
             last_activity_at=now(),last_screen='confirmation', \
             last_sequence=last_sequence+1,completed_at=now() \
         WHERE journey_id=$1 AND completed_at IS NULL \
         RETURNING last_sequence",
    )
    .bind(reservation_id)
    .fetch_optional(&mut *transaction)
    .await?;

    if let Some(sequence) = sequence {
        sqlx::query(
            "INSERT INTO reservation_analytics.journey_events \
             (journey_id,sequence,event_type,screen) \
             VALUES ($1,$2,'completed','confirmation') \
             ON CONFLICT (journey_id,sequence) DO NOTHING",
        )
        .bind(reservation_id)
        .bind(sequence)
        .execute(&mut *transaction)
        .await?;
    }

    transaction.commit().await?;
    Ok(())
}

fn validate_event(request: &JourneyEventRequest) -> Result<(), ApiError> {
    let valid = !request.journey_id.is_nil()
        && match request.event_type {
            JourneyEventType::Started => {
                request.sequence == 1 && request.screen == JourneyScreen::Details
            }
            JourneyEventType::ScreenViewed => {
                request.sequence > 1 && request.screen != JourneyScreen::Confirmation
            }
            JourneyEventType::Completed => {
                request.sequence > 1 && request.screen == JourneyScreen::Confirmation
            }
        };

    if valid {
        Ok(())
    } else {
        Err(ApiError::bad_request(
            "invalid_reservation_journey_event",
            "Check the journey ID, sequence, event type, and screen.",
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::{JourneyEventRequest, JourneyEventType, JourneyScreen, validate_event};
    use uuid::Uuid;

    fn event(
        event_type: JourneyEventType,
        screen: JourneyScreen,
        sequence: i32,
    ) -> JourneyEventRequest {
        JourneyEventRequest {
            journey_id: Uuid::new_v4(),
            sequence,
            event_type,
            screen,
        }
    }

    #[test]
    fn accepts_a_started_journey_on_the_details_screen() {
        assert!(
            validate_event(&event(JourneyEventType::Started, JourneyScreen::Details, 1)).is_ok()
        );
    }

    #[test]
    fn rejects_invalid_start_and_screen_sequence_pairs() {
        assert!(
            validate_event(&event(
                JourneyEventType::Started,
                JourneyScreen::Checkout,
                1
            ))
            .is_err()
        );
        assert!(
            validate_event(&event(
                JourneyEventType::ScreenViewed,
                JourneyScreen::Details,
                1
            ))
            .is_err()
        );
        assert!(
            validate_event(&event(
                JourneyEventType::ScreenViewed,
                JourneyScreen::Confirmation,
                2
            ))
            .is_err()
        );
        assert!(
            validate_event(&event(
                JourneyEventType::Completed,
                JourneyScreen::Checkout,
                3
            ))
            .is_err()
        );
        assert!(
            validate_event(&event(
                JourneyEventType::Completed,
                JourneyScreen::Confirmation,
                1
            ))
            .is_err()
        );
    }

    #[test]
    fn rejects_a_nil_journey_id() {
        let mut request = event(JourneyEventType::Started, JourneyScreen::Details, 1);
        request.journey_id = Uuid::nil();
        assert!(validate_event(&request).is_err());
    }
}
