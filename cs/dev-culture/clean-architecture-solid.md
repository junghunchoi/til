# 클린 아키텍처와 SOLID 원칙 실무 적용

## 클린 아키텍처 핵심 개념

### 클린 아키텍처 구조
```
                외부 인터페이스
        ┌─────────────────────────────┐
        │    Frameworks & Drivers     │
        │  (Web, DB, External APIs)   │
        └─────────────────────────────┘
                        │
        ┌─────────────────────────────┐
        │    Interface Adapters       │
        │  (Controllers, Gateways)    │
        └─────────────────────────────┘
                        │
        ┌─────────────────────────────┐
        │    Application Business     │
        │       Rules (Use Cases)     │
        └─────────────────────────────┘
                        │
        ┌─────────────────────────────┐
        │   Enterprise Business Rules │
        │        (Entities)           │
        └─────────────────────────────┘
                    Core
```

### 의존성 규칙 (Dependency Rule)
```java
// 잘못된 예: 내부 계층이 외부 계층에 의존
public class OrderService {  // Use Case 계층
    @Autowired
    private OrderController controller;  // ❌ 외부 계층에 의존
    
    @Autowired
    private OrderRepository repository;  // ❌ 구체적인 구현에 의존
}

// 올바른 예: 의존성 역전을 통한 의존성 규칙 준수
public class OrderService {  // Use Case 계층
    private final OrderPort orderPort;      // ✅ 인터페이스에 의존
    private final PaymentPort paymentPort;  // ✅ 인터페이스에 의존
    
    public OrderService(OrderPort orderPort, PaymentPort paymentPort) {
        this.orderPort = orderPort;
        this.paymentPort = paymentPort;
    }
    
    public Order processOrder(OrderRequest request) {
        // 비즈니스 로직만 포함
        Order order = Order.create(request);
        PaymentResult result = paymentPort.processPayment(request.getPayment());
        
        if (result.isSuccessful()) {
            order.confirm();
            orderPort.save(order);
        }
        
        return order;
    }
}

// 인터페이스 정의 (Use Case 계층)
public interface OrderPort {
    void save(Order order);
    Optional<Order> findById(OrderId id);
}

// 구현체 (Infrastructure 계층)
@Repository
public class JpaOrderPort implements OrderPort {
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
```

## SOLID 원칙 실무 적용

### 1. 단일 책임 원칙 (Single Responsibility Principle)

```java
// ❌ SRP 위반: 하나의 클래스가 너무 많은 책임을 가짐
public class OrderService {
    public void createOrder(OrderRequest request) {
        // 1. 주문 검증
        validateOrder(request);
        
        // 2. 주문 생성
        Order order = new Order(request);
        
        // 3. 결제 처리
        processPayment(request.getPayment());
        
        // 4. 재고 차감
        reduceInventory(request.getItems());
        
        // 5. 이메일 발송
        sendConfirmationEmail(request.getCustomerEmail());
        
        // 6. 로그 기록
        logOrderCreation(order);
        
        // 7. 데이터베이스 저장
        saveToDatabase(order);
    }
    
    private void validateOrder(OrderRequest request) { /* ... */ }
    private void processPayment(Payment payment) { /* ... */ }
    private void reduceInventory(List<OrderItem> items) { /* ... */ }
    private void sendConfirmationEmail(String email) { /* ... */ }
    private void logOrderCreation(Order order) { /* ... */ }
    private void saveToDatabase(Order order) { /* ... */ }
}

// ✅ SRP 준수: 각 클래스가 하나의 책임만 가짐
@Service
public class OrderService {
    private final OrderValidator orderValidator;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final OrderRepository orderRepository;
    
    public Order createOrder(OrderRequest request) {
        // 주문 생성과 조합만 담당
        orderValidator.validate(request);
        
        Order order = Order.create(request);
        
        PaymentResult paymentResult = paymentService.processPayment(request.getPayment());
        if (paymentResult.isSuccessful()) {
            order.confirm();
            inventoryService.reduceStock(request.getItems());
            orderRepository.save(order);
            notificationService.sendOrderConfirmation(order);
        }
        
        return order;
    }
}

@Component
public class OrderValidator {
    public void validate(OrderRequest request) {
        if (request.getItems().isEmpty()) {
            throw new InvalidOrderException("주문 항목이 없습니다");
        }
        
        if (request.getCustomerId() == null) {
            throw new InvalidOrderException("고객 정보가 없습니다");
        }
    }
}

@Service
public class PaymentService {
    private final PaymentGateway paymentGateway;
    
    public PaymentResult processPayment(PaymentRequest request) {
        // 결제 처리만 담당
        return paymentGateway.charge(request);
    }
}
```

