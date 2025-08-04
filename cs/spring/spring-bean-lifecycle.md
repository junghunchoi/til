# 스프링 빈(Bean)의 생명주기와 실제 운영환경에서의 주의사항

## 스프링 빈 생명주기 개요

### 빈 생명주기 단계
```
1. 빈 인스턴스화 (Instantiation)
2. 의존성 주입 (Dependency Injection)
3. 빈 후처리기 적용 (BeanPostProcessor)
4. 초기화 콜백 (Initialization Callbacks)
5. 빈 사용 (Bean Usage)
6. 소멸 콜백 (Destruction Callbacks)
```

## 상세한 빈 생명주기

### 1. 빈 인스턴스화와 의존성 주입
```java
@Component
public class OrderService {
    
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private EmailService emailService; // Setter 주입
    
    // 1. 생성자 호출 (인스턴스화)
    public OrderService(PaymentService paymentService, InventoryService inventoryService) {
        System.out.println("OrderService 생성자 호출");
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
    }
    
    // 2. Setter 주입 (의존성 주입)
    @Autowired
    public void setEmailService(EmailService emailService) {
        System.out.println("EmailService 의존성 주입");
        this.emailService = emailService;
    }
}
```

### 2. BeanPostProcessor 적용
```java
// 모든 빈에 적용되는 후처리기
@Component
public class CustomBeanPostProcessor implements BeanPostProcessor {
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof OrderService) {
            System.out.println("Before Initialization: " + beanName);
            // 초기화 전 커스텀 로직
        }
        return bean;
    }
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof OrderService) {
            System.out.println("After Initialization: " + beanName);
            // 초기화 후 커스텀 로직 (프록시 생성 등)
        }
        return bean;
    }
}

// 특정 어노테이션을 처리하는 후처리기
@Component
public class MetricsAnnotationProcessor implements BeanPostProcessor {
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        
        if (beanClass.isAnnotationPresent(EnableMetrics.class)) {
            // 메트릭 수집을 위한 프록시 생성
            return createMetricsProxy(bean);
        }
        
        return bean;
    }
    
    private Object createMetricsProxy(Object target) {
        return Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            target.getClass().getInterfaces(),
            (proxy, method, args) -> {
                long startTime = System.currentTimeMillis();
                try {
                    return method.invoke(target, args);
                } finally {
                    long duration = System.currentTimeMillis() - startTime;
                    System.out.println("Method " + method.getName() + " took " + duration + "ms");
                }
            }
        );
    }
}
```

### 3. 초기화 콜백
```java
@Component
public class DatabaseConnectionService implements InitializingBean, DisposableBean {
    
    private DataSource dataSource;
    private Connection connection;
    
    // 방법 1: InitializingBean 인터페이스
    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("InitializingBean.afterPropertiesSet() 호출");
        initializeConnection();
    }
    
    // 방법 2: @PostConstruct 어노테이션 (권장)
    @PostConstruct
    public void init() {
        System.out.println("@PostConstruct init() 호출");
        setupInitialData();
    }
    
    // 방법 3: @Bean의 initMethod 속성
    public void customInit() {
        System.out.println("Custom init method 호출");
        performHealthCheck();
    }
    
    private void initializeConnection() {
        try {
            this.connection = dataSource.getConnection();
            System.out.println("데이터베이스 연결 초기화 완료");
        } catch (SQLException e) {
            throw new RuntimeException("데이터베이스 연결 실패", e);
        }
    }
    
    private void setupInitialData() {
        // 초기 데이터 설정 로직
        System.out.println("초기 데이터 설정 완료");
    }
    
    private void performHealthCheck() {
        // 헬스 체크 로직
        System.out.println("헬스 체크 완료");
    }
    
    // 소멸 콜백
    @Override
    public void destroy() throws Exception {
        System.out.println("DisposableBean.destroy() 호출");
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
    
    @PreDestroy
    public void cleanup() {
        System.out.println("@PreDestroy cleanup() 호출");
        // 리소스 정리
    }
}
```

### 4. 빈 설정을 통한 초기화/소멸 메서드 지정
```java
@Configuration
public class ServiceConfig {
    
    @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
    public ExternalApiClient externalApiClient() {
        return new ExternalApiClient();
    }
    
    // destroyMethod = "(inferred)" - close() 또는 shutdown() 메서드 자동 호출
    @Bean(destroyMethod = "(inferred)")
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://localhost:3306/mydb");
        return dataSource;
    }
}

public class ExternalApiClient {
    private RestTemplate restTemplate;
    
    public void customInit() {
        System.out.println("ExternalApiClient 초기화");
        this.restTemplate = new RestTemplate();
        // 연결 풀 설정, 타임아웃 설정 등
    }
    
    public void customDestroy() {
        System.out.println("ExternalApiClient 정리");
        // 연결 풀 정리, 리소스 해제
    }
}
```

