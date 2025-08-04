# 통합 테스트와 컨트랙트 테스트

## 통합 테스트 전략

### 통합 테스트 레벨
통합 테스트는 여러 컴포넌트나 시스템이 함께 작동하는 것을 검증합니다.

```java
// 1. 컴포넌트 통합 테스트 - Repository와 Database
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryIntegrationTest {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TestEntityManager testEntityManager;
    
    @Test
    @DisplayName("사용자 생성 및 조회 통합 테스트")
    void shouldCreateAndFindUser() {
        // Given
        User user = User.builder()
                       .username("testuser")
                       .email("test@example.com")
                       .password("password123")
                       .build();
        
        // When
        User savedUser = userRepository.save(user);
        testEntityManager.flush(); // 영속성 컨텍스트 동기화
        
        Optional<User> foundUser = userRepository.findByUsername("testuser");
        
        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getId()).isNotNull();
    }
    
    @Test
    @DisplayName("사용자 검색 쿼리 통합 테스트")
    void shouldFindUsersByEmailDomain() {
        // Given
        userRepository.saveAll(Arrays.asList(
            User.builder().username("user1").email("user1@company.com").build(),
            User.builder().username("user2").email("user2@company.com").build(),
            User.builder().username("user3").email("user3@gmail.com").build()
        ));
        testEntityManager.flush();
        
        // When
        List<User> companyUsers = userRepository.findByEmailContaining("@company.com");
        
        // Then
        assertThat(companyUsers).hasSize(2);
        assertThat(companyUsers)
            .extracting(User::getUsername)
            .containsExactlyInAnyOrder("user1", "user2");
    }
}
```

### 2. 서비스 계층 통합 테스트
```java
@SpringBootTest
@Transactional
class UserServiceIntegrationTest {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @MockBean
    private EmailService emailService; // 외부 의존성은 Mock
    
    @Test
    @DisplayName("사용자 등록 프로세스 통합 테스트")
    void shouldRegisterUserWithEmailVerification() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                                                    .username("newuser")
                                                    .email("newuser@example.com")
                                                    .password("strongPassword123!")
                                                    .build();
        
        // When
        UserDto createdUser = userService.createUser(request);
        
        // Then
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getUsername()).isEqualTo("newuser");
        assertThat(createdUser.getEmail()).isEqualTo("newuser@example.com");
        assertThat(createdUser.isActive()).isFalse(); // 이메일 인증 전이므로 비활성
        
        // 이메일 발송 검증
        verify(emailService).sendVerificationEmail(eq("newuser@example.com"), anyString());
    }
    
    @Test
    @DisplayName("비밀번호 변경 프로세스 통합 테스트")
    void shouldChangePasswordWithProperValidation() {
        // Given
        User existingUser = createTestUser();
        String oldPassword = "oldPassword123!";
        String newPassword = "newPassword456!";
        
        ChangePasswordRequest request = new ChangePasswordRequest(
            existingUser.getId(), oldPassword, newPassword
        );
        
        // When
        userService.changePassword(request);
        
        // Then
        User updatedUser = userService.findById(existingUser.getId());
        assertThat(passwordEncoder.matches(newPassword, updatedUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(oldPassword, updatedUser.getPassword())).isFalse();
        
        // 보안 알림 이메일 검증
        verify(emailService).sendPasswordChangedNotification(existingUser.getEmail());
    }
    
    private User createTestUser() {
        return userService.createUser(CreateUserRequest.builder()
                                                      .username("testuser")
                                                      .email("test@example.com")
                                                      .password("oldPassword123!")
                                                      .build()).toEntity();
    }
}
```

