# 영속성 컨텍스트와 1차 캐시의 동작 원리

## 영속성 컨텍스트(Persistence Context) 개념

### 영속성 컨텍스트란?
```java
// 영속성 컨텍스트는 EntityManager가 관리하는 엔티티 저장 환경
@Service
@Transactional
public class OrderService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public void demonstratePersistenceContext() {
        // 1. 비영속 상태 (Transient)
        Order order = new Order("ORDER-001");
        System.out.println("비영속 상태: " + order);
        
        // 2. 영속 상태 (Managed) - 영속성 컨텍스트에 저장
        entityManager.persist(order);
        System.out.println("영속 상태: " + order);
        
        // 3. 준영속 상태 (Detached) - 영속성 컨텍스트에서 분리
        entityManager.detach(order);
        System.out.println("준영속 상태: " + order);
        
        // 4. 삭제 상태 (Removed)
        entityManager.remove(order);
        System.out.println("삭제 상태: " + order);
    }
}
```

### 엔티티 생명주기와 상태 변화
```java
@Service
@Transactional
public class EntityLifecycleService {
    
    @PersistenceContext
    private EntityManager em;
    
    public void demonstrateEntityLifecycle() {
        // 1. 비영속 → 영속
        Order order = new Order("ORDER-001"); // 비영속
        em.persist(order);                    // 영속
        
        // 2. 영속 → 준영속  
        em.detach(order);                     // 준영속
        
        // 3. 준영속 → 영속
        order = em.merge(order);              // 영속 (새로운 인스턴스)
        
        // 4. 영속 → 삭제
        em.remove(order);                     // 삭제
        
        // 5. 삭제된 엔티티 재사용 시도
        try {
            em.persist(order);                // IllegalArgumentException 발생
        } catch (IllegalArgumentException e) {
            System.out.println("삭제된 엔티티는 다시 영속화할 수 없음");
        }
    }
    
    // 플러시(Flush) 동작 확인
    public void demonstrateFlush() {
        Order order = new Order("ORDER-002");
        em.persist(order);
        
        // 수동 플러시 - SQL이 DB로 전송됨 (트랜잭션 커밋 전)
        em.flush();
        System.out.println("플러시 후 - SQL 실행됨");
        
        // 여전히 영속 상태
        order.setStatus(OrderStatus.COMPLETED);
        System.out.println("상태 변경 - 여전히 영속 상태");
    }
}
```

## 1차 캐시 동작 원리

### 1차 캐시의 내부 구조
```java
/*
영속성 컨텍스트 내부의 1차 캐시 구조:

1차 캐시 (Map<EntityKey, Entity>)
┌─────────────────┬─────────────────────────┐
│ EntityKey       │ Entity                  │
├─────────────────┼─────────────────────────┤
│ Order#1         │ Order{id=1, number=...} │
│ Customer#100    │ Customer{id=100, ...}   │  
│ Product#50      │ Product{id=50, ...}     │
└─────────────────┴─────────────────────────┘

스냅샷 (Map<EntityKey, Object[]>)  
┌─────────────────┬─────────────────────────┐
│ EntityKey       │ Snapshot (원본 값들)    │
├─────────────────┼─────────────────────────┤
│ Order#1         │ [1, "ORDER-001", ...]   │
│ Customer#100    │ [100, "홍길동", ...]    │
└─────────────────┴─────────────────────────┘
*/

@Service
@Transactional
public class FirstLevelCacheService {
    
    @PersistenceContext
    private EntityManager em;
    
    public void demonstrateFirstLevelCache() {
        System.out.println("=== 1차 캐시 동작 확인 ===");
        
        // 1. 첫 번째 조회 - DB에서 조회하여 1차 캐시에 저장
        Order order1 = em.find(Order.class, 1L);
        System.out.println("첫 번째 조회: " + order1);
        
        // 2. 두 번째 조회 - 1차 캐시에서 조회 (DB 쿼리 없음)
        Order order2 = em.find(Order.class, 1L);
        System.out.println("두 번째 조회: " + order2);
        
        // 3. 동일성 보장 확인
        System.out.println("order1 == order2: " + (order1 == order2)); // true
        System.out.println("order1.equals(order2): " + order1.equals(order2)); // true
    }
    
    public void demonstrateCacheHitMiss() {
        // 캐시 히트 시나리오
        System.out.println("=== 캐시 히트 ===");
        Order order = em.find(Order.class, 1L);    // DB 쿼리 실행
        Order same = em.find(Order.class, 1L);     // 캐시에서 조회
        
        // 캐시 미스 시나리오  
        System.out.println("=== 캐시 미스 ===");
        Order order1 = em.find(Order.class, 1L);   // DB 쿼리 실행
        Order order2 = em.find(Order.class, 2L);   // 다른 ID, DB 쿼리 실행
    }
}
```

