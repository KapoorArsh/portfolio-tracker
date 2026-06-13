package com.arshkapoor.portfolio.exception;

/**
 * Thrown when a portfolio ID supplied by the caller does not match any row
 * in the portfolios table.
 *
 * Extends RuntimeException (unchecked) so callers are not forced to declare or
 * catch it — the @ControllerAdvice in the web layer will intercept it globally
 * and map it to HTTP 404.  Checked exceptions would pollute every service and
 * controller signature with "throws PortfolioNotFoundException" for no benefit.
 */
public class PortfolioNotFoundException extends RuntimeException {

    private final Long portfolioId;

    public PortfolioNotFoundException(Long portfolioId) {
        super("Portfolio not found with id: " + portfolioId);
        this.portfolioId = portfolioId;
    }

    /** Exposes the id so the error handler can include it in the HTTP response body. */
    public Long getPortfolioId() {
        return portfolioId;
    }
}

