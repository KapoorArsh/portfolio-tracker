package com.arshkapoor.portfolio.repository;

import com.arshkapoor.portfolio.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data-access interface for Watchlist.
 *
 * The stocks collection is LAZY, so the JOIN FETCH variant is essential for any
 * view that needs to render the watchlist's stock tickers — calling
 * watchlist.getStocks() outside a transaction would otherwise throw
 * LazyInitializationException.
 */
@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    Optional<Watchlist> findByName(String name);

    /**
     * Loads the watchlist AND its stocks collection in a single SQL query.
     * LEFT JOIN FETCH means an empty watchlist (no stocks added yet) is still
     * returned rather than absent.
     */
    @Query("SELECT w FROM Watchlist w LEFT JOIN FETCH w.stocks WHERE w.id = :id")
    Optional<Watchlist> findByIdWithStocks(Long id);

    /**
     * Find every watchlist that contains a given stock.
     * Useful for a "which watchlists track AAPL?" feature and for cleanup
     * before deleting a stock from the system.
     */
    @Query("SELECT w FROM Watchlist w JOIN w.stocks s WHERE s.id = :stockId")
    List<Watchlist> findAllContainingStock(Long stockId);

    /**
     * Load ALL watchlists with their stocks collections in a single query.
     * DISTINCT prevents duplicate Watchlist instances when a watchlist has
     * multiple stocks — without it Hibernate returns one row per (watchlist, stock)
     * pair and the Java result list contains the same Watchlist object repeated.
     */
    @Query("SELECT DISTINCT w FROM Watchlist w LEFT JOIN FETCH w.stocks")
    List<Watchlist> findAllWithStocks();
}

