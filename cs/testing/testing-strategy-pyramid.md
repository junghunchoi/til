# 테스트 전략과 테스트 피라미드

## 테스트 피라미드 개념

### 테스트 피라미드 구조
```
        /\
       /  \    E2E Tests (적음)
      /____\   - 전체 시스템 테스트
     /      \  - 브라우저 자동화
    /        \ - 느리고 비용이 많이 듦
   /          \
  /__________\ Integration Tests (중간)
 /            \ - 모듈 간 상호작용 테스트
/              \ - 데이터베이스, 외부 API 테스트
\______________/ - 중간 속도, 중간 비용
\              /
 \____________/ Unit Tests (많음)
  \          / - 개별 함수/클래스 테스트
   \        / - 빠르고 저렴함
    \______/ - 높은 커버리지
     \    /
      \__/
```

## Unit Tests (단위 테스트)

### 기본 단위 테스트
```java
// 테스트 대상 클래스
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    
    public Order createOrder(CreateOrderRequest request) {
        // 재고 확인
        if (!inventoryService.hasStock(request.getProductId(), request.getQuantity())) {
            throw new InsufficientStockException("재고가 부족합니다");
        }
        
        // 주문 생성
        Order order = Order.builder()
                          .customerId(request.getCustomerId())
                          .productId(request.getProductId())
                          .quantity(request.getQuantity())
                          .status(OrderStatus.PENDING)
                          .build();
        
        Order savedOrder = orderRepository.save(order);
        
        // 결제 처리
        PaymentResult result = paymentService.processPayment(
            request.getPaymentInfo(), savedOrder.getTotalAmount());
            
        if (result.isSuccess()) {
            savedOrder.setStatus(OrderStatus.CONFIRMED);
            inventoryService.reduceStock(request.getProductId(), request.getQuantity());
        } else {
            savedOrder.setStatus(OrderStatus.FAILED);
        }
        
        return orderRepository.save(savedOrder);
    }
    
    public BigDecimal calculateDiscount(Order order, Customer customer) {
        BigDecimal discount = BigDecimal.ZERO;
        
        // VIP 고객 할인
        if (customer.getGrade() == CustomerGrade.VIP) {
            discount = order.getTotalAmount().multiply(new BigDecimal("0.1"));
        }
        
        // 대량 주문 할인
        if (order.getQuantity() >= 10) {
            BigDecimal bulkDiscount = order.getTotalAmount().multiply(new BigDecimal("0.05"));
            discount = discount.add(bulkDiscount);
        }
        
        return discount;
    }
}
```

