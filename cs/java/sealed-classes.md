# Sealed Classes의 개념과 활용 사례 (Java 17+)

## 핵심 개념

### Sealed Classes란?
Java 17에서 정식 도입된 기능으로, **클래스 계층 구조를 제한**하여 특정 클래스들만 상속하거나 구현할 수 있도록 하는 기능

### 기본 문법
```java
// sealed 클래스 선언
public sealed class Shape 
    permits Circle, Rectangle, Triangle {
    // 공통 기능
}

// 허용된 서브클래스들
public final class Circle extends Shape {
    private final double radius;
    public Circle(double radius) { this.radius = radius; }
    public double radius() { return radius; }
}

public final class Rectangle extends Shape {
    private final double width, height;
    public Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }
    public double width() { return width; }
    public double height() { return height; }
}

public non-sealed class Triangle extends Shape {
    private final double base, height;
    public Triangle(double base, double height) {
        this.base = base;
        this.height = height;
    }
    public double base() { return base; }
    public double height() { return height; }
}
```

## Sealed Classes의 특징

### 1. 서브클래스 제한
```java
// 허용된 서브클래스만 상속 가능
public sealed interface PaymentMethod 
    permits CreditCard, DebitCard, BankTransfer, DigitalWallet {
}

// 각 구현체의 접근 제어자 선택
public final class CreditCard implements PaymentMethod {
    // final: 더 이상 상속 불가
}

public sealed class DigitalWallet implements PaymentMethod 
    permits PayPal, ApplePay, GooglePay {
    // sealed: 지정된 클래스만 상속 가능
}

public non-sealed class BankTransfer implements PaymentMethod {
    // non-sealed: 누구나 상속 가능 (일반 클래스처럼)
}

// 컴파일 에러: Bitcoin은 permits에 없음
// public class Bitcoin implements PaymentMethod {} // 에러!
```

### 2. 서브클래스의 접근 제어자
```java
public sealed class Vehicle permits Car, Motorcycle, Truck {
    protected String brand;
    protected int year;
}

// 1. final: 더 이상 상속 불가
public final class Car extends Vehicle {
    private int doors;
}

// 2. sealed: 지정된 클래스만 상속 가능
public sealed class Motorcycle extends Vehicle 
    permits SportBike, Cruiser {
    private boolean hasSidecar;
}

// 3. non-sealed: 제한 없이 상속 가능
public non-sealed class Truck extends Vehicle {
    private double cargoCapacity;
}

// Motorcycle의 서브클래스들
public final class SportBike extends Motorcycle {}
public final class Cruiser extends Motorcycle {}

// Truck은 non-sealed이므로 자유롭게 상속 가능
public class DeliveryTruck extends Truck {}
public class FireTruck extends Truck {}
```

### 3. 인터페이스에서의 활용
```java
public sealed interface Result<T> 
    permits Success, Failure {
    
    // 공통 메서드
    boolean isSuccess();
    
    // 기본 메서드
    default T getOrThrow() {
        if (this instanceof Success<T> success) {
            return success.value();
        } else if (this instanceof Failure<T> failure) {
            throw new RuntimeException(failure.error());
        }
        throw new IllegalStateException("Unknown result type");
    }
}

public record Success<T>(T value) implements Result<T> {
    @Override
    public boolean isSuccess() { return true; }
}

public record Failure<T>(String error) implements Result<T> {
    @Override
    public boolean isSuccess() { return false; }
}
```

## 실제 활용 사례

