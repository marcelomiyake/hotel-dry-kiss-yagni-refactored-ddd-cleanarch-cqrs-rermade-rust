# Stays · Lisbon Hotel Reservations

A React hotel reservation app backed by a Rust API and PostgreSQL. This fork replaces the former Java services with one deployable Axum backend while retaining the existing React design system and HTTP API contracts.

![Stays hotel reservation search page](docs/screenshots/hotel-reservation-home.png)

## Product behavior

- Search Lisbon hotels by destination, dates, and guest count; compare availability and nightly rates.
- Reserve by room type. PostgreSQL stores nightly inventory, supports up to 10% overbooking, and uses version-checked updates for concurrent bookings.
- Replay a reservation with its UUID as an idempotency key. Reusing that key with changed details returns a conflict.
- Read reservation history by email and cancel a confirmed reservation. Cancellation releases inventory and records a simulated refund.
- Let staff manage hotels, room types, inventory, and nightly rates behind the admin key.

The seeded catalog contains two Lisbon hotels. Payments are simulated; the app does not collect card details or contact a hotel.

## Architecture

`services/backend` is a single Rust 2024 service using Axum, SQLx, and PostgreSQL. Its modules group the hotel catalog, rates, payments, reservations/search, API models, and HTTP error mapping. SQL schemas are in `services/backend/schema`. The Docker build compiles a small release binary and runs it as an unprivileged user.

The React frontend remains organized into domain, application, infrastructure, and presentation areas. Its `HttpHotelGateway` continues to call the same `/api` routes, and `frontend/src/styles.css` retains the established design system.

## Run on local Kind

Requirements: Docker, Kind, kubectl, and `openssl`.

```bash
./scripts/kind-up.sh
```

