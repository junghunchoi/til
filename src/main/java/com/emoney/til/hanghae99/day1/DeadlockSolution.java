package com.emoney.til.hanghae99.day1;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 데드락 문제를 해결한 예제 클래스
 * 다음의 방법으로 데드락을 방지합니다:
 * 1. ReentrantLock 사용
 * 2. tryLock() 메서드로 타임아웃 설정
 * 3. 일관된 순서로 리소스 잠금 획득
 */
public class DeadlockSolution {
    
    // 두 개의 리소스에 대한 잠금
    private final Lock lock1 = new ReentrantLock();
    private final Lock lock2 = new ReentrantLock();

    /**
     * 첫 번째 해결책: tryLock() 메서드를 사용하여 타임아웃 설정
     * 잠금을 획득하지 못하면 모든 잠금을 해제하고 다시 시도합니다.
     */
    public boolean operationWithTryLock() {
        try {
            if (lock1.tryLock()) {
                try {
                    System.out.println(Thread.currentThread().getName() + ": lock1 획득");
                    Thread.sleep(50); // 실제 작업 시뮬레이션
                    
                    if (lock2.tryLock()) {
                        try {
                            System.out.println(Thread.currentThread().getName() + ": lock2 획득");
                            // 두 리소스를 모두 획득한 후 수행할 작업
                            return true;
                        } finally {
                            lock2.unlock();
                            System.out.println(Thread.currentThread().getName() + ": lock2 해제");
                        }
                    }
                } finally {
                    lock1.unlock();
                    System.out.println(Thread.currentThread().getName() + ": lock1 해제");
                }
            }
            
            // 잠금 획득 실패 시 잠시 대기 후 재시도 (백오프 전략)
            Thread.sleep(10 + (long)(Math.random() * 50));
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 두 번째 해결책: 항상 동일한 순서로 잠금 획득
     * 모든 스레드가 동일한 순서로 리소스를 획득하면 데드락이 발생하지 않습니다.
     */
    public void operationWithOrderedLocks() {
        lock1.lock();
        try {
            System.out.println(Thread.currentThread().getName() + ": lock1 획득");
            
            try {
                Thread.sleep(100); // 실제 작업 시뮬레이션
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            lock2.lock();
            try {
                System.out.println(Thread.currentThread().getName() + ": lock2 획득");
                // 두 리소스를 모두 획득한 후 수행할 작업
            } finally {
                lock2.unlock();
                System.out.println(Thread.currentThread().getName() + ": lock2 해제");
            }
        } finally {
            lock1.unlock();
            System.out.println(Thread.currentThread().getName() + ": lock1 해제");
        }
    }

    /**
     * tryLock 방식을 사용하여 데드락 없이 작업을 실행합니다.
     */
    public void executeWithTryLock() {
        Thread thread1 = new Thread(() -> {
            boolean done = false;
            while (!done) {
                done = operationWithTryLock();
            }
        }, "Thread-1");
        
        Thread thread2 = new Thread(() -> {
            boolean done = false;
            while (!done) {
                done = operationWithTryLock();
            }
        }, "Thread-2");
        
        thread1.start();
        thread2.start();
        
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 순서 보장 방식을 사용하여 데드락 없이 작업을 실행합니다.
     */
    public void executeWithOrderedLocks() {
        Thread thread1 = new Thread(this::operationWithOrderedLocks, "Thread-1");
        Thread thread2 = new Thread(this::operationWithOrderedLocks, "Thread-2");
        
        thread1.start();
        thread2.start();
        
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        DeadlockSolution solution = new DeadlockSolution();
        
        System.out.println("tryLock 방식으로 실행:");
        solution.executeWithTryLock();
        
        System.out.println("\n순서 보장 방식으로 실행:");
        solution.executeWithOrderedLocks();
    }
}
