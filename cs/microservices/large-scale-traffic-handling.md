# 대규모 트래픽 환경에서의 시스템 설계와 문제 해결

## 핵심 개념

### 대규모 트래픽이란?
- **동시 접속자**: 수만~수백만 명의 동시 사용자
- **높은 TPS**: 초당 수천~수만 건의 트랜잭션 처리
- **데이터 볼륨**: TB~PB 단위의 데이터 처리
- **글로벌 서비스**: 지역별 분산 처리 필요

## 1. 대규모 트래픽 환경에서 발생하는 주요 문제점들

### 1.1 데이터베이스 병목 현상

#### 문제 상황
```java
// 잘못된 예: 모든 요청이 DB를 직접 조회
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public Product getProduct(Long productId) {
        // 매번 DB 조회 - 트래픽 증가 시 DB 부하
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException());
    }
}
```

#### 해결 방안: 캐싱 전략
```java
// 개선된 예: 다층 캐싱 구조
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisTemplate<String, Product> redisTemplate;

    private final LoadingCache<Long, Product> localCache =
        Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(this::loadFromRedis);

    public Product getProduct(Long productId) {
        // 1차: 로컬 캐시 (L1)
        try {
            return localCache.get(productId);
        } catch (Exception e) {
            // 캐시 미스 시 직접 조회
            return loadFromDatabase(productId);
        }
    }

    private Product loadFromRedis(Long productId) {
        // 2차: Redis 캐시 (L2)
        String key = "product:" + productId;
        Product product = redisTemplate.opsForValue().get(key);

        if (product == null) {
            // 3차: DB 조회
            product = loadFromDatabase(productId);
            // Redis에 캐싱 (TTL 30분)
            redisTemplate.opsForValue().set(key, product, 30, TimeUnit.MINUTES);
        }

        return product;
    }

    private Product loadFromDatabase(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException());
    }
}
```

### 1.2 Connection Pool 고갈

#### 문제 상황
```yaml
# 잘못된 설정: 부족한 커넥션 풀
spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # 너무 작음
      connection-timeout: 30000
```

#### 해결 방안
```yaml
# 개선된 설정: 적절한 커넥션 풀 크기
spring:
  datasource:
    hikari:
      # 공식: connections = ((core_count * 2) + effective_spindle_count)
      # 예: 4코어 서버 = (4 * 2) + 1 = 9, 여유있게 20으로 설정
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 3000  # 빠른 실패
      idle-timeout: 600000      # 10분
      max-lifetime: 1800000     # 30분
      leak-detection-threshold: 60000  # 커넥션 누수 감지
```

```java
// 커넥션 효율적 사용
@Service
public class OrderService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 잘못된 예: 불필요하게 긴 트랜잭션
    @Transactional
    public void processOrder(Order order) {
        saveOrder(order);
        sendEmail(order);        // 외부 API 호출 - 트랜잭션 불필요
        updateInventory(order);
        generateInvoice(order);  // 파일 I/O - 트랜잭션 불필요
    }

    // 개선된 예: 트랜잭션 범위 최소화
    public void processOrder(Order order) {
        // DB 작업만 트랜잭션으로 묶기
        Long orderId = saveOrderWithTransaction(order);

        // 외부 작업은 비동기 처리
        CompletableFuture.runAsync(() -> sendEmail(order));
        CompletableFuture.runAsync(() -> generateInvoice(order));

        updateInventoryWithTransaction(order);
    }

    @Transactional
    public Long saveOrderWithTransaction(Order order) {
        return orderRepository.save(order).getId();
    }
}
```

### 1.3 메모리 부족 (OutOfMemoryError)

#### 문제 상황
```java
// 메모리 누수 위험이 있는 코드
@RestController
public class ReportController {

    // 정적 컬렉션에 계속 쌓임 - 메모리 누수!
    private static List<UserActivity> activityLog = new ArrayList<>();

    @GetMapping("/report/users")
    public List<UserDto> getAllUsers() {
        // 대용량 데이터 한번에 로딩 - OOM 위험!
        return userRepository.findAll().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }
}
```

#### 해결 방안
```java
// 개선된 예: 스트리밍과 페이징
@RestController
public class ReportController {

    @GetMapping("/report/users")
    public void streamUsers(HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter writer = response.getWriter();
        writer.write("[");

        // 스트리밍 방식으로 데이터 처리
        AtomicBoolean first = new AtomicBoolean(true);
        userRepository.findAll().forEach(user -> {
            if (!first.getAndSet(false)) {
                writer.write(",");
            }
            writer.write(objectMapper.writeValueAsString(user));
            writer.flush();  // 즉시 전송
        });

        writer.write("]");
    }

    // 또는 페이징 사용
    @GetMapping("/report/users/paged")
    public Page<UserDto> getUsersPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return userRepository.findAll(pageable)
            .map(this::convertToDto);
    }
}

// JVM 옵션 최적화
// -Xms4g -Xmx4g              # 힙 크기 고정
// -XX:+UseG1GC               # G1GC 사용 (대용량에 적합)
// -XX:MaxGCPauseMillis=200   # GC 일시 정지 목표
// -XX:+HeapDumpOnOutOfMemoryError  # OOM 시 힙 덤프
```

### 1.4 네트워크 타임아웃과 지연

