# 대용량 데이터 처리와 페이징 최적화

## 대용량 데이터 처리 개요

### 대용량 데이터 처리의 특징
대용량 데이터 처리에서는 **메모리 효율성**, **처리 속도**, **시스템 안정성**이 핵심입니다. 일반적인 CRUD 방식으로는 성능 문제와 메모리 부족이 발생할 수 있습니다.

### 주요 고려사항
```sql
-- 문제가 되는 접근 방식
SELECT * FROM large_table;  -- 수백만 건의 데이터를 한번에 로딩
UPDATE large_table SET status = 'processed';  -- 모든 데이터를 한번에 업데이트

-- 개선된 접근 방식: 청크 단위 처리
SELECT * FROM large_table LIMIT 1000 OFFSET 0;
SELECT * FROM large_table LIMIT 1000 OFFSET 1000;
-- 또는 커서 기반 페이징 사용
```

## 효율적인 페이징 전략

### 1. 오프셋 기반 페이징의 문제점
```sql
-- 성능이 급격히 저하되는 쿼리
SELECT * FROM orders 
ORDER BY created_at DESC 
LIMIT 20 OFFSET 1000000;  -- 100만 번째 페이지

-- 실행 계획 분석
EXPLAIN SELECT * FROM orders 
ORDER BY created_at DESC 
LIMIT 20 OFFSET 1000000;

/*
문제점:
1. MySQL은 처음부터 1,000,020개 레코드를 정렬해야 함
2. 1,000,000개 레코드를 건너뛰는 과정에서 비효율 발생
3. 페이지 번호가 클수록 성능이 기하급수적으로 저하
*/
```

```java
// Spring Data JPA의 문제가 있는 페이징
@RestController
public class OrderController {
    
    @GetMapping("/orders")
    public Page<Order> getOrders(@PageableDefault(size = 20) Pageable pageable) {
        // 깊은 페이지에서 성능 저하 발생
        return orderRepository.findAll(pageable);
    }
}

// 성능 테스트 결과
/*
페이지 1 (OFFSET 0): 10ms
페이지 100 (OFFSET 2000): 50ms  
페이지 1000 (OFFSET 20000): 200ms
페이지 10000 (OFFSET 200000): 2000ms
페이지 50000 (OFFSET 1000000): 15000ms
*/
```

### 2. 커서 기반 페이징 (Cursor-based Pagination)
```sql
-- 커서 기반 페이징의 기본 원리
-- 첫 번째 페이지
SELECT * FROM orders 
ORDER BY created_at DESC, id DESC 
LIMIT 20;

-- 다음 페이지 (마지막 레코드의 created_at, id를 커서로 사용)
SELECT * FROM orders 
WHERE (created_at < '2024-01-15 10:30:45' OR 
       (created_at = '2024-01-15 10:30:45' AND id < 12345))
ORDER BY created_at DESC, id DESC 
LIMIT 20;

-- 인덱스 생성 (성능 최적화)
CREATE INDEX idx_orders_created_id ON orders(created_at DESC, id DESC);
```

