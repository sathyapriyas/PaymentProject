package com.wex.purchase.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyUtilsTest {

    @Test
    void roundsToNearestCentHalfUp() {
        assertEquals(new BigDecimal("10.13"), MoneyUtils.toCents(new BigDecimal("10.125")));
        assertEquals(new BigDecimal("10.12"), MoneyUtils.toCents(new BigDecimal("10.124")));
    }

    @Test
    void keepsTwoDecimalPlaces() {
        assertEquals(new BigDecimal("99.99"), MoneyUtils.toCents(new BigDecimal("99.99")));
    }
}