#### 문제 상황
```java
// 동기 방식의 느린 외부 API 호출
@Service
public class PaymentService {

    @Autowired
    private RestTemplate restTemplate;

    public Payment processPayment(PaymentRequest request) {
        // 외부 결제 API 호출 - 응답 느리면 전체 시스템 지연
        String response = restTemplate.postForObject(
            paymentApiUrl, request, String.class);

        // 재고 확인 API
        restTemplate.getForObject(inventoryApiUrl, Inventory.class);

        // 포인트 차감 API
        restTemplate.postForObject(pointApiUrl, request, String.class);

        return parseResponse(response);
    }
}
```

#### 해결 방안
```java
// 개선: 비동기 + 타임아웃 + 재시도
@Service
public class PaymentService {

    @Autowired
    private WebClient webClient;

    @Autowired
    private Executor asyncExecutor;

    public CompletableFuture<Payment> processPayment(PaymentRequest request) {

        // 병렬 처리
        CompletableFuture<String> paymentFuture = callPaymentApi(request);
        CompletableFuture<Inventory> inventoryFuture = checkInventory(request);
        CompletableFuture<Point> pointFuture = deductPoints(request);

        // 모든 호출 완료 대기
        return CompletableFuture.allOf(paymentFuture, inventoryFuture, pointFuture)
            .thenApply(v -> {
                String payment = paymentFuture.join();
                Inventory inventory = inventoryFuture.join();
                Point point = pointFuture.join();
                return buildPaymentResult(payment, inventory, point);
            });
    }

    private CompletableFuture<String> callPaymentApi(PaymentRequest request) {
        return webClient.post()
            .uri(paymentApiUrl)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(3))  // 3초 타임아웃
            .retry(3)  // 3번 재시도
            .toFuture();
    }
}

// WebClient 설정
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .responseTimeout(Duration.ofSeconds(3))
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(3))
                    .addHandlerLast(new WriteTimeoutHandler(3)));

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
```

### 1.5 급격한 트래픽 증가 (Traffic Spike)

#### 문제 상황
```
일반 트래픽: 1,000 TPS
이벤트 시작: 50,000 TPS (50배 증가)
결과: 시스템 다운, 응답 지연
```

#### 해결 방안: Rate Limiting과 Queue
```java
// Rate Limiter 구현
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // IP당 분당 100개 요청 제한
    private final LoadingCache<String, AtomicInteger> requestCountsPerIpAddress =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(key -> new AtomicInteger(0));

    private static final int MAX_REQUESTS_PER_MINUTE = 100;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = getClientIP(request);
        AtomicInteger requests = requestCountsPerIpAddress.get(clientIp);

        if (requests.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests");
            return;
        }

        filterChain.doFilter(request, response);
    }
}

// Redis 기반 분산 Rate Limiter
@Component
public class RedisRateLimiter {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public boolean allowRequest(String userId) {
        String key = "rate_limit:" + userId;
        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount == 1) {
            // 첫 요청이면 TTL 설정
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }

        return currentCount <= 100;  // 분당 100개 제한
    }
}

// 큐를 통한 트래픽 제어
@Service
public class OrderQueueService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // 요청을 큐에 적재
    public String enqueueOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        rabbitTemplate.convertAndSend("order.queue",
            new OrderMessage(orderId, request));

        return orderId;  // 즉시 주문 ID 반환
    }

    // 워커가 큐에서 꺼내 처리 (속도 조절 가능)
    @RabbitListener(queues = "order.queue", concurrency = "10-20")
    public void processOrder(OrderMessage message) {
        try {
            orderService.processOrder(message.getRequest());
        } catch (Exception e) {
            // DLQ로 이동
            rabbitTemplate.convertAndSend("order.dlq", message);
        }
    }
}
```

## 2. 분산 환경에서의 데이터 정합성 보장 방법

### 2.1 분산 트랜잭션 문제

#### 문제 상황
```java
// 여러 서비스에 걸친 트랜잭션 - 일관성 보장 어려움
public void purchaseProduct(Long userId, Long productId, int quantity) {
    // 1. 재고 차감 (Inventory Service)
    inventoryService.decreaseStock(productId, quantity);

    // 2. 결제 처리 (Payment Service)
    paymentService.charge(userId, price);

    // 3. 주문 생성 (Order Service)
    orderService.createOrder(userId, productId, quantity);

    // 만약 3번에서 실패하면? 1, 2번은 이미 완료됨!
}
```

### 2.2 해결 방안 1: Saga Pattern (Choreography)

