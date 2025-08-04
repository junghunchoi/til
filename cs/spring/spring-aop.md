# AOP(관점 지향 프로그래밍)의 개념과 실제 활용 사례

## AOP 핵심 개념

### AOP란?
**Aspect-Oriented Programming**은 횡단 관심사(Cross-cutting Concerns)를 모듈화하여 코드의 중복을 줄이고 관심사의 분리를 실현하는 프로그래밍 패러다임입니다.

### 주요 용어
```java
// Target: 부가 기능을 부여할 대상
@Service
public class OrderService {
    public Order createOrder(OrderRequest request) {
        // 비즈니스 로직
        return new Order(request);
    }
}

// Aspect: 횡단 관심사를 모듈화한 것
@Aspect
@Component
public class LoggingAspect {
    
    // Pointcut: 어디에 부가 기능을 적용할지 정의
    @Pointcut("execution(* com.example.service.*.*(..))")
    public void serviceLayer() {}
    
    // Advice: 언제, 어떤 부가 기능을 적용할지 정의
    @Around("serviceLayer()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        // JoinPoint: 실제 부가 기능이 적용될 지점
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed(); // Target 메서드 실행
        long endTime = System.currentTimeMillis();
        
        System.out.println(joinPoint.getSignature() + " executed in " + 
                          (endTime - startTime) + "ms");
        return result;
    }
}
```

## Spring AOP 동작 원리

### 프록시 기반 AOP
```java
// 인터페이스 기반 프록시 (JDK Dynamic Proxy)
public interface OrderService {
    Order createOrder(OrderRequest request);
}

@Service
public class OrderServiceImpl implements OrderService {
    @Override
    public Order createOrder(OrderRequest request) {
        return new Order(request);
    }
}

// 클래스 기반 프록시 (CGLIB)
@Service
public class ProductService { // 인터페이스 없음
    public Product createProduct(ProductRequest request) {
        return new Product(request);  
    }
}

// 프록시 생성 확인
@RestController
public class TestController {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired  
    private ProductService productService;
    
    @GetMapping("/proxy-info")
    public String getProxyInfo() {
        return "OrderService: " + orderService.getClass().getName() + "\n" +
               "ProductService: " + productService.getClass().getName();
    }
}

// 출력 결과:
// OrderService: com.sun.proxy.$Proxy123 (JDK Dynamic Proxy)
// ProductService: com.example.ProductService$$EnhancerBySpringCGLIB$$abc123 (CGLIB)
```

### AOP 설정
```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true) // CGLIB 강제 사용
public class AopConfig {
    // AOP 설정
}

// application.yml에서 설정
spring:
  aop:
    proxy-target-class: true  # CGLIB 사용
    auto: true               # AOP 자동 설정 활성화
```

## Advice 타입별 상세 구현

### 1. @Before - 메서드 실행 전
```java
@Aspect
@Component
public class SecurityAspect {
    
    @Before("@annotation(RequiresRole)")
    public void checkPermission(JoinPoint joinPoint) {
        // 메서드 실행 전 권한 체크
        RequiresRole requiresRole = getRequiresRoleAnnotation(joinPoint);
        String requiredRole = requiresRole.value();
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !hasRole(auth, requiredRole)) {
            throw new AccessDeniedException("권한이 없습니다: " + requiredRole);
        }
        
        log.info("Permission checked for role: {}", requiredRole);
    }
    
    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                  .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}

// 사용 예시
@Service
public class AdminService {
    
    @RequiresRole("ADMIN")
    public void deleteAllUsers() {
        // 관리자만 실행 가능한 메서드
    }
}
```

### 2. @After - 메서드 실행 후 (예외 발생 여부 무관)
```java
@Aspect
@Component
public class AuditAspect {
    
    @Autowired
    private AuditRepository auditRepository;
    
    @After("@annotation(Auditable)")
    public void logAudit(JoinPoint joinPoint) {
        // 항상 실행되는 감사 로그
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        
        AuditLog auditLog = AuditLog.builder()
                                  .className(className)
                                  .methodName(methodName)
                                  .arguments(Arrays.toString(args))
                                  .timestamp(LocalDateTime.now())
                                  .userId(getCurrentUserId())
                                  .build();
        
        auditRepository.save(auditLog);
        log.info("Audit logged: {}.{}", className, methodName);
    }
    
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
```

