# Java 동시성/멀티스레딩 완전 가이드

> 6년차 대기업 면접에서 탈락률 1위 영역.
> "매일 쓰지만 막상 설명하려면 막히는" 동시성 개념을 동작 원리부터 완전히 정리합니다.

---

## 1. 동시성 문제의 근원: CPU 캐시와 명령어 재정렬

### 1.1 CPU 캐시 계층과 가시성 문제

```
Thread 1 (Core 1)         Thread 2 (Core 2)
┌─────────────┐           ┌─────────────┐
│ L1 Cache    │           │ L1 Cache    │
│ count = 1   │           │ count = 0   │  ← 서로 다른 캐시
└──────┬──────┘           └──────┬──────┘
       │                         │
       └──────────┬──────────────┘
              ┌───▼───┐
              │  L3   │  (공유 캐시)
              └───┬───┘
                  │
              ┌───▼───┐
              │  RAM  │  count = 0
              └───────┘
```

**문제 시나리오:**
```java
// 공유 변수
int count = 0;

// Thread 1: count를 1로 증가
count = 1;  // Core 1의 L1 캐시에만 반영, RAM에는 아직 미반영

// Thread 2: count를 읽음
System.out.println(count); // 0 출력! (RAM의 오래된 값을 읽음)
// → 가시성(Visibility) 문제
```

### 1.2 명령어 재정렬 (Instruction Reordering)

컴파일러와 CPU는 성능 최적화를 위해 코드 순서를 바꿀 수 있습니다.

```java
// 작성한 코드
boolean initialized = false;
Object resource = null;

void init() {
    resource = new Object();  // 1
    initialized = true;       // 2
}

// CPU가 실제로 실행할 수 있는 순서
void init() {
    initialized = true;       // 2 먼저 실행될 수 있음!
    resource = new Object();  // 1
}

// 다른 스레드에서:
if (initialized) {
    resource.doSomething();  // NullPointerException 발생 가능!
}
```

---

## 2. Java 메모리 모델 (Java Memory Model, JMM)

### 2.1 happens-before 관계

**happens-before 규칙이 있으면, 한 스레드의 쓰기가 다른 스레드의 읽기에 반드시 보임을 보장합니다.**

```
주요 happens-before 규칙:

1. 프로그램 순서 규칙
   - 같은 스레드 내에서 순서대로 실행된 코드는 happens-before 관계

2. 모니터 잠금 규칙
   - synchronized 블록의 unlock은 다음 lock보다 happens-before
   - unlock 전의 모든 쓰기가 다음 lock 후에 보임

3. volatile 변수 규칙
   - volatile 변수 쓰기는 이후 읽기보다 happens-before

4. 스레드 시작 규칙
   - Thread.start() 전의 쓰기는 해당 스레드 안에서 보임

5. 스레드 종료 규칙
   - 스레드의 모든 작업은 Thread.join() 완료보다 happens-before
```

---

## 3. volatile 키워드 심화

### 3.1 volatile이 제공하는 것 vs 제공하지 않는 것

```java
volatile int count = 0;

// 보장: 가시성 (Visibility)
// Thread 1의 쓰기가 Thread 2의 읽기에 즉시 반영됨
// CPU 캐시를 거치지 않고 Main Memory를 직접 읽고 씀

// 미보장: 원자성 (Atomicity)
count++;  // 이것은 3단계 연산: read → modify → write
// 여러 스레드가 동시에 실행하면 갱신 손실 발생 가능
```

```java
// volatile이 적합한 경우: 단일 스레드 쓰기, 다수 스레드 읽기
volatile boolean running = true;  // 상태 플래그

void stop() {
    running = false;  // 단일 스레드에서 쓰기
}

void run() {
    while (running) {  // 여러 스레드에서 읽기
        doWork();
    }
}

// volatile이 부적합한 경우: 복합 연산
volatile int counter = 0;
counter++;  // 원자적이지 않음 → AtomicInteger 사용해야 함
```

### 3.2 Double-Checked Locking (DCL) 패턴 - 면접 필수

```java
// 잘못된 DCL: volatile 없음
public class Singleton {
    private static Singleton instance;  // volatile 없음

    public static Singleton getInstance() {
        if (instance == null) {             // 1차 체크
            synchronized (Singleton.class) {
                if (instance == null) {     // 2차 체크
                    instance = new Singleton(); // 문제!
                    // new Singleton()은 3단계:
                    // a) 메모리 할당
                    // b) 생성자 실행 (초기화)
                    // c) instance에 참조 할당
                    // b와 c가 재정렬되면 반쯤 초기화된 객체가 노출!
                }
            }
        }
        return instance;
    }
}

// 올바른 DCL: volatile 필수
public class Singleton {
    private static volatile Singleton instance;  // volatile 추가

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                    // volatile이 있으면 b, c의 재정렬 금지
                    // → 완전히 초기화된 객체만 노출됨
                }
            }
        }
        return instance;
    }
}

// 더 나은 방법: Initialization-on-demand Holder (volatile도 필요 없음)
public class Singleton {
    private Singleton() {}

    private static class Holder {
        private static final Singleton INSTANCE = new Singleton();
        // 클래스 로딩 시 JVM이 원자적으로 초기화 보장
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

---

## 4. synchronized 심화

### 4.1 모니터(Monitor)와 Lock 업그레이드

Java 객체의 헤더(Mark Word)에 lock 정보가 저장됩니다.

```
Lock 상태 전환:
Unlocked → Biased Lock → Lightweight Lock → Heavyweight Lock

