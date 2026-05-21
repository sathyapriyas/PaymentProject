package com.wex.purchase.service;

import com.wex.purchase.client.TreasuryExchangeRateClient;
import com.wex.purchase.dto.TreasuryRatesResponse;
import com.wex.purchase.exception.CurrencyConversionException;
import com.wex.purchase.util.MoneyUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CurrencyConversionService {

    private static final int LOOKBACK_MONTHS = 6;

    private final TreasuryExchangeRateClient treasuryClient;

    public CurrencyConversionService(TreasuryExchangeRateClient treasuryClient) {
        this.treasuryClient = treasuryClient;
    }

    public ConversionResult convert(BigDecimal amountUsd, LocalDate transactionDate, String targetCurrency) {
        LocalDate earliestAllowed = transactionDate.minusMonths(LOOKBACK_MONTHS);

        List<TreasuryRatesResponse.TreasuryRateRecord> rates =
                treasuryClient.findRates(targetCurrency, transactionDate, earliestAllowed);

        TreasuryRatesResponse.TreasuryRateRecord selected = rates.stream()
                .filter(rate -> isWithinWindow(rate.record_date(), transactionDate, earliestAllowed))
                .findFirst()
                .orElseThrow(() -> new CurrencyConversionException(
                        "Purchase cannot be converted to the target currency: no exchange rate found within "
                                + LOOKBACK_MONTHS + " months on or before the purchase date."));

        BigDecimal exchangeRate = new BigDecimal(selected.exchange_rate());
        BigDecimal converted = MoneyUtils.toCents(amountUsd.multiply(exchangeRate));

        return new ConversionResult(
                targetCurrency,
                exchangeRate,
                converted,
                LocalDate.parse(selected.record_date()));
    }

    private boolean isWithinWindow(String recordDate, LocalDate purchaseDate, LocalDate earliestAllowed) {
        LocalDate rateDate = LocalDate.parse(recordDate);
        return !rateDate.isAfter(purchaseDate) && !rateDate.isBefore(earliestAllowed);
    }

    public record ConversionResult(
            String targetCurrency,
            BigDecimal exchangeRate,
            BigDecimal convertedAmount,
            LocalDate rateRecordDate
    ) {
    }
}
