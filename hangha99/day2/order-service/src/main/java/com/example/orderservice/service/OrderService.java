package com.example.orderservice.service;

import com.example.orderservice.event.OrderEvent;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    private final OrderRepository orderRepository;
    private final StreamBridge streamBridge;
    
    @Autowired
    public OrderService(OrderRepository orderRepository, StreamBridge streamBridge) {
        this.orderRepository = orderRepository;
        this.streamBridge = streamBridge;
    }
    
    @Transactional
    public Order createOrder(String productId, int quantity, BigDecimal price) {
        logger.info("Creating order for product: {}, quantity: {}", productId, quantity);
        
        // 1. 주문 생성 및 저장 (로컬 트랜잭션)
        Order order = new Order(productId, quantity, price.multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(Order.OrderStatus.PENDING);
        Order savedOrder = orderRepository.save(order);
        
        // 2. 주문 생성 이벤트 발행
        OrderEvent orderCreatedEvent = OrderEvent.orderCreatedEvent(savedOrder);
        boolean sent = streamBridge.send("orderCreated-out-0", orderCreatedEvent);
        
        if (!sent) {
            logger.error("Failed to send order created event for order: {}", savedOrder.getId());
            throw new RuntimeException("Failed to process order due to messaging failure");
        }
        
        logger.info("Order created with ID: {} and status: {}", savedOrder.getId(), savedOrder.getStatus());
        return savedOrder;
    }
    
    @Transactional
    public void processInventoryResponse(OrderEvent event) {
        logger.info("Processing inventory response: {}", event);
        
        Optional<Order> optionalOrder = orderRepository.findById(event.getOrderId());
        if (optionalOrder.isEmpty()) {
            logger.error("Order not found for ID: {}", event.getOrderId());
            return;
        }
        
        Order order = optionalOrder.get();
        
        if (event.getEventType() == OrderEvent.OrderEventType.INVENTORY_CONFIRMED) {
            logger.info("Inventory confirmed for order: {}", order.getId());
            order.setStatus(Order.OrderStatus.COMPLETED);
            orderRepository.save(order);
            
        } else if (event.getEventType() == OrderEvent.OrderEventType.INVENTORY_REJECTED) {
            logger.info("Inventory rejected for order: {}, cancelling order", order.getId());
            order.setStatus(Order.OrderStatus.CANCELLED);
            orderRepository.save(order);
            
            // 보상 트랜잭션은 필요없음 (재고가 감소되지 않았으므로)
        }
    }
    
    @Transactional(readOnly = true)
    public Optional<Order> getOrder(Long orderId) {
        return orderRepository.findById(orderId);
    }
}
