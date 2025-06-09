package com.example.inventoryservice.consumer;

import com.example.inventoryservice.event.OrderEvent;
import com.example.inventoryservice.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class OrderEventConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);
    
    private final InventoryService inventoryService;
    
    @Autowired
    public OrderEventConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }
    
    @Bean
    public Consumer<OrderEvent> orderEvents() {
        return event -> {
            logger.info("Received order event: {}", event);
            
            switch (event.getEventType()) {
                case ORDER_CREATED:
                    inventoryService.processOrderCreatedEvent(event);
                    break;
                case ORDER_CANCELLED:
                    inventoryService.processOrderCancelledEvent(event);
                    break;
                default:
                    logger.warn("Unhandled event type: {}", event.getEventType());
            }
        };
    }
}