### 1차 캐시와 동일성 보장
```java
@Service
@Transactional
public class IdentityService {
    
    @PersistenceContext
    private EntityManager em;
    
    public void demonstrateIdentity() {
        // 같은 영속성 컨텍스트 내에서 동일성 보장
        Order order1 = em.find(Order.class, 1L);
        Order order2 = em.find(Order.class, 1L);
        
        System.out.println("order1 == order2: " + (order1 == order2)); // true
        System.out.println("주소값 비교: " + System.identityHashCode(order1) + 
                          " vs " + System.identityHashCode(order2));
        
        // 다른 영속성 컨텍스트에서는 동일성 보장 안됨
        demonstrateDifferentPersistenceContext();
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void demonstrateDifferentPersistenceContext() {
        Order order3 = em.find(Order.class, 1L); // 새로운 영속성 컨텍스트
        System.out.println("새로운 영속성 컨텍스트의 order: " + order3);
    }
    
    // 컬렉션에서의 동일성 보장
    public void demonstrateCollectionIdentity() {
        Order order = em.find(Order.class, 1L);
        
        // 컬렉션 조회
        List<OrderItem> items1 = order.getOrderItems();
        List<OrderItem> items2 = order.getOrderItems();
        
        System.out.println("items1 == items2: " + (items1 == items2)); // true
        
        // 개별 아이템의 동일성도 보장
        if (!items1.isEmpty() && !items2.isEmpty()) {
            OrderItem item1 = items1.get(0);
            OrderItem item2 = items2.get(0);
            System.out.println("item1 == item2: " + (item1 == item2)); // true
        }
    }
}
```

## 변경 감지(Dirty Checking)

### 변경 감지 메커니즘
```java
@Service
@Transactional
public class DirtyCheckingService {
    
    @PersistenceContext
    private EntityManager em;
    
    public void demonstrateDirtyChecking() {
        // 1. 엔티티 조회 - 스냅샷 저장
        Order order = em.find(Order.class, 1L);
        String originalStatus = order.getStatus().name();
        
        // 2. 엔티티 수정 - 영속 엔티티의 값 변경
        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedDate(LocalDateTime.now());
        
        // 3. 플러시 시점에 스냅샷과 비교하여 UPDATE 쿼리 생성
        // em.flush(); // 트랜잭션 커밋 시 자동으로 flush 됨
        
        System.out.println("원본 상태: " + originalStatus);
        System.out.println("변경된 상태: " + order.getStatus());
        System.out.println("UPDATE 쿼리가 자동으로 생성됨");
    }
    
    // 변경 감지 최적화
    public void optimizedDirtyChecking() {
        Order order = em.find(Order.class, 1L);
        
        // 실제로 값이 변경된 경우에만 UPDATE
        String currentStatus = order.getStatus().name();
        if (!currentStatus.equals("SHIPPED")) {
            order.setStatus(OrderStatus.SHIPPED); // 변경 감지 발생
        }
        
        // 같은 값으로 설정하면 변경 감지 안됨
        order.setStatus(order.getStatus()); // 변경 감지 발생하지 않음
    }
    
    // 부분 업데이트 확인
    public void partialUpdate() {
        Order order = em.find(Order.class, 1L);
        
        // 일부 필드만 변경
        order.setStatus(OrderStatus.DELIVERED);
        // order.setOrderDate()는 변경하지 않음
        
        // UPDATE order SET status = ? WHERE id = ?
        // 변경된 필드에 대해서만 UPDATE 쿼리 생성
    }
}
```