```java
// 주문 서비스: 주문 생성 및 이벤트 발행
@Service
public class OrderService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OrderRepository orderRepository;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // 주문을 PENDING 상태로 생성
        Order order = Order.builder()
            .userId(request.getUserId())
            .productId(request.getProductId())
            .quantity(request.getQuantity())
            .status(OrderStatus.PENDING)
            .build();

        orderRepository.save(order);

        // 이벤트 발행: 재고 확인 요청
        eventPublisher.publishEvent(new OrderCreatedEvent(order));

        return order;
    }

    // 재고 확인 완료 이벤트 수신
    @TransactionalEventListener
    public void handleStockReserved(StockReservedEvent event) {
        Order order = orderRepository.findById(event.getOrderId())
            .orElseThrow();

        // 결제 요청 이벤트 발행
        eventPublisher.publishEvent(new PaymentRequestEvent(order));
    }

    // 결제 완료 이벤트 수신
    @TransactionalEventListener
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        Order order = orderRepository.findById(event.getOrderId())
            .orElseThrow();

        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
    }

    // 실패 이벤트 수신 - 보상 트랜잭션
    @TransactionalEventListener
    public void handlePaymentFailed(PaymentFailedEvent event) {
        Order order = orderRepository.findById(event.getOrderId())
            .orElseThrow();

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // 재고 복구 이벤트 발행
        eventPublisher.publishEvent(new StockReleaseEvent(order));
    }
}

// 재고 서비스
@Service
public class InventoryService {

    @TransactionalEventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            // 재고 확인 및 예약
            boolean reserved = reserveStock(
                event.getProductId(),
                event.getQuantity()
            );

            if (reserved) {
                eventPublisher.publishEvent(
                    new StockReservedEvent(event.getOrderId())
                );
            } else {
                eventPublisher.publishEvent(
                    new StockReservationFailedEvent(event.getOrderId())
                );
            }
        } catch (Exception e) {
            eventPublisher.publishEvent(
                new StockReservationFailedEvent(event.getOrderId())
            );
        }
    }

    // 보상 트랜잭션: 재고 복구
    @TransactionalEventListener
    public void handleStockRelease(StockReleaseEvent event) {
        releaseStock(event.getProductId(), event.getQuantity());
    }
}

// 결제 서비스
@Service
public class PaymentService {

    @TransactionalEventListener
    public void handlePaymentRequest(PaymentRequestEvent event) {
        try {
            Payment payment = processPayment(
                event.getUserId(),
                event.getAmount()
            );

            eventPublisher.publishEvent(
                new PaymentCompletedEvent(event.getOrderId(), payment)
            );
        } catch (Exception e) {
            eventPublisher.publishEvent(
                new PaymentFailedEvent(event.getOrderId(), e.getMessage())
            );
        }
    }
}
```

### 2.3 해결 방안 2: Saga Pattern (Orchestration)

```java
// Saga Orchestrator
@Service
public class OrderSagaOrchestrator {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    public CompletableFuture<OrderResult> executeOrderSaga(OrderRequest request) {

        SagaContext context = new SagaContext(request);

        return CompletableFuture
            // Step 1: 주문 생성
            .supplyAsync(() -> createOrder(context))
            // Step 2: 재고 예약
            .thenCompose(order -> reserveInventory(context, order))
            // Step 3: 결제 처리
            .thenCompose(order -> processPayment(context, order))
            // Step 4: 주문 확정
            .thenApply(order -> completeOrder(context, order))
            // 실패 시 보상 트랜잭션 실행
            .exceptionally(ex -> compensate(context, ex));
    }

    private CompletableFuture<Order> reserveInventory(
            SagaContext context, Order order) {

        return inventoryService.reserveStock(
                order.getProductId(),
                order.getQuantity())
            .thenApply(reserved -> {
                if (!reserved) {
                    throw new InsufficientStockException();
                }
                context.addCompensation(() ->
                    inventoryService.releaseStock(
                        order.getProductId(),
                        order.getQuantity()
                    )
                );
                return order;
            });
    }

    private OrderResult compensate(SagaContext context, Throwable ex) {
        // 역순으로 보상 트랜잭션 실행
        context.getCompensations()
            .stream()
            .sorted(Comparator.reverseOrder())
            .forEach(compensation -> {
                try {
                    compensation.run();
                } catch (Exception e) {
                    log.error("Compensation failed", e);
                }
            });

        return OrderResult.failed(ex.getMessage());
    }
}
```

### 2.4 이벤트 소싱과 CQRS

```java
// 이벤트 저장소
@Entity
@Table(name = "event_store")
public class EventStore {

    @Id
    private String eventId;

    private String aggregateId;  // 주문 ID
    private String eventType;     // OrderCreated, PaymentCompleted 등

    @Column(columnDefinition = "TEXT")
    private String eventData;     // JSON

    private LocalDateTime occurredAt;
    private Long version;         // 낙관적 잠금
}

// 이벤트 기반 주문 상태 재구성
@Service
public class OrderEventSourcingService {

    @Autowired
    private EventStoreRepository eventStoreRepository;

    // 현재 상태는 이벤트를 재생하여 계산
    public Order getOrderCurrentState(String orderId) {
        List<EventStore> events = eventStoreRepository
            .findByAggregateIdOrderByVersion(orderId);

        Order order = new Order();

        for (EventStore event : events) {
            switch (event.getEventType()) {
                case "OrderCreated":
                    OrderCreatedEvent created = parseEvent(event, OrderCreatedEvent.class);
                    order.setId(created.getOrderId());
                    order.setStatus(OrderStatus.PENDING);
                    break;

                case "PaymentCompleted":
                    order.setStatus(OrderStatus.PAID);
                    break;

                case "OrderShipped":
                    order.setStatus(OrderStatus.SHIPPED);
                    break;

                case "OrderCancelled":
                    order.setStatus(OrderStatus.CANCELLED);
                    break;
            }
        }

        return order;
    }

    // 이벤트 저장 (Optimistic Locking)
    @Transactional
    public void saveEvent(String aggregateId, DomainEvent event) {
        // 현재 버전 확인
        Long currentVersion = eventStoreRepository
            .findMaxVersionByAggregateId(aggregateId)
            .orElse(0L);

        EventStore eventStore = EventStore.builder()
            .eventId(UUID.randomUUID().toString())
            .aggregateId(aggregateId)
            .eventType(event.getClass().getSimpleName())
            .eventData(toJson(event))
            .occurredAt(LocalDateTime.now())
            .version(currentVersion + 1)
            .build();

        try {
            eventStoreRepository.save(eventStore);
        } catch (OptimisticLockException e) {
            // 동시성 충돌 - 재시도
            throw new ConcurrentModificationException("Event version conflict");
        }
    }
}

// CQRS: 읽기 모델 분리
@Service
public class OrderQueryService {

    @Autowired
    private MongoTemplate mongoTemplate;  // 읽기 전용 DB

    // 이벤트 발생 시 읽기 모델 업데이트
    @EventListener
    public void handleOrderEvent(DomainEvent event) {
        if (event instanceof OrderCreatedEvent) {
            OrderCreatedEvent created = (OrderCreatedEvent) event;

            OrderReadModel readModel = OrderReadModel.builder()
                .orderId(created.getOrderId())
                .userId(created.getUserId())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

            mongoTemplate.save(readModel);
        }
        // 다른 이벤트들도 처리...
    }

    // 빠른 조회 (읽기 최적화된 모델)
    public List<OrderReadModel> getUserOrders(String userId) {
        return mongoTemplate.find(
            Query.query(Criteria.where("userId").is(userId)),
            OrderReadModel.class
        );
    }
}
```

