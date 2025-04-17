package com.emoney.til.hanghae99.day1.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DeadlockPrevention {

    public static void main(String[] args) {
        // 데드락이 발생하는 예제
        System.out.println("=== 데드락 발생 가능성 있는 코드 실행 ===");
        DeadlockRisk deadlockRisk = new DeadlockRisk();
        deadlockRisk.runDeadlockRisk();

        try {
            // 스레드가 실행될 시간을 주기 위해 잠시 대기
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 데드락을 방지하는 예제
        System.out.println("\n=== 데드락 방지 코드 실행 ===");
        DeadlockPrevention deadlockPrevention = new DeadlockPrevention();
        deadlockPrevention.runDeadlockPrevention();
    }

    /**
     * 데드락을 방지하는 방법을 보여주는 메서드
     */
    public void runDeadlockPrevention() {
        // 공유 자원을 나타내는 두 개의 ReentrantLock 객체
        final Lock firstLock = new ReentrantLock();
        final Lock secondLock = new ReentrantLock();

        // 첫 번째 스레드 - 자원을 획득하는 순서가 firstLock -> secondLock
        Thread thread1 = new Thread(() -> {
            boolean firstLockAcquired = false;
            boolean secondLockAcquired = false;

            try {
                // tryLock을 사용하여 락 획득 시도 (타임아웃 설정)
                firstLockAcquired = firstLock.tryLock(500, TimeUnit.MILLISECONDS);
                if (firstLockAcquired) {
                    System.out.println("Thread 1: 첫 번째 락 획득");

                    // 작업 시뮬레이션
                    Thread.sleep(100);

                    secondLockAcquired = secondLock.tryLock(500, TimeUnit.MILLISECONDS);
                    if (secondLockAcquired) {
                        System.out.println("Thread 1: 두 번째 락 획득");

                        // 두 락을 모두 획득한 후 작업 수행
                        System.out.println("Thread 1: 두 자원을 모두 사용하여 작업 수행 중...");
                        Thread.sleep(1000);
                    } else {
                        // 두 번째 락을 획득하지 못했을 때 대체 로직
                        System.out.println("Thread 1: 두 번째 락 획득 실패 - 대체 로직 수행");
                    }
                } else {
                    // 첫 번째 락을 획득하지 못했을 때 대체 로직
                    System.out.println("Thread 1: 첫 번째 락 획득 실패 - 대체 로직 수행");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                // 중요: 획득한 락은 반드시 해제
                if (secondLockAcquired) {
                    secondLock.unlock();
                    System.out.println("Thread 1: 두 번째 락 해제");
                }
                if (firstLockAcquired) {
                    firstLock.unlock();
                    System.out.println("Thread 1: 첫 번째 락 해제");
                }
            }
        }, "Thread-1");

        // 두 번째 스레드 - 자원을 획득하는 순서가 다름에도 데드락을 방지
        Thread thread2 = new Thread(() -> {
            boolean firstLockAcquired = false;
            boolean secondLockAcquired = false;

            try {
                // 첫 번째 스레드와 동일한 순서로 락 획득 시도
                firstLockAcquired = firstLock.tryLock(500, TimeUnit.MILLISECONDS);
                if (firstLockAcquired) {
                    System.out.println("Thread 2: 첫 번째 락 획득");

                    // 작업 시뮬레이션
                    Thread.sleep(100);

                    secondLockAcquired = secondLock.tryLock(500, TimeUnit.MILLISECONDS);
                    if (secondLockAcquired) {
                        System.out.println("Thread 2: 두 번째 락 획득");

                        // 두 락을 모두 획득한 후 작업 수행
                        System.out.println("Thread 2: 두 자원을 모두 사용하여 작업 수행 중...");
                        Thread.sleep(1000);
                    } else {
                        // 두 번째 락을 획득하지 못했을 때 대체 로직
                        System.out.println("Thread 2: 두 번째 락 획득 실패 - 잠시 후 재시도");

                        // 첫 번째 락을 해제하고 잠시 대기 후 다시 시도하는 방식으로 데드락 방지
                        firstLock.unlock();
                        System.out.println("Thread 2: 첫 번째 락 해제 후 잠시 대기");
                        firstLockAcquired = false;

                        // 잠시 대기 후 다시 시도
                        Thread.sleep(200);

                        // 재시도 로직 (실제 코드에서는 이 부분을 재귀 호출이나 루프로 구현할 수 있음)
                        System.out.println("Thread 2: 재시도 중...");
                    }
                } else {
                    // 첫 번째 락을 획득하지 못했을 때 대체 로직
                    System.out.println("Thread 2: 첫 번째 락 획득 실패 - 대체 로직 수행");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                // 중요: 획득한 락은 반드시 해제
                if (secondLockAcquired) {
                    secondLock.unlock();
                    System.out.println("Thread 2: 두 번째 락 해제");
                }
                if (firstLockAcquired) {
                    firstLock.unlock();
                    System.out.println("Thread 2: 첫 번째 락 해제");
                }
            }
        }, "Thread-2");

        // 스레드 시작
        thread1.start();
        thread2.start();

        // 스레드 종료 대기
        try {
            thread1.join();
            thread2.join();
            System.out.println("데드락 방지 데모 완료!");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 데드락이 발생할 수 있는 위험한 코드 예제
     */
    static class DeadlockRisk {
        private final Object resource1 = new Object();
        private final Object resource2 = new Object();

        public void runDeadlockRisk() {
            // 첫 번째 스레드: resource1 -> resource2 순으로 락 획득 시도
            Thread thread1 = new Thread(() -> {
                try {
                    synchronized (resource1) {
                        System.out.println("Thread A: 자원 1 락 획득");

                        // 의도적으로 지연시켜 데드락 가능성 증가
                        Thread.sleep(100);

                        // resource2 락 획득 시도
                        synchronized (resource2) {
                            System.out.println("Thread A: 자원 2 락 획득");
                            // 작업 수행
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Thread-A");

            // 두 번째 스레드: resource2 -> resource1 순으로 락 획득 시도 (순서가 다름!)
            Thread thread2 = new Thread(() -> {
                try {
                    synchronized (resource2) {
                        System.out.println("Thread B: 자원 2 락 획득");

                        // 의도적으로 지연시켜 데드락 가능성 증가
                        Thread.sleep(100);

                        // resource1 락 획득 시도
                        synchronized (resource1) {
                            System.out.println("Thread B: 자원 1 락 획득");
                            // 작업 수행
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Thread-B");

            // 두 스레드 시작
            thread1.start();
            thread2.start();

            // 이 코드는 종종 데드락에 빠질 수 있음
            System.out.println("데드락 가능성 있는 스레드 시작됨");
        }
    }
}