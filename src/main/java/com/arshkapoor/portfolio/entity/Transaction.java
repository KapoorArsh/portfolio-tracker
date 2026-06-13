package com.arshkapoor.portfolio.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An immutable audit record of a single BUY or SELL event.
 *
 * Design principle: transactions are append-only.  We never UPDATE a transaction
 * row; a correction is modelled as a new offsetting transaction.  The
 * updatable = false on timestamp enforces this at the DB level.
 *
 * Like Holding, Transaction is the owning side of two @ManyToOne relationships
 * and holds both foreign key columns (portfolio_id, stock_id).
 */
@Entity
@Table(
    name = "stock_transactions",    // "transactions" can conflict with the SQL keyword in some dialects
    indexes = {
        @Index(name = "idx_txn_portfolio_id",        columnList = "portfolio_id"),
        @Index(name = "idx_txn_portfolio_timestamp",  columnList = "portfolio_id, timestamp")
    }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @Enumerated(EnumType.STRING) persists the literal string "BUY" or "SELL".
     * EnumType.ORDINAL (the JPA default) would store 0 or 1 — adding a new
     * constant between them silently re-numbers everything and corrupts history.
     * Always use STRING for enums stored in the database.
     */
    @NotNull(message = "Transaction type must not be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 4)
    private TransactionType type;

    @NotNull(message = "Quantity must not be null")
    @Positive(message = "Quantity must be greater than zero")
    @Column(nullable = false)
    private Integer quantity;

    /** Price per share at the time of the transaction. BigDecimal — see Stock for rationale. */
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.0001", message = "Price must be greater than zero")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    /**
     * updatable = false makes this column write-once: Hibernate never generates
     * an UPDATE for it.  @PrePersist fills it automatically when left null,
     * but it CAN be set explicitly (e.g. for importing historical data).
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    // ── Relationships ─────────────────────────────────────────────────────────

    /**
     * Both associations are LAZY for the same reason as in Holding:
     * loading a transaction list must not fan out into N additional SELECTs.
     * The repository's findAllWithStockAndPortfolio() uses JOIN FETCH for views
     * that need the full picture.
     */
    @NotNull(message = "Portfolio must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @NotNull(message = "Stock must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    // ── Lifecycle hook ────────────────────────────────────────────────────────

    /**
     * Auto-assign timestamp on first persist only if not already set.
     * The null check preserves explicitly-set timestamps (back-dated imports).
     */
    @PrePersist
    protected void onPersist() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Transaction() {}

    /**
     * portfolio is absent — call Portfolio.addTransaction(this) after construction
     * to keep the bidirectional association in sync on both sides.
     */
    public Transaction(Stock stock, TransactionType type, Integer quantity, BigDecimal price) {
        this.stock    = stock;
        this.type     = type;
        this.quantity = quantity;
        this.price    = price;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long            getId()        { return id; }

    public TransactionType getType()      { return type; }
    public void            setType(TransactionType type) { this.type = type; }

    public Integer         getQuantity()  { return quantity; }
    public void            setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal      getPrice()     { return price; }
    public void            setPrice(BigDecimal price) { this.price = price; }

    public LocalDateTime   getTimestamp() { return timestamp; }
    /** Allows back-dating; once persisted, the column is not updated. */
    public void            setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Portfolio       getPortfolio() { return portfolio; }
    /** Called by Portfolio.addTransaction / removeTransaction — do not call directly. */
    public void            setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }

    public Stock           getStock()     { return stock; }
    public void            setStock(Stock stock) { this.stock = stock; }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Transaction{id=%d, type=%s, quantity=%d, price=%s, timestamp=%s}"
                .formatted(id, type, quantity, price, timestamp);
    }
}

