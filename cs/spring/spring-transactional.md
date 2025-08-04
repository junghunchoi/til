# @Transactional 애노테이션의 동작 원리와 속성

## @Transactional 동작 원리

### 프록시 기반 트랜잭션 관리
```java
// 프록시가 생성되는 과정
@Service
public class OrderService {
    
    @Transactional
    public Order createOrder(OrderRequest request) {
        // 1. 프록시가 트랜잭션 시작
        // 2. 실제 메서드 실행
        // 3. 성공 시 커밋, 예외 시 롤백
        return orderRepository.save(new Order(request));
    }
}

// Spring이 생성하는 프록시 의사코드
public class OrderServiceProxy extends OrderService {
    
    private PlatformTransactionManager transactionManager;
    private OrderService target;
    
    @Override
    public Order createOrder(OrderRequest request) {
        TransactionStatus status = null;
        try {
            // 트랜잭션 시작
            status = transactionManager.getTransaction(new DefaultTransactionDefinition());
            
            // 실제 메서드 호출
            Order result = target.createOrder(request);
            
            // 커밋
            transactionManager.commit(status);
            return result;
            
        } catch (Exception e) {
            // 롤백
            if (status != null) {
                transactionManager.rollback(status);
            }
            throw e;
        }
    }
}
```

### TransactionManager 구조
```java
// 트랜잭션 매니저 설정
@Configuration
@EnableTransactionManagement
public class TransactionConfig {
    
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
    
    // JPA 사용 시
    @Bean
    public PlatformTransactionManager jpaTransactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
    
    // 여러 데이터소스 사용 시 (JTA)
    @Bean
    public PlatformTransactionManager jtaTransactionManager() {
        return new JtaTransactionManager();
    }
}
```

## Propagation (전파 속성)

### 전파 속성별 상세 동작
```java
@Service
public class OrderService {
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private InventoryService inventoryService;
    
    // REQUIRED (기본값): 기존 트랜잭션이 있으면 참여, 없으면 새로 생성
    @Transactional(propagation = Propagation.REQUIRED)
    public void processOrder(OrderRequest request) {
        orderRepository.save(new Order(request));
        
        // 같은 트랜잭션에서 실행됨
        paymentService.processPayment(request.getPaymentInfo());
        inventoryService.reduceStock(request.getProductId(), request.getQuantity());
        
        // 하나라도 실패하면 모두 롤백
    }
}

@Service
public class PaymentService {
    
    // REQUIRES_NEW: 항상 새로운 트랜잭션 생성 (기존 트랜잭션 일시 중단)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResult processPayment(PaymentInfo paymentInfo) {
        // 독립적인 트랜잭션에서 실행
        // 이 메서드가 실패해도 상위 트랜잭션에 영향 없음
        Payment payment = new Payment(paymentInfo);
        return paymentRepository.save(payment);
    }
    
    // NESTED: 중첩 트랜잭션 (Savepoint 사용)
    @Transactional(propagation = Propagation.NESTED)
    public void processRefund(Long paymentId) {
        // 이 메서드 실패 시 이 부분만 롤백, 상위 트랜잭션은 계속
        Payment payment = paymentRepository.findById(paymentId);
        payment.refund();
        paymentRepository.save(payment);
    }
}

@Service  
public class LoggingService {
    
    // NOT_SUPPORTED: 트랜잭션 없이 실행 (기존 트랜잭션 일시 중단)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void logActivity(String activity) {
        // 트랜잭션과 무관하게 항상 기록됨
        activityLogRepository.save(new ActivityLog(activity));
    }
    
    // NEVER: 트랜잭션이 존재하면 예외 발생
    @Transactional(propagation = Propagation.NEVER)
    public void validateConfiguration() {
        // 트랜잭션 컨텍스트에서 호출되면 안 되는 메서드
    }
    
    // MANDATORY: 기존 트랜잭션이 반드시 있어야 함
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        // 반드시 트랜잭션 내에서만 호출되어야 하는 메서드
        Order order = orderRepository.findById(orderId);
        order.setStatus(status);
    }
    
    // SUPPORTS: 트랜잭션이 있으면 참여, 없으면 트랜잭션 없이 실행
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Order> getOrderHistory(Long customerId) {
        // 읽기 전용 메서드, 트랜잭션 유무에 상관없이 동작
        return orderRepository.findByCustomerId(customerId);
    }
}
```

