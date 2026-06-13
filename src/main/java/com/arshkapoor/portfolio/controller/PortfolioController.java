package com.arshkapoor.portfolio.controller;

import com.arshkapoor.portfolio.dto.*;
import com.arshkapoor.portfolio.entity.Portfolio;
import com.arshkapoor.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * REST controller for portfolio CRUD and analytics.
 *
 * Design rules:
 *   • Never serialise JPA entities directly — every response is a DTO record.
 *   • No business logic here; every decision is delegated to PortfolioService.
 *   • Error responses are handled centrally by GlobalExceptionHandler via
 *     @RestControllerAdvice — no try/catch blocks in controllers.
 *
 * HTTP status conventions used:
 *   200 OK       — successful read
 *   201 Created  — successful write; Location header points to the new resource
 *   400 Bad Req  — validation failure on the request body (@Valid)
 *   404 Not Found — unknown portfolio or stock id (thrown by service)
 *   422 Unprocessable — SELL exceeds held quantity (InsufficientSharesException)
 */
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    // ── GET /api/portfolios ───────────────────────────────────────────────────

    /**
     * Lists all portfolios as lightweight items (id, name, createdAt).
     * Does NOT include holdings — use GET /api/portfolios/{id} for the full view.
     */
    @GetMapping
    public ResponseEntity<List<PortfolioListItemDto>> getAllPortfolios() {
        List<PortfolioListItemDto> items = portfolioService.getAllPortfolios()
                .stream()
                .map(p -> new PortfolioListItemDto(p.getId(), p.getName(), p.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(items);
    }

    // ── POST /api/portfolios ──────────────────────────────────────────────────

    /**
     * Creates a new portfolio.
     *
     * @Valid triggers Bean Validation on CreatePortfolioRequest.  If the name is
     * blank, GlobalExceptionHandler catches the MethodArgumentNotValidException
     * and returns HTTP 400 with a field-level error map.
     *
     * 201 Created + Location header is the correct status for a resource creation.
     * The Location value tells clients where to GET the new resource.
     */
    @PostMapping
    public ResponseEntity<PortfolioDetailDto> createPortfolio(
            @RequestBody @Valid CreatePortfolioRequest req) {
        Portfolio portfolio = portfolioService.createPortfolio(req.name());
        PortfolioDetailDto dto = new PortfolioDetailDto(
                portfolio.getId(), portfolio.getName(), portfolio.getCreatedAt(), List.of());
        URI location = URI.create("/api/portfolios/" + portfolio.getId());
        return ResponseEntity.created(location).body(dto);
    }

    // ── GET /api/portfolios/{id} ──────────────────────────────────────────────

    /**
     * Returns the full portfolio with its holdings and per-position analytics.
     * HoldingDto includes currentValue and unrealizedGain computed by the service.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PortfolioDetailDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(portfolioService.getPortfolioDetail(id));
    }

    // ── GET /api/portfolios/{id}/summary ─────────────────────────────────────

    /**
     * Returns aggregate analytics: currentValue, costBasis, unrealizedGain,
     * unrealizedGainPct, and a ticker→percentage allocation map.
     * Computed in a single pass over holdings (no redundant DB round-trips).
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<PortfolioSummaryDto> getSummary(@PathVariable Long id) {
        return ResponseEntity.ok(portfolioService.getPortfolioSummary(id));
    }

    // ── POST /api/portfolios/{id}/transactions ────────────────────────────────

    /**
     * Records a BUY or SELL transaction and updates the holding accordingly.
     *
     * BUY: creates a new holding or recomputes the weighted-average buy price.
     * SELL: reduces quantity; returns 422 if quantity sold > quantity held.
     *
     * The response includes the persisted transaction with its generated id,
     * timestamp, and totalValue convenience field.
     */
    @PostMapping("/{id}/transactions")
    public ResponseEntity<TransactionResponseDto> applyTransaction(
            @PathVariable Long id,
            @RequestBody @Valid TransactionRequest req) {
        TransactionResponseDto dto = portfolioService.applyTransaction(id, req);
        return ResponseEntity.status(201).body(dto);
    }
}
