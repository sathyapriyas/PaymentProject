package com.wex.purchase.client;

import com.wex.purchase.dto.TreasuryRatesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class TreasuryExchangeRateClientCacheTest {

    @Autowired
    private TreasuryExchangeRateClient client;

    @Autowired
    private TreasuryRateCache rateCache;

    @MockBean
    private TreasuryHttpClient httpClient;

    @BeforeEach
    void clearCache() {
        rateCache.invalidateAll();
        org.mockito.Mockito.reset(httpClient);
    }

    @Test
    void cachesTreasuryRatesForSameLookupKey() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        LocalDate earliest = purchaseDate.minusMonths(6);
        List<TreasuryRatesResponse.TreasuryRateRecord> rates = List.of(
                new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "1.355", "2024-03-31"));

        when(httpClient.fetchRates(eq("Canada-Dollar"), eq(purchaseDate), eq(earliest))).thenReturn(rates);

        client.findRates("Canada-Dollar", purchaseDate, earliest);
        client.findRates("Canada-Dollar", purchaseDate, earliest);

        verify(httpClient, times(1)).fetchRates(eq("Canada-Dollar"), eq(purchaseDate), eq(earliest));
    }

    @Test
    void doesNotUseCacheForDifferentCurrency() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        LocalDate earliest = purchaseDate.minusMonths(6);

        when(httpClient.fetchRates(any(), eq(purchaseDate), eq(earliest)))
                .thenReturn(List.of());

        client.findRates("Canada-Dollar", purchaseDate, earliest);
        client.findRates("Japan-Yen", purchaseDate, earliest);

        verify(httpClient, times(1)).fetchRates(eq("Canada-Dollar"), eq(purchaseDate), eq(earliest));
        verify(httpClient, times(1)).fetchRates(eq("Japan-Yen"), eq(purchaseDate), eq(earliest));
    }
}
