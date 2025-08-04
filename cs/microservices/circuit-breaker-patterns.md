# 서킷 브레이커와 장애 격리 패턴

## 서킷 브레이커 패턴 개요

### 서킷 브레이커란?
**서킷 브레이커**는 전기 회로의 차단기에서 영감을 받은 패턴으로, 외부 서비스 호출 시 장애가 발생했을 때 추가적인 호출을 차단하여 시스템을 보호하는 메커니즘입니다.

### 기본 동작 원리
```
정상 상태 (CLOSED)
    ↓
장애 발생 감지
    ↓
차단 상태 (OPEN) ← 모든 호출 즉시 실패
    ↓
일정 시간 후
    ↓
반열림 상태 (HALF_OPEN) ← 제한적 호출 허용
    ↓
성공 시 → CLOSED / 실패 시 → OPEN
```

## 서킷 브레이커 구현

### 1. 기본 서킷 브레이커 구현
```java
public class CircuitBreaker {
    
    public enum State {
        CLOSED,    // 정상 상태 - 호출 허용
        OPEN,      // 차단 상태 - 호출 차단
        HALF_OPEN  // 반열림 상태 - 제한적 호출 허용
    }
    
    private State state = State.CLOSED;
    private int failureCount = 0;
    private long lastFailureTime = 0;
    
    // 설정값들
    private final int failureThreshold;     // 실패 임계값
    private final long timeoutDuration;     // 차단 유지 시간
    private final int halfOpenMaxCalls;     // 반열림 상태에서 허용할 최대 호출 수
    
    private int halfOpenCallCount = 0;
    private int halfOpenSuccessCount = 0;
    
    public CircuitBreaker(int failureThreshold, long timeoutDuration, int halfOpenMaxCalls) {
        this.failureThreshold = failureThreshold;
        this.timeoutDuration = timeoutDuration;
        this.halfOpenMaxCalls = halfOpenMaxCalls;
    }
    
    public synchronized <T> T call(Supplier<T> operation) throws CircuitBreakerOpenException {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime >= timeoutDuration) {
                state = State.HALF_OPEN;
                halfOpenCallCount = 0;
                halfOpenSuccessCount = 0;
            } else {
                throw new CircuitBreakerOpenException("서킷 브레이커가 OPEN 상태입니다");
            }
        }
        
        if (state == State.HALF_OPEN && halfOpenCallCount >= halfOpenMaxCalls) {
            throw new CircuitBreakerOpenException("반열림 상태에서 최대 호출 수 초과");
        }
        
        try {
            T result = operation.get();
            onSuccess();
            return result;
            
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }
    
    private void onSuccess() {
        if (state == State.HALF_OPEN) {
            halfOpenCallCount++;
            halfOpenSuccessCount++;
            
            // 반열림 상태에서 충분한 성공이 있으면 CLOSED로 전환
            if (halfOpenSuccessCount >= halfOpenMaxCalls / 2) {
                state = State.CLOSED;
                failureCount = 0;
            }
        } else if (state == State.CLOSED) {
            failureCount = 0; // 성공 시 실패 카운터 리셋
        }
    }
    
    private void onFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
        
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
        } else if (failureCount >= failureThreshold) {
            state = State.OPEN;
        }
    }
    
    public State getState() {
        return state;
    }
    
    public int getFailureCount() {
        return failureCount;
    }
}

// 사용 예시
@Service
public class PaymentService {
    
    private final CircuitBreaker circuitBreaker = new CircuitBreaker(5, 60000, 3);
    private final ExternalPaymentClient paymentClient;
    
    public PaymentResult processPayment(PaymentRequest request) {
        try {
            return circuitBreaker.call(() -> paymentClient.processPayment(request));
            
        } catch (CircuitBreakerOpenException e) {
            // 폴백 처리
            return PaymentResult.failure("결제 서비스 일시 중단");
        }
    }
}
```

