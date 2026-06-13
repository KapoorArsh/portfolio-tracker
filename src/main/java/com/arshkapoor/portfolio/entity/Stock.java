package com.arshkapoor.portfolio.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents a publicly-traded stock — the shared reference table of the system.
 *
 * Stock is intentionally a "leaf" entity: it owns no collection mappings.
 * Many Portfolios and Watchlists point to the same Stock row, so cascading
 * deletes from here would be catastrophic.  Relations are declared on the
 * *other* side (Holding, Transaction, Watchlist).
 */
@Entity
@Table(
    name = "stocks",
    uniqueConstraints = @UniqueConstraint(
        columnNames = "ticker",
        name         = "uq_stock_ticker"   // named constraint → readable in error messages
    )
)
public class Stock {

    /**
     * Surrogate primary key.
     * IDENTITY strategy delegates ID generation to the database auto-increment
     * column.  It is simpler than SEQUENCE for H2/PostgreSQL and avoids the
     * extra "select nextval(…)" round-trip that GenerationType.SEQUENCE adds
     * before the INSERT.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Exchange ticker symbol, e.g. "AAPL".
     * unique = true creates a DB-level unique index — the last line of defence
     * if two threads race past an application-level duplicate check.
     * length = 10 covers the longest realistic tickers (e.g. "BRK.B").
     */
    @NotBlank(message = "Ticker must not be blank")
    @Size(max = 10, message = "Ticker must be 10 characters or fewer")
    @Column(nullable = false, unique = true, length = 10)
    private String ticker;

    @NotBlank(message = "Name must not be blank")
    @Column(nullable = false)
    private String name;

    /**
     * WHY BigDecimal, NOT double/float:
     * IEEE-754 floating-point cannot represent 0.1 exactly; 0.1 + 0.2 evaluates
     * to 0.30000000000000004.  For any monetary value, use BigDecimal so that
     * arithmetic is exact.
     *
     * precision = 19, scale = 4: supports prices up to $999,999,999,999,999.9999,
     * which is more than sufficient and matches standard financial DB column sizing.
     */
    @NotNull(message = "Current price must not be null")
    @DecimalMin(value = "0.0001", message = "Price must be greater than zero")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @NotBlank(message = "Sector must not be blank")
    @Column(nullable = false)
    private String sector;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * No-arg constructor required by the JPA specification: the provider
     * (Hibernate) instantiates entities via reflection when hydrating query
     * results, so it needs a zero-argument constructor it can call.
     * protected (not private) so Hibernate's byte-buddy proxying still works.
     */
    protected Stock() {}

    public Stock(String ticker, String name, BigDecimal currentPrice, String sector) {
        this.ticker       = ticker.toUpperCase().trim();
        this.name         = name;
        this.currentPrice = currentPrice;
        this.sector       = sector;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long       getId()           { return id; }

    public String     getTicker()       { return ticker; }
    /** Normalise to upper-case on every write so "aapl" and "AAPL" are the same. */
    public void       setTicker(String ticker) { this.ticker = ticker.toUpperCase().trim(); }

    public String     getName()         { return name; }
    public void       setName(String name) { this.name = name; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void       setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public String     getSector()       { return sector; }
    public void       setSector(String sector) { this.sector = sector; }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    /**
     * Based on the natural business key (ticker), NOT the generated id.
     * This is the recommended approach for entities that have a meaningful
     * unique field: two Stock objects with the same ticker are the same stock
     * regardless of which JVM session created them.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stock other)) return false;
        return ticker != null && ticker.equalsIgnoreCase(other.ticker);
    }

    /** Constant hash so that unsaved entities (id == null) are safe in HashSets. */
    @Override
    public int hashCode() {
        return Objects.hash(ticker == null ? null : ticker.toUpperCase());
    }

    @Override
    public String toString() {
        return "Stock{id=%d, ticker='%s', name='%s', sector='%s', currentPrice=%s}"
                .formatted(id, ticker, name, sector, currentPrice);
    }
}

