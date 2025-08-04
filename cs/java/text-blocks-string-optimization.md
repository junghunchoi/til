# Text Blocks와 String 처리 최적화 (Java 15+)

## 핵심 개념

### Text Blocks란?
Java 15에서 정식 도입된 **다중 행 문자열 리터럴**을 간편하게 작성할 수 있는 기능

### 기본 문법
```java
// 전통적인 방식
String jsonOld = "{\n" +
                 "  \"name\": \"John Doe\",\n" +
                 "  \"age\": 30,\n" +
                 "  \"city\": \"Seoul\"\n" +
                 "}";

// Text Blocks 방식 (Java 15+)
String json = """
              {
                "name": "John Doe",
                "age": 30,
                "city": "Seoul"
              }
              """;
```

## Text Blocks 상세 기능

### 1. 기본 사용법
```java
public class TextBlockExamples {
    
    // HTML 템플릿
    public String getHtmlTemplate() {
        return """
               <!DOCTYPE html>
               <html>
               <head>
                   <title>Welcome</title>
               </head>
               <body>
                   <h1>Hello, World!</h1>
                   <p>This is a text block example.</p>
               </body>
               </html>
               """;
    }
    
    // SQL 쿼리
    public String getComplexQuery() {
        return """
               SELECT u.id, u.name, u.email,
                      p.title, p.content, p.created_at
               FROM users u
               JOIN posts p ON u.id = p.user_id
               WHERE u.active = true
                 AND p.published = true
                 AND p.created_at > ?
               ORDER BY p.created_at DESC
               LIMIT 10
               """;
    }
    
    // JSON 데이터
    public String getUserJson() {
        return """
               {
                 "users": [
                   {
                     "id": 1,
                     "name": "김철수",
                     "roles": ["USER", "ADMIN"],
                     "profile": {
                       "email": "kim@example.com",
                       "phone": "010-1234-5678"
                     }
                   },
                   {
                     "id": 2,
                     "name": "이영희",
                     "roles": ["USER"],
                     "profile": {
                       "email": "lee@example.com",
                       "phone": "010-9876-5432"
                     }
                   }
                 ]
               }
               """;
    }
}
```

### 2. 인덴테이션 처리
```java
public class IndentationExamples {
    
    public void demonstrateIndentation() {
        // 공통 인덴테이션은 자동으로 제거됨
        String code = """
                      if (condition) {
                          System.out.println("Hello");
                          if (nested) {
                              System.out.println("Nested");
                          }
                      }
                      """;
        
        // 결과: 가장 왼쪽 공통 들여쓰기가 제거됨
        System.out.println(code);
        /*
        출력:
        if (condition) {
            System.out.println("Hello");
            if (nested) {
                System.out.println("Nested");
            }
        }
        */
    }
    
    // 의도적인 들여쓰기 보존
    public String getFormattedCode() {
        return """
                   public class Example {
                       public void method() {
                           System.out.println("Indented code");
                       }
                   }
               """;
    }
}
```

### 3. 이스케이프 시퀀스와 특수 문자
```java
public class EscapeSequenceExamples {
    
    public void demonstrateEscapeSequences() {
        // 백슬래시 처리
        String path = """
                      Windows path: C:\\Users\\John\\Documents
                      Unix path: /home/john/documents
                      """;
        
        // 따옴표 처리 (이스케이프 불필요)
        String quotes = """
                        He said: "Hello, how are you?"
                        She replied: 'I'm fine, thanks!'
                        """;
        
        // 변수 삽입을 위한 포맷팅
        String name = "John";
        int age = 30;
        String info = """
                      Name: %s
                      Age: %d
                      Status: Active
                      """.formatted(name, age);
        
        // 또는 String.format 사용
        String info2 = String.format("""
                                    Name: %s
                                    Age: %d
                                    Status: Active
                                    """, name, age);
    }
    
    // 줄 끝 공백 제거
    public String getCleanText() {
        return """
               This line has no trailing spaces
               This line also has no trailing spaces   \s
               This line preserves trailing spaces with \\s
               """;
    }
}
```

## 실제 활용 사례

### 1. 로깅 및 디버깅
```java
@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    public User createUser(CreateUserRequest request) {
        logger.debug("""
                     Creating new user:
                     - Email: {}
                     - Name: {}
                     - Roles: {}
                     - Timestamp: {}
                     """, 
                     request.email(), 
                     request.name(), 
                     request.roles(),
                     LocalDateTime.now());
        
        // 사용자 생성 로직...
        User user = userRepository.save(new User(request));
        
        logger.info("""
                    User created successfully:
                    - User ID: {}
                    - Email: {}
                    - Created at: {}
                    """, 
                    user.getId(), 
                    user.getEmail(), 
                    user.getCreatedAt());
        
        return user;
    }
}
```