Unlocked:
- 아무도 잠금 안 함

Biased Lock (편향 잠금):
- 항상 같은 스레드가 접근하면 CAS 없이 획득
- 스레드 ID를 Mark Word에 기록
- 경합이 없는 경우 가장 빠름

Lightweight Lock (얇은 잠금):
- 다른 스레드가 접근 시도 → Biased Lock 해제
- CAS(Compare-And-Swap) 스핀으로 획득 시도
- 짧은 경합에 적합 (스핀이 CPU를 계속 사용하므로)

Heavyweight Lock (두꺼운 잠금):
- 스핀 한계 초과 → OS Mutex로 전환
- 획득 실패 시 스레드를 Block (CPU 사용 안 함)
- 깨어날 때 비용(컨텍스트 스위칭)이 큼
```

### 4.2 synchronized의 재진입 (Reentrant)

```java
public class Account {
    private int balance = 1000;

    // synchronized 메서드가 같은 객체의 다른 synchronized 메서드를 호출 가능
    public synchronized void transfer(Account to, int amount) {
        this.withdraw(amount);   // 같은 모니터 재진입 가능
        to.deposit(amount);
    }

    public synchronized void withdraw(int amount) {
        balance -= amount;
    }

    public synchronized void deposit(int amount) {
        balance += amount;
    }
}
```

### 4.3 synchronized의 주의사항

```java
// 잘못된 사용: String 리터럴로 잠금 (전역적으로 공유됨)
synchronized ("lock") { ... }  // 절대 하지 말 것

// 잘못된 사용: Integer/Long 캐시 범위 주의
Integer lock = 100;
synchronized (lock) { ... }  // 100은 캐시되므로 다른 곳과 공유될 수 있음

// 올바른 사용: 전용 Object
private final Object lock = new Object();
synchronized (lock) { ... }

// this를 잠금 객체로 사용 시: 외부에서도 접근 가능하므로 주의
synchronized (this) { ... }  // 피하는 것이 좋음
```

---

## 5. java.util.concurrent 패키지

### 5.1 ReentrantLock

```java
ReentrantLock lock = new ReentrantLock(true); // fair=true: 대기 순서 보장

// synchronized가 불가능한 기능들:
// 1. 타임아웃
boolean acquired = lock.tryLock(1, TimeUnit.SECONDS);
if (acquired) {
    try {
        doWork();
    } finally {
        lock.unlock(); // 반드시 finally에서 unlock!
    }
}

// 2. 인터럽트 응답
lock.lockInterruptibly();  // 대기 중 interrupt() 시 InterruptedException

// 3. 공정성 (fair=true)
// synchronized: 무작위로 스레드 선택 (기아 현상 가능)
// ReentrantLock(true): FIFO 순서로 스레드 선택

// 4. 여러 Condition
Condition notFull = lock.newCondition();
Condition notEmpty = lock.newCondition();
notFull.await();    // 조건 대기
notEmpty.signal();  // 특정 조건의 스레드만 깨움
// cf. synchronized는 wait/notify가 하나뿐
```

**공정성(fair) 잠금의 단점:**
```
fair=true일 때:
- 스레드 선택이 예측 가능하지만 처리량(Throughput)이 낮아짐
- OS가 다음 스레드를 깨우는 시간이 추가로 필요
- 일반적으로 fair=false가 더 나은 성능 (기아가 문제 되는 경우에만 fair=true)
```

### 5.2 ReadWriteLock과 StampedLock

```java
// 읽기가 훨씬 많고 쓰기가 드문 경우
ReadWriteLock rwLock = new ReentrantReadWriteLock();

// 읽기: 여러 스레드 동시 허용
rwLock.readLock().lock();
try {
    return data.get(key);
} finally {
    rwLock.readLock().unlock();
}

// 쓰기: 독점 (다른 읽기/쓰기 모두 차단)
rwLock.writeLock().lock();
try {
    data.put(key, value);
} finally {
    rwLock.writeLock().unlock();
}
```

```java
// StampedLock (Java 8+): 낙관적 읽기 지원
StampedLock sl = new StampedLock();

// 낙관적 읽기 (lock 없이 시도)
long stamp = sl.tryOptimisticRead();
int x = this.x;
int y = this.y;

if (!sl.validate(stamp)) {
    // 읽는 동안 쓰기가 발생했으면 비관적 읽기로 재시도
    stamp = sl.readLock();
    try {
        x = this.x;
        y = this.y;
    } finally {
        sl.unlockRead(stamp);
    }
}
// 경합이 드문 경우 ReadWriteLock보다 빠름
```

### 5.3 Atomic 클래스와 CAS (Compare-And-Swap)

```java
// CAS 원리: CPU 명령어 수준의 원자 연산
// 기대값(expected)과 현재값(current)이 같을 때만 새 값(update)으로 교체
// 실패 시 재시도 (스핀)
AtomicInteger count = new AtomicInteger(0);

// 내부적으로 CAS
int result = count.incrementAndGet();  // 원자적 ++
count.compareAndSet(expected, update); // 직접 CAS

// AtomicReference: 객체 참조의 원자적 교체
AtomicReference<Node> head = new AtomicReference<>();
Node oldHead = head.get();
Node newHead = new Node(value, oldHead);
head.compareAndSet(oldHead, newHead);  // Lock-Free 스택 구현
```

```java
// LongAdder: 고경합 환경에서 AtomicLong보다 빠름
// Striped64 기법: 여러 Cell에 값을 분산하여 경합 감소

