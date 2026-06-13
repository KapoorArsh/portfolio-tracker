package com.arshkapoor.portfolio.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Read-only snapshot of a portfolio's financial state at a point in time.
 *
 * Bundling all five computed values into one record lets the service calculate
 * them in a SINGLE pass over the holdings list, avoiding redundant repository
 * calls.  The alternative — calling currentValue(), costBasis(), allocation()
 * separately — would hit the database three times and iterate the same list
 * three times.
 *
 * All monetary values are rounded to 2 decimal places (display-ready).
 * Allocation percentages are rounded to 2 decimal places.
 *
 * Fields:
 *   portfolioId        — the PK, useful when this DTO is serialised to JSON
 *   portfolioName      — the human-readable label
 *   currentValue       — Σ(qty × currentPrice)
 *   costBasis          — Σ(qty × avgBuyPrice)
 *   unrealizedGain     — currentValue − costBasis  (negative = loss)
 *   unrealizedGainPct  — unrealizedGain / costBasis × 100
 *   allocation         — ticker → percentage of total portfolio value (sums to ~100%)
 */
public record PortfolioSummaryDto(
        Long                    portfolioId,
        String                  portfolioName,
        BigDecimal              currentValue,
        BigDecimal              costBasis,
        BigDecimal              unrealizedGain,
        BigDecimal              unrealizedGainPct,
        Map<String, BigDecimal> allocation
) {}

