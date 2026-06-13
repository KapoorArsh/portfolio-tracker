package com.arshkapoor.portfolio;

import com.arshkapoor.portfolio.entity.Stock;
import com.arshkapoor.portfolio.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Seeds the core stock catalogue into the database on every startup.
 *
 * This component has NO @Profile annotation, so it runs in ALL Spring profiles
 * (dev, prod, test, etc.). It ensures that production instances on Render always
 * have a working stock dropdown, even on first boot or after a restart that
 * clears ephemeral storage.
 *
 * Idempotency guard: before creating each stock, we check if it already exists
 * by ticker. This prevents duplicate rows on application restart (important on
 * Render where the container may be restarted multiple times).
 *
 * The stock catalogue is reference data with no parent–child relationships,
 * so it is safe to seed unconditionally in production.
 */
@Component
public class StockCatalogueSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StockCatalogueSeeder.class);

    private final StockRepository stockRepository;

    public StockCatalogueSeeder(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[StockCatalogueSeeder] Ensuring stock catalogue is populated …");

        // Define the core stock catalogue: well-known, stable companies across sectors
        final Stock[] catalogue = {
            new Stock("AAPL", "Apple Inc.", new BigDecimal("189.30"), "Technology"),
            new Stock("MSFT", "Microsoft Corp.", new BigDecimal("415.50"), "Technology"),
            new Stock("GOOGL", "Alphabet Inc.", new BigDecimal("178.40"), "Technology"),
            new Stock("JPM", "JPMorgan Chase & Co.", new BigDecimal("198.75"), "Financials"),
            new Stock("JNJ", "Johnson & Johnson", new BigDecimal("159.20"), "Healthcare")
        };

        int savedCount = 0;

        for (Stock stock : catalogue) {
            // Idempotency check: if this ticker already exists, skip it
            if (stockRepository.existsByTickerIgnoreCase(stock.getTicker())) {
                log.debug("[StockCatalogueSeeder] {} already exists — skipping", stock.getTicker());
                continue;
            }

            stockRepository.save(stock);
            savedCount++;
            log.debug("[StockCatalogueSeeder] ✓ Saved stock: {} ({})", stock.getTicker(), stock.getName());
        }

        if (savedCount > 0) {
            log.info("[StockCatalogueSeeder] ✓ Catalogue seeding complete: {} new stocks added", savedCount);
        } else {
            log.info("[StockCatalogueSeeder] ✓ Catalogue already populated: {} stocks in database",
                    stockRepository.count());
        }
    }
}
