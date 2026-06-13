# Portfolio Tracker

A full-stack stock portfolio analytics application built with Spring Boot, demonstrating a clean layered architecture, domain-driven data modelling, and production-ready engineering practices.

---

## What the App Does

Portfolio Tracker lets you:

- **Manage portfolios** — create named portfolios and view their complete holdings
- **Record transactions** — BUY or SELL shares of any tracked stock; the app maintains your running position automatically
- **Track performance** — per-position and portfolio-level analytics: current market value, cost basis, unrealised gain/loss (absolute + %)
- **Visualise allocation** — a Chart.js doughnut chart renders your portfolio weights by ticker, fetched client-side from the JSON API
- **Watch stocks** — maintain named watchlists of stocks you want to monitor without necessarily holding them
- **Browse reference data** — query the full stock catalogue (with optional free-text search by ticker or name)

The app ships with a dev-profile seed that pre-populates five stocks, one portfolio, three holdings, four transactions (BUY + SELL), and a watchlist — so every feature is exercisable immediately on startup.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1 |
| Web MVC | Spring Web MVC + Thymeleaf |
| Persistence | Spring Data JPA (Hibernate 7) |
| Validation | Jakarta Bean Validation 3 |
| Dev database | H2 (in-memory) |
| Prod database | PostgreSQL |
| Build | Apache Maven |
| Tests | JUnit 5 + Mockito + Spring Boot Test |
| Frontend | Thymeleaf templates + Chart.js (CDN) |
| Container | Docker (multi-stage build) |

---

## Architecture

The application follows a strict **three-layer architecture**. Each layer has a single responsibility and communicates only with the layer directly below it.

```
┌──────────────────────────────────────────────────────┐
│            Presentation Layer                        │
│                                                      │
│  DashboardController  (@Controller, Thymeleaf views) │
│  PortfolioController  (@RestController, JSON API)    │
│  StockController      (@RestController, JSON API)    │
│  WatchlistController  (@RestController, JSON API)    │
│  GlobalExceptionHandler (@RestControllerAdvice)      │
└──────────────────────────┬───────────────────────────┘
                           │  calls service methods,
                           │  passes/receives DTOs only
┌──────────────────────────▼───────────────────────────┐
│             Service Layer                            │
│                                                      │
│  PortfolioService  — analytics + transaction engine  │
│  StockService      — reference data + price updates  │
│  WatchlistService  — read-only watchlist queries     │
└──────────────────────────┬───────────────────────────┘
                           │  calls repository methods,
                           │  works with JPA entities
┌──────────────────────────▼───────────────────────────┐
│            Repository Layer                          │
│                                                      │
│  PortfolioRepository  (JpaRepository<Portfolio,Long>)│
│  HoldingRepository    (JpaRepository<Holding,Long>)  │
│  TransactionRepository(JpaRepository<Transaction,Long│
│  StockRepository      (JpaRepository<Stock,Long>)    │
│  WatchlistRepository  (JpaRepository<Watchlist,Long>)│
└──────────────────────────────────────────────────────┘
```

### Key Design Decisions

- **Controllers never serialise JPA entities.** Every HTTP response is a DTO (Java record). This decouples the wire format from the persistence model and prevents accidental lazy-load exceptions at serialisation time.
- **Business logic lives exclusively in the service layer.** Controllers perform HTTP mapping only; no calculations, no repository calls.
- **`@Transactional(readOnly = true)` is the class-level default** on every service. Write methods individually override this with `@Transactional`. Read-only mode lets Hibernate skip dirty-checking and enables routing to a read replica if configured.
- **Open-Session-In-View is disabled** (`spring.jpa.open-in-view=false`). All lazy associations that a view or DTO needs must be JOIN FETCHed inside the service transaction — there are no hidden N+1 queries masked by a long-lived session.
- **Error handling is centralised** in `GlobalExceptionHandler` (`@RestControllerAdvice`). All domain exceptions are mapped to RFC 9457 `ProblemDetail` responses there; exception classes carry no HTTP annotations.

---

## Data Model

### Entity Relationship Diagram

