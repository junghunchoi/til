# Spring IoC 컨테이너와 프록시 메커니즘 내부 동작 원리

> "Spring을 6년 써왔다면 내부를 설명할 수 있어야 한다" - 시니어 면접 필수 주제
> 기존 파일(spring-bean-lifecycle.md, spring-aop.md, spring-transactional.md)의 원리 레벨 심화편

---

## 1. IoC 컨테이너의 계층 구조

### 1.1 BeanFactory vs ApplicationContext

```
BeanFactory (최소 컨테이너)
└── ApplicationContext (확장 컨테이너) ← Spring이 실제로 사용
    ├── ClassPathXmlApplicationContext
    ├── AnnotationConfigApplicationContext
    └── AnnotationConfigServletWebServerApplicationContext  ← Spring Boot 웹
```

```java
// BeanFactory: 최소 기능만 (지연 초기화가 기본)
BeanFactory factory = new DefaultListableBeanFactory();
// getBean() 호출 시점에 빈 생성

// ApplicationContext: BeanFactory 확장
ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
// 시작 시점에 모든 싱글톤 빈을 즉시 초기화 (기본값)
```

| 기능 | BeanFactory | ApplicationContext |
|------|------------|-------------------|
| 빈 관리 | ✅ | ✅ |
| 국제화(MessageSource) | ❌ | ✅ |
| 이벤트 발행(ApplicationEventPublisher) | ❌ | ✅ |
| 환경 변수 처리(Environment) | ❌ | ✅ |
| BeanPostProcessor 자동 등록 | ❌ (수동) | ✅ (자동) |
| AOP 통합 | ❌ | ✅ |

**ApplicationContext가 BeanFactory보다 중요한 이유:**
> BeanPostProcessor 자동 등록 덕분에 @Autowired, @Transactional, @Async 등의 어노테이션이 자동으로 처리됩니다. BeanFactory에서는 이를 수동으로 등록해야 합니다.

---

## 2. BeanDefinition - 빈의 설계도

### 2.1 BeanDefinition이란?

```java
// @Component, @Bean 등을 처리할 때 Spring이 내부적으로 만드는 "설계도"
// 실제 인스턴스(객체)와 분리된 메타데이터

// BeanDefinition에 담기는 정보:
// - 클래스 정보 (어떤 클래스로 만들 것인가)
// - 스코프 (singleton, prototype, ...)
// - 초기화 여부 (lazyInit)
// - 의존성 정보
// - 생성자/팩토리 메서드 정보

// 직접 확인하는 방법
ConfigurableApplicationContext ctx = ...;
BeanDefinition def = ctx.getBeanFactory().getBeanDefinition("orderService");
System.out.println(def.getScope());         // "singleton"
System.out.println(def.getBeanClassName()); // "com.example.OrderService"
System.out.println(def.isLazyInit());       // false
```

**설계도(BeanDefinition)와 실체(Bean Instance)의 분리 의미:**
```
BeanDefinition은 ApplicationContext 시작 시 모두 등록됨
Bean Instance는 (기본값으로) ApplicationContext 시작 시 모두 생성됨

이 분리 덕분에:
- 테스트에서 BeanDefinition을 바꾸어 Mock으로 교체 가능
- 조건부 생성 (@Conditional)이 BeanDefinition 등록 단계에서 결정
- 빈 등록과 빈 생성을 분리하여 생명주기 제어 용이
```

### 2.2 BeanDefinition 등록 과정

```
@SpringBootApplication
│
├── @ComponentScan → ComponentScanAnnotationParser
│   → ClassPathBeanDefinitionScanner
│   → @Component 클래스 탐색
│   → BeanDefinitionRegistry에 BeanDefinition 등록
│
├── @Configuration + @Bean → ConfigurationClassParser
│   → @Bean 메서드를 FactoryMethodBeanDefinition으로 등록
│
└── Auto-Configuration → AutoConfigurationImportSelector
    → spring.factories / AutoConfiguration.imports 파일 읽기
    → @Conditional 평가 후 BeanDefinition 등록
```