## 초기화 순서와 우선순위

### 초기화 메서드 실행 순서
```java
@Component
public class LifecycleDemo implements InitializingBean {
    
    @PostConstruct
    public void postConstruct() {
        System.out.println("1. @PostConstruct 실행");
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("2. InitializingBean.afterPropertiesSet() 실행");
    }
    
    public void customInit() {
        System.out.println("3. Custom init method 실행");
    }
}

// 출력 결과:
// 1. @PostConstruct 실행
// 2. InitializingBean.afterPropertiesSet() 실행  
// 3. Custom init method 실행
```

### 소멸 메서드 실행 순서
```java
@Component
public class DestructionDemo implements DisposableBean {
    
    @PreDestroy
    public void preDestroy() {
        System.out.println("1. @PreDestroy 실행");
    }
    
    @Override
    public void destroy() throws Exception {
        System.out.println("2. DisposableBean.destroy() 실행");
    }
    
    public void customDestroy() {
        System.out.println("3. Custom destroy method 실행");
    }
}
```

## 실제 운영환경에서의 주의사항

### 1. 초기화 시간과 애플리케이션 시작 시간
```java
@Component
public class SlowInitializationService {
    
    @PostConstruct
    public void init() {
        // 잘못된 예: 시작 시간을 지연시키는 무거운 작업
        try {
            Thread.sleep(10000); // 10초 대기
            loadLargeDataSet();   // 대용량 데이터 로딩
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void loadLargeDataSet() {
        // 대용량 데이터 로딩 로직
    }
}

// 개선된 버전: 지연 초기화 사용
@Component
@Lazy
public class ImprovedInitializationService {
    
    private volatile boolean initialized = false;
    private final Object initLock = new Object();
    
    @PostConstruct
    public void init() {
        // 빠른 초기화만 수행
        System.out.println("Service created but not fully initialized");
    }
    
    public void processRequest() {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    performHeavyInitialization();
                    initialized = true;
                }
            }
        }
        // 실제 비즈니스 로직
    }
    
    private void performHeavyInitialization() {
        // 무거운 초기화 작업을 실제 사용 시점에 수행
    }
}
```

### 2. 의존성 순환 문제
```java
// 문제가 있는 코드: 순환 의존성
@Component
public class ServiceA {
    
    @Autowired
    private ServiceB serviceB;
    
    @PostConstruct
    public void init() {
        serviceB.doSomething(); // ServiceB가 아직 초기화되지 않을 수 있음
    }
}

@Component
public class ServiceB {
    
    @Autowired
    private ServiceA serviceA;
    
    @PostConstruct
    public void init() {
        serviceA.doSomething(); // ServiceA가 아직 초기화되지 않을 수 있음
    }
}

// 해결 방법 1: @Lazy 사용
@Component
public class ServiceA {
    
    @Autowired
    @Lazy
    private ServiceB serviceB; // 지연 주입으로 순환 의존성 해결
    
    @PostConstruct
    public void init() {
        // 초기화 로직에서는 serviceB 사용 금지
    }
    
    public void businessMethod() {
        serviceB.doSomething(); // 실제 사용 시점에 호출
    }
}

// 해결 방법 2: ApplicationListener 사용
@Component
public class ServiceA implements ApplicationListener<ContextRefreshedEvent> {
    
    @Autowired
    private ServiceB serviceB;
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 모든 빈이 초기화된 이후에 실행
        serviceB.doSomething();
    }
}
```

### 3. 예외 처리와 실패 복구
```java
@Component
public class RobustInitializationService {
    
    private boolean initialized = false;
    
    @PostConstruct
    public void init() {
        try {
            performCriticalInitialization();
            initialized = true;
        } catch (Exception e) {
            // 초기화 실패 시 애플리케이션 시작을 중단할지 결정
            log.error("Critical initialization failed", e);
            throw new BeanInitializationException("Cannot initialize service", e);
        }
        
        try {
            performOptionalInitialization();
        } catch (Exception e) {
            // 선택적 초기화는 실패해도 애플리케이션 시작 허용
            log.warn("Optional initialization failed, continuing with degraded functionality", e);
        }
    }
    
    private void performCriticalInitialization() {
        // 반드시 성공해야 하는 초기화
        connectToDatabase();
        loadEssentialConfiguration();
    }
    
    private void performOptionalInitialization() {
        // 실패해도 되는 초기화
        loadCacheData();
        setupMetrics();
    }
    
    public void doBusinessLogic() {
        if (!initialized) {
            throw new IllegalStateException("Service not properly initialized");
        }
        // 비즈니스 로직
    }
}
```