### 2. 개방-폐쇄 원칙 (Open-Closed Principle)

```java
// ❌ OCP 위반: 새로운 할인 정책 추가 시 기존 코드 수정 필요
public class DiscountCalculator {
    public BigDecimal calculateDiscount(Order order, String discountType) {
        if ("VIP".equals(discountType)) {
            return order.getTotalAmount().multiply(BigDecimal.valueOf(0.1));
        } else if ("SEASONAL".equals(discountType)) {
            return order.getTotalAmount().multiply(BigDecimal.valueOf(0.05));
        } else if ("BULK".equals(discountType)) {
            if (order.getItemCount() >= 10) {
                return order.getTotalAmount().multiply(BigDecimal.valueOf(0.15));
            }
        }
        // 새로운 할인 정책 추가 시 이 메서드를 수정해야 함
        return BigDecimal.ZERO;
    }
}

// ✅ OCP 준수: 확장에는 열려있고 수정에는 닫혀있음
public interface DiscountPolicy {
    BigDecimal calculateDiscount(Order order);
    boolean isApplicable(Order order);
}

@Component
public class VipDiscountPolicy implements DiscountPolicy {
    @Override
    public BigDecimal calculateDiscount(Order order) {
        return order.getTotalAmount().multiply(BigDecimal.valueOf(0.1));
    }
    
    @Override
    public boolean isApplicable(Order order) {
        return order.getCustomer().isVip();
    }
}

@Component
public class SeasonalDiscountPolicy implements DiscountPolicy {
    @Override
    public BigDecimal calculateDiscount(Order order) {
        return order.getTotalAmount().multiply(BigDecimal.valueOf(0.05));
    }
    
    @Override
    public boolean isApplicable(Order order) {
        LocalDate now = LocalDate.now();
        return now.getMonthValue() == 12; // 12월 시즌 할인
    }
}

@Service
public class DiscountCalculator {
    private final List<DiscountPolicy> discountPolicies;
    
    public DiscountCalculator(List<DiscountPolicy> discountPolicies) {
        this.discountPolicies = discountPolicies;
    }
    
    public BigDecimal calculateTotalDiscount(Order order) {
        return discountPolicies.stream()
                              .filter(policy -> policy.isApplicable(order))
                              .map(policy -> policy.calculateDiscount(order))
                              .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    // 새로운 할인 정책 추가 시 이 클래스는 수정하지 않아도 됨
}

// 새로운 할인 정책 추가 (기존 코드 수정 없이 확장)
@Component
public class FirstTimeCustomerDiscountPolicy implements DiscountPolicy {
    @Override
    public BigDecimal calculateDiscount(Order order) {
        return new BigDecimal("5000"); // 5천원 할인
    }
    
    @Override
    public boolean isApplicable(Order order) {
        return order.getCustomer().isFirstTimeCustomer();
    }
}
```

### 3. 리스코프 치환 원칙 (Liskov Substitution Principle)

