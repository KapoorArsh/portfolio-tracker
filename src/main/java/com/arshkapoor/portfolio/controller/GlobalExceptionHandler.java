package com.arshkapoor.portfolio.controller;

import com.arshkapoor.portfolio.exception.InsufficientSharesException;
import com.arshkapoor.portfolio.exception.PortfolioNotFoundException;
import com.arshkapoor.portfolio.exception.StockNotFoundException;
import com.arshkapoor.portfolio.exception.WatchlistNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised HTTP error mapping for all domain exceptions.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody.  Every handler
 * method here returns JSON automatically.
 *
 * WHY centralise instead of using @ResponseStatus on exception classes?
 *   @ResponseStatus mixes web concerns (HTTP status codes) into the domain/
 *   service layer.  A centralised handler keeps those concerns separated: the
 *   exception says WHAT went wrong; the handler says WHICH HTTP status that maps
 *   to.  This also makes it trivial to change status codes without touching
 *   exception classes, and lets us add structured response bodies.
 *
 * ProblemDetail (RFC 9457 / RFC 7807) is Spring 6+'s built-in error response
 * format.  It provides a standardised JSON shape:
 *   { "type": "...", "title": "...", "status": 404, "detail": "..." }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maps PortfolioNotFoundException → HTTP 404 Not Found.
     * ProblemDetail.forStatusAndDetail() creates the RFC 9457 JSON body.
     */
    @ExceptionHandler(PortfolioNotFoundException.class)
    public ProblemDetail handlePortfolioNotFound(PortfolioNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("/errors/portfolio-not-found"));
        pd.setTitle("Portfolio Not Found");
        return pd;
    }

    /** Maps StockNotFoundException → HTTP 404 Not Found. */
    @ExceptionHandler(StockNotFoundException.class)
    public ProblemDetail handleStockNotFound(StockNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("/errors/stock-not-found"));
        pd.setTitle("Stock Not Found");
        return pd;
    }

    /** Maps WatchlistNotFoundException → HTTP 404 Not Found. */
    @ExceptionHandler(WatchlistNotFoundException.class)
    public ProblemDetail handleWatchlistNotFound(WatchlistNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("/errors/watchlist-not-found"));
        pd.setTitle("Watchlist Not Found");
        return pd;
    }

    /**
     * Maps @Valid / @Validated failures → HTTP 400 Bad Request.
     *
     * MethodArgumentNotValidException is thrown by Spring MVC when a @RequestBody
     * annotated with @Valid fails Bean Validation.  We extract every field-level
     * error and return them as a map so the client knows exactly which fields to fix.
     *
     * Example response body:
     *   { "status": 400, "title": "Validation Failed",
     *     "errors": { "name": "Portfolio name must not be blank" } }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("/errors/validation-failed"));
        pd.setTitle("Validation Failed");
        pd.setDetail(fieldErrors.size() + " field(s) failed validation");
        pd.setProperty("errors", fieldErrors);
        return pd;
    }

    /**
     * Maps InsufficientSharesException → HTTP 422 Unprocessable Entity.
     * 422 (not 400) because the request was well-formed — the business rule
     * (can't sell more than held) was the problem, not a malformed payload.
     */
    @ExceptionHandler(InsufficientSharesException.class)
    public ProblemDetail handleInsufficientShares(InsufficientSharesException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("/errors/insufficient-shares"));
        pd.setTitle("Insufficient Shares");
        pd.setProperty("ticker",    ex.getTicker());
        pd.setProperty("requested", ex.getRequested());
        pd.setProperty("held",      ex.getHeld());
        return pd;
    }

    /** Catch-all — logs the full stack trace so any unexpected bug is visible in the server log. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception on request: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        pd.setTitle("Internal Server Error");
        return pd;
    }
}
