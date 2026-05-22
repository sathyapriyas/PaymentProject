package com.wex.purchase.client;

import com.wex.purchase.dto.TreasuryRatesResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Facade for Treasury exchange rate lookups backed by a {@link TreasuryRateCache}.
 */
@Component
public class TreasuryExchangeRateClient {

    private static final Logger log = LogManager.getLogger(TreasuryExchangeRateClient.class);

    private final TreasuryRateCache rateCache;

    public TreasuryExchangeRateClient(TreasuryRateCache rateCache) {
        this.rateCache = rateCache;
    }

    /**
     * Fetches exchange rates for the target currency on or before the purchase date,
     * limited to the 6-month lookback window, sorted by most recent first.
     */
    public List<TreasuryRatesResponse.TreasuryRateRecord> findRates(
            String targetCurrency,
            LocalDate purchaseDate,
            LocalDate earliestAllowedDate) {

        log.debug("Resolving Treasury rates: currency={}, purchaseDate={}", targetCurrency, purchaseDate);
        return rateCache.get(targetCurrency, purchaseDate, earliestAllowedDate);
    }
}
