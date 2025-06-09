# 프록시 패턴 (Proxy Pattern)

## 개념
프록시 패턴은 다른 객체에 대한 접근을 제어하기 위해 대리자(Proxy) 역할을 하는 객체를 제공하는 구조 패턴입니다. 실제 객체에 대한 접근을 제어하면서 추가적인 기능을 제공할 수 있습니다.

## 언제 사용하면 효과적인가?
- 비용이 많이 드는 객체의 생성을 지연시키고 싶을 때 (Lazy Loading)
- 원격 객체에 대한 로컬 대리자가 필요할 때 (Remote Proxy)
- 객체에 대한 접근 권한을 제어하고 싶을 때 (Protection Proxy)
- 객체 접근 시 추가적인 작업(로깅, 캐싱 등)을 수행하고 싶을 때 (Smart Proxy)
- 메모리 사용량을 최적화하고 싶을 때 (Virtual Proxy)

## 장점
- **접근 제어**: 실제 객체에 대한 접근을 세밀하게 제어 가능
- **지연 로딩**: 실제로 필요할 때까지 객체 생성을 미룰 수 있음
- **추가 기능**: 실제 객체의 기능을 변경하지 않고 부가 기능 추가 가능
- **투명성**: 클라이언트는 프록시와 실제 객체를 구분하지 않고 사용 가능

## 주의해야 할 점
- 코드 복잡성이 증가할 수 있음
- 프록시를 통한 간접 접근으로 인한 성능 오버헤드
- 프록시 객체가 실제 객체와 동일한 인터페이스를 구현해야 함

## 자바 예시 코드

### 1. Virtual Proxy (지연 로딩)
```java
// 공통 인터페이스
interface Image {
    void display();
    String getInfo();
}

// 실제 객체 (무거운 리소스)
class RealImage implements Image {
    private String filename;
    
    public RealImage(String filename) {
        this.filename = filename;
        loadFromDisk(); // 비용이 많이 드는 작업
    }
    
    private void loadFromDisk() {
        System.out.println("디스크에서 " + filename + " 로딩 중...");
        // 실제로는 시간이 오래 걸리는 작업
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(filename + " 로딩 완료");
    }
    
    @Override
    public void display() {
        System.out.println("이미지 " + filename + " 표시 중");
    }
    
    @Override
    public String getInfo() {
        return "Real Image: " + filename;
    }
}

// 프록시 객체
class ImageProxy implements Image {
    private String filename;
    private RealImage realImage;
    
    public ImageProxy(String filename) {
        this.filename = filename;
    }
    
    @Override
    public void display() {
        if (realImage == null) {
            realImage = new RealImage(filename); // 지연 로딩
        }
        realImage.display();
    }
    
    @Override
    public String getInfo() {
        if (realImage == null) {
            return "Proxy Image: " + filename + " (not loaded)";
        }
        return realImage.getInfo();
    }
}

// 클라이언트 코드
public class VirtualProxyExample {
    public static void main(String[] args) {
        System.out.println("프록시 이미지 생성 (실제 로딩 X)");
        Image image1 = new ImageProxy("고해상도_이미지1.jpg");
        Image image2 = new ImageProxy("고해상도_이미지2.jpg");
        
        System.out.println("\n이미지 정보 확인:");
        System.out.println(image1.getInfo());
        System.out.println(image2.getInfo());
        
        System.out.println("\n첫 번째 이미지 표시 (실제 로딩 발생):");
        image1.display();
        
        System.out.println("\n두 번째 이미지 표시 (실제 로딩 발생):");
        image2.display();
        
        System.out.println("\n첫 번째 이미지 다시 표시 (로딩 X):");
        image1.display();
    }
}
```

