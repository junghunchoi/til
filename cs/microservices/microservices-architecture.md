# 마이크로서비스 아키텍처 설계와 도메인 분리

## 핵심 개념

### 마이크로서비스란?
- **단일 책임**: 각 서비스는 하나의 비즈니스 기능을 담당
- **독립 배포**: 서비스별로 독립적인 배포와 확장 가능
- **기술 다양성**: 서비스마다 적합한 기술 스택 선택 가능
- **분산 시스템**: 네트워크를 통한 서비스 간 통신

### 도메인 분리 전략 (Domain-Driven Design)

#### 1. Bounded Context 식별
```java
// 잘못된 예: 하나의 거대한 User 서비스
@Entity
public class User {
    private Long id;
    private String email;
    private String password;
    private Address shippingAddress;  // 주문 도메인
    private List<Order> orders;       // 주문 도메인
    private PaymentInfo paymentInfo;  // 결제 도메인
    private List<Review> reviews;     // 리뷰 도메인
}

// 올바른 예: 도메인별 분리
// 사용자 관리 서비스
@Entity
public class UserAccount {
    private Long userId;
    private String email;
    private String password;
    private ProfileInfo profile;
}

// 주문 서비스
@Entity
public class Order {
    private Long orderId;
    private Long customerId;  // 사용자 서비스 참조
    private List<OrderItem> items;
    private OrderStatus status;
}
```

#### 2. 서비스 분해 패턴

**기능별 분해 (Decompose by Business Capability)**
```
E-Commerce 시스템 예시:
├── User Service (사용자 관리)
├── Product Service (상품 관리)
├── Order Service (주문 처리)
├── Payment Service (결제 처리)
├── Inventory Service (재고 관리)
└── Notification Service (알림)
```

**하위 도메인별 분해 (Decompose by Sub-domain)**
```java
// 주문 도메인의 하위 서비스들
@Service
public class OrderProcessingService {
    // 주문 생성 및 처리 로직
}

@Service
public class OrderHistoryService {
    // 주문 이력 조회 로직
}

@Service
public class OrderAnalyticsService {
    // 주문 분석 및 통계 로직
}
```

## 마이크로서비스 통신 패턴

### 1. 동기 통신 (RESTful API)
```java
@RestController
public class OrderController {
    
    @Autowired
    private UserServiceClient userServiceClient;
    
    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        // 다른 서비스 호출
        User user = userServiceClient.getUser(request.getUserId());
        
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }
        
        Order order = orderService.createOrder(request);
        return ResponseEntity.ok(order);
    }
}

// Feign Client 사용 예시
@FeignClient(name = "user-service", url = "${user.service.url}")
public interface UserServiceClient {
    @GetMapping("/users/{userId}")
    User getUser(@PathVariable Long userId);
}
```

### 2. 비동기 통신 (이벤트 기반)
```java
// 주문 생성 시 이벤트 발행
@Service
public class OrderService {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(request);
        orderRepository.save(order);
        
        // 이벤트 발행
        OrderCreatedEvent event = new OrderCreatedEvent(order.getId(), 
                                                       order.getCustomerId(),
                                                       order.getItems());
        eventPublisher.publishEvent(event);
        
        return order;
    }
}

// 다른 서비스에서 이벤트 수신
@EventListener
@Service
public class InventoryService {
    
    @Async
    public void handleOrderCreated(OrderCreatedEvent event) {
        for (OrderItem item : event.getItems()) {
            reduceInventory(item.getProductId(), item.getQuantity());
        }
    }
}
```

## 데이터 관리 패턴

### Database per Service 패턴
```yaml
# docker-compose.yml 예시
version: '3'
services:
  user-service:
    image: user-service:latest
    environment:
      - DB_URL=jdbc:mysql://user-db:3306/userdb
  
  user-db:
    image: mysql:8.0
    environment:
      - MYSQL_DATABASE=userdb
  
  order-service:
    image: order-service:latest
    environment:
      - DB_URL=jdbc:postgresql://order-db:5432/orderdb
  
  order-db:
    image: postgres:13
    environment:
      - POSTGRES_DB=orderdb
```

