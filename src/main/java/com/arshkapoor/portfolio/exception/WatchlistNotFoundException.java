package com.arshkapoor.portfolio.exception;

/** Thrown when a watchlist ID supplied by the caller does not match any row. */
public class WatchlistNotFoundException extends RuntimeException {

    private final Long watchlistId;

    public WatchlistNotFoundException(Long watchlistId) {
        super("Watchlist not found with id: " + watchlistId);
        this.watchlistId = watchlistId;
    }

    public Long getWatchlistId() { return watchlistId; }
}