```java
// BeanDefinitionRegistry를 활용한 커스텀 빈 등록 (고급)
@Configuration
public class DynamicBeanConfig implements BeanDefinitionRegistryPostProcessor {
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        // 런타임에 BeanDefinition 추가/수정/삭제 가능
        GenericBeanDefinition def = new GenericBeanDefinition();
        def.setBeanClass(DynamicService.class);
        def.setScope("singleton");
        registry.registerBeanDefinition("dynamicService", def);
    }
}
```

---

## 3. 의존성 주입(DI) 내부 처리

### 3.1 @Autowired 처리 과정

```java
// @Autowired를 처리하는 BeanPostProcessor
// AutowiredAnnotationBeanPostProcessor가 담당

@Service
public class OrderService {
    @Autowired
    private PaymentService paymentService;  // 어떻게 주입되는가?
}

// 처리 흐름:
// 1. OrderService 인스턴스 생성 (생성자 호출)
// 2. AutowiredAnnotationBeanPostProcessor가 필드 스캔
// 3. @Autowired 발견 → PaymentService 타입으로 빈 검색
// 4. 단일 빈 → 주입 / 여러 빈 → @Primary 또는 @Qualifier 확인
// 5. 없음 → NoSuchBeanDefinitionException
//    (required=false이면 null 주입)
```

### 3.2 생성자 주입 vs 필드 주입

```java
// 필드 주입 (비권장)
@Service
public class OrderService {
    @Autowired
    private PaymentService paymentService;
    // 단점: 불변성 없음, 순환 의존성 감지 어려움, 테스트 불편
}

// 생성자 주입 (권장)
@Service
public class OrderService {
    private final PaymentService paymentService;  // final 가능

    @Autowired  // 단일 생성자면 생략 가능 (Spring 4.3+)
    public OrderService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
    // 장점: 불변성, 순환 의존성 즉시 감지, 테스트 용이
}
```

---

## 4. 순환 의존성과 3-Level Cache

### 4.1 순환 의존성 발생

```java
@Service
class A {
    @Autowired B b;
}

@Service
class B {
    @Autowired A a;
}

// 생성자 주입 시: BeanCurrentlyInCreationException 즉시 발생 (Spring 6+)
// 세터/필드 주입 시: Spring이 3-Level Cache로 해결 (단, 권장하지 않음)
```

### 4.2 3-Level Cache 순환 의존성 해결 과정

```
세 가지 캐시:
1. singletonObjects      (1차 캐시): 완전히 초기화된 빈
2. earlySingletonObjects (2차 캐시): 초기화 중인 빈 (AOP 프록시 포함)
3. singletonFactories    (3차 캐시): 빈 생성 팩토리 (ObjectFactory)
```

```
A가 B를 의존, B가 A를 의존하는 경우:

Step 1: A 생성 시작
        → A가 생성 중임을 표시 (currentlyInCreation에 추가)
        → A의 ObjectFactory를 3차 캐시(singletonFactories)에 등록

Step 2: A의 의존성 확인 → B 필요
        → B 생성 시작
        → B의 ObjectFactory를 3차 캐시에 등록

Step 3: B의 의존성 확인 → A 필요
        → 1차 캐시 확인: 없음
        → 2차 캐시 확인: 없음
        → 3차 캐시 확인: A의 ObjectFactory 발견!
        → ObjectFactory.getObject() 호출 → 초기화되지 않은 A의 프록시 반환
        → 이 프록시를 2차 캐시로 이동

Step 4: B가 미완성 A 프록시를 주입받아 초기화 완료
        → B를 1차 캐시에 등록

Step 5: A에 완성된 B를 주입하여 초기화 완료
        → A를 1차 캐시에 등록
        → 2차, 3차 캐시에서 A 제거

결과: 두 빈 모두 초기화 완료, 서로 참조
```

```java
// 3차 캐시가 필요한 이유: AOP 프록시 때문
// A가 AOP 프록시 대상이면, B가 주입받는 것은 원본 A가 아닌 A의 프록시여야 함
// 3차 캐시의 ObjectFactory가 AOP 프록시를 생성하여 반환
// 2차 캐시는 이 프록시를 저장하여 재사용
```