### 2.5 분산 락 (Distributed Lock)

```java
// Redis 기반 분산 락
@Service
public class DistributedLockService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public boolean tryLock(String lockKey, String requestId, long expireTime) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, requestId, expireTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    public boolean releaseLock(String lockKey, String requestId) {
        // Lua 스크립트로 원자성 보장
        String script =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(lockKey),
            requestId
        );

        return result != null && result == 1L;
    }
}

// 사용 예시: 동시에 한 명만 티켓 구매 가능
@Service
public class TicketService {

    @Autowired
    private DistributedLockService lockService;

    public PurchaseResult purchaseTicket(Long ticketId, Long userId) {
        String lockKey = "ticket:lock:" + ticketId;
        String requestId = UUID.randomUUID().toString();

        // 락 획득 시도
        boolean locked = lockService.tryLock(lockKey, requestId, 10);

        if (!locked) {
            return PurchaseResult.failed("다른 사용자가 구매 중입니다");
        }

        try {
            // 재고 확인
            Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow();

            if (ticket.getStock() <= 0) {
                return PurchaseResult.failed("품절");
            }

            // 재고 차감
            ticket.decreaseStock();
            ticketRepository.save(ticket);

            // 구매 기록
            Purchase purchase = new Purchase(userId, ticketId);
            purchaseRepository.save(purchase);

            return PurchaseResult.success(purchase);

        } finally {
            // 락 해제
            lockService.releaseLock(lockKey, requestId);
        }
    }
}

// Redisson을 사용한 고급 분산 락
@Service
public class RedissonLockService {

    @Autowired
    private RedissonClient redissonClient;

    public <T> T executeWithLock(String lockKey,
                                  Supplier<T> supplier,
                                  long waitTime,
                                  long leaseTime) throws InterruptedException {
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도 (waitTime 동안 대기)
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

            if (!acquired) {
                throw new LockAcquisitionException("Failed to acquire lock");
            }

            return supplier.get();

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

## 3. 시스템 과부하 상황에 대한 대응 방안

### 3.1 Circuit Breaker 패턴

```java
// Resilience4j Circuit Breaker
@Service
public class ExternalApiService {

    @Autowired
    private WebClient webClient;

    // Circuit Breaker 적용
    @CircuitBreaker(name = "externalApi", fallbackMethod = "fallback")
    @Retry(name = "externalApi")
    @Bulkhead(name = "externalApi")  // 동시 호출 제한
    public String callExternalApi(String request) {
        return webClient.post()
            .uri("/api/endpoint")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }

    // Fallback 메서드
    public String fallback(String request, Exception ex) {
        log.error("External API call failed, returning cached data", ex);
        return getCachedData(request);
    }
}