### 단위 테스트 구현
```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private PaymentService paymentService;
    
    @Mock
    private InventoryService inventoryService;
    
    @InjectMocks
    private OrderService orderService;
    
    @Test
    @DisplayName("정상적인 주문 생성 시 주문이 확정된다")
    void createOrder_Success() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId(100L)
            .quantity(2)
            .paymentInfo(createPaymentInfo())
            .build();
            
        Order pendingOrder = createOrder(OrderStatus.PENDING);
        Order confirmedOrder = createOrder(OrderStatus.CONFIRMED);
        
        when(inventoryService.hasStock(100L, 2)).thenReturn(true);
        when(orderRepository.save(any(Order.class)))
            .thenReturn(pendingOrder)
            .thenReturn(confirmedOrder);
        when(paymentService.processPayment(any(), any()))
            .thenReturn(PaymentResult.success("payment-id"));
        
        // When
        Order result = orderService.createOrder(request);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        
        // 검증 - 정확한 순서로 호출되었는지 확인
        InOrder inOrder = inOrder(inventoryService, orderRepository, paymentService);
        inOrder.verify(inventoryService).hasStock(100L, 2);
        inOrder.verify(orderRepository).save(any(Order.class));
        inOrder.verify(paymentService).processPayment(any(), any());
        inOrder.verify(inventoryService).reduceStock(100L, 2);
        inOrder.verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    @DisplayName("재고 부족 시 예외가 발생한다")
    void createOrder_InsufficientStock_ThrowsException() {
        // Given
        CreateOrderRequest request = createOrderRequest();
        when(inventoryService.hasStock(any(), anyInt())).thenReturn(false);
        
        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessage("재고가 부족합니다");
            
        verify(orderRepository, never()).save(any());
        verify(paymentService, never()).processPayment(any(), any());
    }
    
    @ParameterizedTest
    @DisplayName("고객 등급과 주문 수량에 따른 할인 계산")
    @CsvSource({
        "REGULAR, 5, 100, 0",      // 일반 고객, 소량 주문
        "VIP, 5, 100, 10",         // VIP 고객, 소량 주문 (10% 할인)
        "REGULAR, 15, 100, 5",     // 일반 고객, 대량 주문 (5% 할인)
        "VIP, 15, 100, 15"         // VIP 고객, 대량 주문 (15% 할인)
    })
    void calculateDiscount(CustomerGrade grade, int quantity, 
                          BigDecimal totalAmount, BigDecimal expectedDiscount) {
        // Given
        Customer customer = Customer.builder().grade(grade).build();
        Order order = Order.builder()
                          .quantity(quantity)
                          .totalAmount(totalAmount)
                          .build();
        
        // When
        BigDecimal discount = orderService.calculateDiscount(order, customer);
        
        // Then
        assertThat(discount).isEqualTo(expectedDiscount);
    }
}
```

### 테스트 더블 활용
```java
// Spy 사용 예시 - 실제 객체의 일부만 모킹
@Test
void processOrder_WithSpy() {
    OrderService orderServiceSpy = spy(orderService);
    
    // 특정 메서드만 모킹
    doReturn(BigDecimal.valueOf(10)).when(orderServiceSpy)
                                   .calculateDiscount(any(), any());
    
    Order result = orderServiceSpy.createOrder(request);
    
    // 실제 메서드 호출 확인
    verify(orderServiceSpy).calculateDiscount(any(), any());
}

// ArgumentCaptor 사용 - 메서드 호출 시 전달된 인자 검증
@Test
void createOrder_CaptureArguments() {
    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    
    orderService.createOrder(request);
    
    verify(orderRepository, times(2)).save(orderCaptor.capture());
    List<Order> capturedOrders = orderCaptor.getAllValues();
    
    assertThat(capturedOrders.get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(capturedOrders.get(1).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
}
```

## Integration Tests (통합 테스트)