**Spring 6+에서 순환 의존성 기본 비활성화:**
```yaml
# application.yml
spring:
  main:
    allow-circular-references: false  # 기본값 (Spring Boot 2.6+)
```

---

## 5. JDK Dynamic Proxy vs CGLIB Proxy

### 5.1 JDK Dynamic Proxy

```java
// 조건: 대상 클래스가 인터페이스를 구현해야 함
public interface OrderService {
    Order createOrder(OrderRequest request);
}

@Service
public class OrderServiceImpl implements OrderService {
    @Override
    public Order createOrder(OrderRequest request) { ... }
}

// Spring이 내부적으로 하는 일 (의사코드):
OrderService proxy = (OrderService) Proxy.newProxyInstance(
    OrderServiceImpl.class.getClassLoader(),
    new Class[]{OrderService.class},    // 인터페이스 배열 필요!
    new TransactionInterceptor(...)     // InvocationHandler
);
// proxy.createOrder() 호출 시 → InvocationHandler.invoke() 실행
// → 트랜잭션 시작 → 실제 메서드 실행 → 트랜잭션 커밋/롤백

// 주의: 리플렉션 기반이므로 상대적으로 느릴 수 있음
// (Java 8 이후 많이 개선됨)
```

### 5.2 CGLIB Proxy

```java
// 조건: 인터페이스 없어도 됨 (클래스를 상속하여 서브클래스 생성)
@Service
public class OrderService {  // 인터페이스 없음
    @Transactional
    public Order createOrder(OrderRequest request) { ... }
}

// Spring이 바이트코드 조작으로 서브클래스를 생성:
// OrderService$$EnhancerBySpringCGLIB$$xxxxx extends OrderService {
//     @Override
//     public Order createOrder(OrderRequest request) {
//         // 트랜잭션 처리 + super.createOrder() 호출
//     }
// }
```

**CGLIB의 제약사항:**
```java
// 1. final 클래스는 상속 불가 → 프록시 생성 불가
@Service
public final class OrderService { ... }  // CGLIB 프록시 불가!

// 2. final 메서드는 오버라이드 불가 → 해당 메서드에는 AOP 적용 안 됨
@Service
public class OrderService {
    @Transactional
    public final Order createOrder(...) { ... }  // @Transactional 무시됨!
}

// 3. 기본 생성자(no-arg constructor) 필요 (Spring 4+ CGLIB objenesis로 완화)
// Spring Boot 기본적으로 objenesis를 사용하여 기본 생성자 없어도 됨
```

### 5.3 Spring Boot의 기본값과 선택 기준

```yaml
# Spring Boot 2.x+: 기본값 CGLIB (spring.aop.proxy-target-class=true)
spring:
  aop:
    proxy-target-class: true   # CGLIB 강제 (기본값)
    # false로 설정 시: 인터페이스 있으면 JDK Proxy, 없으면 CGLIB
```

```java
// 실무 영향: 주입받은 빈의 타입 확인
@Autowired
OrderService orderService;  // 실제로는 CGLIB 프록시

// instanceof 체크는 정상 동작 (상속 관계이므로)
orderService instanceof OrderService  // true

// 정확한 클래스 타입 확인 시 주의
orderService.getClass() == OrderService.class         // false! (CGLIB 서브클래스)
orderService.getClass().getSuperclass() == OrderService.class  // true

// 스프링 프록시 여부 확인
AopUtils.isAopProxy(orderService)       // true
AopUtils.isCglibProxy(orderService)    // true
AopUtils.isJdkDynamicProxy(orderService) // false
```

---

## 6. @Transactional 프록시 동작 심화

### 6.1 셀프 호출(Self-Invocation) 문제

```java
@Service
public class OrderService {

    @Transactional
    public void createOrder(OrderRequest request) {
        // 이 메서드는 프록시를 통해 호출됨 → @Transactional 동작
    }

    public void batchCreateOrders(List<OrderRequest> requests) {
        for (OrderRequest request : requests) {
            this.createOrder(request);  // ← 셀프 호출!
            // 이 호출은 프록시를 거치지 않음 → @Transactional 무시!
        }
    }
}

// 왜? 외부에서 batchCreateOrders()를 호출하면:
// 호출자 → 프록시.batchCreateOrders() → 실제 OrderService.batchCreateOrders()
// 실제 OrderService 안에서 this.createOrder() 호출
// = 실제 OrderService.createOrder() 직접 호출 (프록시 우회)
```