```java
// Spring Data JPA를 이용한 커서 기반 페이징 구현
@Repository
public class OrderRepository extends JpaRepository<Order, Long> {
    
    // 첫 번째 페이지
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC, o.id DESC")
    List<Order> findFirstPage(Pageable pageable);
    
    // 다음 페이지 (커서 기반)
    @Query("SELECT o FROM Order o " +
           "WHERE o.createdAt < :cursorDate OR " +
           "(o.createdAt = :cursorDate AND o.id < :cursorId) " +
           "ORDER BY o.createdAt DESC, o.id DESC")
    List<Order> findNextPage(@Param("cursorDate") LocalDateTime cursorDate,
                           @Param("cursorId") Long cursorId,
                           Pageable pageable);
    
    // 이전 페이지 (역방향 커서)
    @Query("SELECT o FROM Order o " +
           "WHERE o.createdAt > :cursorDate OR " +
           "(o.createdAt = :cursorDate AND o.id > :cursorId) " +
           "ORDER BY o.createdAt ASC, o.id ASC")
    List<Order> findPreviousPage(@Param("cursorDate") LocalDateTime cursorDate,
                               @Param("cursorId") Long cursorId,
                               Pageable pageable);
}

@RestController
public class OrderController {
    
    @GetMapping("/orders")
    public CursorPageResponse<Order> getOrders(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        
        List<Order> orders;
        
        if (cursor == null) {
            // 첫 번째 페이지
            orders = orderRepository.findFirstPage(PageRequest.of(0, size));
        } else {
            // 커서 디코딩
            CursorInfo cursorInfo = CursorInfo.decode(cursor);
            orders = orderRepository.findNextPage(
                cursorInfo.getCreatedAt(),
                cursorInfo.getId(),
                PageRequest.of(0, size)
            );
        }
        
        return CursorPageResponse.of(orders, size);
    }
}

// 커서 정보 클래스
public class CursorInfo {
    private LocalDateTime createdAt;
    private Long id;
    
    public static String encode(LocalDateTime createdAt, Long id) {
        // Base64 인코딩 등을 사용하여 커서 문자열 생성
        String cursorData = createdAt.toString() + ":" + id;
        return Base64.getEncoder().encodeToString(cursorData.getBytes());
    }
    
    public static CursorInfo decode(String cursor) {
        // 커서 문자열을 디코딩하여 객체 생성
        String decoded = new String(Base64.getDecoder().decode(cursor));
        String[] parts = decoded.split(":");
        return new CursorInfo(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
    }
}
```

### 3. 하이브리드 페이징 (성능과 UX 균형)
```java
// 초기 페이지는 오프셋, 깊은 페이지는 커서 기반
@Service
public class HybridPagingService {
    
    private static final int OFFSET_THRESHOLD = 100; // 100페이지까지는 오프셋 사용
    
    public PageResponse<Order> getOrders(int page, int size, String cursor) {
        if (page <= OFFSET_THRESHOLD && cursor == null) {
            // 얕은 페이지: 오프셋 기반 페이징 (UX 좋음)
            Page<Order> result = orderRepository.findAll(PageRequest.of(page - 1, size));
            return PageResponse.fromPage(result);
            
        } else {
            // 깊은 페이지: 커서 기반 페이징 (성능 좋음)
            List<Order> orders = getOrdersByCursor(cursor, size);
            return PageResponse.fromCursor(orders, size);
        }
    }
}
```

## 배치 처리 최적화

### 1. JDBC 배치 처리
```java
@Repository
public class BulkOrderRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // 대용량 INSERT - 배치 처리
    public void bulkInsertOrders(List<Order> orders) {
        String sql = "INSERT INTO orders (customer_id, total_amount, status, created_at) VALUES (?, ?, ?, ?)";
        
        jdbcTemplate.batchUpdate(sql, orders, 1000, (ps, order) -> {
            ps.setLong(1, order.getCustomerId());
            ps.setBigDecimal(2, order.getTotalAmount());
            ps.setString(3, order.getStatus().name());
            ps.setTimestamp(4, Timestamp.valueOf(order.getCreatedAt()));
        });
    }
    
    // 대용량 UPDATE - 청크 단위 처리
    public void bulkUpdateOrderStatus(OrderStatus fromStatus, OrderStatus toStatus) {
        String countSql = "SELECT COUNT(*) FROM orders WHERE status = ?";
        int totalCount = jdbcTemplate.queryForObject(countSql, Integer.class, fromStatus.name());
        
        String updateSql = "UPDATE orders SET status = ?, updated_at = CURRENT_TIMESTAMP " +
                          "WHERE status = ? LIMIT ?";
        
        int batchSize = 1000;
        int processedCount = 0;
        
        while (processedCount < totalCount) {
            int updatedRows = jdbcTemplate.update(updateSql, 
                toStatus.name(), fromStatus.name(), batchSize);
            
            processedCount += updatedRows;
            
            // 다른 트랜잭션에게 기회 제공
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            if (updatedRows == 0) break; // 더 이상 업데이트할 레코드 없음
        }
    }
}
```