### 1. 상태 관리 (State Pattern)
```java
public sealed interface OrderStatus 
    permits Pending, Confirmed, Shipped, Delivered, Cancelled {
    
    String getStatusMessage();
    boolean canTransitionTo(OrderStatus newStatus);
}

public record Pending() implements OrderStatus {
    @Override
    public String getStatusMessage() {
        return "주문이 대기 중입니다.";
    }
    
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return newStatus instanceof Confirmed || newStatus instanceof Cancelled;
    }
}

public record Confirmed(LocalDateTime confirmedAt) implements OrderStatus {
    @Override
    public String getStatusMessage() {
        return "주문이 확인되었습니다. (" + confirmedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ")";
    }
    
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return newStatus instanceof Shipped || newStatus instanceof Cancelled;
    }
}

public record Shipped(String trackingNumber, LocalDateTime shippedAt) implements OrderStatus {
    @Override
    public String getStatusMessage() {
        return "주문이 배송 중입니다. 추적번호: " + trackingNumber;
    }
    
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return newStatus instanceof Delivered;
    }
}

public record Delivered(LocalDateTime deliveredAt) implements OrderStatus {
    @Override
    public String getStatusMessage() {
        return "주문이 배송 완료되었습니다.";
    }
    
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return false; // 최종 상태
    }
}

public record Cancelled(String reason, LocalDateTime cancelledAt) implements OrderStatus {
    @Override
    public String getStatusMessage() {
        return "주문이 취소되었습니다. 사유: " + reason;
    }
    
    @Override
    public boolean canTransitionTo(OrderStatus newStatus) {
        return false; // 최종 상태
    }
}

// 주문 서비스에서 활용
@Service
public class OrderService {
    
    public void updateOrderStatus(Order order, OrderStatus newStatus) {
        OrderStatus currentStatus = order.getStatus();
        
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition from " + currentStatus.getClass().getSimpleName() + 
                " to " + newStatus.getClass().getSimpleName()
            );
        }
        
        order.setStatus(newStatus);
        
        // 상태별 처리
        switch (newStatus) {
            case Confirmed(var confirmedAt) -> {
                emailService.sendConfirmationEmail(order);
                inventoryService.reserveItems(order.getItems());
            }
            case Shipped(var trackingNumber, var shippedAt) -> {
                emailService.sendShippingEmail(order, trackingNumber);
                logisticsService.notifyCarrier(order, trackingNumber);
            }
            case Delivered(var deliveredAt) -> {
                emailService.sendDeliveryEmail(order);
                reviewService.requestReview(order);
            }
            case Cancelled(var reason, var cancelledAt) -> {
                emailService.sendCancellationEmail(order, reason);
                inventoryService.releaseReservedItems(order.getItems());
                paymentService.processRefund(order);
            }
            default -> {
                // Pending 상태는 특별한 처리 없음
            }
        }
    }
}
```

### 2. API 응답 타입 (Result Type)
```java
public sealed interface ApiResponse<T> 
    permits Success, ClientError, ServerError {
    
    int getStatusCode();
    String getMessage();
    
    default boolean isSuccess() {
        return this instanceof Success<T>;
    }
    
    default T getDataOrThrow() {
        return switch (this) {
            case Success<T> success -> success.data();
            case ClientError<T> error -> throw new IllegalArgumentException(error.message());
            case ServerError<T> error -> throw new RuntimeException(error.message());
        };
    }
}

public record Success<T>(T data, String message) implements ApiResponse<T> {
    public Success(T data) {
        this(data, "Success");
    }
    
    @Override
    public int getStatusCode() { return 200; }
    
    @Override
    public String getMessage() { return message; }
}

public record ClientError<T>(int statusCode, String message, String errorCode) implements ApiResponse<T> {
    public ClientError(String message) {
        this(400, message, "BAD_REQUEST");
    }
    
    @Override
    public int getStatusCode() { return statusCode; }
    
    @Override
    public String getMessage() { return message; }
}

public record ServerError<T>(int statusCode, String message, String errorCode, Throwable cause) implements ApiResponse<T> {
    public ServerError(String message, Throwable cause) {
        this(500, message, "INTERNAL_SERVER_ERROR", cause);
    }
    
    @Override
    public int getStatusCode() { return statusCode; }
    
    @Override
    public String getMessage() { return message; }
}

// 컨트롤러에서 활용
@RestController
public class UserController {
    
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> getUser(@PathVariable Long id) {
        ApiResponse<User> response = userService.findById(id);
        
        return ResponseEntity
            .status(response.getStatusCode())
            .body(response);
    }
}

@Service
public class UserService {
    
    public ApiResponse<User> findById(Long id) {
        try {
            User user = userRepository.findById(id)
                .orElse(null);
            
            if (user == null) {
                return new ClientError<>(404, "User not found", "USER_NOT_FOUND");
            }
            
            return new Success<>(user);
            
        } catch (DataAccessException e) {
            return new ServerError<>("Database error", e);
        } catch (Exception e) {
            return new ServerError<>("Unexpected error", e);
        }
    }
}
```

