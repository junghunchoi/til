# 분산 트랜잭션 패턴 (Saga, 2PC, 이벤트 소싱)

## 분산 트랜잭션의 필요성

### 마이크로서비스에서의 트랜잭션 문제
```java
// 전통적인 모놀리스 트랜잭션 (단일 DB)
@Transactional
public void processOrder(OrderRequest request) {
    orderRepository.save(order);           // 주문 저장
    inventoryRepository.updateStock(item); // 재고 차감
    paymentRepository.processPayment(pay); // 결제 처리
    // 모든 작업이 하나의 트랜잭션에서 원자적으로 처리됨
}

// 마이크로서비스 환경 (분산 DB)
public void processOrder(OrderRequest request) {
    orderService.createOrder(order);         // 주문 서비스 DB
    inventoryService.reduceStock(item);      // 재고 서비스 DB
    paymentService.processPayment(payment);  // 결제 서비스 DB
    // 각각 다른 DB, 분산 트랜잭션 필요!
}
```

## 1. Two-Phase Commit (2PC)

### 동작 원리
```
Phase 1 (Prepare):
  Coordinator → All Participants: "Can you commit?"
  Participants → Coordinator: "Yes" or "No"

Phase 2 (Commit/Rollback):
  If all "Yes": Coordinator → All: "Commit"
  If any "No": Coordinator → All: "Rollback"
```

### Spring Boot에서 JTA 구현
```java
// XA 데이터소스 설정
@Configuration
public class JtaConfig {
    
    @Bean
    @Primary
    public DataSource orderDataSource() {
        MysqlXADataSource dataSource = new MysqlXADataSource();
        dataSource.setUrl("jdbc:mysql://order-db:3306/orderdb");
        return dataSource;
    }
    
    @Bean
    public DataSource inventoryDataSource() {
        MysqlXADataSource dataSource = new MysqlXADataSource();
        dataSource.setUrl("jdbc:mysql://inventory-db:3306/inventorydb");
        return dataSource;
    }
    
    @Bean
    public PlatformTransactionManager transactionManager() {
        return new JtaTransactionManager();
    }
}

// 분산 트랜잭션 사용
@Service
public class OrderProcessingService {
    
    @Transactional
    public void processOrder(OrderRequest request) {
        // 여러 XA 데이터소스에 걸친 트랜잭션
        orderRepository.save(createOrder(request));
        inventoryRepository.updateStock(request.getItems());
        
        if (someCondition) {
            throw new RuntimeException("Rollback all operations");
        }
    }
}
```

### 2PC의 문제점
- **가용성 문제**: Coordinator 장애 시 모든 참여자가 블록됨
- **성능 오버헤드**: 두 번의 네트워크 라운드 트립
- **확장성 제한**: 참여자 수가 늘어날수록 성능 저하

## 2. Saga 패턴

### Orchestration-based Saga
```java
// Saga Orchestrator
@Component
public class OrderSagaOrchestrator {
    
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private InventoryService inventoryService;
    
    public void processOrder(OrderRequest request) {
        try {
            // Step 1: 주문 생성
            Order order = orderService.createOrder(request);
            
            // Step 2: 결제 처리
            Payment payment = paymentService.processPayment(
                request.getPaymentInfo());
            
            // Step 3: 재고 차감
            inventoryService.reserveItems(request.getItems());
            
            // 모든 단계 성공
            orderService.confirmOrder(order.getId());
            
        } catch (PaymentFailedException e) {
            // 보상 트랜잭션: 주문 취소
            orderService.cancelOrder(order.getId());
            
        } catch (InsufficientStockException e) {
            // 보상 트랜잭션: 결제 취소 + 주문 취소
            paymentService.refundPayment(payment.getId());
            orderService.cancelOrder(order.getId());
        }
    }
}
```

