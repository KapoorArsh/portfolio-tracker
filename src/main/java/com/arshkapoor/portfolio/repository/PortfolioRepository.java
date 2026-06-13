package com.arshkapoor.portfolio.repository;

import com.arshkapoor.portfolio.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data-access interface for Portfolio.
 *
 * The key queries here are the two JOIN FETCH variants.  Because Holdings and
 * Transactions are mapped LAZY, calling portfolio.getHoldings() outside of an
 * active Hibernate session throws LazyInitializationException.  The JOIN FETCH
 * queries solve this by loading the collection in the same SELECT, so the caller
 * gets a fully-populated Portfolio without needing an open session.
 */
@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    Optional<Portfolio> findByName(String name);

    /**
     * JOIN FETCH forces Hibernate to write:
     *   SELECT p.*, h.* FROM portfolios p LEFT JOIN holdings h ON h.portfolio_id = p.id
     *   WHERE p.id = :id
     *
     * "LEFT JOIN FETCH" (not inner) means a Portfolio with zero holdings is still
     * returned rather than no result at all.
     *
     * Use this variant specifically in service methods that need to display
     * or iterate over holdings — it prevents N+1 queries.
     */
    @Query("SELECT p FROM Portfolio p LEFT JOIN FETCH p.holdings WHERE p.id = :id")
    Optional<Portfolio> findByIdWithHoldings(Long id);

    /**
     * Same pattern for transactions — use when building a transaction-history view.
     * Note: you cannot JOIN FETCH two separate collections (holdings AND transactions)
     * in a single query without risk of a Cartesian product.  Fetch them in two
     * separate queries via EntityGraph or two @Query methods like these.
     */
    @Query("SELECT p FROM Portfolio p LEFT JOIN FETCH p.transactions WHERE p.id = :id")
    Optional<Portfolio> findByIdWithTransactions(Long id);
}

