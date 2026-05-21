package com.wex.purchase.service;

import com.wex.purchase.dto.CreatePurchaseRequest;
import com.wex.purchase.dto.StoredPurchaseResponse;
import com.wex.purchase.entity.PurchaseTransaction;
import com.wex.purchase.repository.PurchaseTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchaseTransactionRepository repository;

    @Mock
    private CurrencyConversionService currencyConversionService;

    @InjectMocks
    private PurchaseService purchaseService;

    @Test
    void storePersistsRoundedAmount() {
        UUID id = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "Coffee",
                LocalDate.of(2024, 1, 10),
                new BigDecimal("4.995"));

        StoredPurchaseResponse response = purchaseService.store(request);

        ArgumentCaptor<PurchaseTransaction> captor = ArgumentCaptor.forClass(PurchaseTransaction.class);
        verify(repository).save(captor.capture());

        assertEquals(new BigDecimal("5.00"), captor.getValue().getAmountUsd());
        assertEquals("Coffee", response.description());
        assertEquals(LocalDate.of(2024, 1, 10), response.transactionDate());
        assertEquals(new BigDecimal("5.00"), response.purchaseAmountUsd());
    }

    @Test
    void retrieveDelegatesToConversionService() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PurchaseTransaction transaction = new PurchaseTransaction(
                id, "Supplies", LocalDate.of(2024, 6, 15), new BigDecimal("100.00"));
        when(repository.findById(id)).thenReturn(Optional.of(transaction));
        when(currencyConversionService.convert(any(), any(), any()))
                .thenReturn(new CurrencyConversionService.ConversionResult(
                        "Canada-Dollar",
                        new BigDecimal("1.355"),
                        new BigDecimal("135.50"),
                        LocalDate.of(2024, 3, 31)));

        var response = purchaseService.retrieveConverted(id, "Canada-Dollar");

        assertEquals(id, response.identifier());
        assertEquals(new BigDecimal("135.50"), response.convertedAmount());
    }
}