### 데이터베이스 통합 테스트
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Test
    @DisplayName("고객 ID로 주문 목록을 조회한다")
    void findByCustomerId() {
        // Given
        Customer customer = entityManager.persistAndFlush(
            Customer.builder().name("홍길동").email("hong@test.com").build());
            
        Order order1 = entityManager.persistAndFlush(
            Order.builder().customerId(customer.getId()).status(OrderStatus.CONFIRMED).build());
        Order order2 = entityManager.persistAndFlush(
            Order.builder().customerId(customer.getId()).status(OrderStatus.PENDING).build());
            
        entityManager.clear();
        
        // When
        List<Order> orders = orderRepository.findByCustomerId(customer.getId());
        
        // Then
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(Order::getStatus)
                         .containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.PENDING);
    }
    
    @Test
    @DisplayName("주문 상태별 통계를 조회한다")
    void getOrderStatistics() {
        // Given - 테스트 데이터 준비
        createOrdersForStatistics();
        
        // When
        List<OrderStatistics> statistics = orderRepository.getOrderStatisticsByStatus();
        
        // Then
        assertThat(statistics).hasSize(3);
        OrderStatistics confirmedStats = statistics.stream()
            .filter(s -> s.getStatus() == OrderStatus.CONFIRMED)
            .findFirst().orElseThrow();
            
        assertThat(confirmedStats.getCount()).isEqualTo(5);
        assertThat(confirmedStats.getTotalAmount()).isEqualTo(new BigDecimal("500.00"));
    }
}
```

### 웹 계층 통합 테스트
```java
@WebMvcTest(OrderController.class)
class OrderControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private OrderService orderService;
    
    @MockBean
    private JwtService jwtService;
    
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("주문 생성 API 테스트")
    void createOrder_Success() throws Exception {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(1L)
            .productId(100L)
            .quantity(2)
            .build();
            
        Order order = Order.builder()
                          .id(1L)
                          .customerId(1L)
                          .status(OrderStatus.CONFIRMED)
                          .build();
                          
        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(order);
        
        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpected(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andDo(print());
                
        verify(orderService).createOrder(any(CreateOrderRequest.class));
    }
    
    @Test
    @DisplayName("권한 없는 사용자의 주문 조회 시 401 에러")
    void getOrders_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자 권한으로 모든 주문 조회")
    void getAllOrders_WithAdminRole() throws Exception {
        List<Order> orders = Arrays.asList(
            Order.builder().id(1L).build(),
            Order.builder().id(2L).build()
        );
        
        when(orderService.getAllOrders()).thenReturn(orders);
        
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }
}
```

### 외부 서비스 통합 테스트
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PaymentServiceIntegrationTest {
    
    @Container
    static WireMockContainer wireMock = new WireMockContainer("wiremock/wiremock:2.35.0")
            .withMappingFromResource("payment-api-mappings.json");
    
    @Autowired
    private PaymentService paymentService;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("payment.api.url", wireMock::getBaseUrl);
    }
    
    @Test
    @DisplayName("외부 결제 API 호출 성공")
    void processPayment_Success() {
        // Given
        PaymentInfo paymentInfo = PaymentInfo.builder()
                                            .cardNumber("1234-5678-9012-3456")
                                            .amount(new BigDecimal("100.00"))
                                            .build();
        
        // WireMock stub 설정은 mapping 파일에서 처리
        
        // When
        PaymentResult result = paymentService.processPayment(paymentInfo, new BigDecimal("100.00"));
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTransactionId()).isNotNull();
    }
    
    @Test
    @DisplayName("외부 결제 API 타임아웃 처리")
    void processPayment_Timeout() {
        // Given - WireMock에서 지연 응답 설정
        wireMock.stubFor(post(urlEqualTo("/payment/process"))
                        .willReturn(aResponse()
                                   .withStatus(200)
                                   .withFixedDelay(10000))); // 10초 지연
        
        // When & Then
        assertThatThrownBy(() -> 
            paymentService.processPayment(paymentInfo, amount))
            .isInstanceOf(PaymentTimeoutException.class);
    }
}
```

## End-to-End Tests (E2E 테스트)

### Selenium 기반 E2E 테스트
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers
class OrderE2ETest {
    
    @Container
    static GenericContainer<?> seleniumHub = new GenericContainer<>("selenium/hub:4.0.0")
            .withExposedPorts(4444);
    
    @Container
    static GenericContainer<?> chromeNode = new GenericContainer<>("selenium/node-chrome:4.0.0")
            .withEnv("HUB_HOST", "selenium-hub")
            .dependsOn(seleniumHub);
    
    private WebDriver driver;
    
    @BeforeEach
    void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
        