### 3. Web 계층 통합 테스트
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @MockBean
    private EmailService emailService;
    
    @Test
    @DisplayName("사용자 생성 API 통합 테스트")
    void shouldCreateUserThroughApi() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                                                    .username("apiuser")
                                                    .email("apiuser@example.com")
                                                    .password("apiPassword123!")
                                                    .build();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateUserRequest> entity = new HttpEntity<>(request, headers);
        
        // When
        ResponseEntity<UserDto> response = restTemplate.postForEntity(
            "/api/users", entity, UserDto.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUsername()).isEqualTo("apiuser");
        
        // 데이터베이스 검증
        Optional<User> savedUser = userRepository.findByUsername("apiuser");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getEmail()).isEqualTo("apiuser@example.com");
    }
    
    @Test
    @DisplayName("사용자 목록 조회 API 통합 테스트")
    void shouldGetUsersWithPagination() {
        // Given
        createTestUsers(15); // 15명의 테스트 사용자 생성
        
        // When
        ResponseEntity<PagedResponse<UserDto>> response = restTemplate.exchange(
            "/api/users?page=0&size=10&sort=username,asc",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<PagedResponse<UserDto>>() {}
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(10);
        assertThat(response.getBody().getTotalElements()).isEqualTo(15);
        assertThat(response.getBody().getTotalPages()).isEqualTo(2);
    }
    
    private void createTestUsers(int count) {
        for (int i = 1; i <= count; i++) {
            User user = User.builder()
                           .username("user" + i)
                           .email("user" + i + "@example.com")
                           .password("password" + i)
                           .build();
            userRepository.save(user);
        }
    }
}
```

### 4. 멀티 모듈 통합 테스트
```java
@SpringBootTest
@Testcontainers
class OrderProcessingIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6.2-alpine")
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private InventoryService inventoryService;
    
    @MockBean
    private NotificationService notificationService;
    
    @Test
    @DisplayName("주문 생성부터 결제까지 전체 프로세스 통합 테스트")
    void shouldProcessCompleteOrderFlow() {
        // Given
        Product product = createTestProduct("iPhone 14", new BigDecimal("1200000"), 10);
        Customer customer = createTestCustomer("customer@example.com");
        
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                                                           .customerId(customer.getId())
                                                           .items(Arrays.asList(
                                                               OrderItemRequest.builder()
                                                                              .productId(product.getId())
                                                                              .quantity(2)
                                                                              .build()
                                                           ))
                                                           .build();
        
        PaymentRequest paymentRequest = PaymentRequest.builder()
                                                     .paymentMethod(PaymentMethod.CREDIT_CARD)
                                                     .cardNumber("1234-5678-9012-3456")
                                                     .build();
        
        // When
        Order createdOrder = orderService.createOrder(orderRequest);
        PaymentResult paymentResult = paymentService.processPayment(createdOrder.getId(), paymentRequest);
        
        // Then
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(paymentResult.isSuccess()).isTrue();
        
        Order finalOrder = orderService.findById(createdOrder.getId());
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        
        // 재고 차감 검증
        Product updatedProduct = inventoryService.findProductById(product.getId());
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(8); // 10 - 2 = 8
        
        // 알림 발송 검증
        verify(notificationService).sendOrderConfirmation(eq(customer.getEmail()), any(Order.class));
    }
    
    @Test
    @DisplayName("재고 부족 시 주문 실패 시나리오 통합 테스트")
    void shouldFailOrderWhenInsufficientStock() {
        // Given
        Product product = createTestProduct("Limited Item", new BigDecimal("500000"), 1);
        Customer customer = createTestCustomer("customer@example.com");
        
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                                                           .customerId(customer.getId())
                                                           .items(Arrays.asList(
                                                               OrderItemRequest.builder()
                                                                              .productId(product.getId())
                                                                              .quantity(5) // 재고보다 많이 주문
                                                                              .build()
                                                           ))
                                                           .build();
        
        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessageContaining("재고가 부족합니다");
        
        // 재고는 변경되지 않았어야 함
        Product unchangedProduct = inventoryService.findProductById(product.getId());
        assertThat(unchangedProduct.getStockQuantity()).isEqualTo(1);
    }
}
```

## 컨트랙트 테스트

### 1. Consumer Driven Contract 테스트
```java
// Consumer 측 테스트 (주문 서비스)
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-service", hostInterface = "localhost")
class PaymentServiceContractTest {
    
    @MockServer
    private MockServer mockServer;
    