### 2. 고급 서킷 브레이커 구현
```java
@Component
public class AdvancedCircuitBreaker {
    
    // 슬라이딩 윈도우 방식으로 실패율 계산
    private final CircularBuffer<Boolean> resultBuffer;
    private final int windowSize;
    private final double failureRateThreshold;
    
    // 메트릭 수집
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer responseTimer;
    
    public AdvancedCircuitBreaker(int windowSize, double failureRateThreshold) {
        this.windowSize = windowSize;
        this.failureRateThreshold = failureRateThreshold;
        this.resultBuffer = new CircularBuffer<>(windowSize);
        
        // Micrometer 메트릭 초기화
        this.successCounter = Metrics.counter("circuit_breaker.success");
        this.failureCounter = Metrics.counter("circuit_breaker.failure");
        this.responseTimer = Timer.builder("circuit_breaker.response_time").register(Metrics.globalRegistry);
    }
    
    public <T> T execute(String name, Supplier<T> operation, Function<Exception, T> fallback) {
        Timer.Sample sample = Timer.start();
        
        try {
            if (shouldRejectCall()) {
                return fallback.apply(new CircuitBreakerOpenException("Circuit breaker is OPEN"));
            }
            
            T result = operation.get();
            recordSuccess(sample);
            return result;
            
        } catch (Exception e) {
            recordFailure(sample, e);
            return fallback.apply(e);
        }
    }
    
    private boolean shouldRejectCall() {
        synchronized (resultBuffer) {
            if (resultBuffer.size() < windowSize) {
                return false; // 충분한 데이터가 없으면 호출 허용
            }
            
            long failures = resultBuffer.stream()
                .mapToLong(success -> success ? 0 : 1)
                .sum();
            
            double failureRate = (double) failures / windowSize;
            return failureRate >= failureRateThreshold;
        }
    }
    
    private void recordSuccess(Timer.Sample sample) {
        synchronized (resultBuffer) {
            resultBuffer.add(true);
        }
        successCounter.increment();
        sample.stop(responseTimer);
    }
    
    private void recordFailure(Timer.Sample sample, Exception e) {
        synchronized (resultBuffer) {
            resultBuffer.add(false);
        }
        failureCounter.increment();
        sample.stop(responseTimer);
    }
}

// 원형 버퍼 구현
public class CircularBuffer<T> {
    private final T[] buffer;
    private int head = 0;
    private int size = 0;
    private final int capacity;
    
    @SuppressWarnings("unchecked")
    public CircularBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = (T[]) new Object[capacity];
    }
    
    public synchronized void add(T item) {
        buffer[head] = item;
        head = (head + 1) % capacity;
        
        if (size < capacity) {
            size++;
        }
    }
    
    public synchronized Stream<T> stream() {
        return Arrays.stream(buffer, 0, size);
    }
    
    public synchronized int size() {
        return size;
    }
}
```

### 3. Spring Cloud Circuit Breaker 활용
```java
// Spring Cloud Circuit Breaker 설정
@Configuration
public class CircuitBreakerConfig {
    
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)                    // 슬라이딩 윈도우 크기
                .minimumNumberOfCalls(5)                  // 최소 호출 수
                .failureRateThreshold(50.0f)             // 실패율 임계값 50%
                .waitDurationInOpenState(Duration.ofSeconds(30)) // OPEN 상태 유지 시간
                .permittedNumberOfCallsInHalfOpenState(3) // HALF_OPEN 상태에서 허용 호출 수
                .build())
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))   // 타임아웃 설정
                .build())
            .build());
    }
}

@Service
public class OrderService {
    
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    
    public OrderService(CircuitBreakerFactory circuitBreakerFactory,
                       PaymentClient paymentClient,
                       InventoryClient inventoryClient) {
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
    }
    
    public OrderResult createOrder(OrderRequest request) {
        // 결제 서비스 호출 with Circuit Breaker
        CircuitBreaker paymentCircuitBreaker = circuitBreakerFactory.create("payment-service");
        PaymentResult paymentResult = paymentCircuitBreaker.run(
            () -> paymentClient.processPayment(request.getPaymentInfo()),
            throwable -> PaymentResult.failure("결제 서비스 일시 중단")
        );
        
        if (!paymentResult.isSuccess()) {
            return OrderResult.failure(paymentResult.getErrorMessage());
        }
        
        // 재고 서비스 호출 with Circuit Breaker
        CircuitBreaker inventoryCircuitBreaker = circuitBreakerFactory.create("inventory-service");
        InventoryResult inventoryResult = inventoryCircuitBreaker.run(
            () -> inventoryClient.reserveStock(request.getItems()),
            throwable -> {
                // 재고 서비스 실패 시 결제 롤백
                paymentClient.refund(paymentResult.getTransactionId());
                return InventoryResult.failure("재고 서비스 일시 중단");
            }
        );
        
        if (inventoryResult.isSuccess()) {
            return OrderResult.success(paymentResult.getTransactionId());
        } else {
            return OrderResult.failure(inventoryResult.getErrorMessage());
        }
    }
}
```

