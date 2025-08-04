# Pattern Matching과 Switch Expression (Java 14+)

## 핵심 개념

### Pattern Matching이란?
데이터의 구조나 값을 패턴으로 매칭하여 조건부 로직을 간결하게 표현하는 프로그래밍 기법

### Switch Expression (Java 14+)
기존 switch 문의 한계를 극복하고 표현식으로 사용할 수 있는 새로운 형태

## Switch Expression 기본

### 전통적인 Switch 문의 문제점
```java
// Java 13 이전 - 문제가 많은 코드
public String getSeasonOld(int month) {
    String season;
    switch (month) {
        case 12:
        case 1:
        case 2:
            season = "겨울";
            break;  // break 빠뜨리기 쉬움
        case 3:
        case 4:
        case 5:
            season = "봄";
            break;
        case 6:
        case 7:
        case 8:
            season = "여름";
            break;
        case 9:
        case 10:
        case 11:
            season = "가을";
            break;
        default:
            season = "잘못된 월";
    }
    return season;  // 변수 선언 필요
}
```

### Switch Expression (Java 14+)
```java
// Java 14+ - 간결하고 안전한 코드
public String getSeason(int month) {
    return switch (month) {
        case 12, 1, 2 -> "겨울";      // 화살표 구문, 여러 값 매칭
        case 3, 4, 5 -> "봄";
        case 6, 7, 8 -> "여름"; 
        case 9, 10, 11 -> "가을";
        default -> "잘못된 월";
    };  // 표현식으로 직접 반환
}

// 블록 형태도 가능
public String getSeasonDetailed(int month) {
    return switch (month) {
        case 12, 1, 2 -> {
            System.out.println("추운 계절입니다");
            yield "겨울";  // yield 키워드 사용
        }
        case 3, 4, 5 -> {
            System.out.println("따뜻한 계절입니다");
            yield "봄";
        }
        case 6, 7, 8 -> {
            System.out.println("더운 계절입니다");
            yield "여름";
        }
        case 9, 10, 11 -> {
            System.out.println("선선한 계절입니다");
            yield "가을";
        }
        default -> "잘못된 월";
    };
}
```

## Pattern Matching for instanceof (Java 16+)

### 전통적인 instanceof 사용법
```java
// Java 15 이전
public String processShapeOld(Object shape) {
    if (shape instanceof Circle) {
        Circle circle = (Circle) shape;  // 명시적 캐스팅 필요
        return "Circle with radius: " + circle.radius();
    } else if (shape instanceof Rectangle) {
        Rectangle rectangle = (Rectangle) shape;  // 반복적인 캐스팅
        return "Rectangle: " + rectangle.width() + "x" + rectangle.height();
    } else if (shape instanceof Triangle) {
        Triangle triangle = (Triangle) shape;
        return "Triangle with base: " + triangle.base();
    }
    return "Unknown shape";
}
```

### Pattern Matching for instanceof (Java 16+)
```java
// Java 16+ - 패턴 매칭
public String processShape(Object shape) {
    if (shape instanceof Circle circle) {  // 자동 캐스팅 및 변수 선언
        return "Circle with radius: " + circle.radius();
    } else if (shape instanceof Rectangle rect) {
        return "Rectangle: " + rect.width() + "x" + rect.height();  
    } else if (shape instanceof Triangle triangle) {
        return "Triangle with base: " + triangle.base();
    }
    return "Unknown shape";
}

// 조건과 함께 사용
public boolean isLargeShape(Object shape) {
    return shape instanceof Circle circle && circle.radius() > 10 ||
           shape instanceof Rectangle rect && rect.width() * rect.height() > 100 ||
           shape instanceof Triangle triangle && triangle.base() > 15;
}
```

## Pattern Matching for Switch (Java 17+ Preview, Java 21 완성)

### Record와 함께 사용하는 패턴 매칭
```java
// Shape 계층 구조
public sealed interface Shape permits Circle, Rectangle, Triangle {}
public record Circle(double radius) implements Shape {}
public record Rectangle(double width, double height) implements Shape {}
public record Triangle(double base, double height) implements Shape {}

// Java 21+ Switch Pattern Matching
public double calculateArea(Shape shape) {
    return switch (shape) {
        case Circle(var radius) -> Math.PI * radius * radius;
        case Rectangle(var width, var height) -> width * height;
        case Triangle(var base, var height) -> 0.5 * base * height;
    };
}

// 더 복잡한 패턴 매칭
public String analyzeShape(Shape shape) {
    return switch (shape) {
        case Circle(var radius) when radius > 10 -> 
            "Large circle with radius " + radius;
        case Circle(var radius) -> 
            "Small circle with radius " + radius;
        case Rectangle(var width, var height) when width == height ->
            "Square with side " + width;
        case Rectangle(var width, var height) ->
            "Rectangle " + width + "x" + height;
        case Triangle(var base, var height) when base == height ->
            "Isosceles triangle";
        case Triangle(var base, var height) ->
            "Triangle with base " + base + " and height " + height;
    };
}
```

