package com.emoney.til.hanghae99.day1;

/**
 * 데드락을 발생시키는 예제 클래스
 * 두 스레드가 서로 다른 순서로 두 개의 리소스를 획득하려고 할 때 데드락이 발생합니다.
 */
public class DeadlockExample {
    
    // 두 개의 리소스 객체
    private final Object resource1 = new Object();
    private final Object resource2 = new Object();

    /**
     * 첫 번째 스레드가 실행할 메서드
     * resource1을 먼저 획득한 후 resource2를 획득하려고 시도합니다.
     */
    public void operation1() {
        synchronized (resource1) {
            System.out.println("Thread 1: resource1 잠금 획득");
            
            try {
                // 데드락 발생 가능성을 높이기 위해 잠시 대기
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            System.out.println("Thread 1: resource2 잠금 시도...");
            
            synchronized (resource2) {
                System.out.println("Thread 1: resource2 잠금 획득");
                // 두 리소스를 모두 획득한 후 수행할 작업
            }
        }
    }

    /**
     * 두 번째 스레드가 실행할 메서드
     * resource2를 먼저 획득한 후 resource1을 획득하려고 시도합니다.
     * operation1과 반대 순서로 리소스를 획득하려고 해서 데드락이 발생합니다.
     */
    public void operation2() {
        synchronized (resource2) {
            System.out.println("Thread 2: resource2 잠금 획득");
            
            try {
                // 데드락 발생 가능성을 높이기 위해 잠시 대기
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            System.out.println("Thread 2: resource1 잠금 시도...");
            
            synchronized (resource1) {
                System.out.println("Thread 2: resource1 잠금 획득");
                // 두 리소스를 모두 획득한 후 수행할 작업
            }
        }
    }

    /**
     * 데드락 발생 시연을 위한 메서드
     */
    public void demonstrateDeadlock() {
        Thread thread1 = new Thread(this::operation1);
        Thread thread2 = new Thread(this::operation2);
        
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
        DeadlockExample example = new DeadlockExample();
        example.demonstrateDeadlock();
        // 이 코드는 데드락으로 인해 완료되지 않을 것입니다.
    }
}
