package com.wex.purchase.client;

import java.time.LocalDate;

/**
 * Cache key for a Treasury rate lookup (currency plus the applicable date window).
 */
public record TreasuryRateCacheKey(
        String targetCurrency,
        LocalDate purchaseDate,
        LocalDate earliestAllowedDate) {

    public String cacheKey() {
        return targetCurrency + '|' + purchaseDate + '|' + earliestAllowedDate;
    }
}
