# 🔄 자바 동시성 문제 해결하기

> 안녕하세요! 오늘은 실무에서 정말 자주 마주치는 동시성 문제와 그 해결책에 대해 알아보려고 합니다. 

## 🚨 왜 동시성 문제를 알아야 할까요?

여러분이 개발한 서비스가 성공해서 트래픽이 늘어나면 반드시 마주치게 되는 문제가 바로 **동시성 문제**입니다. 아래 상황들이 익숙하지 않나요?

- 전자상거래 사이트에서 동시에 여러 유저가 남은 1개의 상품을 주문 → 재고는 -2가 됨 😱
- 경쟁이 치열한 예약 시스템에서 1개의 좌석에 2명이 예약 성공 😫 
- 이벤트 응모에서 중복 당첨이 발생 🤦‍♂️

문제의 심각성은 개발 환경에선 거의 발견되지 않다가 **실제 서비스 환경에서만 발생**한다는 점이죠!

## 🔍 가장 흔한 동시성 문제 상황 TOP 3

### 1️⃣ 재고 관리 시스템 (가장 흔한 사례!)

```java
// ❌ 문제가 생기는 코드
@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;
    
    @Transactional
    public void order(Long productId, int quantity) {
        Product product = productRepository.findById(productId).orElseThrow();
        if (product.getStock() >= quantity) {
            product.decreaseStock(quantity);
            productRepository.save(product);
        } else {
            throw new SoldOutException();
        }
    }
}
```

**문제점**: 여러 사용자가 동시에 같은 상품을 주문하면? A와 B가 동시에 재고를 조회하고 각자 주문 처리 → 재고 마이너스!

### 2️⃣ 포인트/머니 차감 시스템

```java
// ❌ 문제가 생기는 코드
@Service
public class WalletService {
    @Autowired
    private WalletRepository walletRepository;
    
    @Transactional 
    public void withdraw(Long userId, int amount) {
        Wallet wallet = walletRepository.findByUserId(userId);
        if (wallet.getBalance() >= amount) {
            wallet.decreaseBalance(amount);
            walletRepository.save(wallet);
        } else {
            throw new InsufficientBalanceException();
        }
    }
}
```

**문제점**: 동시에 여러 요청이 들어오면 잔액 검증 시점과 차감 시점 사이에 타이밍 이슈 발생!

### 3️⃣ 이벤트/쿠폰 발급 시스템

```java
// ❌ 문제가 생기는 코드
@Service
public class CouponService {
    @Autowired
    private CouponRepository couponRepository;
    
    @Transactional
    public void issueCoupon(Long userId, String couponCode) {
        // 이미 발급 받았는지 체크
        if (!couponRepository.existsByUserIdAndCouponCode(userId, couponCode)) {
            couponRepository.save(new Coupon(userId, couponCode));
        }
    }
}
```

**문제점**: 짧은 시간에 동일 사용자가 여러 번 요청하면 중복 발급 가능성 존재!

## 💡 실전 해결책 (스프링 & 자바)

### 방법 1: 낙관적 락(Optimistic Lock) - 충돌이 적을 때 사용

```java
// ✅ 낙관적 락을 사용한 해결책
@Entity
public class Product {
    @Id
    private Long id;
    private int stock;
    
    @Version  // 이게 핵심! JPA의 낙관적 락
    private Long version;
    
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new SoldOutException();
        }
        this.stock -= quantity;
    }
}

@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void order(Long productId, int quantity) {
        try {
            Product product = productRepository.findById(productId).orElseThrow();
            product.decreaseStock(quantity);
            productRepository.save(product);
        } catch (ObjectOptimisticLockingFailureException e) {
            // 충돌 발생! 사용자에게 "다시 시도" 메시지
            throw new ConcurrentModificationException("주문 중 충돌이 발생했습니다. 다시 시도해주세요.");
        }
    }
}
```

**장점**: 구현이 간단하고 대부분의 상황에서 효율적
**단점**: 충돌 발생 시 사용자가 재시도 해야 함

### 방법 2: 비관적 락(Pessimistic Lock) - 충돌이 많을 때