```java
// ❌ LSP 위반: 하위 클래스가 상위 클래스의 계약을 위반
public abstract class PaymentProcessor {
    public abstract PaymentResult process(PaymentRequest request);
    
    // 전제 조건: amount > 0
    // 후행 조건: 성공 시 PaymentResult.success() 반환
}

public class CreditCardProcessor extends PaymentProcessor {
    @Override
    public PaymentResult process(PaymentRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다");
            // ❌ 상위 클래스보다 더 엄격한 전제 조건
        }
        
        // 신용카드 처리 로직
        return PaymentResult.success("CARD_TX_123");
    }
}

public class FreePaymentProcessor extends PaymentProcessor {
    @Override
    public PaymentResult process(PaymentRequest request) {
        // ❌ LSP 위반: 0원 결제 시에도 예외 발생
        if (request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new UnsupportedOperationException("무료 결제는 지원하지 않습니다");
        }
        
        return PaymentResult.success("FREE_TX");
    }
}

// ✅ LSP 준수: 하위 클래스가 상위 클래스의 계약을 완전히 준수
public abstract class PaymentProcessor {
    /**
     * 결제를 처리합니다.
     * @param request 결제 요청 (amount >= 0)
     * @return 결제 결과 (실패 시에도 예외를 던지지 않고 실패 결과 반환)
     */
    public abstract PaymentResult process(PaymentRequest request);
}

public class CreditCardProcessor extends PaymentProcessor {
    @Override
    public PaymentResult process(PaymentRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            return PaymentResult.failure("음수 금액은 처리할 수 없습니다");
            // ✅ 예외를 던지지 않고 실패 결과 반환
        }
        
        if (request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return PaymentResult.success("FREE_CARD_TX");
            // ✅ 0원 결제도 정상 처리
        }
        
        // 신용카드 처리 로직
        return PaymentResult.success("CARD_TX_123");
    }
}

public class BankTransferProcessor extends PaymentProcessor {
    @Override
    public PaymentResult process(PaymentRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            return PaymentResult.failure("음수 금액은 처리할 수 없습니다");
        }
        
        if (request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return PaymentResult.success("FREE_BANK_TX");
        }
        
        // 계좌이체 처리 로직
        return PaymentResult.success("BANK_TX_456");
    }
}
```

### 4. 인터페이스 분리 원칙 (Interface Segregation Principle)

```java
// ❌ ISP 위반: 하나의 인터페이스가 너무 많은 책임을 가짐
public interface OrderProcessor {
    void validateOrder(Order order);
    void processPayment(PaymentInfo payment);
    void updateInventory(List<OrderItem> items);
    void sendEmail(String email, String content);
    void generateInvoice(Order order);
    void scheduleDelivery(Address address);
    void updateLoyaltyPoints(CustomerId customerId, int points);
}

// 모든 구현체가 모든 메서드를 구현해야 함
public class SimpleOrderProcessor implements OrderProcessor {
    @Override
    public void validateOrder(Order order) { /* 구현 */ }
    
    @Override
    public void processPayment(PaymentInfo payment) { /* 구현 */ }
    
    @Override
    public void updateInventory(List<OrderItem> items) { /* 구현 */ }
    
    @Override
    public void sendEmail(String email, String content) {
        throw new UnsupportedOperationException("이메일 기능 불필요");
        // ❌ 필요하지 않은 메서드도 구현해야 함
    }
    
    @Override
    public void generateInvoice(Order order) {
        throw new UnsupportedOperationException("송장 기능 불필요");
        // ❌ 필요하지 않은 메서드도 구현해야 함
    }
    
    @Override
    public void scheduleDelivery(Address address) { /* 구현 */ }
    
    @Override
    public void updateLoyaltyPoints(CustomerId customerId, int points) {
        throw new UnsupportedOperationException("포인트 기능 불필요");
        // ❌ 필요하지 않은 메서드도 구현해야 함
    }
}

// ✅ ISP 준수: 인터페이스를 역할별로 분리
public interface OrderValidator {
    void validate(Order order);
}

public interface PaymentProcessor {
    PaymentResult processPayment(PaymentInfo payment);
}

public interface InventoryUpdater {
    void updateInventory(List<OrderItem> items);
}

public interface NotificationSender {
    void sendNotification(String recipient, String message);
}

public interface InvoiceGenerator {
    Invoice generateInvoice(Order order);
}

public interface DeliveryScheduler {
    void scheduleDelivery(Order order, Address address);
}

public interface LoyaltyPointManager {
    void updatePoints(CustomerId customerId, int points);
}

// 각 구현체는 필요한 인터페이스만 구현
public class BasicOrderValidator implements OrderValidator {
    @Override
    public void validate(Order order) {
        if (order.getItems().isEmpty()) {
            throw new InvalidOrderException("주문 항목이 없습니다");
        }
    }
}

public class EmailNotificationSender implements NotificationSender {
    @Override
    public void sendNotification(String recipient, String message) {
        // 이메일 발송 로직
    }
}

public class SmsNotificationSender implements NotificationSender {
    @Override
    public void sendNotification(String recipient, String message) {
        // SMS 발송 로직
    }
}
```

