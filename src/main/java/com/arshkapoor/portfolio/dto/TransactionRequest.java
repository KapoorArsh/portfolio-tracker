package com.arshkapoor.portfolio.dto;

import com.arshkapoor.portfolio.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Immutable value object carrying the inputs for a single BUY or SELL.
 *
 * WHY a record and not an entity:
 *   Records are Java's built-in immutable data carriers (Java 16+).  They
 *   auto-generate equals, hashCode, toString, and accessor methods.  Using a
 *   record here instead of the Transaction entity separates the "API contract"
 *   (what the caller provides) from the "persistence contract" (what Hibernate
 *   stores), so the two can evolve independently.
 *
 * Bean Validation annotations on record components are honoured by both
 * Spring MVC (@Valid on the controller parameter) and by calling
 * Validator.validate() directly in unit tests.
 */
public record TransactionRequest(

        @NotNull(message = "Stock ID must not be null")
        Long stockId,

        @NotNull(message = "Transaction type must not be null")
        TransactionType type,

        @NotNull(message = "Quantity must not be null")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.0001", message = "Price must be greater than zero")
        BigDecimal price

) {}