**해결책 3가지:**

```java
// 해결책 1: 클래스 분리 (가장 권장)
@Service
public class OrderBatchService {
    @Autowired
    private OrderService orderService;  // 외부 주입

    public void batchCreateOrders(List<OrderRequest> requests) {
        for (OrderRequest request : requests) {
            orderService.createOrder(request);  // 프록시를 통해 호출
        }
    }
}

// 해결책 2: Self-Injection (ApplicationContext에서 자신을 주입)
@Service
public class OrderService {
    @Autowired
    @Lazy  // 순환 의존성 방지
    private OrderService self;

    public void batchCreateOrders(List<OrderRequest> requests) {
        for (OrderRequest request : requests) {
            self.createOrder(request);  // self = 프록시, 정상 동작
        }
    }
}

// 해결책 3: AopContext (비권장, 설정 필요)
@EnableAspectJAutoProxy(exposeProxy = true)  // 설정 필요
public class OrderService {
    public void batchCreateOrders(List<OrderRequest> requests) {
        OrderService proxy = (OrderService) AopContext.currentProxy();
        proxy.createOrder(request);  // 프록시를 통해 호출
    }
}
```

### 6.2 @Transactional 내부 처리 흐름

```
외부 호출
    │
    ▼
TransactionInterceptor (프록시의 MethodInterceptor)
    │
    ├── TransactionAttributeSource.getTransactionAttribute()
    │   → @Transactional 속성 읽기 (propagation, isolation, readOnly 등)
    │
    ├── PlatformTransactionManager.getTransaction()
    │   → 현재 스레드에 트랜잭션이 있는지 확인 (TransactionSynchronizationManager)
    │   → propagation에 따라 새 트랜잭션 시작 또는 기존 참여
    │
    ├── 실제 메서드 실행
    │
    └── 예외 발생 여부에 따라:
        → rollbackFor 조건 확인
        → commit() 또는 rollback()
```

```java
// TransactionSynchronizationManager: ThreadLocal 기반으로 트랜잭션 정보 관리
// 트랜잭션이 같은 스레드에서만 공유되는 이유

// @Async 메서드에서 @Transactional이 별도 트랜잭션을 사용하는 이유:
// @Async → 다른 스레드에서 실행
// → 다른 스레드의 TransactionSynchronizationManager
// → 부모 트랜잭션에 참여 불가 (항상 새 트랜잭션)
```

---

## 7. Spring Boot Auto-Configuration 내부 동작

### 7.1 @SpringBootApplication의 정체

```java
// @SpringBootApplication = 다음 세 어노테이션의 합성
@SpringBootConfiguration  // = @Configuration
@EnableAutoConfiguration  // Auto-Configuration 활성화
@ComponentScan            // 현재 패키지부터 컴포넌트 스캔
public @interface SpringBootApplication { ... }
```

### 7.2 Auto-Configuration 처리 과정

```
@EnableAutoConfiguration
│
└── AutoConfigurationImportSelector
    │
    ├── 1. 파일 읽기
    │   Spring Boot 2.x: META-INF/spring.factories
    │   Spring Boot 3.x: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │   (spring-boot-autoconfigure-X.X.X.jar 내부)
    │
    ├── 2. @Conditional 조건 평가 (각 AutoConfiguration 클래스)
    │   → 조건 통과한 것만 BeanDefinition 등록
    │
    └── 3. 사용자 정의 빈을 우선 (ConditionalOnMissingBean)
        → 사용자가 정의한 빈이 있으면 Auto-Configuration 빈 등록 안 함
```

### 7.3 @Conditional 어노테이션 종류

