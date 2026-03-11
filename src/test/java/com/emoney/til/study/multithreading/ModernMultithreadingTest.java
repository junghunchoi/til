package com.emoney.til.study.multithreading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 현대 자바 멀티스레드 학습 테스트
 * - ExecutorService (Fixed, Cached, Scheduled)
 * - ForkJoinPool (RecursiveTask, Work Stealing)
 * - CompletableFuture (비동기 체이닝, 조합, 예외 처리)
 */
class ModernMultithreadingTest {

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== ExecutorService ====================

    @Test
    @DisplayName("Fixed Thread Pool: 고정 크기 스레드 풀로 작업 실행")
    void fixedThreadPool() throws Exception {
        // given
        int poolSize = 3;
        executor = Executors.newFixedThreadPool(poolSize);
        AtomicInteger counter = new AtomicInteger(0);

        // when: 10개 작업을 3개 스레드로 처리
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(() -> {
                Thread.sleep(100);  // 작업 시뮬레이션
                return counter.incrementAndGet();
            }));
        }

        // then: 모든 작업 완료 대기
        for (Future<Integer> future : futures) {
            Integer result = future.get();
            System.out.println("Task completed: " + result);
        }

        assertThat(counter.get()).isEqualTo(10);
    }

    @Test
    @DisplayName("Callable vs Runnable: 반환값 유무 차이")
    void callableVsRunnable() throws Exception {
        // given
        executor = Executors.newFixedThreadPool(2);

        // when: Runnable (반환값 없음)
        Future<?> runnableFuture = executor.submit(() -> {
            System.out.println("Runnable executed");
        });

        // when: Callable (반환값 있음)
        Future<String> callableFuture = executor.submit(() -> {
            Thread.sleep(100);
            return "Callable result";
        });

        // then
        assertThat(runnableFuture.get()).isNull();
        assertThat(callableFuture.get()).isEqualTo("Callable result");
    }

    @Test
    @DisplayName("Scheduled Executor: 지연 및 주기적 실행")
    void scheduledExecutor() throws Exception {
        // given
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        AtomicInteger counter = new AtomicInteger(0);

        try {
            // when: 1초 후 실행
            ScheduledFuture<?> delayed = scheduler.schedule(() -> {
                counter.incrementAndGet();
                System.out.println("Delayed task executed");
            }, 1, TimeUnit.SECONDS);

            // when: 초기 0초, 이후 500ms마다 실행
            ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(() -> {
                int value = counter.incrementAndGet();
                System.out.println("Periodic task: " + value);
            }, 0, 500, TimeUnit.MILLISECONDS);

            // then: 2초 대기
            Thread.sleep(2000);
            periodic.cancel(false);

            assertThat(counter.get()).isGreaterThan(3);  // 최소 4번 실행 (delayed 1회 + periodic 3회)
        } finally {
            scheduler.shutdown();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ThreadPoolExecutor: 커스텀 설정과 RejectionPolicy")
    void threadPoolExecutorWithRejection() throws Exception {
        // given: 작은 풀과 큐 (rejection 유발)
        ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(
                1,                              // corePoolSize
                2,                              // maximumPoolSize
                60L, TimeUnit.SECONDS,          // keepAliveTime
                new LinkedBlockingQueue<>(2),   // 큐 크기 2
                new ThreadPoolExecutor.CallerRunsPolicy()  // Caller가 직접 실행
        );

        AtomicInteger completedTasks = new AtomicInteger(0);

        // when: 10개 작업 제출 (풀과 큐를 초과)
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            customExecutor.submit(() -> {
                System.out.println("Task " + taskId + " executed by " + Thread.currentThread().getName());
                Thread.sleep(200);
                completedTasks.incrementAndGet();
                return null;
            });
        }

        // then: CallerRunsPolicy로 인해 제출 스레드도 작업 실행
        customExecutor.shutdown();
        customExecutor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(completedTasks.get()).isEqualTo(10);
    }

    @Test
    @DisplayName("ExecutorService 종료 패턴: shutdown + awaitTermination")
    void executorShutdownPattern() throws Exception {
        // given
        ExecutorService testExecutor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(3);

        // when: 작업 제출
        for (int i = 0; i < 3; i++) {
            testExecutor.submit(() -> {
                sleep(500);
                latch.countDown();
            });
        }

        // then: 정상 종료 패턴
        testExecutor.shutdown();  // 새 작업 거부
        assertThat(testExecutor.isShutdown()).isTrue();
        assertThat(testExecutor.isTerminated()).isFalse();  // 아직 작업 진행 중

        boolean terminated = testExecutor.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(terminated).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
    }

    // ==================== ForkJoinPool ====================

    @Test
    @DisplayName("RecursiveTask: 분할 정복으로 배열 합계 계산")
    void recursiveTaskSum() {
        // given
        long[] array = IntStream.range(0, 1_000_000)
                .mapToLong(i -> i)
                .toArray();

        // when: Fork/Join으로 병렬 계산
        ForkJoinPool pool = new ForkJoinPool();
        long result = pool.invoke(new SumTask(array, 0, array.length));

        // then
        long expectedSum = IntStream.range(0, 1_000_000)
                .mapToLong(i -> i)
                .sum();
        assertThat(result).isEqualTo(expectedSum);

        System.out.println("Sum: " + result);
        System.out.println("Parallelism: " + pool.getParallelism());
        System.out.println("Steal Count: " + pool.getStealCount());
    }

    @Test
    @DisplayName("RecursiveAction: 배열 요소 증가 (반환값 없음)")
    void recursiveActionIncrement() {
        // given
        int[] array = new int[10000];

        // when: Fork/Join으로 병렬 증가
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(new IncrementTask(array, 0, array.length));

        // then: 모든 요소가 1 증가
        assertThat(array[0]).isEqualTo(1);
        assertThat(array[5000]).isEqualTo(1);
        assertThat(array[9999]).isEqualTo(1);
    }

    @Test
    @DisplayName("ForkJoinPool.commonPool: 전역 공유 풀")
    void commonPool() {
        // given
        ForkJoinPool commonPool = ForkJoinPool.commonPool();

        // when
        int parallelism = commonPool.getParallelism();
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        // then: commonPool 크기 = CPU 코어 수 - 1
        System.out.println("Common Pool Parallelism: " + parallelism);
        System.out.println("Available Processors: " + availableProcessors);

        assertThat(parallelism).isGreaterThan(0);
    }

    // ==================== CompletableFuture ====================

    @Test
    @DisplayName("CompletableFuture: 기본 생성과 완료")
    void completableFutureBasics() throws Exception {
        // 1. 즉시 완료
        CompletableFuture<String> completed = CompletableFuture.completedFuture("done");
        assertThat(completed.get()).isEqualTo("done");

        // 2. supplyAsync: 비동기 작업
        CompletableFuture<String> async = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "async result";
        });
        assertThat(async.get()).isEqualTo("async result");

        // 3. runAsync: 반환값 없음
        CompletableFuture<Void> runAsync = CompletableFuture.runAsync(() -> {
            System.out.println("runAsync executed");
        });
        assertThat(runAsync.get()).isNull();
    }

    @Test
    @DisplayName("thenApply: 값 변환 체이닝")
    void thenApply() throws Exception {
        // given
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> "123")
                .thenApply(Integer::parseInt)   // String → Integer
                .thenApply(i -> i * 2);          // Integer → Integer

        // then
        assertThat(future.get()).isEqualTo(246);
    }

    @Test
    @DisplayName("thenCompose: 비동기 flatMap")
    void thenCompose() throws Exception {
        // given
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> 1L)
                .thenCompose(userId -> fetchUserAsync(userId));  // Long → CF<String>

        // then
        assertThat(future.get()).isEqualTo("User-1");
    }

    @Test
    @DisplayName("thenCombine: 두 CF를 병렬 실행 후 결합")
    void thenCombine() throws Exception {
        // given
        CompletableFuture<String> nameFuture = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "John";
        });

        CompletableFuture<Integer> ageFuture = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return 30;
        });

        // when: 두 작업을 병렬 실행 후 결합
        long startTime = System.currentTimeMillis();
        CompletableFuture<String> combined = nameFuture.thenCombine(
                ageFuture,
                (name, age) -> name + " is " + age + " years old"
        );
        long duration = System.currentTimeMillis() - startTime;

        // then: 병렬 실행으로 약 100ms (순차면 200ms)
        assertThat(combined.get()).isEqualTo("John is 30 years old");
        assertThat(duration).isLessThan(150);  // 병렬 실행 확인
    }

    @Test
    @DisplayName("allOf: 모든 CF 완료 대기 (병렬)")
    void allOf() throws Exception {
        // given: 3개의 비동기 작업
        List<CompletableFuture<String>> futures = List.of(
                CompletableFuture.supplyAsync(() -> {
                    sleep(100);
                    return "Task-1";
                }),
                CompletableFuture.supplyAsync(() -> {
                    sleep(150);
                    return "Task-2";
                }),
                CompletableFuture.supplyAsync(() -> {
                    sleep(200);
                    return "Task-3";
                })
        );

        // when: 모든 작업 완료 대기
        long startTime = System.currentTimeMillis();
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        // then: 결과 수집
        allDone.join();
        long duration = System.currentTimeMillis() - startTime;

        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        assertThat(results).containsExactly("Task-1", "Task-2", "Task-3");
        assertThat(duration).isLessThan(250);  // 병렬로 약 200ms
    }

    @Test
    @DisplayName("anyOf: 가장 빠른 CF의 결과 반환")
    void anyOf() throws Exception {
        // given: 속도가 다른 3개 작업
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
            sleep(300);
            return "slow";
        });

        CompletableFuture<String> fast = CompletableFuture.supplyAsync(() -> {
            sleep(50);
            return "fast";
        });

        CompletableFuture<String> medium = CompletableFuture.supplyAsync(() -> {
            sleep(150);
            return "medium";
        });

        // when: 가장 빠른 결과만 사용
        CompletableFuture<Object> fastest = CompletableFuture.anyOf(slow, fast, medium);

        // then
        assertThat(fastest.get()).isEqualTo("fast");
    }

    @Test
    @DisplayName("exceptionally: 예외 처리 및 대체 값 제공")
    void exceptionally() throws Exception {
        // given: 예외가 발생하는 작업
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            if (Math.random() > -1) {  // 항상 예외 발생
                throw new RuntimeException("Error occurred");
            }
            return "success";
        }).exceptionally(ex -> {
            System.out.println("Exception handled: " + ex.getMessage());
            return "fallback";
        });

        // then: 예외가 처리되어 fallback 반환
        assertThat(future.get()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("handle: 정상/예외 모두 처리")
    void handle() throws Exception {
        // given
        CompletableFuture<String> successFuture = CompletableFuture.supplyAsync(() -> "success")
                .handle((result, ex) -> {
                    if (ex != null) {
                        return "error: " + ex.getMessage();
                    }
                    return "success: " + result;
                });

        CompletableFuture<String> errorFuture = CompletableFuture.supplyAsync(() -> {
                    throw new RuntimeException("failed");
                })
                .handle((result, ex) -> {
                    if (ex != null) {
                        return "error: " + ex.getCause().getMessage();
                    }
                    return "success: " + result;
                });

        // then
        assertThat(successFuture.get()).isEqualTo("success: success");
        assertThat(errorFuture.get()).isEqualTo("error: failed");
    }

    @Test
    @DisplayName("whenComplete: 결과 소비 (반환값 변경 불가)")
    void whenComplete() throws Exception {
        // given
        AtomicInteger callCount = new AtomicInteger(0);

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "result")
                .whenComplete((result, ex) -> {
                    callCount.incrementAndGet();
                    System.out.println("Completed with: " + result);
                });

        // then: whenComplete는 값을 변경하지 않음
        assertThat(future.get()).isEqualTo("result");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("커스텀 Executor 사용 (I/O 바운드)")
    void customExecutor() throws Exception {
        // given: I/O 작업용 전용 풀
        ExecutorService ioExecutor = Executors.newFixedThreadPool(10);

        try {
            // when: I/O 블로킹 작업을 전용 풀에서 실행
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                sleep(100);  // I/O 시뮬레이션
                return "IO result";
            }, ioExecutor);

            // then
            assertThat(future.get()).isEqualTo("IO result");
        } finally {
            ioExecutor.shutdown();
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("실전 예제: Fan-Out/Fan-In 패턴")
    void fanOutFanIn() throws Exception {
        // given: 여러 사용자 ID
        List<Long> userIds = List.of(1L, 2L, 3L, 4L, 5L);

        // when: 모든 사용자 정보를 병렬로 조회
        List<CompletableFuture<String>> userFutures = userIds.stream()
                .map(this::fetchUserAsync)
                .toList();

        // when: 모든 조회 완료 대기
        CompletableFuture<List<String>> allUsers = CompletableFuture.allOf(
                userFutures.toArray(new CompletableFuture[0])
        ).thenApply(v ->
                userFutures.stream()
                        .map(CompletableFuture::join)
                        .toList()
        );

        // then
        List<String> users = allUsers.get();
        assertThat(users).hasSize(5);
        assertThat(users).containsExactly("User-1", "User-2", "User-3", "User-4", "User-5");
    }

    // ==================== Helper Methods ====================

    /**
     * RecursiveTask: 배열 합계 계산
     */
    static class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 10_000;
        private final long[] array;
        private final int start, end;

        SumTask(long[] array, int start, int end) {
            this.array = array;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            int length = end - start;

            if (length <= THRESHOLD) {
                // 직접 계산
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += array[i];
                }
                return sum;
            }

            // 분할
            int mid = start + length / 2;
            SumTask leftTask = new SumTask(array, start, mid);
            SumTask rightTask = new SumTask(array, mid, end);

            leftTask.fork();  // 비동기 실행
            long rightResult = rightTask.compute();  // 현재 스레드에서 실행
            long leftResult = leftTask.join();  // 결과 대기

            return leftResult + rightResult;
        }
    }

    /**
     * RecursiveAction: 배열 요소 증가 (반환값 없음)
     */
    static class IncrementTask extends RecursiveAction {
        private static final int THRESHOLD = 1000;
        private final int[] array;
        private final int start, end;

        IncrementTask(int[] array, int start, int end) {
            this.array = array;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            if (end - start <= THRESHOLD) {
                for (int i = start; i < end; i++) {
                    array[i]++;
                }
                return;
            }

            int mid = start + (end - start) / 2;
            invokeAll(
                    new IncrementTask(array, start, mid),
                    new IncrementTask(array, mid, end)
            );
        }
    }

    /**
     * 비동기로 사용자 조회 (시뮬레이션)
     */
    private CompletableFuture<String> fetchUserAsync(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(50);  // 네트워크 지연 시뮬레이션
            return "User-" + userId;
        });
    }

    /**
     * Thread.sleep 래퍼 (unchecked exception)
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