### 전파 속성 실제 테스트
```java
@SpringBootTest
@Transactional
@Rollback
class TransactionPropagationTest {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private PaymentService paymentService;
    
    @Test
    void testRequiredPropagation() {
        // REQUIRED: 하나의 트랜잭션에서 모두 실행
        assertThatThrownBy(() -> {
            orderService.processOrder(createOrderRequest());
        }).isInstanceOf(PaymentException.class);
        
        // 모든 변경사항이 롤백됨
        assertThat(orderRepository.count()).isEqualTo(0);
        assertThat(paymentRepository.count()).isEqualTo(0);
    }
    
    @Test
    void testRequiresNewPropagation() {
        // REQUIRES_NEW: 독립적인 트랜잭션
        try {
            orderService.processOrderWithIndependentPayment(createOrderRequest());
        } catch (Exception e) {
            // Order는 롤백되지만 Payment는 커밋됨
        }
        
        assertThat(orderRepository.count()).isEqualTo(0);  // 롤백됨
        assertThat(paymentRepository.count()).isEqualTo(1); // 커밋됨
    }
}
```

## Isolation (격리 수준)

### 격리 수준별 문제점과 해결
```java
@Service
public class AccountService {
    
    // READ_UNCOMMITTED: Dirty Read 가능
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public BigDecimal getBalance(Long accountId) {
        // 다른 트랜잭션의 커밋되지 않은 데이터도 읽을 수 있음
        // 성능은 최고, 일관성은 최저
        return accountRepository.findById(accountId).getBalance();
    }
    
    // READ_COMMITTED: Dirty Read 방지, Non-repeatable Read 가능
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        Account fromAccount = accountRepository.findById(fromId);
        // 중간에 다른 트랜잭션이 fromAccount를 수정할 수 있음
        Thread.sleep(1000); // 시뮬레이션
        Account fromAccountAgain = accountRepository.findById(fromId);
        // fromAccount와 fromAccountAgain의 값이 다를 수 있음 (Non-repeatable Read)
        
        if (fromAccount.getBalance().compareTo(amount) >= 0) {
            fromAccount.withdraw(amount);
            Account toAccount = accountRepository.findById(toId);
            toAccount.deposit(amount);
        }
    }
    
    // REPEATABLE_READ: Non-repeatable Read 방지, Phantom Read 가능  
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public BigDecimal calculateTotalBalance(Long customerId) {
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        BigDecimal total = BigDecimal.ZERO;
        
        for (Account account : accounts) {
            total = total.add(account.getBalance());
            // 같은 계좌를 다시 조회해도 같은 값 보장 (Repeatable Read)
        }
        
        // 하지만 중간에 새로운 계좌가 추가될 수 있음 (Phantom Read)
        List<Account> accountsAgain = accountRepository.findByCustomerId(customerId);
        // accounts와 accountsAgain의 크기가 다를 수 있음
        
        return total;
    }
    
    // SERIALIZABLE: 모든 문제 방지, 성능 최저
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void criticalBankingOperation(Long accountId) {
        // 완전한 격리 보장, 성능은 최저
        // 실제로는 거의 사용하지 않음
    }
}
```

### 데이터베이스별 기본 격리 수준
```java
@Configuration
public class DatabaseIsolationConfig {
    
    // MySQL InnoDB: REPEATABLE_READ (기본)
    @Bean
    @Profile("mysql")
    public DataSource mysqlDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://localhost:3306/mydb");
        // MySQL의 REPEATABLE_READ는 Phantom Read도 방지함 (Gap Lock)
        return ds;
    }
    
    // PostgreSQL: READ_COMMITTED (기본)
    @Bean
    @Profile("postgresql") 
    public DataSource postgresDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        return ds;
    }
    
    // Oracle: READ_COMMITTED (기본)
    @Bean
    @Profile("oracle")
    public DataSource oracleDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:oracle:thin:@localhost:1521:XE");
        return ds;
    }
}
```