```
┌─────────────┐        ┌──────────────┐        ┌─────────────────┐
│   stocks    │        │   holdings   │        │  portfolios     │
│─────────────│◄───────│──────────────│────────►│─────────────────│
│ id (PK)     │  N:1   │ id (PK)      │  N:1   │ id (PK)         │
│ ticker      │        │ portfolio_id │        │ name            │
│ name        │        │ stock_id     │        │ created_at      │
│ currentPrice│        │ quantity     │        └────────┬────────┘
│ sector      │        │ avg_buy_price│                 │ 1:N
└──────┬──────┘        └──────────────┘                 │
       │                                                 ▼
       │ N:1                                  ┌──────────────────────┐
       │                                      │  stock_transactions  │
       │        ┌──────────────────┐          │──────────────────────│
       │        │  watchlist_stocks│          │ id (PK)              │
       │  N:M   │──────────────────│          │ portfolio_id (FK)    │
       └───────►│ watchlist_id (FK)│          │ stock_id (FK)        │
                │ stock_id     (FK)│          │ transaction_type     │
                └────────┬─────────┘          │ quantity             │
                         │                    │ price                │
                ┌────────▼─────────┐          │ timestamp            │
                │   watchlists     │          └──────────────────────┘
                │──────────────────│
                │ id (PK)          │
                │ name             │
                └──────────────────┘
```

### Entities

#### `Stock`
The shared reference table. Many portfolios and watchlists reference the same `Stock` row.

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | Auto-generated PK |
| `ticker` | `VARCHAR(10)` | Unique, normalised to upper-case (DB unique index `uq_stock_ticker`) |
| `name` | `VARCHAR` | Full company name |
| `current_price` | `NUMERIC(19,4)` | `BigDecimal` — exact decimal arithmetic, never `float` |
| `sector` | `VARCHAR` | e.g. "Technology", "Financials" |

#### `Portfolio`
Aggregate root for a named collection of positions. Owns its holdings and transactions via `CascadeType.ALL + orphanRemoval`.

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | Auto-generated PK |
| `name` | `VARCHAR` | Not blank |
| `created_at` | `TIMESTAMP` | Set by `@PrePersist`, `updatable = false` (immutable after INSERT) |

#### `Holding`
Represents a current open position (owning side of two FK relationships).  
A **composite unique constraint** `(portfolio_id, stock_id)` prevents two rows for the same stock in the same portfolio.

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | Auto-generated PK |
| `portfolio_id` | `BIGINT FK` | References `portfolios` |
| `stock_id` | `BIGINT FK` | References `stocks` |
| `quantity` | `INT` | Must be > 0 |
| `avg_buy_price` | `NUMERIC(19,4)` | Weighted-average cost per share across all BUYs |

#### `Transaction` (table: `stock_transactions`)
Immutable audit record of a single BUY or SELL event. Never updated — corrections are new offsetting rows.

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | Auto-generated PK |
| `portfolio_id` | `BIGINT FK` | References `portfolios` |
| `stock_id` | `BIGINT FK` | References `stocks` |
| `transaction_type` | `VARCHAR(4)` | `EnumType.STRING`: `"BUY"` or `"SELL"` (never ordinal) |
| `quantity` | `INT` | Shares transacted |
| `price` | `NUMERIC(19,4)` | Price per share at execution |
| `timestamp` | `TIMESTAMP` | Auto-set by `@PrePersist`, `updatable = false` |

#### `Watchlist`
A named monitoring list. Owns the `watchlist_stocks` join table; deleting a watchlist removes join rows but never the `Stock` rows.

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | Auto-generated PK |
| `name` | `VARCHAR` | Not blank |

---

## How It Works

### Transaction Processing

When `POST /api/portfolios/{id}/transactions` is called, `PortfolioService.applyTransaction()` executes the following logic atomically inside a single `@Transactional` method:

**BUY path**
1. Look up the portfolio and the stock (throw 404 if either is missing).
2. Query `holdings` for an existing position in that stock.
3. If a holding exists → recompute the **weighted-average cost basis**:
   ```
   newAvgPrice = (oldQty × oldAvg + newQty × newPrice) / (oldQty + newQty)
   ```
   Example: holding 10 shares at $150.00, buying 5 more at $160.00 →
   `(10 × 150.00 + 5 × 160.00) / 15 = $153.33`
4. If no holding exists → create a new `Holding` row with `avgBuyPrice = transactionPrice`.
5. Persist an immutable `Transaction` audit record.

**SELL path**
1. Look up the portfolio, stock, and existing holding.
2. **Fail fast** before any write: if `requested > held`, throw `InsufficientSharesException` (→ HTTP 422).
3. If `remaining == 0` → delete the `Holding` row entirely (position fully exited).
4. Otherwise → decrement `quantity`; `avgBuyPrice` is unchanged (standard cost-accounting).
5. Persist an immutable `Transaction` audit record.

> The sell path never modifies `avgBuyPrice`. The weighted average is a property of the cost of shares *acquired*, not of shares sold. This matches the standard FIFO/WAC accounting treatment.

### Portfolio Valuation

All five analytics values are computed in a **single pass** over the holdings list inside `getPortfolioSummary()` — no repeated repository calls:

