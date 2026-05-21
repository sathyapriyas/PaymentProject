package com.wex.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StoredPurchaseResponse(
        UUID identifier,
        String description,
        LocalDate transactionDate,
        BigDecimal purchaseAmountUsd
) {
}