### Choreography-based Saga
```java
// 이벤트 기반 Saga
@Service
public class OrderService {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public Order createOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        
        // 다음 단계 이벤트 발행
        eventPublisher.publishEvent(
            new OrderCreatedEvent(order.getId(), request));
        
        return order;
    }
}

@EventListener
@Service
public class PaymentService {
    
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            Payment payment = processPayment(event.getPaymentInfo());
            
            // 성공 시 다음 단계 이벤트 발행
            eventPublisher.publishEvent(
                new PaymentCompletedEvent(event.getOrderId(), payment));
                
        } catch (PaymentException e) {
            // 실패 시 보상 이벤트 발행
            eventPublisher.publishEvent(
                new PaymentFailedEvent(event.getOrderId(), e.getMessage()));
        }
    }
}

@EventListener
@Service
public class InventoryService {
    
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            reserveItems(event.getItems());
            
            // 최종 성공 이벤트
            eventPublisher.publishEvent(
                new OrderProcessingCompletedEvent(event.getOrderId()));
                
        } catch (InsufficientStockException e) {
            // 보상 트랜잭션 이벤트들
            eventPublisher.publishEvent(
                new StockReservationFailedEvent(event.getOrderId()));
        }
    }
}
```

### Saga 상태 관리
```java
@Entity
public class SagaTransaction {
    @Id
    private String sagaId;
    
    @Enumerated(EnumType.STRING)
    private SagaStatus status;
    
    @ElementCollection
    private List<SagaStep> completedSteps;
    
    @ElementCollection
    private List<SagaStep> compensatingSteps;
    
    public void addCompletedStep(SagaStep step) {
        completedSteps.add(step);
        // 실패 시 보상 단계 추가
        compensatingSteps.add(0, step.getCompensatingStep());
    }
}

@Service
public class SagaManager {
    
    public void compensate(String sagaId) {
        SagaTransaction saga = sagaRepository.findById(sagaId);
        
        // 역순으로 보상 트랜잭션 실행
        for (SagaStep step : saga.getCompensatingSteps()) {
            try {
                step.execute();
            } catch (Exception e) {
                // 보상 실패 로깅 및 수동 개입 필요
                log.error("Compensation failed for step: {}", step, e);
            }
        }
    }
}
```

## 3. 이벤트 소싱 (Event Sourcing)

### 기본 구조
```java
// 도메인 이벤트
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = "OrderCreated"),
    @JsonSubTypes.Type(value = OrderPaidEvent.class, name = "OrderPaid"),
    @JsonSubTypes.Type(value = OrderShippedEvent.class, name = "OrderShipped")
})
public abstract class OrderEvent {
    private String orderId;
    private LocalDateTime timestamp;
    private Long version;
}

public class OrderCreatedEvent extends OrderEvent {
    private String customerId;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
}
```

### Event Store 구현
```java
@Entity
@Table(name = "event_store")
public class EventStoreEntry {
    @Id
    private String eventId;
    
    private String aggregateId;
    private String aggregateType;
    private String eventType;
    private Long version;
    
    @Column(columnDefinition = "TEXT")
    private String eventData;
    
    private LocalDateTime timestamp;
}

@Repository
public class EventStore {
    
    public void saveEvent(String aggregateId, OrderEvent event) {
        EventStoreEntry entry = EventStoreEntry.builder()
            .eventId(UUID.randomUUID().toString())
            .aggregateId(aggregateId)
            .aggregateType("Order")
            .eventType(event.getClass().getSimpleName())
            .version(event.getVersion())
            .eventData(objectMapper.writeValueAsString(event))
            .timestamp(LocalDateTime.now())
            .build();
            
        eventStoreRepository.save(entry);
    }
    
    public List<OrderEvent> getEvents(String aggregateId) {
        return eventStoreRepository
            .findByAggregateIdOrderByVersion(aggregateId)
            .stream()
            .map(this::deserializeEvent)
            .collect(Collectors.toList());
    }
}
```

### Aggregate 재구성
```java
public class Order {
    private String orderId;
    private String customerId;
    private OrderStatus status;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private Long version = 0L;
    
    // 이벤트로부터 상태 재구성
    public static Order fromEvents(List<OrderEvent> events) {
        Order order = new Order();
        events.forEach(order::apply);
        return order;
    }
    
    private void apply(OrderEvent event) {
        if (event instanceof OrderCreatedEvent) {
            OrderCreatedEvent e = (OrderCreatedEvent) event;
            this.orderId = e.getOrderId();
            this.customerId = e.getCustomerId();
            this.items = e.getItems();
            this.totalAmount = e.getTotalAmount();
            this.status = OrderStatus.CREATED;
        } else if (event instanceof OrderPaidEvent) {
            this.status = OrderStatus.PAID;
        } else if (event instanceof OrderShippedEvent) {
            this.status = OrderStatus.SHIPPED;
        }
        this.version = event.getVersion();
    }
}
```

