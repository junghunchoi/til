# 스프링 WebFlux와 리액티브 프로그래밍 구현

## 리액티브 프로그래밍 핵심 개념

### 왜 리액티브인가?
```java
// 전통적인 블로킹 방식
@RestController
public class BlockingController {
    
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        User user = userService.findById(id);          // DB 블로킹
        Profile profile = profileService.getProfile(id); // 외부 API 블로킹
        user.setProfile(profile);
        return user; // 총 처리 시간 = DB 시간 + API 시간
    }
}

// 리액티브 방식
@RestController
public class ReactiveController {
    
    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        Mono<User> userMono = userService.findById(id);
        Mono<Profile> profileMono = profileService.getProfile(id);
        
        return userMono.zipWith(profileMono)
                      .map(tuple -> {
                          User user = tuple.getT1();
                          Profile profile = tuple.getT2();
                          user.setProfile(profile);
                          return user;
                      }); // 병렬 처리로 시간 단축
    }
}
```

### Reactor 기본 타입

#### Mono (0 또는 1개 요소)
```java
@Service
public class UserService {
    
    // 단일 사용자 조회
    public Mono<User> findById(Long id) {
        return Mono.fromCallable(() -> userRepository.findById(id))
                   .subscribeOn(Schedulers.boundedElastic()) // 블로킹 작업을 별도 스레드에서
                   .switchIfEmpty(Mono.error(new UserNotFoundException()));
    }
    
    // 사용자 생성
    public Mono<User> createUser(User user) {
        return Mono.fromCallable(() -> userRepository.save(user))
                   .subscribeOn(Schedulers.boundedElastic())
                   .doOnSuccess(savedUser -> log.info("User created: {}", savedUser.getId()))
                   .doOnError(error -> log.error("Failed to create user", error));
    }
}
```

#### Flux (0개 이상의 요소)
```java
@Service
public class ProductService {
    
    // 모든 상품 조회 (스트리밍)
    public Flux<Product> findAllProducts() {
        return Flux.fromIterable(productRepository.findAll())
                   .subscribeOn(Schedulers.boundedElastic())
                   .delayElements(Duration.ofMillis(100)); // 백프레셔 시뮬레이션
    }
    
    // 실시간 가격 업데이트
    public Flux<ProductPrice> priceUpdates() {
        return Flux.interval(Duration.ofSeconds(1))
                   .map(tick -> generateRandomPrice())
                   .share(); // 멀티캐스트
    }
}
```

## WebFlux 컨트롤러 패턴

### 애노테이션 기반 컨트롤러
```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    @Autowired
    private ProductService productService;
    
    // 단일 상품 조회
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Product>> getProduct(@PathVariable String id) {
        return productService.findById(id)
                           .map(product -> ResponseEntity.ok(product))
                           .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    // 상품 목록 조회 (Server-Sent Events)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Product> streamProducts() {
        return productService.findAllProducts()
                           .delayElements(Duration.ofSeconds(1));
    }
    
    // 상품 생성
    @PostMapping
    public Mono<ResponseEntity<Product>> createProduct(@RequestBody Mono<Product> productMono) {
        return productMono
            .flatMap(productService::createProduct)
            .map(savedProduct -> ResponseEntity.status(HttpStatus.CREATED).body(savedProduct))
            .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // 배치 업로드
    @PostMapping("/batch")
    public Mono<ResponseEntity<String>> uploadProducts(@RequestBody Flux<Product> products) {
        return products
            .flatMap(productService::createProduct)
            .count()
            .map(count -> ResponseEntity.ok("Created " + count + " products"));
    }
}
```