## Rollback 규칙

### 롤백 조건 세밀 제어
```java
@Service
public class OrderService {
    
    // 기본: RuntimeException과 Error만 롤백
    @Transactional
    public void processOrderDefault(OrderRequest request) throws OrderException {
        orderRepository.save(new Order(request));
        
        // RuntimeException -> 롤백됨
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        
        // Checked Exception -> 롤백 안됨 (커밋됨)
        if (request.getProductId() == null) {
            throw new OrderException("상품 ID가 필요합니다");
        }
    }
    
    // 특정 예외에 대한 롤백 설정  
    @Transactional(rollbackFor = {OrderException.class, SQLException.class})
    public void processOrderWithCustomRollback(OrderRequest request) throws OrderException {
        orderRepository.save(new Order(request));
        
        // OrderException -> 롤백됨 (rollbackFor 설정)
        if (request.getProductId() == null) {
            throw new OrderException("상품 ID가 필요합니다");
        }
    }
    
    // 특정 예외는 롤백하지 않음
    @Transactional(noRollbackFor = {ValidationException.class})
    public void processOrderWithNoRollback(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        
        try {
            validateOrder(order);
        } catch (ValidationException e) {
            // ValidationException은 롤백하지 않음
            // 주문은 저장되고, 검증 실패 로그만 남김
            log.warn("Order validation failed: {}", e.getMessage());
            order.setStatus(OrderStatus.PENDING_VALIDATION);
            orderRepository.save(order); // 상태 업데이트는 커밋됨
        }
    }
    
    // 복합 롤백 규칙
    @Transactional(
        rollbackFor = {PaymentException.class, InventoryException.class},
        noRollbackFor = {NotificationException.class}
    )
    public void processComplexOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        
        try {
            paymentService.processPayment(request.getPaymentInfo()); // PaymentException -> 롤백
            inventoryService.reduceStock(request.getProductId(), request.getQuantity()); // InventoryException -> 롤백
            notificationService.sendOrderConfirmation(order); // NotificationException -> 롤백 안함
            
        } catch (NotificationException e) {
            // 알림 실패는 주문 처리에 영향 없음
            log.warn("Failed to send notification: {}", e.getMessage());
            // 주문은 정상적으로 커밋됨
        }
    }
}
```

### 프로그래매틱 롤백
```java
@Service
public class OrderService {
    
    @Transactional
    public void processOrderWithProgrammaticRollback(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        
        try {
            PaymentResult result = paymentService.processPayment(request.getPaymentInfo());
            
            if (!result.isSuccess()) {
                // 예외를 던지지 않고 직접 롤백 마크
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return;
            }
            
            inventoryService.reduceStock(request.getProductId(), request.getQuantity());
            
        } catch (Exception e) {
            // 선택적 롤백 로직
            if (isRecoverableError(e)) {
                // 복구 가능한 에러는 대체 로직 실행
                processAlternativePayment(request);
            } else {
                // 복구 불가능한 에러는 롤백
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                throw e;
            }
        }
    }
    
    private boolean isRecoverableError(Exception e) {
        return e instanceof TemporaryPaymentException ||
               e instanceof NetworkTimeoutException;
    }
}
```

## readOnly 속성

