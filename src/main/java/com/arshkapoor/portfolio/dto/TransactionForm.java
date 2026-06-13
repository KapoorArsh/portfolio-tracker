package com.arshkapoor.portfolio.dto;

import com.arshkapoor.portfolio.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Mutable form-backing object for POST /portfolios/{id}/transactions (Thymeleaf transaction form).
 *
 * WHY not TransactionRequest:
 *   Same reason as CreatePortfolioForm — TransactionRequest is a record (immutable),
 *   so Spring MVC's DataBinder cannot bind HTML form fields to it via setters.
 *   This class mirrors TransactionRequest's fields and validation rules, but as a
 *   mutable POJO.  The DashboardController converts it to TransactionRequest before
 *   delegating to the service:
 *     new TransactionRequest(form.getStockId(), form.getType(), form.getQuantity(), form.getPrice())
 *
 * WHY @Min(1) instead of @Positive:
 *   @Positive (from jakarta.validation.constraints) validates that a value is > 0,
 *   but for Integer it applies to the raw int comparison.  @Min(1) is equivalent
 *   and produces the same error message as TransactionRequest's @Positive, keeping
 *   user-facing messages consistent across both entry points.
 */
public class TransactionForm {

    /** Database ID of the selected stock — drives the dropdown selection. */
    @NotNull(message = "Stock must be selected")
    private Long stockId;

    /**
     * BUY or SELL.
     * Spring MVC's ConversionService automatically converts the submitted string
     * ("BUY" or "SELL") to the TransactionType enum constant — no custom converter needed.
     */
    @NotNull(message = "Transaction type must be selected")
    private TransactionType type;

    @NotNull(message = "Quantity must not be null")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.0001", message = "Price must be greater than zero")
    private BigDecimal price;

    /** Zero-arg constructor required by Spring MVC's DataBinder. */
    public TransactionForm() {}

    public Long            getStockId()  { return stockId;  }
    public void            setStockId(Long stockId)   { this.stockId  = stockId;   }

    public TransactionType getType()     { return type;     }
    public void            setType(TransactionType type) { this.type = type; }

    public Integer         getQuantity() { return quantity; }
    public void            setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal      getPrice()    { return price;    }
    public void            setPrice(BigDecimal price) { this.price = price; }
}

