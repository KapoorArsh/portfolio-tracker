package com.arshkapoor.portfolio.service;

import com.arshkapoor.portfolio.dto.HoldingDto;
import com.arshkapoor.portfolio.dto.PortfolioDetailDto;
import com.arshkapoor.portfolio.dto.PortfolioSummaryDto;
import com.arshkapoor.portfolio.dto.TransactionRequest;
import com.arshkapoor.portfolio.dto.TransactionResponseDto;
import com.arshkapoor.portfolio.entity.*;
import com.arshkapoor.portfolio.exception.InsufficientSharesException;
import com.arshkapoor.portfolio.exception.PortfolioNotFoundException;
import com.arshkapoor.portfolio.exception.StockNotFoundException;
import com.arshkapoor.portfolio.repository.HoldingRepository;
import com.arshkapoor.portfolio.repository.PortfolioRepository;
import com.arshkapoor.portfolio.repository.StockRepository;
import com.arshkapoor.portfolio.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business-logic service for portfolio analytics and transaction processing.
 *
 * All public methods are designed to be unit-testable with a mocked repository
 * layer — no HTTP, no Thymeleaf context, no Spring MVC is required to call them.
 *
 * @Transactional(readOnly = true) on the class sets every method to a read-only
 * transaction by default.  Read-only mode:
 *   • tells Hibernate to skip dirty-checking (faster flush)
 *   • allows the datasource to route the query to a read replica if configured
 *   • prevents accidental writes from slipping into a "read" method
 * Individual write methods override this with plain @Transactional.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioService {

    // Scale used for intermediate BigDecimal arithmetic; 4 decimal places give
    // enough precision before we round down to 2 for display.
    private static final int           CALC_SCALE   = 4;
    private static final RoundingMode  ROUNDING     = RoundingMode.HALF_UP;
    private static final BigDecimal    HUNDRED       = new BigDecimal("100");

    private final PortfolioRepository  portfolioRepo;
    private final HoldingRepository    holdingRepo;
    private final TransactionRepository transactionRepo;
    private final StockRepository      stockRepo;

    public PortfolioService(PortfolioRepository   portfolioRepo,
                            HoldingRepository     holdingRepo,
                            TransactionRepository transactionRepo,
                            StockRepository       stockRepo) {
        this.portfolioRepo   = portfolioRepo;
        this.holdingRepo     = holdingRepo;
        this.transactionRepo = transactionRepo;
        this.stockRepo       = stockRepo;
    }

    // =========================================================================
    // Simple lookups
    // =========================================================================

    /** Returns the portfolio or throws PortfolioNotFoundException — never null. */
    public Portfolio getById(Long portfolioId) {
        return portfolioRepo.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }

    public List<Portfolio> getAllPortfolios() {
        return portfolioRepo.findAll();
    }

    // =========================================================================
    // Analytics — individual formulas
    // =========================================================================

    /**
     * currentValue = Σ (holding.quantity × stock.currentPrice)
     *
     * Uses findByPortfolioIdWithStock() which JOIN FETCHes the Stock in the same
     * SELECT.  Without JOIN FETCH, accessing holding.getStock() inside the stream
     * would fire one extra SELECT per holding (the N+1 problem).
     */
    public BigDecimal currentValue(Long portfolioId) {
        ensureExists(portfolioId);
        return holdingRepo.findByPortfolioIdWithStock(portfolioId).stream()
                .map(h -> h.getStock().getCurrentPrice()
                           .multiply(BigDecimal.valueOf(h.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(CALC_SCALE, ROUNDING);
    }

    /**
     * costBasis = Σ (holding.quantity × holding.avgBuyPrice)
     *
     * Uses the plain findByPortfolioId() — no stock join needed because
     * avgBuyPrice is stored on the Holding row itself.
     */
    public BigDecimal costBasis(Long portfolioId) {
        ensureExists(portfolioId);
        return holdingRepo.findByPortfolioId(portfolioId).stream()
                .map(h -> h.getAvgBuyPrice()
                           .multiply(BigDecimal.valueOf(h.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(CALC_SCALE, ROUNDING);
    }

    /**
     * unrealizedGain = currentValue − costBasis
     *
     * A positive result means profit (current market value exceeds what was paid).
     * A negative result means unrealised loss.
     * "Unrealised" because the gain/loss is not locked in until the shares are sold.
     */
    public BigDecimal unrealizedGain(Long portfolioId) {
        return currentValue(portfolioId)
                .subtract(costBasis(portfolioId))
                .setScale(CALC_SCALE, ROUNDING);
    }

    /**
     * allocation = ticker → (positionValue / totalValue × 100), rounded to 2 dp.
     *
     * Division by zero is guarded explicitly: if the portfolio is empty or every
     * stock somehow has a zero price, return an empty map rather than throwing
     * ArithmeticException.  BigDecimal.divide() does NOT silently return 0 on
     * divide-by-zero — it throws, so the guard is mandatory.
     *
     * LinkedHashMap preserves the order returned by the repository query so the
     * UI renders holdings in a consistent sequence.
     */
    public Map<String, BigDecimal> allocation(Long portfolioId) {
        List<Holding> holdings = holdingRepo.findByPortfolioIdWithStock(portfolioId);
        if (holdings.isEmpty()) return Collections.emptyMap();

        // First pass: compute each position's market value and the portfolio total.
        Map<String, BigDecimal> positionValues = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Holding h : holdings) {
            BigDecimal posVal = h.getStock().getCurrentPrice()
                                 .multiply(BigDecimal.valueOf(h.getQuantity()));
            positionValues.put(h.getStock().getTicker(), posVal);
            total = total.add(posVal);
        }

        // Guard: avoid BigDecimal.divide(ZERO) which throws ArithmeticException.
        if (total.compareTo(BigDecimal.ZERO) == 0) return Collections.emptyMap();

        // Second pass: convert each position value to a percentage.
        final BigDecimal finalTotal = total;   // effectively-final for lambda capture
        return positionValues.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue()
                               .divide(finalTotal, CALC_SCALE + 2, ROUNDING)
                               .multiply(HUNDRED)
                               .setScale(2, ROUNDING),
                        (a, b) -> a,            // merge fn: never triggered (keys are unique tickers)
                        LinkedHashMap::new      // preserve order
                ));
    }

    /**
     * getPortfolioSummary — computes ALL five analytics values in a SINGLE pass.
     *
     * Prefer this over calling currentValue() + costBasis() + allocation()
     * individually: those three methods would hit the database three separate times
     * and iterate the holdings list three times.  This method loads holdings once,
     * computes every value in one loop, then assembles the PortfolioSummaryDto.
     *
     * Returned values:
     *   currentValue      = Σ(qty × currentPrice)
     *   costBasis         = Σ(qty × avgBuyPrice)
     *   unrealizedGain    = currentValue − costBasis
     *   unrealizedGainPct = unrealizedGain / costBasis × 100
     *   allocation        = ticker → % of total value
     */
    public PortfolioSummaryDto getPortfolioSummary(Long portfolioId) {
        Portfolio portfolio = portfolioRepo.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));

        List<Holding> holdings = holdingRepo.findByPortfolioIdWithStock(portfolioId);

        // Empty portfolio: return zeroes rather than dividing by zero later.
        if (holdings.isEmpty()) {
            return new PortfolioSummaryDto(
                    portfolioId, portfolio.getName(),
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    Collections.emptyMap());
        }

        // Single pass: accumulate totals and record each position's market value.
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis    = BigDecimal.ZERO;
        Map<String, BigDecimal> positionValues = new LinkedHashMap<>();

        for (Holding h : holdings) {
            BigDecimal qty         = BigDecimal.valueOf(h.getQuantity());
            BigDecimal posValue    = h.getStock().getCurrentPrice().multiply(qty);
            BigDecimal posCost     = h.getAvgBuyPrice().multiply(qty);
            totalCurrentValue      = totalCurrentValue.add(posValue);
            totalCostBasis         = totalCostBasis.add(posCost);
            positionValues.put(h.getStock().getTicker(), posValue);
        }

        // unrealizedGain = currentValue − costBasis
        BigDecimal gain = totalCurrentValue.subtract(totalCostBasis);

        // unrealizedGainPct = gain / costBasis × 100
        // Guard against costBasis == 0 (e.g. gifted shares with a zero cost basis).
        BigDecimal gainPct = totalCostBasis.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : gain.divide(totalCostBasis, CALC_SCALE + 2, ROUNDING)
                       .multiply(HUNDRED)
                       .setScale(2, ROUNDING);

        // Build allocation map from already-computed position values.
        final BigDecimal finalTotal = totalCurrentValue;
        Map<String, BigDecimal> alloc = positionValues.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue()
                               .divide(finalTotal, CALC_SCALE + 2, ROUNDING)
                               .multiply(HUNDRED)
                               .setScale(2, ROUNDING),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return new PortfolioSummaryDto(
                portfolioId,
                portfolio.getName(),
                totalCurrentValue.setScale(2, ROUNDING),
                totalCostBasis.setScale(2, ROUNDING),
                gain.setScale(2, ROUNDING),
                gainPct,
                alloc);
    }

    // =========================================================================
    // Write methods  (override class-level readOnly = true)
    // =========================================================================

    @Transactional
    public Portfolio createPortfolio(String name) {
        return portfolioRepo.save(new Portfolio(name));
    }

    /**
     * getPortfolioDetail — returns the portfolio with its complete holdings list.
     *
     * Each HoldingDto includes currentValue and unrealizedGain computed while the
     * Hibernate session is still open (within this @Transactional method), so
     * getStock() calls on LAZY associations are safe here.
     */
    public PortfolioDetailDto getPortfolioDetail(Long id) {
        Portfolio portfolio = portfolioRepo.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException(id));

        // JOIN FETCH loads stock data in one SELECT — no N+1 queries.
        List<HoldingDto> holdingDtos = holdingRepo.findByPortfolioIdWithStock(id)
                .stream()
                .map(HoldingDto::from)   // computes currentValue and unrealizedGain
                .toList();

        return new PortfolioDetailDto(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getCreatedAt(),
                holdingDtos);
    }

    /**
     * applyTransaction — the core mutation method.
     *
     * BUY path: weighted-average cost basis update or new Holding creation.
     * SELL path: quantity reduction; throws InsufficientSharesException before
     *            any write if qty > held.
     *
     * Returns TransactionResponseDto built while the session is still open so
     * txn.getStock() and txn.getPortfolio() don't trigger LazyInitializationException
     * after the transaction commits.
     *
     * WHY set timestamp explicitly instead of relying on @PrePersist:
     *   @PrePersist only fires in a real JPA persistence context.  Unit tests use
     *   Mockito-mocked repositories and never call lifecycle callbacks.  Setting the
     *   timestamp here ensures the DTO always has a non-null value in all contexts.
     */
    @Transactional
    public TransactionResponseDto applyTransaction(Long portfolioId, TransactionRequest req) {
        Portfolio portfolio = portfolioRepo.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        Stock stock = stockRepo.findById(req.stockId())
                .orElseThrow(() -> new StockNotFoundException(req.stockId()));

        Optional<Holding> existing =
                holdingRepo.findByPortfolioIdAndStockId(portfolioId, req.stockId());

        if (req.type() == TransactionType.BUY) {
            applyBuy(portfolio, stock, existing, req.quantity(), req.price());
        } else {
            applySell(portfolio, stock, existing, req.quantity());
        }

        // Build and persist the audit record.
        // Timestamp is set explicitly here (not only via @PrePersist) so that
        // unit tests (which don't call JPA lifecycle hooks) also get a valid value.
        Transaction txn = new Transaction(stock, req.type(), req.quantity(), req.price());
        txn.setPortfolio(portfolio);
        txn.setTimestamp(LocalDateTime.now());
        Transaction saved = transactionRepo.save(txn);

        // Build DTO while entities are still managed (session open) — safe to
        // call txn.getStock() and txn.getPortfolio() without lazy-load errors.
        return TransactionResponseDto.from(saved);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * BUY: merge into an existing holding or create a new one.
     *
     * Weighted-average formula:
     *   newAvgPrice = (oldQty × oldAvg + newQty × newPrice) / (oldQty + newQty)
     *
     * Why HALF_UP with scale 4:
     *   BigDecimal.divide() throws ArithmeticException if the result is a
     *   non-terminating decimal (e.g. 1/3).  Specifying an explicit scale and
     *   RoundingMode prevents that exception and gives a deterministic result.
     */
    private void applyBuy(Portfolio portfolio, Stock stock,
                          Optional<Holding> existing, int qty, BigDecimal price) {
        if (existing.isPresent()) {
            Holding h = existing.get();

            // Numerator: total cost already invested + cost of new purchase
            BigDecimal numerator = h.getAvgBuyPrice()
                    .multiply(BigDecimal.valueOf(h.getQuantity()))  // oldQty × oldAvg
                    .add(price.multiply(BigDecimal.valueOf(qty)));   // + newQty × newPrice

            int newQty = h.getQuantity() + qty;

            // Divide with explicit scale to avoid ArithmeticException on repeating decimals.
            BigDecimal newAvg = numerator.divide(BigDecimal.valueOf(newQty), CALC_SCALE, ROUNDING);

            h.setQuantity(newQty);
            h.setAvgBuyPrice(newAvg);
            holdingRepo.save(h);

        } else {
            // First purchase of this stock in this portfolio.
            // setPortfolio() rather than portfolio.addHolding() avoids lazy-loading
            // portfolio.holdings just to append to the list.
            Holding h = new Holding(stock, qty, price);
            h.setPortfolio(portfolio);
            holdingRepo.save(h);
        }
    }

    /**
     * SELL: reduce quantity.  Throws before any write if the sell is invalid.
     *
     * Fail-fast design: throwing before writing means the database is never
     * left in a partially-updated state, even if @Transactional were absent.
     */
    private void applySell(Portfolio portfolio, Stock stock,
                           Optional<Holding> existing, int qty) {
        // No holding means zero shares — treat as "held = 0" for a clear error message.
        Holding h = existing.orElseThrow(
                () -> new InsufficientSharesException(stock.getTicker(), qty, 0));

        if (qty > h.getQuantity()) {
            throw new InsufficientSharesException(stock.getTicker(), qty, h.getQuantity());
        }

        int remaining = h.getQuantity() - qty;
        if (remaining == 0) {
            // Fully exited position: delete the Holding row.
            // We call holdingRepo.delete() directly rather than portfolio.removeHolding()
            // to avoid loading the lazy portfolio.holdings collection.
            holdingRepo.delete(h);
        } else {
            h.setQuantity(remaining);
            holdingRepo.save(h);
        }
    }

    /** Cheap existence check — throws PortfolioNotFoundException if not found. */
    private void ensureExists(Long portfolioId) {
        if (!portfolioRepo.existsById(portfolioId)) {
            throw new PortfolioNotFoundException(portfolioId);
        }
    }
}