### 읽기 전용 트랜잭션 최적화
```java
@Service
public class OrderQueryService {
    
    // 읽기 전용 트랜잭션: 성능 최적화
    @Transactional(readOnly = true)
    public List<Order> getOrderHistory(Long customerId) {
        // 1. Hibernate: flush 모드를 MANUAL로 설정하여 dirty checking 스킵
        // 2. 일부 DB: 읽기 전용 연결 사용으로 성능 향상
        // 3. 실수로 데이터 변경 시 예외 발생
        
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }
    
    @Transactional(readOnly = true)
    public OrderStatistics calculateOrderStatistics(Long customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        
        // 읽기 전용이므로 엔티티 변경해도 DB에 반영되지 않음
        BigDecimal totalAmount = orders.stream()
                                      .map(Order::getTotalAmount)
                                      .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return OrderStatistics.builder()
                             .totalOrders(orders.size())
                             .totalAmount(totalAmount)
                             .averageAmount(totalAmount.divide(BigDecimal.valueOf(orders.size())))
                             .build();
    }
    
    // 잘못된 사용: readOnly=true인데 데이터 변경 시도
    @Transactional(readOnly = true)
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId);
        order.setStatus(status); // 실제로는 DB에 반영되지 않음
        // 일부 DB에서는 예외 발생 
    }
}
```

### readOnly 최적화 효과 측정
```java
@Service
public class PerformanceComparisonService {
    
    @Transactional(readOnly = true)
    public List<Order> readOnlyQuery() {
        long startTime = System.currentTimeMillis();
        List<Order> orders = orderRepository.findAll();
        long endTime = System.currentTimeMillis();
        
        log.info("ReadOnly query took {}ms", endTime - startTime);
        return orders;
    }
    
    @Transactional
    public List<Order> readWriteQuery() {
        long startTime = System.currentTimeMillis();
        List<Order> orders = orderRepository.findAll();
        long endTime = System.currentTimeMillis();
        
        log.info("ReadWrite query took {}ms", endTime - startTime);
        return orders;
    }
}
```

## timeout 속성

### 트랜잭션 타임아웃 설정
```java
@Service
public class OrderService {
    
    // 30초 타임아웃
    @Transactional(timeout = 30)
    public void processLargeOrder(OrderRequest request) {
        // 대용량 주문 처리
        // 30초 내에 완료되지 않으면 TransactionTimedOutException 발생
        
        for (OrderItem item : request.getItems()) {
            processOrderItem(item);
            Thread.sleep(100); // 시뮬레이션
        }
    }
    
    // 빠른 작업용 짧은 타임아웃
    @Transactional(timeout = 5)
    public void quickOrderValidation(OrderRequest request) {
        // 5초 내에 완료되어야 하는 빠른 검증
        validateOrderItems(request.getItems());
        validateCustomer(request.getCustomerId());
    }
    
    // 배치 작업용 긴 타임아웃
    @Transactional(timeout = 300) // 5분
    public void processBatchOrders(List<OrderRequest> requests) {
        // 대량 배치 처리
        for (OrderRequest request : requests) {
            processOrder(request);
        }
    }
}
```

### 타임아웃 모니터링
```java
@Aspect
@Component
public class TransactionTimeoutAspect {
    
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object monitorTransactionTimeout(ProceedingJoinPoint joinPoint) throws Throwable {
        Transactional transactional = getTransactionalAnnotation(joinPoint);
        int timeout = transactional.timeout();
        
        if (timeout > 0) {
            long startTime = System.currentTimeMillis();
            
            try {
                Object result = joinPoint.proceed();
                long executionTime = System.currentTimeMillis() - startTime;
                
                // 타임아웃의 80% 이상 소요된 경우 경고
                if (executionTime > timeout * 800) { // 80% of timeout in milliseconds
                    log.warn("Transaction took {}ms, close to timeout of {}s: {}", 
                            executionTime, timeout, joinPoint.getSignature());
                }
                
                return result;
                
            } catch (TransactionTimedOutException e) {
                log.error("Transaction timed out after {}s: {}", 
                         timeout, joinPoint.getSignature());
                throw e;
            }
        }
        
        return joinPoint.proceed();
    }
}
```

## 트랜잭션 동기화와 이벤트

