package com.emoney.til.study.multithreading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실무 중심 자바 멀티스레드 학습 테스트
 * - CompletableFuture (비동기 체이닝, 조합, 예외 처리)
 * - Custom Executor (I/O 바운드 작업 처리)
 * - 실전 비동기 패턴 (Fan-Out/Fan-In)
 *
 */
class ModernMultithreadingTest {

    // ==================== CompletableFuture ====================

    @Test
    @DisplayName("CompletableFuture: 기본 생성과 완료")
    void completableFutureBasics() throws Exception {
        // 1. completedFuture: 이미 있는 값을 CompletableFuture로 감싸기
        // - 사용 시점: 캐시 히트, 조기 반환, 조건부 비동기 처리
        // - 비동기 X (즉시 완료)
        // - 실무 예: if (cached) return CompletableFuture.completedFuture(cachedValue);
        //           조건에 따라 비동기/동기를 섞어쓸 때, 일관된 반환 타입 유지
        CompletableFuture<String> completed = CompletableFuture.completedFuture("done");
        assertThat(completed.get()).isEqualTo("done");

        // 2. supplyAsync: 비동기로 값을 반환 (가장 많이 사용 ★★★)
        // - 사용 시점: 외부 API 호출, DB 조회, 무거운 계산, I/O 작업
        // - 비동기 O, 반환값 O
        // - 실무 예: CompletableFuture.supplyAsync(() -> restTemplate.getForObject(url, User.class))
        //           CompletableFuture.supplyAsync(() -> userRepository.findById(id))
        //           여러 API를 병렬로 호출할 때 필수
        CompletableFuture<String> async = CompletableFuture.supplyAsync(() -> {
            sleep(100);  // API 호출, DB 조회 시뮬레이션
            return "async result";
        });
        assertThat(async.get()).isEqualTo("async result");

        // 3. runAsync: 비동기로 실행하지만 반환값 없음 (Fire and Forget)
        // - 사용 시점: 로그 기록, 이벤트 발행, 알림 발송, 캐시 워밍업
        // - 비동기 O, 반환값 X
        // - 실무 예: CompletableFuture.runAsync(() -> emailService.sendWelcomeEmail(user))
        //           CompletableFuture.runAsync(() -> eventPublisher.publish(event))
        //           메인 로직의 응답 속도에 영향을 주지 않는 부가 작업
        CompletableFuture<Void> runAsync = CompletableFuture.runAsync(() -> {
            System.out.println("runAsync executed");
        });
        assertThat(runAsync.get()).isNull();

        // 정리: 반환값 필요 → supplyAsync, 반환값 불필요 → runAsync, 이미 값 있음 → completedFuture
    }

    @Test
    @DisplayName("thenApply: 값 변환 체이닝")
    void thenApply() throws Exception {
        // thenApply: 비동기 작업 결과를 동기적으로 변환 (동일 스레드에서 실행)
        // - 사용 시점: API 응답 파싱, DTO 변환, 간단한 데이터 가공
        // - Stream의 map()과 유사
        // - 실무 예: userFuture.thenApply(user -> new UserDto(user))
        //           apiFuture.thenApply(response -> response.getData())
        //           JSON 파싱 후 필요한 필드만 추출

        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> "123")
                .thenApply(Integer::parseInt)   // String → Integer (파싱)
                .thenApply(i -> i * 2);          // Integer → Integer (계산)

