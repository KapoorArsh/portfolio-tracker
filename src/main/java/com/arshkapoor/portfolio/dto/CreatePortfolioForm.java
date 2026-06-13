package com.arshkapoor.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mutable form-backing object for POST /portfolios (Thymeleaf create-portfolio form).
 *
 * WHY a separate class and not CreatePortfolioRequest:
 *   CreatePortfolioRequest is a Java record — records are immutable and have no
 *   setter methods.  Spring MVC's default DataBinder populates model attributes
 *   via setter injection (property-based binding), so a mutable POJO is required
 *   for HTML form binding.
 *
 *   The DashboardController converts this form object to a service call:
 *     portfolioService.createPortfolio(form.getName())
 *   This keeps the service and REST layer completely unaware of how the form works.
 *
 * Validation annotations mirror CreatePortfolioRequest so both entry points
 * enforce the same rules.
 */
public class CreatePortfolioForm {

    @NotBlank(message = "Portfolio name must not be blank")
    @Size(max = 100, message = "Portfolio name must be 100 characters or fewer")
    private String name;

    /** Zero-arg constructor required by Spring MVC's DataBinder. */
    public CreatePortfolioForm() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

