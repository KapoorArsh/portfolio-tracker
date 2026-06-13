package com.arshkapoor.portfolio.repository;

import com.arshkapoor.portfolio.entity.Transaction;
import com.arshkapoor.portfolio.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data-access interface for Transaction.
 *
 * Transactions are append-only: the repository exposes no update methods
 * (JpaRepository.save() on an existing entity would update it, but the service
 * layer is responsible for never calling it that way).
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Full transaction history for a portfolio, newest first.
     * "OrderByTimestampDesc" appended to the derived method name adds
     * ORDER BY t.timestamp DESC — no @Query annotation needed.
     */
    List<Transaction> findByPortfolioIdOrderByTimestampDesc(Long portfolioId);

    /**
     * Filter history by BUY or SELL — useful for "show me only purchases"
     * or for the cost-basis calculation (sum of BUY transactions).
     */
    List<Transaction> findByPortfolioIdAndTypeOrderByTimestampDesc(
            Long portfolioId, TransactionType type);

    /** All transactions for a specific stock in a portfolio — for per-stock drill-down. */
    List<Transaction> findByPortfolioIdAndStockIdOrderByTimestampDesc(
            Long portfolioId, Long stockId);

    /**
     * Admin/audit view: all transactions across all portfolios with their stock
     * and portfolio details loaded in a single query.
     *
     * JOIN FETCH (not LEFT JOIN FETCH) because every transaction is guaranteed
     * to have a non-null stock and portfolio — an inner join is slightly more
     * efficient and makes the intent explicit.
     *
     * Text block (Java 15+) used for readability; the JPQL itself is standard.
     */
    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.stock
            JOIN FETCH t.portfolio
            ORDER BY t.timestamp DESC
            """)
    List<Transaction> findAllWithStockAndPortfolio();
}

