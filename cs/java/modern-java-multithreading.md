# 현대 자바 멀티스레드 실전 가이드: CompletableFuture, ExecutorService, ForkJoinPool

> "스레드 풀은 알아도 CompletableFuture는 안 써봤다"는 개발자를 위한 실전 가이드.
> ExecutorService, ForkJoinPool, CompletableFuture를 실무에서 바로 쓸 수 있도록 예제 중심으로 정리합니다.

---

## 1. ExecutorService: 스레드 풀의 기본

### 1.1 왜 Thread를 직접 생성하면 안 될까?

```java
// 안티 패턴: 요청마다 스레드 생성
for (int i = 0; i < 10000; i++) {
    new Thread(() -> processRequest()).start();  // 스레드 10,000개 생성!
}

// 문제점:
// 1. 스레드 생성 비용이 큼 (약 1MB 스택 메모리)
// 2. 컨텍스트 스위칭 비용 증가
// 3. OS 리소스 한계 초과 → java.lang.OutOfMemoryError: unable to create new native thread
```

**스레드 풀의 장점:**
```
1. 스레드 재사용 → 생성/소멸 비용 절감
2. 동시 실행 스레드 수 제한 → 시스템 안정성
3. 작업 큐(BlockingQueue)로 Backpressure 자연스러운 제어
4. 예외 처리와 모니터링 통합
```

### 1.2 ExecutorService의 주요 구현체

#### Fixed Thread Pool

```java
// 고정 크기 스레드 풀 (가장 일반적)
ExecutorService executor = Executors.newFixedThreadPool(10);

// 내부 구조:
// ThreadPoolExecutor(
//     corePoolSize: 10,
//     maximumPoolSize: 10,
//     keepAliveTime: 0,
//     workQueue: LinkedBlockingQueue (무제한)
// )

// 적합한 사용 사례:
// - CPU 바운드 작업: 스레드 수 = CPU 코어 수
// - I/O 바운드 작업: 스레드 수 = CPU 코어 수 × (1 + 대기시간/처리시간)

executor.submit(() -> {
    // 작업 실행
    return processData();
});

// 반드시 종료 처리!
executor.shutdown();  // 새 작업 거부, 기존 작업 완료 대기
executor.awaitTermination(60, TimeUnit.SECONDS);  // 최대 60초 대기
```

#### Cached Thread Pool

```java
// 필요에 따라 스레드를 동적으로 생성
ExecutorService executor = Executors.newCachedThreadPool();

// 내부 구조:
// ThreadPoolExecutor(
//     corePoolSize: 0,
//     maximumPoolSize: Integer.MAX_VALUE,  // 무제한!
//     keepAliveTime: 60초,
//     workQueue: SynchronousQueue  // 큐 없이 직접 전달
// )

// 동작 방식:
// 1. idle 스레드가 있으면 재사용
// 2. 없으면 새 스레드 즉시 생성
// 3. 60초 동안 사용되지 않은 스레드는 제거

// 적합한 사용 사례:
// - 짧고 빈번한 비동기 작업
// - 작업 수가 예측 불가능한 경우

// 주의사항:
// - 스레드가 무한정 증가할 수 있음 (OOM 위험)
// - 운영 환경에서는 제한된 풀 사용 권장
```

#### Single Thread Executor

```java
// 단일 스레드로 순차 실행
ExecutorService executor = Executors.newSingleThreadExecutor();

// 적합한 사용 사례:
// - 순서 보장이 필요한 작업
// - 이벤트 루프 패턴
// - 로그 처리, 메시지 큐 소비자

executor.submit(() -> {
    // 이전 작업이 완료된 후 실행 보장
    processSequentially();
});
```

#### Scheduled Thread Pool

```java
// 주기적/지연 실행
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

// 일회성 지연 실행
scheduler.schedule(() -> {
    System.out.println("3초 후 실행");
}, 3, TimeUnit.SECONDS);

// 고정 주기 실행 (작업 시작 시점 기준)
scheduler.scheduleAtFixedRate(() -> {
    System.out.println("5초마다 실행");
}, 0, 5, TimeUnit.SECONDS);
// 이전 작업 시작 + 5초 후 다음 작업 시작
// 작업이 5초보다 오래 걸리면 즉시 다음 작업 시작

// 고정 지연 실행 (작업 완료 시점 기준)
scheduler.scheduleWithFixedDelay(() -> {
    System.out.println("작업 완료 후 5초 대기 후 다시 실행");
}, 0, 5, TimeUnit.SECONDS);
// 이전 작업 완료 + 5초 후 다음 작업 시작
```

