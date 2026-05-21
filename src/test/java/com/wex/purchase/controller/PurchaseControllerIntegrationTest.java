package com.wex.purchase.controller;

import com.wex.purchase.client.TreasuryExchangeRateClient;
import com.wex.purchase.dto.TreasuryRatesResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TreasuryExchangeRateClient treasuryClient;

    @Test
    void storeAndRetrievePurchaseWithConversion() throws Exception {
        when(treasuryClient.findRates(eq("Canada-Dollar"), any(), any()))
                .thenReturn(List.of(
                        new TreasuryRatesResponse.TreasuryRateRecord("Canada-Dollar", "1.355", "2024-03-31")));

        String createResponse = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Office supplies",
                                  "transactionDate": "2024-06-15",
                                  "purchaseAmountUsd": 100.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifier").exists())
                .andExpect(jsonPath("$.purchaseAmountUsd").value(100.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = createResponse.replaceAll(".*\"identifier\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/purchases/{id}", UUID.fromString(id))
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value(id))
                .andExpect(jsonPath("$.description").value("Office supplies"))
                .andExpect(jsonPath("$.transactionDate").value("2024-06-15"))
                .andExpect(jsonPath("$.originalAmountUsd").value(100.00))
                .andExpect(jsonPath("$.exchangeRate").value(1.355))
                .andExpect(jsonPath("$.targetCurrency").value("Canada-Dollar"))
                .andExpect(jsonPath("$.convertedAmount").value(135.50));
    }

    @Test
    void rejectsInvalidDescriptionLength() throws Exception {
        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "%s",
                                  "transactionDate": "2024-06-15",
                                  "purchaseAmountUsd": 10.00
                                }
                                """.formatted("x".repeat(51))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns422WhenConversionUnavailable() throws Exception {
        when(treasuryClient.findRates(eq("Japan-Yen"), any(), any())).thenReturn(List.of());

        String createResponse = mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Travel",
                                  "transactionDate": "2024-06-15",
                                  "purchaseAmountUsd": 50.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = createResponse.replaceAll(".*\"identifier\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/purchases/{id}", UUID.fromString(id))
                        .param("currency", "Japan-Yen"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("cannot be converted")));
    }

    @Test
    void returns404ForUnknownPurchase() throws Exception {
        mockMvc.perform(get("/api/purchases/{id}", UUID.randomUUID())
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isNotFound());
    }
}
