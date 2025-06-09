# 템플릿 메소드 패턴 (Template Method Pattern)

## 개념
템플릿 메소드 패턴은 상위 클래스에서 알고리즘의 구조를 정의하고, 하위 클래스에서 알고리즘의 특정 단계들을 구현하도록 하는 행동 패턴입니다. 전체적인 알고리즘의 구조는 유지하면서 특정 단계만 서브클래스에서 재정의할 수 있습니다.

## 언제 사용하면 효과적인가?
- 여러 클래스에서 공통된 알고리즘을 사용하지만 일부 단계만 다를 때
- 알고리즘의 구조는 고정하고 특정 단계만 변경하고 싶을 때
- 코드 중복을 제거하고 공통 로직을 상위 클래스로 추출하고 싶을 때
- 프레임워크에서 확장 포인트를 제공하고 싶을 때
- Hook 메소드를 통해 선택적인 단계를 제공하고 싶을 때

## 장점
- **코드 재사용**: 공통된 알고리즘을 상위 클래스에서 한 번만 정의
- **제어의 역전**: 상위 클래스가 알고리즘의 흐름을 제어
- **확장성**: 새로운 구현체를 쉽게 추가할 수 있음
- **유지보수성**: 알고리즘의 구조 변경 시 상위 클래스만 수정하면 됨

## 주의해야 할 점
- 상속을 기반으로 하므로 결합도가 높을 수 있음
- 알고리즘이 복잡해질수록 템플릿 메소드도 복잡해짐
- 리스코프 치환 원칙을 위반할 위험성
- 너무 많은 추상 메소드가 있으면 구현이 복잡해짐

## 자바 예시 코드

### 1. 데이터 처리 파이프라인 예시
```java
import java.util.*;

// 추상 클래스 - 템플릿 메소드 정의
abstract class DataProcessor {
    
    // 템플릿 메소드 - 알고리즘의 골격을 정의
    public final void processData() {
        System.out.println("=== 데이터 처리 시작 ===");
        
        // 1. 데이터 로드
        List<String> rawData = loadData();
        System.out.println("로드된 데이터: " + rawData);
        
        // 2. 데이터 검증
        if (!validateData(rawData)) {
            System.out.println("❌ 데이터 검증 실패");
            return;
        }
        
        // 3. 데이터 변환
        List<String> transformedData = transformData(rawData);
        System.out.println("변환된 데이터: " + transformedData);
        
        // 4. 데이터 저장
        saveData(transformedData);
        
        // 5. 후처리 (Hook 메소드)
        if (shouldNotify()) {
            sendNotification();
        }
        
        System.out.println("=== 데이터 처리 완료 ===\n");
    }
    
    // 추상 메소드들 - 하위 클래스에서 반드시 구현
    protected abstract List<String> loadData();
    protected abstract List<String> transformData(List<String> data);
    protected abstract void saveData(List<String> data);
    
    // 구체 메소드 - 공통 로직
    protected boolean validateData(List<String> data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        
        for (String item : data) {
            if (item == null || item.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    // Hook 메소드들 - 선택적으로 오버라이드 가능
    protected boolean shouldNotify() {
        return false; // 기본값: 알림 안함
    }
    
    protected void sendNotification() {
        System.out.println("📧 처리 완료 알림 전송");
    }
}

// 구체 클래스 1 - CSV 파일 처리
class CsvDataProcessor extends DataProcessor {
    private String filePath;
    private String outputPath;
    
    public CsvDataProcessor(String filePath, String outputPath) {
        this.filePath = filePath;
        this.outputPath = outputPath;
    }
    
    @Override
    protected List<String> loadData() {
        System.out.println("CSV 파일에서 데이터 로드: " + filePath);
        return Arrays.asList("Name,Age,City", "김철수,30,서울", "이영희,25,부산");
    }
    
    @Override
    protected List<String> transformData(List<String> data) {
        System.out.println("CSV 데이터를 JSON 형태로 변환");
        List<String> transformed = new ArrayList<>();
        
        if (data.size() > 1) {
            String[] headers = data.get(0).split(",");
            
            for (int i = 1; i < data.size(); i++) {
                String[] values = data.get(i).split(",");
                StringBuilder json = new StringBuilder("{");
                
                for (int j = 0; j < headers.length && j < values.length; j++) {
                    if (j > 0) json.append(", ");
                    json.append("\"").append(headers[j]).append("\": \"")
                        .append(values[j]).append("\"");
                }
                json.append("}");
                transformed.add(json.toString());
            }
        }
        
        return transformed;
    }
    
    @Override
    protected void saveData(List<String> data) {
        System.out.println("변환된 데이터를 JSON 파일로 저장: " + outputPath);
        for (String item : data) {
            System.out.println("  " + item);
        }
    }
    
    @Override
    protected boolean shouldNotify() {
        return true; // CSV 처리는 항상 알림
    }
}

// 구체 클래스 2 - 데이터베이스 처리
class DatabaseProcessor extends DataProcessor {
    private String tableName;
    
    public DatabaseProcessor(String tableName) {
        this.tableName = tableName;
    }
    
    @Override
    protected List<String> loadData() {
        System.out.println("데이터베이스 테이블에서 데이터 로드: " + tableName);
        return Arrays.asList("user1:admin:active", "user2:user:inactive", "user3:user:active");
    }
    
    @Override
    protected List<String> transformData(List<String> data) {
        System.out.println("사용자 데이터 정규화 및 필터링");
        List<String> transformed = new ArrayList<>();
        
        for (String item : data) {
            String[] parts = item.split(":");
            if (parts.length == 3 && "active".equals(parts[2])) {
                transformed.add(parts[0].toUpperCase() + " (" + parts[1] + ")");
            }
        }
        
        return transformed;
    }
    
    @Override
    protected void saveData(List<String> data) {
        System.out.println("정규화된 데이터를 캐시에 저장");
        for (String item : data) {
            System.out.println("  캐시 저장: " + item);
        }
    }
}

// 클라이언트 코드
public class TemplateMethodExample {
    public static void main(String[] args) {
        DataProcessor csvProcessor = new CsvDataProcessor("users.csv", "users.json");
        DataProcessor dbProcessor = new DatabaseProcessor("users");
        
        // 동일한 인터페이스로 다양한 데이터 처리
        csvProcessor.processData();
        dbProcessor.processData();
    }
}
```