```java
// 가장 많이 사용되는 @Conditional 조건들

@ConditionalOnClass(DataSource.class)
// 클래스패스에 DataSource 클래스가 있을 때만 등록
// 예: JDBC 관련 의존성이 있을 때만 DataSourceAutoConfiguration 활성화

@ConditionalOnMissingBean(DataSource.class)
// 사용자가 DataSource 빈을 등록하지 않았을 때만 등록
// 핵심: 사용자 커스터마이징 허용

@ConditionalOnProperty(
    prefix = "spring.redis",
    name = "host",
    havingValue = "localhost",
    matchIfMissing = true
)
// 특정 프로퍼티 값에 따라 조건부 등록

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
// Servlet 웹 애플리케이션일 때만 등록

@ConditionalOnExpression("${feature.enabled:false}")
// SpEL 표현식 평가
```

### 7.4 DataSourceAutoConfiguration 예시 분석

```java
// spring-boot-autoconfigure 내부
@AutoConfiguration(before = SqlInitializationAutoConfiguration.class)
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @Conditional(EmbeddedDatabaseCondition.class)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @Import(EmbeddedDataSourceConfiguration.class)
    protected static class EmbeddedDatabaseConfiguration { }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @ConditionalOnSingleCandidate(DataSourceProperties.class)
    protected static class PooledDataSourceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DataSourcePoolMetadataProvider pooledDataSourceMetadataProvider(...) { ... }
    }
}

// 사용자가 application.yml에 spring.datasource.url을 설정하거나
// @Bean으로 DataSource를 직접 정의하면
// @ConditionalOnMissingBean에 의해 위 설정이 활성화되지 않음
```

### 7.5 커스텀 Auto-Configuration 만들기

```java
// 라이브러리 개발 시 활용
// 1. AutoConfiguration 클래스 작성
@AutoConfiguration
@ConditionalOnClass(MyService.class)
@EnableConfigurationProperties(MyServiceProperties.class)
public class MyServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MyService myService(MyServiceProperties properties) {
        return new MyService(properties.getEndpoint());
    }
}

// 2. META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 파일에 등록
// com.example.MyServiceAutoConfiguration
```

---

## 8. BeanPostProcessor와 Spring의 확장 포인트

### 8.1 BeanPostProcessor - 빈 초기화 전/후 처리

```java
// Spring의 핵심 확장 포인트
// @Autowired 처리, @Transactional 프록시 생성, @Async 처리 등이 모두 BeanPostProcessor

public interface BeanPostProcessor {
    // 빈 초기화(@PostConstruct, afterPropertiesSet) 전에 호출
    Object postProcessBeforeInitialization(Object bean, String beanName);

    // 빈 초기화 후에 호출 ← 프록시를 여기서 교체
    Object postProcessAfterInitialization(Object bean, String beanName);
}

// 주요 BeanPostProcessor 구현체들:
// AutowiredAnnotationBeanPostProcessor → @Autowired 처리
// CommonAnnotationBeanPostProcessor   → @Resource, @PostConstruct 처리
// AbstractAdvisingBeanPostProcessor   → @Transactional, @Async 프록시 생성
// ApplicationContextAwareProcessor    → ApplicationContextAware 주입
```

```java
// @Transactional 프록시가 어떻게 생성되는가
// AbstractAutoProxyCreator.postProcessAfterInitialization()에서:
// 1. 현재 빈이 AOP 대상인지 확인 (@Transactional, @Aspect 포인트컷 등)
// 2. AOP 대상이면 원본 빈 대신 프록시를 반환
// 3. ApplicationContext는 원본 빈 대신 프록시를 빈으로 관리
```

### 8.2 BeanFactoryPostProcessor - BeanDefinition 수정

```java
// 빈 인스턴스 생성 전, BeanDefinition 등록 후 실행
// BeanDefinition을 수정하거나 추가 가능

// 대표적 예: PropertyPlaceholderConfigurer (${} 처리)
// @Value("${server.port}") 같은 프로퍼티 치환이 이 단계에서 일어남
```

---

## 9. Spring 싱글톤 빈의 Thread-Safety