// Circuit Breaker 설정
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)  // 10개 요청 기준
                .failureRateThreshold(50)  // 실패율 50% 이상
                .waitDurationInOpenState(Duration.ofSeconds(30))  // 30초 대기
                .permittedNumberOfCallsInHalfOpenState(5)  // 반개방 시 5개 테스트
                .build())
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))
                .build())
            .build());
    }
}
```

### 3.2 Bulkhead 패턴 (격벽 패턴)

```java
// 스레드 풀 격리
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    // 결제 API용 전용 스레드 풀
    @Bean(name = "paymentExecutor")
    public Executor paymentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("payment-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    // 알림 API용 전용 스레드 풀
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

@Service
public class PaymentService {

    // 결제 스레드 풀 사용
    @Async("paymentExecutor")
    public CompletableFuture<Payment> processPaymentAsync(PaymentRequest request) {
        return CompletableFuture.completedFuture(processPayment(request));
    }
}

@Service
public class NotificationService {

    // 알림 스레드 풀 사용
    @Async("notificationExecutor")
    public CompletableFuture<Void> sendNotificationAsync(Notification notification) {
        sendNotification(notification);
        return CompletableFuture.completedFuture(null);
    }
}
```

### 3.3 Graceful Degradation (우아한 성능 저하)

```java
@Service
public class RecommendationService {

    @Autowired
    private AIRecommendationService aiService;

    @Autowired
    private SimpleRecommendationService simpleService;

    @Autowired
    private MetricsService metricsService;

    public List<Product> getRecommendations(Long userId) {

        // 시스템 부하 확인
        double cpuUsage = metricsService.getCpuUsage();
        int activeRequests = metricsService.getActiveRequests();

        // 부하가 높으면 단순한 알고리즘 사용
        if (cpuUsage > 80 || activeRequests > 1000) {
            log.warn("High load detected, using simple recommendation");
            return simpleService.getPopularProducts();
        }

        // 부하가 중간이면 캐시된 추천 사용
        if (cpuUsage > 60 || activeRequests > 500) {
            return getCachedRecommendations(userId);
        }

        // 정상 상황: AI 기반 개인화 추천
        try {
            return aiService.getPersonalizedRecommendations(userId);
        } catch (Exception e) {
            log.error("AI recommendation failed, fallback to simple", e);
            return simpleService.getPopularProducts();
        }
    }
}
```

### 3.4 백프레셔(Backpressure) 처리

```java
// Reactive Streams를 이용한 백프레셔
@Service
public class DataProcessingService {

    public Flux<ProcessedData> processLargeDataset(Long datasetId) {

        return dataRepository.findByDatasetId(datasetId)
            // 백프레셔 전략: 버퍼링
            .onBackpressureBuffer(
                1000,  // 버퍼 크기
                BufferOverflowStrategy.DROP_OLDEST  // 오래된 것 버림
            )
            // 병렬 처리
            .parallel()
            .runOn(Schedulers.parallel())
            .map(this::processData)
            .sequential()
            // 에러 처리
            .onErrorContinue((error, item) -> {
                log.error("Failed to process item: " + item, error);
            });
    }

    // WebFlux Controller
    @GetMapping(value = "/stream/data", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ProcessedData> streamData(@RequestParam Long datasetId) {
        return processLargeDataset(datasetId)
            // 클라이언트가 느리면 자동으로 속도 조절
            .delayElements(Duration.ofMillis(100));
    }
}
```

### 3.5 Auto Scaling 및 부하 분산

```java
// Spring Boot Actuator Health Indicator
@Component
public class CustomHealthIndicator implements HealthIndicator {

    @Autowired
    private DataSource dataSource;

    @Override
    public Health health() {
        try {
            // DB 연결 확인
            Connection conn = dataSource.getConnection();
            conn.close();

            // CPU, 메모리 확인
            double cpuUsage = getCpuUsage();
            long freeMemory = Runtime.getRuntime().freeMemory();

            if (cpuUsage > 90 || freeMemory < 100_000_000) {
                return Health.down()
                    .withDetail("cpu", cpuUsage)
                    .withDetail("freeMemory", freeMemory)
                    .build();
            }

            return Health.up().build();

        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

```yaml
# Kubernetes HPA (Horizontal Pod Autoscaler)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 3
  maxReplicas: 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70  # CPU 70% 이상 시 스케일 아웃
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60  # 1분 동안 안정화
      policies:
      - type: Percent
        value: 100  # 한 번에 100% 증가 (2배)
        periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300  # 5분 동안 안정화
      policies:
      - type: Percent
        value: 50  # 한 번에 50% 감소
        periodSeconds: 60
```

## 4. 확장성을 고려한 아키텍처 설계

### 4.1 Stateless 설계

```java
// 잘못된 예: Stateful 서버
@RestController
public class ShoppingCartController {

    // 서버 메모리에 장바구니 저장 - 서버 재시작 시 소실
    private Map<Long, ShoppingCart> carts = new ConcurrentHashMap<>();

    @PostMapping("/cart/add")
    public void addToCart(@RequestBody CartItem item, @AuthUser User user) {
        ShoppingCart cart = carts.computeIfAbsent(
            user.getId(),
            k -> new ShoppingCart()
        );
        cart.addItem(item);
    }
}

// 개선: Stateless 서버 (Redis 사용)
@RestController
public class ShoppingCartController {

    @Autowired
    private RedisTemplate<String, ShoppingCart> redisTemplate;

    @PostMapping("/cart/add")
    public void addToCart(@RequestBody CartItem item, @AuthUser User user) {
        String key = "cart:" + user.getId();

        // Redis에서 장바구니 조회
        ShoppingCart cart = redisTemplate.opsForValue().get(key);
        if (cart == null) {
            cart = new ShoppingCart();
        }

        cart.addItem(item);

        // Redis에 저장 (24시간 TTL)
        redisTemplate.opsForValue().set(key, cart, 24, TimeUnit.HOURS);
    }
}
```

### 4.2 데이터베이스 샤딩 (Sharding)

```java
// Sharding Key 기반 라우팅
@Configuration
public class ShardingConfig {

    @Bean
    public DataSource dataSource() {
        Map<Object, Object> dataSourceMap = new HashMap<>();

        // 4개의 샤드
        dataSourceMap.put("shard0", createDataSource("jdbc:mysql://db0:3306/orders"));
        dataSourceMap.put("shard1", createDataSource("jdbc:mysql://db1:3306/orders"));
        dataSourceMap.put("shard2", createDataSource("jdbc:mysql://db2:3306/orders"));
        dataSourceMap.put("shard3", createDataSource("jdbc:mysql://db3:3306/orders"));

        ShardingDataSource shardingDataSource = new ShardingDataSource();
        shardingDataSource.setTargetDataSources(dataSourceMap);

        return shardingDataSource;
    }
}

@Component
public class ShardingKeyResolver {

    private static final int SHARD_COUNT = 4;

    public String resolveShardKey(Long userId) {
        // 사용자 ID 기반 샤딩
        int shardIndex = (int) (userId % SHARD_COUNT);
        return "shard" + shardIndex;
    }
}

// Sharding을 고려한 Repository
@Repository
public class OrderShardingRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShardingKeyResolver shardingKeyResolver;

    public Order findByOrderId(Long orderId, Long userId) {
        // 샤드 결정
        String shardKey = shardingKeyResolver.resolveShardKey(userId);

        // 해당 샤드에서 조회
        DataSource dataSource = getDataSource(shardKey);
        JdbcTemplate shardedJdbcTemplate = new JdbcTemplate(dataSource);

        return shardedJdbcTemplate.queryForObject(
            "SELECT * FROM orders WHERE order_id = ?",
            new Object[]{orderId},
            new OrderRowMapper()
        );
    }

    // 여러 샤드 검색이 필요한 경우
    public List<Order> findAllByStatus(OrderStatus status) {
        // 모든 샤드에서 병렬 조회
        List<CompletableFuture<List<Order>>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            String shardKey = "shard" + i;
            CompletableFuture<List<Order>> future = CompletableFuture.supplyAsync(() -> {
                DataSource dataSource = getDataSource(shardKey);
                JdbcTemplate template = new JdbcTemplate(dataSource);
                return template.query(
                    "SELECT * FROM orders WHERE status = ?",
                    new Object[]{status.name()},
                    new OrderRowMapper()
                );
            });
            futures.add(future);
        }

        // 모든 결과 합치기
        return futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }
}
```

### 4.3 Read/Write 분리 (CQRS)

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource routingDataSource() {
        ReplicationRoutingDataSource routingDataSource =
            new ReplicationRoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();

        // Master (Write)
        DataSource master = createDataSource("jdbc:mysql://master:3306/db");
        dataSourceMap.put("master", master);

        // Slaves (Read)
        dataSourceMap.put("slave1", createDataSource("jdbc:mysql://slave1:3306/db"));
        dataSourceMap.put("slave2", createDataSource("jdbc:mysql://slave2:3306/db"));

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(master);

        return routingDataSource;
    }
}

public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

        if (isReadOnly) {
            // Read Replica 중 랜덤 선택 (로드 밸런싱)
            int replicaCount = 2;
            int replicaIndex = ThreadLocalRandom.current().nextInt(replicaCount);
            return "slave" + (replicaIndex + 1);
        } else {
            // Write는 Master로
            return "master";
        }
    }
}

// 사용 예시
@Service
public class ProductService {

    // Write - Master DB 사용
    @Transactional
    public Product createProduct(ProductDto dto) {
        Product product = new Product(dto);
        return productRepository.save(product);
    }

    // Read - Slave DB 사용
    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow();
    }

    // Read - Slave DB 사용
    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String keyword, Pageable pageable) {
        return productRepository.findByNameContaining(keyword, pageable);
    }
}
```

### 4.4 캐시 계층 구조

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // 캐시별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 상품 정보: 1시간
        cacheConfigurations.put("products",
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeValuesWith(SerializationPair.fromSerializer(
                    new Jackson2JsonRedisSerializer<>(Product.class)
                ))
        );

        // 사용자 세션: 30분
        cacheConfigurations.put("sessions",
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
        );

        // 핫한 상품 (인기 상품): 5분
        cacheConfigurations.put("hot-products",
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}

@Service
public class ProductService {

    // 로컬 캐시 (L1)
    private final LoadingCache<Long, Product> localCache =
        Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build(this::loadProduct);

    @Autowired
    private CacheManager cacheManager;

    // 3단계 캐시 조회
    public Product getProduct(Long productId) {
        // L1: 로컬 캐시 (가장 빠름)
        try {
            return localCache.get(productId);
        } catch (Exception e) {
            log.warn("Local cache miss for product: " + productId);
        }

        // L2: Redis 캐시
        Cache redisCache = cacheManager.getCache("products");
        Product product = redisCache.get(productId, Product.class);

        if (product != null) {
            // 로컬 캐시에도 저장
            localCache.put(productId, product);
            return product;
        }

        // L3: DB 조회
        product = loadProduct(productId);

        // 상위 캐시에 저장
        redisCache.put(productId, product);
        localCache.put(productId, product);

        return product;
    }

    // 캐시 무효화 전략
    @CacheEvict(value = "products", key = "#productId")
    public void updateProduct(Long productId, ProductDto dto) {
        Product product = productRepository.findById(productId)
            .orElseThrow();

        product.update(dto);
        productRepository.save(product);

        // 로컬 캐시도 무효화
        localCache.invalidate(productId);
    }
}
```

