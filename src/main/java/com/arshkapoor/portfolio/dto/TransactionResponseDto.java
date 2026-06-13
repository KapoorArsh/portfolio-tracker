package com.arshkapoor.portfolio.dto;

import com.arshkapoor.portfolio.entity.Transaction;
import com.arshkapoor.portfolio.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Returned by POST /api/portfolios/{id}/transactions.
 *
 * totalValue = quantity × price is a convenience field so the client does not
 * need to multiply client-side.
 *
 * Built inside PortfolioService.applyTransaction() while the Hibernate session
 * is still open, which is the only safe place to call txn.getStock().getTicker()
 * and txn.getPortfolio().getId() without triggering LazyInitializationException.
 */
public record TransactionResponseDto(
        Long            id,
        Long            portfolioId,
        TransactionType type,
        String          ticker,
        Integer         quantity,
        BigDecimal      price,
        BigDecimal      totalValue,
        LocalDateTime   timestamp
) {
    public static TransactionResponseDto from(Transaction txn) {
        return new TransactionResponseDto(
                txn.getId(),
                txn.getPortfolio().getId(),
                txn.getType(),
                txn.getStock().getTicker(),
                txn.getQuantity(),
                txn.getPrice(),
                txn.getPrice().multiply(BigDecimal.valueOf(txn.getQuantity())),
                txn.getTimestamp());
    }
}