### 3. @AfterReturning - 정상 반환 후
```java
@Aspect
@Component
public class CacheAspect {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @AfterReturning(pointcut = "@annotation(Cacheable)", returning = "result")
    public void cacheResult(JoinPoint joinPoint, Object result, Cacheable cacheable) {
        // 성공적으로 실행된 경우에만 캐시 저장
        String cacheKey = generateCacheKey(joinPoint, cacheable.key());
        
        if (result != null) {
            redisTemplate.opsForValue().set(
                cacheKey, 
                result, 
                Duration.ofMinutes(cacheable.expireMinutes())
            );
            log.info("Result cached with key: {}", cacheKey);
        }
    }
    
    private String generateCacheKey(JoinPoint joinPoint, String keyExpression) {
        // SpEL 표현식 파싱하여 동적 키 생성
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        return String.format("%s_%s_%s", 
                           joinPoint.getTarget().getClass().getSimpleName(),
                           methodName,
                           Arrays.hashCode(args));
    }
}

// 커스텀 어노테이션
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cacheable {
    String key() default "";
    int expireMinutes() default 60;
}
```

### 4. @AfterThrowing - 예외 발생 후
```java
@Aspect
@Component
public class ExceptionHandlingAspect {
    
    @Autowired
    private NotificationService notificationService;
    
    @AfterThrowing(pointcut = "execution(* com.example.service.*.*(..))", throwing = "ex")
    public void handleServiceException(JoinPoint joinPoint, Exception ex) {
        String serviceName = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        log.error("Exception in {}.{}: {}", serviceName, methodName, ex.getMessage(), ex);
        
        // 중요한 서비스 예외인 경우 알림 발송
        if (isCriticalService(serviceName)) {
            ErrorAlert alert = ErrorAlert.builder()
                                       .serviceName(serviceName)
                                       .methodName(methodName)
                                       .exception(ex.getClass().getSimpleName())
                                       .message(ex.getMessage())
                                       .timestamp(LocalDateTime.now())
                                       .build();
            
            notificationService.sendErrorAlert(alert);
        }
        
        // 메트릭 수집
        Metrics.counter("service.exception", 
                       "service", serviceName,
                       "method", methodName,
                       "exception", ex.getClass().getSimpleName())
               .increment();
    }
    
    private boolean isCriticalService(String serviceName) {
        return Arrays.asList("PaymentService", "OrderService", "UserService")
                     .contains(serviceName);
    }
}
```

### 5. @Around - 메서드 실행 전후 (가장 강력함)
```java
@Aspect
@Component
public class PerformanceAspect {
    
    private final MeterRegistry meterRegistry;
    
    @Around("@annotation(MonitorPerformance)")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint, 
                                   MonitorPerformance annotation) throws Throwable {
        
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // 실행 전 로직
        Timer.Sample sample = Timer.start(meterRegistry);
        MDC.put("method", className + "." + methodName);
        
        try {
            // 실제 메서드 실행
            Object result = joinPoint.proceed();
            
            // 성공 시 로직
            recordSuccess(className, methodName);
            return result;
            
        } catch (Exception e) {
            // 실패 시 로직
            recordFailure(className, methodName, e);
            
            // 재시도 로직
            if (annotation.retryOnFailure() && isRetriableException(e)) {
                log.warn("Retrying method {} due to {}", methodName, e.getMessage());
                return joinPoint.proceed();
            }
            
            throw e;
            
        } finally {
            // 항상 실행되는 로직
            sample.stop(Timer.builder("method.execution.time")
                            .tag("class", className)
                            .tag("method", methodName)
                            .register(meterRegistry));
            
            MDC.clear();
        }
    }
    
    private void recordSuccess(String className, String methodName) {
        Metrics.counter("method.execution.success",
                       "class", className,
                       "method", methodName)
               .increment();
    }
    
    private void recordFailure(String className, String methodName, Exception e) {
        Metrics.counter("method.execution.failure",
                       "class", className,
                       "method", methodName,
                       "exception", e.getClass().getSimpleName())
               .increment();
    }
    
    private boolean isRetriableException(Exception e) {
        return e instanceof TransientDataAccessException ||
               e instanceof ConnectException ||
               e instanceof SocketTimeoutException;
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MonitorPerformance {
    boolean retryOnFailure() default false;
}
```

## Pointcut 표현식 상세