AtomicLong atomicCounter = new AtomicLong();
// 경합이 심하면 CAS 재시도가 많아져 성능 저하

LongAdder adder = new LongAdder();
adder.increment();   // 각 스레드가 자신의 Cell에 기록
adder.sum();         // 모든 Cell 합산 (스냅샷, 완전히 정확하지 않을 수 있음)

// 선택 기준:
// AtomicLong: 경합이 낮거나, 정확한 실시간 값이 필요
// LongAdder: 경합이 높고, 카운터 합산만 필요 (통계, 모니터링)
```

### 5.4 CountDownLatch, CyclicBarrier, Semaphore

```java
// CountDownLatch: N개 완료를 기다림 (재사용 불가)
CountDownLatch latch = new CountDownLatch(3);

// 세 개의 워커 스레드
executor.submit(() -> { doWork(); latch.countDown(); });
executor.submit(() -> { doWork(); latch.countDown(); });
executor.submit(() -> { doWork(); latch.countDown(); });

latch.await();  // count가 0이 될 때까지 대기
// count가 0이 되면 재사용 불가
```

```java
// CyclicBarrier: N개 스레드가 서로를 기다림 (재사용 가능)
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    System.out.println("모든 스레드가 배리어에 도달!");  // 모두 도달 시 실행
});

// 각 스레드에서:
doPhase1Work();
barrier.await();  // 다른 스레드들도 여기까지 오기를 기다림
doPhase2Work();   // 모두 Phase1 완료 후 Phase2 시작
barrier.await();  // 재사용 가능
```

```java
// Semaphore: 동시 접근 수 제한 (동시에 10개까지만)
Semaphore semaphore = new Semaphore(10);  // 허가증 10개

semaphore.acquire();  // 허가증 획득 (없으면 대기)
try {
    connectToExternalAPI();  // 외부 API 동시 호출 제한
} finally {
    semaphore.release();     // 허가증 반납
}
```

---

## 6. ThreadLocal 심화

### 6.1 동작 원리

```java
// ThreadLocal의 내부 구조
// Thread 객체: ThreadLocalMap threadLocals 필드 보유
// ThreadLocalMap: Entry[] 배열 (Key=WeakReference<ThreadLocal>, Value=Object)

ThreadLocal<String> userContext = new ThreadLocal<>();

// Thread A에서:
userContext.set("userA");   // Thread A의 ThreadLocalMap에 저장
userContext.get();           // "userA" (Thread A의 Map에서 조회)

// Thread B에서:
userContext.get();           // null (Thread B의 Map은 별도)
```

```
구조 상세:
Thread A → ThreadLocalMap → Entry(WeakRef(userContext), "userA")
Thread B → ThreadLocalMap → Entry(WeakRef(userContext), null)

각 Thread가 자신만의 Map을 보유하여 완전히 독립됨
```

### 6.2 메모리 누수 시나리오 (면접 핵심)

```java
// WAS(Tomcat)의 스레드 풀 환경에서의 위험

// 요청 1 처리 (Thread Pool의 Thread-1 사용)
userContext.set("user1");
processRequest1();
// ← 여기서 remove() 누락!

// 요청 2 처리 (Thread Pool이 Thread-1 재사용!)
processRequest2();
userContext.get();  // "user1" 반환! → 보안 이슈, 데이터 오염

// 또한 Entry의 Key(ThreadLocal)는 WeakReference
// ThreadLocal 객체가 GC되면 Key는 null이 됨
// 하지만 Value는 StrongReference → Map에 Entry(null, value)가 남음 → 누수!
```

```java
// 반드시 finally에서 remove()
try {
    userContext.set(getCurrentUser());
    processRequest();
} finally {
    userContext.remove();  // 필수! GC와 관계없이 명시적 제거
}

// Spring Security의 SecurityContextHolder도 동일한 패턴
// Spring이 알아서 처리하지만, 커스텀 ThreadLocal은 직접 관리 필요
```

### 6.3 InheritableThreadLocal

```java
// 부모 스레드의 값을 자식 스레드에 상속
InheritableThreadLocal<String> context = new InheritableThreadLocal<>();
context.set("parentValue");

Thread child = new Thread(() -> {
    System.out.println(context.get()); // "parentValue" 출력
});
child.start();

// Virtual Thread 주의: 수백만 개의 Virtual Thread를 생성할 때
// InheritableThreadLocal을 사용하면 부모 값이 복사되어 메모리 증가
// Java 20+의 ScopedValue가 대안
```

---

## 7. ThreadPoolExecutor 내부 구조와 설정

### 7.1 핵심 파라미터와 작업 추가 흐름

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,                          // corePoolSize: 기본 스레드 수
    10,                         // maximumPoolSize: 최대 스레드 수
    60L, TimeUnit.SECONDS,      // keepAliveTime: 유휴 스레드 유지 시간
    new LinkedBlockingQueue<>(100), // workQueue: 대기열
    new ThreadFactory() { ... }, // threadFactory
    new ThreadPoolExecutor.CallerRunsPolicy() // rejectionHandler
);
```

**작업 추가 시 4단계 흐름:**
```
1. corePool에 여유 있음 → 즉시 새 스레드 생성하여 실행
   (idle 스레드가 있어도 새 스레드 생성 - 중요!)

2. corePool 꽉 참 → workQueue에 추가

3. workQueue도 꽉 참 → maximumPoolSize까지 스레드 추가 후 실행

4. maximumPoolSize도 꽉 참 → RejectionPolicy 실행
```

