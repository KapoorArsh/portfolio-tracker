package com.arshkapoor.portfolio.service;

import com.arshkapoor.portfolio.entity.Stock;
import com.arshkapoor.portfolio.exception.StockNotFoundException;
import com.arshkapoor.portfolio.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * CRUD and search operations for Stock.
 *
 * Kept intentionally thin — Stock is a reference entity with no complex
 * business logic.  The analytics that use Stock data live in PortfolioService,
 * which depends on StockRepository directly (no cross-service call) to keep the
 * dependency graph simple and unit tests fast.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepo;

    public StockService(StockRepository stockRepo) {
        this.stockRepo = stockRepo;
    }

    public List<Stock> getAllStocks() {
        // Ordered by ticker for consistent UI rendering.
        return stockRepo.findAllOrderedByTicker();
    }

    /** @throws StockNotFoundException if no row with this id exists. */
    public Stock getById(Long id) {
        return stockRepo.findById(id)
                .orElseThrow(() -> new StockNotFoundException(id));
    }

    /** @throws StockNotFoundException if no row with this ticker exists. */
    public Stock getByTicker(String ticker) {
        return stockRepo.findByTickerIgnoreCase(ticker)
                .orElseThrow(() -> new StockNotFoundException(ticker));
    }

    /**
     * Full-text search across ticker and name — used by the autocomplete endpoint.
     * Delegates to the OR-clause derived query in StockRepository.
     */
    public List<Stock> search(String query) {
        return stockRepo.findByTickerContainingIgnoreCaseOrNameContainingIgnoreCase(
                query, query);
    }

    /**
     * Persist a new stock.
     * Guards against duplicate tickers at the service level so the caller
     * receives an IllegalArgumentException (with a clear message) rather than
     * a DataIntegrityViolationException from the DB unique constraint — the
     * latter is harder to map to a user-friendly error.
     */
    @Transactional
    public Stock createStock(Stock stock) {
        if (stockRepo.existsByTickerIgnoreCase(stock.getTicker())) {
            throw new IllegalArgumentException(
                    "A stock with ticker '" + stock.getTicker() + "' already exists.");
        }
        return stockRepo.save(stock);
    }

    /**
     * Update the current market price of a stock.
     *
     * In a production system this would be called by a scheduled price-feed job
     * (e.g. @Scheduled every minute).  Keeping it here means the controller and
     * the scheduled job share the same validated path — no duplicated logic.
     *
     * @throws StockNotFoundException  if the stock does not exist
     * @throws IllegalArgumentException if the new price is not positive
     */
    @Transactional
    public Stock updatePrice(Long id, BigDecimal newPrice) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero.");
        }
        Stock stock = getById(id);
        stock.setCurrentPrice(newPrice);
        // No explicit save() call needed here: the entity is already managed
        // (Hibernate tracks it), so the UPDATE fires automatically at flush time.
        // save() would work too but is redundant — including it would imply
        // the entity might not be managed, which is misleading.
        return stock;
    }
}

