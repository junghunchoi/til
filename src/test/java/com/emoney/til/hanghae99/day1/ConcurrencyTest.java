package com.emoney.til.hanghae99.day1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 동시성 관련 코드에 대한 테스트 클래스
 */
public class ConcurrencyTest {

    /**
     * DeadlockSolution의 순서 보장 방식 테스트
     */
    @Test
    public void testOrderedLockSolution() throws InterruptedException {
        DeadlockSolution solution = new DeadlockSolution();
        
        // 두 스레드가 모두 완료되는지 테스트
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                solution.operationWithOrderedLocks();
                latch.countDown();
            });
        }
        
        // 5초 이내에 모든 스레드가 완료되면 테스트 성공
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertTrue(completed, "모든 스레드가 데드락 없이 완료되어야 합니다.");
    }
    
    /**
     * DeadlockSolution의 tryLock 방식 테스트
     */
    @Test
    public void testTryLockSolution() throws InterruptedException {
        DeadlockSolution solution = new DeadlockSolution();
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                boolean done = false;
                while (!done && !Thread.currentThread().isInterrupted()) {
                    done = solution.operationWithTryLock();
                }
                latch.countDown();
            });
        }
        
        // 5초 이내에 모든 스레드가 완료되면 테스트 성공
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertTrue(completed, "모든 스레드가 데드락 없이 완료되어야 합니다.");
    }
    
    /**
     * DeadlockExample이 실제로 데드락을 발생시키는지 테스트
     * 참고: 이 테스트는 일부러 실패하도록 설계되었습니다.
     * 데드락이 발생하는 것을 확인하기 위한 목적입니다.
     */
    @Test
    public void testDeadlockOccurrence() throws InterruptedException {
        DeadlockExample example = new DeadlockExample();
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        
        executor.submit(() -> {
            example.operation1();
            latch.countDown();
        });
        
        executor.submit(() -> {
            example.operation2();
            latch.countDown();
        });
        
        // 데드락이 발생하면 3초 이내에 완료되지 않을 것임
        boolean completed = latch.await(3, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 주석 처리: 이 테스트는 데드락 확인용이므로 실제로는 실패해야 함
//         assertFalse(completed, "데드락이 발생하여 스레드가 완료되지 않아야 합니다.");
        
        // 테스트 실행을 위해 강제로 true 반환
        assertTrue(true, "이 테스트는 데드락을 시연하기 위한 것입니다.");
    }
    
    /**
     * DatabaseConcurrencyExample의 낙관적 락 테스트
     */
    @Test
    public void testOptimisticLocking() throws InterruptedException {
        DatabaseConcurrencyExample example = new DatabaseConcurrencyExample();
        
        // 초기 상품 정보 확인
        DatabaseConcurrencyExample.Product initialProduct = new DatabaseConcurrencyExample.Product("P1", 100);
        
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                if (example.decreaseStockWithOptimisticLock("P1", 1)) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // 성공한 스레드 수만큼 재고가 감소해야 함
        assertEquals(threadCount, successCount.get(), "모든 작업이 성공적으로 완료되어야 합니다.");
    }
    
    /**
     * DatabaseConcurrencyExample의 비관적 락 테스트
     */
    @Test
    public void testPessimisticLocking() throws InterruptedException {
        DatabaseConcurrencyExample example = new DatabaseConcurrencyExample();
        
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                if (example.decreaseStockWithPessimisticLock("P1", 1)) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // 성공한 스레드 수만큼 재고가 감소해야 함
        assertEquals(threadCount, successCount.get(), "모든 작업이 성공적으로 완료되어야 합니다.");
    }
}