### 함수형 라우팅
```java
@Configuration
public class ProductRouterConfig {
    
    @Bean
    public RouterFunction<ServerResponse> productRoutes(ProductHandler handler) {
        return RouterFunctions
            .route(GET("/api/products/{id}"), handler::getProduct)
            .andRoute(GET("/api/products"), handler::getAllProducts)
            .andRoute(POST("/api/products"), handler::createProduct)
            .andRoute(PUT("/api/products/{id}"), handler::updateProduct)
            .andRoute(DELETE("/api/products/{id}"), handler::deleteProduct)
            .andRoute(GET("/api/products/search"), handler::searchProducts);
    }
}

@Component
public class ProductHandler {
    
    private final ProductService productService;
    
    public Mono<ServerResponse> getProduct(ServerRequest request) {
        String id = request.pathVariable("id");
        
        return productService.findById(id)
                           .flatMap(product -> ServerResponse.ok().bodyValue(product))
                           .switchIfEmpty(ServerResponse.notFound().build());
    }
    
    public Mono<ServerResponse> getAllProducts(ServerRequest request) {
        // 쿼리 파라미터 처리
        int page = Integer.parseInt(request.queryParam("page").orElse("0"));
        int size = Integer.parseInt(request.queryParam("size").orElse("10"));
        
        Flux<Product> products = productService.findProducts(page, size);
        
        return ServerResponse.ok()
                           .contentType(MediaType.APPLICATION_JSON)
                           .body(products, Product.class);
    }
    
    public Mono<ServerResponse> createProduct(ServerRequest request) {
        Mono<Product> productMono = request.bodyToMono(Product.class);
        
        return productMono
            .flatMap(productService::createProduct)
            .flatMap(savedProduct -> 
                ServerResponse.status(HttpStatus.CREATED).bodyValue(savedProduct))
            .onErrorResume(ValidationException.class,
                error -> ServerResponse.badRequest().bodyValue(error.getMessage()));
    }
}
```

## 리액티브 데이터 액세스

### Spring Data R2DBC
```java
// 설정
@Configuration
@EnableR2dbcRepositories
public class R2dbcConfig extends AbstractR2dbcConfiguration {
    
    @Override
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get("r2dbc:mysql://localhost:3306/productdb");
    }
}

// Repository
public interface ProductRepository extends R2dbcRepository<Product, String> {
    
    @Query("SELECT * FROM products WHERE category = :category AND price BETWEEN :minPrice AND :maxPrice")
    Flux<Product> findByCategoryAndPriceBetween(String category, BigDecimal minPrice, BigDecimal maxDecimal);
    
    @Query("SELECT * FROM products WHERE name LIKE :name ORDER BY created_at DESC LIMIT :limit")
    Flux<Product> findByNameContainingOrderByCreatedAtDesc(String name, int limit);
    
    @Modifying
    @Query("UPDATE products SET stock = stock - :quantity WHERE id = :productId AND stock >= :quantity")
    Mono<Integer> reduceStock(String productId, int quantity);
}

// 서비스에서 사용
@Service
public class ProductService {
    
    private final ProductRepository productRepository;
    
    public Flux<Product> searchProducts(String category, BigDecimal minPrice, BigDecimal maxPrice) {
        return productRepository.findByCategoryAndPriceBetween(category, minPrice, maxPrice)
                               .switchIfEmpty(Flux.empty())
                               .onErrorResume(error -> {
                                   log.error("Error searching products", error);
                                   return Flux.empty();
                               });
    }
    
    public Mono<Boolean> purchaseProduct(String productId, int quantity) {
        return productRepository.reduceStock(productId, quantity)
                               .map(updatedRows -> updatedRows > 0);
    }
}
```

### MongoDB Reactive
```java
@Document(collection = "products")
public class Product {
    @Id
    private String id;
    private String name;
    private String category;
    private BigDecimal price;
    private int stock;
    @CreatedDate
    private LocalDateTime createdAt;
}

public interface ProductRepository extends ReactiveMongoRepository<Product, String> {
    
    Flux<Product> findByCategoryOrderByPriceAsc(String category);
    
    @Query("{ 'price' : { $gte: ?0, $lte: ?1 } }")
    Flux<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice);
    
    Flux<Product> findByNameRegexIgnoreCase(String nameRegex);
}
```

## 에러 처리와 백프레셔

