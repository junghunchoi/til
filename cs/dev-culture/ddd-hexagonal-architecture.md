# 도메인 주도 설계(DDD)와 헥사고날 아키텍처

## 도메인 주도 설계(DDD) 핵심 개념

### DDD란?
**Domain-Driven Design**은 복잡한 소프트웨어의 핵심인 도메인과 도메인 로직에 집중하여 소프트웨어를 설계하는 방법론입니다.

### 핵심 구성 요소
```java
// 1. 엔티티 (Entity) - 고유 식별자를 가진 도메인 객체
@Entity
public class Order {
    @Id
    private OrderId id;
    private CustomerId customerId;
    private OrderStatus status;
    private Money totalAmount;
    private List<OrderItem> orderItems;
    
    // 도메인 로직을 포함한 메서드
    public void addItem(Product product, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        
        OrderItem item = new OrderItem(product, quantity);
        orderItems.add(item);
        recalculateTotalAmount();
    }
    
    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("배송된 주문은 취소할 수 없습니다");
        }
        this.status = OrderStatus.CANCELLED;
    }
    
    private void recalculateTotalAmount() {
        this.totalAmount = orderItems.stream()
                                   .map(OrderItem::getSubtotal)
                                   .reduce(Money.ZERO, Money::add);
    }
}

// 2. 값 객체 (Value Object) - 불변 객체
public class Money {
    public static final Money ZERO = new Money(BigDecimal.ZERO);
    
    private final BigDecimal amount;
    
    public Money(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다");
        }
        this.amount = amount;
    }
    
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }
    
    public Money multiply(int multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)));
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Money)) return false;
        Money money = (Money) obj;
        return Objects.equals(amount, money.amount);
    }
}

// 3. 도메인 서비스 - 여러 엔티티에 걸친 도메인 로직
@Service
public class OrderDomainService {
    
    public boolean canApplyDiscount(Customer customer, Order order) {
        // 복잡한 할인 정책 로직
        return customer.isVip() && 
               order.getTotalAmount().isGreaterThan(new Money(BigDecimal.valueOf(100000)));
    }
    
    public Money calculateShippingFee(Order order, Address shippingAddress) {
        // 배송비 계산 로직
        if (order.getTotalAmount().isGreaterThan(new Money(BigDecimal.valueOf(50000)))) {
            return Money.ZERO; // 무료배송
        }
        
        return calculateDistanceBasedFee(shippingAddress);
    }
}
```

### Aggregate 패턴
```java
// Aggregate Root - 데이터 일관성의 경계
public class Order { // Aggregate Root
    private OrderId id;
    private CustomerId customerId;
    private List<OrderItem> orderItems; // Aggregate 내부 엔티티
    private OrderStatus status;
    
    // Aggregate 외부에서는 반드시 이 메서드를 통해서만 OrderItem 추가
    public void addOrderItem(ProductId productId, int quantity, Money unitPrice) {
        validateOrderModification();
        
        OrderItem item = new OrderItem(productId, quantity, unitPrice);
        orderItems.add(item);
        
        // 도메인 이벤트 발행
        DomainEventPublisher.instance().publish(
            new OrderItemAddedEvent(this.id, item.getProductId(), quantity)
        );
    }
    
    private void validateOrderModification() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("주문 상태가 DRAFT가 아니면 수정할 수 없습니다");
        }
    }
}

// Repository 인터페이스 - 도메인 계층에 정의
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
    List<Order> findByCustomerId(CustomerId customerId);
}
```

## 헥사고날 아키텍처 (Ports and Adapters)

### 아키텍처 구조
```
    외부 세계
      ↓
┌─────────────────┐
│   Adapters      │ ← REST Controller, JPA Repository
├─────────────────┤
│   Ports         │ ← Interfaces
├─────────────────┤
│   Application   │ ← Service Layer
├─────────────────┤
│   Domain        │ ← Business Logic
└─────────────────┘
```