### 2. 게임 캐릭터 예시
```java
// 추상 게임 캐릭터 클래스
abstract class GameCharacter {
    protected String name;
    protected int health;
    protected int mana;
    
    public GameCharacter(String name) {
        this.name = name;
        this.health = 100;
        this.mana = 50;
    }
    
    // 템플릿 메소드 - 전투 시퀀스
    public final void performBattle(GameCharacter enemy) {
        System.out.println("=== " + name + " vs " + enemy.name + " 전투 시작 ===");
        
        // 1. 전투 준비
        prepareBattle();
        
        // 2. 스킬 사용 가능 확인
        if (canUseSkill()) {
            useSkill(enemy);
        }
        
        // 3. 기본 공격
        performAttack(enemy);
        
        // 4. 방어 행동
        performDefense();
        
        // 5. 전투 후 처리
        afterBattle();
        
        System.out.println("=== 전투 종료 ===\n");
    }
    
    // 추상 메소드들
    protected abstract void useSkill(GameCharacter enemy);
    protected abstract void performAttack(GameCharacter enemy);
    
    // 구체 메소드들 (공통 로직)
    protected void prepareBattle() {
        System.out.println(name + "이(가) 전투 준비를 합니다.");
    }
    
    protected void performDefense() {
        System.out.println(name + "이(가) 방어 자세를 취합니다.");
    }
    
    protected void afterBattle() {
        System.out.println(name + "의 체력: " + health + ", 마나: " + mana);
    }
    
    // Hook 메소드들
    protected boolean canUseSkill() {
        return mana >= 20; // 기본적으로 마나 20 이상일 때 스킬 사용 가능
    }
    
    public void takeDamage(int damage) {
        health = Math.max(0, health - damage);
        System.out.println(name + "이(가) " + damage + " 데미지를 받았습니다. (체력: " + health + ")");
    }
    
    public void consumeMana(int amount) {
        mana = Math.max(0, mana - amount);
    }
}

// 전사 클래스
class Warrior extends GameCharacter {
    private int rage;
    
    public Warrior(String name) {
        super(name);
        this.rage = 0;
    }
    
    @Override
    protected void useSkill(GameCharacter enemy) {
        System.out.println(name + "이(가) '강타' 스킬을 사용합니다!");
        enemy.takeDamage(35);
        consumeMana(20);
        rage += 5;
    }
    
    @Override
    protected void performAttack(GameCharacter enemy) {
        int damage = 20 + (rage / 5); // 분노에 따라 데미지 증가
        System.out.println(name + "이(가) 기본 공격을 합니다! (" + damage + " 데미지)");
        enemy.takeDamage(damage);
    }
}

// 마법사 클래스
class Wizard extends GameCharacter {
    
    public Wizard(String name) {
        super(name);
        this.mana = 80; // 마법사는 마나가 더 많음
    }
    
    @Override
    protected void useSkill(GameCharacter enemy) {
        System.out.println(name + "이(가) '파이어볼' 마법을 시전합니다!");
        enemy.takeDamage(40);
        consumeMana(25);
    }
    
    @Override
    protected void performAttack(GameCharacter enemy) {
        System.out.println(name + "이(가) 마법 미사일을 발사합니다!");
        enemy.takeDamage(15);
        consumeMana(5);
    }
}

// 클라이언트 코드
public class GameTemplateMethodExample {
    public static void main(String[] args) {
        GameCharacter warrior = new Warrior("용감한 아서");
        GameCharacter wizard = new Wizard("현명한 간달프");
        
        // 더미 적 캐릭터
        GameCharacter dummy = new Warrior("훈련용 더미") {
            @Override
            protected void useSkill(GameCharacter enemy) {
                // 더미는 스킬을 사용하지 않음
            }
            
            @Override
            protected void performAttack(GameCharacter enemy) {
                // 더미는 공격하지 않음
            }
        };
        
        // 각 캐릭터의 전투 실행
        warrior.performBattle(dummy);
        wizard.performBattle(dummy);
    }
}
```