### 에러 처리 패턴
```java
@Service
public class OrderService {
    
    public Mono<Order> processOrder(OrderRequest request) {
        return validateOrder(request)
            .flatMap(this::createOrder)
            .flatMap(this::processPayment)
            .flatMap(this::updateInventory)
            .onErrorResume(ValidationException.class, 
                error -> Mono.error(new BadRequestException(error.getMessage())))
            .onErrorResume(PaymentException.class,
                error -> {
                    // 보상 트랜잭션
                    return cancelOrder(request.getOrderId())
                            .then(Mono.error(error));
                })
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                          .filter(throwable -> throwable instanceof TransientException))
            .timeout(Duration.ofSeconds(30))
            .doOnError(error -> log.error("Order processing failed", error));
    }
    
    private Mono<OrderRequest> validateOrder(OrderRequest request) {
        return Mono.fromCallable(() -> {
            if (request.getItems().isEmpty()) {
                throw new ValidationException("Order must have items");
            }
            return request;
        });
    }
}
```

### 백프레셔 처리
```java
@Service
public class LogProcessingService {
    
    // 백프레셔를 고려한 로그 처리
    public Flux<ProcessedLog> processLogs(Flux<LogEntry> logStream) {
        return logStream
            .onBackpressureBuffer(1000, // 버퍼 크기
                overflow -> log.warn("Log buffer overflow, dropping entries"))
            .flatMap(this::processLogEntry, 10) // 동시성 제한
            .onBackpressureDrop(dropped -> 
                log.warn("Dropping log entry due to backpressure: {}", dropped));
    }
    
    // 실시간 데이터 스트림 처리
    public Flux<Alert> monitorMetrics() {
        return Flux.interval(Duration.ofSeconds(1))
                  .flatMap(tick -> getCurrentMetrics())
                  .sample(Duration.ofSeconds(5)) // 샘플링으로 데이터 양 제한
                  .filter(metrics -> metrics.getCpuUsage() > 80.0)
                  .map(this::createAlert)
                  .share(); // Hot stream으로 변환
    }
}
```

## WebClient를 이용한 HTTP 클라이언트

### 기본 사용법
```java
@Component
public class ExternalApiClient {
    
    private final WebClient webClient;
    
    public ExternalApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.external-service.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, "MyApp/1.0")
            .build();
    }
    
    // GET 요청
    public Mono<UserProfile> getUserProfile(String userId) {
        return webClient.get()
                       .uri("/users/{id}/profile", userId)
                       .retrieve()
                       .onStatus(HttpStatus::is4xxClientError,
                           response -> Mono.error(new ClientException("User not found")))
                       .onStatus(HttpStatus::is5xxServerError,
                           response -> Mono.error(new ServerException("External service error")))
                       .bodyToMono(UserProfile.class)
                       .timeout(Duration.ofSeconds(5))
                       .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)));
    }
    
    // POST 요청
    public Mono<CreateUserResponse> createUser(CreateUserRequest request) {
        return webClient.post()
                       .uri("/users")
                       .bodyValue(request)
                       .retrieve()
                       .bodyToMono(CreateUserResponse.class);
    }
    
    // 스트리밍 응답
    public Flux<Event> streamEvents() {
        return webClient.get()
                       .uri("/events/stream")
                       .accept(MediaType.TEXT_EVENT_STREAM)
                       .retrieve()
                       .bodyToFlux(Event.class);
    }
}
```

### 연결 풀과 성능 튜닝
```java
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
            .connectionProvider(ConnectionProvider.builder("custom")
                .maxConnections(100)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(60))
                .build())
            .responseTimeout(Duration.ofSeconds(10))
            .keepAlive(true);
            
        return WebClient.builder()
                       .clientConnector(new ReactorClientHttpConnector(httpClient))
                       .exchangeStrategies(ExchangeStrategies.builder()
                           .codecs(configurer -> configurer.defaultCodecs()
                               .maxInMemorySize(1024 * 1024)) // 1MB
                           .build())
                       .build();
    }
}
```

## 테스팅