Open [http://localhost:8080](http://localhost:8080). The script creates the `hotel-reservation` cluster, builds and loads the Rust API and web images, and deploys the app. It creates local database and staff keys in `.kind-db-password` and `.kind-admin-key`; Git ignores those files.

```bash
kubectl -n hotel-reservation get deployments,pods,services
./scripts/kind-down.sh
```

## HTTP API contracts

Admin endpoints require the `X-Admin-Key` header. Request and response JSON fields are covered by the backend API contract test.

| Method and route | Purpose |
| --- | --- |
| `GET /health` | Kubernetes health check |
| `GET /api/search?destination=Lisbon&checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD&guests=2` | Search availability and rates |
| `GET /api/hotels?destination=Lisbon` | Read the hotel catalog |
| `GET /api/hotels/{id}` | Read a hotel and its room types |
| `GET /api/rates/quote?roomTypeId=…&checkIn=…&checkOut=…` | Quote every night in a stay |
| `POST /api/payments` | Charge a simulated payment |
| `PUT /api/payments/{reservationId}/refund` | Refund a simulated payment |
| `POST /api/reservations` | Create or replay a reservation |
| `GET /api/reservations?email=…` | Read a guest’s reservation history |
| `GET /api/reservations/{id}` | Read one reservation |
| `DELETE /api/reservations/{id}` | Cancel a reservation and refund its demo payment |
| `POST /api/admin/hotels` | Add a hotel |
| `PUT, DELETE /api/admin/hotels/{id}` | Update or remove a hotel |
| `POST /api/admin/hotels/{hotelId}/room-types` | Add a room type |
| `PUT, DELETE /api/admin/room-types/{id}` | Update or remove a room type |
| `PUT /api/admin/inventory` | Update future inventory |
| `PUT /api/admin/rates` | Update one nightly rate |
| `POST /api/admin/rates/schedule` | Create a nightly rate schedule |

## Development and quality checks

Frontend checks:

```bash
cd frontend
npm ci
npm test
npm run lint
npm run build
```

Backend formatting, linting, and coverage (Rust toolchain, `cargo-llvm-cov`, and a reachable disposable PostgreSQL database are required):

```bash
cd services/backend
cargo fmt --check
cargo clippy --all-targets --all-features -- -D warnings
DATABASE_URL=postgres://hotel_app:hotel_local@localhost:5432/hotel_test \
  cargo llvm-cov --all-targets
```

The verified frontend suite has **15 passing tests** and **92.15% line coverage**. The Rust suite has **12 passing tests** (11 unit tests and one API contract test) and **91.02% line coverage**.

### SonarQube Cloud

The public [Hotel Reservation System – Rust Backend SonarQube Cloud project](https://sonarcloud.io/project/overview?id=marcelomiyake_hotel-dry-kiss-yagni-refactored-ddd-cleanarch-cqrs-rermade-rust) uses scanner-based analysis; Automatic Analysis is disabled for this project. The scan ran the Rust Enterprise sensor, imported Rust coverage in Sonar’s generic coverage format, imported Clippy findings in the generic external issues format, and imported frontend LCOV coverage. See Sonar’s [Rust analysis documentation](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/languages/rust), [generic issue import format](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/importing-external-issues/generic-issue-data), and [coverage import guide](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/overview).

The latest verified analysis reports **0 open issues**, **91.1% overall coverage**, and a **passed quality gate**. Strict Clippy completed without warnings.

To prepare the Rust reports for a later analysis, first run coverage and Clippy from `services/backend` with `DATABASE_URL` set to a disposable database:

```bash
mkdir -p target/sonar
cargo llvm-cov --all-targets --lcov --output-path target/sonar/rust-lcov.info
cargo clippy --all-targets --all-features --message-format=json -- -D warnings \
  > target/sonar/rust-clippy.jsonl
cd ../..
python3 scripts/sonar-rust-reports.py "$PWD" \
  services/backend/target/sonar/rust-lcov.info \
  services/backend/target/sonar/rust-clippy.jsonl
```

Run `npm test` in `frontend` to create its LCOV report, then run SonarScanner CLI from the repository root with `SONAR_TOKEN` and `SONAR_HOST_URL=https://sonarcloud.io` set in the environment. The project settings are in `sonar-project.properties`.

**Credential follow-up:** The configured `SONAR_TOKEN` was inadvertently included in local process output during this task. Revoke it in SonarQube Cloud and replace it in `~/.zshrc` before running another scan. No replacement token was generated.

### Lighthouse and SEO

The production frontend was audited with Lighthouse 13.5.0 using the desktop preset. It scored **100** for performance, accessibility, best practices, and SEO (and 100 in the experimental agentic-browsing category). The page includes a title, description, robots directives, Open Graph and Twitter metadata, TravelAgency JSON-LD, `robots.txt`, `llms.txt`, and an AI Catalog manifest.

## Analysis record

### Original task prompt

> This is a fork of [https://github.com/marcelomiyake/hotel-dry-kiss-yagni-refactored-ddd-cleanarch-cqrs](https://github.com/marcelomiyake/hotel-dry-kiss-yagni-refactored-ddd-cleanarch-cqrs), and the backend implementation must now be rebuilt in Rust. Retain the same design system and API contracts. Use SonarQube Cloud via Chrome ([https://sonarcloud.io/organizations/marcelomiyake/](https://sonarcloud.io/organizations/marcelomiyake/)) to create new SonarQube projects, manage them, and complete this job with zero SonarQube issues and test coverage above 80%. If you need to run the scanner from the command line, I updated \~/.zshrc with the SONAR_TOKEN. Still, you can also use GitHub Actions and push commits in a loop until the issues are clean (the problem is that Rust is not supported for automatic analysis (), so use another approach to consider Rust code in SonarQube Cloud ([https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/languages/rust](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/languages/rust)); if you generate another SONAR_KEY, update it in the GitHub project or in .zshrc. The frontend should have a perfect Lighthouse grade and good SEO META in 1 Click. Finally, update the [README.md](http://README.md) with an analysis that includes this prompt, the harness used here (Codex, GPT-6 Luna with max effort), and the token costs from the sessions to complete this task (input tokens, cache tokens, reasoning tokens, output tokens) and LOC. The cache and sessions were empty just before starting this session. Consult the OpenAI official documentation for token prices to estimate total costs.

### Harness, LOC, and token estimate

| Measure | Result |
| --- | --- |
| Harness | Codex · GPT-6 Luna · max effort |
| Frontend tests and line coverage | 15 passed · 92.15% |
| Rust tests and line coverage | 12 passed · 91.02% |
| SonarQube Cloud | 0 open issues · 91.1% coverage · quality gate passed |
| Lighthouse desktop | 100 performance · 100 accessibility · 100 best practices · 100 SEO |
| Production source LOC | 3,467 nonblank lines across 23 Rust, SQL, TypeScript, TSX, and CSS files |
| Test source LOC | 979 nonblank lines across 4 embedded Rust test modules and 6 external test/setup files |
| Total source and test LOC | 4,446 nonblank lines |

LOC counts include nonblank lines (including comments), exclude blank lines, and separate Rust unit-test modules from production source. Generated output, dependencies, assets, documentation, and configuration are excluded.

The user reported that the session and cache were empty before work began. These are the cumulative Codex thread counters at **2026-09-30 23:55 UTC**, before this final token-table update and response. Input and cached input are recorded as separate categories. Reasoning tokens are a subset of output tokens and are not charged a second time.

| Token measure | Count |
| --- | ---: |
| Input tokens (uncached) | 27,574,140 |
| Cached input tokens | 26,891,264 |
| Reasoning tokens (included in output) | 99,605 |
| Output tokens | 168,144 |
| Estimated model-token cost | **$3.11 USD** |

Worked for 1h 2m 24s

The estimate uses the official [OpenAI GPT-6 Luna rate card](https://help.openai.com/en/articles/20001415-chatgpt-rate-card-enterprise-token-based-pricing) for Standard mode: `(input × $0.10/M) + (cached input × $0.01/M) + (output × $0.50/M)`. It is a token-only estimate; separate feature charges and workspace billing terms may differ.
