package com.wex.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ConvertedPurchaseResponse(
        UUID identifier,
        String description,
        LocalDate transactionDate,
        BigDecimal originalAmountUsd,
        BigDecimal exchangeRate,
        String targetCurrency,
        BigDecimal convertedAmount
) {
}