### WebTestClient 사용
```java
@WebFluxTest(ProductController.class)
class ProductControllerTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @MockBean
    private ProductService productService;
    
    @Test
    void getProduct_Success() {
        // Given
        Product product = Product.builder()
                                .id("1")
                                .name("Test Product")
                                .price(BigDecimal.valueOf(100))
                                .build();
        
        when(productService.findById("1")).thenReturn(Mono.just(product));
        
        // When & Then
        webTestClient.get()
                    .uri("/api/products/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Product.class)
                    .value(p -> {
                        assertThat(p.getName()).isEqualTo("Test Product");
                        assertThat(p.getPrice()).isEqualTo(BigDecimal.valueOf(100));
                    });
    }
    
    @Test
    void streamProducts_Success() {
        // Given
        Flux<Product> products = Flux.just(
            Product.builder().id("1").name("Product 1").build(),
            Product.builder().id("2").name("Product 2").build()
        );
        
        when(productService.findAllProducts()).thenReturn(products);
        
        // When & Then
        webTestClient.get()
                    .uri("/api/products/stream")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Product.class)
                    .hasSize(2);
    }
}
```

### StepVerifier를 이용한 리액티브 스트림 테스트
```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    
    @Mock
    private ProductRepository productRepository;
    
    @InjectMocks
    private ProductService productService;
    
    @Test
    void findById_Success() {
        // Given
        Product product = Product.builder().id("1").name("Test").build();
        when(productRepository.findById("1")).thenReturn(Mono.just(product));
        
        // When
        Mono<Product> result = productService.findById("1");
        
        // Then
        StepVerifier.create(result)
                   .expectNext(product)
                   .verifyComplete();
    }
    
    @Test
    void findAll_WithDelay() {
        // Given
        Flux<Product> products = Flux.just(
            Product.builder().id("1").build(),
            Product.builder().id("2").build()
        ).delayElements(Duration.ofMillis(100));
        
        when(productRepository.findAll()).thenReturn(products);
        
        // When
        Flux<Product> result = productService.findAllProducts();
        
        // Then
        StepVerifier.create(result)
                   .expectNextCount(2)
                   .expectComplete()
                   .verify(Duration.ofSeconds(1));
    }
    
    @Test
    void processOrder_WithError() {
        // Given
        when(productRepository.findById("1"))
            .thenReturn(Mono.error(new RuntimeException("DB Error")));
        
        // When
        Mono<Product> result = productService.findById("1");
        
        // Then
        StepVerifier.create(result)
                   .expectError(RuntimeException.class)
                   .verify();
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "WebFlux는 언제 사용해야 하나요?"
**답변 포인트:**
- **I/O 집약적인 애플리케이션**: 외부 API 호출이 많은 경우
- **높은 동시성**: 많은 수의 동시 연결이 필요한 경우
- **스트리밍 데이터**: 실시간 데이터 처리가 필요한 경우
- **리소스 효율성**: 적은 스레드로 높은 처리량이 필요한 경우

### Q2: "WebFlux와 WebMVC의 성능 차이는?"
**답변 포인트:**
- **스레드 모델**: WebMVC(스레드 풀) vs WebFlux(이벤트 루프)
- **메모리 사용량**: WebFlux가 더 적은 메모리 사용
- **지연 시간**: I/O 대기가 많을 때 WebFlux가 유리
- **CPU 집약적 작업**: WebMVC가 더 적합할 수 있음

### Q3: "리액티브 스트림에서 블로킹 호출을 해야 한다면?"
**답변 포인트:**
- **Schedulers.boundedElastic()** 사용
- **Mono.fromCallable()** 또는 **Flux.fromIterable()** 활용
- **publishOn()** 또는 **subscribeOn()** 으로 스레드 전환
- **블로킹 코드를 최소화**하고 별도 스레드 풀에서 실행

## 실무 적용 팁

1. **점진적 도입**: 기존 WebMVC와 병행하여 특정 엔드포인트부터 적용
2. **모니터링**: 리액티브 애플리케이션의 메트릭스는 전통적인 방식과 다름
3. **디버깅**: 비동기 스택 트레이스는 복잡하므로 로깅 전략 중요
4. **학습 곡선**: 팀의 리액티브 프로그래밍 이해도 고려

WebFlux는 높은 동시성과 효율적인 리소스 사용이 필요한 현대적인 애플리케이션에 매우 유용한 기술입니다.