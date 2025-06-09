package com.example.inventoryservice;

import com.example.inventoryservice.model.Inventory;
import com.example.inventoryservice.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class InventoryServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
    
    // 데모용 초기 데이터 설정
    @Bean
    public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
        return args -> {
            // 기존 데이터가 없는 경우에만 샘플 데이터 추가
            if (inventoryRepository.count() == 0) {
                inventoryRepository.save(new Inventory("PRODUCT-1", 100));
                inventoryRepository.save(new Inventory("PRODUCT-2", 50));
                inventoryRepository.save(new Inventory("PRODUCT-3", 10));
                inventoryRepository.save(new Inventory("PRODUCT-4", 0));
            }
        };
    }
}
