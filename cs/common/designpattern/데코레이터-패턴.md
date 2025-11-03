# 데코레이터 패턴 (Decorator Pattern)

## 개념
데코레이터 패턴은 기존 객체에 새로운 기능을 동적으로 추가할 수 있는 구조 패턴입니다. 상속을 사용하지 않고도 객체의 행위를 확장할 수 있으며, 여러 데코레이터를 조합하여 다양한 기능 조합을 만들 수 있습니다.

## 언제 사용하면 효과적인가?
- 객체에 새로운 기능을 동적으로 추가하고 싶을 때
- 상속보다 조합을 선호할 때
- 기존 코드를 수정하지 않고 기능을 확장하고 싶을 때
- 여러 기능을 자유롭게 조합하고 싶을 때
- 런타임에 객체의 행위를 변경하고 싶을 때

## 장점
- **유연성**: 런타임에 객체의 행위를 동적으로 변경 가능
- **조합성**: 여러 데코레이터를 조합하여 다양한 기능 조합 가능
- **단일 책임 원칙**: 각 데코레이터는 하나의 기능만 담당
- **개방-폐쇄 원칙**: 기존 코드 수정 없이 새로운 기능 추가 가능

## 주의해야 할 점
- 데코레이터가 많아질 경우 복잡성 증가
- 디버깅이 어려울 수 있음 (래퍼 체인)
- 작은 객체들이 많이 생성될 수 있음
- 특정 타입에 의존하는 코드는 동작하지 않을 수 있음

## 자바 예시 코드

### 1. 커피 주문 시스템 예시
```java
// 기본 컴포넌트 인터페이스
interface Coffee {
    String getDescription();
    double getCost();
}

// 기본 구현체
class Espresso implements Coffee {
    @Override
    public String getDescription() {
        return "에스프레소";
    }
    
    @Override
    public double getCost() {
        return 2.00;
    }
}

class Americano implements Coffee {
    @Override
    public String getDescription() {
        return "아메리카노";
    }
    
    @Override
    public double getCost() {
        return 2.50;
    }
}

// 추상 데코레이터 클래스
abstract class CoffeeDecorator implements Coffee {
    protected Coffee coffee;
    
    public CoffeeDecorator(Coffee coffee) {
        this.coffee = coffee;
    }
    
    @Override
    public String getDescription() {
        return coffee.getDescription();
    }
    
    @Override
    public double getCost() {
        return coffee.getCost();
    }
}

// 구체적인 데코레이터들
class MilkDecorator extends CoffeeDecorator {
    public MilkDecorator(Coffee coffee) {
        super(coffee);
    }
    
    @Override
    public String getDescription() {
        return coffee.getDescription() + " + 우유";
    }
    
    @Override
    public double getCost() {
        return coffee.getCost() + 0.60;
    }
}

class SugarDecorator extends CoffeeDecorator {
    public SugarDecorator(Coffee coffee) {
        super(coffee);
    }
    
    @Override
    public String getDescription() {
        return coffee.getDescription() + " + 설탕";
    }
    
    @Override
    public double getCost() {
        return coffee.getCost() + 0.20;
    }
}

class WhipDecorator extends CoffeeDecorator {
    public WhipDecorator(Coffee coffee) {
        super(coffee);
    }
    
    @Override
    public String getDescription() {
        return coffee.getDescription() + " + 휘핑크림";
    }
    
    @Override
    public double getCost() {
        return coffee.getCost() + 0.80;
    }
}

// 클라이언트 코드
public class CoffeeDecoratorExample {
    public static void main(String[] args) {
        // 1. 기본 에스프레소
        Coffee order1 = new Espresso();
        System.out.printf("주문: %s, 가격: $%.2f\n", order1.getDescription(), order1.getCost());
        
        // 2. 우유가 들어간 아메리카노
        Coffee order2 = new MilkDecorator(new Americano());
        System.out.printf("주문: %s, 가격: $%.2f\n", order2.getDescription(), order2.getCost());
        
        // 3. 복잡한 조합: 아메리카노 + 우유 + 설탕 + 휘핑크림
        Coffee order3 = new WhipDecorator(
                           new SugarDecorator(
                               new MilkDecorator(
                                   new Americano())));
        System.out.printf("주문: %s, 가격: $%.2f\n", order3.getDescription(), order3.getCost());
    }
}
```