### 2. 테스트 데이터 및 Mock
```java
@Test
public class ApiTest {
    
    @Test
    public void testUserApiResponse() {
        // Given
        String expectedJson = """
                              {
                                "status": "success",
                                "data": {
                                  "user": {
                                    "id": 1,
                                    "name": "Test User",
                                    "email": "test@example.com"
                                  }
                                },
                                "message": "User retrieved successfully"
                              }
                              """;
        
        // When
        String actualJson = userController.getUser(1L);
        
        // Then
        JSONAssert.assertEquals(expectedJson, actualJson, false);
    }
    
    @Test
    public void testDatabaseQuery() {
        String sql = """
                     INSERT INTO users (name, email, password, created_at) 
                     VALUES 
                     ('John Doe', 'john@example.com', 'hashed_password', NOW()),
                     ('Jane Smith', 'jane@example.com', 'hashed_password', NOW()),
                     ('Bob Johnson', 'bob@example.com', 'hashed_password', NOW())
                     """;
        
        jdbcTemplate.execute(sql);
        
        List<User> users = userRepository.findAll();
        assertEquals(3, users.size());
    }
}
```

### 3. 설정 파일 및 템플릿
```java
@Component
public class ConfigurationGenerator {
    
    public String generateNginxConfig(String serverName, int port) {
        return """
               server {
                   listen 80;
                   server_name %s;
                   
                   location / {
                       proxy_pass http://localhost:%d;
                       proxy_set_header Host $host;
                       proxy_set_header X-Real-IP $remote_addr;
                       proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                       proxy_set_header X-Forwarded-Proto $scheme;
                   }
                   
                   location /health {
                       access_log off;
                       return 200 "healthy\\n";
                       add_header Content-Type text/plain;
                   }
               }
               """.formatted(serverName, port);
    }
    
    public String generateDockerfile(String baseImage, String appName, int port) {
        return """
               FROM %s
               
               WORKDIR /app
               
               COPY target/%s.jar app.jar
               
               EXPOSE %d
               
               ENV JAVA_OPTS="-Xmx512m -Xms512m"
               
               HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \\
                   CMD curl -f http://localhost:%d/health || exit 1
               
               ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
               """.formatted(baseImage, appName, port, port);
    }
}
```

### 4. 이메일 템플릿
```java
@Service
public class EmailService {
    
    public void sendWelcomeEmail(User user) {
        String subject = "Welcome to Our Service!";
        String htmlContent = """
                             <!DOCTYPE html>
                             <html>
                             <head>
                                 <meta charset="UTF-8">
                                 <title>Welcome!</title>
                                 <style>
                                     body { font-family: Arial, sans-serif; }
                                     .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                                     .header { background-color: #007bff; color: white; padding: 20px; text-align: center; }
                                     .content { padding: 20px; background-color: #f8f9fa; }
                                     .button { background-color: #28a745; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; }
                                 </style>
                             </head>
                             <body>
                                 <div class="container">
                                     <div class="header">
                                         <h1>Welcome, %s!</h1>
                                     </div>
                                     <div class="content">
                                         <p>Thank you for joining our service. We're excited to have you on board!</p>
                                         <p>Your account details:</p>
                                         <ul>
                                             <li>Email: %s</li>
                                             <li>Member since: %s</li>
                                         </ul>
                                         <p>Get started by exploring our features:</p>
                                         <p><a href="https://our-service.com/dashboard" class="button">Go to Dashboard</a></p>
                                         <p>If you have any questions, feel free to contact our support team.</p>
                                         <p>Best regards,<br>The Team</p>
                                     </div>
                                 </div>
                             </body>
                             </html>
                             """.formatted(user.getName(), user.getEmail(), user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        emailSender.send(user.getEmail(), subject, htmlContent);
    }
}
```

## String 처리 최적화

