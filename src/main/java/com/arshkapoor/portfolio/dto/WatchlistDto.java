package com.arshkapoor.portfolio.dto;

import com.arshkapoor.portfolio.entity.Watchlist;

import java.util.Comparator;
import java.util.List;

/**
 * Returned by GET /api/watchlists and GET /api/watchlists/{id}.
 * Includes the full stocks list so the client can render tickers and prices inline.
 *
 * Stocks are sorted alphabetically by ticker for a consistent UI order.
 * The Watchlist entity's stocks Set has no guaranteed order (it's a HashSet)
 * so sorting here produces stable JSON output.
 */
public record WatchlistDto(
        Long           id,
        String         name,
        List<StockDto> stocks
) {
    /**
     * The Watchlist must be fetched with its stocks collection loaded
     * (via findByIdWithStocks or findAllWithStocks) before calling this.
     */
    public static WatchlistDto from(Watchlist w) {
        List<StockDto> stockDtos = w.getStocks().stream()
                .map(StockDto::from)
                .sorted(Comparator.comparing(StockDto::ticker))
                .toList();
        return new WatchlistDto(w.getId(), w.getName(), stockDtos);
    }
}

