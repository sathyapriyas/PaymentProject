package com.wex.purchase.controller;

import com.wex.purchase.dto.ConvertedPurchaseResponse;
import com.wex.purchase.dto.CreatePurchaseRequest;
import com.wex.purchase.dto.StoredPurchaseResponse;
import com.wex.purchase.service.PurchaseService;
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

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<StoredPurchaseResponse> store(@Valid @RequestBody CreatePurchaseRequest request) {
        StoredPurchaseResponse response = purchaseService.store(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ConvertedPurchaseResponse retrieve(
            @PathVariable UUID id,
            @RequestParam("currency") @NotBlank String currency) {
        return purchaseService.retrieveConverted(id, currency.trim());
    }
}