```
주의: "여유 스레드가 있으면 재사용"이 아니라
      "core 수 미만이면 새 스레드 생성 우선"
      idle 스레드가 있어도 core 수 미만이면 새 스레드 생성!
```

### 7.2 RejectionPolicy 4가지

```java
// 1. AbortPolicy (기본값)
// RejectedExecutionException 던짐 → 호출자가 처리
new ThreadPoolExecutor.AbortPolicy()

// 2. CallerRunsPolicy (추천)
// 작업을 제출한 스레드가 직접 실행
// 자연스러운 Backpressure 효과 → 제출 속도 자동 조절
new ThreadPoolExecutor.CallerRunsPolicy()

// 3. DiscardPolicy
// 조용히 작업 폐기 (오류 없음)
new ThreadPoolExecutor.DiscardPolicy()

// 4. DiscardOldestPolicy
// 가장 오래된 대기 작업 폐기 후 재시도
new ThreadPoolExecutor.DiscardOldestPolicy()
```

### 7.3 스레드 풀 크기 결정 기준

```
I/O 바운드 작업 (DB 쿼리, HTTP 호출):
- 스레드가 대부분 블로킹 상태
- corePoolSize = CPU 코어 수 × 2~4
- maximumPoolSize = corePoolSize × 2

CPU 바운드 작업 (계산, 이미지 처리):
- 스레드가 CPU를 계속 사용
- corePoolSize = CPU 코어 수
- corePoolSize보다 많으면 컨텍스트 스위칭 증가

실무:
- Little's Law: 처리량 = 스레드 수 × (1 / 응답시간)
- 부하 테스트로 병목 지점 확인 후 결정
- Virtual Thread 도입 시 I/O 바운드는 newVirtualThreadPerTaskExecutor() 권장
```

---

## 8. CompletableFuture

### 8.1 기본 구성과 비동기 체이닝

```java
// 비동기 작업 시작
CompletableFuture<User> userFuture =
    CompletableFuture.supplyAsync(() -> fetchUser(userId), customExecutor);

// 체이닝
CompletableFuture<Dashboard> dashboard = userFuture
    .thenApplyAsync(user -> enrichUser(user))      // 변환 (T → U)
    .thenComposeAsync(user -> fetchOrders(user))    // 비동기 flatMap (T → CF<U>)
    .thenCombine(fetchProfile(userId),              // 두 CF 병합
        (orders, profile) -> new Dashboard(orders, profile))
    .exceptionally(ex -> {                          // 에러 처리
        log.error("Error", ex);
        return Dashboard.empty();
    })
    .whenComplete((result, ex) -> {                 // 항상 실행 (finally)
        log.info("Completed");
    });
```

### 8.2 thenApply vs thenCompose vs thenCombine

```java
// thenApply: 동기 변환 (T → U)
CF<String> cf1 = CF.supplyAsync(() -> "hello")
                   .thenApply(s -> s.toUpperCase()); // "HELLO"

// thenCompose: 비동기 변환, flatMap (T → CF<U>)
// 중첩 CF를 평탄화 (CF<CF<U>> → CF<U>)
CF<User> cf2 = CF.supplyAsync(() -> userId)
                 .thenCompose(id -> fetchUserAsync(id)); // CF<User> 반환

// thenCombine: 두 CF가 모두 완료되면 결합
CF<String> cf3 = CF.supplyAsync(() -> "Hello")
                   .thenCombine(
                       CF.supplyAsync(() -> "World"),
                       (a, b) -> a + " " + b  // "Hello World"
                   );
```

### 8.3 실무 주의사항

```java
// 위험: 기본 ForkJoinPool.commonPool() 사용
CompletableFuture.supplyAsync(() -> {
    // ForkJoinPool.commonPool()의 스레드를 사용
    // parallelStream()도 같은 풀 사용
    // I/O 블로킹 작업이면 pool을 잠식하여 다른 곳에 영향
    return httpClient.send(...); // 블로킹!
});

// 올바른 방법: 전용 ExecutorService 지정
ExecutorService ioExecutor = Executors.newFixedThreadPool(20);
CompletableFuture.supplyAsync(() -> httpClient.send(...), ioExecutor);
```

```java
// 위험: 예외 처리 누락
CompletableFuture<String> cf = CF.supplyAsync(() -> {
    throw new RuntimeException("error");
});
// 아무도 예외를 처리하지 않으면 조용히 삼켜짐!
// cf.get() 호출 시 ExecutionException으로 감싸져 나옴

// 올바른 방법: exceptionally 또는 handle 사용
cf.exceptionally(ex -> {
    log.error("비동기 작업 실패", ex);
    return "fallback";
});
```

---

## 9. Virtual Thread (Java 21) 완전 가이드

### 9.1 Platform Thread의 한계

```java
// 기존 방식: Thread-Per-Request
// 동시 요청 10,000개 → OS Thread 10,000개 필요
// OS Thread 1개 = 약 1MB Stack = 총 10GB 메모리
// + 컨텍스트 스위칭 비용 급증

// Reactive 방식 (WebFlux): 해결책이지만 복잡성 증가
// 콜백 지옥, 디버깅 어려움, 학습 곡선

// Virtual Thread: 동기 코드로 Reactive 효과
```

### 9.2 Virtual Thread 생성과 사용

