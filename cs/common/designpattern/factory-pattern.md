# 팩토리 패턴 (Factory Pattern)

## 개념
팩토리 패턴은 객체 생성을 전담하는 별도의 클래스를 두어 객체 생성의 복잡성을 숨기고, 생성되는 객체의 타입을 결정하는 로직을 캡슐화하는 생성 패턴입니다.

## 언제 사용하면 효과적인가?
- 객체 생성 로직이 복잡하거나 조건에 따라 다른 타입의 객체를 생성해야 할 때
- 클라이언트 코드에서 구체적인 클래스에 의존하지 않고 객체를 생성하고 싶을 때
- 런타임에 어떤 클래스의 인스턴스를 만들지 결정해야 할 때
- 객체 생성과 관련된 설정이나 초기화 작업이 복잡할 때

## 장점
- **객체 생성 로직의 캡슐화**: 객체 생성의 복잡성을 팩토리 클래스에서 처리
- **결합도 감소**: 클라이언트 코드가 구체적인 클래스에 직접 의존하지 않음
- **확장성**: 새로운 타입의 객체를 추가할 때 팩토리만 수정하면 됨
- **코드 재사용성**: 동일한 객체 생성 로직을 여러 곳에서 재사용 가능

## 주의해야 할 점
- 팩토리 클래스가 복잡해질 수 있음 (God Object 위험)
- 단순한 객체 생성에는 오버엔지니어링이 될 수 있음
- 새로운 제품 타입을 추가할 때마다 팩토리 클래스를 수정해야 함

## 자바 예시 코드

### 1. Simple Factory Pattern
```java
// 제품 인터페이스
interface Vehicle {
    void start();
    void stop();
}

// 구체적인 제품들
class Car implements Vehicle {
    @Override
    public void start() {
        System.out.println("자동차 시동을 걸었습니다.");
    }
    
    @Override
    public void stop() {
        System.out.println("자동차를 정지했습니다.");
    }
}

class Motorcycle implements Vehicle {
    @Override
    public void start() {
        System.out.println("오토바이 시동을 걸었습니다.");
    }
    
    @Override
    public void stop() {
        System.out.println("오토바이를 정지했습니다.");
    }
}

class Truck implements Vehicle {
    @Override
    public void start() {
        System.out.println("트럭 시동을 걸었습니다.");
    }
    
    @Override
    public void stop() {
        System.out.println("트럭을 정지했습니다.");
    }
}

// 팩토리 클래스
class VehicleFactory {
    public static Vehicle createVehicle(String type) {
        return switch (type.toLowerCase()) {
            case "car" -> new Car();
            case "motorcycle" -> new Motorcycle();
            case "truck" -> new Truck();
            default -> throw new IllegalArgumentException("알 수 없는 차량 타입: " + type);
        };
    }
}

// 클라이언트 코드
public class FactoryPatternExample {
    public static void main(String[] args) {
        Vehicle car = VehicleFactory.createVehicle("car");
        car.start();
        car.stop();
        
        Vehicle motorcycle = VehicleFactory.createVehicle("motorcycle");
        motorcycle.start();
        motorcycle.stop();
        
        Vehicle truck = VehicleFactory.createVehicle("truck");
        truck.start();
        truck.stop();
    }
}
```

### 2. Factory Method Pattern
```java
// 제품 인터페이스
interface Logger {
    void log(String message);
}

// 구체적인 제품들
class FileLogger implements Logger {
    @Override
    public void log(String message) {
        System.out.println("파일에 로그 기록: " + message);
    }
}

class DatabaseLogger implements Logger {
    @Override
    public void log(String message) {
        System.out.println("데이터베이스에 로그 기록: " + message);
    }
}

class ConsoleLogger implements Logger {
    @Override
    public void log(String message) {
        System.out.println("콘솔에 로그 기록: " + message);
    }
}

// 추상 팩토리
abstract class LoggerFactory {
    public abstract Logger createLogger();
    
    public void logMessage(String message) {
        Logger logger = createLogger();
        logger.log(message);
    }
}

// 구체적인 팩토리들
class FileLoggerFactory extends LoggerFactory {
    @Override
    public Logger createLogger() {
        return new FileLogger();
    }
}

class DatabaseLoggerFactory extends LoggerFactory {
    @Override
    public Logger createLogger() {
        return new DatabaseLogger();
    }
}

class ConsoleLoggerFactory extends LoggerFactory {
    @Override
    public Logger createLogger() {
        return new ConsoleLogger();
    }
}

// 클라이언트 코드
public class FactoryMethodExample {
    public static void main(String[] args) {
        LoggerFactory fileLoggerFactory = new FileLoggerFactory();
        fileLoggerFactory.logMessage("파일 로그 테스트");
        
        LoggerFactory dbLoggerFactory = new DatabaseLoggerFactory();
        dbLoggerFactory.logMessage("데이터베이스 로그 테스트");
        
        LoggerFactory consoleLoggerFactory = new ConsoleLoggerFactory();
        consoleLoggerFactory.logMessage("콘솔 로그 테스트");
    }
}
```

## Spring에서의 활용
스프링에서는 `BeanFactory`와 `ApplicationContext`가 팩토리 패턴을 구현한 대표적인 예입니다. 설정에 따라 다양한 빈 객체를 생성하고 관리합니다.

```java
@Component
public class PaymentServiceFactory {
    
    public PaymentService createPaymentService(String paymentType) {
        return switch (paymentType) {
            case "CARD" -> new CardPaymentService();
            case "BANK" -> new BankTransferService();
            case "MOBILE" -> new MobilePaymentService();
            default -> throw new IllegalArgumentException("지원하지 않는 결제 방식: " + paymentType);
        };
    }
}
```