## Resilience4j를 활용한 종합적 장애 처리

### 1. 다중 패턴 조합
```java
@Component
public class ResilientExternalService {
    
    private final ExternalApiClient externalApiClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final RateLimiter rateLimiter;
    private final TimeLimiter timeLimiter;
    private final Bulkhead bulkhead;
    
    public ResilientExternalService(ExternalApiClient externalApiClient) {
        this.externalApiClient = externalApiClient;
        
        // Circuit Breaker 설정
        this.circuitBreaker = CircuitBreaker.ofDefaults("external-api");
        
        // Retry 설정
        this.retry = Retry.custom("external-api")
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryOnException(ex -> ex instanceof HttpServerErrorException)
            .build();
        
        // Rate Limiter 설정
        this.rateLimiter = RateLimiter.custom("external-api")
            .limitForPeriod(10)                    // 초당 10회 제한
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(500))
            .build();
        
        // Time Limiter 설정
        this.timeLimiter = TimeLimiter.custom("external-api")
            .timeoutDuration(Duration.ofSeconds(3))
            .build();
        
        // Bulkhead 설정 (동시 실행 제한)
        this.bulkhead = Bulkhead.custom("external-api")
            .maxConcurrentCalls(5)
            .maxWaitDuration(Duration.ofMillis(100))
            .build();
    }
    
    public CompletableFuture<ApiResponse> callExternalApi(ApiRequest request) {
        Supplier<CompletableFuture<ApiResponse>> decoratedSupplier = Decorators
            .ofSupplier(() -> CompletableFuture.supplyAsync(() -> externalApiClient.call(request)))
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .withRateLimiter(rateLimiter)
            .withTimeLimiter(timeLimiter)
            .withBulkhead(bulkhead)
            .decorate();
        
        return decoratedSupplier.get()
            .handle((result, throwable) -> {
                if (throwable != null) {
                    return handleFailure(throwable);
                }
                return result;
            });
    }
    
    private ApiResponse handleFailure(Throwable throwable) {
        if (throwable instanceof CircuitBreakerOpenException) {
            return ApiResponse.error("외부 서비스가 일시적으로 사용 불가능합니다");
        } else if (throwable instanceof RequestNotPermitted) {
            return ApiResponse.error("요청 한도를 초과했습니다");
        } else if (throwable instanceof TimeoutException) {
            return ApiResponse.error("요청 시간이 초과되었습니다");
        } else {
            return ApiResponse.error("알 수 없는 오류가 발생했습니다");
        }
    }
}
```

