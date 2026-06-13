package com.arshkapoor.portfolio.exception;

/**
 * Thrown when a stock lookup (by id or ticker) finds no matching row.
 *
 * Two constructors let callers use whichever identifier they have at hand —
 * the message will say "id: 42" or "ticker: 'AAPL'" accordingly, making
 * logs and API error responses immediately actionable without extra context.
 */
public class StockNotFoundException extends RuntimeException {

    public StockNotFoundException(Long id) {
        super("Stock not found with id: " + id);
    }

    public StockNotFoundException(String ticker) {
        super("Stock not found with ticker: '" + ticker + "'");
    }
}

