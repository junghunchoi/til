# 옵저버 패턴 (Observer Pattern)

## 개념
옵저버 패턴은 객체의 상태 변화를 관찰하는 관찰자들(Observers)의 목록을 객체에 등록하여, 상태 변화가 있을 때마다 메서드 등을 통해 객체가 직접 목록의 각 관찰자에게 통지하도록 하는 행동 패턴입니다.

## 언제 사용하면 효과적인가?
- 한 객체의 상태 변경이 다른 여러 객체에 영향을 주어야 할 때
- 객체 간의 결합도를 낮추면서 동적인 관계를 유지하고 싶을 때
- 이벤트 처리 시스템을 구축할 때
- MVC 패턴에서 Model의 변경을 View에 알릴 때
- 실시간 알림 시스템을 구현할 때

## 장점
- **느슨한 결합**: Subject와 Observer 간의 결합도가 낮음
- **동적 관계**: 런타임에 Observer를 추가/제거할 수 있음
- **확장성**: 새로운 Observer를 쉽게 추가할 수 있음
- **재사용성**: Observer를 다른 Subject에서도 재사용 가능

## 주의해야 할 점
- Observer가 많을 경우 성능 저하 가능
- 순환 참조 위험성
- Observer의 업데이트 순서를 보장하기 어려움
- 메모리 누수 가능성 (Observer 해제를 잊을 경우)

## 자바 예시 코드

### 1. 기본 옵저버 패턴 구현
```java
import java.util.*;

// Observer 인터페이스
interface Observer {
    void update(String message);
}

// Subject 인터페이스
interface Subject {
    void attach(Observer observer);
    void detach(Observer observer);
    void notifyObservers();
}

// 구체적인 Subject - 뉴스 피드
class NewsFeed implements Subject {
    private List<Observer> observers;
    private String latestNews;
    
    public NewsFeed() {
        this.observers = new ArrayList<>();
    }
    
    @Override
    public void attach(Observer observer) {
        observers.add(observer);
        System.out.println("새로운 구독자가 추가되었습니다.");
    }
    
    @Override
    public void detach(Observer observer) {
        observers.remove(observer);
        System.out.println("구독자가 제거되었습니다.");
    }
    
    @Override
    public void notifyObservers() {
        System.out.println("모든 구독자에게 뉴스를 전송합니다...");
        for (Observer observer : observers) {
            observer.update(latestNews);
        }
    }
    
    public void setNews(String news) {
        this.latestNews = news;
        System.out.println("새로운 뉴스: " + news);
        notifyObservers();
    }
    
    public int getObserverCount() {
        return observers.size();
    }
}

// 구체적인 Observer - 뉴스 구독자
class NewsSubscriber implements Observer {
    private String name;
    
    public NewsSubscriber(String name) {
        this.name = name;
    }
    
    @Override
    public void update(String message) {
        System.out.println(name + "님이 뉴스를 받았습니다: " + message);
    }
    
    public String getName() {
        return name;
    }
}

// 클라이언트 코드
public class ObserverPatternExample {
    public static void main(String[] args) {
        // 뉴스 피드 생성
        NewsFeed newsFeed = new NewsFeed();
        
        // 다양한 구독자들 생성
        NewsSubscriber subscriber1 = new NewsSubscriber("김철수");
        NewsSubscriber subscriber2 = new NewsSubscriber("이영희");
        
        // 구독자들을 뉴스 피드에 등록
        newsFeed.attach(subscriber1);
        newsFeed.attach(subscriber2);
        
        System.out.println("현재 구독자 수: " + newsFeed.getObserverCount());
        
        // 첫 번째 뉴스 발행
        System.out.println("\n=== 첫 번째 뉴스 발행 ===");
        newsFeed.setNews("스프링 부트 3.0 정식 출시!");
        
        // 구독자 제거
        System.out.println("\n=== 구독자 제거 ===");
        newsFeed.detach(subscriber2);
        
        // 두 번째 뉴스 발행
        System.out.println("\n=== 두 번째 뉴스 발행 ===");
        newsFeed.setNews("자바 21 LTS 버전 발표!");
    }
}
```

## Spring에서의 활용
스프링에서는 이벤트 기반 프로그래밍을 위해 옵저버 패턴을 광범위하게 사용합니다:

```java
// Spring Events 사용 예시
@Component
public class OrderService {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public void createOrder(Order order) {
        // 주문 처리 로직
        orderRepository.save(order);
        
        // 이벤트 발행
        eventPublisher.publishEvent(new OrderCreatedEvent(order));
    }
}

@EventListener
@Component
public class EmailService {
    
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 주문 완료 이메일 발송
        sendOrderConfirmationEmail(event.getOrder());
    }
}

@EventListener
@Async
@Component  
public class InventoryService {
    
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 재고 업데이트
        updateInventory(event.getOrder());
    }
}
```

옵저버 패턴은 마이크로서비스 아키텍처에서 서비스 간 느슨한 결합을 유지하면서 이벤트 기반 통신을 구현하는 데 매우 유용합니다.