### CQRS와 함께 사용
```java
// Command Side (Write)
@Service
public class OrderCommandService {
    
    public void createOrder(CreateOrderCommand command) {
        OrderCreatedEvent event = new OrderCreatedEvent(
            command.getOrderId(),
            command.getCustomerId(),
            command.getItems(),
            command.getTotalAmount()
        );
        
        eventStore.saveEvent(command.getOrderId(), event);
        eventPublisher.publishEvent(event);
    }
}

// Query Side (Read) - 별도의 Read Model
@Entity
@Table(name = "order_view")
public class OrderView {
    @Id
    private String orderId;
    private String customerId;
    private String customerName;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@EventListener
@Service
public class OrderViewUpdater {
    
    public void handle(OrderCreatedEvent event) {
        OrderView view = OrderView.builder()
            .orderId(event.getOrderId())
            .customerId(event.getCustomerId())
            .status(OrderStatus.CREATED)
            .totalAmount(event.getTotalAmount())
            .createdAt(event.getTimestamp())
            .build();
            
        orderViewRepository.save(view);
    }
    
    public void handle(OrderPaidEvent event) {
        OrderView view = orderViewRepository.findById(event.getOrderId());
        view.setStatus(OrderStatus.PAID);
        view.setUpdatedAt(event.getTimestamp());
        orderViewRepository.save(view);
    }
}
```

## 패턴 비교 및 선택 기준

### 비교표
| 패턴 | 일관성 | 가용성 | 복잡도 | 성능 | 적용 상황 |
|------|--------|---------|--------|------|----------|
| 2PC | 강한 일관성 | 낮음 | 중간 | 낮음 | 금융, 중요한 트랜잭션 |
| Saga | 최종 일관성 | 높음 | 높음 | 높음 | 일반적인 비즈니스 프로세스 |
| Event Sourcing | 최종 일관성 | 높음 | 매우 높음 | 높음 | 감사, 복잡한 도메인 |

## 인터뷰 꼬리질문 대비

### Q1: "Saga에서 보상 트랜잭션이 실패하면 어떻게 하나요?"
**답변 포인트:**
- **멱등성 보장**: 보상 트랜잭션도 여러 번 실행될 수 있도록 설계
- **Dead Letter Queue**: 실패한 보상 트랜잭션을 별도 큐로 관리
- **수동 개입**: 최종적으로는 운영팀의 수동 처리 필요
- **모니터링**: 보상 실패에 대한 즉각적인 알림 시스템

### Q2: "이벤트 소싱에서 스키마 진화는 어떻게 처리하나요?"
**답변 포인트:**
- **이벤트 버저닝**: 이벤트 클래스에 버전 정보 포함
- **Upcasting**: 구 버전 이벤트를 신 버전으로 변환
- **Weak Schema**: 이벤트 구조 변경에 유연한 설계
- **스냅샷**: 성능을 위한 주기적인 상태 저장

### Q3: "분산 트랜잭션의 격리 수준은 어떻게 보장하나요?"
**답변 포인트:**
- **Saga는 ACI만 보장** (Isolation 없음)
- **Semantic Lock**: 비즈니스 로직으로 격리 구현
- **Version Control**: 낙관적 락으로 동시성 제어
- **Commutative Operations**: 순서에 무관한 연산 설계

## 실무 적용 팁

### 1. 점진적 도입
```java
// 1단계: 기존 모놀리스에 Saga 패턴 부분 적용
@Transactional
public void processOrder(OrderRequest request) {
    Order order = orderService.createOrder(request);
    
    // 기존 방식
    inventoryService.updateStock(request.getItems());
    
    // 새로운 방식 (외부 서비스)
    try {
        externalPaymentService.processPayment(request.getPayment());
    } catch (PaymentException e) {
        // 보상 트랜잭션
        inventoryService.restoreStock(request.getItems());
        throw e;
    }
}
```

### 2. 모니터링과 관찰성
```java
@Component
public class SagaMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordSagaCompletion(String sagaType, SagaStatus status) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("saga.duration")
            .tag("type", sagaType)
            .tag("status", status.name())
            .register(meterRegistry));
    }
}
```

분산 트랜잭션은 마이크로서비스 아키텍처의 핵심 도전 과제입니다. 각 패턴의 장단점을 이해하고 비즈니스 요구사항에 맞는 적절한 선택이 중요합니다.