### 3. 도메인 이벤트 시스템
```java
public sealed interface DomainEvent 
    permits UserEvent, OrderEvent, PaymentEvent {
    
    String getEventId();
    LocalDateTime getTimestamp();
    String getAggregateId();
}

// 사용자 관련 이벤트
public sealed interface UserEvent extends DomainEvent 
    permits UserRegistered, UserUpdated, UserDeleted {
}

public record UserRegistered(
    String eventId,
    LocalDateTime timestamp,
    String userId,
    String email,
    String name
) implements UserEvent {
    @Override
    public String getAggregateId() { return userId; }
}

public record UserUpdated(
    String eventId,
    LocalDateTime timestamp,
    String userId,
    Map<String, Object> changedFields
) implements UserEvent {
    @Override
    public String getAggregateId() { return userId; }
}

public record UserDeleted(
    String eventId,
    LocalDateTime timestamp,
    String userId,
    String reason
) implements UserEvent {
    @Override
    public String getAggregateId() { return userId; }
}

// 주문 관련 이벤트
public sealed interface OrderEvent extends DomainEvent 
    permits OrderCreated, OrderStatusChanged, OrderCancelled {
}

public record OrderCreated(
    String eventId,
    LocalDateTime timestamp,
    String orderId,
    String userId,
    List<OrderItem> items,
    BigDecimal totalAmount
) implements OrderEvent {
    @Override
    public String getAggregateId() { return orderId; }
}

public record OrderStatusChanged(
    String eventId,
    LocalDateTime timestamp,
    String orderId,
    OrderStatus oldStatus,
    OrderStatus newStatus
) implements OrderEvent {
    @Override
    public String getAggregateId() { return orderId; }
}

// 이벤트 핸들러
@Component
public class DomainEventHandler {
    
    @EventListener
    public void handleEvent(DomainEvent event) {
        switch (event) {
            case UserRegistered userRegistered -> {
                emailService.sendWelcomeEmail(userRegistered.email());
                analyticsService.trackUserRegistration(userRegistered);
            }
            case UserUpdated userUpdated -> {
                auditService.logUserUpdate(userUpdated);
                if (userUpdated.changedFields().containsKey("email")) {
                    emailService.sendEmailChangeNotification(userUpdated.userId());
                }
            }
            case UserDeleted userDeleted -> {
                auditService.logUserDeletion(userDeleted);
                cleanupService.scheduleUserDataCleanup(userDeleted.userId());
            }
            case OrderCreated orderCreated -> {
                inventoryService.reserveItems(orderCreated.items());
                notificationService.notifyOrderCreated(orderCreated);
            }
            case OrderStatusChanged statusChanged -> {
                notificationService.notifyStatusChange(statusChanged);
                if (statusChanged.newStatus() instanceof Delivered) {
                    loyaltyService.addPoints(statusChanged.orderId());
                }
            }
            default -> {
                logger.warn("Unhandled event type: {}", event.getClass().getSimpleName());
            }
        }
    }
}
```