### 2. JPA 배치 처리 최적화
```java
@Service
@Transactional
public class BatchProcessingService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Hibernate 배치 설정
    public void bulkInsertWithBatch(List<Order> orders) {
        int batchSize = 50; // hibernate.jdbc.batch_size와 동일하게 설정
        
        for (int i = 0; i < orders.size(); i++) {
            entityManager.persist(orders.get(i));
            
            if (i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear(); // 1차 캐시 클리어로 메모리 절약
            }
        }
        
        entityManager.flush();
        entityManager.clear();
    }
    
    // JPQL 벌크 연산 활용
    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus WHERE o.status = :oldStatus AND o.createdAt < :cutoffDate")
    int bulkUpdateOrderStatus(@Param("newStatus") OrderStatus newStatus,
                            @Param("oldStatus") OrderStatus oldStatus,
                            @Param("cutoffDate") LocalDateTime cutoffDate);
    
    // 청크 단위 조회 및 처리
    public void processOrdersInChunks(OrderStatus status, int chunkSize) {
        Long lastId = 0L;
        List<Order> orders;
        
        do {
            orders = entityManager.createQuery(
                "SELECT o FROM Order o WHERE o.id > :lastId AND o.status = :status " +
                "ORDER BY o.id ASC", Order.class)
                .setParameter("lastId", lastId)
                .setParameter("status", status)
                .setMaxResults(chunkSize)
                .getResultList();
            
            if (!orders.isEmpty()) {
                // 비즈니스 로직 처리
                processOrders(orders);
                
                lastId = orders.get(orders.size() - 1).getId();
                
                // 메모리 관리
                entityManager.clear();
            }
            
        } while (!orders.isEmpty());
    }
}
```

### 3. 스트리밍 처리
```java
@Repository
public class StreamingRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // ResultSet을 스트림으로 처리 (메모리 효율적)
    public void processLargeDatasetWithStream() {
        String sql = "SELECT id, customer_id, total_amount FROM orders WHERE status = ?";
        
        jdbcTemplate.query(sql, rs -> {
            // 각 행마다 콜백 실행 (메모리에 전체 결과를 올리지 않음)
            Long id = rs.getLong("id");
            Long customerId = rs.getLong("customer_id");
            BigDecimal totalAmount = rs.getBigDecimal("total_amount");
            
            // 개별 레코드 처리
            processOrder(id, customerId, totalAmount);
            
        }, "COMPLETED");
    }
    
    // Java 8 Stream API 활용
    @Transactional(readOnly = true)
    public void processOrdersWithJavaStream() {
        try (Stream<Order> orderStream = orderRepository.findByStatusOrderById(OrderStatus.PENDING)) {
            orderStream
                .filter(order -> order.getTotalAmount().compareTo(BigDecimal.valueOf(1000)) > 0)
                .map(this::enrichOrderData)
                .forEach(this::processHighValueOrder);
        }
    }
}

// Repository에서 Stream 반환
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @QueryHints(@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE, value = "1000"))
    @Query("SELECT o FROM Order o WHERE o.status = :status ORDER BY o.id")
    Stream<Order> findByStatusOrderById(@Param("status") OrderStatus status);
}
```

## 인덱스 기반 최적화

### 1. 커버링 인덱스 활용
```sql
-- 페이징에 최적화된 커버링 인덱스
CREATE INDEX idx_orders_status_created_covering 
ON orders(status, created_at DESC, id DESC, customer_id, total_amount);

-- 커버링 인덱스를 활용한 효율적인 페이징
SELECT customer_id, total_amount, created_at
FROM orders 
WHERE status = 'COMPLETED'
  AND (created_at < '2024-01-15 10:30:45' OR 
       (created_at = '2024-01-15 10:30:45' AND id < 12345))
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- 실행 계획에서 "Using index" 확인 가능 (테이블 접근 없이 인덱스만으로 처리)
```