```java
// 단건 생성
Thread vThread = Thread.ofVirtual()
    .name("vt-worker")
    .start(() -> processRequest());

// ExecutorService (Spring Boot 3.2+ 권장 방식)
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // 각 작업마다 새 Virtual Thread 생성 (비용이 매우 낮음)
    List<Future<String>> futures = tasks.stream()
        .map(task -> executor.submit(() -> process(task)))
        .toList();
}

// Spring Boot 3.2+: application.yml 설정으로 전체 적용
// spring.threads.virtual.enabled: true
```

### 9.3 Pinning 문제 (면접 핵심)

```
Virtual Thread가 carrier thread에 고정(pin)되는 경우:
carrier thread가 Virtual Thread와 함께 블로킹되어 다른 Virtual Thread를 처리 불가!
```

```java
// Pinning 발생 케이스 1: synchronized 블록 안에서 블로킹
synchronized (lock) {
    // 여기서 블로킹되면 carrier thread도 함께 블로킹됨!
    Thread.sleep(1000);           // Pinning!
    InputStream is = socket.getInputStream();  // Pinning!
}

// Pinning 발생 케이스 2: native method 실행 중
// JNI 호출 중에는 Virtual Thread를 분리할 수 없음
```

```java
// 해결책: synchronized → ReentrantLock으로 교체
ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    Thread.sleep(1000);           // Pinning 없음! carrier thread 해방
    InputStream is = socket.getInputStream();  // 정상 동작
} finally {
    lock.unlock();
}
```

```bash
# Pinning 진단 방법
-Djdk.tracePinnedThreads=full   # Pinning 발생 시 스택 트레이스 출력
-Djdk.tracePinnedThreads=short  # 간략한 출력
```

### 9.4 Virtual Thread 적합/부적합 사례

```
적합한 경우:
✅ HTTP 서버 요청 처리 (Thread-Per-Request 모델)
✅ 데이터베이스 쿼리
✅ 외부 API 호출
✅ 파일 I/O

부적합한 경우:
❌ CPU 바운드 작업 (이미지 처리, 암호화)
   → Virtual Thread가 carrier thread를 점유하여 다른 VT 못 실행
   → Platform Thread + ForkJoinPool이 더 적합

❌ ThreadLocal을 많이 사용하는 경우
   → Virtual Thread는 수백만 개 생성 가능
   → 각각 ThreadLocal 복사본 보유 → 메모리 급증
   → ScopedValue 사용 권장 (Java 21+)
```

---

## 10. 동시성 자료구조

### 10.1 ConcurrentHashMap 내부 구조 (Java 7 vs Java 8)

```java
// Java 7: Segment 기반 (16개 Segment, 각각 ReentrantLock)
// 최대 16개 스레드가 서로 다른 Segment에 동시 쓰기 가능
// 하나의 Segment 내에서는 직렬화됨

// Java 8: 버킷 단위 CAS + synchronized
// 읽기: lock 없음 (volatile 읽기)
// 쓰기: 해당 버킷의 첫 번째 노드에만 synchronized
//        → 훨씬 높은 동시성!
// 버킷 깊이 8 초과 시: LinkedList → Red-Black Tree로 전환 (O(n) → O(log n))
```

```java
// putIfAbsent vs computeIfAbsent
Map<String, List<String>> map = new ConcurrentHashMap<>();

// putIfAbsent: V를 항상 생성 (낭비 가능)
map.putIfAbsent("key", new ArrayList<>()); // 항상 new ArrayList() 호출

// computeIfAbsent: 없을 때만 생성 (원자적)
map.computeIfAbsent("key", k -> new ArrayList<>()); // 원자적으로 생성

// 멀티스레드 환경에서 computeIfAbsent가 더 안전하고 효율적
```

### 10.2 BlockingQueue의 종류와 선택

```java
// LinkedBlockingQueue: 기본적으로 Integer.MAX_VALUE 크기 (위험!)
// 실수로 무제한 큐 사용 → OOM 위험
new LinkedBlockingQueue<>();  // 피할 것!
new LinkedBlockingQueue<>(1000);  // 반드시 크기 지정

// ArrayBlockingQueue: 고정 크기, fair 옵션 지원
new ArrayBlockingQueue<>(100, true);  // fair=true: FIFO

// SynchronousQueue: 큐 없이 직접 전달 (생산자-소비자 직접 핸드오프)
// Executors.newCachedThreadPool()에서 사용
// put()이 블로킹 → take()가 준비될 때까지 대기

// PriorityBlockingQueue: Comparator 기반 우선순위 정렬
// DelayQueue: 지연 시간이 지난 후 꺼낼 수 있음 (스케줄링에 활용)
```

---

## 11. 데드락, 라이브락, 기아

### 11.1 데드락의 4가지 발생 조건 (Coffman Conditions)

```
다음 4가지가 모두 성립할 때 데드락 발생:

1. 상호 배제 (Mutual Exclusion)
   - 자원은 한 번에 하나의 스레드만 사용 가능

2. 점유와 대기 (Hold and Wait)
   - 자원을 가진 채로 다른 자원을 기다림

3. 비선점 (No Preemption)
   - 스레드가 보유한 자원을 강제로 빼앗을 수 없음

4. 순환 대기 (Circular Wait)
   - Thread A가 자원X 보유 후 자원Y 대기
   - Thread B가 자원Y 보유 후 자원X 대기
```

### 11.2 데드락 예방 전략

