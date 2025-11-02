# Spring Event System

## 목차
1. [개요](#개요)
2. [핵심 구성 요소](#핵심-구성-요소)
3. [기본 사용법](#기본-사용법)
4. [고급 기능](#고급-기능)
5. [실무 활용 패턴](#실무-활용-패턴)
6. [주의사항 및 Best Practices](#주의사항-및-best-practices)
7. [면접 질문](#면접-질문)

---

## 개요

### Spring Event란?
Spring Event는 **옵저버 패턴(Observer Pattern)의 구현체**로, 애플리케이션 컴포넌트 간 느슨한 결합(Loose Coupling)을 제공하는 메커니즘입니다.

### 왜 사용하는가?

#### ❌ Event 없이 직접 호출
```java
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final EmailService emailService;
    private final InventoryService inventoryService;
    private final PointService pointService;
    private final LogService logService;
    private final NotificationService notificationService;
    // ... 의존성이 계속 늘어남

    @Transactional
    public Order createOrder(Order order) {
        Order saved = orderRepository.save(order);

        // 모든 부가 기능을 직접 호출
        emailService.sendOrderConfirmation(saved);
        inventoryService.decreaseStock(saved.getItems());
        pointService.earnPoints(saved.getCustomerId(), saved.getTotalAmount());
        logService.log(saved);
        notificationService.notify(saved);

        return saved;
    }
}
```

**문제점:**
- OrderService가 너무 많은 책임을 가짐 (SRP 위반)
- 높은 결합도 - 새 기능 추가 시마다 OrderService 수정 필요 (OCP 위반)
- 테스트 복잡도 증가 - 모든 의존성을 mock 해야 함
- 핵심 비즈니스 로직(주문 생성)과 부가 기능(이메일, 로그 등)이 섞임

#### ✅ Event 사용
```java
@Service
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;
    private final OrderRepository orderRepository;

    public OrderService(ApplicationEventPublisher eventPublisher,
                       OrderRepository orderRepository) {
        this.eventPublisher = eventPublisher;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createOrder(Order order) {
        // 1. 핵심 비즈니스 로직만 집중
        Order saved = orderRepository.save(order);

        // 2. 이벤트 발행 - 누가 처리하는지 몰라도 됨
        eventPublisher.publishEvent(new OrderCreatedEvent(saved));

        return saved;
    }
}
```

**장점:**
- **낮은 결합도**: OrderService는 이벤트 리스너들을 알 필요 없음
- **단일 책임**: 주문 생성에만 집중
- **확장성**: 새 기능 추가 시 리스너만 추가, OrderService 수정 불필요
- **테스트 용이성**: eventPublisher만 mock하면 됨

---

## 핵심 구성 요소

### 1. ApplicationEventPublisher
```java
public interface ApplicationEventPublisher {
    void publishEvent(Object event);
}
```

- Spring이 제공하는 이벤트 발행 인터페이스
- `ApplicationContext`가 이를 구현하므로 어디서든 주입 가능
- 이벤트를 발행하면 Spring이 자동으로 등록된 리스너들에게 전파

### 2. Event 클래스
```java
// 방법 1: POJO 클래스 (Spring 4.2+)
public class OrderCreatedEvent {
    private final Order order;
    private final LocalDateTime occurredAt;

    public OrderCreatedEvent(Order order) {
        this.order = order;
        this.occurredAt = LocalDateTime.now();
    }

    public Order getOrder() { return order; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}

// 방법 2: ApplicationEvent 상속 (레거시)
public class OrderCreatedEvent extends ApplicationEvent {
    private final Order order;

    public OrderCreatedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }

    public Order getOrder() { return order; }
}
```

**권장**: POJO 클래스 사용 (더 간단하고 유연함)

### 3. Event Listener
```java
@Component
public class EmailNotificationListener {
    private final EmailService emailService;

    public EmailNotificationListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        Order order = event.getOrder();
        emailService.sendOrderConfirmation(order.getCustomerEmail(), order);
    }
}
```

---

## 기본 사용법

### 완전한 예제

```java
// 1. 이벤트 클래스
public class OrderCreatedEvent {
    private final Long orderId;
    private final String customerId;
    private final BigDecimal totalAmount;
    private final List<OrderItem> items;
    private final LocalDateTime createdAt;

    public OrderCreatedEvent(Order order) {
        this.orderId = order.getId();
        this.customerId = order.getCustomerId();
        this.totalAmount = order.getTotalAmount();
        this.items = new ArrayList<>(order.getItems());
        this.createdAt = LocalDateTime.now();
    }

    // getters...
}

// 2. 이벤트 발행자
@Service
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;
    private final OrderRepository orderRepository;

    public OrderService(ApplicationEventPublisher eventPublisher,
                       OrderRepository orderRepository) {
        this.eventPublisher = eventPublisher;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // 주문 생성
        Order order = Order.create(request);
        Order savedOrder = orderRepository.save(order);

        // 이벤트 발행
        eventPublisher.publishEvent(new OrderCreatedEvent(savedOrder));

        return savedOrder;
    }
}

// 3-1. 이메일 발송 리스너
@Component
@Slf4j
public class EmailNotificationListener {
    private final EmailService emailService;

    public EmailNotificationListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Sending order confirmation email for order: {}", event.getOrderId());
        emailService.sendOrderConfirmation(event);
    }
}

// 3-2. 재고 차감 리스너
@Component
@Slf4j
public class InventoryListener {
    private final InventoryService inventoryService;

    public InventoryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Decreasing inventory for order: {}", event.getOrderId());
        inventoryService.decreaseStock(event.getItems());
    }
}

// 3-3. 포인트 적립 리스너
@Component
@Slf4j
public class PointAccumulationListener {
    private final PointService pointService;

    public PointAccumulationListener(PointService pointService) {
        this.pointService = pointService;
    }

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Accumulating points for customer: {}", event.getCustomerId());
        int points = calculatePoints(event.getTotalAmount());
        pointService.earnPoints(event.getCustomerId(), points);
    }

    private int calculatePoints(BigDecimal amount) {
        return amount.divide(BigDecimal.valueOf(100)).intValue();
    }
}

// 3-4. 통계 업데이트 리스너
@Component
public class StatisticsListener {
    private final StatisticsService statisticsService;

    public StatisticsListener(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        statisticsService.incrementOrderCount();
        statisticsService.addRevenue(event.getTotalAmount());
    }
}
```

---

## 고급 기능

### 1. 비동기 이벤트 처리 (@Async)

```java
// AsyncConfig 설정
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "eventTaskExecutor")
    public Executor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

// 비동기 리스너
@Component
@Slf4j
public class EmailNotificationListener {
    private final EmailService emailService;

    public EmailNotificationListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async("eventTaskExecutor")
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Sending email asynchronously on thread: {}",
                 Thread.currentThread().getName());
        // 이메일 발송은 시간이 걸리므로 비동기로 처리
        emailService.sendOrderConfirmation(event);
    }
}
```

**언제 사용?**
- 시간이 오래 걸리는 작업 (이메일 발송, 외부 API 호출)
- 메인 트랜잭션과 무관한 작업
- 실패해도 메인 로직에 영향 없는 작업

**주의사항:**
- 예외 처리 필수 (AsyncUncaughtExceptionHandler 설정)
- 트랜잭션 컨텍스트가 별도로 관리됨
- 반환값을 받으려면 CompletableFuture 사용

### 2. 트랜잭션 기반 이벤트 (@TransactionalEventListener)

```java
@Component
@Slf4j
public class InventoryListener {
    private final InventoryService inventoryService;

    public InventoryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // 트랜잭션 커밋 후에만 실행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreatedAfterCommit(OrderCreatedEvent event) {
        log.info("Order committed successfully, decreasing inventory");
        inventoryService.decreaseStock(event.getItems());
    }

    // 트랜잭션 롤백 후 실행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleOrderRollback(OrderCreatedEvent event) {
        log.warn("Order creation rolled back for order: {}", event.getOrderId());
        // 보상 트랜잭션 또는 알림 발송
    }

    // 완료 후 실행 (커밋/롤백 무관)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void handleOrderCompletion(OrderCreatedEvent event) {
        log.info("Order processing completed");
    }

    // 커밋 전 실행
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBeforeCommit(OrderCreatedEvent event) {
        log.info("About to commit order");
        // 커밋 전 추가 검증
    }
}
```

**TransactionPhase 옵션:**

| Phase | 실행 시점 | 사용 사례 |
|-------|---------|----------|
| `AFTER_COMMIT` | 트랜잭션 커밋 성공 후 (기본값) | 정상 처리 후 부가 작업 |
| `AFTER_ROLLBACK` | 트랜잭션 롤백 후 | 실패 알림, 보상 트랜잭션 |
| `AFTER_COMPLETION` | 커밋/롤백 후 (성공/실패 무관) | 리소스 정리, 로깅 |
| `BEFORE_COMMIT` | 커밋 전 | 추가 검증, 동일 트랜잭션 내 작업 |

**fallbackExecution 옵션:**
```java
// 트랜잭션이 없어도 실행
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT,
    fallbackExecution = true
)
public void handleEvent(OrderCreatedEvent event) {
    // 트랜잭션 없는 경우에도 즉시 실행
}
```

### 3. 조건부 리스닝

```java
@Component
public class PremiumCustomerListener {

    // SpEL(Spring Expression Language) 사용
    @EventListener(condition = "#event.totalAmount.compareTo(T(java.math.BigDecimal).valueOf(100000)) > 0")
    public void handleLargeOrder(OrderCreatedEvent event) {
        // 10만원 이상 주문에만 반응
        log.info("Large order detected: {}", event.getOrderId());
        sendVIPNotification(event);
    }

    @EventListener(condition = "#event.customerId.startsWith('VIP')")
    public void handleVIPCustomerOrder(OrderCreatedEvent event) {
        // VIP 고객 주문에만 반응
        applyVIPBenefit(event);
    }

    @EventListener(condition = "#event.items.size() > 5")
    public void handleBulkOrder(OrderCreatedEvent event) {
        // 5개 이상 상품 주문 시
        applyBulkDiscount(event);
    }
}
```

**SpEL 활용 예:**
- `#event.property`: 이벤트 객체의 속성 접근
- `#root.event`: 이벤트 객체 자체
- `T(클래스)`: 정적 메서드/필드 접근
- 비교 연산자: `>`, `<`, `==`, `!=`
- 논리 연산자: `and`, `or`, `not`

### 4. 실행 순서 지정 (@Order)

```java
@Component
@Slf4j
public class ValidationListener {

    @Order(1)  // 가장 먼저 실행
    @EventListener
    public void validateOrder(OrderCreatedEvent event) {
        log.info("Step 1: Validating order");
        // 검증 로직
    }
}

@Component
@Slf4j
public class InventoryListener {

    @Order(2)  // 두 번째 실행
    @EventListener
    public void decreaseInventory(OrderCreatedEvent event) {
        log.info("Step 2: Decreasing inventory");
        // 재고 차감
    }
}

@Component
@Slf4j
public class NotificationListener {

    @Order(3)  // 세 번째 실행
    @EventListener
    public void sendNotification(OrderCreatedEvent event) {
        log.info("Step 3: Sending notification");
        // 알림 발송
    }
}
```

**주의:**
- `@Order`는 동기 실행에만 유효
- `@Async`와 함께 사용하면 순서 보장 안 됨
- 숫자가 낮을수록 우선순위 높음

### 5. 제네릭 이벤트 리스너

```java
// 제네릭 이벤트
public class EntityCreatedEvent<T> {
    private final T entity;
    private final LocalDateTime createdAt;

    public EntityCreatedEvent(T entity) {
        this.entity = entity;
        this.createdAt = LocalDateTime.now();
    }

    public T getEntity() { return entity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

// 타입별 리스너
@Component
public class OrderEventListener {

    @EventListener
    public void handleOrderCreated(EntityCreatedEvent<Order> event) {
        Order order = event.getEntity();
        log.info("Order created: {}", order.getId());
    }
}

@Component
public class ProductEventListener {

    @EventListener
    public void handleProductCreated(EntityCreatedEvent<Product> event) {
        Product product = event.getEntity();
        log.info("Product created: {}", product.getId());
    }
}
```

### 6. 이벤트 리스너 반환값 활용

```java
@Component
public class EventChainListener {

    // 이벤트를 받아서 다른 이벤트를 반환
    @EventListener
    public OrderShippedEvent handleOrderPaid(OrderPaidEvent event) {
        log.info("Order paid, preparing shipment");
        // OrderPaidEvent 처리 후 OrderShippedEvent 발행
        return new OrderShippedEvent(event.getOrderId());
    }

    @EventListener
    public OrderCompletedEvent handleOrderShipped(OrderShippedEvent event) {
        log.info("Order shipped, marking as completed");
        // OrderShippedEvent 처리 후 OrderCompletedEvent 발행
        return new OrderCompletedEvent(event.getOrderId());
    }
}
```

**이벤트 체이닝:**
- 리스너가 이벤트를 반환하면 Spring이 자동으로 재발행
- 여러 이벤트를 반환하려면 `Collection<?>` 반환

---

## 실무 활용 패턴

### 1. 도메인 이벤트 (Domain Event)

```java
// 도메인 엔티티
@Entity
@Getter
public class Order extends AbstractAggregateRoot<Order> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerId;
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL)
    private List<OrderItem> items;

    // 도메인 로직
    public void complete() {
        if (this.status != OrderStatus.PAID) {
            throw new IllegalStateException("Cannot complete unpaid order");
        }

        this.status = OrderStatus.COMPLETED;

        // 도메인 이벤트 등록
        registerEvent(new OrderCompletedEvent(this));
    }

    public void cancel() {
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed order");
        }

        this.status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelledEvent(this));
    }
}

// 서비스에서 사용
@Service
public class OrderService {
    private final OrderRepository orderRepository;

    @Transactional
    public void completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.complete();  // 도메인 이벤트 등록

        orderRepository.save(order);  // 저장 시 이벤트 자동 발행
    }
}
```

**장점:**
- 도메인 로직과 이벤트가 함께 관리됨
- 비즈니스 규칙이 엔티티에 캡슐화됨
- DDD(Domain-Driven Design) 패턴과 일치

### 2. 이벤트 소싱 (Event Sourcing) 패턴

```java
// 이벤트 저장소
@Entity
@Table(name = "event_store")
public class StoredEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateId;
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime occurredAt;

    // getters/setters...
}

@Repository
public interface EventStoreRepository extends JpaRepository<StoredEvent, Long> {
    List<StoredEvent> findByAggregateIdOrderByOccurredAt(String aggregateId);
}

// 이벤트 저장 리스너
@Component
@Slf4j
public class EventStoreListener {
    private final EventStoreRepository eventStoreRepository;
    private final ObjectMapper objectMapper;

    public EventStoreListener(EventStoreRepository eventStoreRepository,
                             ObjectMapper objectMapper) {
        this.eventStoreRepository = eventStoreRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeOrderEvent(OrderCreatedEvent event) {
        try {
            StoredEvent storedEvent = new StoredEvent();
            storedEvent.setAggregateId(event.getOrderId().toString());
            storedEvent.setEventType(event.getClass().getSimpleName());
            storedEvent.setPayload(objectMapper.writeValueAsString(event));
            storedEvent.setOccurredAt(event.getOccurredAt());

            eventStoreRepository.save(storedEvent);
            log.info("Stored event: {}", event.getClass().getSimpleName());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event", e);
        }
    }
}
```

### 3. 아웃박스 패턴 (Transactional Outbox Pattern)

```java
// 아웃박스 테이블
@Entity
@Table(name = "outbox")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateId;
    private String eventType;
    private String payload;
    private LocalDateTime createdAt;
    private boolean published;

    // getters/setters...
}

// 이벤트를 아웃박스에 저장
@Component
@Slf4j
public class OutboxEventListener {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void saveToOutbox(OrderCreatedEvent event) {
        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateId(event.getOrderId().toString());
            outboxEvent.setEventType(event.getClass().getSimpleName());
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setCreatedAt(LocalDateTime.now());
            outboxEvent.setPublished(false);

            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to save to outbox", e);
            throw new RuntimeException("Outbox save failed", e);
        }
    }
}

// 스케줄러로 아웃박스 이벤트를 외부 시스템에 발행
@Component
@Slf4j
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)  // 5초마다 실행
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> unpublished = outboxRepository
            .findByPublishedFalseOrderByCreatedAt(PageRequest.of(0, 100));

        for (OutboxEvent event : unpublished) {
            try {
                kafkaTemplate.send("order-events", event.getPayload());
                event.setPublished(true);
                outboxRepository.save(event);
                log.info("Published event: {}", event.getId());
            } catch (Exception e) {
                log.error("Failed to publish event: {}", event.getId(), e);
            }
        }
    }
}
```

**아웃박스 패턴의 장점:**
- 데이터베이스 트랜잭션과 메시지 발행을 원자적으로 처리
- 메시지 브로커 장애 시에도 이벤트 유실 방지
- 최소 한 번(At-least-once) 전달 보장

### 4. 사가 패턴 (Saga Pattern) - 오케스트레이션

```java
// 주문 사가 이벤트들
public class OrderSagaStartedEvent {
    private final Long orderId;
    private final String customerId;
    private final BigDecimal amount;
    // ...
}

public class PaymentCompletedEvent {
    private final Long orderId;
    private final String paymentId;
    // ...
}

public class InventoryReservedEvent {
    private final Long orderId;
    private final List<String> reservedItems;
    // ...
}

public class ShippingScheduledEvent {
    private final Long orderId;
    private final String trackingNumber;
    // ...
}

// 사가 오케스트레이터
@Component
@Slf4j
public class OrderSagaOrchestrator {
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final ShippingService shippingService;

    // Step 1: 주문 생성 → 결제 시작
    @EventListener
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Saga Step 1: Starting payment for order {}", event.getOrderId());
        try {
            String paymentId = paymentService.processPayment(
                event.getCustomerId(),
                event.getTotalAmount()
            );
            eventPublisher.publishEvent(new PaymentCompletedEvent(event.getOrderId(), paymentId));
        } catch (Exception e) {
            log.error("Payment failed, cancelling order", e);
            eventPublisher.publishEvent(new OrderCancelledEvent(event.getOrderId()));
        }
    }

    // Step 2: 결제 완료 → 재고 예약
    @EventListener
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Saga Step 2: Reserving inventory for order {}", event.getOrderId());
        try {
            List<String> reserved = inventoryService.reserveItems(event.getOrderId());
            eventPublisher.publishEvent(new InventoryReservedEvent(event.getOrderId(), reserved));
        } catch (Exception e) {
            log.error("Inventory reservation failed, refunding payment", e);
            paymentService.refund(event.getPaymentId());
            eventPublisher.publishEvent(new OrderCancelledEvent(event.getOrderId()));
        }
    }

    // Step 3: 재고 예약 → 배송 예약
    @EventListener
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Saga Step 3: Scheduling shipping for order {}", event.getOrderId());
        try {
            String trackingNumber = shippingService.scheduleShipping(event.getOrderId());
            eventPublisher.publishEvent(new ShippingScheduledEvent(event.getOrderId(), trackingNumber));
        } catch (Exception e) {
            log.error("Shipping failed, rolling back inventory", e);
            inventoryService.releaseItems(event.getReservedItems());
            eventPublisher.publishEvent(new OrderCancelledEvent(event.getOrderId()));
        }
    }

    // Step 4: 배송 예약 → 주문 완료
    @EventListener
    @Transactional
    public void handleShippingScheduled(ShippingScheduledEvent event) {
        log.info("Saga completed successfully for order {}", event.getOrderId());
        eventPublisher.publishEvent(new OrderCompletedEvent(event.getOrderId()));
    }
}
```

### 5. CQRS (Command Query Responsibility Segregation)

```java
// Command Side - 쓰기 모델
@Service
public class OrderCommandService {
    private final ApplicationEventPublisher eventPublisher;
    private final OrderRepository orderRepository;

    @Transactional
    public Long createOrder(CreateOrderCommand command) {
        Order order = Order.create(command);
        Order saved = orderRepository.save(order);

        // 이벤트 발행
        eventPublisher.publishEvent(new OrderCreatedEvent(saved));

        return saved.getId();
    }
}

// Query Side - 읽기 모델 업데이트
@Component
@Slf4j
public class OrderQueryModelUpdater {
    private final OrderQueryRepository queryRepository;

    @EventListener
    @Transactional
    public void updateQueryModel(OrderCreatedEvent event) {
        // 읽기 최적화된 모델 업데이트
        OrderQueryModel queryModel = new OrderQueryModel();
        queryModel.setOrderId(event.getOrderId());
        queryModel.setCustomerId(event.getCustomerId());
        queryModel.setTotalAmount(event.getTotalAmount());
        queryModel.setStatus("CREATED");
        queryModel.setCreatedAt(event.getCreatedAt());

        queryRepository.save(queryModel);
        log.info("Updated query model for order: {}", event.getOrderId());
    }
}

// Query Service - 읽기 전용
@Service
public class OrderQueryService {
    private final OrderQueryRepository queryRepository;

    public List<OrderQueryModel> findOrdersByCustomer(String customerId) {
        return queryRepository.findByCustomerId(customerId);
    }
}
```

---

## 주의사항 및 Best Practices

### 1. 트랜잭션 경계 문제

#### ❌ 문제 상황
```java
@Service
public class OrderService {
    @Transactional
    public Order createOrder(Order order) {
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCreatedEvent(saved));
        return saved;
        // 여기서 트랜잭션 커밋
    }
}

@Component
public class InventoryListener {
    @EventListener  // 동기 실행!
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 아직 OrderService의 트랜잭션 내부!
        // 여기서 예외 발생 시 주문도 롤백됨
        inventoryService.decreaseStock(event.getItems());
    }
}
```

#### ✅ 해결 방법 1: @TransactionalEventListener
```java
@Component
public class InventoryListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 주문 트랜잭션 커밋 후 실행
        // 예외 발생해도 주문은 이미 저장됨
        inventoryService.decreaseStock(event.getItems());
    }
}
```

#### ✅ 해결 방법 2: @Async
```java
@Component
public class InventoryListener {
    @Async
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 별도 스레드에서 실행
        inventoryService.decreaseStock(event.getItems());
    }
}
```

#### ✅ 해결 방법 3: 새 트랜잭션
```java
@Component
public class InventoryListener {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 새로운 트랜잭션에서 실행
        inventoryService.decreaseStock(event.getItems());
    }
}
```

### 2. 예외 처리

#### ❌ 예외를 그대로 전파
```java
@Component
public class EmailListener {
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 이메일 발송 실패 시 주문 생성도 실패!
        emailService.sendEmail(event);
    }
}
```

#### ✅ 적절한 예외 처리
```java
@Component
@Slf4j
public class EmailListener {
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            emailService.sendEmail(event);
        } catch (Exception e) {
            // 이메일 실패가 주문 생성을 막으면 안 됨
            log.error("Failed to send email for order: {}", event.getOrderId(), e);
            // 재시도 큐에 추가하거나 알림 발송
        }
    }
}
```

#### ✅ 비동기 예외 핸들러
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Async exception in method: {}", method.getName(), throwable);
            // 예외 처리 로직 (알림, 로깅 등)
        };
    }
}
```

### 3. 순환 참조 방지

#### ❌ 순환 이벤트 발생
```java
@Component
public class OrderListener {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // ...
        eventPublisher.publishEvent(new OrderUpdatedEvent(event.getOrderId()));
    }
}

@Component
public class OrderUpdatedListener {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @EventListener
    public void handleOrderUpdated(OrderUpdatedEvent event) {
        // ...
        // 다시 OrderCreatedEvent 발행 → 무한 루프!
        eventPublisher.publishEvent(new OrderCreatedEvent(...));
    }
}
```

#### ✅ 이벤트 체인 제한
```java
@Component
public class OrderListener {
    private final Set<Long> processedOrders = ConcurrentHashMap.newKeySet();

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (processedOrders.contains(event.getOrderId())) {
            log.warn("Already processed order: {}", event.getOrderId());
            return;
        }

        processedOrders.add(event.getOrderId());

        try {
            // 처리 로직
        } finally {
            // 일정 시간 후 캐시에서 제거
            scheduler.schedule(() -> processedOrders.remove(event.getOrderId()),
                             Duration.ofMinutes(5));
        }
    }
}
```

### 4. 이벤트 데이터 불변성

#### ❌ 가변 객체 전달
```java
public class OrderCreatedEvent {
    private Order order;  // 가변 객체!

    public OrderCreatedEvent(Order order) {
        this.order = order;  // 참조 공유
    }

    public Order getOrder() { return order; }
}

// 리스너에서 수정 시 다른 리스너에도 영향
@EventListener
public void handle(OrderCreatedEvent event) {
    Order order = event.getOrder();
    order.setStatus(OrderStatus.PROCESSING);  // 위험!
}
```

#### ✅ 불변 데이터 전달
```java
public class OrderCreatedEvent {
    private final Long orderId;
    private final String customerId;
    private final BigDecimal totalAmount;
    private final List<OrderItem> items;  // 복사본

    public OrderCreatedEvent(Order order) {
        this.orderId = order.getId();
        this.customerId = order.getCustomerId();
        this.totalAmount = order.getTotalAmount();
        // 방어적 복사
        this.items = new ArrayList<>(order.getItems());
    }

    // getters only (no setters)
}
```

### 5. 성능 고려사항

#### 리스너가 많을 때
```java
// 동기 리스너가 10개라면?
@Service
public class OrderService {
    @Transactional
    public Order createOrder(Order order) {
        Order saved = orderRepository.save(order);

        // 10개 리스너가 순차 실행 → 느림!
        eventPublisher.publishEvent(new OrderCreatedEvent(saved));

        return saved;
    }
}
```

#### ✅ 해결책: 비동기 + @TransactionalEventListener
```java
@Component
public class FastListener {
    // 빠르게 처리해야 하는 것만 동기
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCritical(OrderCreatedEvent event) {
        // 중요한 로직
    }
}

@Component
public class SlowListener {
    // 시간 걸리는 작업은 비동기
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNonCritical(OrderCreatedEvent event) {
        // 이메일, 통계 등
    }
}
```

### 6. 테스트 작성

```java
@SpringBootTest
class OrderServiceTest {
    @Autowired
    private OrderService orderService;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @Test
    void createOrder_shouldPublishEvent() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(...);

        // when
        Order order = orderService.createOrder(request);

        // then
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    }
}

@SpringBootTest
class EmailListenerTest {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockBean
    private EmailService emailService;

    @Test
    void handleOrderCreated_shouldSendEmail() {
        // given
        OrderCreatedEvent event = new OrderCreatedEvent(...);

        // when
        eventPublisher.publishEvent(event);

        // then
        verify(emailService).sendOrderConfirmation(any());
    }
}
```

### 7. Best Practices 요약

| 항목 | 권장 사항 |
|------|----------|
| **이벤트 클래스** | POJO 사용, 불변 객체로 설계 |
| **이벤트 데이터** | 필요한 데이터만 포함 (Entity 전체 X) |
| **리스너 트랜잭션** | `@TransactionalEventListener` 사용 |
| **시간 걸리는 작업** | `@Async`로 비동기 처리 |
| **예외 처리** | 반드시 try-catch, 실패가 메인 로직 영향 X |
| **순환 참조** | 이벤트 체인 깊이 제한, 중복 처리 방지 |
| **테스트** | 이벤트 발행/처리 각각 테스트 |
| **네이밍** | `{Entity}{Action}Event` (ex: OrderCreatedEvent) |

---

## 면접 질문

### Q1: Spring Event와 직접 메서드 호출의 차이는?

**답변:**

**직접 호출 방식:**
```java
orderService.createOrder(order);
emailService.send();
inventoryService.decrease();
```
- 높은 결합도
- 새 기능 추가 시 기존 코드 수정 (OCP 위반)
- 테스트 시 모든 의존성 mock 필요

**이벤트 방식:**
```java
eventPublisher.publishEvent(new OrderCreatedEvent(order));
// 리스너들이 각자 처리
```
- 낮은 결합도 (Publisher는 Listener를 몰라도 됨)
- 확장성 (새 리스너 추가만으로 기능 확장)
- 비동기, 트랜잭션 분리 등 유연한 제어

**사용 기준:**
- **직접 호출**: 핵심 비즈니스 로직, 트랜잭션 일관성 필수, 순서 보장 필요
- **이벤트**: 부가 기능, 여러 시스템 간 느슨한 결합, 비동기 처리 가능

---

### Q2: @EventListener vs @TransactionalEventListener 차이는?

**답변:**

**@EventListener:**
```java
@EventListener
public void handle(OrderCreatedEvent event) {
    // 발행 즉시 실행 (동기)
    // 발행자의 트랜잭션 내에서 실행
}
```

**@TransactionalEventListener:**
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handle(OrderCreatedEvent event) {
    // 트랜잭션 커밋 후 실행
    // 롤백 시 실행 안 됨
}
```

**주요 차이:**
| 항목 | @EventListener | @TransactionalEventListener |
|------|---------------|---------------------------|
| 실행 시점 | 즉시 | 트랜잭션 단계별 |
| 트랜잭션 컨텍스트 | 발행자와 동일 | 분리 가능 |
| 롤백 시 | 실행됨 (이미 실행) | AFTER_COMMIT은 실행 안 됨 |
| 사용 사례 | 일반 이벤트 | DB 작업 후 부가 작업 |

---

### Q3: 비동기 이벤트 처리 시 주의할 점은?

**답변:**

**1. 트랜잭션 분리:**
```java
@Async
@EventListener
public void handle(OrderCreatedEvent event) {
    // 별도 스레드 → 별도 트랜잭션
    // 발행자 트랜잭션 컨텍스트 없음
}
```

**2. 예외 처리:**
```java
@Override
public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) -> {
        log.error("Async exception", ex);
    };
}
```

**3. 스레드 풀 고갈:**
```java
@Bean
public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(25);
    // RejectedExecutionHandler 설정
    executor.setRejectedExecutionHandler(new CallerRunsPolicy());
    return executor;
}
```

**4. 순서 보장 불가:**
- 비동기는 실행 순서 보장 안 됨
- 순서가 중요하면 동기 처리 또는 순차 처리 메커니즘 필요

---

### Q4: Event 사용 시 단점은?

**답변:**

**1. 디버깅 어려움:**
- 코드 흐름 추적 어려움 (누가 어디서 처리하는지 불명확)
- IDE의 "Find Usages"로 찾기 어려움

**2. 성능 오버헤드:**
- 리스너가 많으면 처리 시간 증가
- 이벤트 발행/전달 비용

**3. 트랜잭션 관리 복잡:**
- 트랜잭션 경계 이해 필요
- 잘못 사용 시 데이터 불일치

**4. 테스트 복잡도:**
- 통합 테스트 필요
- 이벤트 발행 확인 어려움

**5. 과도한 사용:**
- 이벤트 체이닝 → 복잡도 증가
- 간단한 로직도 이벤트화 → 오버 엔지니어링

---

### Q5: 언제 Event를 사용하고 언제 직접 호출하나?

**답변:**

**Event 사용 시기:**
```java
// ✅ 좋은 예: 부가 기능 분리
@Transactional
public Order createOrder(Order order) {
    Order saved = orderRepository.save(order);
    eventPublisher.publishEvent(new OrderCreatedEvent(saved));
    return saved;
}
// 이메일, 통계, 로그 등은 리스너에서
```

**직접 호출 시기:**
```java
// ✅ 좋은 예: 핵심 로직
@Transactional
public Order createOrder(Order order) {
    validateOrder(order);  // 검증은 직접 호출
    Order saved = orderRepository.save(order);
    return saved;
}
```

**판단 기준:**

| 기준 | Event 사용 | 직접 호출 |
|------|----------|----------|
| **책임** | 부가 기능 | 핵심 로직 |
| **결합도** | 낮춰야 함 | 강해도 됨 |
| **실패 시** | 메인 로직 영향 X | 메인 로직 실패 |
| **트랜잭션** | 분리 가능 | 동일 트랜잭션 |
| **확장성** | 자주 변경/추가 | 안정적 |

---

### Q6: 이벤트 유실을 방지하는 방법은?

**답변:**

**1. Transactional Outbox Pattern:**
```java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
public void saveToOutbox(OrderCreatedEvent event) {
    outboxRepository.save(new OutboxEvent(event));
}

@Scheduled(fixedDelay = 5000)
public void publishOutboxEvents() {
    List<OutboxEvent> events = outboxRepository.findUnpublished();
    events.forEach(event -> {
        kafkaTemplate.send(event);
        event.markPublished();
    });
}
```

**2. Event Sourcing:**
```java
@EventListener
public void storeEvent(OrderCreatedEvent event) {
    eventStore.save(event);  // 모든 이벤트 저장
}
```

**3. 재시도 메커니즘:**
```java
@EventListener
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void handleWithRetry(OrderCreatedEvent event) {
    // 실패 시 재시도
}
```

**4. Dead Letter Queue:**
```java
@EventListener
public void handle(OrderCreatedEvent event) {
    try {
        process(event);
    } catch (Exception e) {
        deadLetterQueue.send(event);  // 실패한 이벤트 보관
    }
}
```

---

### Q7: 실무에서 Event를 어떻게 활용했나?

**답변 예시:**

"주문 시스템에서 주문 생성 시 이메일 발송, 재고 차감, 포인트 적립, 통계 업데이트 등 여러 부가 기능이 필요했습니다.

**문제점:**
- OrderService가 모든 의존성을 가지면서 비대해짐
- 새 기능 추가 시마다 OrderService 수정 필요
- 단위 테스트 시 모든 서비스를 mock 해야 함

**해결:**
```java
@Service
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order createOrder(Order order) {
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCreatedEvent(saved));
        return saved;
    }
}
```

각 기능을 독립적인 리스너로 분리:
- EmailListener: 비동기로 이메일 발송
- InventoryListener: 트랜잭션 커밋 후 재고 차감
- PointListener: 포인트 적립
- StatisticsListener: 통계 업데이트

**효과:**
- OrderService 의존성이 2개로 감소
- 새 기능 추가 시 리스너만 추가
- 각 기능별 독립 테스트 가능
- 이메일 발송 실패가 주문 생성에 영향 없음

**추가 적용:**
- `@TransactionalEventListener`로 DB 커밋 후 처리 보장
- `@Async`로 시간 걸리는 작업 비동기 처리
- 아웃박스 패턴으로 Kafka 메시지 발행 시 유실 방지"

---

## 정리

### 핵심 포인트

1. **Spring Event = 옵저버 패턴의 현대적 구현**
   - 낮은 결합도, 높은 확장성
   - 부가 기능 분리에 적합

2. **핵심 어노테이션**
   - `@EventListener`: 기본 리스너
   - `@TransactionalEventListener`: 트랜잭션 단계별 처리
   - `@Async`: 비동기 처리

3. **실무 패턴**
   - Domain Event
   - Event Sourcing
   - Transactional Outbox
   - Saga Pattern
   - CQRS

4. **주의사항**
   - 트랜잭션 경계 이해
   - 예외 처리 필수
   - 순환 참조 방지
   - 이벤트 데이터 불변성

5. **사용 기준**
   - Event: 부가 기능, 느슨한 결합, 비동기 가능
   - 직접 호출: 핵심 로직, 트랜잭션 일관성, 순서 보장

---

## 참고 자료

- [Spring Framework Reference - Application Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [Spring Boot Guide - Application Events and Listeners](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.spring-application.application-events-and-listeners)
- [Baeldung - Spring Events](https://www.baeldung.com/spring-events)
- [DDD - Domain Events](https://martinfowler.com/eaaDev/DomainEvent.html)
- [Microservices Patterns - Saga](https://microservices.io/patterns/data/saga.html)