### 4. JSON 파싱 및 데이터 모델링
```java
public sealed interface JsonValue 
    permits JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean, JsonNull {
    
    default String toJsonString() {
        return switch (this) {
            case JsonObject(var properties) -> {
                String content = properties.entrySet().stream()
                    .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue().toJsonString())
                    .collect(Collectors.joining(","));
                yield "{" + content + "}";
            }
            case JsonArray(var values) -> {
                String content = values.stream()
                    .map(JsonValue::toJsonString)
                    .collect(Collectors.joining(","));
                yield "[" + content + "]";
            }
            case JsonString(var value) -> "\"" + value + "\"";
            case JsonNumber(var value) -> value.toString();
            case JsonBoolean(var value) -> value.toString();
            case JsonNull() -> "null";
        };
    }
}

public record JsonObject(Map<String, JsonValue> properties) implements JsonValue {
    public JsonObject() {
        this(new HashMap<>());
    }
    
    public Optional<JsonValue> get(String key) {
        return Optional.ofNullable(properties.get(key));
    }
    
    public Optional<String> getString(String key) {
        return get(key).flatMap(value -> 
            value instanceof JsonString(var str) ? Optional.of(str) : Optional.empty()
        );
    }
    
    public Optional<BigDecimal> getNumber(String key) {
        return get(key).flatMap(value ->
            value instanceof JsonNumber(var num) ? Optional.of(num) : Optional.empty()
        );
    }
}

public record JsonArray(List<JsonValue> values) implements JsonValue {
    public JsonArray() {
        this(new ArrayList<>());
    }
    
    public int size() { return values.size(); }
    
    public Optional<JsonValue> get(int index) {
        return index >= 0 && index < values.size() 
            ? Optional.of(values.get(index)) 
            : Optional.empty();
    }
}

public record JsonString(String value) implements JsonValue {}
public record JsonNumber(BigDecimal value) implements JsonValue {}
public record JsonBoolean(boolean value) implements JsonValue {}
public record JsonNull() implements JsonValue {}

// 사용 예시
public class JsonProcessor {
    
    public void processApiResponse(JsonValue response) {
        switch (response) {
            case JsonObject obj when obj.getString("status").equals(Optional.of("success")) -> {
                Optional<JsonValue> data = obj.get("data");
                processSuccessResponse(data);
            }
            case JsonObject obj when obj.getString("status").equals(Optional.of("error")) -> {
                String errorMessage = obj.getString("message").orElse("Unknown error");
                handleError(errorMessage);
            }
            case JsonArray array -> {
                array.values().forEach(this::processApiResponse);
            }
            default -> {
                logger.warn("Unexpected JSON structure: {}", response.toJsonString());
            }
        }
    }
}
```

## Pattern Matching과의 조합

### Switch Expression과 함께 사용
```java
public class PaymentProcessor {
    
    public BigDecimal calculateFee(PaymentMethod method, BigDecimal amount) {
        return switch (method) {
            case CreditCard(var number, var type) -> 
                calculateCreditCardFee(amount, type);
            case DebitCard(var number, var bank) -> 
                calculateDebitCardFee(amount, bank);
            case BankTransfer(var accountNumber, var routingNumber) -> 
                new BigDecimal("5.00"); // 고정 수수료
            case PayPal(var email) -> 
                amount.multiply(new BigDecimal("0.029")).add(new BigDecimal("0.30"));
            case ApplePay(var deviceId) -> 
                amount.multiply(new BigDecimal("0.015"));
            case GooglePay(var accountId) -> 
                amount.multiply(new BigDecimal("0.015"));
        };
    }
    
    public String getPaymentDescription(PaymentMethod method) {
        return switch (method) {
            case CreditCard(var number, var type) -> 
                type + " 카드 ****" + number.substring(number.length() - 4);
            case DebitCard(var number, var bank) -> 
                bank + " 체크카드 ****" + number.substring(number.length() - 4);
            case BankTransfer(var account, var routing) -> 
                "계좌이체 " + account;
            case PayPal(var email) -> 
                "PayPal (" + email + ")";
            case ApplePay(var deviceId) -> 
                "Apple Pay";
            case GooglePay(var accountId) -> 
                "Google Pay";
        };
    }
}
```

## 면접 핵심 포인트

### 자주 묻는 질문들

**Q: Sealed Classes의 장점은?**
1. **제한된 상속**: 명시적으로 허용된 클래스만 상속 가능
2. **완전성 보장**: Pattern matching에서 모든 케이스 처리 확인
3. **도메인 모델링**: 고정된 타입 집합을 안전하게 표현
4. **컴파일 타임 안전성**: 새로운 서브타입 추가 시 컴파일 에러로 누락 방지

**Q: 언제 Sealed Classes를 사용하나요?**
- **상태 패턴**: 고정된 상태 집합 표현
- **Result Type**: 성공/실패 등의 고정된 결과 타입
- **도메인 이벤트**: 제한된 이벤트 타입 집합
- **API 응답**: 정해진 응답 타입들

**Q: 일반 상속과의 차이점은?**
- **일반 상속**: 누구나 상속 가능, 열린 계층 구조
- **Sealed Classes**: 허용된 클래스만 상속, 닫힌 계층 구조
- **Pattern Matching**: Sealed classes는 완전성 검사 지원

**Q: Enum과의 차이점은?**
- **Enum**: 상수만 가능, 메서드는 모든 인스턴스가 동일
- **Sealed Classes**: 각 서브타입이 다른 데이터와 동작 가능

## 실무 주의사항

