# N+1 문제의 원인과 다양한 해결 방법

## N+1 문제란?

### 문제 발생 원리
```java
// N+1 문제가 발생하는 대표적인 예시
@Entity
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String orderNumber;
    
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> orderItems = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
}

@Entity
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    
    private int quantity;
    private BigDecimal price;
}

// 문제가 되는 코드
@Service
public class OrderService {
    
    public List<OrderDto> getAllOrdersWithItems() {
        // 1. 모든 주문 조회 (1번의 쿼리)
        List<Order> orders = orderRepository.findAll();
        
        List<OrderDto> result = new ArrayList<>();
        for (Order order : orders) {
            OrderDto dto = new OrderDto();
            dto.setOrderNumber(order.getOrderNumber());
            
            // 2. 각 주문별로 주문 아이템 조회 (N번의 쿼리)
            List<OrderItem> items = order.getOrderItems(); // 지연 로딩 발생!
            dto.setItemCount(items.size());
            
            // 3. 각 주문별로 고객 정보 조회 (추가 N번의 쿼리)
            Customer customer = order.getCustomer(); // 또 다른 지연 로딩!
            dto.setCustomerName(customer.getName());
            
            result.add(dto);
        }
        return result;
    }
}

/*
실행되는 SQL:
1. SELECT * FROM orders;                    -- 1번
2. SELECT * FROM order_items WHERE order_id = 1; -- N번 (주문 수만큼)
3. SELECT * FROM order_items WHERE order_id = 2;
4. SELECT * FROM order_items WHERE order_id = 3;
...
5. SELECT * FROM customers WHERE id = 1;   -- 추가 N번
6. SELECT * FROM customers WHERE id = 2;
...
총 1 + N + N = 1 + 2N번의 쿼리 실행!
*/
```

## 해결 방법 1: Fetch Join 사용

### 기본 Fetch Join
```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // 1. 단일 연관관계 Fetch Join
    @Query("SELECT o FROM Order o JOIN FETCH o.customer")
    List<Order> findAllWithCustomer();
    
    // 2. 다중 연관관계 Fetch Join (주의: 카테시안 곱 발생 가능)
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN FETCH o.customer " +
           "JOIN FETCH o.orderItems")
    List<Order> findAllWithCustomerAndItems();
    
    // 3. 조건부 Fetch Join
    @Query("SELECT o FROM Order o " +
           "JOIN FETCH o.customer c " +
           "WHERE c.customerGrade = :grade")
    List<Order> findOrdersByCustomerGrade(@Param("grade") CustomerGrade grade);
}

@Service
public class OrderService {
    
    public List<OrderDto> getAllOrdersOptimized() {
        // 1번의 조인 쿼리로 모든 데이터 조회
        List<Order> orders = orderRepository.findAllWithCustomerAndItems();
        
        return orders.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
    }
    
    private OrderDto convertToDto(Order order) {
        return OrderDto.builder()
                      .orderNumber(order.getOrderNumber())
                      .customerName(order.getCustomer().getName()) // 추가 쿼리 없음
                      .itemCount(order.getOrderItems().size())     // 추가 쿼리 없음
                      .build();
    }
}
```

### Fetch Join의 한계와 해결책
```java
// 문제: 둘 이상의 컬렉션을 Fetch Join하면 카테시안 곱 발생
@Query("SELECT DISTINCT o FROM Order o " +
       "JOIN FETCH o.orderItems " +
       "JOIN FETCH o.deliveries") // MultipleBagFetchException 발생!
List<Order> findAllWithItemsAndDeliveries();

// 해결책 1: 단계별 Fetch Join
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems")
    List<Order> findAllWithItems();
    
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.deliveries WHERE o IN :orders")
    List<Order> findWithDeliveries(@Param("orders") List<Order> orders);
}

@Service
public class OrderService {
    
    public List<OrderDto> getAllOrdersWithItemsAndDeliveries() {
        // 1단계: 주문과 주문아이템 조회
        List<Order> orders = orderRepository.findAllWithItems();
        
        // 2단계: 주문과 배송정보 조회
        List<Order> ordersWithDeliveries = orderRepository.findWithDeliveries(orders);
        
        return ordersWithDeliveries.stream()
                                  .map(this::convertToDto)
                                  .collect(Collectors.toList());
    }
}

// 해결책 2: @BatchSize 사용
@Entity
public class Order {
    
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @BatchSize(size = 100) // 100개씩 IN 절로 조회
    private List<OrderItem> orderItems = new ArrayList<>();
    
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<Delivery> deliveries = new ArrayList<>();
}
```

