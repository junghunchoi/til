# 어댑터 패턴 (Adapter Pattern)

## 개념
어댑터 패턴은 호환되지 않는 인터페이스를 가진 클래스들이 함께 동작할 수 있도록 중간에서 인터페이스를 변환해주는 구조 패턴입니다. 기존 클래스의 인터페이스를 다른 인터페이스로 변환하여 클라이언트가 기대하는 형태로 사용할 수 있게 해줍니다.

## 언제 사용하면 효과적인가?
- 기존 클래스를 수정하지 않고 새로운 인터페이스와 호환되도록 하고 싶을 때
- 서드파티 라이브러리나 레거시 코드를 새로운 시스템에 통합할 때
- 인터페이스가 다른 여러 클래스를 동일한 방식으로 사용하고 싶을 때
- 외부 API나 데이터 소스의 인터페이스를 내부 시스템에 맞게 변환할 때

## 장점
- **코드 재사용성**: 기존 코드를 수정하지 않고 재사용 가능
- **관심사의 분리**: 인터페이스 변환 로직을 별도로 분리
- **개방-폐쇄 원칙**: 기존 코드는 수정하지 않고 새로운 기능 추가
- **유연성**: 다양한 외부 시스템과의 통합이 용이

## 주의해야 할 점
- 코드 복잡성 증가 (추가적인 래퍼 클래스 필요)
- 런타임 오버헤드 발생 가능
- 어댑터가 너무 많아지면 시스템이 복잡해질 수 있음

## 자바 예시 코드

### 1. Object Adapter Pattern (구성 방식)
```java
import java.util.*;

// 클라이언트가 기대하는 인터페이스
interface MediaPlayer {
    void play(String audioType, String fileName);
}

// 기존의 호환되지 않는 인터페이스들
interface AdvancedMediaPlayer {
    void playVlc(String fileName);
    void playMp4(String fileName);
}

// 기존 VLC 플레이어 (수정할 수 없는 외부 라이브러리)
class VlcPlayer implements AdvancedMediaPlayer {
    @Override
    public void playVlc(String fileName) {
        System.out.println("VLC로 " + fileName + " 재생 중");
    }
    
    @Override
    public void playMp4(String fileName) {
        // VLC는 mp4를 지원하지 않음
    }
}

// 기존 MP4 플레이어 (수정할 수 없는 외부 라이브러리)
class Mp4Player implements AdvancedMediaPlayer {
    @Override
    public void playVlc(String fileName) {
        // Mp4Player는 vlc를 지원하지 않음
    }
    
    @Override
    public void playMp4(String fileName) {
        System.out.println("MP4 플레이어로 " + fileName + " 재생 중");
    }
}

// 어댑터 클래스
class MediaAdapter implements MediaPlayer {
    private AdvancedMediaPlayer advancedMusicPlayer;
    
    public MediaAdapter(String audioType) {
        switch (audioType.toLowerCase()) {
            case "vlc":
                advancedMusicPlayer = new VlcPlayer();
                break;
            case "mp4":
                advancedMusicPlayer = new Mp4Player();
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 오디오 타입: " + audioType);
        }
    }
    
    @Override
    public void play(String audioType, String fileName) {
        switch (audioType.toLowerCase()) {
            case "vlc":
                advancedMusicPlayer.playVlc(fileName);
                break;
            case "mp4":
                advancedMusicPlayer.playMp4(fileName);
                break;
        }
    }
}

// 기본 오디오 플레이어
class AudioPlayer implements MediaPlayer {
    private MediaAdapter mediaAdapter;
    
    @Override
    public void play(String audioType, String fileName) {
        // 기본 지원 포맷
        if ("mp3".equalsIgnoreCase(audioType)) {
            System.out.println("기본 MP3 플레이어로 " + fileName + " 재생 중");
        }
        // 어댑터를 통한 지원 포맷
        else if ("vlc".equalsIgnoreCase(audioType) || "mp4".equalsIgnoreCase(audioType)) {
            mediaAdapter = new MediaAdapter(audioType);
            mediaAdapter.play(audioType, fileName);
        } else {
            System.out.println("지원하지 않는 미디어 포맷: " + audioType);
        }
    }
}

// 클라이언트 코드
public class ObjectAdapterExample {
    public static void main(String[] args) {
        AudioPlayer audioPlayer = new AudioPlayer();
        
        audioPlayer.play("mp3", "song.mp3");
        audioPlayer.play("vlc", "movie.vlc");
        audioPlayer.play("mp4", "video.mp4");
        audioPlayer.play("avi", "unsupported.avi");
    }
}
```

