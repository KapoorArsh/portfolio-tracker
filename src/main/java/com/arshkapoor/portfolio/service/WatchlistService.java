package com.arshkapoor.portfolio.service;

import com.arshkapoor.portfolio.dto.WatchlistDto;
import com.arshkapoor.portfolio.exception.WatchlistNotFoundException;
import com.arshkapoor.portfolio.repository.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only operations for Watchlist.
 *
 * Both methods use JOIN FETCH queries (findAllWithStocks / findByIdWithStocks) to
 * load the stocks collection in the same SELECT.  Without JOIN FETCH, accessing
 * w.getStocks() after the session closes would throw LazyInitializationException
 * because open-in-view is disabled.
 *
 * WatchlistDto.from() is called while the transaction is still active so that
 * all Stock fields are accessible.
 */
@Service
@Transactional(readOnly = true)
public class WatchlistService {

    private final WatchlistRepository watchlistRepo;

    public WatchlistService(WatchlistRepository watchlistRepo) {
        this.watchlistRepo = watchlistRepo;
    }

    /** Returns all watchlists with their stock lists eagerly loaded. */
    public List<WatchlistDto> getAllWatchlists() {
        return watchlistRepo.findAllWithStocks()
                .stream()
                .map(WatchlistDto::from)
                .toList();
    }

    /**
     * Returns a single watchlist with its stocks.
     * @throws WatchlistNotFoundException if the id is unknown.
     */
    public WatchlistDto getById(Long id) {
        return watchlistRepo.findByIdWithStocks(id)
                .map(WatchlistDto::from)
                .orElseThrow(() -> new WatchlistNotFoundException(id));
    }
}

