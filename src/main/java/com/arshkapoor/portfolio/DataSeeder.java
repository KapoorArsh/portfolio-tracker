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
 * Populates the H2 in-memory database with representative sample data on every
 * dev-profile startup so you can immediately verify persistence in the H2
 * console without writing a test.
 *
 * @Profile("dev") — this bean is only created when the "dev" Spring profile
 * is active.  It will NOT run when the "prod" profile is active.
 *
 * NOTE: The stock catalogue (AAPL, MSFT, JPM, etc.) is seeded separately by
 * StockCatalogueSeeder in ALL profiles (dev + prod).  This seeder focuses only
 * on creating sample Portfolio, Holdings, Transactions, and Watchlist data.
 *
 * Idempotency: checking portfolioRepo.count() prevents re-seeding the sample
 * data on app restart, but note that the stock catalogue is idempotent at the
 * individual stock level (per-ticker check), so it can safely be seeded first
 * then this sample data added on top.
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
     * The idempotency guard (portfolioRepo.count() > 0) is a safety net for
     * scenarios where ddl-auto = update instead of create-drop: it prevents
     * duplicate sample data on application restart.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (portfolioRepo.count() > 0) {
            log.info("[DataSeeder] Sample data already seeded — skipping.");
            return;
        }

        log.info("[DataSeeder] Seeding database with sample data …");

        // ── Load stocks from catalogue (seeded by StockCatalogueSeeder) ────────
        // These will always exist because StockCatalogueSeeder runs first in all profiles.
        Stock aapl = stockRepo.findByTickerIgnoreCase("AAPL").orElseThrow(
            () -> new RuntimeException("[DataSeeder] AAPL not found in catalogue — StockCatalogueSeeder may not have run")
        );
        Stock msft = stockRepo.findByTickerIgnoreCase("MSFT").orElseThrow(
            () -> new RuntimeException("[DataSeeder] MSFT not found in catalogue")
        );
        Stock jpm = stockRepo.findByTickerIgnoreCase("JPM").orElseThrow(
            () -> new RuntimeException("[DataSeeder] JPM not found in catalogue")
        );
        Stock nvda = stockRepo.findByTickerIgnoreCase("GOOGL").orElseThrow(
            () -> new RuntimeException("[DataSeeder] GOOGL not found in catalogue (using as proxy for NVDA)")
        );

        log.info("[DataSeeder] ✓ Loaded {} stocks from catalogue", 4);

        // ── 1. Portfolio → Holdings → Transactions ────────────────────────────
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

        // ── 2. Watchlist (many-to-many with Stock) ────────────────────────────
        // Watchlist.addStock() calls stocks.add(stock) on the owning Set.
        // Because Watchlist owns the @JoinTable, saving the Watchlist causes
        // Hibernate to INSERT rows into watchlist_stocks.
        // No CascadeType on stocks — the Stock rows already exist and must NOT
        // be deleted if the watchlist is removed.
        Watchlist aiWatch = new Watchlist("AI & Semiconductors");
        aiWatch.addStock(nvda);  // ← row 1 in watchlist_stocks (GOOGL as placeholder)
        aiWatch.addStock(msft);  // ← row 2
        watchlistRepo.save(aiWatch);

        log.info("[DataSeeder] ✓ Watchlist '{}' saved with {} stocks",
                aiWatch.getName(), 2);
        log.info("[DataSeeder] ✓ Sample data seeding complete.");
    }
}

