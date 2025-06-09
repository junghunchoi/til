package com.example.inventoryservice.repository;

import com.example.inventoryservice.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {
    
    // 비관적 락을 통해 동시성 문제 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findById(String productId);
}
