package com.wex.purchase.service;

import com.wex.purchase.dto.ConvertedPurchaseResponse;
import com.wex.purchase.dto.CreatePurchaseRequest;
import com.wex.purchase.dto.StoredPurchaseResponse;
import com.wex.purchase.entity.PurchaseTransaction;
import com.wex.purchase.exception.PurchaseNotFoundException;
import com.wex.purchase.repository.PurchaseTransactionRepository;
import com.wex.purchase.util.MoneyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PurchaseService {

    private final PurchaseTransactionRepository repository;
    private final CurrencyConversionService currencyConversionService;

    public PurchaseService(
            PurchaseTransactionRepository repository,
            CurrencyConversionService currencyConversionService) {
        this.repository = repository;
        this.currencyConversionService = currencyConversionService;
    }

    @Transactional
    public StoredPurchaseResponse store(CreatePurchaseRequest request) {
        UUID id = UUID.randomUUID();
        PurchaseTransaction transaction = new PurchaseTransaction(
                id,
                request.description().trim(),
                request.transactionDate(),
                MoneyUtils.toCents(request.purchaseAmountUsd()));

        PurchaseTransaction saved = repository.save(transaction);
        return toStoredResponse(saved);
    }

    @Transactional(readOnly = true)
    public ConvertedPurchaseResponse retrieveConverted(UUID id, String targetCurrency) {
        PurchaseTransaction transaction = repository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException("Purchase transaction not found: " + id));

        CurrencyConversionService.ConversionResult conversion = currencyConversionService.convert(
                transaction.getAmountUsd(),
                transaction.getTransactionDate(),
                targetCurrency);

        return new ConvertedPurchaseResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getAmountUsd(),
                conversion.exchangeRate(),
                conversion.targetCurrency(),
                conversion.convertedAmount());
    }

    private StoredPurchaseResponse toStoredResponse(PurchaseTransaction transaction) {
        return new StoredPurchaseResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getAmountUsd());
    }
}
