# 전략 패턴 (Strategy Pattern)

## 개념
전략 패턴은 실행 중에 알고리즘의 동작을 선택할 수 있게 하는 행동 패턴입니다. 동일한 목적을 달성하는 여러 가지 방법(알고리즘)을 정의하고, 런타임에 적절한 전략을 선택하여 사용할 수 있습니다.

## 언제 사용하면 효과적인가?
- 동일한 작업을 수행하는 여러 가지 방법이 있을 때
- 조건문(if-else, switch)으로 알고리즘을 선택하는 코드를 개선하고 싶을 때
- 런타임에 알고리즘을 동적으로 변경해야 할 때
- 새로운 알고리즘을 자주 추가해야 하는 경우
- 클라이언트가 알고리즘의 구현 세부사항을 알 필요가 없을 때

## 장점
- **유연성**: 런타임에 알고리즘을 변경할 수 있음
- **확장성**: 새로운 전략을 쉽게 추가할 수 있음
- **코드 재사용**: 전략을 다른 컨텍스트에서도 재사용 가능
- **테스트 용이성**: 각 전략을 독립적으로 테스트 가능
- **개방-폐쇄 원칙**: 기존 코드 수정 없이 새로운 전략 추가 가능

## 주의해야 할 점
- 전략이 많아질 경우 클래스 수가 증가
- 클라이언트가 전략의 차이점을 알아야 함
- 간단한 알고리즘의 경우 오버엔지니어링이 될 수 있음

## 자바 예시 코드

### 1. 결제 시스템 예시
```java
// 전략 인터페이스
interface PaymentStrategy {
    void pay(double amount);
    String getPaymentType();
    boolean isAvailable();
}

// 구체적인 전략들
class CreditCardPayment implements PaymentStrategy {
    private String cardNumber;
    private String holderName;
    
    public CreditCardPayment(String cardNumber, String holderName) {
        this.cardNumber = cardNumber;
        this.holderName = holderName;
    }
    
    @Override
    public void pay(double amount) {
        System.out.printf("💳 신용카드로 $%.2f 결제했습니다.\n", amount);
        System.out.printf("카드 소유자: %s\n", holderName);
    }
    
    @Override
    public String getPaymentType() {
        return "신용카드";
    }
    
    @Override
    public boolean isAvailable() {
        return cardNumber != null && cardNumber.length() == 16;
    }
}

class PayPalPayment implements PaymentStrategy {
    private String email;
    
    public PayPalPayment(String email) {
        this.email = email;
    }
    
    @Override
    public void pay(double amount) {
        System.out.printf("💰 PayPal로 $%.2f 결제했습니다.\n", amount);
        System.out.printf("계정: %s\n", email);
    }
    
    @Override
    public String getPaymentType() {
        return "PayPal";
    }
    
    @Override
    public boolean isAvailable() {
        return email != null && email.contains("@");
    }
}

// 컨텍스트 클래스
class PaymentContext {
    private PaymentStrategy paymentStrategy;
    
    public void setPaymentStrategy(PaymentStrategy paymentStrategy) {
        this.paymentStrategy = paymentStrategy;
    }
    
    public void processPayment(double amount) {
        if (paymentStrategy == null) {
            System.out.println("❌ 결제 방법이 선택되지 않았습니다.");
            return;
        }
        
        if (!paymentStrategy.isAvailable()) {
            System.out.println("❌ 선택된 결제 방법을 사용할 수 없습니다.");
            return;
        }
        
        System.out.printf("=== %s 결제 처리 시작 ===\n", paymentStrategy.getPaymentType());
        paymentStrategy.pay(amount);
        System.out.println("✅ 결제가 완료되었습니다.\n");
    }
}

// 클라이언트 코드
public class PaymentStrategyExample {
    public static void main(String[] args) {
        PaymentContext paymentContext = new PaymentContext();
        
        PaymentStrategy creditCard = new CreditCardPayment("1234567890123456", "김철수");
        PaymentStrategy paypal = new PayPalPayment("user@example.com");
        
        double orderAmount = 299.99;
        
        // 신용카드로 결제
        paymentContext.setPaymentStrategy(creditCard);
        paymentContext.processPayment(orderAmount);
        
        // PayPal로 결제
        paymentContext.setPaymentStrategy(paypal);
        paymentContext.processPayment(orderAmount);
    }
}
```