```java
// 전략 1: 자원 획득 순서 고정 (Circular Wait 제거)
// 모든 스레드가 항상 같은 순서로 잠금
void transfer(Account from, Account to, int amount) {
    Account first = from.id < to.id ? from : to;  // ID 순서로 정렬
    Account second = from.id < to.id ? to : from;

    synchronized (first) {
        synchronized (second) {
            from.withdraw(amount);
            to.deposit(amount);
        }
    }
}

// 전략 2: tryLock으로 타임아웃 (Hold and Wait 제거)
boolean acquired1 = lock1.tryLock(100, TimeUnit.MILLISECONDS);
if (acquired1) {
    boolean acquired2 = lock2.tryLock(100, TimeUnit.MILLISECONDS);
    if (!acquired2) {
        lock1.unlock();  // 얻지 못하면 보유 중인 자원 해제
        // 잠시 후 재시도 (Exponential Backoff)
    }
}
```

### 11.3 데드락 진단

```java
// 런타임에 데드락 감지
ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
long[] deadlockedThreadIds = mxBean.findDeadlockedThreads();

if (deadlockedThreadIds != null) {
    ThreadInfo[] infos = mxBean.getThreadInfo(deadlockedThreadIds, true, true);
    for (ThreadInfo info : infos) {
        System.out.println(info.toString()); // 스택 트레이스 포함
    }
}

// 명령줄 진단
// jstack <pid> | grep -A 20 "deadlock"
```

### 11.4 라이브락과 기아

```java
// 라이브락: 데드락은 아니지만 진행이 없음
// 두 스레드가 서로를 배려하다가 아무 것도 못 함
// 예: 두 사람이 복도에서 서로 길을 비켜주려다 계속 막힘

void processWithLivelock() {
    while (true) {
        if (lock1.tryLock()) {
            if (!lock2.tryLock()) {
                lock1.unlock();  // 서로 양보
                Thread.sleep(randomTime);  // 랜덤 대기로 해결
            }
        }
    }
}

// 기아 (Starvation): 특정 스레드가 자원을 계속 못 얻음
// 해결: fair=true 잠금 사용, 우선순위 조정
```

---

## 12. 면접 예상 질문 & 모범 답변

### Q1. volatile과 synchronized의 차이가 무엇인가요?

**답변 포인트:**
> "volatile은 가시성(Visibility)만 보장합니다. 한 스레드의 쓰기가 다른 스레드의 읽기에 즉시 반영됩니다. 하지만 원자성(Atomicity)은 보장하지 않습니다. count++ 같은 복합 연산에는 적합하지 않습니다. synchronized는 가시성, 원자성, 재진입성을 모두 보장합니다. 하나의 스레드만 임계 구역에 진입할 수 있고, 블록 진입/퇴출 시 happens-before가 설정됩니다. 단일 변수의 단순 읽기/쓰기는 volatile로, 복합 연산이나 여러 변수의 일관성이 필요하면 synchronized 또는 Atomic 클래스를 사용합니다."

---

### Q2. Double-Checked Locking에서 volatile이 왜 필요한가요?

**답변 포인트:**
> "new Singleton()은 세 단계로 이루어집니다: 메모리 할당, 생성자 실행(초기화), 참조 변수에 주소 할당. volatile 없이는 CPU가 생성자 실행과 참조 할당 순서를 바꿀 수 있습니다. 그러면 다른 스레드가 1차 체크에서 instance가 null이 아니라고 판단하고 반쯤 초기화된 객체를 사용할 수 있습니다. volatile을 선언하면 해당 변수에 대한 쓰기-읽기 사이에 happens-before 관계가 성립하여 순서 재정렬이 방지됩니다."

---

### Q3. ThreadLocal의 메모리 누수가 어떤 상황에서 발생하나요?

**답변 포인트:**
> "WAS의 스레드 풀 환경에서 발생합니다. 스레드 풀의 스레드는 요청이 끝나도 재사용됩니다. 이전 요청에서 ThreadLocal에 값을 set하고 remove를 하지 않으면, 다음 요청에서 같은 스레드가 이전 요청의 값을 가지고 있어 데이터 오염이나 보안 문제가 발생합니다. 또한 ThreadLocalMap의 Entry는 Key가 WeakReference이지만 Value는 StrongReference입니다. ThreadLocal 객체가 GC되면 Key는 null이 되지만 Value는 Map에 남아 메모리 누수가 됩니다. 반드시 try-finally에서 remove()를 호출해야 합니다."

---

### Q4. ThreadPoolExecutor에서 corePoolSize와 maximumPoolSize의 동작 차이는?

**답변 포인트:**
> "작업이 추가되면 4단계로 처리됩니다. 먼저 현재 스레드 수가 corePoolSize 미만이면 idle 스레드가 있어도 새 스레드를 생성합니다. corePoolSize에 도달하면 workQueue에 넣습니다. 큐가 꽉 차면 maximumPoolSize까지 스레드를 추가합니다. maximumPoolSize도 꽉 차면 RejectionPolicy가 실행됩니다. 주의할 점은 '여유 스레드가 있으면 재사용'이 아니라 'core 수 미만이면 새 스레드 우선 생성'이라는 것입니다."

---

### Q5. ConcurrentHashMap이 Hashtable보다 나은 이유는?