### 1.3 ThreadPoolExecutor 커스텀 설정

```java
// 세밀한 제어가 필요한 경우 직접 생성
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,                              // corePoolSize: 기본 스레드 수
    10,                             // maximumPoolSize: 최대 스레드 수
    60L,                            // keepAliveTime
    TimeUnit.SECONDS,               // 유휴 스레드 유지 시간
    new LinkedBlockingQueue<>(100), // 대기 큐 (크기 제한 필수!)
    new ThreadFactory() {           // 커스텀 ThreadFactory
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "MyWorker-" + threadNumber.getAndIncrement());
            t.setDaemon(false);  // 논데몬 스레드
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    },
    new ThreadPoolExecutor.CallerRunsPolicy()  // RejectionPolicy
);

// AllowCoreThreadTimeOut: core 스레드도 유휴 시 제거
executor.allowCoreThreadTimeOut(true);
```

**작업 추가 시 4단계 흐름 (중요!):**
```
1. 현재 스레드 수 < corePoolSize
   → 즉시 새 스레드 생성 (idle 스레드가 있어도!)

2. 현재 스레드 수 >= corePoolSize
   → workQueue에 추가 시도

3. workQueue가 꽉 참
   → 현재 스레드 수 < maximumPoolSize면 새 스레드 생성

4. maximumPoolSize도 도달
   → RejectedExecutionHandler 실행
```

**RejectionPolicy 선택 가이드:**
```java
// 1. AbortPolicy (기본값)
// RejectedExecutionException 던짐 → 호출자가 명시적으로 처리해야 함
new ThreadPoolExecutor.AbortPolicy()

// 2. CallerRunsPolicy (추천!)
// 작업을 제출한 스레드가 직접 실행
// 자연스러운 Backpressure: 제출 스레드가 블로킹되어 제출 속도 자동 조절
new ThreadPoolExecutor.CallerRunsPolicy()

// 3. DiscardPolicy
// 조용히 작업 폐기 (로그도 없음) - 데이터 손실 위험
new ThreadPoolExecutor.DiscardPolicy()

// 4. DiscardOldestPolicy
// 가장 오래 대기한 작업을 폐기하고 새 작업 추가
new ThreadPoolExecutor.DiscardOldestPolicy()

// 5. 커스텀 핸들러
new RejectedExecutionHandler() {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        log.error("Task rejected: {}", r);
        metrics.incrementRejectedTasks();
        // 별도 큐에 저장하거나 알림 발송
    }
}
```

### 1.4 ExecutorService의 종료 처리

```java
// 잘못된 종료: 리소스 누수
executor.shutdown();  // 이것만 하면 JVM이 종료 안 될 수 있음!

// 올바른 종료 패턴 1: try-with-resources (Java 19+)
try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
    // 작업 제출
    executor.submit(() -> doWork());
}  // 자동으로 shutdown() + awaitTermination(1일) 호출

// 올바른 종료 패턴 2: 명시적 종료 (Java 18 이하)
ExecutorService executor = Executors.newFixedThreadPool(10);
try {
    executor.submit(() -> doWork());
} finally {
    executor.shutdown();  // 새 작업 거부, 기존 작업은 완료
    try {
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            executor.shutdownNow();  // 강제 종료 (interrupt 발송)

            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                log.error("Executor did not terminate");
            }
        }
    } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}

// shutdown() vs shutdownNow() 차이
executor.shutdown();     // 진행 중인 작업과 대기 중인 작업 모두 완료
executor.shutdownNow();  // 진행 중인 작업에 interrupt 발송, 대기 작업 반환
```

---

## 2. ForkJoinPool: 분할 정복 최적화 스레드 풀

### 2.1 ForkJoinPool의 핵심: Work Stealing

