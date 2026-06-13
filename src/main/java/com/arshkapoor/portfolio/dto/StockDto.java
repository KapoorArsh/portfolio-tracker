package com.arshkapoor.portfolio.dto;

import com.arshkapoor.portfolio.entity.Stock;

import java.math.BigDecimal;

/**
 * Read-only projection of a Stock entity.
 * Shared across holding details, watchlist responses, and the stocks list endpoint.
 *
 * The static factory method keeps mapping logic co-located with the shape it produces
 * rather than scattered across controllers or a separate mapper class.
 */
public record StockDto(
        Long       id,
        String     ticker,
        String     name,
        BigDecimal currentPrice,
        String     sector
) {
    /** Purely structural mapping — no business logic involved. */
    public static StockDto from(Stock s) {
        return new StockDto(
                s.getId(),
                s.getTicker(),
                s.getName(),
                s.getCurrentPrice(),
                s.getSector());
    }
}