### 4.5 CDN과 정적 자원 최적화

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${cdn.base-url}")
    private String cdnBaseUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 정적 리소스는 CDN으로
        registry.addResourceHandler("/static/**")
            .addResourceLocations("classpath:/static/")
            .setCachePeriod(31536000)  // 1년
            .resourceChain(true)
            .addResolver(new VersionResourceResolver()
                .addContentVersionStrategy("/**"));
    }
}

@RestController
public class ImageController {

    @Autowired
    private S3Client s3Client;

    @Value("${cdn.base-url}")
    private String cdnBaseUrl;

    @PostMapping("/images/upload")
    public ImageUploadResponse uploadImage(@RequestParam MultipartFile file) {
        // S3에 업로드
        String fileName = UUID.randomUUID().toString() +
            getFileExtension(file.getOriginalFilename());

        s3Client.putObject(PutObjectRequest.builder()
            .bucket("my-bucket")
            .key("images/" + fileName)
            .contentType(file.getContentType())
            .build(),
            RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        // CDN URL 반환
        String imageUrl = cdnBaseUrl + "/images/" + fileName;

        return new ImageUploadResponse(imageUrl);
    }
}
```

### 4.6 비동기 처리와 이벤트 기반 아키텍처

```java
// 이벤트 발행
@Service
public class OrderService {

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // 주문 생성
        Order order = new Order(request);
        orderRepository.save(order);