**답변 포인트:**
> "Hashtable은 모든 메서드에 synchronized가 걸려있어 한 번에 하나의 스레드만 접근 가능합니다. ConcurrentHashMap은 Java 8 기준으로 읽기에는 lock이 없고, 쓰기는 해당 버킷의 첫 번째 노드에만 synchronized를 겁니다. 서로 다른 버킷에 쓰는 스레드들은 동시에 실행 가능하므로 훨씬 높은 동시성을 제공합니다. 읽기 시에는 volatile로 선언된 노드를 읽으므로 lock 없이도 최신 값을 볼 수 있습니다."

---

### Q6. Virtual Thread의 Pinning 문제란 무엇이고 어떻게 해결하나요?

**답변 포인트:**
> "Virtual Thread는 I/O 블로킹 시 carrier thread에서 분리되어 다른 Virtual Thread가 실행될 수 있는데, 이것이 안 되는 상태를 Pinning이라고 합니다. synchronized 블록 안에서 블로킹 I/O가 발생하거나 native 메서드를 실행 중일 때 발생합니다. Pinning 시 carrier thread도 함께 블로킹되어 Virtual Thread의 장점이 없어집니다. 해결책은 synchronized를 ReentrantLock으로 교체하는 것입니다. ReentrantLock.lock()은 Virtual Thread가 블로킹되어도 carrier thread가 분리될 수 있습니다. -Djdk.tracePinnedThreads=full로 Pinning 발생 지점을 진단할 수 있습니다."

---

### Q7. AtomicLong과 LongAdder 중 어느 것을 선택해야 하나요?

**답변 포인트:**
> "AtomicLong은 단일 메모리 위치에 대한 CAS 연산을 사용합니다. 경합이 적은 환경에서는 매우 빠르지만, 여러 스레드가 동시에 같은 변수를 수정하면 CAS 재시도가 증가하여 성능이 저하됩니다. LongAdder는 Striped64 기법으로 여러 Cell에 값을 분산합니다. 각 스레드가 자신에게 할당된 Cell에 기록하여 경합을 최소화합니다. 최종 합산 시 모든 Cell을 더합니다. 따라서 경합이 높고 정확한 실시간 값보다 누적 통계가 필요한 카운터(요청 수, 에러 수)에는 LongAdder를, 경합이 낮거나 정확한 최신 값이 필요한 경우에는 AtomicLong을 사용합니다."

---

### Q8. 데드락의 4가지 조건이 무엇이며, 실무에서 어떻게 예방하나요?

**답변 포인트:**
> "데드락은 상호 배제, 점유와 대기, 비선점, 순환 대기 네 가지 조건이 모두 성립할 때 발생합니다. 실무에서는 주로 두 가지 방법으로 예방합니다. 첫째, 자원 획득 순서를 항상 고정합니다. 예를 들어 계좌 이체 시 항상 ID가 낮은 계좌를 먼저 잠급니다. 둘째, tryLock으로 타임아웃을 설정하여 일정 시간 안에 획득하지 못하면 보유 중인 잠금을 해제하고 재시도합니다. 발생 후 진단은 jstack이나 ThreadMXBean.findDeadlockedThreads()를 사용합니다."

---

### Q9. CountDownLatch와 CyclicBarrier의 차이는?

**답변 포인트:**
> "CountDownLatch는 하나의 스레드(또는 여러 스레드)가 N개의 이벤트가 완료될 때까지 기다리는 데 사용합니다. countDown()이 N번 호출되면 await()이 해제됩니다. 재사용이 불가능합니다. CyclicBarrier는 N개의 스레드가 모두 특정 지점에 도달할 때까지 서로를 기다립니다. 모두 도달하면 지정된 Runnable(옵션)을 실행하고 다음 단계로 진행합니다. 재사용 가능합니다. 사용 예시로는 CountDownLatch는 여러 초기화 작업이 완료된 후 서버 시작, CyclicBarrier는 병렬 연산의 각 단계 동기화에 사용합니다."

---

### Q10. CompletableFuture에서 기본 스레드 풀을 사용하면 왜 안 되나요?

**답변 포인트:**
> "CompletableFuture.supplyAsync()에서 Executor를 지정하지 않으면 ForkJoinPool.commonPool()을 사용합니다. 이 풀은 parallelStream()도 공유하는 전역 공유 풀입니다. 만약 I/O 블로킹 작업을 이 풀에서 실행하면 풀의 스레드가 블로킹되어 다른 작업도 지연됩니다. 또한 기본 스레드 수가 CPU 코어 수 - 1로 적습니다. I/O 바운드 비동기 작업은 반드시 전용 ExecutorService를 생성하여 지정해야 합니다."

---

### Q11. synchronized와 ReentrantLock 중 어떤 것을 선택해야 하나요?

**답변 포인트:**
> "단순한 상호 배제가 필요하면 synchronized를 사용합니다. JVM이 최적화(Lock Upgrade 등)를 적용하고, 코드가 더 간결하며 예외 시 자동으로 잠금이 해제됩니다. ReentrantLock은 synchronized가 지원하지 않는 기능이 필요할 때 사용합니다: 타임아웃(tryLock), 인터럽트 응답(lockInterruptibly), 공정성 보장(fair=true), 여러 조건 변수(Condition), 현재 대기 중인 스레드 수 확인. 다만 ReentrantLock은 finally에서 반드시 unlock()을 호출해야 하는 번거로움이 있습니다."

---

### Q12. happens-before 관계가 없으면 어떤 문제가 발생하나요?

