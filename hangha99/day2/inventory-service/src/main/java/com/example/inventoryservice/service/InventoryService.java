package com.example.inventoryservice.service;

import com.example.inventoryservice.event.OrderEvent;
import com.example.inventoryservice.model.Inventory;
import com.example.inventoryservice.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InventoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);
    
    private final InventoryRepository inventoryRepository;
    private final StreamBridge streamBridge;
    
    @Autowired
    public InventoryService(InventoryRepository inventoryRepository, StreamBridge streamBridge) {
        this.inventoryRepository = inventoryRepository;
        this.streamBridge = streamBridge;
    }
    
    @Transactional
    public void processOrderCreatedEvent(OrderEvent event) {
        logger.info("Processing order created event: {}", event);
        
        String productId = event.getProductId();
        int requiredQuantity = event.getQuantity();
        
        // 재고 조회 (비관적 락 적용)
        Optional<Inventory> optionalInventory = inventoryRepository.findById(productId);
        
        if (optionalInventory.isEmpty()) {
            logger.error("Product not found in inventory: {}", productId);
            rejectOrder(event);
            return;
        }
        
        Inventory inventory = optionalInventory.get();
        
        // 재고 확인 및 감소
        if (inventory.getAvailableQuantity() >= requiredQuantity) {
            // 충분한 재고가 있으면 재고 감소
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - requiredQuantity);
            inventoryRepository.save(inventory);
            
            logger.info("Inventory updated for product: {}, new quantity: {}", 
                        productId, inventory.getAvailableQuantity());
            
            // 재고 확인 이벤트 발행
            confirmOrder(event);
        } else {
            // 재고 부족시 주문 거부
            logger.warn("Insufficient inventory for product: {}, available: {}, required: {}",
                       productId, inventory.getAvailableQuantity(), requiredQuantity);
            
            rejectOrder(event);
        }
    }
    
    @Transactional
    public void processOrderCancelledEvent(OrderEvent event) {
        logger.info("Processing order cancelled event: {}", event);
        
        String productId = event.getProductId();
        int quantity = event.getQuantity();
        
        // 재고 조회 및 원복 (비관적 락 적용)
        Optional<Inventory> optionalInventory = inventoryRepository.findById(productId);
        
        if (optionalInventory.isPresent()) {
            Inventory inventory = optionalInventory.get();
            
            // 재고 복원 (보상 트랜잭션)
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
            inventoryRepository.save(inventory);
            
            logger.info("Inventory restored for product: {}, new quantity: {}", 
                        productId, inventory.getAvailableQuantity());
        } else {
            logger.error("Cannot restore inventory for non-existent product: {}", productId);
        }
    }
    
    private void confirmOrder(OrderEvent event) {
        OrderEvent confirmEvent = OrderEvent.inventoryConfirmedEvent(
            event.getOrderId(),
            event.getProductId(),
            event.getQuantity()
        );
        
        boolean sent = streamBridge.send("inventoryResponse-out-0", confirmEvent);
        
        if (!sent) {
            logger.error("Failed to send inventory confirmed event for order: {}", event.getOrderId());
            throw new RuntimeException("Failed to process inventory response due to messaging failure");
        }
    }
    
    private void rejectOrder(OrderEvent event) {
        OrderEvent rejectEvent = OrderEvent.inventoryRejectedEvent(
            event.getOrderId(),
            event.getProductId(),
            event.getQuantity()
        );
        
        boolean sent = streamBridge.send("inventoryResponse-out-0", rejectEvent);
        
        if (!sent) {
            logger.error("Failed to send inventory rejected event for order: {}", event.getOrderId());
            throw new RuntimeException("Failed to process inventory response due to messaging failure");
        }
    }
}