```
전통적인 스레드 풀:
┌─────────┐     ┌─────────────┐
│ Thread1 │────→│ Shared Queue│
│ Thread2 │────→│  [T1][T2]   │
│ Thread3 │────→│  [T3][T4]   │
└─────────┘     └─────────────┘
→ 공유 큐로 인한 경합(contention) 발생

ForkJoinPool (Work Stealing):
┌─────────┐     ┌──────────┐
│ Thread1 │────→│ Deque 1  │  [T1][T2] (own)
└─────────┘     └──────────┘
                      ↑
┌─────────┐     ┌────┼─────┐
│ Thread2 │────→│ Deque 2  │  [T3] (own)
└─────────┘     └──────────┘
                      ↑ steal (다른 스레드가 훔쳐감)
┌─────────┐     ┌────┼─────┐
│ Thread3 │────→│ Deque 3  │  [] (idle, stealing)
└─────────┘     └──────────┘

각 스레드가 자신의 Deque 보유:
- 자신의 작업: 앞에서 꺼냄 (LIFO) - 캐시 locality 향상
- 훔칠 때: 뒤에서 꺼냄 (FIFO) - 경합 최소화
- idle 스레드가 바쁜 스레드의 작업 훔쳐서 실행
→ 부하 자동 균등화!
```

### 2.2 RecursiveTask와 RecursiveAction

```java
// RecursiveTask<V>: 값을 반환하는 작업
class SumTask extends RecursiveTask<Long> {
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

        // 기준값보다 작으면 직접 계산 (분할 중지)
        if (length <= THRESHOLD) {
            long sum = 0;
            for (int i = start; i < end; i++) {
                sum += array[i];
            }
            return sum;
        }

        // 분할 (Fork)
        int mid = start + length / 2;
        SumTask leftTask = new SumTask(array, start, mid);
        SumTask rightTask = new SumTask(array, mid, end);

        leftTask.fork();   // 비동기 실행 (다른 스레드에 제출)
        long rightResult = rightTask.compute();  // 현재 스레드에서 직접 실행
        long leftResult = leftTask.join();       // left 작업 완료 대기

        // 병합 (Join)
        return leftResult + rightResult;
    }
}

// 사용 예시
long[] array = new long[1_000_000];
// ... 배열 초기화

ForkJoinPool pool = new ForkJoinPool();  // 기본값: CPU 코어 수
long result = pool.invoke(new SumTask(array, 0, array.length));

System.out.println("Sum: " + result);
```

```java
// RecursiveAction: 값을 반환하지 않는 작업
class SortTask extends RecursiveAction {
    private static final int THRESHOLD = 1000;
    private final int[] array;
    private final int start, end;

    @Override
    protected void compute() {
        if (end - start <= THRESHOLD) {
            Arrays.sort(array, start, end);  // 직접 정렬
            return;
        }

        int mid = start + (end - start) / 2;
        invokeAll(
            new SortTask(array, start, mid),
            new SortTask(array, mid, end)
        );
        // invokeAll: 모든 작업을 fork하고 완료 대기

        // 병합 로직 (Quick Sort는 병합 불필요, Merge Sort면 필요)
    }
}
```

### 2.3 ForkJoinPool.commonPool()

```java
// JVM 전역 공유 ForkJoinPool
ForkJoinPool commonPool = ForkJoinPool.commonPool();

// 크기: Runtime.getRuntime().availableProcessors() - 1
// parallelStream()도 이 풀 사용
// CompletableFuture (executor 미지정 시)도 이 풀 사용

// 주의사항:
// 1. 전역 공유이므로 I/O 블로킹 작업 금지!
//    CPU 바운드 작업만 사용
// 2. parallelStream()과 CompletableFuture가 같은 풀을 쓰면
//    서로 영향을 줄 수 있음

// 크기 조정 (시스템 프로퍼티)
// -Djava.util.concurrent.ForkJoinPool.common.parallelism=8
```

### 2.4 언제 ForkJoinPool을 사용할까?

```
✅ 적합한 경우:
- CPU 바운드 재귀 작업 (분할 정복)
- 병렬 스트림 (parallelStream)
- 작업 크기가 불균등한 경우 (Work Stealing이 자동 균등화)
- 예: 대용량 배열 정렬, 병렬 계산, 이미지 처리

❌ 부적합한 경우:
- I/O 블로킹 작업 (DB 쿼리, HTTP 호출)
  → 스레드가 블로킹되어 Work Stealing 효과 없음
  → ExecutorService (Fixed/Cached) 사용
- 작업 간 의존성이 복잡한 경우
  → CompletableFuture가 더 적합
```

---

## 3. CompletableFuture: 비동기 프로그래밍의 핵심

### 3.1 기본 생성과 완료

