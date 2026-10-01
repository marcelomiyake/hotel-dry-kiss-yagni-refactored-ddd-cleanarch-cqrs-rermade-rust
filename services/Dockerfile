FROM rust:1.98-slim AS build
WORKDIR /workspace
COPY services/backend/Cargo.toml services/backend/Cargo.lock services/backend/
COPY services/backend/schema services/backend/schema
COPY services/backend/src services/backend/src
RUN cargo build --manifest-path services/backend/Cargo.toml --release

FROM debian:bookworm-slim
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/services/backend/target/release/stays-backend /app/stays-backend
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/stays-backend"]
