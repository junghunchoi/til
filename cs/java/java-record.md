# Record 클래스의 특징과 언제 사용해야 하는가?

## 핵심 개념

### Record란? (Java 14+)
Java 14에서 preview로 도입되고 Java 16에서 정식 출시된 **불변 데이터 클래스**를 간결하게 정의하는 기능

### 기본 문법
```java
// 전통적인 방식
public class PersonOld {
    private final String name;
    private final int age;
    
    public PersonOld(String name, int age) {
        this.name = name;
        this.age = age;
    }
    
    public String getName() { return name; }
    public int getAge() { return age; }
    
    @Override
    public boolean equals(Object obj) {
        // 긴 equals 구현...
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }
    
    @Override
    public String toString() {
        return "Person{name='" + name + "', age=" + age + "}";
    }
}

// Record 방식
public record Person(String name, int age) {}
```

## Record의 특징

### 1. 자동 생성되는 요소들

#### 컴파일러가 자동으로 생성
```java
public record User(String email, String name, LocalDateTime createdAt) {}

// 다음과 같은 요소들이 자동 생성됨:
// 1. private final 필드들
// 2. public 생성자
// 3. public 접근자 메서드 (email(), name(), createdAt())
// 4. equals() 메서드
// 5. hashCode() 메서드
// 6. toString() 메서드
```

#### 사용 예시
```java
User user = new User("john@example.com", "John Doe", LocalDateTime.now());

// 접근자 메서드 (getter가 아닌 필드명 그대로)
String email = user.email();
String name = user.name();
LocalDateTime created = user.createdAt();

// 자동 생성된 toString
System.out.println(user);
// 출력: User[email=john@example.com, name=John Doe, createdAt=2024-01-15T10:30:00]
```

### 2. 불변성 (Immutability)
```java
public record Product(String name, BigDecimal price, List<String> categories) {}

Product product = new Product("Laptop", new BigDecimal("1000"), 
                             List.of("Electronics", "Computers"));

// 필드 수정 불가 (컴파일 에러)
// product.name = "Desktop"; // 불가능

// 하지만 mutable 객체의 내부 상태는 변경 가능 (주의!)
// product.categories().add("Gaming"); // 위험! - UnsupportedOperationException (List.of 사용 시)
```

### 3. 상속 제한
```java
// Record는 다른 클래스를 상속할 수 없음
// public record Employee(String name) extends Person {} // 컴파일 에러

// Record는 다른 Record를 상속할 수 없음
// public record Manager(String name, String department) extends Employee {} // 컴파일 에러

// 하지만 인터페이스 구현은 가능
public record Employee(String name, String department) implements Comparable<Employee> {
    @Override
    public int compareTo(Employee other) {
        return this.name.compareTo(other.name);
    }
}
```

## Record 고급 활용

### 1. 커스텀 생성자
```java
public record Temperature(double celsius) {
    
    // Compact constructor - 유효성 검증
    public Temperature {
        if (celsius < -273.15) {
            throw new IllegalArgumentException("Temperature below absolute zero");
        }
    }
    
    // 추가 생성자
    public Temperature(double fahrenheit, boolean isFahrenheit) {
        this(isFahrenheit ? (fahrenheit - 32) * 5/9 : fahrenheit);
    }
    
    // 추가 메서드
    public double fahrenheit() {
        return celsius * 9/5 + 32;
    }
    
    public boolean isFreezing() {
        return celsius <= 0;
    }
}
```

### 2. 정적 팩토리 메서드
```java
public record EmailAddress(String value) {
    
    public EmailAddress {
        if (!isValidEmail(value)) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
    }
    
    public static EmailAddress of(String email) {
        return new EmailAddress(email.toLowerCase().trim());
    }
    
    public static EmailAddress fromParts(String localPart, String domain) {
        return new EmailAddress(localPart + "@" + domain);
    }
    
    private static boolean isValidEmail(String email) {
        return email.contains("@") && email.length() > 3;
    }
    
    public String domain() {
        return value.substring(value.indexOf("@") + 1);
    }
}
```