    @Pact(consumer = "order-service")
    public RequestResponsePact processPaymentPact(PactDslWithProvider builder) {
        return builder
            .given("payment service is available")
            .uponReceiving("a payment processing request")
                .path("/api/payments")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(PactDslJsonBody.body()
                    .stringType("orderId", "order-123")
                    .numberType("amount", 100000)
                    .stringType("paymentMethod", "CREDIT_CARD")
                    .stringType("cardNumber", "1234-5678-9012-3456")
                )
            .willRespondWith()
                .status(200)
                .headers("Content-Type", "application/json")
                .body(PactDslJsonBody.body()
                    .booleanType("success", true)
                    .stringType("transactionId", "tx-456")
                    .stringType("status", "COMPLETED")
                )
            .toPact();
    }
    
    @Test
    @PactTestFor(pactMethod = "processPaymentPact")
    void shouldProcessPaymentSuccessfully() {
        // Given
        PaymentServiceClient paymentClient = new PaymentServiceClient(
            mockServer.getUrl() // Pact Mock Server URL
        );
        
        PaymentRequest request = PaymentRequest.builder()
                                             .orderId("order-123")
                                             .amount(new BigDecimal("100000"))
                                             .paymentMethod("CREDIT_CARD")
                                             .cardNumber("1234-5678-9012-3456")
                                             .build();
        
        // When
        PaymentResponse response = paymentClient.processPayment(request);
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTransactionId()).isEqualTo("tx-456");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }
    
    @Pact(consumer = "order-service")
    public RequestResponsePact paymentFailurePact(PactDslWithProvider builder) {
        return builder
            .given("payment service is available but payment fails")
            .uponReceiving("a payment processing request that fails")
                .path("/api/payments")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(PactDslJsonBody.body()
                    .stringType("orderId", "order-456")
                    .numberType("amount", 100000)
                    .stringType("paymentMethod", "CREDIT_CARD")
                    .stringType("cardNumber", "0000-0000-0000-0000") // 실패하는 카드 번호
                )
            .willRespondWith()
                .status(400)
                .headers("Content-Type", "application/json")
                .body(PactDslJsonBody.body()
                    .booleanType("success", false)
                    .stringType("errorCode", "INVALID_CARD")
                    .stringType("errorMessage", "잘못된 카드 번호입니다")
                )
            .toPact();
    }
    
    @Test
    @PactTestFor(pactMethod = "paymentFailurePact")
    void shouldHandlePaymentFailure() {
        // Given
        PaymentServiceClient paymentClient = new PaymentServiceClient(mockServer.getUrl());
        
        PaymentRequest request = PaymentRequest.builder()
                                             .orderId("order-456")
                                             .amount(new BigDecimal("100000"))
                                             .paymentMethod("CREDIT_CARD")
                                             .cardNumber("0000-0000-0000-0000")
                                             .build();
        
        // When
        PaymentResponse response = paymentClient.processPayment(request);
        
        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("INVALID_CARD");
        assertThat(response.getErrorMessage()).isEqualTo("잘못된 카드 번호입니다");
    }
}
```

### 2. Provider 측 검증 테스트
```java
// Provider 측 테스트 (결제 서비스)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
                properties = "server.port=8080")
@Provider("payment-service")
@PactFolder("pacts") // Consumer가 생성한 Pact 파일 위치
class PaymentServiceProviderTest {
    
    @MockBean
    private PaymentGateway paymentGateway;
    
    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
    
    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", 8080));
    }
    
    @State("payment service is available")
    void paymentServiceIsAvailable() {
        // 정상적인 결제 처리 시나리오 설정
        when(paymentGateway.processPayment(any()))
            .thenReturn(PaymentGatewayResponse.builder()
                                            .success(true)
                                            .transactionId("tx-456")
                                            .status("COMPLETED")
                                            .build());
    }
    
    @State("payment service is available but payment fails")
    void paymentServiceFailsPayment() {
        // 결제 실패 시나리오 설정
        when(paymentGateway.processPayment(any()))
            .thenReturn(PaymentGatewayResponse.builder()
                                            .success(false)
                                            .errorCode("INVALID_CARD")
                                            .errorMessage("잘못된 카드 번호입니다")
                                            .build());
    }
}
```

### 3. GraphQL 컨트랙트 테스트
```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "user-graphql-service", hostInterface = "localhost")
class UserGraphQLContractTest {
    
    @MockServer
    private MockServer mockServer;
    
