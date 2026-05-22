package com.wex.purchase.client;

import com.wex.purchase.dto.TreasuryRatesResponse;
import com.wex.purchase.exception.TreasuryApiUnavailableException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

/**
 * Performs HTTP calls to the Treasury API with automatic retry on transient failures.
 */
@Component
public class TreasuryHttpClient {

    private static final Logger log = LogManager.getLogger(TreasuryHttpClient.class);

    private final RestClient restClient;

    public TreasuryHttpClient(RestClient treasuryRestClient) {
        this.restClient = treasuryRestClient;
    }

    @Retryable(
            retryFor = {RestClientException.class, ResourceAccessException.class, HttpServerErrorException.class},
            maxAttemptsExpression = "${treasury.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${treasury.retry.initial-delay-ms:500}",
                    multiplierExpression = "${treasury.retry.multiplier:2.0}"))
    public List<TreasuryRatesResponse.TreasuryRateRecord> fetchRates(
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

    @Recover
    public List<TreasuryRatesResponse.TreasuryRateRecord> recoverFetchRates(
            Exception ex,
            String targetCurrency,
            LocalDate purchaseDate,
            LocalDate earliestAllowedDate) {
        log.error("Treasury API unavailable after retries: currency={}, purchaseDate={}",
                targetCurrency, purchaseDate, ex);
        throw new TreasuryApiUnavailableException(
                "Treasury API is unavailable after multiple retry attempts", ex);
    }
}