### 다양한 Pointcut 표현식
```java
@Aspect
@Component
public class AdvancedPointcutAspect {
    
    // 1. execution: 메서드 실행 지점
    @Pointcut("execution(public * com.example.service..*(..))")
    public void publicServiceMethods() {}
    
    // 2. within: 특정 타입 내의 모든 메서드
    @Pointcut("within(com.example.service..*)")
    public void servicePackage() {}
    
    // 3. target: 특정 인터페이스를 구현한 객체
    @Pointcut("target(com.example.service.OrderService)")
    public void orderServiceTarget() {}
    
    // 4. args: 특정 인자를 가진 메서드
    @Pointcut("args(java.lang.String, java.lang.Long)")
    public void stringAndLongArgs() {}
    
    // 5. @annotation: 특정 어노테이션이 붙은 메서드
    @Pointcut("@annotation(org.springframework.web.bind.annotation.GetMapping)")
    public void getMappingMethods() {}
    
    // 6. @within: 특정 어노테이션이 붙은 클래스 내의 모든 메서드
    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void serviceClasses() {}
    
    // 7. @target: 특정 어노테이션이 붙은 클래스의 인스턴스
    @Pointcut("@target(org.springframework.stereotype.Repository)")
    public void repositoryInstances() {}
    
    // 8. @args: 특정 어노테이션이 붙은 인자를 가진 메서드
    @Pointcut("@args(javax.validation.Valid)")
    public void validatedArgs() {}
    
    // 복합 표현식
    @Pointcut("servicePackage() && publicServiceMethods() && !args(String)")
    public void complexPointcut() {}
    
    @Around("complexPointcut()")
    public Object handleComplexPointcut(ProceedingJoinPoint joinPoint) throws Throwable {
        // 복합 조건에 맞는 메서드 처리
        return joinPoint.proceed();
    }
}
```

## 실제 활용 사례

### 1. 분산 락 구현
```java
@Aspect
@Component
public class DistributedLockAspect {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Around("@annotation(DistributedLock)")
    public Object handleDistributedLock(ProceedingJoinPoint joinPoint, 
                                      DistributedLock distributedLock) throws Throwable {
        
        String lockKey = generateLockKey(joinPoint, distributedLock.key());
        String lockValue = UUID.randomUUID().toString();
        long timeout = distributedLock.timeout();
        TimeUnit timeUnit = distributedLock.timeUnit();
        
        boolean acquired = false;
        try {
            // 락 획득 시도
            acquired = redisTemplate.opsForValue()
                                   .setIfAbsent(lockKey, lockValue, 
                                              Duration.of(timeout, timeUnit.toChronoUnit()));
            
            if (!acquired) {
                throw new LockAcquisitionException("Unable to acquire lock: " + lockKey);
            }
            
            // 실제 메서드 실행
            return joinPoint.proceed();
            
        } finally {
            // 락 해제 (Lua 스크립트로 원자성 보장)
            if (acquired) {
                releaseLock(lockKey, lockValue);
            }
        }
    }
    
    private void releaseLock(String lockKey, String lockValue) {
        String luaScript = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
        
        redisTemplate.execute(new DefaultRedisScript<>(luaScript, Long.class), 
                             Arrays.asList(lockKey), lockValue);
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String key();
    long timeout() default 10;
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}

// 사용 예시
@Service
public class InventoryService {
    
    @DistributedLock(key = "'inventory:' + #productId", timeout = 30)
    public void reduceStock(Long productId, int quantity) {
        // 동시에 하나의 스레드만 특정 상품의 재고를 수정할 수 있음
        Product product = productRepository.findById(productId);
        if (product.getStock() >= quantity) {
            product.setStock(product.getStock() - quantity);
            productRepository.save(product);
        } else {
            throw new InsufficientStockException("재고 부족");
        }
    }
}
```

### 2. API 호출 제한 (Rate Limiting)
```java
@Aspect
@Component
public class RateLimitAspect {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Before("@annotation(RateLimit)")
    public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
        String key = generateRateLimitKey(joinPoint, rateLimit);
        String currentCount = redisTemplate.opsForValue().get(key);
        
        if (currentCount == null) {
            // 첫 번째 요청
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(rateLimit.period()));
        } else {
            int count = Integer.parseInt(currentCount);
            if (count >= rateLimit.limit()) {
                throw new RateLimitExceededException(
                    String.format("Rate limit exceeded. Max %d requests per %d seconds", 
                                 rateLimit.limit(), rateLimit.period()));
            }
            redisTemplate.opsForValue().increment(key);
        }
    }
    
    private String generateRateLimitKey(JoinPoint joinPoint, RateLimit rateLimit) {
        String userKey = getCurrentUserId(); // 사용자별 제한
        String methodKey = joinPoint.getSignature().getName();
        return String.format("rate_limit:%s:%s", userKey, methodKey);
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int limit() default 100;    // 요청 수 제한
    int period() default 60;    // 시간 간격 (초)
}
```