    @Pact(consumer = "frontend-app")  
    public RequestResponsePact getUserProfilePact(PactDslWithProvider builder) {
        return builder
            .given("user exists with id 123")
            .uponReceiving("a GraphQL query for user profile")
                .path("/graphql")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(PactDslJsonBody.body()
                    .stringValue("query", "query GetUser($id: ID!) { user(id: $id) { id username email profile { firstName lastName } } }")
                    .object("variables")
                        .stringValue("id", "123")
                    .closeObject()
                )
            .willRespondWith()
                .status(200)
                .headers("Content-Type", "application/json")
                .body(PactDslJsonBody.body()
                    .object("data")
                        .object("user")
                            .stringValue("id", "123")
                            .stringValue("username", "johndoe")
                            .stringValue("email", "john@example.com")
                            .object("profile")
                                .stringValue("firstName", "John")
                                .stringValue("lastName", "Doe")
                            .closeObject()
                        .closeObject()
                    .closeObject()
                )
            .toPact();
    }
    
    @Test
    @PactTestFor(pactMethod = "getUserProfilePact")
    void shouldGetUserProfileViaGraphQL() {
        // Given
        GraphQLClient graphQLClient = new GraphQLClient(mockServer.getUrl() + "/graphql");
        
        String query = """
            query GetUser($id: ID!) {
                user(id: $id) {
                    id
                    username
                    email
                    profile {
                        firstName
                        lastName
                    }
                }
            }
        """;
        
        // When
        GraphQLResponse response = graphQLClient.execute(query, Map.of("id", "123"));
        
        // Then
        assertThat(response.isSuccessful()).isTrue();
        
        JsonNode userData = response.getData().get("user");
        assertThat(userData.get("id").asText()).isEqualTo("123");
        assertThat(userData.get("username").asText()).isEqualTo("johndoe");
        assertThat(userData.get("email").asText()).isEqualTo("john@example.com");
        assertThat(userData.get("profile").get("firstName").asText()).isEqualTo("John");
        assertThat(userData.get("profile").get("lastName").asText()).isEqualTo("Doe");
    }
}
```

### 4. 메시지 기반 컨트랙트 테스트
```java
// Message Consumer 테스트
@ExtendWith(PactConsumerTestExt.class)
class OrderEventConsumerContractTest {
    
    @Pact(consumer = "inventory-service")
    public MessagePact orderCreatedEventPact(MessagePactBuilder builder) {
        return builder
            .given("an order is created")
            .expectsToReceive("order created event")
            .withContent(PactDslJsonBody.body()
                .stringType("eventType", "OrderCreated")
                .stringType("eventId", "event-123")
                .stringType("orderId", "order-456")
                .stringType("customerId", "customer-789")
                .numberType("totalAmount", 150000)
                .minArrayLike("items", 1)
                    .stringType("productId", "product-123")
                    .numberType("quantity", 2)
                    .numberType("unitPrice", 75000)
                .closeArray()
                .datetime("occurredAt", "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            )
            .toPact();
    }
    
    @Test
    @PactTestFor(pactMethod = "orderCreatedEventPact")
    void shouldProcessOrderCreatedEvent(MessagePact pact) throws Exception {
        // Given
        OrderEventHandler eventHandler = new OrderEventHandler(inventoryService);
        String messageBody = new String(pact.getMessages().get(0).getContents().getValue());
        
        // When
        OrderCreatedEvent event = objectMapper.readValue(messageBody, OrderCreatedEvent.class);
        eventHandler.handleOrderCreated(event);
        
        // Then  
        verify(inventoryService).reserveStock(eq("order-456"), any());
    }
}

// Message Provider 테스트
@Provider("order-service")
@PactFolder("pacts")
class OrderServiceMessageProviderTest {
    
    @Autowired
    private OrderEventPublisher eventPublisher;
    
    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
    
    @PactVerifyProvider("order created event")
    String verifyOrderCreatedEvent() {
        // Given
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                                                  .eventType("OrderCreated")
                                                  .eventId("event-123")
                                                  .orderId("order-456")
                                                  .customerId("customer-789")
                                                  .totalAmount(new BigDecimal("150000"))
                                                  .items(Arrays.asList(
                                                      OrderItem.builder()
                                                              .productId("product-123")
                                                              .quantity(2)
                                                              .unitPrice(new BigDecimal("75000"))
                                                              .build()
                                                  ))
                                                  .occurredAt(LocalDateTime.now())
                                                  .build();
        
        // When
        return objectMapper.writeValueAsString(event);
    }
}
```

## 테스트 환경 구성

### 1. TestContainers 활용
```java
@Testcontainers
@SpringBootTest
class DatabaseIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("init-test-data.sql"); // 초기 데이터 스크립트
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6.2-alpine")
            .withExposedPorts(6379);
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Test
    @DisplayName("복합 시스템 통합 테스트")
    void shouldWorkWithMultipleServices() {
        // 데이터베이스, 레디스, 카프카를 모두 사용하는 통합 테스트
        // ...
    }
}

// 커스텀 TestContainer 구성
@TestConfiguration
static class TestContainerConfiguration {
    