## 해결 방법 2: @EntityGraph 사용

### @EntityGraph 기본 사용법
```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // 1. attributePaths로 로딩할 연관관계 지정
    @EntityGraph(attributePaths = {"customer", "orderItems"})
    List<Order> findAll();
    
    // 2. 메서드별로 다른 EntityGraph 적용
    @EntityGraph(attributePaths = {"customer"})
    List<Order> findByOrderDateBetween(LocalDateTime start, LocalDateTime end);
    
    // 3. 중첩된 연관관계 로딩
    @EntityGraph(attributePaths = {"orderItems.product", "customer.address"})
    List<Order> findByStatus(OrderStatus status);
    
    // 4. type = LOAD vs FETCH
    @EntityGraph(attributePaths = {"customer"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Order> findWithCustomerById(Long id);
    
    @EntityGraph(attributePaths = {"orderItems"}, type = EntityGraph.EntityGraphType.FETCH)
    Optional<Order> findWithItemsById(Long id);
}
```

### Named EntityGraph 사용
```java
@Entity
@NamedEntityGraphs({
    @NamedEntityGraph(
        name = "Order.withCustomer",
        attributeNodes = @NamedAttributeNode("customer")
    ),
    @NamedEntityGraph(
        name = "Order.withItems",
        attributeNodes = @NamedAttributeNode("orderItems")
    ),
    @NamedEntityGraph(
        name = "Order.withCustomerAndItems",
        attributeNodes = {
            @NamedAttributeNode("customer"),
            @NamedAttributeNode("orderItems")
        }
    ),
    @NamedEntityGraph(
        name = "Order.detailed",
        attributeNodes = {
            @NamedAttributeNode("customer"),
            @NamedAttributeNode(value = "orderItems", subgraph = "orderItems.product")
        },
        subgraphs = @NamedSubgraph(
            name = "orderItems.product",
            attributeNodes = @NamedAttributeNode("product")
        )
    )
})
public class Order {
    // 엔티티 필드들...
}

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @EntityGraph("Order.withCustomer")
    List<Order> findByCustomerId(Long customerId);
    
    @EntityGraph("Order.withCustomerAndItems")
    Optional<Order> findById(Long id);
    
    @EntityGraph("Order.detailed")
    List<Order> findByOrderNumberLike(String pattern);
}
```

## 해결 방법 3: Batch Size 최적화

### @BatchSize 어노테이션
```java
@Entity
public class Order {
    
    // 1. 컬렉션에 @BatchSize 적용
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @BatchSize(size = 10) // 10개씩 IN 절로 조회
    private List<OrderItem> orderItems = new ArrayList<>();
    
    // 2. 단일 연관관계에도 적용 가능
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @BatchSize(size = 20)
    private Customer customer;
}

// 전역 배치 사이즈 설정
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100 # 전역 기본값 설정
```

### 배치 사이즈 동작 확인
```java
@Service
public class OrderService {
    
    public List<OrderDto> testBatchSize() {
        // 100개의 주문 조회 (1번의 쿼리)
        List<Order> orders = orderRepository.findAll();
        
        List<OrderDto> result = new ArrayList<>();
        for (Order order : orders) {
            // @BatchSize(size=10) 설정으로 인해
            // 10개씩 묶어서 IN 절로 조회
            // 총 100/10 = 10번의 쿼리 실행
            List<OrderItem> items = order.getOrderItems();
            
            OrderDto dto = new OrderDto();
            dto.setItemCount(items.size());
            result.add(dto);
        }
        return result;
    }
}

/*
실행되는 SQL:
1. SELECT * FROM orders; -- 1번
2. SELECT * FROM order_items WHERE order_id IN (1,2,3,4,5,6,7,8,9,10); -- 10번
3. SELECT * FROM order_items WHERE order_id IN (11,12,13,14,15,16,17,18,19,20);
...
총 1 + 10 = 11번의 쿼리 (기존 101번에서 대폭 감소!)
*/
```

## 해결 방법 4: Projection과 DTO 사용