### 변경 감지 vs 수동 업데이트 비교
```java
@Service
@Transactional
public class UpdateComparisonService {
    
    @PersistenceContext
    private EntityManager em;
    
    @Autowired
    private OrderRepository orderRepository;
    
    // 방법 1: 변경 감지 (Dirty Checking)
    public void updateWithDirtyChecking(Long orderId, OrderStatus newStatus) {
        Order order = em.find(Order.class, orderId);
        order.setStatus(newStatus); // 자동으로 UPDATE 쿼리 생성
        // em.merge()나 save() 호출 불필요
    }
    
    // 방법 2: 명시적 save() 호출 (불필요하지만 동작함)
    public void updateWithExplicitSave(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(newStatus);
        orderRepository.save(order); // 불필요한 호출이지만 동작함
    }
    
    // 방법 3: 벌크 업데이트 (변경 감지 사용 안함)
    public void bulkUpdate(OrderStatus oldStatus, OrderStatus newStatus) {
        int updatedCount = em.createQuery(
            "UPDATE Order o SET o.status = :newStatus WHERE o.status = :oldStatus")
            .setParameter("newStatus", newStatus)
            .setParameter("oldStatus", oldStatus)
            .executeUpdate();
        
        System.out.println("벌크 업데이트: " + updatedCount + "개 레코드 수정");
        
        // 벌크 연산 후 영속성 컨텍스트 초기화 필요
        em.clear();
    }
}
```

## 플러시(Flush) 메커니즘

### 플러시 발생 시점과 모드
```java
@Service
@Transactional
public class FlushService {
    
    @PersistenceContext
    private EntityManager em;
    
    public void demonstrateFlushModes() {
        // 플러시 모드 확인
        FlushModeType currentMode = em.getFlushMode();
        System.out.println("현재 플러시 모드: " + currentMode);
        
        // 1. AUTO 모드 (기본값)
        em.setFlushMode(FlushModeType.AUTO);
        Order order = new Order("ORDER-001");
        em.persist(order);
        
        // 쿼리 실행 전 자동 플러시 발생
        List<Order> orders = em.createQuery("SELECT o FROM Order o", Order.class)
                               .getResultList();
        System.out.println("AUTO 모드: 쿼리 전 자동 플러시");
        
        // 2. COMMIT 모드
        em.setFlushMode(FlushModeType.COMMIT);
        Order order2 = new Order("ORDER-002");
        em.persist(order2);
        
        // 쿼리 실행해도 플러시 안됨
        List<Order> orders2 = em.createQuery("SELECT o FROM Order o", Order.class)
                                .getResultList();
        System.out.println("COMMIT 모드: 쿼리 전 플러시 안됨");
        
        // 수동 플러시
        em.flush();
        System.out.println("수동 플러시 실행");
    }
    
    // 플러시 시점별 동작 확인
    public void flushTiming() {
        Order order = new Order("ORDER-003");
        em.persist(order);
        
        // 1. 수동 플러시
        System.out.println("=== 수동 플러시 ===");
        em.flush(); // 즉시 SQL 실행
        
        // 2. JPQL 쿼리 실행 전 자동 플러시
        System.out.println("=== JPQL 실행 전 자동 플러시 ===");
        order.setStatus(OrderStatus.PENDING);
        List<Order> result = em.createQuery("SELECT o FROM Order o WHERE o.id = :id", Order.class)
                               .setParameter("id", order.getId())
                               .getResultList();
        
        // 3. 트랜잭션 커밋 시 자동 플러시
        System.out.println("=== 트랜잭션 커밋 시 자동 플러시 ===");
        order.setOrderDate(LocalDateTime.now());
        // 메서드 종료 시 @Transactional에 의해 커밋되면서 플러시 발생
    }
}
```

### 플러시와 준영속 상태
```java
@Service
public class DetachedStateService {
    
    @PersistenceContext
    private EntityManager em;
    
    @Transactional
    public Order getOrderForEdit(Long orderId) {
        Order order = em.find(Order.class, orderId);
        // 트랜잭션 종료 시 order는 준영속 상태가 됨
        return order;
    }
    
    @Transactional  
    public void updateDetachedOrder(Order detachedOrder) {
        // 준영속 엔티티 수정
        detachedOrder.setStatus(OrderStatus.SHIPPED);
        
        // 방법 1: merge() 사용
        Order managedOrder = em.merge(detachedOrder);
        System.out.println("merge 후 영속 상태: " + em.contains(managedOrder));
        System.out.println("원본은 여전히 준영속: " + em.contains(detachedOrder));
        
        // 방법 2: find() 후 값 복사
        Order foundOrder = em.find(Order.class, detachedOrder.getId());
        foundOrder.setStatus(detachedOrder.getStatus());
        foundOrder.setShippedDate(detachedOrder.getShippedDate());
    }
    
    public void demonstrateMerge() {
        // 새로운 엔티티 merge
        Order newOrder = new Order("ORDER-NEW");
        newOrder.setId(999L); // ID 설정
        
        Order mergedOrder = em.merge(newOrder);
        // ID가 존재하지 않으면 INSERT, 존재하면 UPDATE
        
        System.out.println("newOrder 영속 상태: " + em.contains(newOrder));     // false
        System.out.println("mergedOrder 영속 상태: " + em.contains(mergedOrder)); // true
    }
}
```