### 2. 이벤트 리스너를 통한 모니터링
```java
@Component  
public class CircuitBreakerEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerEventListener.class);
    
    @EventListener
    public void onCircuitBreakerEvent(CircuitBreakerEvent event) {
        switch (event.getEventType()) {
            case STATE_TRANSITION:
                CircuitBreakerOnStateTransitionEvent stateTransitionEvent = 
                    (CircuitBreakerOnStateTransitionEvent) event;
                logger.warn("Circuit Breaker {} 상태 변경: {} -> {}", 
                    event.getCircuitBreakerName(),
                    stateTransitionEvent.getStateTransition().getFromState(),
                    stateTransitionEvent.getStateTransition().getToState());
                
                // 알림 발송
                if (stateTransitionEvent.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                    sendAlert("Circuit Breaker OPEN", event.getCircuitBreakerName());
                }
                break;
                
            case SUCCESS:
                logger.debug("Circuit Breaker {} 호출 성공", event.getCircuitBreakerName());
                break;
                
            case ERROR:
                CircuitBreakerOnErrorEvent errorEvent = (CircuitBreakerOnErrorEvent) event;
                logger.error("Circuit Breaker {} 호출 실패: {}", 
                    event.getCircuitBreakerName(), 
                    errorEvent.getThrowable().getMessage());
                break;
        }
    }
    
    private void sendAlert(String message, String circuitBreakerName) {
        // 슬랙, 이메일, SMS 등으로 알림 발송
        // alertService.send(message + ": " + circuitBreakerName);
    }
}

// 메트릭 수집
@Component
public class CircuitBreakerMetrics {
    
    @EventListener
    public void recordCircuitBreakerMetrics(CircuitBreakerEvent event) {
        Tags tags = Tags.of(
            "circuit_breaker", event.getCircuitBreakerName(),
            "event_type", event.getEventType().name()
        );
        
        Metrics.counter("circuit_breaker.events", tags).increment();
        
        if (event instanceof CircuitBreakerOnStateTransitionEvent) {
            CircuitBreakerOnStateTransitionEvent stateEvent = 
                (CircuitBreakerOnStateTransitionEvent) event;
            
            Gauge.builder("circuit_breaker.state")
                .tags(Tags.of("circuit_breaker", event.getCircuitBreakerName()))
                .register(Metrics.globalRegistry, stateEvent.getStateTransition().getToState(),
                    state -> state == CircuitBreaker.State.OPEN ? 1 : 0);
        }
    }
}
```

## 장애 격리 패턴

### 1. Bulkhead 패턴
```java
// 스레드 풀 격리
@Configuration  
public class BulkheadConfig {
    
    @Bean("paymentExecutor")
    public Executor paymentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("payment-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Bean("inventoryExecutor")  
    public Executor inventoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("inventory-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}

@Service
public class OrderProcessingService {
    
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    
    // 각 서비스별로 독립적인 스레드 풀 사용
    @Async("paymentExecutor")
    public CompletableFuture<PaymentResult> processPayment(PaymentRequest request) {
        return CompletableFuture.completedFuture(paymentService.process(request));
    }
    
    @Async("inventoryExecutor")
    public CompletableFuture<InventoryResult> reserveStock(List<OrderItem> items) {
        return CompletableFuture.completedFuture(inventoryService.reserve(items));
    }
    
    @Async("notificationExecutor")
    public CompletableFuture<Void> sendNotification(NotificationRequest request) {
        notificationService.send(request);
        return CompletableFuture.completedFuture(null);
    }
    
    public OrderResult processOrder(OrderRequest request) {
        try {
            // 병렬 처리로 성능 향상, 각각 격리된 스레드 풀 사용
            CompletableFuture<PaymentResult> paymentFuture = processPayment(request.getPayment());
            CompletableFuture<InventoryResult> inventoryFuture = reserveStock(request.getItems());
            
            // 결과 조합
            CompletableFuture<OrderResult> orderFuture = paymentFuture.thenCombine(
                inventoryFuture,
                (paymentResult, inventoryResult) -> {
                    if (paymentResult.isSuccess() && inventoryResult.isSuccess()) {
                        // 비동기 알림 (격리된 스레드 풀)
                        sendNotification(NotificationRequest.orderCreated(request));
                        return OrderResult.success();
                    } else {
                        return OrderResult.failure("주문 처리 실패");
                    }
                });
            
            return orderFuture.get(10, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            return OrderResult.failure("주문 처리 중 오류 발생: " + e.getMessage());
        }
    }
}
```

