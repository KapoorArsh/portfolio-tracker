package com.arshkapoor.portfolio.dto;

import java.time.LocalDateTime;

/**
 * Lightweight projection returned by GET /api/portfolios.
 *
 * We return this record instead of the Portfolio entity directly because the
 * entity has LAZY collections (holdings, transactions).  Serialising the entity
 * would either trigger N+1 lazy-loads or throw LazyInitializationException once
 * the Hibernate session is closed (which it is — open-in-view is disabled).
 * A DTO contains only the fields we actually need on the list page.
 */
public record PortfolioListItemDto(
        Long          id,
        String        name,
        LocalDateTime createdAt
) {}