```java
// 1. 즉시 완료된 Future
CompletableFuture<String> completed = CompletableFuture.completedFuture("result");

// 2. 비동기 작업 시작
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    // 다른 스레드에서 실행 (ForkJoinPool.commonPool)
    return fetchFromDatabase();
});

// 3. 값을 반환하지 않는 비동기 작업
CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
    sendNotification();
});

// 4. 커스텀 ExecutorService 지정 (권장!)
ExecutorService executor = Executors.newFixedThreadPool(20);
CompletableFuture<String> future = CompletableFuture.supplyAsync(
    () -> callExternalAPI(),
    executor  // I/O 작업은 전용 풀 사용
);

// 5. 수동으로 완료
CompletableFuture<String> manual = new CompletableFuture<>();
manual.complete("done");       // 정상 완료
manual.completeExceptionally(new RuntimeException("error"));  // 예외로 완료
```

### 3.2 변환 연산: thenApply, thenCompose, thenCombine

```java
// thenApply: 값 변환 (T → U)
CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> "123")
    .thenApply(s -> Integer.parseInt(s))   // String → Integer
    .thenApply(i -> i * 2);                // Integer → Integer

// thenApplyAsync: 별도 스레드에서 변환
future.thenApplyAsync(i -> i + 1, customExecutor);

// thenCompose: 비동기 flatMap (T → CompletableFuture<U>)
// 중첩된 CF를 평탄화
CompletableFuture<User> userFuture = CompletableFuture
    .supplyAsync(() -> getUserId())
    .thenCompose(userId -> fetchUserAsync(userId));  // userId → CF<User>
// 만약 thenApply를 쓰면 CF<CF<User>>가 되어 번거로움

// thenCombine: 두 CF를 병렬 실행 후 결합
CompletableFuture<String> nameFuture = CompletableFuture.supplyAsync(() -> "John");
CompletableFuture<Integer> ageFuture = CompletableFuture.supplyAsync(() -> 30);

CompletableFuture<String> combined = nameFuture.thenCombine(
    ageFuture,
    (name, age) -> name + " is " + age + " years old"
);
// 두 작업이 모두 완료되면 결합 함수 실행

// thenAcceptBoth: 두 결과를 소비 (반환값 없음)
nameFuture.thenAcceptBoth(ageFuture, (name, age) -> {
    System.out.println(name + ": " + age);
});
```

### 3.3 예외 처리

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    if (Math.random() > 0.5) {
        throw new RuntimeException("Random error");
    }
    return "success";
});

// exceptionally: 예외 발생 시 대체 값
future.exceptionally(ex -> {
    log.error("Error occurred", ex);
    return "fallback";  // 예외를 정상 값으로 변환
});

// handle: 정상/예외 모두 처리
future.handle((result, ex) -> {
    if (ex != null) {
        log.error("Error", ex);
        return "error: " + ex.getMessage();
    }
    return "success: " + result;
});

// whenComplete: 결과를 소비 (반환값 변경 불가, finally와 유사)
future.whenComplete((result, ex) -> {
    if (ex != null) {
        log.error("Failed", ex);
    } else {
        log.info("Completed: {}", result);
    }
});
// 주의: whenComplete는 예외를 처리해도 다음 단계로 전파됨!

// 체인에서 예외 전파
CompletableFuture.supplyAsync(() -> step1())
    .thenApply(r -> step2(r))       // step2에서 예외 발생
    .thenApply(r -> step3(r))       // 실행 안 됨
    .exceptionally(ex -> {
        // 여기서 예외 처리
        return fallbackValue();
    });
```

### 3.4 조합 연산: allOf, anyOf

```java
// allOf: 모든 CF가 완료될 때까지 대기 (병렬 실행)
List<CompletableFuture<String>> futures = List.of(
    CompletableFuture.supplyAsync(() -> fetchData1()),
    CompletableFuture.supplyAsync(() -> fetchData2()),
    CompletableFuture.supplyAsync(() -> fetchData3())
);

CompletableFuture<Void> allDone = CompletableFuture.allOf(
    futures.toArray(new CompletableFuture[0])
);

// allOf는 Void 반환 → 결과를 수집하려면 join() 필요
allDone.thenRun(() -> {
    List<String> results = futures.stream()
        .map(CompletableFuture::join)  // 이미 완료되어 블로킹 안 됨
        .toList();
    System.out.println(results);
});

