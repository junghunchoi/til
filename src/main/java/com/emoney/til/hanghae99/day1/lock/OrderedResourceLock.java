package com.emoney.til.hanghae99.day1.lock;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 순서화된 락 획득을 통한 데드락 방지 예제
 *
 * 데드락을 방지하는 가장 효과적인 방법 중 하나는 모든 스레드가 동일한 순서로
 * 락을 획득하도록 강제하는 것입니다. 이 예제에서는 자원에 고유 ID를 부여하고
 * ID 순서대로 락을 획득하는 방법을 보여줍니다.
 */
public class OrderedResourceLock {

    public static void main(String[] args) {
        // 여러 자원 생성
        Resource resource1 = new Resource(1, "Database");
        Resource resource2 = new Resource(2, "File");
        Resource resource3 = new Resource(3, "Network");

        // 여러 작업 스레드 생성
        Thread task1 = new Thread(() -> {
            // 자원 1과 자원 3을 사용하는 작업
            processResources(resource1, resource3);
        }, "Task-1");

        Thread task2 = new Thread(() -> {
            // 자원 2와 자원 1을 사용하는 작업 (순서가 다름에도 데드락 방지)
            processResources(resource2, resource1);
        }, "Task-2");

        Thread task3 = new Thread(() -> {
            // 자원 3과 자원 2를 사용하는 작업
            processResources(resource3, resource2);
        }, "Task-3");

        // 모든 작업 시작
        task1.start();
        task2.start();
        task3.start();

        // 작업 완료 대기
        try {
            task1.join();
            task2.join();
            task3.join();
            System.out.println("모든 작업이 정상적으로 완료되었습니다.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 두 개의 자원을 안전하게 처리하는 메서드
     * 자원의 ID 순서에 따라 락을 획득하므로 데드락이 발생하지 않음
     */
    public static void processResources(Resource res1, Resource res2) {
        // ID가 낮은 자원과 높은 자원 결정
        Resource lowerIdResource;
        Resource higherIdResource;

        if (res1.getId() < res2.getId()) {
            lowerIdResource = res1;
            higherIdResource = res2;
        } else {
            lowerIdResource = res2;
            higherIdResource = res1;
        }

        // 항상 ID가 낮은 자원의 락부터 획득 (중요: 모든 스레드가 동일한 순서로 락 획득)
        try {
            lowerIdResource.getLock().lock();
            System.out.println(Thread.currentThread().getName() + ": "
                + lowerIdResource.getName() + " 자원 락 획득 (ID: " + lowerIdResource.getId() + ")");

            // 작업 시뮬레이션
            Thread.sleep(100);

            higherIdResource.getLock().lock();
            System.out.println(Thread.currentThread().getName() + ": "
                + higherIdResource.getName() + " 자원 락 획득 (ID: " + higherIdResource.getId() + ")");

            // 두 자원을 모두 획득한 후 작업 수행
            System.out.println(Thread.currentThread().getName() + ": "
                + lowerIdResource.getName() + "와 " + higherIdResource.getName()
                + " 자원을 사용하여 작업 중...");

            // 실제 작업 시뮬레이션
            Thread.sleep(500);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 획득한 순서의 역순으로 락 해제
            System.out.println(Thread.currentThread().getName() + ": "
                + higherIdResource.getName() + " 자원 락 해제 (ID: " + higherIdResource.getId() + ")");
            higherIdResource.getLock().unlock();

            System.out.println(Thread.currentThread().getName() + ": "
                + lowerIdResource.getName() + " 자원 락 해제 (ID: " + lowerIdResource.getId() + ")");
            lowerIdResource.getLock().unlock();
        }
    }

    /**
     * 자원 클래스
     */
    static class Resource {
        private final int id;
        private final String name;
        private final Lock lock;

        public Resource(int id, String name) {
            this.id = id;
            this.name = name;
            this.lock = new ReentrantLock();
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Lock getLock() {
            return lock;
        }
    }
}