### 실제 구현 예시
```java
// 1. 도메인 계층 - 핵심 비즈니스 로직
public class Order {
    // 도메인 로직 (위의 예시와 동일)
}

// 2. Port (인터페이스) - 도메인이 외부와 통신하는 계약
public interface OrderPort {
    void save(Order order);
    Optional<Order> findById(OrderId id);
}

public interface PaymentPort {
    PaymentResult processPayment(PaymentRequest request);
}

public interface NotificationPort {
    void sendOrderConfirmation(OrderId orderId, String email);
}

// 3. Application Service - 유스케이스 조합
@Service
@Transactional
public class OrderApplicationService {
    
    private final OrderPort orderPort;
    private final PaymentPort paymentPort;
    private final NotificationPort notificationPort;
    
    public OrderApplicationService(OrderPort orderPort, 
                                 PaymentPort paymentPort,
                                 NotificationPort notificationPort) {
        this.orderPort = orderPort;
        this.paymentPort = paymentPort;
        this.notificationPort = notificationPort;
    }
    
    public OrderDto createOrder(CreateOrderCommand command) {
        // 1. 도메인 객체 생성
        Order order = new Order(command.getCustomerId());
        
        // 2. 도메인 로직 실행
        for (OrderItemCommand item : command.getItems()) {
            order.addOrderItem(item.getProductId(), item.getQuantity(), item.getUnitPrice());
        }
        
        // 3. 결제 처리 (외부 시스템)
        PaymentResult paymentResult = paymentPort.processPayment(
            new PaymentRequest(order.getTotalAmount(), command.getPaymentInfo())
        );
        
        if (paymentResult.isSuccess()) {
            order.confirmPayment();
        } else {
            throw new PaymentFailedException("결제 실패: " + paymentResult.getErrorMessage());
        }
        
        // 4. 저장
        orderPort.save(order);
        
        // 5. 알림 발송
        notificationPort.sendOrderConfirmation(order.getId(), command.getCustomerEmail());
        
        return OrderDto.from(order);
    }
}

// 4. Adapter - 외부 기술과의 연결점
@Repository
public class JpaOrderAdapter implements OrderPort {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public void save(Order order) {
        OrderJpaEntity entity = OrderJpaEntity.from(order);
        entityManager.persist(entity);
    }
    
    @Override
    public Optional<Order> findById(OrderId id) {
        OrderJpaEntity entity = entityManager.find(OrderJpaEntity.class, id.getValue());
        return Optional.ofNullable(entity).map(OrderJpaEntity::toDomain);
    }
}

@Component
public class PaymentAdapter implements PaymentPort {
    
    private final PaymentApiClient paymentApiClient;
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        try {
            ExternalPaymentResponse response = paymentApiClient.processPayment(
                ExternalPaymentRequest.from(request)
            );
            return PaymentResult.success(response.getTransactionId());
            
        } catch (PaymentApiException e) {
            return PaymentResult.failure(e.getMessage());
        }
    }
}

@RestController
public class OrderController {
    
    private final OrderApplicationService orderApplicationService;
    
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        CreateOrderCommand command = CreateOrderCommand.from(request);
        OrderDto order = orderApplicationService.createOrder(command);
        
        OrderResponse response = OrderResponse.from(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

## 실무 적용 전략

### 1. 점진적 도입 전략
```java
// 기존 레거시 코드
@Service
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired  
    private PaymentService paymentService;
    
    public Order createOrder(CreateOrderRequest request) {
        // 모든 로직이 서비스에 집중됨 (Anemic Domain Model)
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setStatus("PENDING");
        
        // 비즈니스 로직이 서비스에 분산됨
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest item : request.getItems()) {
            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        order.setTotalAmount(total);
        
        return orderRepository.save(order);
    }
}

