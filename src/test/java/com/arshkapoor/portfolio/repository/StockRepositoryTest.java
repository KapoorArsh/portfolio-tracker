package com.arshkapoor.portfolio.repository;

import com.arshkapoor.portfolio.entity.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4.x moved @DataJpaTest to the data-jpa-test module with a new package.
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Slice test that boots ONLY the JPA layer — no web stack, no @Service beans,
 * no DataSeeder.  Spring replaces the configured datasource with an isolated
 * in-memory H2 so these tests never touch the dev/prod database.
 *
 * @DataJpaTest automatically wraps every @Test in a transaction that is ROLLED
 * BACK after the method, so data saved in test A is invisible to test B.
 * This gives full isolation without recreating the schema between tests.
 *
 * What we're verifying:
 *   • The JPQL in @Query methods is syntactically valid (Hibernate parses it at
 *     startup — a bad query throws immediately and all tests fail).
 *   • Derived query method name parsing is correct (wrong property name → startup fail).
 *   • Unique-constraint DDL is applied by Hibernate (duplicate ticker → exception).
 */
@DataJpaTest
@DisplayName("StockRepository — JPA slice tests")
class StockRepositoryTest {

    @Autowired
    private StockRepository stockRepo;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Stock saveStock(String ticker, String name, String price, String sector) {
        return stockRepo.save(new Stock(ticker, name, new BigDecimal(price), sector));
    }

    // ── findByTickerIgnoreCase ────────────────────────────────────────────────

    @Test
    @DisplayName("findByTickerIgnoreCase: returns the stock regardless of input case")
    void findByTickerIgnoreCase_returnsStock_forLowerInput() {
        saveStock("AAPL", "Apple Inc.", "189.30", "Technology");

        Optional<Stock> found = stockRepo.findByTickerIgnoreCase("aapl");

        assertThat(found).isPresent();
        assertThat(found.get().getTicker()).isEqualTo("AAPL"); // stored upper-case
        assertThat(found.get().getName()).isEqualTo("Apple Inc.");
    }

    @Test
    @DisplayName("findByTickerIgnoreCase: returns empty for an unknown ticker")
    void findByTickerIgnoreCase_returnsEmpty_forUnknownTicker() {
        assertThat(stockRepo.findByTickerIgnoreCase("GOOG")).isEmpty();
    }

    // ── existsByTickerIgnoreCase ──────────────────────────────────────────────

    @Test
    @DisplayName("existsByTickerIgnoreCase: true for a saved ticker, false otherwise")
    void existsByTickerIgnoreCase_correctResults() {
        saveStock("MSFT", "Microsoft", "415.50", "Technology");

        assertThat(stockRepo.existsByTickerIgnoreCase("msft")).isTrue();
        assertThat(stockRepo.existsByTickerIgnoreCase("MSFT")).isTrue();
        assertThat(stockRepo.existsByTickerIgnoreCase("GOOG")).isFalse();
    }

    // ── findBySectorIgnoreCase ────────────────────────────────────────────────

    @Test
    @DisplayName("findBySectorIgnoreCase: returns only stocks in the matching sector")
    void findBySectorIgnoreCase_returnsOnlyMatchingSector() {
        saveStock("AAPL", "Apple Inc.",          "189.30", "Technology");
        saveStock("MSFT", "Microsoft Corp.",     "415.50", "Technology");
        saveStock("JPM",  "JPMorgan Chase",      "198.75", "Financials");

        List<Stock> techStocks = stockRepo.findBySectorIgnoreCase("technology");

        assertThat(techStocks).hasSize(2);
        assertThat(techStocks)
                .extracting(Stock::getTicker)
                .containsExactlyInAnyOrder("AAPL", "MSFT");
    }

    @Test
    @DisplayName("findBySectorIgnoreCase: returns empty list for unknown sector")
    void findBySectorIgnoreCase_returnsEmpty_forUnknownSector() {
        assertThat(stockRepo.findBySectorIgnoreCase("Healthcare")).isEmpty();
    }

    // ── findAllOrderedByTicker ────────────────────────────────────────────────

    @Test
    @DisplayName("findAllOrderedByTicker: result is alphabetically sorted")
    void findAllOrderedByTicker_isSortedAlphabetically() {
        // Save out of order intentionally
        saveStock("MSFT", "Microsoft",  "415.50", "Technology");
        saveStock("AAPL", "Apple Inc.", "189.30", "Technology");
        saveStock("JPM",  "JPMorgan",   "198.75", "Financials");

        List<Stock> ordered = stockRepo.findAllOrderedByTicker();

        assertThat(ordered)
                .extracting(Stock::getTicker)
                .containsExactly("AAPL", "JPM", "MSFT");  // strict order
    }

    // ── unique constraint ─────────────────────────────────────────────────────

    @Test
    @DisplayName("unique-ticker constraint: duplicate INSERT throws an exception")
    void uniqueTickerConstraint_duplicateTicker_throwsException() {
        saveStock("AAPL", "Apple Inc.", "189.30", "Technology");

        // saveAndFlush forces Hibernate to flush to the DB immediately so the
        // constraint violation is thrown inside this test (not deferred to commit).
        assertThatThrownBy(() ->
                stockRepo.saveAndFlush(
                        new Stock("AAPL", "Apple Duplicate", new BigDecimal("200.00"), "Technology")))
                .isInstanceOf(Exception.class);  // DataIntegrityViolationException or ConstraintViolationException
    }

    // ── full-text search ──────────────────────────────────────────────────────

    @Test
    @DisplayName("search by ticker or name: OR clause returns matches from either field")
    void searchByTickerOrName_returnsMatchesFromEitherField() {
        saveStock("AAPL", "Apple Inc.",       "189.30", "Technology");
        saveStock("MSFT", "Microsoft Corp.",  "415.50", "Technology");
        saveStock("NVDA", "NVIDIA Corporation","875.40", "Technology");

        // "apple" matches AAPL's name; "NV" matches NVDA's ticker
        List<Stock> results = stockRepo
                .findByTickerContainingIgnoreCaseOrNameContainingIgnoreCase("NV", "apple");

        assertThat(results)
                .extracting(Stock::getTicker)
                .containsExactlyInAnyOrder("AAPL", "NVDA");
    }
}