### Interface-based Projection
```java
// 1. Closed Projection (필요한 필드만 선언)
public interface OrderSummary {
    String getOrderNumber();
    LocalDateTime getOrderDate();
    String getCustomerName(); // customer.name
    Integer getItemCount();    // orderItems.size()
    
    // SpEL 표현식 사용 가능
    @Value("#{target.orderItems.size()}")
    Integer getItemCount();
    
    // 중첩 프로젝션
    CustomerSummary getCustomer();
    
    interface CustomerSummary {
        String getName();
        String getEmail();
    }
}

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Native Query with Projection
    @Query("SELECT o.orderNumber as orderNumber, " +
           "       o.orderDate as orderDate, " +
           "       c.name as customerName, " +
           "       COUNT(oi) as itemCount " +
           "FROM Order o " +
           "JOIN o.customer c " +
           "LEFT JOIN o.orderItems oi " +
           "GROUP BY o.id, o.orderNumber, o.orderDate, c.name")
    List<OrderSummary> findOrderSummaries();
    
    // 조건부 프로젝션
    List<OrderSummary> findByOrderDateBetween(LocalDateTime start, LocalDateTime end);
}
```

### Class-based Projection (DTO)
```java
// DTO 클래스
public class OrderSummaryDto {
    private String orderNumber;
    private LocalDateTime orderDate;
    private String customerName;
    private Integer itemCount;
    
    // 생성자 기반 프로젝션
    public OrderSummaryDto(String orderNumber, LocalDateTime orderDate, 
                          String customerName, Long itemCount) {
        this.orderNumber = orderNumber;
        this.orderDate = orderDate;
        this.customerName = customerName;
        this.itemCount = itemCount.intValue();
    }
    
    // getters and setters...
}

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT new com.example.dto.OrderSummaryDto(" +
           "o.orderNumber, o.orderDate, c.name, COUNT(oi)) " +
           "FROM Order o " +
           "JOIN o.customer c " +
           "LEFT JOIN o.orderItems oi " +
           "GROUP BY o.id, o.orderNumber, o.orderDate, c.name")
    List<OrderSummaryDto> findOrderSummaryDtos();
}
```

### Dynamic Projection
```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // 제네릭을 사용한 동적 프로젝션
    <T> List<T> findByCustomerId(Long customerId, Class<T> type);
}

@Service
public class OrderService {
    
    public List<OrderSummary> getOrderSummaries(Long customerId) {
        return orderRepository.findByCustomerId(customerId, OrderSummary.class);
    }
    
    public List<OrderSummaryDto> getOrderSummaryDtos(Long customerId) {
        return orderRepository.findByCustomerId(customerId, OrderSummaryDto.class);
    }
}
```

## 해결 방법 5: QueryDSL과 커스텀 쿼리

### QueryDSL을 활용한 최적화
```java
@Repository
public class OrderRepositoryCustomImpl implements OrderRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private final JPAQueryFactory queryFactory;
    
    public OrderRepositoryCustomImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }
    
    @Override
    public List<OrderDto> findOrdersWithDetails(OrderSearchCondition condition) {
        QOrder order = QOrder.order;
        QCustomer customer = QCustomer.customer;
        QOrderItem orderItem = QOrderItem.orderItem;
        QProduct product = QProduct.product;
        
        // 서브쿼리를 활용한 효율적인 조회
        return queryFactory
            .select(Projections.constructor(OrderDto.class,
                order.id,
                order.orderNumber,
                order.orderDate,
                customer.name,
                ExpressionUtils.as(
                    JPAExpressions.select(orderItem.count())
                                  .from(orderItem)
                                  .where(orderItem.order.eq(order)),
                    "itemCount"
                ),
                ExpressionUtils.as(
                    JPAExpressions.select(orderItem.price.sum())
                                  .from(orderItem)
                                  .where(orderItem.order.eq(order)),
                    "totalAmount"
                )
            ))
            .from(order)
            .join(order.customer, customer)
            .where(buildWhereClause(condition))
            .orderBy(order.orderDate.desc())
            .fetch();
    }
    
    // 복잡한 조건의 N+1 해결
    @Override
    public List<Order> findOrdersWithItemsAndProducts(List<Long> orderIds) {
        QOrder order = QOrder.order;
        QOrderItem orderItem = QOrderItem.orderItem;
        QProduct product = QProduct.product;
        
        // Fetch Join을 사용한 한 번의 쿼리로 모든 데이터 조회
        return queryFactory
            .selectFrom(order)
            .distinct()
            .leftJoin(order.orderItems, orderItem).fetchJoin()
            .leftJoin(orderItem.product, product).fetchJoin()
            .where(order.id.in(orderIds))
            .fetch();
    }
}
```

## 해결 방법 6: 2차 캐시 활용