        assertThat(future.get()).isEqualTo(246);
    }

    @Test
    @DisplayName("thenCompose: 비동기 flatMap")
    void thenCompose() throws Exception {
        // thenCompose: 비동기 작업의 결과로 또 다른 비동기 작업 실행 (체이닝)
        // - 사용 시점: 첫 번째 API 결과로 두 번째 API 호출, DB 조회 후 추가 조회
        // - Stream의 flatMap()과 유사
        // - thenApply vs thenCompose:
        //   thenApply: T -> U (동기 변환)
        //   thenCompose: T -> CompletableFuture<U> (비동기 체이닝)
        // - 실무 예: getOrderAsync(orderId)
        //             .thenCompose(order -> getUserAsync(order.getUserId()))
        //             .thenCompose(user -> getAddressAsync(user.getAddressId()))
        //           순차적 비동기 호출 체이닝

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> 1L)
                .thenCompose(userId -> fetchUserAsync(userId));  // Long → CF<String>

        assertThat(future.get()).isEqualTo("User-1");
    }

    @Test
    @DisplayName("thenCombine: 두 CF를 병렬 실행 후 결합")
    void thenCombine() throws Exception {
        // thenCombine: 두 개의 독립적인 비동기 작업을 병렬로 실행하고 결과를 결합
        // - 사용 시점: 서로 의존하지 않는 두 API를 동시 호출 후 결과 병합
        // - 핵심: 병렬 실행으로 성능 최적화 (순차 실행보다 빠름)
        // - 실무 예:
        //   CompletableFuture<User> userFuture = getUserAsync(userId);
        //   CompletableFuture<Product> productFuture = getProductAsync(productId);
        //   userFuture.thenCombine(productFuture, (user, product) -> new Order(user, product))
        // - 두 개의 서로 다른 DB 조회, 두 개의 외부 API 호출 등

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
    @DisplayName("allOf: 모든 CF 완료 대기 (병렬) - 실무에서 가장 많이 사용")
    void allOf() throws Exception {
        // allOf: N개의 비동기 작업을 모두 병렬로 실행하고 전체 완료 대기 ★★★
        // - 사용 시점: 여러 API를 동시에 호출하고 모든 결과가 필요할 때 (가장 빈번)
        // - 성능 최적화의 핵심 패턴 (순차 실행 대비 N배 빠름)
        // - 실무 예:
        //   List<CompletableFuture<User>> futures = userIds.stream()
        //       .map(id -> getUserAsync(id))
        //       .toList();
        //   CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        //       .thenApply(v -> futures.stream().map(CompletableFuture::join).toList())
        // - MSA에서 여러 서비스 동시 호출, 대량 데이터 병렬 처리 등

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
        assertThat(duration).isLessThan(250);  // 병렬로 약 200ms (순차면 450ms)
    }

    @Test
    @DisplayName("anyOf: 가장 빠른 CF의 결과 반환 (타임아웃 구현 등에 활용)")
    void anyOf() throws Exception {
        // anyOf: 여러 비동기 작업 중 가장 먼저 완료된 결과만 사용
        // - 사용 시점: 여러 데이터 소스 중 빠른 것만 사용 (캐시 vs DB vs API)
        // - 타임아웃 구현, 장애 대응 (Primary/Fallback 패턴)
        // - 실무 예:
        //   CompletableFuture<User> cacheFuture = getCachedUserAsync(id);
        //   CompletableFuture<User> dbFuture = getUserFromDbAsync(id);
        //   CompletableFuture.anyOf(cacheFuture, dbFuture); // 둘 중 빠른 것
        // - 여러 리전의 서버 중 가장 빠른 응답 사용
        // - 주의: allOf와 달리 사용 빈도는 낮음 (특수한 경우에만 사용)

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
        // exceptionally: 예외 발생 시 fallback 값 제공 (간단한 예외 처리)
        // - 사용 시점: API 호출 실패 시 기본값 반환, 캐시 실패 시 빈 값 반환
        // - 정상 흐름은 그대로, 예외만 처리
        // - handle()과 차이: exceptionally는 예외만 처리, handle은 정상/예외 모두 처리
        // - 실무 예:
        //   getUserAsync(id)
        //       .exceptionally(ex -> {
        //           log.error("User fetch failed", ex);
        //           return User.getDefaultUser(); // fallback
        //       })
        // - API 호출 실패 시 캐시 데이터 반환, 외부 서비스 장애 시 기본값 사용

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
    @DisplayName("handle: 정상/예외 모두 처리 (exceptionally보다 범용적)")
    void handle() throws Exception {
        // handle: 정상 결과와 예외 모두를 하나의 핸들러로 처리 (더 범용적 ★)
        // - 사용 시점: 정상/예외 모두 처리 로직이 필요할 때
        // - exceptionally vs handle:
        //   exceptionally: 예외만 처리 (정상은 그대로 통과)
        //   handle: 정상/예외 모두 처리 (모든 경우 처리 가능)
        // - 실무 예:
        //   getProductAsync(id).handle((product, ex) -> {
        //       if (ex != null) {
        //           log.error("Failed to fetch product", ex);
        //           return ResponseDto.error(ex.getMessage());
        //       }
        //       return ResponseDto.success(product);
        //   })
        // - API 응답 통일, 에러 로깅 + 정상 데이터 가공 동시 처리

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
    @DisplayName("커스텀 Executor 사용 (I/O 바운드 작업) - 실무 필수")
    void customExecutor() throws Exception {
        // Custom Executor: 작업 특성에 맞는 전용 스레드 풀 사용 ★★★
        // - 사용 시점: I/O 블로킹 작업이 많을 때 (DB, HTTP 호출)
        // - 기본 ForkJoinPool vs Custom ThreadPool:
        //   기본(ForkJoinPool): CPU 바운드 작업에 최적화 (스레드 수 = CPU 코어 수)
        //   커스텀(FixedThreadPool): I/O 바운드 작업에 최적화 (스레드 수 >> CPU 코어 수)
        // - 실무 예:
        //   ExecutorService ioExecutor = Executors.newFixedThreadPool(50);
        //   CompletableFuture.supplyAsync(() -> restTemplate.getForObject(url), ioExecutor)
        // - Spring에서는 @Async와 함께 ThreadPoolTaskExecutor 사용
        // - 주의: ForkJoinPool을 I/O 작업에 사용하면 모든 스레드가 블로킹되어 성능 저하

        // given: I/O 작업용 전용 풀
        // CPU 바운드: ForkJoinPool (기본) - 계산 작업
        // I/O 바운드: newFixedThreadPool (커스텀) - DB, HTTP 호출
        ExecutorService ioExecutor = Executors.newFixedThreadPool(10);

        try {
            // when: I/O 블로킹 작업을 전용 풀에서 실행
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                sleep(100);  // DB 조회, HTTP 호출 등 I/O 시뮬레이션
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
    @DisplayName("실전 예제: Fan-Out/Fan-In 패턴 (병렬 API 호출)")
    void fanOutFanIn() throws Exception {
        // Fan-Out/Fan-In: 실무에서 가장 많이 사용하는 패턴 ★★★
        // - Fan-Out: 여러 작업을 동시에 시작 (병렬 실행)
        // - Fan-In: 모든 작업 완료 후 결과 수집
        // - 사용 시점:
        //   1. 여러 사용자/상품 정보를 한번에 조회
        //   2. 여러 외부 API를 동시에 호출
        //   3. 대량 데이터 병렬 처리 (배치)
        // - 실무 예:
        //   List<Long> productIds = cart.getProductIds();
        //   List<CompletableFuture<Product>> futures = productIds.stream()
        //       .map(id -> getProductAsync(id))  // Fan-Out: 병렬 조회
        //       .toList();
        //   CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        //       .thenApply(v -> futures.stream()  // Fan-In: 결과 수집
        //           .map(CompletableFuture::join)
        //           .toList())
        // - 성능 개선 효과: N개 API를 순차 호출 시 N * latency, 병렬은 max(latency)

        // given: 여러 사용자 ID
        List<Long> userIds = List.of(1L, 2L, 3L, 4L, 5L);

        // when: 모든 사용자 정보를 병렬로 조회 (Fan-Out)
        List<CompletableFuture<String>> userFutures = userIds.stream()
                .map(this::fetchUserAsync)
                .toList();

        // when: 모든 조회 완료 대기 후 결과 수집 (Fan-In)
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

    @Test
    @DisplayName("타임아웃 처리 (orTimeout, completeOnTimeout)")
    void timeout() throws Exception {
        // Timeout 처리: 비동기 작업에 시간 제한 설정 (Java 9+) ★
        // - 사용 시점: 외부 API 호출, DB 조회 등 응답 시간 제한 필요할 때
        // - orTimeout vs completeOnTimeout:
        //   orTimeout: 타임아웃 시 TimeoutException 발생 (예외 처리 필요)
        //   completeOnTimeout: 타임아웃 시 기본값 반환 (예외 없음)
        // - 실무 예:
        //   getExternalApiAsync()
        //       .orTimeout(3, TimeUnit.SECONDS)  // 3초 초과 시 예외
        //       .exceptionally(ex -> fallbackData);
        //   또는
        //   getExternalApiAsync()
        //       .completeOnTimeout(defaultValue, 3, TimeUnit.SECONDS)  // 3초 초과 시 기본값
        // - Circuit Breaker 패턴과 함께 사용, SLA 준수

        // 1. orTimeout: 타임아웃 시 예외 발생
        CompletableFuture<String> timeoutFuture = CompletableFuture.supplyAsync(() -> {
            sleep(200);
            return "slow";
        }).orTimeout(100, TimeUnit.MILLISECONDS);

        try {
            timeoutFuture.get();
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
        }

        // 2. completeOnTimeout: 타임아웃 시 기본값 반환
        CompletableFuture<String> defaultFuture = CompletableFuture.supplyAsync(() -> {
            sleep(200);
            return "slow";
        }).completeOnTimeout("default", 100, TimeUnit.MILLISECONDS);

        assertThat(defaultFuture.get()).isEqualTo("default");
    }

    @Test
    @DisplayName("예외 처리 체이닝 (실무 패턴)")
    void exceptionChaining() throws Exception {
        // Exception Chaining: 예외 처리 후 정상 흐름 체이닝
        // - 사용 시점: 예외 발생 시 fallback 처리 후 후속 작업 계속
        // - 핵심: exceptionally로 복구 후 thenApply/thenCompose 등으로 계속 체이닝
        // - 실무 예:
        //   getProductFromCacheAsync(id)
        //       .exceptionally(ex -> {
        //           log.warn("Cache miss, fallback to DB");
        //           return getProductFromDb(id);  // Cache 실패 시 DB 조회
        //       })
        //       .thenApply(product -> new ProductDto(product))  // DTO 변환
        //       .thenApply(dto -> enrichWithRecommendations(dto))  // 추가 데이터
        //       .exceptionally(ex -> ProductDto.getDefault());  // 최종 fallback
        // - 다단계 fallback, Graceful Degradation 구현

        // given: 예외가 발생하는 작업
        CompletableFuture<String> future = CompletableFuture
                .<String>supplyAsync(() -> {
                    throw new IllegalArgumentException("Invalid input");
                })
                .exceptionally(ex -> {
                    // 특정 예외 처리 (1차 fallback)
                    if (ex.getCause() instanceof IllegalArgumentException) {
                        return "fallback-1";
                    }
                    return "error";
                })
                .thenApply(String::toUpperCase);  // 정상 흐름 계속 (값 변환)

        // then: 예외가 처리되어 대문자로 변환된 fallback 반환
        assertThat(future.get()).isEqualTo("FALLBACK-1");
    }

    // ==================== Helper Methods ====================

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