```java
// 싱글톤 빈 자체는 Thread-Safe하지 않음!
@Service  // 싱글톤 빈
public class CounterService {
    private int count = 0;  // 상태를 가진 필드 → 스레드 안전하지 않음!

    public void increment() {
        count++;  // 여러 스레드에서 동시 접근 시 Race Condition
    }
}

// 올바른 방법 1: 상태를 갖지 않도록 설계 (가장 권장)
@Service
public class OrderService {
    // 모든 필드가 다른 빈에 대한 참조 (변경 안 됨)
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    // 지역 변수만 사용 → Thread-Safe
}

// 올바른 방법 2: ThreadLocal 사용
// 올바른 방법 3: AtomicInteger 사용
private AtomicInteger count = new AtomicInteger(0);

// 올바른 방법 4: Prototype 스코프 사용 (빈 생성 비용 있음)
@Service
@Scope("prototype")  // 요청마다 새 인스턴스
public class StatefulService { ... }
```

---

## 10. 면접 예상 질문 & 모범 답변

### Q1. BeanFactory와 ApplicationContext의 차이는?

**답변 포인트:**
> "BeanFactory는 빈 생성과 의존성 주입만 담당하는 최소 컨테이너입니다. ApplicationContext는 BeanFactory를 확장하여 국제화, 이벤트 발행, 환경 변수 처리, BeanPostProcessor 자동 등록 등을 추가로 제공합니다. 실무에서는 항상 ApplicationContext를 사용합니다. BeanFactory는 경량 컨테이너가 필요한 특수 상황에서만 사용합니다. 가장 중요한 차이는 BeanPostProcessor 자동 등록으로, 이것이 없으면 @Autowired, @Transactional 같은 어노테이션이 처리되지 않습니다."

---

### Q2. Spring의 순환 의존성을 3-Level Cache로 어떻게 해결하나요?

**답변 포인트:**
> "Spring은 singletonObjects(완전히 초기화된 빈), earlySingletonObjects(초기화 중인 빈), singletonFactories(빈 생성 팩토리) 세 가지 캐시를 사용합니다. 예를 들어 A가 B를, B가 A를 의존할 때: A 생성을 시작하면서 ObjectFactory를 3차 캐시에 등록합니다. A의 의존성인 B를 생성하다가 A가 필요하면 3차 캐시에서 ObjectFactory로 미완성 A의 프록시를 생성하여 2차 캐시에 저장합니다. B는 이 프록시를 주입받아 완성되고, A도 B를 주입받아 완성됩니다. 3차 캐시가 필요한 이유는 A가 AOP 대상일 때 B가 주입받아야 하는 것이 원본 A가 아닌 A의 프록시이기 때문입니다."

---

### Q3. JDK Dynamic Proxy와 CGLIB Proxy의 차이는?

**답변 포인트:**
> "JDK Dynamic Proxy는 인터페이스 기반입니다. java.lang.reflect.Proxy가 리플렉션으로 인터페이스를 구현한 프록시 클래스를 동적으로 생성합니다. CGLIB Proxy는 클래스 상속 기반입니다. 바이트코드 조작으로 대상 클래스의 서브클래스를 생성합니다. 그래서 final 클래스나 final 메서드에는 적용할 수 없습니다. Spring Boot 2.x부터는 인터페이스가 있어도 기본값으로 CGLIB를 사용합니다(spring.aop.proxy-target-class=true). 이로 인해 getClass()가 원본 클래스와 다를 수 있어 주의가 필요합니다."

---

### Q4. @Transactional 셀프 호출이 왜 동작하지 않나요?

**답변 포인트:**
> "Spring의 @Transactional은 프록시 패턴으로 동작합니다. 외부에서 빈의 메서드를 호출하면 실제로는 프록시 객체의 메서드가 호출되고, 프록시가 트랜잭션을 시작한 후 실제 빈의 메서드를 호출합니다. 하지만 빈 내부에서 this.method()로 자신의 메서드를 호출하면 프록시를 우회하고 실제 객체를 직접 호출합니다. 프록시가 개입하지 않으므로 @Transactional이 무시됩니다. 해결책은 클래스를 분리하여 외부 주입된 프록시를 통해 호출하는 것이 가장 권장됩니다."

---

### Q5. Spring Boot Auto-Configuration은 어떤 순서로 동작하나요?