### Shared Database Anti-pattern 피하기
```java
// 잘못된 예: 여러 서비스가 같은 DB 테이블 접근
// User Service
@Entity
@Table(name = "users")
public class User { ... }

// Order Service (같은 DB의 users 테이블 접근 - 안티패턴!)
@Entity
@Table(name = "users")
public class Customer { ... }

// 올바른 예: 각 서비스별 독립적인 데이터 저장
// User Service
@Entity
@Table(name = "user_accounts")
public class UserAccount { ... }

// Order Service (사용자 정보가 필요할 때는 API 호출)
@Entity
@Table(name = "order_customers")
public class OrderCustomer {
    private Long customerId;
    private String customerName;  // 캐시된 데이터
    private String email;         // 캐시된 데이터
}
```

## 서비스 디스커버리와 구성 관리

### Spring Cloud Netflix Eureka
```java
// 서비스 등록
@SpringBootApplication
@EnableEurekaClient
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

// application.yml
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
  instance:
    prefer-ip-address: true
```

### Spring Cloud Config
```yaml
# bootstrap.yml
spring:
  application:
    name: order-service
  cloud:
    config:
      uri: http://config-server:8888
      fail-fast: true
```

## 인터뷰에서 자주 나오는 꼬리질문들

### Q1: "마이크로서비스의 단점과 극복 방법은?"
**답변 포인트:**
- **분산 시스템 복잡성**: 서킷 브레이커, 재시도 로직 구현
- **데이터 일관성**: Saga 패턴, 이벤트 소싱 활용
- **운영 복잡성**: 컨테이너 오케스트레이션, 모니터링 도구 활용
- **성능 오버헤드**: 캐싱, 연결 풀링, 배치 처리

### Q2: "언제 모놀리스에서 마이크로서비스로 전환해야 하나?"
**답변 포인트:**
- **팀 크기**: Conway's Law - 조직 구조가 시스템 구조를 결정
- **배포 빈도**: 독립적인 배포가 필요한 경우
- **기술 다양성**: 서비스별로 다른 기술이 필요한 경우
- **확장성**: 서비스별로 다른 확장 요구사항

### Q3: "마이크로서비스 간 트랜잭션은 어떻게 처리하나?"
**답변 포인트:**
- **2PC 문제점**: 가용성과 성능 이슈
- **Saga 패턴**: Choreography vs Orchestration
- **이벤트 소싱**: 이벤트 기반 데이터 일관성
- **보상 트랜잭션**: 실패 시 롤백 로직

## 실제 구현 시 고려사항

### 1. 서비스 크기 결정
```
마이크로서비스 크기 지표:
- 개발팀 크기: 2-pizza team (6-8명)
- 코드베이스: 1-2주 내 재작성 가능한 크기
- 배포 단위: 독립적으로 배포 가능
- 도메인 경계: 명확한 비즈니스 기능 단위
```

### 2. API 버전 관리
```java
// URL 버전 관리
@RestController
@RequestMapping("/api/v1/orders")
public class OrderControllerV1 { ... }

@RestController
@RequestMapping("/api/v2/orders")
public class OrderControllerV2 { ... }

// 헤더 버전 관리
@GetMapping(value = "/orders", headers = "X-API-Version=1")
public List<OrderV1> getOrdersV1() { ... }

@GetMapping(value = "/orders", headers = "X-API-Version=2")
public List<OrderV2> getOrdersV2() { ... }
```

### 3. 장애 격리 및 복원력
```java
@Component
public class UserServiceClient {
    
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserFallback")
    @Retry(name = "user-service")
    @TimeLimiter(name = "user-service")
    public CompletableFuture<User> getUser(Long userId) {
        return CompletableFuture.supplyAsync(() -> 
            restTemplate.getForObject("/users/" + userId, User.class));
    }
    
    public CompletableFuture<User> getUserFallback(Long userId, Exception ex) {
        return CompletableFuture.completedFuture(
            User.builder().id(userId).name("Unknown").build());
    }
}
```

## 핵심 암기 포인트

1. **마이크로서비스는 기술이 아닌 조직적/비즈니스적 결정**
2. **도메인 주도 설계(DDD)가 서비스 분리의 핵심**
3. **데이터 일관성보다 가용성을 우선하는 설계**
4. **모니터링과 관찰 가능성이 성공의 핵심**
5. **점진적 전환 (Strangler Fig Pattern) 권장**

이러한 내용들은 실제 마이크로서비스 경험과 함께 설명할 때 더욱 설득력이 있습니다.