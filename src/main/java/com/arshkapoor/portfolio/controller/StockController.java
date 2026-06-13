package com.arshkapoor.portfolio.controller;

import com.arshkapoor.portfolio.dto.StockDto;
import com.arshkapoor.portfolio.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for stock reference data.
 *
 * Stocks are the shared reference table of the system — they are not owned by
 * any single portfolio and are never returned as raw JPA entities.
 */
@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * GET /api/stocks
     * Returns all stocks ordered by ticker.
     * Optional query param ?q= narrows by ticker or name (case-insensitive).
     *
     * @param q optional free-text search; when absent, all stocks are returned.
     */
    @GetMapping
    public ResponseEntity<List<StockDto>> getAllStocks(
            @RequestParam(required = false) String q) {
        List<StockDto> stocks = (q != null && !q.isBlank())
                ? stockService.search(q).stream().map(StockDto::from).toList()
                : stockService.getAllStocks().stream().map(StockDto::from).toList();
        return ResponseEntity.ok(stocks);
    }

    /**
     * GET /api/stocks/{id}
     * Returns a single stock by its primary key.
     * Throws StockNotFoundException (→ 404) when the id is unknown.
     */
    @GetMapping("/{id}")
    public ResponseEntity<StockDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(StockDto.from(stockService.getById(id)));
    }
}