### Null 처리
```java
public String processValue(Object value) {
    return switch (value) {
        case null -> "Null value";
        case String s -> "String: " + s;
        case Integer i -> "Integer: " + i;
        case Double d -> "Double: " + d;
        default -> "Unknown type: " + value.getClass().getSimpleName();
    };
}
```

## 실제 활용 사례

### 1. HTTP 상태 코드 처리
```java
public enum HttpStatus {
    OK(200), CREATED(201), BAD_REQUEST(400), 
    UNAUTHORIZED(401), NOT_FOUND(404), INTERNAL_SERVER_ERROR(500);
    
    private final int code;
    HttpStatus(int code) { this.code = code; }
    public int getCode() { return code; }
}

public String getStatusMessage(HttpStatus status) {
    return switch (status) {
        case OK -> "요청이 성공했습니다";
        case CREATED -> "리소스가 생성되었습니다";
        case BAD_REQUEST -> "잘못된 요청입니다";
        case UNAUTHORIZED -> "인증이 필요합니다";
        case NOT_FOUND -> "리소스를 찾을 수 없습니다";
        case INTERNAL_SERVER_ERROR -> "서버 내부 오류입니다";
    };
}

public boolean isSuccessStatus(HttpStatus status) {
    return switch (status) {
        case OK, CREATED -> true;
        case BAD_REQUEST, UNAUTHORIZED, NOT_FOUND, INTERNAL_SERVER_ERROR -> false;
    };
}
```

### 2. 도메인 객체 처리
```java
// 결제 방법 처리
public sealed interface PaymentMethod permits CreditCard, BankTransfer, PayPal {}
public record CreditCard(String number, String holder) implements PaymentMethod {}
public record BankTransfer(String accountNumber, String bankCode) implements PaymentMethod {}
public record PayPal(String email) implements PaymentMethod {}

public BigDecimal calculateFee(PaymentMethod method, BigDecimal amount) {
    return switch (method) {
        case CreditCard(var number, var holder) -> 
            amount.multiply(new BigDecimal("0.025")); // 2.5% 수수료
        case BankTransfer(var account, var bank) ->
            new BigDecimal("1000"); // 고정 수수료
        case PayPal(var email) ->
            amount.multiply(new BigDecimal("0.035")); // 3.5% 수수료
    };
}

public String getPaymentDescription(PaymentMethod method) {
    return switch (method) {
        case CreditCard(var number, var holder) ->
            "신용카드 결제 (" + maskCardNumber(number) + ")";
        case BankTransfer(var account, var bank) ->
            "계좌이체 (" + bank + " " + maskAccount(account) + ")";
        case PayPal(var email) ->
            "PayPal 결제 (" + email + ")";
    };
}
```

### 3. 이벤트 처리 시스템
```java
public sealed interface Event permits UserEvent, OrderEvent, SystemEvent {}

public sealed interface UserEvent extends Event permits UserRegistered, UserLoggedIn, UserLoggedOut {}
public record UserRegistered(String userId, String email, LocalDateTime timestamp) implements UserEvent {}
public record UserLoggedIn(String userId, String ipAddress, LocalDateTime timestamp) implements UserEvent {}
public record UserLoggedOut(String userId, LocalDateTime timestamp) implements UserEvent {}

public sealed interface OrderEvent extends Event permits OrderCreated, OrderPaid, OrderShipped {}
public record OrderCreated(String orderId, String userId, BigDecimal amount) implements OrderEvent {}
public record OrderPaid(String orderId, String paymentId) implements OrderEvent {}
public record OrderShipped(String orderId, String trackingNumber) implements OrderEvent {}

public record SystemEvent(String message, Level level, LocalDateTime timestamp) implements Event {}

public enum Level { INFO, WARN, ERROR }

// 이벤트 처리
public void handleEvent(Event event) {
    switch (event) {
        case UserRegistered(var userId, var email, var timestamp) -> {
            System.out.println("새 사용자 등록: " + userId + " (" + email + ")");
            sendWelcomeEmail(email);
        }
        case UserLoggedIn(var userId, var ip, var timestamp) -> {
            System.out.println("사용자 로그인: " + userId + " from " + ip);
            updateLastLoginTime(userId, timestamp);
        }
        case UserLoggedOut(var userId, var timestamp) -> {
            System.out.println("사용자 로그아웃: " + userId);
            clearUserSession(userId);
        }
        case OrderCreated(var orderId, var userId, var amount) -> {
            System.out.println("주문 생성: " + orderId + " (금액: " + amount + ")");
            processOrder(orderId);
        }
        case OrderPaid(var orderId, var paymentId) -> {
            System.out.println("주문 결제 완료: " + orderId);
            confirmOrder(orderId);
        }
        case OrderShipped(var orderId, var trackingNumber) -> {
            System.out.println("주문 배송 시작: " + orderId + " (추적번호: " + trackingNumber + ")");
            notifyShipping(orderId, trackingNumber);
        }
        case SystemEvent(var message, var level, var timestamp) -> {
            logSystemEvent(message, level, timestamp);
        }
    };
}
```

