package com.wex.purchase.controller;

import com.wex.purchase.dto.ConvertedPurchaseResponse;
import com.wex.purchase.dto.CreatePurchaseRequest;
import com.wex.purchase.dto.StoredPurchaseResponse;
import com.wex.purchase.service.PurchaseService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/purchases")
@Validated
public class PurchaseController {

    private static final Logger log = LogManager.getLogger(PurchaseController.class);

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<StoredPurchaseResponse> store(@Valid @RequestBody CreatePurchaseRequest request) {
        log.info("Received store purchase request: date={}, amountUsd={}",
                request.transactionDate(), request.purchaseAmountUsd());
        StoredPurchaseResponse response = purchaseService.store(request);
        log.info("Stored purchase: id={}", response.identifier());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ConvertedPurchaseResponse retrieve(
            @PathVariable UUID id,
            @RequestParam("currency") @NotBlank String currency) {
        log.info("Received retrieve request: id={}, currency={}", id, currency);
        ConvertedPurchaseResponse response = purchaseService.retrieveConverted(id, currency.trim());
        log.info("Retrieved converted purchase: id={}, convertedAmount={}", id, response.convertedAmount());
        return response;
    }
}