### Hibernate 2차 캐시 설정
```java
// application.yml
spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
        javax:
          cache:
            provider: org.ehcache.jsr107.EhcacheCachingProvider

@Entity
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Product {
    @Id
    private Long id;
    private String name;
    private BigDecimal price;
    // ...
}

@Entity
public class OrderItem {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private Product product; // 캐시된 Product 사용
}
```

### 쿼리 캐시 사용
```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    @Query("SELECT p FROM Product p WHERE p.category = :category")
    List<Product> findByCategoryWithCache(@Param("category") String category);
}
```

## 성능 측정과 모니터링

### 쿼리 로깅 설정
```yaml
# application.yml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.hibernate.orm.jdbc.bind: TRACE

spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        generate_statistics: true
```

### 성능 측정 코드
```java
@Service
public class OrderPerformanceService {
    
    @Autowired
    private SessionFactory sessionFactory;
    
    public void comparePerformance() {
        Statistics stats = sessionFactory.getStatistics();
        stats.clear();
        
        // N+1 문제가 있는 코드
        long startTime = System.currentTimeMillis();
        List<Order> orders1 = orderRepository.findAll();
        orders1.forEach(order -> {
            order.getOrderItems().size(); // 지연 로딩 발생
            order.getCustomer().getName(); // 지연 로딩 발생
        });
        long endTime1 = System.currentTimeMillis();
        
        log.info("N+1 Problem - Time: {}ms, Queries: {}", 
                endTime1 - startTime, stats.getQueryExecutionCount());
        
        stats.clear();
        
        // 최적화된 코드  
        startTime = System.currentTimeMillis();
        List<Order> orders2 = orderRepository.findAllWithCustomerAndItems();
        orders2.forEach(order -> {
            order.getOrderItems().size(); // 이미 로딩됨
            order.getCustomer().getName(); // 이미 로딩됨
        });
        long endTime2 = System.currentTimeMillis();
        
        log.info("Optimized - Time: {}ms, Queries: {}", 
                endTime2 - startTime, stats.getQueryExecutionCount());
    }
}
```

### N+1 문제 자동 감지
```java
@Component
public class NPlusOneDetector {
    
    private final AtomicInteger queryCount = new AtomicInteger(0);
    private final ThreadLocal<Integer> requestQueryCount = new ThreadLocal<>();
    
    @EventListener
    public void handleQueryExecution(QueryExecutionEvent event) {
        queryCount.incrementAndGet();
        Integer currentCount = requestQueryCount.get();
        requestQueryCount.set(currentCount == null ? 1 : currentCount + 1);
    }
    
    @PreDestroy
    public void checkForNPlusOne() {
        Integer count = requestQueryCount.get();
        if (count != null && count > 10) { // 임계값 설정
            log.warn("Potential N+1 problem detected. Query count: {}", count);
        }
        requestQueryCount.remove();
    }
}
```

## 최적화 전략 선택 가이드

### 상황별 최적 해결책
```java
// 1. 단순한 1:N 관계 - Fetch Join
@Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") Long id);

// 2. 복잡한 다중 관계 - @BatchSize
@Entity
public class Order {
    @OneToMany(mappedBy = "order")
    @BatchSize(size = 50)
    private List<OrderItem> orderItems;
}

// 3. 읽기 전용 화면 - Projection
@Query("SELECT new OrderSummaryDto(o.orderNumber, c.name, COUNT(oi)) " +
       "FROM Order o JOIN o.customer c LEFT JOIN o.orderItems oi " +
       "GROUP BY o.id, o.orderNumber, c.name")
List<OrderSummaryDto> findOrderSummaries();

// 4. 동적 조건이 많은 경우 - QueryDSL
public List<Order> findOrdersWithConditions(OrderSearchCondition condition) {
    return queryFactory
        .selectFrom(order)
        .leftJoin(order.customer, customer).fetchJoin()
        .where(buildDynamicConditions(condition))
        .fetch();
}
```

## 실무에서 Fetch Join을 더 많이 사용하는 이유

1. 명시성과 가독성

   ```java
        // Fetch Join - 쿼리가 명확하게 보임
        @Query("SELECT o FROM Order o JOIN FETCH o.customer JOIN FETCH o.orderItems")                                   
        List<Order> findAllWithDetails();
        
        // @EntityGraph - 어떤 쿼리가 실행될지 예측하기 어려움
        @EntityGraph(attributePaths = {"customer", "orderItems"})                                                       
        List<Order> findAll();
    ``` 
   