### 5. 의존성 역전 원칙 (Dependency Inversion Principle)

```java
// ❌ DIP 위반: 고수준 모듈이 저수준 모듈에 직접 의존
@Service
public class OrderService {
    
    // 구체적인 구현체에 직접 의존
    private JpaOrderRepository orderRepository = new JpaOrderRepository();
    private PayPalPaymentGateway paymentGateway = new PayPalPaymentGateway();
    private SmtpEmailSender emailSender = new SmtpEmailSender();
    
    public Order processOrder(OrderRequest request) {
        Order order = new Order(request);
        
        // 결제 처리 (PayPal에 강하게 결합)
        PayPalPaymentResult result = paymentGateway.processPayment(request.getPayment());
        
        if (result.isSuccessful()) {
            order.confirm();
            orderRepository.save(order);
            emailSender.sendConfirmationEmail(order.getCustomerEmail());
        }
        
        return order;
    }
}

// ✅ DIP 준수: 고수준 모듈이 추상화에 의존
// 인터페이스 정의 (고수준 모듈에서 정의)
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
}

public interface PaymentGateway {
    PaymentResult processPayment(PaymentRequest request);
}

public interface EmailSender {
    void sendConfirmationEmail(String email, Order order);
}

// 고수준 모듈 (비즈니스 로직)
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final EmailSender emailSender;
    
    // 생성자 주입을 통한 의존성 역전
    public OrderService(OrderRepository orderRepository,
                       PaymentGateway paymentGateway,
                       EmailSender emailSender) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.emailSender = emailSender;
    }
    
    public Order processOrder(OrderRequest request) {
        Order order = new Order(request);
        
        PaymentResult result = paymentGateway.processPayment(
            PaymentRequest.from(request.getPayment())
        );
        
        if (result.isSuccessful()) {
            order.confirm();
            orderRepository.save(order);
            emailSender.sendConfirmationEmail(order.getCustomerEmail(), order);
        }
        
        return order;
    }
}

// 구체적인 구현체들 (저수준 모듈)
@Repository
public class JpaOrderRepository implements OrderRepository {
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
public class PayPalPaymentGateway implements PaymentGateway {
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // PayPal API 호출 로직
        return PaymentResult.success("PAYPAL_TX_123");
    }
}

@Component
public class SmtpEmailSender implements EmailSender {
    @Override
    public void sendConfirmationEmail(String email, Order order) {
        // SMTP를 통한 이메일 발송 로직
    }
}

// 설정 클래스에서 구현체 교체 가능
@Configuration
public class PaymentConfig {
    
    @Bean
    @Primary
    @Profile("prod")
    public PaymentGateway payPalGateway() {
        return new PayPalPaymentGateway();
    }
    
    @Bean
    @Profile("test")
    public PaymentGateway mockGateway() {
        return new MockPaymentGateway();
    }
}
```

## 실무 적용 가이드