// DDD 적용 후 - 1단계: Rich Domain Model로 변경
public class Order {
    private OrderId id;
    private CustomerId customerId;
    private OrderStatus status;
    private Money totalAmount;
    private List<OrderItem> orderItems = new ArrayList<>();
    
    // 생성자에서 도메인 불변식 보장
    public Order(CustomerId customerId) {
        this.id = OrderId.generate();
        this.customerId = Objects.requireNonNull(customerId);
        this.status = OrderStatus.DRAFT;
        this.totalAmount = Money.ZERO;
    }
    
    // 도메인 로직을 도메인 객체로 이동
    public void addItem(Product product, int quantity) {
        validateCanAddItem();
        
        OrderItem item = new OrderItem(product.getId(), quantity, product.getPrice());
        orderItems.add(item);
        recalculateTotalAmount();
    }
    
    public void confirm() {
        validateCanConfirm();
        this.status = OrderStatus.CONFIRMED;
    }
    
    private void validateCanAddItem() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("주문이 DRAFT 상태가 아닙니다");
        }
    }
    
    private void validateCanConfirm() {
        if (orderItems.isEmpty()) {
            throw new IllegalStateException("주문 항목이 없습니다");
        }
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("이미 확정된 주문입니다");
        }
    }
    
    private void recalculateTotalAmount() {
        this.totalAmount = orderItems.stream()
                                   .map(OrderItem::getSubtotal)
                                   .reduce(Money.ZERO, Money::add);
    }
}
```

### 2. 테스트 전략
```java
// 도메인 로직 단위 테스트
class OrderTest {
    
    @Test
    @DisplayName("주문 항목을 추가하면 총 금액이 계산된다")
    void addItem_CalculatesTotalAmount() {
        // Given
        Order order = new Order(new CustomerId(1L));
        Product product = new Product("상품", new Money(BigDecimal.valueOf(1000)));
        
        // When
        order.addItem(product, 2);
        
        // Then
        assertThat(order.getTotalAmount()).isEqualTo(new Money(BigDecimal.valueOf(2000)));
        assertThat(order.getOrderItems()).hasSize(1);
    }
    