**답변 포인트:**
> "@SpringBootApplication의 @EnableAutoConfiguration이 AutoConfigurationImportSelector를 활성화합니다. 이 셀렉터가 spring-boot-autoconfigure JAR의 META-INF/spring/AutoConfiguration.imports 파일을 읽어 자동 설정 후보 클래스들을 가져옵니다. 각 Auto-Configuration 클래스의 @Conditional 조건을 평가하여 조건을 통과한 것만 BeanDefinition으로 등록합니다. 가장 중요한 것은 @ConditionalOnMissingBean으로, 사용자가 직접 정의한 빈이 있으면 Auto-Configuration 빈을 등록하지 않아 커스터마이징을 허용합니다. 즉 Auto-Configuration은 '사용자가 설정하지 않은 것만 자동으로 설정한다'가 핵심입니다."

---

### Q6. Spring의 싱글톤 빈은 Thread-Safe한가요?

**답변 포인트:**
> "싱글톤 빈 자체가 Thread-Safe하다는 보장은 없습니다. 싱글톤 빈은 모든 스레드가 공유하는 하나의 인스턴스이므로, 인스턴스 변수에 상태를 저장하면 Race Condition이 발생합니다. Spring이 권장하는 방식은 서비스 빈이 상태를 갖지 않도록(Stateless) 설계하는 것입니다. 즉, 모든 데이터는 지역 변수나 파라미터로 처리하고, 인스턴스 변수는 다른 빈에 대한 불변 참조만 가집니다. 상태가 꼭 필요하면 ThreadLocal이나 Prototype 스코프를 사용합니다."

---

### Q7. BeanPostProcessor가 Spring에서 어떤 역할을 하나요?

**답변 포인트:**
> "BeanPostProcessor는 Spring의 핵심 확장 포인트입니다. 빈 인스턴스가 생성되고 초기화되는 전후에 개입하여 빈을 수정하거나 교체할 수 있습니다. Spring의 주요 기능들이 BeanPostProcessor로 구현됩니다: AutowiredAnnotationBeanPostProcessor가 @Autowired를 처리하고, AbstractAutoProxyCreator가 @Transactional이나 AOP 대상 빈을 프록시로 교체합니다. postProcessAfterInitialization에서 원본 빈 대신 프록시를 반환하면 ApplicationContext는 프록시를 최종 빈으로 관리합니다. 이것이 @Transactional 프록시가 작동하는 메커니즘입니다."

---

### Q8. CGLIB 프록시에서 final 클래스/메서드가 문제 되는 이유는?

**답변 포인트:**
> "CGLIB Proxy는 대상 클래스를 상속하는 서브클래스를 바이트코드 조작으로 생성합니다. final 클래스는 상속이 불가능하므로 프록시 생성 자체가 실패합니다. final 메서드는 상속은 되지만 오버라이드할 수 없어, 프록시의 해당 메서드가 부모의 실제 로직을 그대로 실행합니다. 프록시 인터셉터(AOP Advice)가 개입할 기회가 없어 @Transactional 같은 어노테이션이 무시됩니다. 실무에서는 Spring 빈으로 사용할 클래스에 불필요한 final을 붙이지 않도록 주의합니다."

---

### Q9. @Conditional 어노테이션의 동작 원리는?

**답변 포인트:**
> "@Conditional은 조건에 따라 BeanDefinition 등록 여부를 결정합니다. @ConditionalOnClass는 특정 클래스가 클래스패스에 있는지, @ConditionalOnMissingBean은 특정 타입의 빈이 이미 등록되어 있지 않은지, @ConditionalOnProperty는 특정 프로퍼티 값이 조건에 맞는지 확인합니다. 평가 순서는 중요한데, 사용자 설정(@ComponentScan)이 Auto-Configuration보다 먼저 등록됩니다. 그래서 @ConditionalOnMissingBean이 사용자 빈을 발견하고 Auto-Configuration 빈을 건너뛸 수 있습니다."

---

### Q10. @Async가 동작하지 않는 경우는 어떤 경우인가요?

**답변 포인트:**
> "@Async도 @Transactional과 마찬가지로 프록시 기반으로 동작합니다. 따라서 셀프 호출(this.asyncMethod())은 프록시를 우회하여 비동기로 실행되지 않습니다. 또한 @Async 메서드는 private이면 CGLIB가 오버라이드할 수 없어 동작하지 않습니다. @EnableAsync가 설정되지 않은 경우에도 동작하지 않습니다. 비동기 메서드에서 발생한 예외는 호출자에게 전파되지 않고 AsyncUncaughtExceptionHandler로 처리되는데, 이를 설정하지 않으면 예외가 조용히 사라질 수 있습니다."