### 트랜잭션 이벤트 리스너
```java
@Component
public class OrderEventListener {
    
    // 트랜잭션 커밋 후에만 실행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 트랜잭션이 성공적으로 커밋된 후에만 실행
        // 메일 발송, 외부 시스템 연동 등
        emailService.sendOrderConfirmation(event.getOrderId());
        externalApiService.notifyOrderCreated(event.getOrderId());
    }
    
    // 트랜잭션 롤백 후 실행
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleOrderFailed(OrderCreatedEvent event) {
        // 트랜잭션이 롤백된 경우에만 실행
        log.error("Order creation failed for order: {}", event.getOrderId());
        alertService.sendFailureAlert(event.getOrderId());
    }
    
    // 트랜잭션 완료 후 실행 (커밋/롤백 무관)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void cleanupOrderProcess(OrderCreatedEvent event) {
        // 성공/실패 무관하게 정리 작업
        cacheService.evictOrderCache(event.getCustomerId());
        metricsService.recordOrderAttempt(event.getOrderId());
    }
}

@Service
public class OrderService {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public Order createOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        
        // 이벤트 발행 (트랜잭션 내에서)
        OrderCreatedEvent event = new OrderCreatedEvent(order.getId(), 
                                                       order.getCustomerId());
        eventPublisher.publishEvent(event);
        
        return order;
    }
}
```

## 트랜잭션 테스팅

### 트랜잭션 동작 테스트
```java
@SpringBootTest
@Transactional
@Rollback(false) // 테스트 후 롤백하지 않음
class TransactionTest {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Test
    void testTransactionCommit() {
        OrderRequest request = createValidOrderRequest();
        
        Order order = orderService.createOrder(request);
        
        // 플러시하여 SQL 실행 확인
        entityManager.flush();
        entityManager.clear();
        
        Order savedOrder = orderRepository.findById(order.getId()).orElse(null);
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
    }
    
    @Test
    void testTransactionRollback() {
        OrderRequest invalidRequest = createInvalidOrderRequest();
        
        assertThatThrownBy(() -> {
            orderService.createOrder(invalidRequest);
        }).isInstanceOf(ValidationException.class);
        
        // 롤백되어 저장되지 않음을 확인
        assertThat(orderRepository.count()).isEqualTo(0);
    }
    
    @Test
    @Commit // 이 테스트만 커밋
    void testTransactionPropagation() {
        // 전파 속성 테스트
        OrderRequest request = createValidOrderRequest();
        
        orderService.createOrderWithIndependentPayment(request);
        
        // 독립적인 트랜잭션 동작 확인
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "@Transactional이 적용되지 않는 경우는?"
**답변 포인트:**
- **private 메서드**: 프록시할 수 없음
- **내부 메서드 호출**: 프록시를 거치지 않음
- **final 메서드/클래스**: CGLIB으로 프록시할 수 없음
- **@Transactional이 없는 클래스**: 프록시가 생성되지 않음

### Q2: "REQUIRES_NEW와 NESTED의 차이점은?"
**답변 포인트:**
- **REQUIRES_NEW**: 완전히 독립적인 새 트랜잭션, 상위 트랜잭션과 무관
- **NESTED**: 중첩 트랜잭션, Savepoint 사용, 상위 트랜잭션에 영향받음
- **롤백 동작**: REQUIRES_NEW는 독립적, NESTED는 부분 롤백 가능
- **지원 DB**: NESTED는 Savepoint를 지원하는 DB에서만 동작

### Q3: "트랜잭션 성능 최적화 방법은?"
**답변 포인트:**
- **readOnly=true**: 읽기 전용 최적화
- **적절한 격리 수준**: 비즈니스 요구사항에 맞는 최소 격리 수준
- **트랜잭션 범위 최소화**: 불필요한 로직은 트랜잭션 밖으로
- **지연 로딩 최적화**: N+1 문제 해결

## 실무 베스트 프랙티스

1. **트랜잭션 범위 최소화**: 비즈니스 로직만 트랜잭션에 포함
2. **읽기 전용 설정**: 조회 메서드는 readOnly=true 사용  
3. **적절한 격리 수준**: 성능과 일관성의 균형점 찾기
4. **예외 처리**: 롤백 조건을 명확히 정의
5. **테스트**: 트랜잭션 동작을 반드시 테스트로 검증

@Transactional은 Spring의 핵심 기능 중 하나로, 올바른 이해와 사용이 데이터 일관성과 애플리케이션 안정성에 매우 중요합니다.