```java
// ✅ 비관적 락을 사용한 해결책
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Product findByIdWithPessimisticLock(@Param("id") Long id);
}

@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;
    
    @Transactional
    public void order(Long productId, int quantity) {
        // 여기서 SELECT FOR UPDATE가 실행됨
        Product product = productRepository.findByIdWithPessimisticLock(productId);
        if (product.getStock() >= quantity) {
            product.decreaseStock(quantity);
        } else {
            throw new SoldOutException();
        }
    }
}
```

**장점**: 충돌 자체를 방지하여 재시도 불필요
**단점**: 성능 저하 가능성 (락 획득 대기)

### 방법 3: 네임드 락(Named Lock) - MySQL 기준

```java
// ✅ 네임드 락 활용 (MySQL)
@Repository
public interface LockRepository {
    @Query(value = "SELECT GET_LOCK(:key, 3000)", nativeQuery = true)
    int getLock(@Param("key") String key);
    
    @Query(value = "SELECT RELEASE_LOCK(:key)", nativeQuery = true)
    int releaseLock(@Param("key") String key);
}

@Service
public class ProductService {
    @Autowired
    private LockRepository lockRepository;
    @Autowired
    private ProductRepository productRepository;
    
    public void order(Long productId, int quantity) {
        String lockKey = "product_" + productId;
        
        try {
            // 락 획득 시도
            if (lockRepository.getLock(lockKey) != 1) {
                throw new RuntimeException("락 획득 실패");
            }
            
            // 비즈니스 로직 (별도 트랜잭션으로 처리)
            orderWithTransaction(productId, quantity);
            
        } finally {
            // 락 해제
            lockRepository.releaseLock(lockKey);
        }
    }
    
    @Transactional
    public void orderWithTransaction(Long productId, int quantity) {
        // 주문 처리 로직
    }
}
```

**장점**: DB 레벨에서 강력한 동시성 제어
**단점**: MySQL 전용, 락 관리 복잡

### 방법 4: 분산 환경에서의 Redis 기반 분산 락

```java
// ✅ Redis 분산 락 (여러 서버에 걸쳐 동작)
@Service
public class RedisLockService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    public boolean acquireLock(String lockKey, String requestId, long timeoutMillis) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(
                lockKey, requestId, Duration.ofMillis(timeoutMillis)
            )
        );
    }
    
    public boolean releaseLock(String lockKey, String requestId) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) else return 0 end";
        
        return redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(lockKey),
            requestId
        ) == 1L;
    }
}

@Service
public class ProductService {
    @Autowired
    private RedisLockService lockService;
    
    public void order(Long productId, int quantity) {
        String lockKey = "product_lock:" + productId;
        String requestId = UUID.randomUUID().toString();
        
        try {
            // 3초 동안 락 획득 시도
            if (!lockService.acquireLock(lockKey, requestId, 3000)) {
                throw new RuntimeException("상품 주문이 많아 잠시 후 다시 시도해주세요");
            }
            
            // 실제 주문 처리 로직
            processOrder(productId, quantity);
            
        } finally {
            // 락 해제
            lockService.releaseLock(lockKey, requestId);
        }
    }
}
```

**장점**: 마이크로서비스 환경에서도 적용 가능한 강력한 솔루션
**단점**: Redis 의존성, 네트워크 지연 가능성

## 📊 어떤 방법을 선택해야 할까요?

1. **트래픽이 적거나 충돌 가능성이 낮은 경우**: 낙관적 락 (Optimistic Lock)
2. **트래픽이 많고 충돌이 자주 발생하는 경우**: 비관적 락 (Pessimistic Lock)
3. **분산 환경(여러 서버)에서의 동시성 제어**: Redis 기반 분산 락

## 💼 팁!

- **테스트 케이스 작성할 때**: `CountDownLatch`와 멀티스레드로 동시성 테스트를 꼭 작성하세요!
- **모니터링**: 동시성 이슈는 발견하기 어려우므로 재고, 잔액 등의 데이터 정합성을 주기적으로 체크하는 모니터링 시스템 구축하세요
- **점진적 도입**: 모든 API에 락을 적용하면 성능 저하가 발생합니다. 동시성 이슈가 중요한 부분만 선택적으로 적용하세요.


