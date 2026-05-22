package com.wex.purchase.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.listener.RetryListenerSupport;

@Configuration
@EnableRetry
@EnableConfigurationProperties(TreasuryProperties.class)
public class CacheRetryConfig {

    @Bean
    RetryListenerSupport treasuryRetryListener() {
        return new RetryListenerSupport() {
            private static final Logger log = LogManager.getLogger("TreasuryRetry");

            @Override
            public <T, E extends Throwable> void onError(
                    org.springframework.retry.RetryContext context,
                    org.springframework.retry.RetryCallback<T, E> callback,
                    Throwable throwable) {
                log.warn("Treasury API call failed (attempt {}/{}): {}",
                        context.getRetryCount() + 1,
                        context.getAttribute("maxAttempts"),
                        throwable.getMessage());
            }
        };
    }
}
