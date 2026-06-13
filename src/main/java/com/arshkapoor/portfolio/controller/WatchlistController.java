package com.arshkapoor.portfolio.controller;

import com.arshkapoor.portfolio.dto.WatchlistDto;
import com.arshkapoor.portfolio.service.WatchlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for watchlist read operations.
 * Every response includes the watchlist's stocks so the client can render
 * tickers and prices without a follow-up request.
 */
@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /**
     * GET /api/watchlists
     * Lists all watchlists with their stocks, sorted alphabetically by ticker
     * within each watchlist.
     */
    @GetMapping
    public ResponseEntity<List<WatchlistDto>> getAllWatchlists() {
        return ResponseEntity.ok(watchlistService.getAllWatchlists());
    }

    /**
     * GET /api/watchlists/{id}
     * Returns a single watchlist with its stocks.
     * Throws WatchlistNotFoundException (→ 404) when the id is unknown.
     */
    @GetMapping("/{id}")
    public ResponseEntity<WatchlistDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(watchlistService.getById(id));
    }
}