### 3. Nested Record
```java
public record Order(String orderId, Customer customer, List<Item> items, OrderStatus status) {
    
    public record Customer(String name, EmailAddress email, Address address) {}
    
    public record Item(String productId, String name, int quantity, BigDecimal price) {
        public BigDecimal totalPrice() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }
    
    public record Address(String street, String city, String zipCode) {}
    
    public enum OrderStatus { PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }
    
    public BigDecimal totalAmount() {
        return items.stream()
                   .map(Item::totalPrice)
                   .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

## 실제 활용 사례

### 1. DTO (Data Transfer Object)
```java
// API 응답용 DTO
public record UserResponse(Long id, String username, String email, 
                          LocalDateTime lastLogin, List<String> roles) {}

// API 요청용 DTO
public record CreateUserRequest(String username, String email, String password) {
    public CreateUserRequest {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
    }
}

// 서비스에서 사용
@RestController
public class UserController {
    
    @PostMapping("/users")
    public UserResponse createUser(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);
        return new UserResponse(user.getId(), user.getUsername(), 
                               user.getEmail(), user.getLastLogin(), user.getRoles());
    }
}
```

### 2. Value Object (Domain-Driven Design)
```java
public record Money(BigDecimal amount, Currency currency) {
    
    public Money {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        Objects.requireNonNull(currency, "Currency cannot be null");
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }
    
    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currency);
    }
    
    public static Money usd(String amount) {
        return new Money(new BigDecimal(amount), Currency.getInstance("USD"));
    }
    
    public static Money krw(String amount) {
        return new Money(new BigDecimal(amount), Currency.getInstance("KRW"));
    }
}
```

### 3. 설정 정보
```java
public record DatabaseConfig(String url, String username, String password,
                           int maxPoolSize, Duration connectionTimeout) {
    
    public DatabaseConfig {
        Objects.requireNonNull(url, "URL cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("Max pool size must be positive");
        }
    }
    
    public static DatabaseConfig fromProperties(Properties props) {
        return new DatabaseConfig(
            props.getProperty("db.url"),
            props.getProperty("db.username"),
            props.getProperty("db.password"),
            Integer.parseInt(props.getProperty("db.maxPoolSize", "10")),
            Duration.ofSeconds(Long.parseLong(props.getProperty("db.timeout", "30")))
        );
    }
}
```

### 4. 함수형 프로그래밍과의 조합
```java
public record Result<T>(T data, boolean success, String message) {
    
    public static <T> Result<T> success(T data) {
        return new Result<>(data, true, "Success");
    }
    
    public static <T> Result<T> failure(String message) {
        return new Result<>(null, false, message);
    }
    
    public <U> Result<U> map(Function<T, U> mapper) {
        return success ? Result.success(mapper.apply(data)) 
                      : Result.failure(message);
    }
    
    public Result<T> filter(Predicate<T> predicate, String errorMessage) {
        return success && predicate.test(data) ? this 
                                              : Result.failure(errorMessage);
    }
}

// 사용 예시
Result<String> result = Result.success("hello")
    .map(String::toUpperCase)
    .filter(s -> s.length() > 3, "Too short");
```

## 언제 Record를 사용해야 하는가?

### 사용하기 좋은 경우

#### 1. 불변 데이터 클래스
```java
// 좋은 예: 데이터만 담는 클래스
public record Point(int x, int y) {}
public record Person(String name, int age) {}
public record ApiResponse(int status, String message, Object data) {}
```

#### 2. Value Object
```java
// 좋은 예: 도메인의 값 객체
public record UserId(Long value) {}
public record Email(String address) {}
public record PhoneNumber(String number) {}
```

#### 3. DTO / 설정 객체
```java
// 좋은 예: 데이터 전송 객체
public record UserDto(String name, String email) {}
public record DatabaseConfig(String url, int port) {}
```

### 사용하지 말아야 하는 경우

#### 1. 가변 상태가 필요한 경우
```java
// 나쁜 예: 상태가 변경되어야 하는 엔티티
// public record User(String name, String email) {} // X

// 좋은 예: 전통적인 클래스
public class User {
    private String name;
    private String email;
    
    // getters, setters, business methods
}
```

#### 2. 복잡한 비즈니스 로직이 있는 경우
```java
// 나쁜 예: 복잡한 로직이 있는 도메인 객체를 Record로
// public record Order(List<Item> items) {
//     public void addItem(Item item) { ... } // 복잡한 비즈니스 로직
//     public void calculateDiscount() { ... }
// }

