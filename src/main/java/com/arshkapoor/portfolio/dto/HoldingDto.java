package com.arshkapoor.portfolio.dto;

import com.arshkapoor.portfolio.entity.Holding;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Read-only projection of a Holding with per-position analytics computed inline.
 *
 * The three derived fields are calculated by the service layer while Hibernate's
 * session is still open, so getStock() does not throw LazyInitializationException.
 * Calling from() outside a @Transactional context would fail silently if the
 * stock collection hasn't been JOIN FETCHed.
 *
 * Fields:
 *   currentValue   = quantity × stock.currentPrice
 *   unrealizedGain = (stock.currentPrice − avgBuyPrice) × quantity
 *                    Negative value means an unrealised loss.
 */
public record HoldingDto(
        Long       id,
        String     ticker,
        String     stockName,
        String     sector,
        Integer    quantity,
        BigDecimal avgBuyPrice,
        BigDecimal currentPrice,
        BigDecimal currentValue,
        BigDecimal unrealizedGain
) {
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Called by PortfolioService.getPortfolioDetail() — the Holding must be
     * fetched with its Stock eagerly loaded (via findByPortfolioIdWithStock)
     * before invoking this method.
     */
    public static HoldingDto from(Holding h) {
        BigDecimal qty          = BigDecimal.valueOf(h.getQuantity());
        BigDecimal currentVal   = h.getStock().getCurrentPrice()
                                   .multiply(qty)
                                   .setScale(2, ROUNDING);
        BigDecimal gain         = h.getStock().getCurrentPrice()
                                   .subtract(h.getAvgBuyPrice())
                                   .multiply(qty)
                                   .setScale(2, ROUNDING);
        return new HoldingDto(
                h.getId(),
                h.getStock().getTicker(),
                h.getStock().getName(),
                h.getStock().getSector(),
                h.getQuantity(),
                h.getAvgBuyPrice(),
                h.getStock().getCurrentPrice(),
                currentVal,
                gain);
    }
}

