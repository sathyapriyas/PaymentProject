package com.wex.purchase.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TreasuryRatesResponse(List<TreasuryRateRecord> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TreasuryRateRecord(
            String country_currency_desc,
            String exchange_rate,
            String record_date
    ) {
    }
}
