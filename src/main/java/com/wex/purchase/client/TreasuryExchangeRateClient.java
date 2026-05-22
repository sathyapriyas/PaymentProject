package com.wex.purchase.client;

import com.wex.purchase.dto.TreasuryRatesResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Component
public class TreasuryExchangeRateClient {

    private static final Logger log = LogManager.getLogger(TreasuryExchangeRateClient.class);

    private final RestClient restClient;

    public TreasuryExchangeRateClient(RestClient treasuryRestClient) {
        this.restClient = treasuryRestClient;
    }

    /**
     * Fetches exchange rates for the target currency on or before the purchase date,
     * limited to the 6-month lookback window, sorted by most recent first.
     */
    public List<TreasuryRatesResponse.TreasuryRateRecord> findRates(
            String targetCurrency,
            LocalDate purchaseDate,
            LocalDate earliestAllowedDate) {

        String filter = "country_currency_desc:eq:%s,record_date:lte:%s,record_date:gte:%s"
                .formatted(targetCurrency, purchaseDate, earliestAllowedDate);
        log.debug("Calling Treasury API: currency={}, purchaseDate={}, earliestAllowedDate={}",
                targetCurrency, purchaseDate, earliestAllowedDate);

        TreasuryRatesResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("fields", "country_currency_desc,exchange_rate,record_date")
                        .queryParam("filter", filter)
                        .queryParam("sort", "-record_date")
                        .queryParam("page[size]", "100")
                        .build())
                .retrieve()
                .body(TreasuryRatesResponse.class);

        if (response == null || response.data() == null) {
            log.warn("Treasury API returned no data for currency={}", targetCurrency);
            return List.of();
        }
        log.debug("Treasury API returned {} rate(s) for currency={}", response.data().size(), targetCurrency);
        return response.data();
    }
}