### 2. 외부 API 통합 예시
```java
// 외부 결제 API들의 서로 다른 인터페이스들
class PayPalAPI {
    public void makePayment(String email, double amount) {
        System.out.println("PayPal로 " + email + "에게 $" + amount + " 결제");
    }
}

class StripeAPI {
    public void chargeCard(String cardToken, int cents) {
        System.out.println("Stripe로 카드토큰 " + cardToken + "에 " + cents + "센트 청구");
    }
}

class KakaoPay {
    public void 결제하기(String 사용자ID, int 원화금액) {
        System.out.println("카카오페이로 " + 사용자ID + "에게 " + 원화금액 + "원 결제");
    }
}

// 우리 시스템이 기대하는 통합 결제 인터페이스
interface PaymentProcessor {
    void processPayment(String userId, double amount, String currency);
    String getPaymentMethod();
}

// PayPal 어댑터
class PayPalAdapter implements PaymentProcessor {
    private PayPalAPI paypalAPI;
    
    public PayPalAdapter(PayPalAPI paypalAPI) {
        this.paypalAPI = paypalAPI;
    }
    
    @Override
    public void processPayment(String userId, double amount, String currency) {
        if (!"USD".equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("PayPal 어댑터는 USD만 지원합니다.");
        }
        paypalAPI.makePayment(userId, amount);
    }
    
    @Override
    public String getPaymentMethod() {
        return "PayPal";
    }
}

// Stripe 어댑터
class StripeAdapter implements PaymentProcessor {
    private StripeAPI stripeAPI;
    
    public StripeAdapter(StripeAPI stripeAPI) {
        this.stripeAPI = stripeAPI;
    }
    
    @Override
    public void processPayment(String userId, double amount, String currency) {
        if (!"USD".equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("Stripe 어댑터는 USD만 지원합니다.");
        }
        int cents = (int) (amount * 100); // 달러를 센트로 변환
        stripeAPI.chargeCard(userId, cents);
    }
    
    @Override
    public String getPaymentMethod() {
        return "Stripe";
    }
}

// KakaoPay 어댑터
class KakaoPayAdapter implements PaymentProcessor {
    private KakaoPay kakaoPay;
    private static final double USD_TO_KRW_RATE = 1300.0; // 환율
    
    public KakaoPayAdapter(KakaoPay kakaoPay) {
        this.kakaoPay = kakaoPay;
    }
    
    @Override
    public void processPayment(String userId, double amount, String currency) {
        int 원화금액;
        
        if ("USD".equalsIgnoreCase(currency)) {
            원화금액 = (int) (amount * USD_TO_KRW_RATE);
        } else if ("KRW".equalsIgnoreCase(currency)) {
            원화금액 = (int) amount;
        } else {
            throw new IllegalArgumentException("KakaoPay 어댑터는 USD와 KRW만 지원합니다.");
        }
        
        kakaoPay.결제하기(userId, 원화금액);
    }
    
    @Override
    public String getPaymentMethod() {
        return "KakaoPay";
    }
}

// 통합 결제 서비스
class PaymentService {
    private List<PaymentProcessor> paymentProcessors;
    
    public PaymentService() {
        this.paymentProcessors = new ArrayList<>();
        initializePaymentProcessors();
    }
    
    private void initializePaymentProcessors() {
        // 다양한 결제 시스템들을 어댑터를 통해 통합
        paymentProcessors.add(new PayPalAdapter(new PayPalAPI()));
        paymentProcessors.add(new StripeAdapter(new StripeAPI()));
        paymentProcessors.add(new KakaoPayAdapter(new KakaoPay()));
    }
    
    public void processPayment(String userId, double amount, String currency, String preferredMethod) {
        PaymentProcessor processor = paymentProcessors.stream()
                .filter(p -> p.getPaymentMethod().equalsIgnoreCase(preferredMethod))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 결제 방법: " + preferredMethod));
        
        try {
            processor.processPayment(userId, amount, currency);
            System.out.println("결제가 성공적으로 처리되었습니다.");
        } catch (Exception e) {
            System.out.println("결제 처리 실패: " + e.getMessage());
        }
    }
    
    public void listAvailablePaymentMethods() {
        System.out.println("사용 가능한 결제 방법:");
        paymentProcessors.forEach(p -> System.out.println("- " + p.getPaymentMethod()));
    }
}

// 클라이언트 코드
public class PaymentAdapterExample {
    public static void main(String[] args) {
        PaymentService paymentService = new PaymentService();
        
        paymentService.listAvailablePaymentMethods();
        
        System.out.println("\n=== 다양한 결제 방법으로 결제 테스트 ===");
        
        // PayPal로 결제
        paymentService.processPayment("user@example.com", 100.0, "USD", "PayPal");
        
        // Stripe로 결제
        paymentService.processPayment("card_token_123", 50.0, "USD", "Stripe");
        
        // KakaoPay로 USD 결제 (자동 환율 변환)
        paymentService.processPayment("kakao_user_456", 30.0, "USD", "KakaoPay");
        
        // KakaoPay로 KRW 결제
        paymentService.processPayment("kakao_user_789", 50000.0, "KRW", "KakaoPay");
    }
}
```

## Spring에서의 활용
스프링에서는 다양한 곳에서 어댑터 패턴이 활용됩니다:

```java
// 1. HandlerAdapter - 다양한 타입의 핸들러를 통합
@Component
public class CustomControllerAdapter implements HandlerAdapter {
    
    @Override
    public boolean supports(Object handler) {
        return handler instanceof CustomController;
    }
    
    @Override
    public ModelAndView handle(HttpServletRequest request, 
                             HttpServletResponse response, 
                             Object handler) throws Exception {
        CustomController controller = (CustomController) handler;
        return controller.handleRequest(request, response);
    }
}

// 2. 데이터 소스 어댑터
@Configuration
public class DataSourceConfig {
    
    @Bean
    public DataSource legacyDataSource() {
        // 레거시 데이터베이스 연결을 표준 DataSource로 어댑팅
        return new DataSourceAdapter(legacyDbConnection);
    }
}
```

어댑터 패턴은 특히 마이크로서비스 환경에서 서로 다른 서비스 간의 인터페이스 차이를 해결하는 데 매우 유용합니다.