### 4. JSON 파싱 및 처리
```java
public sealed interface JsonValue permits JsonString, JsonNumber, JsonBoolean, JsonArray, JsonObject, JsonNull {}
public record JsonString(String value) implements JsonValue {}
public record JsonNumber(BigDecimal value) implements JsonValue {}
public record JsonBoolean(boolean value) implements JsonValue {}
public record JsonArray(List<JsonValue> values) implements JsonValue {}
public record JsonObject(Map<String, JsonValue> properties) implements JsonValue {}
public record JsonNull() implements JsonValue {}

public String jsonToString(JsonValue json) {
    return switch (json) {
        case JsonString(var s) -> "\"" + s + "\"";
        case JsonNumber(var n) -> n.toString();
        case JsonBoolean(var b) -> b.toString();
        case JsonNull() -> "null";
        case JsonArray(var values) -> "[" + 
            values.stream().map(this::jsonToString).collect(Collectors.joining(", ")) + "]";
        case JsonObject(var props) -> "{" +
            props.entrySet().stream()
                 .map(entry -> "\"" + entry.getKey() + "\": " + jsonToString(entry.getValue()))
                 .collect(Collectors.joining(", ")) + "}";
    };
}

public Optional<String> extractString(JsonValue json, String key) {
    return switch (json) {
        case JsonObject(var props) -> switch (props.get(key)) {
            case JsonString(var s) -> Optional.of(s);
            case null -> Optional.empty();
            default -> Optional.empty();
        };
        default -> Optional.empty();
    };
}
```

## 고급 패턴 매칭 기법

### Guard 조건 (When 절)
```java
public String classifyNumber(Object obj) {
    return switch (obj) {
        case Integer i when i > 0 -> "Positive integer: " + i;
        case Integer i when i < 0 -> "Negative integer: " + i;
        case Integer i -> "Zero";
        case Double d when d > 0.0 -> "Positive double: " + d;
        case Double d when d < 0.0 -> "Negative double: " + d;
        case Double d -> "Zero double";
        case String s when s.isEmpty() -> "Empty string";
        case String s -> "String: " + s;
        default -> "Unknown type";
    };
}
```

### 중첩 패턴
```java
public record Point(double x, double y) {}
public record Line(Point start, Point end) {}
public record Circle(Point center, double radius) {}

public boolean isOriginRelated(Object shape) {
    return switch (shape) {
        case Point(var x, var y) when x == 0 || y == 0 -> true;
        case Line(Point(var x1, var y1), Point(var x2, var y2)) 
            when (x1 == 0 && y1 == 0) || (x2 == 0 && y2 == 0) -> true;
        case Circle(Point(var x, var y), var radius) 
            when x == 0 && y == 0 -> true;
        default -> false;
    };
}
```

### 배열 패턴 (미래 기능)
```java
// 미래 Java 버전에서 지원 예정
public String analyzeArray(int[] array) {
    return switch (array) {
        case [] -> "Empty array";
        case [var single] -> "Single element: " + single;
        case [var first, var second] -> "Two elements: " + first + ", " + second;
        case [var first, var... rest] -> "Starts with: " + first + ", has " + rest.length + " more";
    };
}
```

## 성능 고려사항

### 컴파일러 최적화
```java
// Switch expression은 컴파일러가 최적화
public int getQuarter(int month) {
    return switch (month) {
        case 1, 2, 3 -> 1;
        case 4, 5, 6 -> 2;
        case 7, 8, 9 -> 3;
        case 10, 11, 12 -> 4;
        default -> throw new IllegalArgumentException("Invalid month: " + month);
    };
    // 컴파일러가 효율적인 jump table 생성
}

// vs if-else chain (덜 효율적)
public int getQuarterOld(int month) {
    if (month >= 1 && month <= 3) return 1;
    else if (month >= 4 && month <= 6) return 2;
    else if (month >= 7 && month <= 9) return 3;
    else if (month >= 10 && month <= 12) return 4;
    else throw new IllegalArgumentException("Invalid month: " + month);
}
```

