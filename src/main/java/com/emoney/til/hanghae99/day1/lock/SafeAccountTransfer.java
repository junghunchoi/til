package com.emoney.til.hanghae99.day1.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 계좌 이체 시 데드락을 방지하는 예제
 *
 * 이 예제는 계좌 간 이체 작업에서 락을 획득할 때 발생할 수 있는 데드락을
 * 타임아웃과 락 해제 및 재시도 전략을 통해 방지하는 방법을 보여줍니다.
 */
public class SafeAccountTransfer {

    public static void main(String[] args) {
        // 계좌 생성
        Account account1 = new Account(1, 1000);
        Account account2 = new Account(2, 2000);

        // 이체 작업을 수행할 스레드 생성
        Thread thread1 = new Thread(() -> {
            // 계좌1 -> 계좌2로 500원 이체
            boolean success = transfer(account1, account2, 500);
            System.out.println("Thread 1 이체 결과: " + (success ? "성공" : "실패"));
        }, "Transfer-Thread-1");

        Thread thread2 = new Thread(() -> {
            // 동시에 계좌2 -> 계좌1로 300원 이체
            boolean success = transfer(account2, account1, 300);
            System.out.println("Thread 2 이체 결과: " + (success ? "성공" : "실패"));
        }, "Transfer-Thread-2");

        // 스레드 시작
        thread1.start();
        thread2.start();

        // 스레드 종료 대기
        try {
            thread1.join();
            thread2.join();

            // 최종 계좌 잔액 출력
            System.out.println("\n최종 계좌 상태:");
            System.out.println("계좌 1 잔액: " + account1.getBalance());
            System.out.println("계좌 2 잔액: " + account2.getBalance());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 데드락을 방지하면서 안전하게 계좌 이체를 수행하는 메서드
     *
     * @param fromAccount 출금 계좌
     * @param toAccount 입금 계좌
     * @param amount 이체 금액
     * @return 이체 성공 여부
     */
    public static boolean transfer(Account fromAccount, Account toAccount, int amount) {
        // 최대 재시도 횟수
        int retryCount = 3;

        while (retryCount > 0) {
            boolean fromLockAcquired = false;
            boolean toLockAcquired = false;

            try {
                // 출금 계좌 락 획득 시도 (타임아웃 설정)
                fromLockAcquired = fromAccount.getLock().tryLock(200, TimeUnit.MILLISECONDS);
                if (!fromLockAcquired) {
                    System.out.println(Thread.currentThread().getName() + ": 출금 계좌 락 획득 실패, 재시도 중...");
                    retryCount--;
                    continue;
                }

                System.out.println(Thread.currentThread().getName() + ": 출금 계좌 " + fromAccount.getId() + " 락 획득");

                // 입금 계좌 락 획득 시도 (타임아웃 설정)
                toLockAcquired = toAccount.getLock().tryLock(200, TimeUnit.MILLISECONDS);
                if (!toLockAcquired) {
                    System.out.println(Thread.currentThread().getName() + ": 입금 계좌 락 획득 실패, 재시도 중...");
                    retryCount--;
                    continue;
                }

                System.out.println(Thread.currentThread().getName() + ": 입금 계좌 " + toAccount.getId() + " 락 획득");

                // 출금 가능 여부 확인
                if (fromAccount.getBalance() < amount) {
                    System.out.println(Thread.currentThread().getName() + ": 잔액 부족!");
                    return false;
                }

                // 이체 작업 시뮬레이션 (출금 후 입금)
                System.out.println(Thread.currentThread().getName() + ": 계좌 " + fromAccount.getId() +
                    "에서 " + toAccount.getId() + "로 " + amount + "원 이체 중...");

                // 실제 이체 작업 수행
                fromAccount.withdraw(amount);

                // 네트워크 지연 시뮬레이션
                Thread.sleep(100);

                toAccount.deposit(amount);

                System.out.println(Thread.currentThread().getName() + ": 이체 완료!");
                return true;

            } catch (InterruptedException e) {
                System.out.println(Thread.currentThread().getName() + ": 인터럽트 발생");
                return false;
            } finally {
                // 중요: 획득한 락은 반드시 해제 (역순으로 해제)
                if (toLockAcquired) {
                    toAccount.getLock().unlock();
                    System.out.println(Thread.currentThread().getName() + ": 입금 계좌 " + toAccount.getId() + " 락 해제");
                }
                if (fromLockAcquired) {
                    fromAccount.getLock().unlock();
                    System.out.println(Thread.currentThread().getName() + ": 출금 계좌 " + fromAccount.getId() + " 락 해제");
                }
            }
        }

        System.out.println(Thread.currentThread().getName() + ": 최대 재시도 횟수 초과, 이체 실패");
        return false;
    }

    /**
     * 계좌 클래스
     */
    static class Account {
        private final int id;
        private int balance;
        private final Lock lock;

        public Account(int id, int initialBalance) {
            this.id = id;
            this.balance = initialBalance;
            this.lock = new ReentrantLock();
        }

        public int getId() {
            return id;
        }

        public int getBalance() {
            return balance;
        }

        public Lock getLock() {
            return lock;
        }

        public void withdraw(int amount) {
            balance -= amount;
        }

        public void deposit(int amount) {
            balance += amount;
        }
    }
}