Fetch Join이 선호되는 이유:
- JPQL 쿼리로 명시적으로 작성되어 의도가 명확함
- 실행될 SQL을 예측하기 쉬움
- 코드 리뷰 시 로딩 전략을 한눈에 파악 가능

2. 세밀한 제어 가능
    ```java
    // Fetch Join - WHERE, ORDER BY 등 조건 추가 가능
    @Query("SELECT DISTINCT o FROM Order o " +
    "JOIN FETCH o.customer c " +
    "JOIN FETCH o.orderItems oi " +
    "WHERE c.status = 'ACTIVE' " +
    "AND oi.quantity > 0 " +
    "ORDER BY o.createdAt DESC")
    List<Order> findActiveOrdersWithItems();
    
    // @EntityGraph - 복잡한 조건 처리가 어려움
    @EntityGraph(attributePaths = {"customer", "orderItems"})                                                       
    @Query("SELECT o FROM Order o WHERE o.customer.status = 'ACTIVE'")                                              
    List<Order> findActiveOrders(); // orderItems 필터링 불가
    ``` 
3. 성능 최적화 가능성

// Fetch Join - LEFT/INNER JOIN 선택 가능
@Query("SELECT o FROM Order o " +
"INNER JOIN FETCH o.customer " +        // INNER JOIN                                                    
"LEFT JOIN FETCH o.orderItems")         // LEFT JOIN                                                     
List<Order> findOrders();

// @EntityGraph - 항상 LEFT OUTER JOIN 사용
@EntityGraph(attributePaths = {"customer", "orderItems"})                                                       
List<Order> findAll(); // 무조건 LEFT OUTER JOIN

성능 차이:
- Fetch Join은 상황에 따라 INNER/LEFT JOIN 선택 가능
- @EntityGraph는 항상 LEFT OUTER JOIN 사용 → 불필요한 데수 있음

4. 쿼리 최적화 도구와의 호환성

// QueryDSL과 함께 사용하기 좋음
public List<Order> findOrdersOptimized(OrderSearchCondition condition) {                                        
return queryFactory
.selectFrom(order)
.leftJoin(order.customer, customer).fetchJoin()
.leftJoin(order.orderItems, orderItem).fetchJoin()
.where(
statusEq(condition.getStatus()),
dateGoe(condition.getStartDate())
)
.fetch();
}

  ---
그렇다면 @EntityGraph는 언제 사용하나?

1. 간단한 조회에서 코드 간결성

// 조건 없이 단순 조회 시 코드가 간결함
@EntityGraph(attributePaths = {"customer"})
Optional<Order> findById(Long id);

// Fetch Join으로 작성하면
@Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.id = :id")                                          
Optional<Order> findByIdWithCustomer(@Param("id") Long id);

2. Spring Data JPA 쿼리 메서드 활용

// 메서드 이름 기반 쿼리 + @EntityGraph
@EntityGraph(attributePaths = {"customer", "orderItems"})                                                       
List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

// Fetch Join은 @Query 필수
@Query("SELECT o FROM Order o " +
"JOIN FETCH o.customer " +
"WHERE o.createdAt BETWEEN :start AND :end")
List<Order> findByCreatedAtBetween(@Param("start") LocalDateTime start,                                         
@Param("end") LocalDateTime end);

3. Named EntityGraph로 재사용성 향상

@Entity
@NamedEntityGraph(
name = "Order.withDetails",
attributeNodes = {
@NamedAttributeNode("customer"),
@NamedAttributeNode("orderItems")
}
)
public class Order { ... }

// 여러 곳에서 재사용
@EntityGraph("Order.withDetails")
List<Order> findByStatus(OrderStatus status);

@EntityGraph("Order.withDetails")
List<Order> findByCustomerId(Long customerId);

  ---
실무 권장 사항

✅ Fetch Join을 사용하는 경우 (70-80%)

1. 복잡한 조건이 있는 조회
2. JOIN 타입 제어가 필요한 경우
3. WHERE, ORDER BY 등 추가 조건 필요
4. QueryDSL과 함께 사용
5. 성능 최적화가 중요한 핵심 쿼리

// 실무에서 가장 많이 사용되는 패턴
@Query("SELECT DISTINCT o FROM Order o " +
"JOIN FETCH o.customer c " +
"LEFT JOIN FETCH o.orderItems oi " +
"WHERE o.status = :status " +
"AND o.createdAt >= :startDate")
List<Order> findActiveOrders(@Param("status") OrderStatus status,                                               
@Param("startDate") LocalDateTime startDate);