### 4. 리소스 정리와 메모리 누수 방지
```java
@Component
public class ResourceManagementService implements DisposableBean {
    
    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutor;
    private final List<Closeable> resources = new ArrayList<>();
    
    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(10);
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        
        // 리소스 추적
        FileInputStream inputStream = new FileInputStream("config.properties");
        resources.add(inputStream);
    }
    
    @Override
    public void destroy() throws Exception {
        log.info("Shutting down ResourceManagementService");
        
        // ExecutorService 정리
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                        log.warn("ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // ScheduledExecutorService 정리
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        
        // 기타 리소스 정리
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException e) {
                log.warn("Failed to close resource", e);
            }
        }
        
        log.info("ResourceManagementService shutdown complete");
    }
}
```

### 5. 프로파일별 빈 초기화
```java
@Configuration
public class EnvironmentSpecificConfig {
    
    @Bean
    @Profile("dev")
    public DataInitializer devDataInitializer() {
        return new DataInitializer() {
            @PostConstruct
            public void init() {
                log.info("개발 환경용 테스트 데이터 초기화");
                loadTestData();
            }
        };
    }
    
    @Bean
    @Profile("prod")
    public DataInitializer prodDataInitializer() {
        return new DataInitializer() {
            @PostConstruct
            public void init() {
                log.info("운영 환경용 데이터 초기화");
                validateProductionData();
            }
        };
    }
}

@Component
@Profile("!test") // 테스트 환경에서는 제외
public class ProductionOnlyService {
    
    @PostConstruct
    public void init() {
        // 운영 환경에서만 실행되는 초기화 로직
        setupMonitoring();
        registerHealthChecks();
    }
}
```

## 모니터링과 디버깅

### 빈 생명주기 이벤트 모니터링
```java
@Component
public class BeanLifecycleMonitor implements ApplicationListener<ApplicationEvent> {
    
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            log.info("Application context refreshed - all beans initialized");
        } else if (event instanceof ContextClosedEvent) {
            log.info("Application context closed - bean destruction started");
        }
    }
}

// 커스텀 빈 생명주기 이벤트
@Component
public class CustomBeanLifecycleProcessor implements BeanPostProcessor {
    
    private final MeterRegistry meterRegistry;
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 빈 초기화 시간 측정
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("bean.initialization.time")
                         .tag("bean.name", beanName)
                         .register(meterRegistry));
        
        return bean;
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "@PostConstruct와 InitializingBean의 차이점은?"
**답변 포인트:**
- **@PostConstruct**: JSR-250 표준, Spring에 의존하지 않음
- **InitializingBean**: Spring 전용 인터페이스, Spring에 의존적
- **실행 순서**: @PostConstruct → InitializingBean.afterPropertiesSet()
- **권장사항**: @PostConstruct 사용 권장 (표준이고 테스트하기 쉬움)

### Q2: "빈 생명주기에서 예외가 발생하면 어떻게 되나요?"
**답변 포인트:**
- **초기화 예외**: BeanCreationException 발생, 애플리케이션 컨텍스트 시작 실패
- **소멸 예외**: 로그만 남기고 다른 빈들의 소멸 계속 진행
- **부분 실패 처리**: @Primary, @Conditional 등으로 대체 빈 제공
- **복구 전략**: 재시도 로직, 대체 구현체 활용

### Q3: "Spring Boot에서 빈 생명주기가 다른 점은?"
**답변 포인트:**
- **자동 구성**: @EnableAutoConfiguration으로 자동 빈 등록
- **조건부 빈**: @ConditionalOnProperty, @ConditionalOnClass 등
- **ApplicationRunner/CommandLineRunner**: 애플리케이션 시작 후 실행
- **Graceful Shutdown**: server.shutdown=graceful로 우아한 종료

## 실무 베스트 프랙티스

1. **초기화 시간 최소화**: 무거운 작업은 지연 로딩 활용
2. **예외 처리**: 중요한 초기화와 선택적 초기화 구분
3. **리소스 관리**: DisposableBean으로 확실한 정리
4. **모니터링**: 초기화 시간과 실패 메트릭 수집
5. **테스트**: @MockBean, @TestConfiguration으로 테스트 용이성 확보

빈 생명주기를 정확히 이해하면 애플리케이션의 시작/종료 과정을 효율적으로 관리할 수 있습니다.