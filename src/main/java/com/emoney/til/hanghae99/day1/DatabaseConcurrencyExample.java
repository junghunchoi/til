package com.emoney.til.hanghae99.day1;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 데이터베이스 동시성 문제 해결 예제
 * 이 클래스는 실제 데이터베이스를 사용하진 않지만, 데이터베이스 동시성 문제를 시뮬레이션하고
 * 다양한 해결책을 보여줍니다.
 */
public class DatabaseConcurrencyExample {
    
    // 데이터베이스 테이블을 시뮬레이션하는 맵
    private final ConcurrentHashMap<String, Product> products = new ConcurrentHashMap<>();
    
    // 낙관적 락을 사용하는 방식에서의 버전 충돌 카운터
    private final AtomicInteger versionConflicts = new AtomicInteger(0);
    
    // 특정 제품에 대한 락을 관리하는 맵 (비관적 락 시뮬레이션)
    private final ConcurrentHashMap<String, Lock> productLocks = new ConcurrentHashMap<>();
    
    /**
     * 제품 모델 클래스 - 낙관적 락을 위한 버전 필드 포함
     */
    public static class Product {
        private final String id;
        private int stock;
        private long version;
        
        public Product(String id, int stock) {
            this.id = id;
            this.stock = stock;
            this.version = 1;
        }
        
        public synchronized int getStock() {
            return stock;
        }
        
        public synchronized long getVersion() {
            return version;
        }
        
        public synchronized void setStock(int stock) {
            this.stock = stock;
        }
        
        public synchronized boolean decreaseStock(int quantity, long expectedVersion) {
            if (this.version != expectedVersion) {
                return false; // 낙관적 락 충돌
            }
            
            if (stock < quantity) {
                return false; // 재고 부족
            }
            
            stock -= quantity;
            version++; // 버전 증가
            return true;
        }
        
        public synchronized void decreaseStock(int quantity) {
            if (stock >= quantity) {
                stock -= quantity;
            }
        }
        
        @Override
        public String toString() {
            return "Product{" +
                    "id='" + id + '\'' +
                    ", stock=" + stock +
                    ", version=" + version +
                    '}';
        }
    }
    
    /**
     * 초기 데이터 설정
     */
    public DatabaseConcurrencyExample() {
        // 초기 상품 데이터 추가
        products.put("P1", new Product("P1", 100));
        products.put("P2", new Product("P2", 200));
        products.put("P3", new Product("P3", 150));
        
        // 제품별 락 초기화
        products.keySet().forEach(key -> productLocks.put(key, new ReentrantLock()));
    }
    
    /**
     * 낙관적 락을 사용한 재고 감소 (JPA @Version 어노테이션 시뮬레이션)
     */
    public boolean decreaseStockWithOptimisticLock(String productId, int quantity) {
        boolean success = false;
        int maxRetries = 3;
        int retries = 0;
        
        while (!success && retries < maxRetries) {
            Product product = products.get(productId);
            if (product == null) {
                return false;
            }
            
            long currentVersion = product.getVersion();
            
            if (product.decreaseStock(quantity, currentVersion)) {
                success = true;
            } else {
                versionConflicts.incrementAndGet();
                retries++;
                
                // 실제 환경에서는 여기서 약간의 지연을 추가할 수 있음
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        return success;
    }
    
    /**
     * 비관적 락을 사용한 재고 감소 (SELECT FOR UPDATE 시뮬레이션)
     */
    public boolean decreaseStockWithPessimisticLock(String productId, int quantity) {
        Lock lock = productLocks.get(productId);
        if (lock == null) {
            return false;
        }
        
        lock.lock();
        try {
            Product product = products.get(productId);
            if (product == null) {
                return false;
            }
            
            if (product.getStock() < quantity) {
                return false;
            }
            
            product.decreaseStock(quantity);
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 동시성 테스트를 위한 메서드
     */
    public void testConcurrency(int threadCount, String method) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 테스트 전 상품 상태 출력
        System.out.println("테스트 전 상태: " + products.get("P1"));
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean result;
                    
                    if ("optimistic".equals(method)) {
                        result = decreaseStockWithOptimisticLock("P1", 1);
                    } else {
                        result = decreaseStockWithPessimisticLock("P1", 1);
                    }
                    
                    // System.out.println(Thread.currentThread().getName() + ": " + result);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // 테스트 후 상품 상태 출력
        System.out.println("테스트 후 상태: " + products.get("P1"));
        if ("optimistic".equals(method)) {
            System.out.println("낙관적 락 충돌 횟수: " + versionConflicts.get());
        }
    }
    
    /**
     * 메인 메서드
     */
    public static void main(String[] args) throws InterruptedException {
        int threadCount = 5;
        
        System.out.println("===== 낙관적 락 테스트 =====");
        DatabaseConcurrencyExample optimisticTest = new DatabaseConcurrencyExample();
        optimisticTest.testConcurrency(threadCount, "optimistic");
        
        System.out.println("\n===== 비관적 락 테스트 =====");
        DatabaseConcurrencyExample pessimisticTest = new DatabaseConcurrencyExample();
        pessimisticTest.testConcurrency(threadCount, "pessimistic");
    }
}