| Metric | Formula |
|---|---|
| **Current Value** | `Σ ( quantity × stock.currentPrice )` |
| **Cost Basis** | `Σ ( quantity × holding.avgBuyPrice )` |
| **Unrealised Gain** | `currentValue − costBasis` |
| **Unrealised Gain %** | `unrealisedGain / costBasis × 100` |
| **Allocation** | `positionValue / totalValue × 100` per ticker |

All intermediate arithmetic uses `BigDecimal` with `RoundingMode.HALF_UP` and scale 4 for intermediate steps, rounded to 2 decimal places for display. This is mandatory for monetary values — IEEE-754 floating-point (`double`) cannot represent `0.1` exactly.

`BigDecimal.divide()` throws `ArithmeticException` on non-terminating decimals (e.g. `1/3`) and on division by zero. Both cases are explicitly guarded: scale + `RoundingMode` is always specified, and `costBasis == 0` returns `0%` gain rather than throwing.

### Allocation Chart

The detail page (`/portfolios/{id}`) does **not** embed the allocation data in the server-rendered HTML. Instead, `allocation-chart.js` calls `GET /api/portfolios/{id}/summary` via `fetch()` after page load and builds a Chart.js doughnut client-side. This keeps the chart decoupled from the Thymeleaf render cycle and makes the same API endpoint reusable.

---

## API Endpoints

All REST endpoints are under `/api/**` and return `application/json`. Error responses follow RFC 9457 `ProblemDetail`.

### Portfolios — `/api/portfolios`

| Method | Path | Status | Description |
|---|---|---|---|
| `GET` | `/api/portfolios` | 200 | List all portfolios (id, name, createdAt) |
| `POST` | `/api/portfolios` | 201 | Create a portfolio. Body: `{"name":"My Portfolio"}`. `Location` header points to the new resource. |
| `GET` | `/api/portfolios/{id}` | 200 / 404 | Full portfolio detail with holdings list and per-position analytics |
| `GET` | `/api/portfolios/{id}/summary` | 200 / 404 | Aggregate analytics: currentValue, costBasis, unrealisedGain, unrealisedGainPct, allocation map |
| `POST` | `/api/portfolios/{id}/transactions` | 201 / 404 / 422 | Record a BUY or SELL. Body: `{"stockId":1,"type":"BUY","quantity":10,"price":"150.00"}` |

### Stocks — `/api/stocks`

| Method | Path | Status | Description |
|---|---|---|---|
| `GET` | `/api/stocks` | 200 | List all stocks ordered by ticker. Optional `?q=` for free-text search by ticker or name |
| `GET` | `/api/stocks/{id}` | 200 / 404 | Single stock by id |

### Watchlists — `/api/watchlists`

| Method | Path | Status | Description |
|---|---|---|---|
| `GET` | `/api/watchlists` | 200 | All watchlists with their stock lists |
| `GET` | `/api/watchlists/{id}` | 200 / 404 | Single watchlist with its stocks |

### Web UI (Thymeleaf)

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Redirects to `/portfolios` |
| `GET` | `/portfolios` | Portfolio list page |
| `GET` | `/portfolios/{id}` | Portfolio detail page with holdings table and allocation chart |

### Error Response Shape (RFC 9457)

```json
{
  "type": "/errors/portfolio-not-found",
  "title": "Portfolio Not Found",
  "status": 404,
  "detail": "Portfolio with id 99 not found."
}
```

Validation errors additionally include a field-level `errors` map:

```json
{
  "type": "/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "1 field(s) failed validation",
  "errors": { "name": "Portfolio name must not be blank" }
}
```

---

## How to Run Locally (Dev)

### Prerequisites

- Java 25+
- Maven 3.9+ (or use the included `./mvnw` wrapper)

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/your-username/portfolio-tracker.git
cd portfolio-tracker

# 2. Run with the dev profile (H2 in-memory, auto-seeded)
./mvnw spring-boot:run
# or on Windows:
mvnw.cmd spring-boot:run

# 3. Open the app
#    Web UI:    http://localhost:8080/portfolios
#    H2 Console: http://localhost:8080/h2-console
#                JDBC URL: jdbc:h2:mem:portfolio
#                User: sa  Password: (blank)
#    REST API:  http://localhost:8080/api/portfolios
```

The `dev` profile is active by default (`spring.profiles.active=dev` in `application.properties`). The `DataSeeder` bean seeds five stocks, one portfolio, and one watchlist on the first startup.

### Running Tests

```bash
./mvnw test
```

Tests use an in-memory H2 database isolated from the dev database. `@DataJpaTest` slices load only the JPA layer; `@SpringBootTest` + `MockMvc` tests exercise the full HTTP stack.

---

## How to Run with Docker (Prod)

### Prerequisites

- Docker Desktop (or Docker Engine)
- A running PostgreSQL instance (or use the example `docker-compose.yml` below)

### Build the Image

```bash
docker build -t portfolio-tracker:latest .
```

The multi-stage `Dockerfile` compiles the fat JAR in a Maven image and copies only the JAR into a minimal JRE runtime image, keeping the final image small.

### Run Against PostgreSQL

```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=your-postgres-host \
  -e DB_PORT=5432 \
  -e DB_NAME=portfolio \
  -e DB_USERNAME=portfolio_user \
  -e DB_PASSWORD=secret \
  portfolio-tracker:latest