### 1. 패키지 구조 설계
```java
// Clean Architecture 기반 패키지 구조
/*
src/main/java/com/example/
├── application/                    # Application Layer
│   ├── usecase/
│   │   ├── CreateOrderUseCase.java
│   │   └── GetOrderUseCase.java
│   ├── port/
│   │   ├── input/                  # Primary Ports
│   │   │   ├── CreateOrderCommand.java
│   │   │   └── OrderQueryService.java
│   │   └── output/                 # Secondary Ports
│   │       ├── OrderRepository.java
│   │       ├── PaymentGateway.java
│   │       └── NotificationSender.java
│   └── service/
│       └── OrderApplicationService.java
├── domain/                         # Domain Layer
│   ├── entity/
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── Customer.java
│   ├── valueobject/
│   │   ├── OrderId.java
│   │   ├── Money.java
│   │   └── Address.java
│   ├── service/
│   │   └── OrderDomainService.java
│   └── exception/
│       ├── OrderNotFoundException.java
│       └── InvalidOrderException.java
├── infrastructure/                 # Infrastructure Layer
│   ├── persistence/
│   │   ├── JpaOrderRepository.java
│   │   └── entity/
│   │       └── OrderJpaEntity.java
│   ├── payment/
│   │   └── PayPalPaymentGateway.java
│   └── notification/
│       └── EmailNotificationSender.java
└── interfaces/                     # Interface Layer
    ├── rest/
    │   ├── OrderController.java
    │   └── dto/
    │       ├── CreateOrderRequest.java
    │       └── OrderResponse.java
    └── config/
        └── BeanConfiguration.java
*/
```

### 2. 테스트 전략
```java
// 1. 도메인 계층 테스트 (단위 테스트)
class OrderTest {
    
    @Test
    @DisplayName("주문 생성 시 총 금액이 계산된다")
    void createOrder_CalculatesTotalAmount() {
        // Given
        List<OrderItem> items = Arrays.asList(
            new OrderItem("상품1", 2, new Money(BigDecimal.valueOf(1000))),
            new OrderItem("상품2", 1, new Money(BigDecimal.valueOf(2000)))
        );
        
        // When
        Order order = Order.create(new CustomerId(1L), items);
        
        // Then
        assertThat(order.getTotalAmount()).isEqualTo(new Money(BigDecimal.valueOf(4000)));
    }
}

// 2. 애플리케이션 서비스 테스트 (통합 테스트)
@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private PaymentGateway paymentGateway;
    
    @Mock
    private NotificationSender notificationSender;
    
    @InjectMocks
    private OrderApplicationService orderApplicationService;
    
    @Test
    @DisplayName("주문 생성 성공 시나리오")
    void createOrder_Success() {
        // Given
        CreateOrderCommand command = createValidCommand();
        when(paymentGateway.processPayment(any())).thenReturn(PaymentResult.success("TX123"));
        
        // When
        OrderDto result = orderApplicationService.createOrder(command);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).save(any(Order.class));
        verify(notificationSender).sendConfirmationEmail(any(), any());
    }
}

// 3. 아키텍처 테스트 (ArchUnit)
@AnalyzeClasses(packages = "com.example")
class ArchitectureTest {
    
    @ArchTest
    static final ArchRule domainShouldNotDependOnInfrastructure =
        classes().that().resideInAPackage("..domain..")
                 .should().onlyDependOnClassesInPackages("..domain..", "java..", "javax..");
    
    @ArchTest
    static final ArchRule applicationShouldNotDependOnInfrastructure =
        classes().that().resideInAPackage("..application..")
                 .should().onlyDependOnClassesInPackages("..application..", "..domain..", "java..", "javax..");
    
    @ArchTest
    static final ArchRule controllersShouldOnlyDependOnApplicationLayer =
        classes().that().resideInAPackage("..interfaces.rest..")
                 .should().onlyDependOnClassesInPackages("..application..", "..domain..", "java..", "javax..", "org.springframework..");
}
```