### 1. String.formatted() 메서드 (Java 15+)
```java
public class StringFormattingExamples {
    
    public void demonstrateFormatted() {
        String name = "John";
        int age = 30;
        double salary = 50000.5;
        
        // 전통적인 방식
        String oldWay = String.format("Name: %s, Age: %d, Salary: %.2f", name, age, salary);
        
        // 새로운 방식 - formatted() 메서드
        String newWay = "Name: %s, Age: %d, Salary: %.2f".formatted(name, age, salary);
        
        // Text blocks와 함께 사용
        String report = """
                        Employee Report
                        ===============
                        Name: %s
                        Age: %d
                        Salary: $%.2f
                        Department: %s
                        """.formatted(name, age, salary, "Engineering");
        
        System.out.println(report);
    }
    
    public String generateReport(List<Employee> employees) {
        StringBuilder report = new StringBuilder();
        report.append("""
                      Employee Report - %s
                      ================================
                      Total Employees: %d
                      
                      """.formatted(LocalDate.now(), employees.size()));
        
        for (Employee emp : employees) {
            report.append("""
                          ID: %d | Name: %-20s | Department: %-15s | Salary: $%,.2f
                          """.formatted(emp.getId(), emp.getName(), emp.getDepartment(), emp.getSalary()));
        }
        
        return report.toString();
    }
}
```

### 2. 성능 최적화 기법
```java
public class StringOptimization {
    
    // StringBuilder 사용 (많은 문자열 조작 시)
    public String buildLargeString(List<String> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                  Processing Items:
                  ================
                  """);
        
        for (int i = 0; i < items.size(); i++) {
            sb.append("Item %d: %s%n".formatted(i + 1, items.get(i)));
        }
        
        sb.append("""
                  
                  Processing completed.
                  Total items: %d
                  """.formatted(items.size()));
        
        return sb.toString();
    }
    
    // String.join() 활용
    public String createCsvHeader() {
        return String.join(",", 
            "ID", "Name", "Email", "Department", "Salary", "Join Date");
    }
    
    public String createCsvRow(Employee emp) {
        return String.join(",",
            String.valueOf(emp.getId()),
            emp.getName(),
            emp.getEmail(),
            emp.getDepartment(),
            String.valueOf(emp.getSalary()),
            emp.getJoinDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        );
    }
    
    // Stream과 함께 사용
    public String generateEmployeeCsv(List<Employee> employees) {
        String header = createCsvHeader();
        String rows = employees.stream()
                              .map(this::createCsvRow)
                              .collect(Collectors.joining("\n"));
        
        return """
               %s
               %s
               """.formatted(header, rows);
    }
}
```

### 3. 국제화 지원
```java
@Component
public class LocalizedMessageService {
    
    private final MessageSource messageSource;
    
    public LocalizedMessageService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
    
    public String getWelcomeMessage(String userName, Locale locale) {
        String template = messageSource.getMessage("welcome.template", null, locale);
        return template.formatted(userName);
    }
    
    // messages_ko.properties:
    // welcome.template=안녕하세요, %s님! 환영합니다.
    
    // messages_en.properties:
    // welcome.template=Hello, %s! Welcome.
    
    public String getMultilineMessage(String userName, int messageCount, Locale locale) {
        return """
               %s
               
               You have %d new messages.
               Please check your inbox.
               """.formatted(
                   messageSource.getMessage("greeting", new Object[]{userName}, locale),
                   messageCount
               );
    }
}
```

## 마이그레이션 가이드

### 기존 코드를 Text Blocks로 변환
```java
public class MigrationExamples {
    
    // Before: 전통적인 문자열 연결
    public String getOldSqlQuery() {
        return "SELECT u.id, u.name, u.email, " +
               "       p.title, p.content, p.created_at " +
               "FROM users u " +
               "JOIN posts p ON u.id = p.user_id " +
               "WHERE u.active = true " +
               "  AND p.published = true " +
               "ORDER BY p.created_at DESC";
    }
    
    // After: Text Blocks 사용
    public String getNewSqlQuery() {
        return """
               SELECT u.id, u.name, u.email,
                      p.title, p.content, p.created_at
               FROM users u
               JOIN posts p ON u.id = p.user_id
               WHERE u.active = true
                 AND p.published = true
               ORDER BY p.created_at DESC
               """;
    }
    
    // Before: 복잡한 HTML 문자열
    public String getOldHtmlForm() {
        return "<form action=\"/submit\" method=\"post\">\n" +
               "  <div class=\"form-group\">\n" +
               "    <label for=\"name\">Name:</label>\n" +
               "    <input type=\"text\" id=\"name\" name=\"name\" required>\n" +
               "  </div>\n" +
               "  <div class=\"form-group\">\n" +
               "    <label for=\"email\">Email:</label>\n" +
               "    <input type=\"email\" id=\"email\" name=\"email\" required>\n" +
               "  </div>\n" +
               "  <button type=\"submit\">Submit</button>\n" +
               "</form>";
    }
    
    // After: Text Blocks로 깔끔하게
    public String getNewHtmlForm() {
        return """
               <form action="/submit" method="post">
                 <div class="form-group">
                   <label for="name">Name:</label>
                   <input type="text" id="name" name="name" required>
                 </div>
                 <div class="form-group">
                   <label for="email">Email:</label>
                   <input type="email" id="email" name="email" required>
                 </div>
                 <button type="submit">Submit</button>
               </form>
               """;
    }
}
```