### 1. 설계 고려사항
```java
// 좋은 예: 명확한 도메인 개념
public sealed interface OrderState 
    permits Draft, Submitted, Processing, Completed, Cancelled {
    // 명확한 상태 전이 규칙
}

// 주의: 너무 많은 서브타입
public sealed interface Animal 
    permits Dog, Cat, Bird, Fish, Reptile, Mammal, Amphibian /* ... 수십 개 */ {
    // 관리가 어려워짐
}
```

### 2. 성능 고려사항
```java
// Pattern matching은 효율적인 dispatch 생성
public String handleRequest(Request request) {
    return switch (request) {
        case GetRequest(var path) -> handleGet(path);
        case PostRequest(var path, var body) -> handlePost(path, body);
        case PutRequest(var path, var body) -> handlePut(path, body);
        case DeleteRequest(var path) -> handleDelete(path);
    };
    // 컴파일러가 효율적인 분기 코드 생성
}
```

### 3. 진화 전략
```java
// 새로운 서브타입 추가 시
public sealed interface PaymentMethod 
    permits CreditCard, DebitCard, BankTransfer, DigitalWallet /* 새로운 타입 추가 */ {
}

// 모든 switch 문에서 컴파일 에러 발생
// → 누락된 케이스 처리 강제됨
```

### 4. 테스트 전략
```java
@Test
public void testAllOrderStates() {
    // 모든 상태에 대한 테스트 보장
    List<OrderState> allStates = List.of(
        new Draft(),
        new Submitted(LocalDateTime.now()),
        new Processing("order-123"),
        new Completed(LocalDateTime.now()),
        new Cancelled("user-requested")
    );
    
    allStates.forEach(state -> {
        assertNotNull(state.getStatusMessage());
        // 각 상태별 동작 테스트
    });
}
```

## 마이그레이션 가이드

### Enum에서 Sealed Classes로
```java
// Before: Enum 사용
public enum PaymentStatus {
    PENDING, PROCESSING, COMPLETED, FAILED;
    
    public String getDescription() {
        return switch (this) {
            case PENDING -> "결제 대기중";
            case PROCESSING -> "결제 처리중";
            case COMPLETED -> "결제 완료";
            case FAILED -> "결제 실패";
        };
    }
}

// After: Sealed Classes 사용 (더 많은 데이터 포함 가능)
public sealed interface PaymentStatus 
    permits Pending, Processing, Completed, Failed {
    String getDescription();
}

public record Pending() implements PaymentStatus {
    @Override
    public String getDescription() { return "결제 대기중"; }
}

public record Processing(String transactionId, LocalDateTime startedAt) implements PaymentStatus {
    @Override
    public String getDescription() { 
        return "결제 처리중 (거래번호: " + transactionId + ")"; 
    }
}

public record Completed(String transactionId, LocalDateTime completedAt, BigDecimal amount) implements PaymentStatus {
    @Override
    public String getDescription() { 
        return "결제 완료 - " + amount + "원"; 
    }
}

public record Failed(String reason, String errorCode, LocalDateTime failedAt) implements PaymentStatus {
    @Override
    public String getDescription() { return "결제 실패: " + reason; }
}
```

## 핵심 요약

### Sealed Classes 특징
- **제한된 상속**: permits로 허용된 클래스만 상속/구현 가능
- **완전성 보장**: Pattern matching에서 모든 케이스 처리 검증
- **타입 안전성**: 컴파일 타임에 타입 계층 구조 검증

### 활용 영역
- **상태 패턴**: 고정된 상태 집합 표현
- **Result Type**: 성공/실패 등의 결과 타입
- **도메인 이벤트**: 제한된 이벤트 타입 집합
- **API 모델링**: 정해진 응답/요청 타입들

### 실무 이점
- **안전한 리팩토링**: 새 서브타입 추가 시 컴파일 에러로 누락 방지
- **명확한 도메인 모델**: 가능한 타입이 명시적으로 제한됨
- **Pattern Matching 활용**: Switch expression과 완벽한 조합

### 설계 원칙
- **도메인 중심**: 비즈니스 개념을 명확히 표현
- **진화 고려**: 새로운 타입 추가에 대한 전략 수립
- **적절한 규모**: 너무 많은 서브타입은 관리 부담 증가