## 영속성 컨텍스트의 범위와 전략

### EntityManager 스코프별 비교
```java
// 1. Transaction-scoped EntityManager (기본값)
@Service
@Transactional
public class TransactionScopedService {
    
    @PersistenceContext // 트랜잭션 범위
    private EntityManager em;
    
    public void method1() {
        Order order = em.find(Order.class, 1L);
        // 같은 트랜잭션 내에서 동일성 보장
    }
    
    public void method2() {
        Order order = em.find(Order.class, 1L);
        // method1의 order와 같은 인스턴스
    }
}

// 2. Extended EntityManager
@Stateful
public class ExtendedScopedService {
    
    @PersistenceContext(type = PersistenceContextType.EXTENDED)
    private EntityManager em;
    
    public void loadOrder(Long id) {
        Order order = em.find(Order.class, id);
        // 여러 트랜잭션에 걸쳐 영속 상태 유지
    }
    
    @Transactional
    public void updateOrder(OrderStatus status) {
        // 이전에 로드한 order가 여전히 영속 상태
    }
}

// 3. Application-managed EntityManager
@Service
public class ApplicationManagedService {
    
    @PersistenceUnit
    private EntityManagerFactory emf;
    
    public void manualEntityManagerHandling() {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        
        try {
            tx.begin();
            Order order = new Order("ORDER-MANUAL");
            em.persist(order);
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
        } finally {
            em.close(); // 수동으로 닫아야 함
        }
    }
}
```

### OSIV(Open Session In View) 패턴
```java
// OSIV 활성화 시 (spring.jpa.open-in-view=true, 기본값)
@Controller
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    @GetMapping("/orders/{id}")
    public String viewOrder(@PathVariable Long id, Model model) {
        Order order = orderService.findById(id); // 서비스에서 조회
        
        // 뷰 렌더링 시점에도 영속성 컨텍스트 유지
        // 지연 로딩 가능
        model.addAttribute("order", order);
        model.addAttribute("itemCount", order.getOrderItems().size()); // 지연 로딩
        
        return "order/detail";
    }
}

// OSIV 비활성화 시 권장 패턴
@Service
@Transactional(readOnly = true)
public class OrderViewService {
    
    public OrderDetailDto getOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        
        // 서비스 계층에서 필요한 데이터를 모두 로딩
        return OrderDetailDto.builder()
                           .orderNumber(order.getOrderNumber())
                           .customerName(order.getCustomer().getName()) // 즉시 로딩
                           .items(order.getOrderItems().stream()         // 즉시 로딩
                                      .map(this::convertToItemDto)
                                      .collect(Collectors.toList()))
                           .build();
    }
}
```

## 성능 최적화와 모니터링

### 1차 캐시 효율성 측정
```java
@Service
@Transactional
public class CacheEfficiencyService {
    
    @PersistenceContext
    private EntityManager em;
    
    public void measureCacheEfficiency() {
        // Hibernate Statistics 활성화 필요
        SessionFactory sessionFactory = em.getEntityManagerFactory()
                                         .unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.clear();
        
        // 캐시 히트율 테스트
        for (int i = 0; i < 10; i++) {
            Order order = em.find(Order.class, 1L); // 첫 번째만 DB 조회
        }
        
        long queryCount = stats.getQueryExecutionCount();
        long cacheHits = stats.getSecondLevelCacheHitCount();
        long cacheMisses = stats.getSecondLevelCacheMissCount();
        
        System.out.println("쿼리 실행 횟수: " + queryCount);           // 1
        System.out.println("2차 캐시 히트: " + cacheHits);
        System.out.println("2차 캐시 미스: " + cacheMisses);
        System.out.println("1차 캐시가 9번 히트됨");
    }
    
    // 캐시 메모리 사용량 모니터링
    public void monitorCacheMemory() {
        // 대량의 엔티티 로딩
        for (int i = 1; i <= 1000; i++) {
            Order order = em.find(Order.class, (long) i);
        }
        
        // 메모리 사용량 확인
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("1차 캐시 메모리 사용량: " + usedMemory / 1024 / 1024 + "MB");
        
        // 캐시 클리어
        em.clear();
        
        runtime.gc();
        long usedMemoryAfterClear = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("캐시 클리어 후 메모리: " + usedMemoryAfterClear / 1024 / 1024 + "MB");
    }
}
```