### 2. 텍스트 처리 시스템 예시
```java
// 기본 텍스트 인터페이스
interface TextProcessor {
    String process(String text);
}

// 기본 텍스트 처리기
class PlainTextProcessor implements TextProcessor {
    @Override
    public String process(String text) {
        return text;
    }
}

// 추상 데코레이터
abstract class TextDecorator implements TextProcessor {
    protected TextProcessor processor;
    
    public TextDecorator(TextProcessor processor) {
        this.processor = processor;
    }
    
    @Override
    public String process(String text) {
        return processor.process(text);
    }
}

// 구체적인 텍스트 데코레이터들
class UpperCaseDecorator extends TextDecorator {
    public UpperCaseDecorator(TextProcessor processor) {
        super(processor);
    }
    
    @Override
    public String process(String text) {
        return processor.process(text).toUpperCase();
    }
}

class TrimDecorator extends TextDecorator {
    public TrimDecorator(TextProcessor processor) {
        super(processor);
    }
    
    @Override
    public String process(String text) {
        return processor.process(text).trim();
    }
}

class PrefixDecorator extends TextDecorator {
    private String prefix;
    
    public PrefixDecorator(TextProcessor processor, String prefix) {
        super(processor);
        this.prefix = prefix;
    }
    
    @Override
    public String process(String text) {
        return prefix + processor.process(text);
    }
}

// 클라이언트 코드
public class TextDecoratorExample {
    public static void main(String[] args) {
        String originalText = "  Hello, World!  ";
        System.out.println("원본 텍스트: \"" + originalText + "\"");
        
        // 1. 기본 처리
        TextProcessor basic = new PlainTextProcessor();
        System.out.println("기본 처리: \"" + basic.process(originalText) + "\"");
        
        // 2. 공백 제거 + 대문자 변환
        TextProcessor processed = new UpperCaseDecorator(
                                     new TrimDecorator(
                                         new PlainTextProcessor()));
        System.out.println("공백제거+대문자: \"" + processed.process(originalText) + "\"");
        
        // 3. 복잡한 조합: 공백제거 + 접두사 추가
        TextProcessor complex = new PrefixDecorator(
                                   new TrimDecorator(
                                       new PlainTextProcessor()), "[처리됨] ");
        System.out.println("복잡한 조합: \"" + complex.process(originalText) + "\"");
    }
}
```

### 3. 로깅 시스템 예시
```java
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 기본 로거 인터페이스
interface Logger {
    void log(String message);
}

// 기본 콘솔 로거
class ConsoleLogger implements Logger {
    @Override
    public void log(String message) {
        System.out.println(message);
    }
}

// 추상 로거 데코레이터
abstract class LoggerDecorator implements Logger {
    protected Logger logger;
    
    public LoggerDecorator(Logger logger) {
        this.logger = logger;
    }
    
    @Override
    public void log(String message) {
        logger.log(message);
    }
}

// 타임스탬프 데코레이터
class TimestampDecorator extends LoggerDecorator {
    public TimestampDecorator(Logger logger) {
        super(logger);
    }
    
    @Override
    public void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logger.log("[" + timestamp + "] " + message);
    }
}

// 로그 레벨 데코레이터
class LogLevelDecorator extends LoggerDecorator {
    private String level;
    
    public LogLevelDecorator(Logger logger, String level) {
        super(logger);
        this.level = level;
    }
    
    @Override
    public void log(String message) {
        logger.log("[" + level + "] " + message);
    }
}

// 클라이언트 코드
public class LoggerDecoratorExample {
    public static void main(String[] args) {
        // 1. 기본 콘솔 로거
        Logger basicLogger = new ConsoleLogger();
        System.out.println("1. 기본 로거:");
        basicLogger.log("기본 로그 메시지");
        
        // 2. 타임스탬프가 추가된 로거
        Logger timestampLogger = new TimestampDecorator(new ConsoleLogger());
        System.out.println("\n2. 타임스탬프 로거:");
        timestampLogger.log("타임스탬프가 포함된 메시지");
        
        // 3. 타임스탬프 + 로그 레벨
        Logger levelLogger = new LogLevelDecorator(
                                new TimestampDecorator(
                                    new ConsoleLogger()), "ERROR");
        System.out.println("\n3. 타임스탬프 + 로그 레벨:");
        levelLogger.log("에러 메시지입니다");
    }
}
```

## Spring에서의 활용
스프링에서는 데코레이터 패턴이 다양한 곳에서 활용됩니다:

```java
// 1. AOP를 통한 데코레이터 패턴
@Component
public class UserService {
    
    @Transactional
    @Cacheable("users")
    @LogExecutionTime
    public User findById(Long id) {
        // 실제 비즈니스 로직
        return userRepository.findById(id);
    }
}

// 2. 서블릿 필터 체인 (데코레이터 패턴의 변형)
@Component
public class RequestLoggingFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        // 요청 전 처리
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        System.out.println("요청: " + httpRequest.getRequestURI());
        
        // 다음 필터로 전달
        chain.doFilter(request, response);
        
        // 응답 후 처리
        System.out.println("응답 완료");
    }
}

// 3. 데코레이터를 이용한 서비스 확장
@Service
public class PaymentServiceDecorator implements PaymentService {
    
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    
    public PaymentServiceDecorator(PaymentService paymentService,
                                 NotificationService notificationService) {
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        try {
            // 실제 결제 처리
            PaymentResult result = paymentService.processPayment(request);
            
            // 성공 시 알림
            notificationService.sendPaymentConfirmation(result);
            
            return result;
        } catch (Exception e) {
            // 실패 시 처리
            notificationService.sendPaymentFailure(request, e);
            throw e;
        }
    }
}
```

데코레이터 패턴은 기존 코드를 수정하지 않고도 새로운 기능을 자유롭게 조합할 수 있게 해주며, 특히 스프링의 AOP와 함께 사용하면 횡단 관심사를 깔끔하게 처리할 수 있습니다.