### 2. 파티셔닝 활용
```sql
-- 날짜 기반 파티셔닝
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT,
    customer_id BIGINT,
    total_amount DECIMAL(10,2),
    status VARCHAR(20),
    created_at DATE,
    PRIMARY KEY (id, created_at)
) 
PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
    PARTITION p202402 VALUES LESS THAN (TO_DAYS('2024-03-01')),
    PARTITION p202403 VALUES LESS THAN (TO_DAYS('2024-04-01')),
    PARTITION p202404 VALUES LESS THAN (TO_DAYS('2024-05-01'))
);

-- 파티션 프루닝으로 검색 범위 자동 제한
SELECT * FROM orders 
WHERE created_at BETWEEN '2024-01-15' AND '2024-01-20'
ORDER BY created_at DESC
LIMIT 20;
-- p202401 파티션만 스캔
```

## 캐싱 전략

### 1. 계층별 캐싱
```java
@Service
public class OrderCachingService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // L1 캐시: 애플리케이션 레벨 (Caffeine)
    @Cacheable(value = "orders", key = "#page + ':' + #size")
    public List<Order> getOrdersWithL1Cache(int page, int size) {
        return orderRepository.findOrdersForPage(page, size);
    }
    
    // L2 캐시: Redis 레벨
    public List<Order> getOrdersWithL2Cache(String cacheKey) {
        // Redis에서 캐시 조회
        List<Order> cachedOrders = (List<Order>) redisTemplate.opsForValue()
            .get("orders:" + cacheKey);
        
        if (cachedOrders != null) {
            return cachedOrders;
        }
        
        // 캐시 미스 시 DB 조회
        List<Order> orders = orderRepository.findRecentOrders();
        
        // Redis에 캐시 저장 (TTL 5분)
        redisTemplate.opsForValue().set("orders:" + cacheKey, orders, 
            Duration.ofMinutes(5));
        
        return orders;
    }
    
    // 무한 스크롤용 캐시 전략
    public List<Order> getOrdersForInfiniteScroll(String cursor, int size) {
        String cacheKey = "infinite_scroll:" + cursor + ":" + size;
        
        List<Order> cachedResult = (List<Order>) redisTemplate.opsForValue()
            .get(cacheKey);
        
        if (cachedResult != null) {
            return cachedResult;
        }
        
        List<Order> orders = orderRepository.findOrdersByCursor(cursor, size);
        
        // 다음 페이지를 위한 프리페칭
        if (orders.size() == size) {
            String nextCursor = generateNextCursor(orders.get(orders.size() - 1));
            CompletableFuture.supplyAsync(() -> 
                orderRepository.findOrdersByCursor(nextCursor, size))
                .thenAccept(nextOrders -> 
                    redisTemplate.opsForValue().set(
                        "infinite_scroll:" + nextCursor + ":" + size,
                        nextOrders, Duration.ofMinutes(2)));
        }
        
        redisTemplate.opsForValue().set(cacheKey, orders, Duration.ofMinutes(5));
        return orders;
    }
}
```

### 2. 계산 결과 캐싱
```java
// 집계 결과 캐싱
@Service
public class OrderStatisticsService {
    
    @Cacheable(value = "order_stats", key = "#date.toString()")
    public OrderStatistics getDailyStatistics(LocalDate date) {
        // 무거운 집계 쿼리
        return orderRepository.calculateDailyStatistics(date);
    }
    
    // 실시간성이 중요하지 않은 데이터는 배치로 미리 계산
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    public void precomputeStatistics() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        
        // 어제와 오늘 통계를 미리 계산하여 캐시에 저장
        CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> getDailyStatistics(yesterday)),
            CompletableFuture.runAsync(() -> getDailyStatistics(today))
        );
    }
}
```

## 실무 성능 최적화 사례

### 케이스 1: 전자상거래 주문 조회 최적화
```java
// Before: 느린 페이징
@RestController
public class OrderControllerBefore {
    
    @GetMapping("/orders")
    public Page<OrderDto> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // 문제: 깊은 페이지에서 성능 저하
        Page<Order> orders = orderRepository.findAll(
            PageRequest.of(page, size, Sort.by("createdAt").descending()));
        
        return orders.map(OrderDto::from);
    }
}

// After: 최적화된 페이징
@RestController
public class OrderControllerAfter {
    
    @GetMapping("/orders")
    public CursorPageResponse<OrderDto> getOrders(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        
        List<Order> orders;
        
        if (cursor == null) {
            orders = orderRepository.findRecentOrders(size);
        } else {
            CursorInfo cursorInfo = CursorInfo.decode(cursor);
            orders = orderRepository.findOrdersAfterCursor(cursorInfo, size);
        }
        
        List<OrderDto> orderDtos = orders.stream()
            .map(OrderDto::from)
            .collect(Collectors.toList());
        
        return CursorPageResponse.of(orderDtos, cursor, size);
    }
}

// 성능 개선 결과
/*
Before (오프셋 기반):
- 1페이지: 50ms
- 100페이지: 500ms  
- 1000페이지: 5000ms

After (커서 기반):
- 1페이지: 30ms
- 100페이지: 35ms
- 1000페이지: 40ms
*/
```