---

### Q11. Spring의 @Transactional propagation REQUIRED와 REQUIRES_NEW의 차이는?

**답변 포인트:**
> "REQUIRED(기본값)는 현재 트랜잭션이 있으면 참여하고, 없으면 새로 시작합니다. 부모와 자식이 같은 트랜잭션을 공유하므로 어느 한쪽에서 예외가 발생하면 전체가 롤백됩니다. REQUIRES_NEW는 현재 트랜잭션이 있어도 무조건 새 트랜잭션을 시작합니다. 부모 트랜잭션을 일시 중단(suspend)하고 자식 트랜잭션을 완전히 독립적으로 실행합니다. 자식의 커밋/롤백이 부모에 영향을 주지 않습니다. 로그 기록이나 알림 발송처럼 메인 비즈니스 로직 실패와 무관하게 독립적으로 커밋해야 할 때 사용합니다."

---

### Q12. Spring에서 빈의 스코프 종류와 각각의 특징은?

**답변 포인트:**
> "singleton(기본값)은 ApplicationContext당 하나의 인스턴스로, 가장 일반적입니다. prototype은 getBean() 또는 @Autowired 시마다 새 인스턴스를 생성합니다. 단, Spring은 prototype 빈의 소멸 처리를 하지 않습니다. request는 HTTP 요청당 하나로 웹 환경에서만 사용합니다. session은 HTTP 세션당 하나입니다. singleton 빈에서 prototype 빈을 주입받으면 prototype이 사실상 singleton처럼 동작하는 문제가 있습니다. 이를 해결하려면 ObjectProvider나 @Lookup, @Scope(proxyMode=INTERFACES)를 사용합니다."

---

## 11. 학습 체크리스트

- [ ] BeanFactory와 ApplicationContext의 차이를 주요 기능 목록으로 설명할 수 있다
- [ ] BeanDefinition이 Bean Instance와 다른 것임을 설명할 수 있다
- [ ] Spring의 3-Level Cache 순환 의존성 해결 과정을 Step별로 설명할 수 있다
- [ ] JDK Dynamic Proxy와 CGLIB Proxy의 생성 조건과 제약을 설명할 수 있다
- [ ] @Transactional 셀프 호출이 동작하지 않는 이유를 프록시 관점으로 설명할 수 있다
- [ ] @Transactional 셀프 호출의 3가지 해결책을 설명할 수 있다
- [ ] Spring Boot Auto-Configuration의 동작 순서를 파일 읽기부터 BeanDefinition 등록까지 설명할 수 있다
- [ ] @ConditionalOnMissingBean이 커스터마이징을 어떻게 허용하는지 설명할 수 있다
- [ ] CGLIB 프록시에서 final이 문제 되는 이유를 설명할 수 있다
- [ ] BeanPostProcessor의 역할과 주요 구현체를 설명할 수 있다
- [ ] Spring 싱글톤 빈이 Thread-Safe하지 않은 이유를 설명하고 해결책을 제시할 수 있다
- [ ] @Transactional의 REQUIRED와 REQUIRES_NEW 차이를 실제 시나리오로 설명할 수 있다

---

## 12. 연관 학습 파일

- [`spring-bean-lifecycle.md`](./spring-bean-lifecycle.md) - 빈 생명주기 상세 (선행 학습 권장)
- [`spring-aop.md`](./spring-aop.md) - AOP 개념과 활용 (선행 학습 권장)
- [`spring-transactional.md`](./spring-transactional.md) - @Transactional 동작 원리 (선행 학습 권장)
- [`../java/jvm-architecture.md`](../java/jvm-architecture.md) - 클래스 로더 (커스텀 ClassLoader와 Spring 연관)
- [`../java/java-concurrency-threading.md`](../java/java-concurrency-threading.md) - ThreadLocal (TransactionSynchronizationManager 이해)
