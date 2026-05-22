package com.wex.purchase.service;

import com.wex.purchase.client.TreasuryExchangeRateClient;
import com.wex.purchase.dto.TreasuryRatesResponse;
import com.wex.purchase.exception.CurrencyConversionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    @Mock
    private TreasuryExchangeRateClient treasuryClient;

    @InjectMocks
    private CurrencyConversionService conversionService;

    @Test
    void convertsUsingMostRecentRateOnOrBeforePurchaseDate() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        when(treasuryClient.findRates(eq("Canada-Dollar"), eq(purchaseDate), any()))
                .thenReturn(List.of(
                        new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "1.355", "2024-03-31"),
                        new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "1.326", "2023-12-31")));

        CurrencyConversionService.ConversionResult result =
                conversionService.convert(new BigDecimal("100.00"), purchaseDate, "Canada-Dollar");

        assertEquals(new BigDecimal("1.355"), result.exchangeRate());
        assertEquals(new BigDecimal("135.50"), result.convertedAmount());
        assertEquals("Canada-Dollar", result.targetCurrency());
    }

    @Test
    void throwsWhenNoRateWithinSixMonthWindow() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        when(treasuryClient.findRates(eq("Japan-Yen"), eq(purchaseDate), any()))
                .thenReturn(List.of());

        assertThrows(CurrencyConversionException.class,
                () -> conversionService.convert(new BigDecimal("50.00"), purchaseDate, "Japan-Yen"));
    }

    @Test
    void ignoresRatesOlderThanSixMonths() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        when(treasuryClient.findRates(eq("Mexico-Peso"), eq(purchaseDate), any()))
                .thenReturn(List.of(
                        new TreasuryRatesResponse.TreasuryRateRecord("Mexico-Peso", "20.0", "2023-06-01")));

        assertThrows(CurrencyConversionException.class,
                () -> conversionService.convert(new BigDecimal("10.00"), purchaseDate, "Mexico-Peso"));
    }

    @Test
    void selectsRateOnExactPurchaseDate() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        when(treasuryClient.findRates(eq("Canada-Dollar"), eq(purchaseDate), any()))
                .thenReturn(List.of(
                        new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "1.400", "2024-06-15")));

        CurrencyConversionService.ConversionResult result =
                conversionService.convert(new BigDecimal("100.00"), purchaseDate, "Canada-Dollar");

        assertEquals(new BigDecimal("1.400"), result.exchangeRate());
        assertEquals(new BigDecimal("140.00"), result.convertedAmount());
        assertEquals(LocalDate.of(2024, 6, 15), result.rateRecordDate());
    }

    @Test
    void selectsRateOnSixMonthBoundary() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        LocalDate earliestAllowed = LocalDate.of(2023, 12, 15);
        when(treasuryClient.findRates(eq("Canada-Dollar"), eq(purchaseDate), eq(earliestAllowed)))
                .thenReturn(List.of(
                        new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "1.326", "2023-12-15")));

        CurrencyConversionService.ConversionResult result =
                conversionService.convert(new BigDecimal("100.00"), purchaseDate, "Canada-Dollar");

        assertEquals(new BigDecimal("1.326"), result.exchangeRate());
        assertEquals(new BigDecimal("132.60"), result.convertedAmount());
        assertEquals(LocalDate.of(2023, 12, 15), result.rateRecordDate());
    }

    @Test
    void usesFirstQualifyingRateInListOrder() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        when(treasuryClient.findRates(eq("Canada-Dollar"), eq(purchaseDate), any()))
                .thenReturn(List.of(
                        new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "1.326", "2023-12-31"),
                        new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "1.355", "2024-03-31")));

        CurrencyConversionService.ConversionResult result =
                conversionService.convert(new BigDecimal("100.00"), purchaseDate, "Canada-Dollar");

        assertEquals(new BigDecimal("1.326"), result.exchangeRate());
        assertEquals(LocalDate.of(2023, 12, 31), result.rateRecordDate());
    }

    @Test
    void throwsWhenExchangeRateIsNotNumeric() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        when(treasuryClient.findRates(eq("Canada-Dollar"), eq(purchaseDate), any()))
                .thenReturn(List.of(
                        new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "not-a-number", "2024-03-31")));

        Exception ex = assertThrows(Exception.class,
                () -> conversionService.convert(new BigDecimal("100.00"), purchaseDate, "Canada-Dollar"));
        assertInstanceOf(NumberFormatException.class, ex);
    }
}