// anyOf: 가장 먼저 완료되는 CF의 결과 반환
CompletableFuture<Object> fastest = CompletableFuture.anyOf(
    CompletableFuture.supplyAsync(() -> callAPI1()),
    CompletableFuture.supplyAsync(() -> callAPI2()),
    CompletableFuture.supplyAsync(() -> callAPI3())
);

fastest.thenAccept(result -> {
    System.out.println("Fastest result: " + result);
});
```

### 3.5 타임아웃과 기본값

```java
// Java 9+: orTimeout과 completeOnTimeout
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    Thread.sleep(5000);  // 5초 걸리는 작업
    return "done";
});

// 3초 안에 완료되지 않으면 TimeoutException
future.orTimeout(3, TimeUnit.SECONDS)
    .exceptionally(ex -> {
        if (ex instanceof TimeoutException) {
            return "timeout!";
        }
        throw new CompletionException(ex);
    });

// 3초 안에 완료되지 않으면 기본값으로 완료
future.completeOnTimeout("default", 3, TimeUnit.SECONDS);

// Java 8: ScheduledExecutorService로 직접 구현
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

CompletableFuture<String> withTimeout(CompletableFuture<String> future, long timeout, TimeUnit unit) {
    CompletableFuture<String> timeoutFuture = new CompletableFuture<>();

    scheduler.schedule(() -> {
        timeoutFuture.completeExceptionally(new TimeoutException());
    }, timeout, unit);

    return future.applyToEither(timeoutFuture, Function.identity());
}
```

### 3.6 실무 패턴과 주의사항

#### 패턴 1: Fan-Out/Fan-In (병렬 처리 후 병합)

```java
List<Long> userIds = List.of(1L, 2L, 3L, 4L, 5L);

// 모든 사용자 정보를 병렬로 조회
List<CompletableFuture<User>> userFutures = userIds.stream()
    .map(id -> CompletableFuture.supplyAsync(() -> fetchUser(id), ioExecutor))
    .toList();

// 모든 조회 완료 대기 후 병합
CompletableFuture<List<User>> allUsers = CompletableFuture.allOf(
    userFutures.toArray(new CompletableFuture[0])
).thenApply(v ->
    userFutures.stream()
        .map(CompletableFuture::join)
        .toList()
);

allUsers.thenAccept(users -> {
    System.out.println("Fetched " + users.size() + " users");
});
```

#### 패턴 2: Retry 로직

```java
<T> CompletableFuture<T> retry(Supplier<CompletableFuture<T>> action, int maxRetries) {
    CompletableFuture<T> future = action.get();

    for (int i = 0; i < maxRetries; i++) {
        future = future.exceptionally(ex -> null)
            .thenCompose(result -> {
                if (result != null) {
                    return CompletableFuture.completedFuture(result);
                }
                return action.get();  // 재시도
            });
    }

    return future;
}

// 사용
retry(() -> CompletableFuture.supplyAsync(() -> unstableAPI()), 3)
    .thenAccept(result -> System.out.println("Success: " + result));
```

#### 패턴 3: 파이프라인 처리

```java
CompletableFuture<Dashboard> dashboard = CompletableFuture
    .supplyAsync(() -> getUserId(), ioExecutor)
    .thenComposeAsync(userId -> fetchUser(userId), ioExecutor)
    .thenComposeAsync(user -> enrichUser(user), cpuExecutor)  // CPU 작업
    .thenCombine(
        CompletableFuture.supplyAsync(() -> fetchMetrics(), ioExecutor),
        (user, metrics) -> new Dashboard(user, metrics)
    )
    .exceptionally(ex -> {
        log.error("Dashboard creation failed", ex);
        return Dashboard.empty();
    })
    .whenComplete((result, ex) -> {
        log.info("Pipeline completed");
    });
```

#### 주의사항 1: 기본 Executor 사용 금지 (I/O 작업)

```java
// 나쁜 예: I/O 블로킹 작업을 commonPool에서 실행
CompletableFuture.supplyAsync(() -> {
    return httpClient.send(request);  // 블로킹!
});
// ForkJoinPool.commonPool()의 스레드를 점유하여
// 다른 parallelStream()이나 CF에 영향