### 패턴 매칭 성능
```java
// instanceof 패턴 매칭은 기존 instanceof + cast보다 빠름
public double calculateArea(Shape shape) {
    return switch (shape) {
        case Circle(var r) -> Math.PI * r * r;           // 한 번의 타입 체크
        case Rectangle(var w, var h) -> w * h;
        case Triangle(var b, var h) -> 0.5 * b * h;
    };
}

// vs 전통적인 방식
public double calculateAreaOld(Shape shape) {
    if (shape instanceof Circle) {                        // 타입 체크
        Circle c = (Circle) shape;                        // 캐스팅 (추가 체크)
        return Math.PI * c.radius() * c.radius();
    }
    // ... 반복적인 instanceof + cast
}
```

## 면접 핵심 포인트

### 자주 묻는 질문들

**Q: Switch Expression의 장점은?**
1. **표현식으로 사용 가능**: 값을 직접 반환
2. **Fall-through 제거**: break 불필요
3. **완전성 검사**: 모든 케이스 커버 확인
4. **간결한 문법**: 화살표 구문으로 가독성 향상

**Q: Pattern Matching의 장점은?**
1. **타입 안전성**: 컴파일 타임에 타입 체크
2. **코드 간결성**: 캐스팅과 null 체크 자동화  
3. **성능 향상**: 중복 타입 체크 제거
4. **함수형 스타일**: 불변성과 패턴 기반 처리

**Q: Sealed Class와 Pattern Matching의 관계는?**
- **완전성 보장**: 모든 서브타입을 switch에서 처리했는지 컴파일러가 확인
- **안전한 계층 구조**: 새로운 서브타입 추가 시 컴파일 에러로 누락 방지
- **도메인 모델링**: ADT(Algebraic Data Type) 구현 가능

**Q: 언제 Pattern Matching을 사용하나요?**
- **타입별 분기**: 다형성 객체의 타입별 처리
- **데이터 구조 분해**: Record나 복잡한 객체의 구성 요소 접근
- **상태 기반 로직**: 열거형이나 상태에 따른 다른 처리

## 실무 주의사항

### 1. 가독성과 복잡성의 균형
```java
// 좋은 예: 간단하고 명확
public String getGrade(int score) {
    return switch (score / 10) {
        case 10, 9 -> "A";
        case 8 -> "B";
        case 7 -> "C";
        case 6 -> "D";
        default -> "F";
    };
}

// 피해야 할 예: 너무 복잡한 패턴
public String processComplexData(Object data) {
    return switch (data) {
        case ComplexRecord(var a, var b, var c) when a > 10 && b.startsWith("test") && c.size() > 5 -> {
            // 복잡한 로직...
            yield "complex result";
        }
        // ... 더 복잡한 케이스들
        default -> "default";
    };
}
```

### 2. 성능 고려사항
```java
// 효율적: enum이나 primitive 타입 사용
public String getColor(TrafficLight light) {
    return switch (light) {
        case RED -> "빨간색";
        case YELLOW -> "노란색"; 
        case GREEN -> "초록색";
    };
}

// 주의: 문자열 매칭은 상대적으로 느림
public int getNumber(String text) {
    return switch (text) {
        case "one" -> 1;
        case "two" -> 2;
        case "three" -> 3;
        default -> 0;
    };
}
```

### 3. null 안전성
```java
// null 처리 포함
public String processString(String input) {
    return switch (input) {
        case null -> "입력값이 null입니다";
        case "" -> "빈 문자열입니다";
        case String s when s.length() == 1 -> "한 글자: " + s;
        case String s -> "문자열: " + s;
    };
}
```

## 핵심 요약

### Switch Expression 특징
- **표현식**: 값을 반환하는 식으로 사용 가능
- **완전성**: 모든 케이스를 다뤄야 함 (컴파일러 검증)
- **안전성**: fall-through 없음, break 불필요

### Pattern Matching 장점
- **타입 안전성**: 컴파일 타임 타입 체크
- **성능**: 중복 타입 검사 제거
- **가독성**: 의도가 명확한 코드

### 실무 적용 포인트
- **도메인 모델**: Sealed class + Record + Pattern matching 조합
- **이벤트 처리**: 타입별 이벤트 핸들링
- **데이터 변환**: 타입별 변환 로직
- **상태 관리**: 상태 기반 비즈니스 로직

### 주의사항
- **복잡도 관리**: 너무 복잡한 패턴은 피하기
- **성능 고려**: 문자열보다는 enum이나 primitive 타입 선호
- **호환성**: Java 버전별 지원 범위 확인