// 좋은 예: 전통적인 클래스
public class Order {
    private List<Item> items = new ArrayList<>();
    
    public void addItem(Item item) {
        // 복잡한 비즈니스 로직
        validateItem(item);
        applyBusinessRules(item);
        items.add(item);
        notifyObservers();
    }
}
```

#### 3. 상속이 필요한 경우
```java
// 나쁜 예: 상속 구조가 필요한 경우
// Record는 상속을 지원하지 않음

// 좋은 예: 추상 클래스와 상속 사용
public abstract class Animal {
    protected String name;
    public abstract void makeSound();
}

public class Dog extends Animal {
    @Override
    public void makeSound() { System.out.println("Woof!"); }
}
```

## 성능 고려사항

### 메모리 효율성
```java
// Record는 컴팩트한 메모리 사용
public record Coordinate(double x, double y) {}

// 전통적인 클래스보다 메모리 오버헤드가 적음
// - 자동 생성된 메서드들이 최적화됨
// - 불필요한 필드나 메서드 없음
```

### 성능 벤치마크 예시
```java
// JMH 벤치마크 결과 예시
@Benchmark
public void recordCreation() {
    Point point = new Point(10, 20); // Record
}

@Benchmark  
public void classCreation() {
    PointClass point = new PointClass(10, 20); // 전통적 클래스
}

// 결과: Record가 약간 더 빠름 (객체 생성 및 메서드 호출)
```

## 면접 핵심 포인트

### 자주 묻는 질문들

**Q: Record와 일반 클래스의 차이점은?**
1. **불변성**: Record는 기본적으로 불변
2. **자동 생성**: equals, hashCode, toString 자동 생성
3. **상속 제한**: Record는 상속 불가
4. **간결성**: 보일러플레이트 코드 제거

**Q: Record는 언제 사용하나요?**
- **데이터 클래스**: 단순히 데이터를 담는 용도
- **Value Object**: DDD의 값 객체
- **DTO**: 데이터 전송 객체
- **설정 객체**: 불변 설정 정보

**Q: Record의 제한사항은?**
- 상속 불가 (다른 클래스나 Record 상속 불가)
- 모든 필드가 final (불변)
- 인스턴스 필드 추가 불가

**Q: Record와 Lombok @Data의 차이는?**
- **Record**: Java 표준, 불변성 보장
- **@Data**: 가변 가능, 더 많은 기능, 외부 라이브러리 의존

## 실무 주의사항

### 1. 가변 객체 포함 시 주의
```java
// 위험한 예시
public record UserData(String name, List<String> roles) {}

UserData user = new UserData("John", new ArrayList<>(List.of("USER")));
user.roles().add("ADMIN"); // 외부에서 내부 상태 변경 가능!

// 안전한 예시
public record UserData(String name, List<String> roles) {
    public UserData(String name, List<String> roles) {
        this.name = name;
        this.roles = List.copyOf(roles); // 불변 복사본 생성
    }
}
```

### 2. 직렬화 고려
```java
// JSON 직렬화를 위한 Jackson 어노테이션
public record ApiResponse(
    @JsonProperty("status") int statusCode,
    @JsonProperty("message") String responseMessage,
    @JsonProperty("data") Object responseData
) {}
```

### 3. 검증 로직 위치
```java
public record Email(String address) {
    // Compact constructor에서 검증
    public Email {
        if (address == null || !address.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        // 정규화
        address = address.toLowerCase().trim();
    }
    
    // 정적 팩토리 메서드에서 검증
    public static Email of(String address) {
        return new Email(address);
    }
}
```

## 핵심 요약

### Record 특징
- **불변 데이터 클래스**를 간결하게 정의
- **자동 생성**: 생성자, 접근자, equals, hashCode, toString
- **상속 불가**: 다른 클래스 상속 불가, 인터페이스 구현 가능

### 사용 시점
- **✅ 사용하기 좋은 경우**: 불변 데이터, Value Object, DTO, 설정 객체
- **❌ 피해야 하는 경우**: 가변 상태, 복잡한 비즈니스 로직, 상속 필요

### 실무 팁
- **검증 로직**은 compact constructor에서 처리
- **가변 객체** 포함 시 방어적 복사 고려
- **직렬화** 라이브러리와의 호환성 확인
- **성능상 이점**이 있지만 설계 원칙을 우선 고려