        // 이벤트 발행 (비동기)
        OrderCreatedEvent event = new OrderCreatedEvent(order);
        kafkaTemplate.send("order-events", order.getId().toString(), event);

        return order;
    }
}

// 이벤트 소비자들 (각각 독립적으로 처리)
@Service
public class InventoryEventHandler {

    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 재고 차감 처리
        inventoryService.reserveStock(event.getProductId(), event.getQuantity());
    }
}

@Service
public class NotificationEventHandler {

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 주문 확인 이메일 발송
        emailService.sendOrderConfirmation(event.getUserId(), event.getOrderId());
    }
}

@Service
public class AnalyticsEventHandler {

    @KafkaListener(topics = "order-events", groupId = "analytics-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 분석 데이터 수집
        analyticsService.recordOrderEvent(event);
    }
}
```

## 인터뷰에서 자주 나오는 꼬리질문들

### Q1: "대규모 트래픽 환경에서 가장 먼저 병목이 발생하는 곳은?"
**답변 포인트:**
- **데이터베이스**: 가장 흔한 병목 지점
  - Connection Pool 고갈
  - Slow Query로 인한 대기
  - Lock Contention
- **해결 방법**:
  - 캐싱 도입 (Redis, CDN)
  - Read Replica 추가
  - 쿼리 최적화 및 인덱싱
  - Connection Pool 튜닝

### Q2: "캐시 stampede 문제를 어떻게 해결하나?"
**답변 포인트:**
- **문제**: 캐시 만료 시 동시에 여러 요청이 DB로 몰림
- **해결 방법**:
  ```java
  // 1. Double-checked locking
  public Product getProduct(Long id) {
      Product product = cache.get(id);
      if (product == null) {
          synchronized(getLockObject(id)) {
              product = cache.get(id);  // 재확인
              if (product == null) {
                  product = db.find(id);
                  cache.set(id, product);
              }
          }
      }
      return product;
  }

  // 2. Probabilistic early expiration
  public Product getProduct(Long id) {
      CacheEntry entry = cache.get(id);
      if (entry == null) {
          return refreshCache(id);
      }

      // 만료 시간에 가까워지면 확률적으로 갱신
      long timeToExpire = entry.getExpiryTime() - now();
      double probability = -log(random()) * refreshTime;

      if (timeToExpire < probability) {
          // 비동기로 갱신
          CompletableFuture.runAsync(() -> refreshCache(id));
      }

      return entry.getValue();
  }
  ```

### Q3: "분산 시스템에서 시간 동기화 문제는?"
**답변 포인트:**
- **문제**: 서버 간 시간 불일치로 인한 순서 보장 불가
- **해결 방법**:
  - NTP 서버 사용으로 시간 동기화
  - Logical Clock (Lamport Timestamp)
  - Vector Clock
  - 순서가 중요한 경우 단일 시퀀스 생성기 사용
  ```java
  // Snowflake ID 생성 (Twitter)
  // - 타임스탬프 (41bit)
  // - 데이터센터 ID (5bit)
  // - 워커 ID (5bit)
  // - 시퀀스 (12bit)
  public class SnowflakeIdGenerator {
      private long workerId;
      private long datacenterId;
      private long sequence = 0L;
      private long lastTimestamp = -1L;

      public synchronized long nextId() {
          long timestamp = System.currentTimeMillis();

          if (timestamp == lastTimestamp) {
              sequence = (sequence + 1) & 4095;
              if (sequence == 0) {
                  timestamp = waitNextMillis(timestamp);
              }
          } else {
              sequence = 0L;
          }

          lastTimestamp = timestamp;

          return ((timestamp - 1288834974657L) << 22)
              | (datacenterId << 17)
              | (workerId << 12)
              | sequence;
      }
  }
  ```

### Q4: "eventual consistency vs strong consistency, 언제 무엇을 선택?"
**답변 포인트:**
- **Strong Consistency** (강한 일관성)
  - 사용 케이스: 금융 거래, 재고 관리, 예약 시스템
  - 장점: 데이터 정확성 보장
  - 단점: 성능 저하, 가용성 감소
  - 구현: 분산 락, 2PC

- **Eventual Consistency** (최종 일관성)
  - 사용 케이스: SNS 좋아요, 조회수, 추천 시스템
  - 장점: 높은 가용성, 성능
  - 단점: 일시적 불일치
  - 구현: 이벤트 소싱, CQRS

### Q5: "수평 확장 vs 수직 확장, 어떤 것을 선택?"
**답변 포인트:**
- **수직 확장 (Scale-Up)**
  - 장점: 구현 단순, 관리 용이
  - 단점: 한계 존재, 비용 급증, 단일 장애점
  - 적합한 경우: 초기 단계, DB (특히 Write)

- **수평 확장 (Scale-Out)**
  - 장점: 무제한 확장, 고가용성, 비용 효율
  - 단점: 복잡성 증가, 데이터 일관성 문제
  - 적합한 경우: Stateless 서비스, Read 작업

### Q6: "대규모 시스템에서 장애 감지와 복구 전략은?"
**답변 포인트:**
```java
// Health Check
@Component
public class HealthCheckService {