        driver = new RemoteWebDriver(
            seleniumHub.getSeleniumAddress(), 
            options
        );
    }
    
    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
    
    @Test
    @DisplayName("전체 주문 프로세스 E2E 테스트")
    void completeOrderProcess() {
        // Given - 로그인
        driver.get("http://localhost:8080/login");
        driver.findElement(By.id("username")).sendKeys("testuser@example.com");
        driver.findElement(By.id("password")).sendKeys("password");
        driver.findElement(By.id("loginBtn")).click();
        
        // When - 상품 선택 및 주문
        driver.get("http://localhost:8080/products");
        
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement productCard = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.className("product-card"))
        );
        productCard.click();
        
        driver.findElement(By.id("quantity")).clear();
        driver.findElement(By.id("quantity")).sendKeys("2");
        driver.findElement(By.id("addToCart")).click();
        
        // 장바구니로 이동
        driver.findElement(By.id("cartIcon")).click();
        driver.findElement(By.id("checkoutBtn")).click();
        
        // 결제 정보 입력
        driver.findElement(By.id("cardNumber")).sendKeys("1234567890123456");
        driver.findElement(By.id("expiryDate")).sendKeys("12/25");
        driver.findElement(By.id("cvv")).sendKeys("123");
        driver.findElement(By.id("placeOrderBtn")).click();
        
        // Then - 주문 완료 확인
        WebElement successMessage = wait.until(
            ExpectedConditions.presenceOfElementLocated(By.className("order-success"))
        );
        
        assertThat(successMessage.getText()).contains("주문이 완료되었습니다");
        
        // 주문 내역 페이지에서 확인
        driver.get("http://localhost:8080/my-orders");
        List<WebElement> orderItems = driver.findElements(By.className("order-item"));
        assertThat(orderItems).isNotEmpty();
        assertThat(orderItems.get(0).findElement(By.className("order-status")).getText())
            .isEqualTo("주문완료");
    }
}
```

### REST Assured 기반 API E2E 테스트
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(OrderAnnotation.class)
class OrderApiE2ETest {
    
    @LocalServerPort
    private int port;
    
    private static String authToken;
    private static Long orderId;
    
    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
    @Test
    @Order(1)
    @DisplayName("사용자 로그인 및 토큰 발급")
    void authenticateUser() {
        LoginRequest loginRequest = new LoginRequest("testuser@example.com", "password");
        
        Response response = given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("tokenType", equalTo("Bearer"))
            .body("accessToken", notNullValue())
        .extract().response();
        
        authToken = response.jsonPath().getString("accessToken");
    }
    
    @Test
    @Order(2)
    @DisplayName("인증된 사용자가 주문을 생성한다")
    void createOrderWithAuthentication() {
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
            .customerId(1L)
            .productId(100L)
            .quantity(2)
            .build();
        
        Response response = given()
            .header("Authorization", "Bearer " + authToken)
            .contentType(ContentType.JSON)
            .body(orderRequest)
        .when()
            .post("/api/orders")
        .then()
            .statusCode(201)
            .body("customerId", equalTo(1))
            .body("status", equalTo("CONFIRMED"))
            .body("id", notNullValue())
        .extract().response();
        
        orderId = response.jsonPath().getLong("id");
    }
    
    @Test
    @Order(3)
    @DisplayName("생성된 주문을 조회한다")
    void getCreatedOrder() {
        given()
            .header("Authorization", "Bearer " + authToken)
        .when()
            .get("/api/orders/{orderId}", orderId)
        .then()
            .statusCode(200)
            .body("id", equalTo(orderId.intValue()))
            .body("status", equalTo("CONFIRMED"));
    }
    
    @Test
    @Order(4)
    @DisplayName("주문을 취소한다")
    void cancelOrder() {
        given()
            .header("Authorization", "Bearer " + authToken)
        .when()
            .put("/api/orders/{orderId}/cancel", orderId)
        .then()
            .statusCode(200)
            .body("status", equalTo("CANCELLED"));
    }
}
```

## 테스트 전략 및 모범 사례

### Given-When-Then 패턴
```java
class OrderServiceTest {
    
    @Test
    @DisplayName("VIP 고객이 대량 주문 시 최대 할인을 받는다")
    void calculateMaximumDiscount() {
        // Given (준비)
        Customer vipCustomer = Customer.builder()
                                     .grade(CustomerGrade.VIP)
                                     .build();
        Order bulkOrder = Order.builder()
                              .quantity(20)
                              .totalAmount(new BigDecimal("1000"))
                              .build();
        
        // When (실행)
        BigDecimal discount = orderService.calculateDiscount(bulkOrder, vipCustomer);
        
        // Then (검증)
        BigDecimal expectedDiscount = new BigDecimal("150"); // 10% + 5% = 15%
        assertThat(discount).isEqualTo(expectedDiscount);
    }
}
```

