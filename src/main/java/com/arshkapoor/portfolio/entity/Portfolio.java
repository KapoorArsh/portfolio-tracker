package com.arshkapoor.portfolio.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An aggregate root that represents a named collection of stock positions.
 *
 * Portfolio is the "one" side of two one-to-many relationships (Holdings and
 * Transactions).  Because it owns those relationships via CascadeType.ALL +
 * orphanRemoval, saving or deleting a Portfolio automatically propagates to
 * its children — there is no need to call save() on individual Holdings or
 * Transactions.
 */
@Entity
@Table(name = "portfolios")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Portfolio name must not be blank")
    @Column(nullable = false)
    private String name;

    /**
     * updatable = false tells Hibernate to NEVER include this column in an
     * UPDATE statement.  Combined with @PrePersist, this makes createdAt
     * effectively immutable at the database level — even if someone calls
     * setCreatedAt() in Java, the value in the DB never changes after INSERT.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Relationships ─────────────────────────────────────────────────────────

    /**
     * FetchType.LAZY — the JPA default for @OneToMany, and the right choice here.
     *
     * If this were EAGER, every single "SELECT * FROM portfolios" would join and
     * load all holdings rows — potentially thousands of rows — even when you only
     * need the portfolio's name.  LAZY defers that SELECT until code actually calls
     * getHoldings(), and lets the service layer use JOIN FETCH queries when it
     * needs both together in one round-trip.
     *
     * mappedBy = "portfolio" tells Hibernate that the FOREIGN KEY column lives on
     * the Holding table (Holding is the "owning" side).  Without mappedBy, JPA
     * would create a spurious join table between portfolios and holdings.
     *
     * orphanRemoval = true: if a Holding is removed from this list, Hibernate
     * issues a DELETE for that row.  Without it, the row would become an orphan
     * (portfolio_id = NULL or stale value) rather than being cleaned up.
     */
    @OneToMany(
        mappedBy      = "portfolio",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    private List<Holding> holdings = new ArrayList<>();

    /**
     * Same reasoning as holdings: LAZY + ALL cascade + orphanRemoval.
     * Transactions are portfolio-scoped records with no independent lifecycle.
     */
    @OneToMany(
        mappedBy      = "portfolio",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    private List<Transaction> transactions = new ArrayList<>();

    // ── Lifecycle hook ────────────────────────────────────────────────────────

    /**
     * @PrePersist fires just before the first INSERT.
     * Setting the timestamp here (not in the constructor) means it reflects the
     * moment the record is actually persisted, not when the Java object was new'd.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Portfolio() {}

    public Portfolio(String name) {
        this.name = name;
    }

    // ── Bidirectional relationship helpers ────────────────────────────────────

    /**
     * Always use these helpers instead of calling getHoldings().add() directly.
     *
     * A bidirectional JPA association has two Java references: Portfolio.holdings
     * and Holding.portfolio.  Both must point to each other or Hibernate's
     * first-level (session) cache returns stale data within the same transaction.
     * These helpers keep both sides in sync in one call.
     */
    public void addHolding(Holding holding) {
        holdings.add(holding);
        holding.setPortfolio(this);
    }

    public void removeHolding(Holding holding) {
        holdings.remove(holding);
        holding.setPortfolio(null);
    }

    /** See addHolding — same synchronisation rationale for transactions. */
    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        transaction.setPortfolio(this);
    }

    public void removeTransaction(Transaction transaction) {
        transactions.remove(transaction);
        transaction.setPortfolio(null);
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long            getId()           { return id; }

    public String          getName()         { return name; }
    public void            setName(String name) { this.name = name; }

    public LocalDateTime   getCreatedAt()    { return createdAt; }

    public List<Holding>     getHoldings()     { return holdings; }
    public List<Transaction> getTransactions() { return transactions; }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    /**
     * Portfolio has no natural business key, so equality is based on the DB id.
     * The null guard (id != null) ensures two unsaved Portfolio objects are never
     * accidentally considered equal just because both have id == null.
     * hashCode returns a class-level constant so the contract
     * (equal objects must have equal hash codes) is never broken.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Portfolio other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Portfolio{id=%d, name='%s', createdAt=%s}".formatted(id, name, createdAt);
    }
}