## Spring에서의 활용
스프링에서는 템플릿 메소드 패턴이 여러 곳에서 사용됩니다:

```java
// 1. JdbcTemplate - 템플릿 메소드 패턴의 대표적인 예
@Repository
public class UserRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<User> findUsers() {
        return jdbcTemplate.query(
            "SELECT * FROM users",
            (rs, rowNum) -> new User(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email")
            )
        );
    }
}

// 2. 추상 컨트롤러 클래스 만들기
@RestController
public abstract class BaseController<T, ID> {
    
    protected abstract Service<T, ID> getService();
    protected abstract String getEntityName();
    
    @GetMapping
    public ResponseEntity<List<T>> findAll() {
        logRequest("findAll");
        List<T> entities = getService().findAll();
        logResponse("findAll", entities.size());
        return ResponseEntity.ok(entities);
    }
    
    @PostMapping
    public ResponseEntity<T> create(@RequestBody T entity) {
        logRequest("create");
        validateEntity(entity);
        T saved = getService().save(entity);
        logResponse("create", "success");
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
    
    // Hook 메소드들
    protected void validateEntity(T entity) {
        // 기본 검증 로직 (오버라이드 가능)
    }
    
    protected void logRequest(String operation, Object... params) {
        System.out.printf("[%s] %s 요청: %s\n", 
                         getEntityName(), operation, Arrays.toString(params));
    }
    
    protected void logResponse(String operation, Object result) {
        System.out.printf("[%s] %s 응답: %s\n", 
                         getEntityName(), operation, result);
    }
}

// 구체적인 컨트롤러 구현
@RestController
@RequestMapping("/users")
public class UserController extends BaseController<User, Long> {
    
    @Autowired
    private UserService userService;
    
    @Override
    protected UserService getService() {
        return userService;
    }
    
    @Override
    protected String getEntityName() {
        return "User";
    }
    
    @Override
    protected void validateEntity(User user) {
        if (user.getEmail() == null || !user.getEmail().contains("@")) {
            throw new IllegalArgumentException("유효하지 않은 이메일 주소입니다.");
        }
    }
}
```

템플릿 메소드 패턴은 특히 프레임워크나 라이브러리에서 확장 포인트를 제공할 때 매우 유용하며, 스프링에서도 이 패턴을 통해 개발자가 특정 부분만 구현하고 나머지는 프레임워크가 처리하도록 하는 구조를 많이 사용합니다.