### 2. Semaphore 기반 동시성 제어
```java
@Component
public class ResourceLimitedService {
    
    // 각 리소스별 Semaphore로 동시 접근 제한
    private final Semaphore databaseSemaphore = new Semaphore(10);     // DB 연결 풀 제한
    private final Semaphore externalApiSemaphore = new Semaphore(5);   // 외부 API 호출 제한
    private final Semaphore fileSemaphore = new Semaphore(3);          // 파일 처리 제한
    
    public DatabaseResult queryDatabase(String query) throws InterruptedException {
        boolean acquired = databaseSemaphore.tryAcquire(1, TimeUnit.SECONDS);
        if (!acquired) {
            throw new ResourceUnavailableException("데이터베이스 리소스 한도 초과");
        }
        
        try {
            // 데이터베이스 쿼리 실행
            return databaseService.execute(query);
        } finally {
            databaseSemaphore.release();
        }
    }
    
    public ApiResponse callExternalApi(ApiRequest request) throws InterruptedException {
        boolean acquired = externalApiSemaphore.tryAcquire(2, TimeUnit.SECONDS);
        if (!acquired) {
            throw new ResourceUnavailableException("외부 API 리소스 한도 초과");
        }
        
        try {
            return externalApiClient.call(request);
        } finally {
            externalApiSemaphore.release();
        }
    }
    
    public FileProcessResult processFile(File file) throws InterruptedException {
        boolean acquired = fileSemaphore.tryAcquire(5, TimeUnit.SECONDS);
        if (!acquired) {
            throw new ResourceUnavailableException("파일 처리 리소스 한도 초과");
        }
        
        try {
            return fileProcessor.process(file);
        } finally {
            fileSemaphore.release();
        }
    }
}
```

## 실무 적용 사례

### 케이스 1: 전자상거래 결제 시스템
```java
@Service
public class PaymentProcessingService {
    
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final PaymentGatewayRouter paymentGatewayRouter;
    
    public PaymentResult processPayment(PaymentRequest request) {
        List<PaymentGateway> gateways = paymentGatewayRouter.getAvailableGateways(request);
        
        for (PaymentGateway gateway : gateways) {
            CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(gateway.getName());
            
            try {
                return circuitBreaker.call(() -> gateway.processPayment(request));
                
            } catch (CircuitBreakerOpenException e) {
                logger.warn("결제 게이트웨이 {} 서킷 브레이커 OPEN, 다음 게이트웨이 시도", gateway.getName());
                continue; // 다음 게이트웨이 시도
                
            } catch (Exception e) {
                logger.error("결제 게이트웨이 {} 호출 실패: {}", gateway.getName(), e.getMessage());
                continue; // 다음 게이트웨이 시도
            }
        }
        
        return PaymentResult.failure("모든 결제 게이트웨이 사용 불가");
    }
    
    private CircuitBreaker getOrCreateCircuitBreaker(String gatewayName) {
        return circuitBreakers.computeIfAbsent(gatewayName, name -> 
            new CircuitBreaker(5, 30000L, 3) // 5회 실패, 30초 차단, 3회 테스트
        );
    }
}
```

### 케이스 2: MSA 간 통신 안정성 확보
```java
@Component
public class ResilientServiceCommunication {
    
    // 각 서비스별 독립적인 서킷 브레이커
    private final CircuitBreaker userServiceCircuitBreaker;
    private final CircuitBreaker orderServiceCircuitBreaker;
    private final CircuitBreaker inventoryServiceCircuitBreaker;
    
    public ResilientServiceCommunication() {
        this.userServiceCircuitBreaker = CircuitBreaker.custom("user-service")
            .failureRateThreshold(30.0f)           // 30% 실패율
            .waitDurationInOpenState(Duration.ofSeconds(20))
            .slidingWindowSize(10)
            .build();
            
        this.orderServiceCircuitBreaker = CircuitBreaker.custom("order-service")
            .failureRateThreshold(50.0f)           // 50% 실패율
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(20)
            .build();
            
        this.inventoryServiceCircuitBreaker = CircuitBreaker.custom("inventory-service")
            .failureRateThreshold(40.0f)           // 40% 실패율
            .waitDurationInOpenState(Duration.ofSeconds(25))
            .slidingWindowSize(15)
            .build();
    }
    
    public UserDto getUser(Long userId) {
        return userServiceCircuitBreaker.executeSupplier(() -> 
            userServiceClient.getUser(userId)
        ).recover(throwable -> UserDto.createFallback(userId));
    }
    
    public OrderDto getOrder(Long orderId) {
        return orderServiceCircuitBreaker.executeSupplier(() ->
            orderServiceClient.getOrder(orderId)  
        ).recover(throwable -> OrderDto.createFallback(orderId));
    }
    
    public InventoryDto checkInventory(Long productId) {
        return inventoryServiceCircuitBreaker.executeSupplier(() ->
            inventoryServiceClient.checkStock(productId)
        ).recover(throwable -> InventoryDto.createUnavailable(productId));
    }
}
```