**답변 포인트:**
> "happens-before 관계가 없으면 가시성과 순서 문제가 발생합니다. 가시성 문제는 한 스레드의 쓰기가 다른 스레드의 읽기에 반영되지 않는 것입니다. CPU 캐시에만 있고 Main Memory에 반영되지 않아서 다른 스레드가 오래된 값을 읽습니다. 순서 문제는 컴파일러와 CPU가 코드 순서를 최적화를 위해 바꿀 수 있는데, happens-before가 없으면 다른 스레드가 중간 상태를 볼 수 있습니다. DCL 패턴에서 volatile 없이 반쯤 초기화된 객체가 노출되는 것이 대표적 예시입니다."

---

### Q13. Virtual Thread와 WebFlux(Reactive Programming)의 차이와 선택 기준은?

**답변 포인트:**
> "둘 다 높은 동시성을 적은 스레드로 달성하는 목표는 같지만 방식이 다릅니다. WebFlux는 비동기 논블로킹 방식으로 적은 스레드가 이벤트 루프에서 콜백을 처리합니다. 코드 작성이 복잡하고 학습 곡선이 있습니다. Virtual Thread는 동기 스타일 코드로 비슷한 효과를 냅니다. 블로킹 I/O에서 JVM이 자동으로 carrier thread를 해방합니다. 기존 동기 코드를 거의 그대로 사용할 수 있어 마이그레이션이 쉽습니다. 새 프로젝트에서는 Virtual Thread가 더 단순하고, 이미 WebFlux로 구성된 프로젝트는 그대로 유지하는 것이 좋습니다."

---

### Q14. ForkJoinPool의 Work Stealing이란 무엇인가요?

**답변 포인트:**
> "Work Stealing은 유휴 스레드가 바쁜 스레드의 작업 큐에서 작업을 훔쳐서 실행하는 알고리즘입니다. 각 스레드가 자신만의 Deque(양방향 큐)를 가집니다. 자신의 작업은 앞에서 꺼내고(LIFO), 다른 스레드에서 훔칠 때는 뒤에서 꺼냅니다(FIFO). 이를 통해 스레드 간 부하가 자동으로 균등화됩니다. CPU 바운드의 재귀적 작업(분할 정복)에 최적화되어 있으며 parallelStream()과 CompletableFuture의 기본 Executor로 사용됩니다."

---

### Q15. 라이브락(Livelock)이 데드락과 어떻게 다른가요?

**답변 포인트:**
> "데드락은 스레드가 완전히 멈춰서 CPU를 사용하지 않습니다. jstack으로 확인하면 BLOCKED 상태로 나타납니다. 라이브락은 스레드가 계속 실행되고 있지만 의미 있는 진행이 없습니다. CPU를 소비하면서 서로 상대방의 상태 변화를 기다리는 상태입니다. 두 사람이 복도에서 서로 비켜주려다 계속 같은 방향으로 이동하는 것과 같습니다. 라이브락 해결은 랜덤 대기 시간(Jitter) 도입으로 서로 다른 타이밍에 재시도하게 합니다. 이더넷의 CSMA/CD 방식도 동일한 원리입니다."

---

## 13. 학습 체크리스트

- [ ] volatile이 가시성만 보장하고 원자성을 보장하지 않는 이유를 CPU 캐시로 설명할 수 있다
- [ ] Double-Checked Locking 패턴을 직접 코드로 작성하고 volatile이 왜 필요한지 설명할 수 있다
- [ ] synchronized의 Lock Upgrade 과정을 Biased → Lightweight → Heavyweight 순서로 설명할 수 있다
- [ ] ReentrantLock이 synchronized보다 추가로 제공하는 4가지 기능을 말할 수 있다
- [ ] ThreadLocal의 메모리 누수가 WAS 스레드 풀 재사용 환경에서 발생하는 이유를 설명할 수 있다
- [ ] ThreadPoolExecutor에 작업이 추가될 때의 4단계 흐름을 설명할 수 있다
- [ ] CallerRunsPolicy가 자연스러운 Backpressure를 제공하는 원리를 설명할 수 있다
- [ ] ConcurrentHashMap의 Java 7(Segment)과 Java 8(CAS + per-bucket synchronized) 차이를 설명할 수 있다
- [ ] AtomicLong과 LongAdder의 선택 기준을 Striped64 관점으로 설명할 수 있다
- [ ] CountDownLatch와 CyclicBarrier의 차이와 각각 적합한 사용 사례를 설명할 수 있다
- [ ] Virtual Thread의 Pinning 문제를 코드 예시와 함께 설명하고 해결책을 제시할 수 있다
- [ ] 데드락의 4가지 조건(Coffman Conditions)을 나열하고 각각의 예방 전략을 설명할 수 있다
- [ ] 라이브락과 데드락의 차이를 설명할 수 있다
- [ ] CompletableFuture에서 기본 ForkJoinPool을 사용하면 안 되는 이유를 설명할 수 있다
- [ ] thenApply, thenCompose, thenCombine의 차이를 설명할 수 있다

---

## 14. 연관 학습 파일

- [`jvm-architecture.md`](./jvm-architecture.md) - JVM 메모리 구조, 객체 헤더(Lock 정보 저장 위치)
- [`garbage-collection.md`](./garbage-collection.md) - GC와 동시성의 관계 (STW 발생 시 스레드 중단)
- [`../database/db-locking-strategies.md`](../database/db-locking-strategies.md) - 낙관적/비관적 락 (DB 레벨 동시성)
- [`../spring/spring-transactional.md`](../spring/spring-transactional.md) - 트랜잭션과 멀티스레딩