### 3. 점진적 리팩토링 전략
```java
// 1단계: 현재 상태 (레거시)
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private PaymentService paymentService;
    
    public Order createOrder(CreateOrderRequest request) {
        // 모든 로직이 서비스에 집중
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        
        // 비즈니스 로직이 분산됨
        BigDecimal total = calculateTotal(request.getItems());
        order.setTotalAmount(total);
        
        // 외부 서비스 직접 호출
        PaymentResult result = paymentService.processPayment(request.getPaymentInfo());
        
        if (result.isSuccess()) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        }
        
        return order;
    }
}

// 2단계: 도메인 로직 도메인 객체로 이동
public class Order {
    private OrderId id;
    private CustomerId customerId;
    private List<OrderItem> items;
    private OrderStatus status;
    private Money totalAmount;
    
    // 생성자에서 불변식 보장
    public Order(CustomerId customerId, List<OrderItem> items) {
        this.id = OrderId.generate();
        this.customerId = Objects.requireNonNull(customerId);
        this.items = new ArrayList<>(items);
        this.status = OrderStatus.DRAFT;
        this.totalAmount = calculateTotalAmount();
    }
    
    // 도메인 로직을 도메인 객체로 이동
    public void confirm() {
        if (this.status != OrderStatus.DRAFT) {
            throw new IllegalStateException("이미 확정된 주문입니다");
        }
        this.status = OrderStatus.CONFIRMED;
    }
    
    private Money calculateTotalAmount() {
        return items.stream()
                   .map(OrderItem::getSubtotal)
                   .reduce(Money.ZERO, Money::add);
    }
}

// 3단계: 인터페이스 분리 및 의존성 역전
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    
    public OrderService(OrderRepository orderRepository, PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
    }
    
    public Order createOrder(CreateOrderCommand command) {
        Order order = Order.create(command.getCustomerId(), command.getItems());
        
        PaymentResult result = paymentGateway.processPayment(
            PaymentRequest.from(command.getPaymentInfo())
        );
        
        if (result.isSuccessful()) {
            order.confirm();
            orderRepository.save(order);
        }
        
        return order;
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "SOLID 원칙을 모두 지키는 것이 항상 좋은가요?"
**답변 포인트:**
- **균형잡힌 접근**: 과도한 추상화는 복잡성을 증가시킬 수 있음
- **상황에 따른 적용**: 간단한 CRUD는 모든 원칙을 엄격히 적용할 필요 없음  
- **점진적 적용**: 복잡성이 증가할 때 단계적으로 원칙 적용
- **팀 역량 고려**: 팀의 이해 수준에 맞는 적절한 수준에서 적용

### Q2: "클린 아키텍처의 단점은 무엇인가요?"
**답변 포인트:**
- **초기 복잡성**: 간단한 기능도 여러 계층을 거쳐야 함
- **코드량 증가**: 인터페이스와 구현체 분리로 파일 수 증가
- **학습 비용**: 팀원들의 아키텍처 이해 필요
- **과도한 추상화**: 불필요한 추상화로 인한 복잡성 증가 가능

### Q3: "레거시 코드에 어떻게 적용하나요?"
**답변 포인트:**
- **Strangler Fig Pattern**: 새로운 기능부터 적용
- **테스트 코드 작성**: 기존 동작을 보장하는 테스트 먼저 작성
- **점진적 리팩토링**: 한 번에 모든 것을 바꾸지 않고 단계적 개선
- **팀 합의**: 리팩토링 우선순위와 방향에 대한 팀 내 합의

## 실무 적용 팁

1. **작게 시작하기**: 전체 시스템보다는 하나의 기능부터 적용
2. **테스트 우선**: 리팩토링 전 충분한 테스트 코드 작성
3. **점진적 개선**: 한 번에 모든 원칙을 적용하려 하지 말기
4. **팀 교육**: 아키텍처 원칙에 대한 지속적인 학습과 토론
5. **도구 활용**: ArchUnit 등을 활용한 아키텍처 규칙 자동 검증

클린 아키텍처와 SOLID 원칙은 코드의 유지보수성과 확장성을 크게 향상시키는 강력한 도구입니다. 하지만 상황에 맞는 적절한 적용이 중요하며, 팀의 역량과 프로젝트의 복잡도를 고려하여 점진적으로 도입하는 것이 바람직합니다.