### 케이스 3: 대용량 배치 처리에서의 장애 격리
```java
@Component
public class BatchProcessingService {
    
    private final Semaphore batchSemaphore = new Semaphore(3); // 최대 3개 배치 동시 실행
    private final CircuitBreaker dbCircuitBreaker;
    private final ExecutorService batchExecutor;
    
    public BatchProcessingService() {
        this.dbCircuitBreaker = CircuitBreaker.custom("batch-db")
            .failureRateThreshold(70.0f)  // 배치는 더 높은 실패율 허용
            .waitDurationInOpenState(Duration.ofMinutes(5))
            .slidingWindowSize(20)
            .build();
            
        this.batchExecutor = Executors.newFixedThreadPool(3);
    }
    
    @Async
    public CompletableFuture<BatchResult> processBatch(BatchJob job) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Semaphore로 동시 실행 제한
                boolean acquired = batchSemaphore.tryAcquire(30, TimeUnit.SECONDS);
                if (!acquired) {
                    return BatchResult.failure("배치 처리 한도 초과");
                }
                
                try {
                    return dbCircuitBreaker.executeSupplier(() -> {
                        // 청크 단위로 처리하여 장애 영향 최소화
                        return processJobInChunks(job);
                    }).recover(throwable -> {
                        // DB 장애 시 파일로 백업 저장
                        saveToFile(job);
                        return BatchResult.partial("DB 장애로 파일에 백업 저장");
                    });
                    
                } finally {
                    batchSemaphore.release();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return BatchResult.failure("배치 처리 중단");
            }
            
        }, batchExecutor);
    }
    
    private BatchResult processJobInChunks(BatchJob job) {
        List<BatchChunk> chunks = job.splitIntoChunks(1000); // 1000개씩 청크 분할
        int successCount = 0;
        
        for (BatchChunk chunk : chunks) {
            try {
                processChunk(chunk);
                successCount++;
            } catch (Exception e) {
                logger.error("청크 처리 실패: {}", e.getMessage());
                // 하나의 청크 실패가 전체 배치를 중단시키지 않음
            }
        }
        
        return BatchResult.success(successCount, chunks.size());
    }
}
```

## 모니터링과 알림

### 1. 메트릭 수집
```java
@Component
public class CircuitBreakerMonitoring {
    
    private final MeterRegistry meterRegistry;
    
    public CircuitBreakerMonitoring(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @EventListener
    public void handleCircuitBreakerEvent(CircuitBreakerEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        Tags tags = Tags.of(
            "circuit_breaker_name", event.getCircuitBreakerName(),
            "event_type", event.getEventType().name()
        );
        
        // 이벤트 카운터
        Counter.builder("circuit_breaker.events")
            .tags(tags)
            .register(meterRegistry)
            .increment();
        
        // 상태 게이지
        if (event instanceof CircuitBreakerOnStateTransitionEvent) {
            CircuitBreakerOnStateTransitionEvent stateEvent = 
                (CircuitBreakerOnStateTransitionEvent) event;
                
            Gauge.builder("circuit_breaker.state")
                .tags("circuit_breaker_name", event.getCircuitBreakerName())
                .register(meterRegistry, stateEvent.getStateTransition().getToState(),
                    state -> state == CircuitBreaker.State.OPEN ? 1 : 
                             state == CircuitBreaker.State.HALF_OPEN ? 0.5 : 0);
        }
        
        // 응답 시간 측정
        sample.stop(Timer.builder("circuit_breaker.response_time")
            .tags(tags)
            .register(meterRegistry));
    }
}
```

