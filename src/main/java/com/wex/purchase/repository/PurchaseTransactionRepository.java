package com.wex.purchase.repository;

import com.wex.purchase.entity.PurchaseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseTransactionRepository extends JpaRepository<PurchaseTransaction, UUID> {
}