```

### Docker Compose (App + DB Together)

```yaml
# docker-compose.yml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: portfolio
      POSTGRES_USER: portfolio_user
      POSTGRES_PASSWORD: secret
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  app:
    image: portfolio-tracker:latest
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: db
      DB_PORT: 5432
      DB_NAME: portfolio
      DB_USERNAME: portfolio_user
      DB_PASSWORD: secret
    depends_on:
      - db

volumes:
  pgdata:
```

```bash
docker compose up --build
```

---

## Production Configuration

Activate the `prod` profile to switch to PostgreSQL:

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=portfolio \
DB_USERNAME=portfolio_user \
DB_PASSWORD=secret \
./mvnw spring-boot:run
```

The `prod` profile (`application-prod.properties`) configures:
- PostgreSQL datasource via environment variables (no credentials in source control)
- `ddl-auto=validate` — Hibernate validates the schema against the entities but never modifies it
- SQL logging disabled
- H2 console disabled
- HikariCP connection pool tuned for production

The dev H2 setup in `application.properties` is untouched — the `prod` properties file overrides only what it declares.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/arshkapoor/portfolio/
│   │   ├── controller/
│   │   │   ├── DashboardController.java        # Thymeleaf views
│   │   │   ├── PortfolioController.java         # REST API
│   │   │   ├── StockController.java             # REST API
│   │   │   ├── WatchlistController.java         # REST API
│   │   │   └── GlobalExceptionHandler.java      # Centralised RFC 9457 errors
│   │   ├── service/
│   │   │   ├── PortfolioService.java            # Analytics + transaction engine
│   │   │   ├── StockService.java                # Reference data
│   │   │   └── WatchlistService.java            # Watchlist reads
│   │   ├── repository/
│   │   │   ├── PortfolioRepository.java
│   │   │   ├── HoldingRepository.java           # JOIN FETCH queries
│   │   │   ├── TransactionRepository.java
│   │   │   ├── StockRepository.java             # Ticker search
│   │   │   └── WatchlistRepository.java         # JOIN FETCH queries
│   │   ├── entity/
│   │   │   ├── Portfolio.java                   # Aggregate root
│   │   │   ├── Holding.java                     # Open position
│   │   │   ├── Transaction.java                 # Immutable audit record
│   │   │   ├── Stock.java                       # Reference entity
│   │   │   ├── Watchlist.java                   # Many-to-many with Stock
│   │   │   └── TransactionType.java             # BUY / SELL enum
│   │   ├── dto/
│   │   │   ├── PortfolioDetailDto.java
│   │   │   ├── PortfolioListItemDto.java
│   │   │   ├── PortfolioSummaryDto.java          # All analytics in one record
│   │   │   ├── HoldingDto.java                  # Per-position analytics
│   │   │   ├── TransactionRequest.java
│   │   │   ├── TransactionResponseDto.java
│   │   │   ├── StockDto.java
│   │   │   └── WatchlistDto.java
│   │   ├── exception/
│   │   │   ├── PortfolioNotFoundException.java
│   │   │   ├── StockNotFoundException.java
│   │   │   ├── WatchlistNotFoundException.java
│   │   │   └── InsufficientSharesException.java
│   │   ├── DataSeeder.java                      # @Profile("dev") seed data
│   │   └── PortfolioTrackerApplication.java
│   └── resources/
│       ├── application.properties               # Dev (H2) defaults
│       ├── application-prod.properties          # Prod (PostgreSQL) overrides
│       ├── static/
│       │   ├── css/app.css
│       │   └── js/allocation-chart.js           # Chart.js doughnut via fetch()
│       └── templates/
│           ├── fragments/layout.html            # Thymeleaf base layout
│           └── portfolios/
│               ├── list.html
│               └── detail.html
└── test/
    └── java/com/arshkapoor/portfolio/
        ├── PortfolioTrackerApplicationTests.java
        ├── PortfolioControllerIntegrationTest.java
        ├── service/PortfolioServiceTest.java
        └── repository/StockRepositoryTest.java
```

