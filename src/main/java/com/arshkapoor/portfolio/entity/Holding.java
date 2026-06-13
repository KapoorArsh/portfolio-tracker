package com.arshkapoor.portfolio.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Represents a current stock position inside a Portfolio: how many shares
 * are held and at what weighted-average cost basis.
 *
 * Holding is the "many" side (owning side) of two relationships:
 *   - many Holdings → one Portfolio  (FK: portfolio_id)
 *   - many Holdings → one Stock      (FK: stock_id)
 *
 * Because Holding owns both foreign keys, it is responsible for writing them
 * to the database.  The Portfolio side uses mappedBy to acknowledge this.
 *
 * The composite unique constraint prevents a portfolio from having two separate
 * rows for the same stock — the service layer must merge (update quantity/price)
 * instead of inserting a duplicate.
 */
@Entity
@Table(
    name = "holdings",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"portfolio_id", "stock_id"},
        name        = "uq_holding_portfolio_stock"
    ),
    indexes = {
        @Index(name = "idx_holding_portfolio_id", columnList = "portfolio_id"),
        @Index(name = "idx_holding_stock_id",     columnList = "stock_id")
    }
)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Quantity must not be null")
    @Positive(message = "Quantity must be greater than zero")
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Weighted-average cost per share across all BUY transactions for this stock.
     * Stored as BigDecimal — see Stock.currentPrice for the full rationale.
     */
    @NotNull(message = "Average buy price must not be null")
    @DecimalMin(value = "0.0001", message = "Average buy price must be greater than zero")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal avgBuyPrice;

    // ── Relationships ─────────────────────────────────────────────────────────

    /**
     * FetchType.LAZY — overrides the @ManyToOne default of EAGER.
     *
     * WHY override the default? Queries like "load all holdings for a portfolio"
     * return N rows.  With EAGER, each row would trigger a separate SELECT for
     * its Portfolio — that is the classic N+1 query problem.  With LAZY, no
     * Portfolio SELECTs are issued unless the caller explicitly accesses
     * holding.getPortfolio().  The repository provides a JOIN FETCH query for
     * callers that genuinely need both objects.
     *
     * optional = false tells Hibernate that portfolio_id is never NULL, allowing
     * it to use an INNER JOIN instead of a LEFT JOIN when it does fetch.
     */
    @NotNull(message = "Portfolio must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    /**
     * LAZY for the same N+1 reason as portfolio above.
     * The repository provides findByPortfolioIdWithStock() which JOIN FETCHes
     * the stock when the caller needs ticker/name alongside the holding data.
     */
    @NotNull(message = "Stock must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Holding() {}

    /**
     * Convenience constructor for direct construction.
     * portfolio is intentionally absent — always call Portfolio.addHolding(this)
     * after construction so both sides of the bidirectional link are in sync.
     */
    public Holding(Stock stock, Integer quantity, BigDecimal avgBuyPrice) {
        this.stock       = stock;
        this.quantity    = quantity;
        this.avgBuyPrice = avgBuyPrice;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long       getId()           { return id; }

    public Integer    getQuantity()     { return quantity; }
    public void       setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getAvgBuyPrice()  { return avgBuyPrice; }
    public void       setAvgBuyPrice(BigDecimal avgBuyPrice) { this.avgBuyPrice = avgBuyPrice; }

    public Portfolio  getPortfolio()    { return portfolio; }
    /** Called by Portfolio.addHolding / removeHolding — do not call directly. */
    public void       setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }

    public Stock      getStock()        { return stock; }
    public void       setStock(Stock stock) { this.stock = stock; }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Holding other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Holding{id=%d, quantity=%d, avgBuyPrice=%s}".formatted(id, quantity, avgBuyPrice);
    }
}

