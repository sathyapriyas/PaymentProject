package com.wex.purchase.client;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.wex.purchase.config.TreasuryProperties;
import com.wex.purchase.dto.TreasuryRatesResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Caffeine {@link LoadingCache} for Treasury exchange rate responses with refresh and expiry policies.
 */
@Component
public class TreasuryRateCache {

    private static final Logger log = LogManager.getLogger(TreasuryRateCache.class);

    private final LoadingCache<TreasuryRateCacheKey, List<TreasuryRatesResponse.TreasuryRateRecord>> exchangeRateCache;

    public TreasuryRateCache(TreasuryHttpClient httpClient, TreasuryProperties treasuryProperties) {
        TreasuryProperties.Cache cacheProps = treasuryProperties.getCache();
        this.exchangeRateCache = Caffeine.newBuilder()
                .maximumSize(cacheProps.getMaximumSize())
                .expireAfterWrite(Duration.ofHours(cacheProps.getExpireAfterWriteHours()))
                .refreshAfterWrite(Duration.ofMinutes(cacheProps.getRefreshAfterWriteMinutes()))
                .recordStats()
                .build(key -> loadRates(httpClient, key));
    }

    public List<TreasuryRatesResponse.TreasuryRateRecord> get(
            String targetCurrency,
            LocalDate purchaseDate,
            LocalDate earliestAllowedDate) {
        TreasuryRateCacheKey key = new TreasuryRateCacheKey(targetCurrency, purchaseDate, earliestAllowedDate);
        log.debug("Loading Treasury rates from cache: key={}", key.cacheKey());
        List<TreasuryRatesResponse.TreasuryRateRecord> rates = exchangeRateCache.get(key);
        logCacheStatsAtDebug();
        return rates;
    }

    public void invalidateAll() {
        exchangeRateCache.invalidateAll();
    }

    public CacheStats stats() {
        return exchangeRateCache.stats();
    }

    private static List<TreasuryRatesResponse.TreasuryRateRecord> loadRates(
            TreasuryHttpClient httpClient,
            TreasuryRateCacheKey key) {
        log.info("Cache loader fetching Treasury rates: key={}", key.cacheKey());
        return httpClient.fetchRates(key.targetCurrency(), key.purchaseDate(), key.earliestAllowedDate());
    }

    private void logCacheStatsAtDebug() {
        if (log.isDebugEnabled()) {
            CacheStats stats = exchangeRateCache.stats();
            log.debug("Treasury cache stats: hitRate={}, loadCount={}, evictionCount={}",
                    stats.hitRate(), stats.loadCount(), stats.evictionCount());
        }
    }
}