### 케이스 2: 대용량 리포트 생성 최적화
```java
// 스트리밍 방식의 대용량 CSV 다운로드
@RestController
public class ReportController {
    
    @GetMapping(value = "/reports/orders.csv", produces = "text/csv")
    public void downloadOrderReport(HttpServletResponse response,
                                  @RequestParam LocalDate startDate,
                                  @RequestParam LocalDate endDate) throws IOException {
        
        response.setHeader("Content-Disposition", "attachment; filename=orders.csv");
        
        try (PrintWriter writer = response.getWriter()) {
            // CSV 헤더
            writer.println("Order ID,Customer ID,Total Amount,Status,Created At");
            
            // 스트리밍 처리로 메모리 효율성 확보
            orderRepository.findOrdersInDateRange(startDate, endDate)
                .forEach(order -> {
                    writer.printf("%d,%d,%.2f,%s,%s%n",
                        order.getId(),
                        order.getCustomerId(),
                        order.getTotalAmount(),
                        order.getStatus(),
                        order.getCreatedAt());
                });
        }
    }
}

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.id")
    @QueryHints(@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE, value = "1000"))
    Stream<Order> findOrdersInDateRange(@Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);
}
```

## 인터뷰 꼬리질문 대비

### Q1: "커서 기반 페이징의 단점은 무엇인가요?"
**답변 포인트:**
- **임의 페이지 접근 불가**: 특정 페이지로 바로 점프할 수 없음
- **UI 복잡성**: 페이지 번호 대신 "더 보기" 방식으로 UX 변경 필요
- **정렬 제한**: 커서로 사용할 컬럼이 고유하고 정렬 가능해야 함
- **구현 복잡성**: 오프셋 기반보다 구현이 복잡함

### Q2: "대용량 배치 처리 시 메모리 부족을 어떻게 방지하나요?"
**답변 포인트:**
- **청크 단위 처리**: 작은 단위로 나누어 처리
- **스트리밍 처리**: 전체 데이터를 메모리에 올리지 않고 순차 처리
- **1차 캐시 클리어**: EntityManager.clear()로 주기적 메모리 해제
- **JDBC 배치**: JPA 대신 JDBC 직접 사용으로 오버헤드 최소화

### Q3: "페이징 성능 최적화를 위한 인덱스 설계는?"
**답변 포인트:**
- **정렬 기준 컬럼**: ORDER BY에 사용되는 컬럼을 인덱스에 포함
- **커버링 인덱스**: SELECT 절의 모든 컬럼을 인덱스에 포함
- **복합 인덱스 순서**: WHERE 조건, ORDER BY 순서를 고려한 설계
- **파티셔닝**: 데이터 범위를 물리적으로 분할하여 스캔 범위 제한

## 실무 베스트 프랙티스

1. **페이징 전략**: 비즈니스 요구사항에 맞는 페이징 방식 선택
2. **인덱스 최적화**: 페이징 쿼리에 최적화된 인덱스 설계
3. **캐싱 활용**: 자주 조회되는 페이지는 캐싱으로 성능 향상
4. **배치 처리**: 대용량 작업은 청크 단위로 분할 처리
5. **모니터링**: 실제 사용 패턴을 분석하여 지속적 최적화

대용량 데이터 처리와 페이징 최적화는 사용자 경험과 시스템 성능에 직접적인 영향을 미치는 중요한 기술입니다. 데이터 규모와 접근 패턴을 고려하여 적절한 전략을 선택하는 것이 핵심입니다.