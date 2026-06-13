package com.arshkapoor.portfolio.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.util.HashSet;
import java.util.Set;

/**
 * A named list of stocks a user wants to monitor without necessarily holding them.
 *
 * The many-to-many association is owned by Watchlist (Watchlist side defines the
 * @JoinTable).  This means:
 *   - Adding/removing a stock from a watchlist is done here.
 *   - Deleting a Watchlist removes its join-table rows, but the Stock rows survive.
 *   - Stock has no "watchlists" back-reference — the relationship is unidirectional,
 *     keeping Stock clean as a reference entity.
 */
@Entity
@Table(name = "watchlists")
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Watchlist name must not be blank")
    @Column(nullable = false)
    private String name;

    // ── Relationship ──────────────────────────────────────────────────────────

    /**
     * FetchType.LAZY — the JPA default for @ManyToMany, and critically important
     * here: a watchlist could hold hundreds of stocks.  Always-eager loading would
     * make every "SELECT * FROM watchlists" query extremely expensive.
     *
     * @JoinTable explicitly names the association table and its columns.  Without
     * this annotation, Hibernate generates an unpredictable table name and you lose
     * control over the schema.
     *
     * WHY Set and not List:
     * Hibernate has a known bug where @ManyToMany on a List with CascadeType.MERGE
     * can DELETE and re-INSERT all join rows on every merge.  A Set avoids this
     * because duplicate detection is built in (using Stock.equals/hashCode).
     *
     * NO CascadeType here: deleting a Watchlist must NOT delete the referenced
     * Stock records — they are shared reference data owned by no one Watchlist.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name               = "watchlist_stocks",
        joinColumns        = @JoinColumn(name = "watchlist_id"),
        inverseJoinColumns = @JoinColumn(name = "stock_id")
    )
    private Set<Stock> stocks = new HashSet<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    protected Watchlist() {}

    public Watchlist(String name) {
        this.name = name;
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    /**
     * Delegates to the Set, which uses Stock.equals (ticker-based) to prevent
     * the same stock from being added twice.
     */
    public void addStock(Stock stock)    { stocks.add(stock); }
    public void removeStock(Stock stock) { stocks.remove(stock); }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long       getId()    { return id; }

    public String     getName()  { return name; }
    public void       setName(String name) { this.name = name; }

    public Set<Stock> getStocks() { return stocks; }

    // ── equals / hashCode ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Watchlist other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Watchlist{id=%d, name='%s'}".formatted(id, name);
    }
}