    @Bean
    @ServiceConnection // Spring Boot 3.1+
    static PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>("postgres:13")
                .withDatabaseName("integration-tests-db")
                .withUsername("username")
                .withPassword("password");
    }
    
    @Bean
    @ServiceConnection
    static RedisContainer redisContainer() {
        return new RedisContainer("redis:6.2-alpine");
    }
}
```

### 2. 테스트 프로파일 구성
```yaml
# application-integration-test.yml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        
  kafka:
    consumer:
      auto-offset-reset: earliest
      group-id: integration-test-group
      
  redis:
    timeout: 2000ms
    
logging:
  level:
    org.springframework.web: DEBUG
    com.example: DEBUG
    
# 외부 서비스 Mock 설정
external-services:
  payment-gateway:
    enabled: false
    mock-responses: true
  email-service:
    enabled: false
    
# 테스트 데이터 설정
test-data:
  users:
    default-password: "testPassword123!"
  products:
    default-stock: 100
```

## 인터뷰 꼬리질문 대비

### Q1: "통합 테스트와 단위 테스트의 경계는 어떻게 구분하나요?"
**답변 포인트:**
- **의존성 범위**: 외부 시스템(DB, 네트워크)과의 상호작용 포함 여부
- **테스트 속도**: 통합 테스트는 일반적으로 더 느림
- **격리 수준**: 단위 테스트는 완전 격리, 통합 테스트는 실제 연동
- **실행 환경**: 통합 테스트는 실제와 유사한 환경 필요

### Q2: "컨트랙트 테스트의 장점과 단점은 무엇인가요?"
**답변 포인트:**
- **장점**: API 호환성 보장, 팀 간 협업 개선, 조기 통합 이슈 발견
- **단점**: 초기 설정 복잡성, 추가 유지보수 비용, 학습 곡선
- **적용 시점**: 마이크로서비스 아키텍처에서 특히 유용
- **대안**: API 문서 기반 테스트, E2E 테스트 등

### Q3: "통합 테스트에서 외부 의존성을 어떻게 처리하나요?"
**답변 포인트:**
- **TestContainers**: 실제 외부 서비스의 컨테이너 버전 사용
- **WireMock**: HTTP 기반 외부 서비스 모킹
- **Embedded 서비스**: H2, 내장 Redis 등 경량 버전 사용
- **Test Doubles**: 상황에 맞는 Mock, Stub, Fake 선택

## 실무 베스트 프랙티스

1. **테스트 피라미드 준수**: 단위 테스트 > 통합 테스트 > E2E 테스트 비율
2. **독립적인 테스트**: 각 테스트는 다른 테스트에 의존하지 않아야 함
3. **환경 일관성**: 개발, 테스트, 프로덕션 환경의 일관성 유지
4. **데이터 격리**: 테스트 간 데이터 간섭 방지를 위한 격리 전략
5. **지속적 통합**: CI/CD 파이프라인에서 자동화된 통합 테스트 실행

통합 테스트와 컨트랙트 테스트는 시스템의 전체적인 동작을 검증하고 서비스 간 호환성을 보장하는 중요한 테스트 전략입니다. 적절한 도구와 전략을 선택하여 효과적인 테스트 환경을 구축하는 것이 중요합니다.