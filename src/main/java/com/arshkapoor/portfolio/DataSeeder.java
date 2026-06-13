package com.arshkapoor.portfolio;

import com.arshkapoor.portfolio.entity.*;
import com.arshkapoor.portfolio.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Populates the H2 in-memory database with representative data on every
 * dev-profile startup so you can immediately verify persistence in the H2
 * console without writing a test.
 *
 * @Profile("dev") — this bean is only created when the "dev" Spring profile
 * is active.  It will NOT run when the "prod" profile is active, so there is
 * no risk of seeding a PostgreSQL production database.
 *
 * CommandLineRunner — Spring Boot calls run() once the application context is
 * fully initialised.  All beans (including repositories) are guaranteed to be
 * ready; this is safe to use for any startup logic.
 */
@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StockRepository     stockRepo;
    private final PortfolioRepository portfolioRepo;
    private final WatchlistRepository watchlistRepo;

    /**
     * Constructor injection — preferred over @Autowired field injection because:
     *   1. Dependencies are explicit and visible in the constructor signature.
     *   2. The class is trivially unit-testable (no Spring context needed).
     *   3. Fields can be declared final, preventing accidental reassignment.
     */
    public DataSeeder(StockRepository     stockRepo,
                      PortfolioRepository portfolioRepo,
                      WatchlistRepository watchlistRepo) {
        this.stockRepo     = stockRepo;
        this.portfolioRepo = portfolioRepo;
        this.watchlistRepo = watchlistRepo;
    }

    /**
     * @Transactional wraps the entire seed run in a single transaction.
     * If any save() call fails, the whole batch is rolled back — the database
     * is never left in a half-seeded state.
     *
     * The idempotency guard (stockRepo.count() > 0) is a safety net for
     * scenarios where ddl-auto = update instead of create-drop: it prevents
     * duplicate data on application restart.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (stockRepo.count() > 0) {
            log.info("[DataSeeder] Database already seeded — skipping.");
            return;
        }

        log.info("[DataSeeder] Seeding database with sample data …");

        // ── 1. Stocks ─────────────────────────────────────────────────────────
        // stockRepo.save() issues an INSERT and returns the managed entity with
        // its auto-generated id populated — we keep the reference so we can
        // attach it to holdings and transactions below.
        Stock aapl = stockRepo.save(new Stock("AAPL", "Apple Inc.",
                new BigDecimal("189.30"), "Technology"));
        Stock msft = stockRepo.save(new Stock("MSFT", "Microsoft Corp.",
                new BigDecimal("415.50"), "Technology"));
        Stock jpm  = stockRepo.save(new Stock("JPM",  "JPMorgan Chase & Co.",
                new BigDecimal("198.75"), "Financials"));
        Stock nvda = stockRepo.save(new Stock("NVDA", "NVIDIA Corporation",
                new BigDecimal("875.40"), "Technology"));
        Stock ko   = stockRepo.save(new Stock("KO",   "The Coca-Cola Company",
                new BigDecimal("62.15"),  "Consumer Staples"));

        log.info("[DataSeeder] ✓ Stocks saved: {}", stockRepo.count());

        // ── 2. Portfolio → Holdings → Transactions ────────────────────────────
        Portfolio portfolio = new Portfolio("My Tech Portfolio");

        // Three holdings — one per stock position currently held.
        // Portfolio.addHolding() keeps the bidirectional link in sync:
        //   • adds the Holding to portfolio.holdings  (so CascadeType.ALL can reach it)
        //   • sets holding.portfolio = this           (so the FK column gets a value)
        // Without both steps, Hibernate either misses the cascade or writes a NULL FK.
        Holding hAapl = new Holding(aapl, 10, new BigDecimal("150.00")); // 10 shares @ $150
        Holding hMsft = new Holding(msft,  5, new BigDecimal("310.00")); //  5 shares @ $310
        Holding hJpm  = new Holding(jpm,   8, new BigDecimal("185.00")); //  8 shares @ $185
        portfolio.addHolding(hAapl);
        portfolio.addHolding(hMsft);
        portfolio.addHolding(hJpm);

        // Transactions: three BUYs that opened the positions, one SELL that
        // partially exited AAPL.  Having both BUY and SELL rows lets us verify
        // that @Enumerated(EnumType.STRING) stores "BUY"/"SELL", not 0/1.
        portfolio.addTransaction(new Transaction(aapl, TransactionType.BUY,  10, new BigDecimal("150.00")));
        portfolio.addTransaction(new Transaction(msft, TransactionType.BUY,   5, new BigDecimal("310.00")));
        portfolio.addTransaction(new Transaction(jpm,  TransactionType.BUY,   8, new BigDecimal("185.00")));
        portfolio.addTransaction(new Transaction(aapl, TransactionType.SELL,  3, new BigDecimal("189.30")));
        // Note: in a real app the service layer would also reduce hAapl.quantity to 7.

        // One portfolioRepo.save() cascades to ALL 3 holdings and ALL 4 transactions
        // because of CascadeType.ALL + orphanRemoval on both @OneToMany mappings.
        portfolioRepo.save(portfolio);
        log.info("[DataSeeder] ✓ Portfolio '{}' saved → 3 holdings, 4 transactions",
                portfolio.getName());

        // ── 3. Watchlist (many-to-many with Stock) ────────────────────────────
        // Watchlist.addStock() calls stocks.add(stock) on the owning Set.
        // Because Watchlist owns the @JoinTable, saving the Watchlist causes
        // Hibernate to INSERT rows into watchlist_stocks.
        // No CascadeType on stocks — the Stock rows already exist and must NOT
        // be deleted if the watchlist is removed.
        Watchlist aiWatch = new Watchlist("AI & Semiconductors");
        aiWatch.addStock(nvda);  // ← row 1 in watchlist_stocks
        aiWatch.addStock(msft);  // ← row 2
        watchlistRepo.save(aiWatch);

        log.info("[DataSeeder] ✓ Watchlist '{}' saved with {} stocks",
                aiWatch.getName(), 2);
        log.info("[DataSeeder] ✓ Database seeding complete.");
    }
}

