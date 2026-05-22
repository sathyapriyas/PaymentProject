package com.wex.purchase.client;

import com.wex.purchase.config.CacheRetryConfig;
import com.wex.purchase.config.TreasuryProperties;
import com.wex.purchase.dto.TreasuryRatesResponse;
import com.wex.purchase.exception.TreasuryApiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {
        CacheRetryConfig.class,
        TreasuryHttpClient.class,
        TreasuryHttpClientRetryTest.RestClientTestConfig.class
})
@EnableConfigurationProperties(TreasuryProperties.class)
@TestPropertySource(properties = {
        "treasury.api.base-url=http://localhost",
        "treasury.retry.max-attempts=3",
        "treasury.retry.initial-delay-ms=1",
        "treasury.retry.multiplier=1.0"
})
@SuppressWarnings({"rawtypes", "unchecked"})
class TreasuryHttpClientRetryTest {

    @Autowired
    private TreasuryHttpClient httpClient;

    @Autowired
    private RestClient treasuryRestClient;

    @BeforeEach
    void resetMock() {
        org.mockito.Mockito.reset(treasuryRestClient);
    }

    @Test
    void retriesThenSucceedsOnThirdAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        TreasuryRatesResponse success = new TreasuryRatesResponse(List.of(
                new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "1.355", "2024-03-31")));

        when(treasuryRestClient.get().uri(any(Function.class)).retrieve().body(eq(TreasuryRatesResponse.class)))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new ResourceAccessException("timeout");
                    }
                    return success;
                });

        List<TreasuryRatesResponse.TreasuryRateRecord> rates = httpClient.fetchRates(
                "Canada-Dollar",
                LocalDate.of(2024, 6, 15),
                LocalDate.of(2023, 12, 15));

        assertEquals(1, rates.size());
        assertEquals(3, attempts.get());
    }

    @Test
    void throws503AfterExhaustingRetries() {
        AtomicInteger attempts = new AtomicInteger();
        when(treasuryRestClient.get().uri(any(Function.class)).retrieve().body(eq(TreasuryRatesResponse.class)))
                .thenAnswer(invocation -> {
                    attempts.incrementAndGet();
                    throw new ResourceAccessException("timeout");
                });

        assertThrows(TreasuryApiUnavailableException.class, () -> httpClient.fetchRates(
                "Canada-Dollar",
                LocalDate.of(2024, 6, 15),
                LocalDate.of(2023, 12, 15)));

        assertEquals(3, attempts.get());
    }

    @TestConfiguration
    static class RestClientTestConfig {
        @Bean
        RestClient treasuryRestClient() {
            return mock(RestClient.class, RETURNS_DEEP_STUBS);
        }
    }
}