### 2. 할인 정책 예시
```java
import java.time.DayOfWeek;
import java.time.LocalDateTime;

// 할인 전략 인터페이스
interface DiscountStrategy {
    double applyDiscount(double originalPrice);
    String getDiscountDescription();
    boolean isApplicable(Customer customer, LocalDateTime dateTime);
}

// 고객 정보 클래스
class Customer {
    private String id;
    private String membershipLevel;
    private int age;
    private boolean isStudent;
    
    public Customer(String id, String membershipLevel, int age, boolean isStudent) {
        this.id = id;
        this.membershipLevel = membershipLevel;
        this.age = age;
        this.isStudent = isStudent;
    }
    
    // getters
    public String getId() { return id; }
    public String getMembershipLevel() { return membershipLevel; }
    public int getAge() { return age; }
    public boolean isStudent() { return isStudent; }
}

// 구체적인 할인 전략들
class VipDiscountStrategy implements DiscountStrategy {
    @Override
    public double applyDiscount(double originalPrice) {
        return originalPrice * 0.8; // 20% 할인
    }
    
    @Override
    public String getDiscountDescription() {
        return "VIP 할인 20%";
    }
    
    @Override
    public boolean isApplicable(Customer customer, LocalDateTime dateTime) {
        return customer.getMembershipLevel().equals("GOLD") || 
               customer.getMembershipLevel().equals("PLATINUM");
    }
}

class StudentDiscountStrategy implements DiscountStrategy {
    @Override
    public double applyDiscount(double originalPrice) {
        return originalPrice * 0.85; // 15% 할인
    }
    
    @Override
    public String getDiscountDescription() {
        return "학생 할인 15%";
    }
    
    @Override
    public boolean isApplicable(Customer customer, LocalDateTime dateTime) {
        return customer.isStudent();
    }
}

// 클라이언트 코드
public class DiscountStrategyExample {
    public static void main(String[] args) {
        Customer goldCustomer = new Customer("C002", "GOLD", 45, false);
        Customer studentCustomer = new Customer("C003", "SILVER", 22, true);
        
        DiscountStrategy vipStrategy = new VipDiscountStrategy();
        DiscountStrategy studentStrategy = new StudentDiscountStrategy();
        
        double originalPrice = 100.0;
        LocalDateTime now = LocalDateTime.now();
        
        // VIP 할인 적용
        if (vipStrategy.isApplicable(goldCustomer, now)) {
            double finalPrice = vipStrategy.applyDiscount(originalPrice);
            System.out.printf("VIP 할인 적용: $%.2f -> $%.2f\n", originalPrice, finalPrice);
        }
        
        // 학생 할인 적용
        if (studentStrategy.isApplicable(studentCustomer, now)) {
            double finalPrice = studentStrategy.applyDiscount(originalPrice);
            System.out.printf("학생 할인 적용: $%.2f -> $%.2f\n", originalPrice, finalPrice);
        }
    }
}
```

## Spring에서의 활용
스프링에서는 전략 패턴을 다양한 방법으로 활용할 수 있습니다:

```java
// 1. @Qualifier를 이용한 전략 선택
@Component("creditCardPayment")
public class CreditCardPaymentStrategy implements PaymentStrategy {
    @Override
    public void processPayment(double amount) {
        System.out.println("신용카드 결제 처리: " + amount);
    }
}

@Service
public class PaymentService {
    
    @Autowired
    @Qualifier("creditCardPayment")
    private PaymentStrategy creditCardStrategy;
    
    public void processPayment(String paymentType, double amount) {
        PaymentStrategy strategy = selectStrategy(paymentType);
        strategy.processPayment(amount);
    }
    
    private PaymentStrategy selectStrategy(String type) {
        return switch (type) {
            case "CREDIT_CARD" -> creditCardStrategy;
            default -> throw new IllegalArgumentException("지원하지 않는 결제 방식");
        };
    }
}

// 2. Map을 이용한 전략 관리
@Service
public class NotificationService {
    
    private final Map<String, NotificationStrategy> strategies;
    
    public NotificationService(List<NotificationStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(
                NotificationStrategy::getType,
                Function.identity()
            ));
    }
    
    public void sendNotification(String type, String message) {
        NotificationStrategy strategy = strategies.get(type);
        if (strategy != null) {
            strategy.send(message);
        }
    }
}
```

전략 패턴은 스프링의 의존성 주입과 함께 사용하면 더욱 강력하고 유연한 시스템을 구축할 수 있습니다.