### 2. Protection Proxy (접근 제어)
```java
// 공통 인터페이스
interface BankAccount {
    void deposit(double amount);
    void withdraw(double amount);
    double getBalance();
}

// 실제 은행 계좌
class RealBankAccount implements BankAccount {
    private double balance;
    private String accountNumber;
    
    public RealBankAccount(String accountNumber, double initialBalance) {
        this.accountNumber = accountNumber;
        this.balance = initialBalance;
    }
    
    @Override
    public void deposit(double amount) {
        balance += amount;
        System.out.println(amount + "원 입금됨. 잔액: " + balance + "원");
    }
    
    @Override
    public void withdraw(double amount) {
        if (balance >= amount) {
            balance -= amount;
            System.out.println(amount + "원 출금됨. 잔액: " + balance + "원");
        } else {
            System.out.println("잔액이 부족합니다. 현재 잔액: " + balance + "원");
        }
    }
    
    @Override
    public double getBalance() {
        return balance;
    }
}

// 사용자 정보
class User {
    private String username;
    private String role; // "OWNER", "VIEWER", "ADMIN"
    
    public User(String username, String role) {
        this.username = username;
        this.role = role;
    }
    
    public String getUsername() { return username; }
    public String getRole() { return role; }
}

// 접근 제어 프록시
class BankAccountProxy implements BankAccount {
    private RealBankAccount realAccount;
    private User currentUser;
    
    public BankAccountProxy(RealBankAccount realAccount, User currentUser) {
        this.realAccount = realAccount;
        this.currentUser = currentUser;
    }
    
    @Override
    public void deposit(double amount) {
        if (hasDepositPermission()) {
            realAccount.deposit(amount);
        } else {
            System.out.println("접근 거부: " + currentUser.getUsername() + "님은 입금 권한이 없습니다.");
        }
    }
    
    @Override
    public void withdraw(double amount) {
        if (hasWithdrawPermission()) {
            realAccount.withdraw(amount);
        } else {
            System.out.println("접근 거부: " + currentUser.getUsername() + "님은 출금 권한이 없습니다.");
        }
    }
    
    @Override
    public double getBalance() {
        if (hasViewPermission()) {
            return realAccount.getBalance();
        } else {
            System.out.println("접근 거부: " + currentUser.getUsername() + "님은 잔액 조회 권한이 없습니다.");
            return -1;
        }
    }
    
    private boolean hasDepositPermission() {
        return "OWNER".equals(currentUser.getRole()) || "ADMIN".equals(currentUser.getRole());
    }
    
    private boolean hasWithdrawPermission() {
        return "OWNER".equals(currentUser.getRole()) || "ADMIN".equals(currentUser.getRole());
    }
    
    private boolean hasViewPermission() {
        return true; // 모든 사용자가 조회 가능
    }
}

// 클라이언트 코드
public class ProtectionProxyExample {
    public static void main(String[] args) {
        RealBankAccount account = new RealBankAccount("123-456-789", 100000);
        
        User owner = new User("김철수", "OWNER");
        User viewer = new User("이영희", "VIEWER");
        User admin = new User("관리자", "ADMIN");
        
        System.out.println("=== 계좌 소유자 접근 ===");
        BankAccount ownerProxy = new BankAccountProxy(account, owner);
        ownerProxy.deposit(50000);
        ownerProxy.withdraw(30000);
        System.out.println("잔액: " + ownerProxy.getBalance() + "원");
        
        System.out.println("\n=== 조회자 접근 ===");
        BankAccount viewerProxy = new BankAccountProxy(account, viewer);
        viewerProxy.deposit(10000); // 권한 없음
        viewerProxy.withdraw(10000); // 권한 없음
        System.out.println("잔액: " + viewerProxy.getBalance() + "원");
        
        System.out.println("\n=== 관리자 접근 ===");
        BankAccount adminProxy = new BankAccountProxy(account, admin);
        adminProxy.deposit(20000);
        adminProxy.withdraw(10000);
        System.out.println("잔액: " + adminProxy.getBalance() + "원");
    }
}
```

### 3. Caching Proxy (캐싱)
```java
import java.util.HashMap;
import java.util.Map;

// 데이터 서비스 인터페이스
interface DataService {
    String getData(String key);
}

// 실제 데이터베이스 서비스
class DatabaseService implements DataService {
    @Override
    public String getData(String key) {
        System.out.println("데이터베이스에서 " + key + " 조회 중...");
        // 시뮬레이션: 데이터베이스 접근 시간
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Database data for " + key;
    }
}

// 캐싱 프록시
class CachingDataServiceProxy implements DataService {
    private DatabaseService databaseService;
    private Map<String, String> cache;
    
    public CachingDataServiceProxy() {
        this.databaseService = new DatabaseService();
        this.cache = new HashMap<>();
    }
    
    @Override
    public String getData(String key) {
        if (cache.containsKey(key)) {
            System.out.println("캐시에서 " + key + " 조회");
            return cache.get(key);
        }
        
        String data = databaseService.getData(key);
        cache.put(key, data);
        System.out.println("데이터를 캐시에 저장: " + key);
        return data;
    }
    
    public void clearCache() {
        cache.clear();
        System.out.println("캐시 클리어됨");
    }
}

// 클라이언트 코드
public class CachingProxyExample {
    public static void main(String[] args) {
        DataService dataService = new CachingDataServiceProxy();
        
        System.out.println("첫 번째 조회:");
        System.out.println(dataService.getData("user123"));
        
        System.out.println("\n두 번째 조회 (캐시 히트):");
        System.out.println(dataService.getData("user123"));
        
        System.out.println("\n다른 데이터 조회:");
        System.out.println(dataService.getData("user456"));
        
        System.out.println("\n캐시된 데이터 다시 조회:");
        System.out.println(dataService.getData("user456"));
    }
}
```

## Spring에서의 활용
스프링에서는 AOP(Aspect-Oriented Programming)를 구현하기 위해 프록시 패턴을 광범위하게 사용합니다.

```java
@Service
@Transactional
public class UserService {
    
    @Cacheable("users")
    public User findUserById(Long id) {
        // 실제 비즈니스 로직
        return userRepository.findById(id);
    }
    
    @LogExecutionTime
    public void processUser(User user) {
        // 처리 로직
    }
}
```

스프링은 `@Transactional`, `@Cacheable`, `@Async` 등의 어노테이션이 적용된 빈에 대해 프록시 객체를 생성하여 부가 기능을 투명하게 제공합니다.
