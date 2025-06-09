package com.example.inventoryservice.event;

public class OrderEvent {
    
    public enum OrderEventType {
        ORDER_CREATED, ORDER_CANCELLED, INVENTORY_CONFIRMED, INVENTORY_REJECTED
    }
    
    private Long orderId;
    private String productId;
    private int quantity;
    private OrderEventType eventType;
    
    public OrderEvent() {
    }
    
    public OrderEvent(Long orderId, String productId, int quantity, OrderEventType eventType) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.eventType = eventType;
    }
    
    public static OrderEvent inventoryConfirmedEvent(Long orderId, String productId, int quantity) {
        return new OrderEvent(
            orderId,
            productId,
            quantity,
            OrderEventType.INVENTORY_CONFIRMED
        );
    }
    
    public static OrderEvent inventoryRejectedEvent(Long orderId, String productId, int quantity) {
        return new OrderEvent(
            orderId,
            productId,
            quantity,
            OrderEventType.INVENTORY_REJECTED
        );
    }
    
    // Getters and Setters
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public String getProductId() {
        return productId;
    }
    
    public void setProductId(String productId) {
        this.productId = productId;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    
    public OrderEventType getEventType() {
        return eventType;
    }
    
    public void setEventType(OrderEventType eventType) {
        this.eventType = eventType;
    }
    
    @Override
    public String toString() {
        return "OrderEvent{" +
                "orderId=" + orderId +
                ", productId='" + productId + '\'' +
                ", quantity=" + quantity +
                ", eventType=" + eventType +
                '}';
    }
}