✅ @EntityGraph를 사용하는 경우 (20-30%)

1. 단순 조회 (조건 없음)
2. Spring Data JPA 메서드 쿼리 활용
3. 코드 간결성이 중요한 경우
4. Named EntityGraph로 재사용성 확보

// 간단한 ID 조회
@EntityGraph(attributePaths = {"customer", "orderItems"})                                                       
Optional<Order> findById(Long id);

// Spring Data 메서드 쿼리 활용
@EntityGraph(attributePaths = {"customer"})
List<Order> findByCustomerEmail(String email);

  ---
성능 비교

두 방식의 성능 차이는 거의 없습니다. 둘 다 결국 같은 SQL을 생성하기 때문입니다.

-- 두 방식 모두 동일한 SQL 생성
SELECT o.*, c.*, oi.*
FROM orders o
LEFT OUTER JOIN customers c ON o.customer_id = c.id
LEFT OUTER JOIN order_items oi ON o.id = oi.order_id

차이점은:
- Fetch Join: INNER/LEFT JOIN 선택 가능
- @EntityGraph: 항상 LEFT OUTER JOIN

  ---
면접 답변 정리

질문: "N+1 해결을 위해 Fetch Join과 @EntityGraph 중 어떤나요?"

모범 답변:

"실무에서는 Fetch Join을 70-80% 정도 더 많이 사용합니다.

주된 이유는:
1. 명시성: JPQL로 명확하게 작성되어 의도가 분명함
2. 세밀한 제어: WHERE, ORDER BY, INNER/LEFT JOIN 선택 등적화 가능
3. QueryDSL 호환성: 동적 쿼리 작성 시 유리

다만 @EntityGraph도 적재적소에 사용합니다:
- 단순 ID 조회 같이 조건이 없는 경우
- Spring Data JPA 메서드 쿼리와 함께 사용할 때
- Named EntityGraph로 재사용성을 높일 때

결론적으로 복잡한 비즈니스 쿼리는 Fetch Join, 간단한 조EntityGraph를 사용하는 것이 일반적인 패턴입니다."

  ---
추가 꼬리질문 대비

Q: "둘 다 카테시안 곱 문제가 있나요?"
- 네, 두 방식 모두 2개 이상의 컬렉션을 Fetch할 때 카테시발생
- 해결책: @BatchSize 또는 다단계 조회 사용

Q: "어떤 게 더 빠른가요?"
- 성능 차이는 거의 없음
- Fetch Join이 INNER JOIN 사용 시 약간 더 빠를 수 있음

## 인터뷰 꼬리질문 대비

### Q1: "Fetch Join과 @EntityGraph의 차이점은?"
**답변 포인트:**
- **Fetch Join**: JPQL에서 명시적으로 작성, 컴파일 타임에 결정
- **@EntityGraph**: 어노테이션 기반, 런타임에 적용
- **유연성**: @EntityGraph가 더 유연함 (동적 적용 가능)
- **성능**: 둘 다 비슷하지만 Fetch Join이 약간 더 명시적

### Q2: "@BatchSize는 어떻게 동작하나요?"
**답변 포인트:**
- **IN 절 사용**: 여러 ID를 IN 절로 한 번에 조회
- **지연 로딩 시점**: 실제 컬렉션 접근 시 배치로 로딩
- **메모리 vs 성능**: 배치 크기는 메모리와 성능의 트레이드오프
- **최적 크기**: 보통 10-100 사이, 데이터 특성에 따라 조정

### Q3: "N+1 문제를 런타임에 감지할 수 있나요?"
**답변 포인트:**
- **Hibernate Statistics**: 쿼리 실행 횟수 모니터링
- **P6Spy**: SQL 로깅 및 분석 도구
- **Custom Interceptor**: 쿼리 실행을 가로채서 분석
- **APM 도구**: 애플리케이션 성능 모니터링 도구 활용

## 실무 베스트 프랙티스

1. **예방이 최선**: 설계 단계에서 연관관계와 조회 패턴 고려
2. **적절한 도구 선택**: 상황에 맞는 해결책 적용
3. **성능 테스트**: 실제 데이터 볼륨으로 성능 테스트 필수
4. **모니터링**: 운영 환경에서 지속적인 쿼리 성능 모니터링
5. **점진적 최적화**: 모든 곳에 적용하지 말고 병목점 위주로 최적화
