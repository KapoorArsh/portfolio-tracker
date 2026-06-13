package com.arshkapoor.portfolio.exception;

/**
 * Thrown by PortfolioService.applyTransaction when a SELL order requests more
 * shares than the portfolio currently holds.
 *
 * Stores the structured fields (ticker, requested, held) separately so the
 * web layer can build a precise, user-facing error message without parsing
 * the exception message string — string parsing is fragile and breaks on
 * internationalisation.
 *
 * "held == 0" means the portfolio has no position in that stock at all;
 * "held > 0 but held < requested" means a partial position exists.
 */
public class InsufficientSharesException extends RuntimeException {

    private final String ticker;
    private final int    requested;
    private final int    held;

    public InsufficientSharesException(String ticker, int requested, int held) {
        super("Cannot sell %d share(s) of %s — only %d held."
                .formatted(requested, ticker, held));
        this.ticker    = ticker;
        this.requested = requested;
        this.held      = held;
    }

    public String getTicker()    { return ticker;    }
    public int    getRequested() { return requested; }
    public int    getHeld()      { return held;      }
}