### 3. 데이터베이스 트랜잭션 로깅
```java
@Aspect
@Component
public class TransactionLoggingAspect {
    
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object logTransactionBoundary(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        
        log.info("Transaction START - Method: {}", methodName);
        
        try {
            Object result = joinPoint.proceed();
            log.info("Transaction COMMIT - Method: {}", methodName);
            return result;
            
        } catch (Exception e) {
            log.error("Transaction ROLLBACK - Method: {}, Reason: {}", 
                     methodName, e.getMessage());
            throw e;
        }
    }
    
    @AfterReturning("execution(* javax.sql.DataSource.getConnection(..))")
    public void logConnectionAcquisition(JoinPoint joinPoint) {
        log.debug("Database connection acquired");
        
        // 연결 풀 상태 모니터링
        monitorConnectionPool();
    }
    
    private void monitorConnectionPool() {
        // HikariCP의 경우
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDS = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDS.getHikariPoolMXBean();
            
            log.debug("Connection Pool - Active: {}, Idle: {}, Total: {}", 
                     poolBean.getActiveConnections(),
                     poolBean.getIdleConnections(),
                     poolBean.getTotalConnections());
        }
    }
}
```

## AOP 한계와 주의사항

### 1. 내부 메서드 호출 문제
```java
@Service
public class OrderService {
    
    @MonitorPerformance
    public void processOrder(OrderRequest request) {
        // 이 메서드는 AOP가 적용됨
        validateOrder(request);  // 내부 호출이므로 AOP가 적용되지 않음
    }
    
    @MonitorPerformance
    public void validateOrder(OrderRequest request) {
        // 외부에서 직접 호출될 때만 AOP 적용됨
    }
}

// 해결 방법 1: Self Injection
@Service
public class OrderService {
    
    @Autowired
    private OrderService self; // 자기 자신을 주입
    
    @MonitorPerformance
    public void processOrder(OrderRequest request) {
        self.validateOrder(request); // 프록시를 통한 호출
    }
    
    @MonitorPerformance
    public void validateOrder(OrderRequest request) {
        // 이제 AOP가 적용됨
    }
}

// 해결 방법 2: ApplicationContext 사용
@Service
public class OrderService {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    public void processOrder(OrderRequest request) {
        OrderService proxy = applicationContext.getBean(OrderService.class);
        proxy.validateOrder(request);
    }
}
```

### 2. private 메서드 문제
```java
@Service
public class UserService {
    
    @MonitorPerformance
    private void validateUser(User user) {
        // private 메서드는 프록시할 수 없으므로 AOP 적용 안됨
    }
    
    @MonitorPerformance
    public void createUser(User user) {
        // public 메서드만 AOP 적용 가능
        validateUser(user);
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "Spring AOP와 AspectJ의 차이점은?"
**답변 포인트:**
- **Spring AOP**: 프록시 기반, 런타임 위빙, Spring 컨테이너 관리 빈만 적용
- **AspectJ**: 바이트코드 조작, 컴파일/로드 타임 위빙, 모든 객체에 적용 가능
- **성능**: AspectJ가 더 빠름 (런타임 프록시 오버헤드 없음)
- **기능**: AspectJ가 더 강력함 (필드 접근, 생성자 호출 등)

### Q2: "JDK Dynamic Proxy와 CGLIB의 차이점은?"
**답변 포인트:**
- **JDK Dynamic Proxy**: 인터페이스 기반, JDK 내장, 빠른 생성
- **CGLIB**: 클래스 기반, 바이트코드 조작, 메모리 사용량 높음
- **제약사항**: CGLIB은 final 클래스/메서드 프록시 불가
- **Spring Boot 기본값**: CGLIB (proxyTargetClass=true)

### Q3: "AOP 성능 오버헤드는 어느 정도인가요?"
**답변 포인트:**
- **프록시 생성 비용**: 초기화 시점에 발생
- **메서드 호출 오버헤드**: 약 1-2% 정도의 성능 저하
- **메모리 사용량**: 프록시 객체만큼 추가 메모리 필요
- **최적화**: 불필요한 Aspect 제거, Pointcut 최적화

## 실무 베스트 프랙티스

1. **Pointcut 재사용**: @Pointcut으로 공통 지점 정의
2. **성능 고려**: 꼭 필요한 곳에만 적용
3. **예외 처리**: Around Advice에서 적절한 예외 처리
4. **테스트**: AOP 로직도 단위 테스트 작성
5. **문서화**: Aspect의 역할과 적용 범위 명확히 문서화

AOP는 횡단 관심사를 깔끔하게 분리할 수 있는 강력한 도구이지만, 남용하면 코드 흐름을 이해하기 어려워질 수 있으므로 신중하게 사용해야 합니다.