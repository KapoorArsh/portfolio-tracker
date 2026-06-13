package com.arshkapoor.portfolio.repository;

import com.arshkapoor.portfolio.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data-access interface for Stock.
 *
 * Extending JpaRepository<Stock, Long> gives us ~18 free methods
 * (save, findById, findAll, delete, count, exists, …) via a proxy that Spring
 * Data generates at startup — zero boilerplate SQL or DAO classes needed.
 *
 * @Repository is technically optional (Spring detects repository interfaces
 * automatically via component scanning) but is included so that Spring's
 * PersistenceExceptionTranslationPostProcessor wraps raw JPA exceptions
 * (PersistenceException) into Spring's unified DataAccessException hierarchy —
 * making error handling consistent across every layer of the app.
 */
@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * Derived query — Spring Data parses "findBy + Ticker" and generates:
     *   SELECT s FROM Stock s WHERE s.ticker = ?1
     * No @Query needed; the method name is the specification.
     */
    Optional<Stock> findByTickerIgnoreCase(String ticker);

    /**
     * Case-insensitive sector filter for the sector-summary page.
     * "IgnoreCase" appended to a derived method name translates to
     * LOWER(sector) = LOWER(?1) in the generated JPQL.
     */
    List<Stock> findBySectorIgnoreCase(String sector);

    /**
     * Full-text search helper for the stock-search box.
     * Spring Data ORs the two clauses automatically when you list two
     * parameters with "Or" in the method name.
     */
    List<Stock> findByTickerContainingIgnoreCaseOrNameContainingIgnoreCase(
            String ticker, String name);

    /** Cheap existence check — SELECT 1 … LIMIT 1, no entity hydration. */
    boolean existsByTickerIgnoreCase(String ticker);

    /**
     * Explicit @Query used here purely for documentation: shows what a
     * raw JPQL query looks like when derived method names become unreadable.
     * nativeQuery = false (default) means this is JPQL, not SQL, so it is
     * database-agnostic and works against both H2 and PostgreSQL.
     */
    @Query("SELECT s FROM Stock s ORDER BY s.ticker ASC")
    List<Stock> findAllOrderedByTicker();
}

