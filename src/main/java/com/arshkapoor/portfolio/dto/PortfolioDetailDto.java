package com.arshkapoor.portfolio.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full portfolio detail returned by GET /api/portfolios/{id}.
 * Includes the complete holdings list with per-position analytics.
 * Contrast with PortfolioListItemDto which carries only id/name/createdAt.
 */
public record PortfolioDetailDto(
        Long             id,
        String           name,
        LocalDateTime    createdAt,
        List<HoldingDto> holdings
) {}

