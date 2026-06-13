package com.arshkapoor.portfolio.entity;

/**
 * Discriminates between a stock purchase and a sale.
 *
 * WHY STRING STORAGE: @Enumerated(EnumType.STRING) is used on every field that
 * references this enum.  The alternative, EnumType.ORDINAL, stores 0 or 1 —
 * which silently corrupts all historical rows if you ever insert a new constant
 * between BUY and SELL.  STRING storage is human-readable and refactor-safe.
 */
public enum TransactionType {
    BUY,
    SELL
}

