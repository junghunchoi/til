package com.example.inventoryservice.controller;

import com.example.inventoryservice.model.Inventory;
import com.example.inventoryservice.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    
    private final InventoryRepository inventoryRepository;
    
    @Autowired
    public InventoryController(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }
    
    @GetMapping
    public ResponseEntity<List<Inventory>> getAllInventories() {
        List<Inventory> inventories = inventoryRepository.findAll();
        return ResponseEntity.ok(inventories);
    }
    
    @GetMapping("/{productId}")
    public ResponseEntity<Inventory> getInventory(@PathVariable String productId) {
        Optional<Inventory> inventory = inventoryRepository.findById(productId);
        return inventory.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<Inventory> createInventory(@RequestBody Inventory inventory) {
        Inventory savedInventory = inventoryRepository.save(inventory);
        return new ResponseEntity<>(savedInventory, HttpStatus.CREATED);
    }
    
    @PutMapping("/{productId}")
    public ResponseEntity<Inventory> updateInventory(
            @PathVariable String productId, 
            @RequestBody Inventory inventory) {
        
        if (!inventoryRepository.existsById(productId)) {
            return ResponseEntity.notFound().build();
        }
        
        inventory.setProductId(productId);
        Inventory updatedInventory = inventoryRepository.save(inventory);
        return ResponseEntity.ok(updatedInventory);
    }
}