### 테스트 데이터 빌더 패턴
```java
public class OrderTestDataBuilder {
    
    private Long id = 1L;
    private Long customerId = 1L;
    private Long productId = 100L;
    private int quantity = 1;
    private OrderStatus status = OrderStatus.PENDING;
    private BigDecimal totalAmount = new BigDecimal("100.00");
    
    public static OrderTestDataBuilder anOrder() {
        return new OrderTestDataBuilder();
    }
    
    public OrderTestDataBuilder withId(Long id) {
        this.id = id;
        return this;
    }
    
    public OrderTestDataBuilder withCustomer(Long customerId) {
        this.customerId = customerId;
        return this;
    }
    
    public OrderTestDataBuilder withStatus(OrderStatus status) {
        this.status = status;
        return this;
    }
    
    public OrderTestDataBuilder confirmed() {
        this.status = OrderStatus.CONFIRMED;
        return this;
    }
    
    public OrderTestDataBuilder cancelled() {
        this.status = OrderStatus.CANCELLED;
        return this;
    }
    
    public Order build() {
        return Order.builder()
                   .id(id)
                   .customerId(customerId)
                   .productId(productId)
                   .quantity(quantity)
                   .status(status)
                   .totalAmount(totalAmount)
                   .build();
    }
}

// 사용 예시
@Test
void testWithBuilder() {
    Order order = anOrder()
                    .withCustomer(123L)
                    .confirmed()
                    .build();
    
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
}
```

### 테스트 슬라이스 활용
```java
// 웹 계층만 테스트
@WebMvcTest(OrderController.class)
class OrderControllerSliceTest { ... }

// JPA 계층만 테스트  
@DataJpaTest
class OrderRepositorySliceTest { ... }

// 서비스 계층 테스트 (전체 컨텍스트)
@SpringBootTest
class OrderServiceIntegrationTest { ... }

// 보안 설정 테스트
@SpringBootTest
@AutoConfigureTestDatabase
@Sql("/test-data.sql")
class SecurityIntegrationTest { ... }
```

## 인터뷰 꼬리질문 대비

### Q1: "테스트 커버리지는 얼마나 확보해야 하나요?"
**답변 포인트:**
- **라인 커버리지 80% 이상** 권장하지만 맹신 금지
- **브랜치 커버리지**가 더 중요 (모든 조건문 테스트)
- **중요한 비즈니스 로직은 100%** 커버리지 목표
- **커버리지보다 테스트 품질**이 더 중요

### Q2: "Mocking은 언제 사용하고 언제 사용하지 않아야 하나요?"
**답변 포인트:**
- **외부 의존성**: 데이터베이스, 외부 API는 Mock 사용
- **단순한 객체**: Value Object는 실제 객체 사용
- **과도한 Mocking 주의**: 테스트가 구현에 너무 의존적
- **통합 테스트에서는 Mock 최소화**

### Q3: "테스트 실행 시간을 단축하는 방법은?"
**답변 포인트:**
- **병렬 실행**: JUnit 5의 @Execution(CONCURRENT)
- **테스트 슬라이스**: 필요한 컨텍스트만 로드
- **테스트 카테고리**: @Tag로 분류하여 선택적 실행
- **TestContainers 재사용**: 컨테이너 라이프사이클 최적화

## 실무 적용 팁

1. **테스트 우선 개발(TDD)**: 복잡한 비즈니스 로직부터 적용
2. **CI/CD 통합**: 테스트 실패 시 배포 중단
3. **테스트 리팩토링**: 프로덕션 코드와 함께 지속적 개선
4. **테스트 문서화**: 테스트 이름과 시나리오로 요구사항 문서화

좋은 테스트는 코드의 안정성을 보장하고, 리팩토링의 자신감을 주며, 살아있는 문서 역할을 합니다.