    @Test
    @DisplayName("확정된 주문에는 항목을 추가할 수 없다")
    void addItem_WhenOrderConfirmed_ThrowsException() {
        // Given
        Order order = new Order(new CustomerId(1L));
        Product product = new Product("상품", new Money(BigDecimal.valueOf(1000)));
        order.addItem(product, 1);
        order.confirm();
        
        // When & Then
        assertThatThrownBy(() -> order.addItem(product, 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("주문이 DRAFT 상태가 아닙니다");
    }
}

// 헥사고날 아키텍처 테스트 - Port 모킹
@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {
    
    @Mock
    private OrderPort orderPort;
    
    @Mock
    private PaymentPort paymentPort;
    
    @Mock
    private NotificationPort notificationPort;
    
    @InjectMocks
    private OrderApplicationService orderApplicationService;
    
    @Test
    @DisplayName("주문 생성 성공 시나리오")
    void createOrder_Success() {
        // Given
        CreateOrderCommand command = createValidOrderCommand();
        when(paymentPort.processPayment(any())).thenReturn(PaymentResult.success("TX123"));
        
        // When
        OrderDto result = orderApplicationService.createOrder(command);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        
        verify(orderPort).save(any(Order.class));
        verify(paymentPort).processPayment(any(PaymentRequest.class));
        verify(notificationPort).sendOrderConfirmation(any(), eq(command.getCustomerEmail()));
    }
}
```

### 3. 팀 도입 가이드라인
```java
// 1. 도메인 전문가와의 협업을 위한 유비쿼터스 언어 정의
public class OrderGlossary {
    /*
    주문(Order): 고객이 상품 구매를 요청한 것
    주문 항목(OrderItem): 주문에 포함된 개별 상품과 수량
    주문 확정(Order Confirmation): 결제가 완료되어 주문이 처리 대기 상태가 된 것
    주문 취소(Order Cancellation): 고객 요청 또는 시스템 문제로 주문이 취소된 것
    
    도메인 전문가(기획자, PO)와 개발자가 같은 용어를 사용해야 함
    */
}

// 2. 경계 컨텍스트 분리 예시
// 주문 컨텍스트
public class Order {
    private OrderId id;
    private CustomerId customerId; // 다른 컨텍스트의 ID 참조만
    // 주문 관련 비즈니스 로직만 포함
}

// 고객 컨텍스트 (별도 모듈/패키지)
public class Customer {
    private CustomerId id;
    private CustomerGrade grade;
    // 고객 관련 비즈니스 로직만 포함
}

// 3. 프로젝트 구조 예시
/*
src/main/java/
├── domain/                    # 도메인 계층
│   ├── order/
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   ├── OrderRepository.java (인터페이스)
│   │   └── OrderDomainService.java
│   └── customer/
│       ├── Customer.java
│       └── CustomerRepository.java
├── application/               # 애플리케이션 계층
│   ├── OrderApplicationService.java
│   └── command/
│       └── CreateOrderCommand.java
├── infrastructure/            # 인프라 계층
│   ├── persistence/
│   │   ├── JpaOrderRepository.java
│   │   └── OrderJpaEntity.java
│   ├── payment/
│   │   └── PaymentAdapter.java
│   └── notification/
│       └── EmailNotificationAdapter.java
└── interfaces/               # 인터페이스 계층
    └── rest/
        ├── OrderController.java
        └── dto/
            ├── CreateOrderRequest.java
            └── OrderResponse.java
*/
```

## 인터뷰 꼬리질문 대비

### Q1: "DDD를 도입했을 때의 실제 효과는 무엇인가요?"
**답변 포인트:**
- **복잡성 관리**: 비즈니스 로직이 도메인 객체에 응집되어 이해하기 쉬워짐
- **변경 용이성**: 비즈니스 요구사항 변경 시 도메인 계층만 수정하면 됨
- **테스트 용이성**: 도메인 로직 단위 테스트가 간편해짐
- **협업 개선**: 도메인 전문가와 개발자 간 공통 언어 형성

### Q2: "헥사고날 아키텍처의 단점은 없나요?"
**답변 포인트:**
- **초기 복잡성**: 간단한 CRUD에는 과한 구조일 수 있음
- **학습 비용**: 팀원들의 아키텍처 이해 필요
- **코드량 증가**: 인터페이스와 구현체 분리로 파일 수 증가
- **적용 시점**: 프로젝트 규모와 복잡도에 따른 적절한 적용 판단 필요

### Q3: "레거시 시스템에 어떻게 점진적으로 적용하나요?"
**답변 포인트:**
- **Strangler Fig Pattern**: 새로운 기능부터 DDD 적용
- **Anti-Corruption Layer**: 레거시와 새 시스템 간 변환 계층
- **Bounded Context 분리**: 독립적인 모듈부터 시작
- **팀 합의**: 점진적 도입을 위한 팀 내 가이드라인 수립

## 실무 적용 팁

1. **작게 시작하기**: 전체 시스템보다는 하나의 도메인부터 적용
2. **도메인 전문가와 협업**: 정기적인 도메인 모델링 세션 진행
3. **테스트 우선**: 도메인 로직부터 철저한 단위 테스트 작성
4. **지속적 리팩토링**: 도메인 이해가 깊어질수록 모델 개선
5. **팀 교육**: DDD 개념과 패턴에 대한 지속적인 학습

DDD와 헥사고날 아키텍처는 복잡한 비즈니스 도메인을 가진 시스템에서 특히 그 가치를 발휘합니다. 단순한 CRUD 애플리케이션보다는 복잡한 비즈니스 규칙과 워크플로우가 있는 시스템에 적용하는 것이 효과적입니다.