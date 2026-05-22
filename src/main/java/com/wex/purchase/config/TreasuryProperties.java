package com.wex.purchase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "treasury")
public class TreasuryProperties {

    private final Api api = new Api();
    private final Retry retry = new Retry();
    private final Cache cache = new Cache();

    public Api getApi() {
        return api;
    }

    public Retry getRetry() {
        return retry;
    }

    public Cache getCache() {
        return cache;
    }

    public static class Api {
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private long initialDelayMs = 500;
        private double multiplier = 2.0;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }
    }

    public static class Cache {
        /** Memory safety net for distinct currency/date-window lookups. */
        private int maximumSize = 5000;
        /** Financial hard stop: evict entries older than this many hours. */
        private int expireAfterWriteHours = 1;
        /** Background refresh interval for stale-but-not-expired entries. */
        private int refreshAfterWriteMinutes = 15;

        public int getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(int maximumSize) {
            this.maximumSize = maximumSize;
        }

        public int getExpireAfterWriteHours() {
            return expireAfterWriteHours;
        }

        public void setExpireAfterWriteHours(int expireAfterWriteHours) {
            this.expireAfterWriteHours = expireAfterWriteHours;
        }

        public int getRefreshAfterWriteMinutes() {
            return refreshAfterWriteMinutes;
        }

        public void setRefreshAfterWriteMinutes(int refreshAfterWriteMinutes) {
            this.refreshAfterWriteMinutes = refreshAfterWriteMinutes;
        }
    }
}
