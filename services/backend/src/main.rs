use sqlx::postgres::PgPoolOptions;
use stays_backend::{AppState, app};
use std::{env, net::SocketAddr};
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    dotenvy::dotenv().ok();
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")),
        )
        .init();

    let database_url = env::var("DATABASE_URL").unwrap_or_else(|_| {
        let user = env::var("SPRING_DATASOURCE_USERNAME").unwrap_or_else(|_| "hotel_app".into());
        let password =
            env::var("SPRING_DATASOURCE_PASSWORD").unwrap_or_else(|_| "hotel_local".into());
        let url = env::var("SPRING_DATASOURCE_URL")
            .unwrap_or_else(|_| "jdbc:postgresql://localhost:5432/hotel".into());
        let host = url.strip_prefix("jdbc:").unwrap_or(&url);
        format!(
            "postgres://{user}:{password}@{}",
            host.trim_start_matches("postgresql://")
        )
    });

    let pool = PgPoolOptions::new()
        .max_connections(12)
        .connect(&database_url)
        .await?;
    stays_backend::initialize(&pool).await?;

    let address: SocketAddr = format!(
        "0.0.0.0:{}",
        env::var("SERVER_PORT").unwrap_or_else(|_| "8080".into())
    )
    .parse()?;
    let state = AppState::new(pool, env::var("ADMIN_API_KEY").unwrap_or_default());
    let listener = tokio::net::TcpListener::bind(address).await?;
    tracing::info!(%address, "Stays Rust backend listening");
    axum::serve(listener, app(state))
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
        })
        .await?;
    Ok(())
}