## 면접 핵심 포인트

### 자주 묻는 질문들

**Q: Text Blocks의 장점은?**
1. **가독성 향상**: 다중 행 문자열을 자연스럽게 표현
2. **유지보수성**: 이스케이프 시퀀스가 줄어들어 수정이 쉬움
3. **개발 생산성**: SQL, HTML, JSON 등을 직관적으로 작성
4. **컴파일 타임 검증**: 문자열 문법 오류를 조기 발견

**Q: 성능상 차이가 있나요?**
- **컴파일 타임**: Text blocks는 컴파일 시점에 일반 문자열로 변환
- **런타임**: 기존 문자열과 동일한 성능
- **메모리**: String interning 등 기존 최적화 기법 모두 적용

**Q: 언제 Text Blocks를 사용하나요?**
- **다중 행 텍스트**: SQL 쿼리, HTML, JSON, XML
- **템플릿**: 이메일 템플릿, 설정 파일 생성
- **테스트 데이터**: 복잡한 테스트 데이터나 예상 결과
- **로깅**: 구조화된 로그 메시지

**Q: 주의사항은?**
- **인덴테이션**: 공통 들여쓰기가 자동으로 제거됨
- **후행 공백**: 줄 끝 공백은 기본적으로 제거됨
- **변수 삽입**: 직접 지원하지 않음 (formatted() 사용 필요)

## 실무 주의사항

### 1. 코드 리뷰 시 고려사항
```java
// 좋은 예: 명확한 구조
public String getApiDocumentation() {
    return """
           API Endpoints:
           =============
           GET    /api/users     - Get all users
           POST   /api/users     - Create new user
           GET    /api/users/{id} - Get user by ID
           PUT    /api/users/{id} - Update user
           DELETE /api/users/{id} - Delete user
           """;
}

// 주의: 너무 긴 텍스트는 가독성 저하
public String getVeryLongText() {
    // 이런 경우 파일로 분리하거나 여러 블록으로 나누는 것을 고려
    return """
           Very long text that spans many lines...
           [100+ lines of text]
           """;
}
```

### 2. 국제화 고려
```java
// 하드코딩된 텍스트 지양
public String getBadMessage() {
    return """
           Welcome to our service!
           Please check your email for verification.
           """; // 다국어 지원 어려움
}

// 메시지 소스 활용
public String getGoodMessage(Locale locale) {
    String template = messageSource.getMessage("welcome.message", null, locale);
    return template; // 또는 template.formatted(...) 사용
}
```

### 3. 보안 고려사항
```java
// 주의: 민감한 정보 하드코딩 금지
public String getBadConfig() {
    return """
           database.url=jdbc:mysql://localhost:3306/mydb
           database.username=admin
           database.password=secretpassword123  # 절대 금지!
           """;
}

// 좋은 예: 환경 변수나 설정 파일 사용
public String getGoodConfig() {
    return """
           database.url=${DATABASE_URL}
           database.username=${DATABASE_USERNAME}
           database.password=${DATABASE_PASSWORD}
           """;
}
```

## 핵심 요약

### Text Blocks 특징
- **다중 행 문자열**: 자연스러운 다중 행 표현
- **이스케이프 최소화**: 따옴표, 백슬래시 등 자동 처리
- **인덴테이션 관리**: 공통 들여쓰기 자동 제거
- **컴파일 타임 변환**: 런타임 성능 동일

### 활용 영역
- **데이터베이스**: SQL 쿼리 작성
- **웹**: HTML, CSS, JavaScript 템플릿
- **API**: JSON 응답 템플릿
- **테스트**: 테스트 데이터 및 예상 결과
- **설정**: 설정 파일 생성

### 최적화 기법
- **formatted() 메서드**: 변수 삽입을 위한 새로운 방법
- **StringBuilder**: 많은 문자열 조작 시
- **String.join()**: 구분자로 연결할 때

### 실무 지침
- **가독성 우선**: 복잡한 다중 행 문자열에 활용
- **국제화 고려**: 하드코딩보다는 메시지 소스 활용
- **보안 주의**: 민감한 정보 하드코딩 금지
- **성능 고려**: 기존 String 최적화 기법과 함께 사용