// 좋은 예: 전용 Executor 지정
ExecutorService ioExecutor = Executors.newFixedThreadPool(20);
CompletableFuture.supplyAsync(() -> {
    return httpClient.send(request);
}, ioExecutor);
```

#### 주의사항 2: 예외 삼킴 방지

```java
// 나쁜 예: 예외 처리 누락
CompletableFuture.supplyAsync(() -> {
    throw new RuntimeException("error");
});
// 아무도 get()이나 join()을 호출하지 않으면 예외가 조용히 삼켜짐!

// 좋은 예: 항상 예외 처리
CompletableFuture.supplyAsync(() -> {
    throw new RuntimeException("error");
}).exceptionally(ex -> {
    log.error("Async task failed", ex);
    metrics.incrementErrors();
    return null;
});
```

#### 주의사항 3: join() vs get()

```java
// join(): CompletionException (unchecked)
try {
    String result = future.join();
} catch (CompletionException ex) {
    Throwable cause = ex.getCause();  // 실제 예외
}

// get(): ExecutionException (checked)
try {
    String result = future.get();
} catch (InterruptedException | ExecutionException ex) {
    // InterruptedException 처리 필요
}

// get(timeout): 타임아웃 지원
try {
    String result = future.get(3, TimeUnit.SECONDS);
} catch (TimeoutException ex) {
    // 타임아웃 처리
}
```

---

## 4. 성능 튜닝과 모니터링

### 4.1 스레드 풀 크기 결정

```java
// Littles Law: 처리량 = 동시 요청 수 / 평균 응답 시간
// 동시 요청 수 = 처리량 × 평균 응답 시간

// I/O 바운드 (DB, HTTP):
int poolSize = cpuCores * (1 + waitTime / cpuTime);
// 예: 4코어, 대기 90ms, CPU 10ms
// poolSize = 4 * (1 + 90/10) = 40

// CPU 바운드 (계산, 암호화):
int poolSize = cpuCores;  // 또는 cpuCores + 1

// 실무:
// 1. 초기값 설정 (위 공식)
// 2. 부하 테스트 (k6, JMeter, Gatling)
// 3. 스레드 풀 모니터링 (activeCount, queueSize)
// 4. 점진적 조정
```

### 4.2 ThreadPoolExecutor 모니터링

```java
ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

// 주요 메트릭
int activeCount = executor.getActiveCount();        // 현재 실행 중인 스레드 수
int poolSize = executor.getPoolSize();              // 현재 풀의 스레드 수
long taskCount = executor.getTaskCount();           // 제출된 총 작업 수
long completedCount = executor.getCompletedTaskCount();  // 완료된 작업 수
int queueSize = executor.getQueue().size();         // 대기 중인 작업 수

// Spring Boot Actuator 연동
@Component
public class ExecutorMetrics {
    @Scheduled(fixedRate = 5000)
    public void logMetrics() {
        log.info("Active: {}, Queue: {}, Completed: {}/{}",
            executor.getActiveCount(),
            executor.getQueue().size(),
            executor.getCompletedTaskCount(),
            executor.getTaskCount()
        );
    }
}

// Micrometer 메트릭 등록
registry.gauge("executor.active", executor, ThreadPoolExecutor::getActiveCount);
registry.gauge("executor.queue.size", executor, e -> e.getQueue().size());
```

### 4.3 ForkJoinPool 모니터링

```java
ForkJoinPool pool = ForkJoinPool.commonPool();

// 주요 메트릭
int parallelism = pool.getParallelism();       // 병렬성 수준 (코어 수)
int activeThreadCount = pool.getActiveThreadCount();  // 활성 스레드 수
int runningThreadCount = pool.getRunningThreadCount();  // 실행 중인 스레드 수
long stealCount = pool.getStealCount();        // Work Stealing 횟수
int queuedTaskCount = pool.getQueuedTaskCount();  // 대기 작업 수