### 2. 알림 시스템
```java
@Component
public class CircuitBreakerAlertService {
    
    private final SlackNotificationService slackService;
    private final EmailService emailService;
    
    // 알림 중복 방지를 위한 캐시
    private final Cache<String, Boolean> alertCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(10))
        .build();
    
    @EventListener
    public void handleCircuitBreakerStateChange(CircuitBreakerOnStateTransitionEvent event) {
        String circuitBreakerName = event.getCircuitBreakerName();
        CircuitBreaker.State newState = event.getStateTransition().getToState();
        
        if (newState == CircuitBreaker.State.OPEN) {
            sendCriticalAlert(circuitBreakerName, "OPEN");
        } else if (newState == CircuitBreaker.State.HALF_OPEN) {
            sendWarningAlert(circuitBreakerName, "HALF_OPEN");
        } else if (newState == CircuitBreaker.State.CLOSED) {
            sendRecoveryAlert(circuitBreakerName, "CLOSED");
        }
    }
    
    private void sendCriticalAlert(String circuitBreakerName, String state) {
        String alertKey = circuitBreakerName + ":" + state;
        
        // 10분 내 중복 알림 방지
        if (alertCache.getIfPresent(alertKey) != null) {
            return;
        }
        
        String message = String.format(
            "🚨 긴급: Circuit Breaker '%s'가 OPEN 상태가 되었습니다. 즉시 확인이 필요합니다.",
            circuitBreakerName
        );
        
        // 즉시 알림
        slackService.sendToChannel("#alerts", message);
        emailService.sendToOncallTeam("Circuit Breaker OPEN Alert", message);
        
        alertCache.put(alertKey, true);
    }
    
    private void sendWarningAlert(String circuitBreakerName, String state) {
        String message = String.format(
            "⚠️ 주의: Circuit Breaker '%s'가 HALF_OPEN 상태입니다. 모니터링이 필요합니다.",
            circuitBreakerName
        );
        
        slackService.sendToChannel("#monitoring", message);
    }
    
    private void sendRecoveryAlert(String circuitBreakerName, String state) {
        String message = String.format(
            "✅ 복구: Circuit Breaker '%s'가 정상(CLOSED) 상태로 복구되었습니다.",
            circuitBreakerName
        );
        
        slackService.sendToChannel("#monitoring", message);
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "서킷 브레이커가 HALF_OPEN 상태에서 어떤 기준으로 CLOSED/OPEN을 결정하나요?"
**답변 포인트:**
- **성공 비율 기준**: 설정된 최소 성공률 달성 시 CLOSED로 전환
- **연속 성공 횟수**: 지정된 횟수만큼 연속 성공 시 정상 상태로 판단
- **단일 실패 즉시 차단**: 하나라도 실패하면 즉시 OPEN으로 전환
- **타임아웃 고려**: 응답 시간도 성공/실패 판단 기준에 포함

### Q2: "마이크로서비스 환경에서 서킷 브레이커를 어디에 위치시켜야 하나요?"
**답변 포인트:**
- **클라이언트 사이드**: 각 서비스의 클라이언트에 구현 (일반적)
- **API 게이트웨이**: 중앙집중식 관리 가능
- **사이드카 프록시**: Istio 등 서비스 메시 활용
- **하이브리드 접근**: 중요도에 따라 다층으로 구성

### Q3: "서킷 브레이커와 재시도 패턴을 함께 사용할 때 주의점은?"
**답변 포인트:**
- **재시도 우선순위**: 재시도를 먼저 적용하고 서킷 브레이커로 감싸기
- **백오프 전략**: 지수 백오프로 시스템 부하 방지
- **최대 재시도 제한**: 무한 재시도로 인한 시스템 과부하 방지
- **빠른 실패**: 명백한 실패(4xx 에러)는 재시도하지 않기

## 실무 베스트 프랙티스

1. **적절한 임계값 설정**: 비즈니스 특성에 맞는 실패율과 대기시간 설정
2. **폴백 메커니즘**: 서킷 브레이커 동작 시 적절한 대안 제공
3. **모니터링 필수**: 상태 변화와 메트릭을 실시간 모니터링
4. **점진적 적용**: 중요한 서비스부터 단계적으로 적용
5. **테스트 자동화**: 장애 상황을 시뮬레이션하는 카오스 엔지니어링 도입

서킷 브레이커와 장애 격리 패턴은 분산 시스템의 안정성을 크게 향상시키는 필수적인 패턴입니다. 적절한 설정과 모니터링을 통해 시스템 전체의 가용성을 보장할 수 있습니다.