    @Scheduled(fixedRate = 30000)  // 30초마다
    public void checkHealth() {
        // DB 체크
        boolean dbHealthy = checkDatabase();

        // 외부 API 체크
        boolean apiHealthy = checkExternalApis();

        // Redis 체크
        boolean cacheHealthy = checkCache();

        if (!dbHealthy || !apiHealthy || !cacheHealthy) {
            // 알림 발송
            alertService.sendAlert("Service unhealthy");

            // 로드 밸런서에서 제외 요청
            updateHealthStatus(HealthStatus.UNHEALTHY);
        }
    }
}

// Circuit Breaker + Fallback
@Service
public class OrderService {

    @CircuitBreaker(name = "order", fallbackMethod = "createOrderFallback")
    public Order createOrder(OrderRequest request) {
        return orderProcessor.process(request);
    }

    // Fallback: 요청을 큐에 저장
    public Order createOrderFallback(OrderRequest request, Exception ex) {
        // 메시지 큐에 저장 (나중에 처리)
        messageQueue.enqueue(request);

        // 임시 주문 번호 반환
        return Order.pending(generateTempOrderId());
    }
}
```

## 실전 시나리오

### 시나리오 1: 티켓팅 시스템
```java
@Service
public class TicketingService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private TicketRepository ticketRepository;

    // 1단계: Redis로 빠른 차단
    public TicketPurchaseResult purchaseTicket(Long userId, Long eventId) {
        String queueKey = "ticket:queue:" + eventId;
        String stockKey = "ticket:stock:" + eventId;

        // Redis에서 재고 확인 및 차감 (원자적 연산)
        Long stock = redisTemplate.opsForValue().decrement(stockKey);

        if (stock < 0) {
            // 재고 없음
            redisTemplate.opsForValue().increment(stockKey);
            return TicketPurchaseResult.soldOut();
        }

        // 대기열에 추가
        String queueItem = userId + ":" + System.currentTimeMillis();
        redisTemplate.opsForList().rightPush(queueKey, queueItem);

        // 비동기로 실제 구매 처리
        CompletableFuture.runAsync(() ->
            processTicketPurchase(userId, eventId)
        );

        return TicketPurchaseResult.queued();
    }

    // 2단계: 백그라운드에서 실제 구매 처리
    @Async
    public void processTicketPurchase(Long userId, Long eventId) {
        String lockKey = "ticket:lock:" + eventId + ":" + userId;

        try {
            boolean locked = redisLock.tryLock(lockKey, 10, TimeUnit.SECONDS);
            if (!locked) return;

            // DB에서 실제 티켓 생성
            Ticket ticket = ticketRepository.createTicket(userId, eventId);

            // 결제 처리
            paymentService.processPayment(userId, ticket);

            // 성공 알림
            notificationService.notifySuccess(userId, ticket);

        } catch (Exception e) {
            // 실패 시 Redis 재고 복구
            redisTemplate.opsForValue().increment("ticket:stock:" + eventId);

            // 실패 알림
            notificationService.notifyFailure(userId, e.getMessage());

        } finally {
            redisLock.unlock(lockKey);
        }
    }
}
```

### 시나리오 2: 실시간 순위 시스템
```java
@Service
public class RankingService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 점수 업데이트
    public void updateScore(Long userId, int score) {
        String key = "ranking:global";
        redisTemplate.opsForZSet().add(key, userId.toString(), score);
    }

    // 실시간 랭킹 조회 (O(log(N)))
    public List<RankingDto> getTopRanking(int count) {
        String key = "ranking:global";

        Set<ZSetOperations.TypedTuple<String>> topUsers =
            redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, count - 1);

        return topUsers.stream()
            .map(tuple -> new RankingDto(
                Long.parseLong(tuple.getValue()),
                tuple.getScore().intValue()
            ))
            .collect(Collectors.toList());
    }

    // 내 순위 조회 (O(log(N)))
    public RankingDto getMyRanking(Long userId) {
        String key = "ranking:global";

        Long rank = redisTemplate.opsForZSet()
            .reverseRank(key, userId.toString());

        Double score = redisTemplate.opsForZSet()
            .score(key, userId.toString());

        return new RankingDto(userId, rank + 1, score.intValue());
    }
}
```

## 핵심 암기 포인트

1. **3계층 캐싱**: Local Cache → Redis → Database
2. **비동기 처리**: 긴 작업은 큐로 분리하여 응답 속도 확보
3. **Circuit Breaker**: 장애 전파 방지, 빠른 실패
4. **Stateless 설계**: 수평 확장의 필수 조건
5. **DB 병목 해결**: Connection Pool, Read Replica, Sharding
6. **Rate Limiting**: 시스템 보호, 공정한 자원 분배
7. **Eventual Consistency**: 가용성과 일관성의 트레이드오프
8. **Monitoring**: 메트릭 수집 및 알림 체계

이러한 패턴들은 실제 대규모 서비스 경험과 함께 설명할 때 더욱 설득력이 있으며, 각 패턴의 장단점과 적용 시점을 명확히 이해하는 것이 중요합니다.
