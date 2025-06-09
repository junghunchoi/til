package com.example.orderservice.consumer;

import com.example.orderservice.event.OrderEvent;
import com.example.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class InventoryResponseConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryResponseConsumer.class);
    
    private final OrderService orderService;
    
    @Autowired
    public InventoryResponseConsumer(OrderService orderService) {
        this.orderService = orderService;
    }
    
    @Bean
    public Consumer<OrderEvent> inventoryResponse() {
        return event -> {
            logger.info("Received inventory response event: {}", event);
            
            if (event.getEventType() == OrderEvent.OrderEventType.INVENTORY_CONFIRMED ||
                event.getEventType() == OrderEvent.OrderEventType.INVENTORY_REJECTED) {
                orderService.processInventoryResponse(event);
            } else {
                logger.warn("Received unexpected event type: {}", event.getEventType());
            }
        };
    }
}
