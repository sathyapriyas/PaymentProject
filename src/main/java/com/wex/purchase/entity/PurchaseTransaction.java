package com.wex.purchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "purchase_transactions")
public class PurchaseTransaction {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(nullable = false, length = 50)
    private String description;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountUsd;

    protected PurchaseTransaction() {
    }

    public PurchaseTransaction(UUID id, String description, LocalDate transactionDate, BigDecimal amountUsd) {
        this.id = id;
        this.description = description;
        this.transactionDate = transactionDate;
        this.amountUsd = amountUsd;
    }

    public UUID getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public BigDecimal getAmountUsd() {
        return amountUsd;
    }
}
