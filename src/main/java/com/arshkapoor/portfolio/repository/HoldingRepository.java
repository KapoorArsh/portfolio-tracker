package com.arshkapoor.portfolio.repository;

import com.arshkapoor.portfolio.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data-access interface for Holding.
 *
 * The most critical query in this repository is findByPortfolioIdWithStock:
 * it prevents the N+1 select problem that would occur if you loaded a list of
 * holdings and then accessed holding.getStock() inside a loop.
 */
@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    /**
     * All holdings in a portfolio using the FK value directly.
     * Spring Data translates "ByPortfolioId" to WHERE portfolio_id = :portfolioId
     * without you having to join the portfolios table.
     */
    List<Holding> findByPortfolioId(Long portfolioId);

    /**
     * Look up a specific position by both FKs.
     * The service layer calls this before deciding whether to INSERT a new
     * Holding or UPDATE an existing one after a BUY transaction.
     */
    Optional<Holding> findByPortfolioIdAndStockId(Long portfolioId, Long stockId);

    /**
     * JOIN FETCH the associated Stock in the same SELECT.
     *
     * Without this, iterating the returned list and calling h.getStock().getTicker()
     * would fire one additional SELECT per holding row — a classic N+1.
     * Use this method wherever the UI needs to display stock tickers alongside
     * holding data.
     */
    @Query("SELECT h FROM Holding h JOIN FETCH h.stock WHERE h.portfolio.id = :portfolioId")
    List<Holding> findByPortfolioIdWithStock(Long portfolioId);

    /**
     * Bulk-delete a position when a user removes a stock from their portfolio.
     * Spring Data derives the DELETE statement from the method name — no @Query needed.
     */
    void deleteByPortfolioIdAndStockId(Long portfolioId, Long stockId);
}