// 효율성 지표: stealCount가 높을수록 부하 균등화 잘 됨
```

---

## 5. 면접 예상 질문 & 모범 답변

### Q1. ExecutorService의 shutdown()과 shutdownNow()의 차이는?

**답변 포인트:**
> "shutdown()은 새로운 작업 제출을 거부하고, 이미 제출된 작업과 현재 실행 중인 작업을 모두 완료한 후 종료합니다. shutdownNow()는 현재 실행 중인 모든 작업에 interrupt를 발송하여 중단을 시도하고, 대기 큐에 있던 작업들을 List로 반환합니다. 실무에서는 정상 종료 시 shutdown()을 먼저 호출하고 awaitTermination()으로 대기하다가, 타임아웃이 발생하면 shutdownNow()로 강제 종료하는 패턴을 사용합니다."

---

### Q2. ForkJoinPool의 Work Stealing이란 무엇인가요?

**답변 포인트:**
> "Work Stealing은 유휴 스레드가 바쁜 스레드의 작업 큐에서 작업을 훔쳐서 실행하는 알고리즘입니다. 각 스레드가 자신만의 Deque를 가지고, 자신의 작업은 앞에서 꺼내고(LIFO, 캐시 locality 향상), 다른 스레드의 작업을 훔칠 때는 뒤에서 꺼냅니다(FIFO, 경합 최소화). 이를 통해 작업 크기가 불균등한 경우에도 자동으로 부하가 균등화됩니다. CPU 바운드 분할 정복 작업에 최적화되어 있으며, parallelStream()과 CompletableFuture의 기본 Executor로 사용됩니다."

---

### Q3. CompletableFuture에서 기본 ForkJoinPool.commonPool()을 사용하면 안 되는 이유는?

**답변 포인트:**
> "commonPool()은 JVM 전역 공유 ForkJoinPool로 크기가 CPU 코어 수 - 1입니다. parallelStream()도 같은 풀을 공유합니다. I/O 블로킹 작업을 이 풀에서 실행하면 스레드가 블로킹되어 다른 작업에 영향을 줍니다. 또한 CPU 바운드 작업을 위한 작은 풀이므로 많은 I/O 작업을 처리하기에 부족합니다. 따라서 I/O 바운드 비동기 작업은 반드시 전용 ExecutorService를 생성하여 supplyAsync()의 두 번째 인자로 지정해야 합니다."

---

### Q4. thenApply와 thenCompose의 차이는 무엇인가요?

**답변 포인트:**
> "thenApply는 동기 변환으로 T → U를 수행합니다. 함수가 일반 값을 반환합니다. thenCompose는 비동기 flatMap으로 T → CompletableFuture<U>를 수행합니다. 함수가 CompletableFuture를 반환하면 중첩된 CF<CF<U>>를 평탄화하여 CF<U>로 만듭니다. 예를 들어 userId를 받아서 비동기로 사용자를 조회하는 경우, thenApply를 쓰면 CF<CF<User>>가 되어 번거롭지만 thenCompose를 쓰면 CF<User>가 되어 깔끔하게 체이닝할 수 있습니다."

---

### Q5. ThreadPoolExecutor에서 corePoolSize와 maximumPoolSize의 동작 차이는?

**답변 포인트:**
> "작업이 제출되면 4단계로 처리됩니다. 먼저 현재 스레드 수가 corePoolSize 미만이면 idle 스레드가 있어도 새 스레드를 생성합니다. corePoolSize에 도달하면 workQueue에 작업을 추가합니다. 큐가 꽉 차면 maximumPoolSize까지 스레드를 추가로 생성합니다. maximumPoolSize도 도달하면 RejectedExecutionHandler가 실행됩니다. 주의할 점은 'idle 스레드가 있으면 재사용'이 아니라 'core 수 미만이면 새 스레드 우선 생성'이라는 것입니다."

---

### Q6. RecursiveTask에서 fork()와 compute()를 어떻게 조합해야 하나요?

**답변 포인트:**
> "일반적인 패턴은 왼쪽 작업은 fork()로 다른 스레드에 제출하고, 오른쪽 작업은 현재 스레드에서 compute()로 직접 실행한 후, fork한 작업은 join()으로 결과를 기다립니다. 이렇게 하면 불필요한 스레드 전환을 줄일 수 있습니다. 만약 양쪽 모두 fork()하면 현재 스레드가 idle 상태가 되어 비효율적입니다. invokeAll()을 사용하면 내부적으로 이 패턴을 자동으로 적용합니다."

---

### Q7. CompletableFuture의 예외 처리에서 exceptionally와 handle의 차이는?

**답변 포인트:**
> "exceptionally는 예외가 발생한 경우에만 실행되며, 예외를 정상 값으로 변환하여 다음 단계로 전달합니다. 함수는 예외만 받습니다. handle은 정상 완료와 예외 완료 모두에서 실행되며, 결과와 예외를 모두 받아서 새로운 값을 반환합니다. 함수는 (result, exception) 두 인자를 받습니다. 둘 중 하나는 항상 null입니다. finally처럼 항상 실행되지만 반환값을 변경할 수 있다는 점에서 whenComplete와 다릅니다."

---

### Q8. 스레드 풀 크기를 어떻게 결정하나요?

**답변 포인트:**
> "작업 유형에 따라 다릅니다. CPU 바운드 작업은 CPU 코어 수와 같거나 +1 정도로 설정합니다. 스레드가 많으면 컨텍스트 스위칭 비용이 증가합니다. I/O 바운드 작업은 코어 수 × (1 + 대기시간/처리시간) 공식을 사용합니다. 예를 들어 4코어에서 대기가 90ms, CPU 처리가 10ms면 4 × (1 + 90/10) = 40개입니다. 실무에서는 공식으로 초기값을 설정한 후 부하 테스트를 통해 activeCount, queueSize 등을 모니터링하며 점진적으로 조정합니다."

---

### Q9. CallerRunsPolicy가 Backpressure를 제공하는 원리는?

**답변 포인트:**
> "스레드 풀과 큐가 모두 꽉 찬 상황에서 CallerRunsPolicy는 작업을 제출한 스레드가 직접 작업을 실행하게 합니다. 제출 스레드가 작업을 실행하는 동안 블로킹되므로 새 작업 제출 속도가 자동으로 느려집니다. 이는 자연스러운 Backpressure 효과로 시스템 과부하를 방지합니다. AbortPolicy처럼 예외를 던지거나 DiscardPolicy처럼 작업을 버리는 것보다 안전하고 우아한 방식입니다."

---

### Q10. CompletableFuture의 allOf와 anyOf의 사용 사례는?

**답변 포인트:**
> "allOf는 여러 비동기 작업을 병렬로 실행하고 모두 완료될 때까지 기다릴 때 사용합니다. 예를 들어 여러 사용자 정보를 병렬로 조회하거나, 여러 외부 API를 동시에 호출한 후 결과를 모아서 처리하는 경우입니다. anyOf는 가장 빠른 응답을 받아야 할 때 사용합니다. 예를 들어 여러 데이터 소스(primary/secondary DB, 캐시)에서 같은 데이터를 조회하고 가장 먼저 응답한 결과를 사용하거나, Circuit Breaker 패턴에서 타임아웃 처리를 구현할 때 유용합니다."

---

## 6. 학습 체크리스트

- [ ] ExecutorService의 4가지 주요 구현체(Fixed, Cached, Single, Scheduled)의 차이를 설명할 수 있다
- [ ] ThreadPoolExecutor의 작업 추가 4단계 흐름을 corePoolSize, workQueue, maximumPoolSize 순서로 설명할 수 있다
- [ ] RejectedExecutionHandler의 4가지 정책과 선택 기준을 설명할 수 있다
- [ ] ExecutorService의 올바른 종료 패턴(shutdown + awaitTermination)을 작성할 수 있다
- [ ] ForkJoinPool의 Work Stealing 알고리즘을 Deque와 LIFO/FIFO 관점에서 설명할 수 있다
- [ ] RecursiveTask에서 fork()와 compute()의 조합 패턴을 설명할 수 있다
- [ ] CompletableFuture.supplyAsync()에서 커스텀 Executor를 지정해야 하는 이유를 설명할 수 있다
- [ ] thenApply, thenCompose, thenCombine의 차이와 사용 사례를 설명할 수 있다
- [ ] CompletableFuture의 예외 처리(exceptionally, handle, whenComplete)의 차이를 설명할 수 있다
- [ ] allOf와 anyOf의 사용 사례와 결과 수집 방법을 설명할 수 있다
- [ ] 스레드 풀 크기 결정 공식(CPU/I/O 바운드)을 설명할 수 있다
- [ ] ThreadPoolExecutor의 주요 모니터링 메트릭을 나열할 수 있다

---

## 7. 연관 학습 파일

- [`java-concurrency-threading.md`](./java-concurrency-threading.md) - 동시성 기초 (volatile, synchronized, Lock)
- [`../spring/spring-async.md`](../spring/spring-async.md) - Spring @Async와 ExecutorService 통합
- [`../microservices/async-communication.md`](../microservices/async-communication.md) - 비동기 마이크로서비스 패턴