### 대용량 데이터 처리 시 주의사항
```java
@Service
@Transactional
public class LargeDataProcessingService {
    
    @PersistenceContext
    private EntityManager em;
    
    // 잘못된 예: 메모리 부족 위험
    public void processAllOrdersBad() {
        List<Order> allOrders = em.createQuery("SELECT o FROM Order o", Order.class)
                                 .getResultList(); // 모든 데이터를 1차 캐시에 로딩
        
        for (Order order : allOrders) {
            processOrder(order);
        }
        // OutOfMemoryError 위험!
    }
    
    // 개선된 예: 배치 처리 + 주기적 클리어
    public void processAllOrdersGood() {
        int batchSize = 100;
        int pageNumber = 0;
        
        List<Order> orders;
        do {
            orders = em.createQuery("SELECT o FROM Order o", Order.class)
                      .setFirstResult(pageNumber * batchSize)
                      .setMaxResults(batchSize)
                      .getResultList();
            
            for (Order order : orders) {
                processOrder(order);
            }
            
            // 배치마다 1차 캐시 클리어
            em.flush();
            em.clear();
            
            pageNumber++;
        } while (orders.size() == batchSize);
    }
    
    // 스트림 처리 방식
    @Transactional(readOnly = true)
    public void processOrdersWithStream() {
        em.createQuery("SELECT o FROM Order o", Order.class)
          .getResultStream()
          .forEach(order -> {
              processOrder(order);
              // 주기적으로 캐시 클리어 (예: 100개마다)
              if (order.getId() % 100 == 0) {
                  em.clear();
              }
          });
    }
    
    private void processOrder(Order order) {
        // 주문 처리 로직
        order.setProcessedDate(LocalDateTime.now());
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "1차 캐시와 2차 캐시의 차이점은?"
**답변 포인트:**
- **1차 캐시**: EntityManager 레벨, 트랜잭션 스코프, 필수 기능
- **2차 캐시**: EntityManagerFactory 레벨, 애플리케이션 스코프, 선택적 기능
- **동일성**: 1차 캐시는 동일성 보장, 2차 캐시는 동등성만 보장
- **성능**: 1차 캐시가 더 빠름 (메모리 내 Map), 2차 캐시는 설정 필요

### Q2: "영속성 컨텍스트가 너무 커지면 어떻게 되나요?"
**답변 포인트:**
- **메모리 부족**: OutOfMemoryError 발생 가능
- **성능 저하**: 변경 감지 시 스냅샷 비교 오버헤드 증가
- **해결책**: 주기적 em.clear(), 배치 처리, 읽기 전용 트랜잭션 활용
- **모니터링**: 메모리 사용량과 쿼리 성능 모니터링

### Q3: "준영속 상태의 엔티티를 다시 영속 상태로 만드는 방법은?"
**답변 포인트:**
- **merge()**: 준영속 엔티티를 새로운 영속 엔티티로 복사
- **find() 후 값 복사**: ID로 조회 후 필요한 값만 업데이트
- **주의사항**: merge()는 새로운 인스턴스 반환, 원본은 여전히 준영속
- **성능**: find() 후 복사가 일반적으로 더 효율적

## 실무 베스트 프랙티스

1. **적절한 캐시 클리어**: 대용량 처리 시 주기적 em.clear() 호출
2. **읽기 전용 최적화**: 조회만 하는 경우 @Transactional(readOnly = true) 사용
3. **OSIV 설정**: 성능과 편의성을 고려하여 적절히 설정
4. **배치 처리**: 대량 데이터는 페이징과 배치 처리로 분할
5. **모니터링**: 1차 캐시 히트율과 메모리 사용량 지속 모니터링

영속성 컨텍스트와 1차 캐시는 JPA의 핵심 개념으로, 올바른 이해가 성능 최적화와 메모리 관리에 매우 중요합니다.