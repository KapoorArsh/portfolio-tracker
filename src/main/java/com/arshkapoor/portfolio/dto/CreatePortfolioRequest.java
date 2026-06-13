package com.arshkapoor.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/portfolios.
 *
 * WHY a record rather than accepting a bare String:
 *   @RequestBody expects a JSON object { "name": "..." }.  A record gives
 *   us a typed shape with validation annotations and room to add more fields
 *   (description, currency, owner) later without changing the controller signature.
 */
public record CreatePortfolioRequest(

        @NotBlank(message = "Portfolio name must not be blank")
        @Size(max = 100, message = "Portfolio name must be 100 characters or fewer")
        String name

) {}

