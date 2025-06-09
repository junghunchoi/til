package com.example.inventoryservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {
    
    @Id
    private String productId;
    private int availableQuantity;
    
    public Inventory() {
    }
    
    public Inventory(String productId, int availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
    }
    
    public String getProductId() {
        return productId;
    }
    
    public void setProductId(String productId) {
        this.productId = productId;
    }
    
    public int getAvailableQuantity() {
        return availableQuantity;
    }
    
    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }
    
    @Override
    public String toString() {
        return "Inventory{" +
                "productId='" + productId + '\'' +
                ", availableQuantity=" + availableQuantity +
                '}';
    }
}
