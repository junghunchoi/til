# 애플리케이션 보안과 OWASP Top 10

## OWASP Top 10 2021 주요 보안 위협

### 1. Broken Access Control (A01)
접근 제어 실패로 인한 권한 취약점입니다.

```java
// ❌ 취약한 코드
@RestController
public class UserController {
    
    @GetMapping("/users/{userId}")
    public UserDto getUser(@PathVariable Long userId) {
        // 현재 사용자가 해당 사용자 정보에 접근할 권한이 있는지 확인하지 않음
        return userService.findById(userId);
    }
    
    @DeleteMapping("/admin/users/{userId}")  
    public void deleteUser(@PathVariable Long userId) {
        // 관리자 권한 확인 없이 사용자 삭제 허용
        userService.delete(userId);
    }
}

// ✅ 안전한 코드
@RestController
public class SecureUserController {
    
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @userService.isOwner(authentication.name, #userId)")
    public UserDto getUser(@PathVariable Long userId) {
        return userService.findById(userId);
    }
    
    @DeleteMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Audit(action = "DELETE_USER")
    public void deleteUser(@PathVariable Long userId, Authentication auth) {
        // 관리자만 접근 가능하며 감사 로그 기록
        userService.delete(userId);
    }
}
```

### 2. Cryptographic Failures (A02)
암호화 실패로 인한 민감 데이터 노출입니다.

```java
// ❌ 취약한 코드
@Entity
public class User {
    private String password; // 평문 저장
    private String creditCardNumber; // 암호화 없이 저장
    private String ssn; // 주민번호 평문 저장
}

// ✅ 안전한 코드
@Entity
public class SecureUser {
    
    @Column(name = "password_hash")
    private String passwordHash; // BCrypt 해시
    
    @Convert(converter = CreditCardEncryptor.class)
    private String creditCardNumber; // AES 암호화
    
    @Convert(converter = SSNEncryptor.class)
    private String ssn; // 주민번호 암호화
    
    // 비밀번호 해싱
    public void setPassword(String plainPassword) {
        this.passwordHash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }
    
    public boolean checkPassword(String plainPassword) {
        return BCrypt.checkpw(plainPassword, this.passwordHash);
    }
}

// AES 암호화 컨버터
@Component
public class CreditCardEncryptor implements AttributeConverter<String, String> {
    
    @Value("${app.encryption.key}")
    private String encryptionKey;
    
    private final AESUtil aesUtil = new AESUtil();
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return aesUtil.encrypt(attribute, encryptionKey);
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        return aesUtil.decrypt(dbData, encryptionKey);
    }
}
```

### 3. Injection (A03)
SQL 인젝션, NoSQL 인젝션, OS 명령 인젝션 등의 취약점입니다.

```java
// ❌ SQL 인젝션 취약 코드
@Repository
public class VulnerableUserRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<User> findByUsername(String username) {
        // SQL 인젝션 취약점
        String query = "SELECT u FROM User u WHERE u.username = '" + username + "'";
        return entityManager.createQuery(query, User.class).getResultList();
    }
    
    public User findByEmailNative(String email) {
        // Native Query SQL 인젝션 취약점
        String sql = "SELECT * FROM users WHERE email = '" + email + "'";
        return (User) entityManager.createNativeQuery(sql, User.class).getSingleResult();
    }
}

// ✅ 안전한 코드
@Repository
public class SecureUserRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<User> findByUsername(String username) {
        // 파라미터 바인딩으로 SQL 인젝션 방지
        return entityManager.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                           .setParameter("username", username)
                           .getResultList();
    }
    
    public User findByEmailNative(String email) {
        // Native Query도 파라미터 바인딩 사용
        String sql = "SELECT * FROM users WHERE email = :email";
        return (User) entityManager.createNativeQuery(sql, User.class)
                                  .setParameter("email", email)
                                  .getSingleResult();
    }
    
    // 입력값 검증과 화이트리스트 방식
    public List<User> findByUsernameWithValidation(String username) {
        // 입력값 검증
        if (!isValidUsername(username)) {
            throw new IllegalArgumentException("유효하지 않은 사용자명");
        }
        
        return entityManager.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                           .setParameter("username", username)
                           .getResultList();
    }
    
    private boolean isValidUsername(String username) {
        // 사용자명 패턴 검증 (알파벳, 숫자, 일부 특수문자만 허용)
        return username != null && username.matches("^[a-zA-Z0-9._-]{3,20}$");
    }
}
```

### 4. Insecure Design (A04)
안전하지 않은 설계로 인한 취약점입니다.

```java
// ❌ 안전하지 않은 설계
@RestController
public class InsecurePasswordResetController {
    
    @PostMapping("/password-reset")
    public ResponseEntity<String> resetPassword(@RequestParam String email) {
        User user = userService.findByEmail(email);
        if (user != null) {
            // 새 비밀번호를 이메일로 전송 (취약점)
            String newPassword = generateRandomPassword();
            user.setPassword(newPassword);
            userService.save(user);
            
            emailService.sendNewPassword(email, newPassword); // 평문 비밀번호 전송
            return ResponseEntity.ok("새 비밀번호가 이메일로 전송되었습니다");
        }
        return ResponseEntity.ok("요청이 처리되었습니다"); // 사용자 존재 여부 노출 방지
    }
}

// ✅ 안전한 설계
@RestController
public class SecurePasswordResetController {
    
    private final PasswordResetTokenService tokenService;
    private final RateLimitService rateLimitService;
    
    @PostMapping("/password-reset-request")
    public ResponseEntity<String> requestPasswordReset(@RequestParam String email, HttpServletRequest request) {
        String clientIP = getClientIP(request);
        
        // Rate Limiting 적용
        if (!rateLimitService.isAllowed(clientIP, "password-reset", 3, Duration.ofMinutes(15))) {
            return ResponseEntity.status(429).body("너무 많은 요청입니다. 나중에 다시 시도해주세요");
        }
        
        User user = userService.findByEmail(email);
        if (user != null) {
            // 안전한 토큰 생성
            String token = tokenService.generateSecureToken(user.getId());
            tokenService.saveToken(user.getId(), token, Duration.ofMinutes(30));
            
            // 토큰이 포함된 링크를 이메일로 전송
            String resetLink = "https://yourapp.com/reset-password?token=" + token;
            emailService.sendPasswordResetLink(email, resetLink);
        }
        
        // 사용자 존재 여부와 관계없이 동일한 응답 (정보 노출 방지)
        return ResponseEntity.ok("비밀번호 재설정 링크가 해당 이메일로 전송되었습니다");
    }
    
    @PostMapping("/password-reset-confirm")
    public ResponseEntity<String> confirmPasswordReset(@RequestParam String token, 
                                                     @RequestParam String newPassword) {
        // 토큰 유효성 검증
        if (!tokenService.isValidToken(token)) {
            return ResponseEntity.badRequest().body("유효하지 않은 토큰입니다");
        }
        
        // 비밀번호 강도 검증
        if (!passwordValidator.isStrong(newPassword)) {
            return ResponseEntity.badRequest().body("비밀번호가 보안 정책을 만족하지 않습니다");
        }
        
        Long userId = tokenService.getUserIdByToken(token);
        User user = userService.findById(userId);
        
        // 비밀번호 변경
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.save(user);
        
        // 토큰 무효화
        tokenService.invalidateToken(token);
        
        // 보안 알림 이메일 발송
        emailService.sendPasswordChangedNotification(user.getEmail());
        
        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다");
    }
}
```

### 5. Security Misconfiguration (A05)
보안 설정 오류로 인한 취약점입니다.

```yaml
# ❌ 취약한 설정
spring:
  profiles:
    active: prod
  datasource:
    url: jdbc:mysql://localhost:3306/myapp
    username: root
    password: root  # 약한 비밀번호
  jpa:
    show-sql: true  # 프로덕션에서 SQL 노출
    hibernate:
      ddl-auto: create-drop  # 프로덕션에서 위험한 설정

management:
  endpoints:
    web:
      exposure:
        include: "*"  # 모든 actuator 엔드포인트 노출
  endpoint:
    health:
      show-details: always  # 상세 정보 노출

logging:
  level:
    org.springframework.web: DEBUG  # 민감한 정보 로깅
```

```yaml
# ✅ 안전한 설정
spring:
  profiles:
    active: prod
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}  # 환경 변수 사용
  jpa:
    show-sql: false  # SQL 숨김
    hibernate:
      ddl-auto: validate  # 안전한 설정

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics  # 필요한 엔드포인트만 노출
      base-path: /internal/actuator  # 경로 변경
  endpoint:
    health:
      show-details: when-authorized  # 인증된 사용자만 상세 정보 확인

logging:
  level:
    org.springframework.web: WARN
    com.yourapp: INFO
    org.springframework.security: WARN  # 보안 로깅 제한

security:
  headers:
    frame-options: DENY
    content-type-options: nosniff
    xss-protection: "1; mode=block"
```

### 6. Vulnerable and Outdated Components (A06)
취약하고 오래된 컴포넌트 사용으로 인한 위험입니다.

```xml
<!-- ❌ 취약한 의존성 -->
<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-web</artifactId>
        <version>4.3.0.RELEASE</version>  <!-- 오래된 버전 -->
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.8.0</version>  <!-- 알려진 취약점 존재 -->
    </dependency>
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-collections4</artifactId>
        <version>4.0</version>  <!-- 역직렬화 취약점 -->
    </dependency>
</dependencies>
```

```xml
<!-- ✅ 안전한 의존성 -->
<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-web</artifactId>
        <version>5.3.21</version>  <!-- 최신 안정 버전 -->
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.13.3</version>  <!-- 보안 패치된 버전 -->
    </dependency>
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-collections4</artifactId>
        <version>4.4</version>  <!-- 취약점 수정된 버전 -->
    </dependency>
</dependencies>

<!-- 보안 취약점 검사 플러그인 -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>7.1.1</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>  <!-- CVSS 7점 이상 시 빌드 실패 -->
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 7. Identification and Authentication Failures (A07)
인증 및 식별 실패로 인한 취약점입니다.

```java
// ❌ 취약한 인증 구현
@RestController
public class WeakAuthController {
    
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String username, @RequestParam String password) {
        User user = userService.findByUsername(username);
        
        // 평문 비밀번호 비교
        if (user != null && user.getPassword().equals(password)) {
            // 세션 고정 공격에 취약
            return ResponseEntity.ok("로그인 성공");
        }
        
        // 사용자 존재 여부 노출
        return ResponseEntity.status(401).body("사용자명 또는 비밀번호가 잘못되었습니다");
    }
    
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestParam String oldPassword, 
                                                @RequestParam String newPassword) {
        // 현재 비밀번호 확인 없이 변경 허용
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        User user = userService.findByUsername(username);
        user.setPassword(newPassword); // 평문 저장
        userService.save(user);
        
        return ResponseEntity.ok("비밀번호가 변경되었습니다");
    }
}

// ✅ 안전한 인증 구현
@RestController
public class SecureAuthController {
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, 
                                             HttpServletRequest httpRequest) {
        String clientIP = getClientIP(httpRequest);
        
        // 브루트 포스 공격 방지
        if (loginAttemptService.isBlocked(clientIP)) {
            return ResponseEntity.status(429)
                               .body(LoginResponse.error("너무 많은 로그인 시도입니다"));
        }
        
        try {
            // Spring Security를 통한 안전한 인증
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtTokenProvider.generateToken(userDetails);
            
            // 로그인 성공 시 시도 횟수 초기화
            loginAttemptService.loginSucceeded(clientIP);
            
            // 보안 이벤트 로깅
            securityEventLogger.logLoginSuccess(userDetails.getUsername(), clientIP);
            
            return ResponseEntity.ok(LoginResponse.success(token));
            
        } catch (BadCredentialsException e) {
            // 로그인 실패 시 시도 횟수 증가
            loginAttemptService.loginFailed(clientIP);
            
            // 보안 이벤트 로깅
            securityEventLogger.logLoginFailure(request.getUsername(), clientIP);
            
            // 동일한 메시지로 정보 노출 방지
            return ResponseEntity.status(401)
                               .body(LoginResponse.error("인증에 실패했습니다"));
        }
    }
    
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request, 
                                                Authentication auth) {
        String username = auth.getName();
        User user = userService.findByUsername(username);
        
        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body("현재 비밀번호가 올바르지 않습니다");
        }
        
        // 새 비밀번호 강도 검증
        if (!passwordValidator.validate(request.getNewPassword())) {
            return ResponseEntity.badRequest().body("비밀번호가 보안 정책을 만족하지 않습니다");
        }
        
        // 안전한 비밀번호 해싱 및 저장
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userService.save(user);
        
        // 보안 알림
        emailService.sendPasswordChangedNotification(user.getEmail());
        
        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다");
    }
}
```

## 보안 강화 실무 구현

### 1. 입력 검증과 출력 인코딩
```java
@Component
public class InputValidator {
    
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("^[0-9-+()\\s]{10,15}$");
    
    private static final Pattern SQL_INJECTION_PATTERN = 
        Pattern.compile("('.+(\\)|;|\\||\\*|\\%|\\+|\\-|\\=|\\<|\\>))|union|select|insert|delete|update|drop|create|alter|exec|execute", 
                       Pattern.CASE_INSENSITIVE);
    
    public boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    public boolean isValidPhoneNumber(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }
    
    public boolean containsSQLInjection(String input) {
        return input != null && SQL_INJECTION_PATTERN.matcher(input).find();
    }
    
    public String sanitizeHtml(String input) {
        if (input == null) return null;
        
        // OWASP Java HTML Sanitizer 사용
        PolicyFactory policy = Sanitizers.FORMATTING.and(Sanitizers.LINKS);
        return policy.sanitize(input);
    }
    
    public String escapeXSS(String value) {
        if (value == null) return null;
        
        return StringEscapeUtils.escapeHtml4(value);
    }
}

// 커스텀 검증 어노테이션
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME) 
@Constraint(validatedBy = NoSQLInjectionValidator.class)
public @interface NoSQLInjection {
    String message() default "잠재적인 SQL 인젝션 패턴이 감지되었습니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class NoSQLInjectionValidator implements ConstraintValidator<NoSQLInjection, String> {
    
    @Autowired
    private InputValidator inputValidator;
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || !inputValidator.containsSQLInjection(value);
    }
}
```

### 2. CSRF 방어
```java
@Configuration
@EnableWebSecurity
public class CSRFSecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/public/**") // 공개 API는 CSRF 검사 제외
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
                .sessionRegistry(sessionRegistry())
            );
            
        return http.build();
    }
    
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }
}

// API에서 CSRF 토큰 제공
@RestController
public class CSRFController {
    
    @GetMapping("/api/csrf-token")
    public CsrfToken csrfToken(HttpServletRequest request) {
        return (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    }
}
```

### 3. 보안 헤더 설정
```java
@Configuration
public class SecurityHeadersConfig implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SecurityHeadersInterceptor());
    }
}

public class SecurityHeadersInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // XSS 방어
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // 콘텐츠 타입 스니핑 방지
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // 클릭재킹 방지
        response.setHeader("X-Frame-Options", "DENY");
        
        // HTTPS 강제
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        
        // 콘텐츠 보안 정책
        response.setHeader("Content-Security-Policy", 
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
        
        // 추천 정보 제한
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        return true;
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "OWASP Top 10 중 가장 우선적으로 해결해야 할 취약점은?"
**답변 포인트:**
- **Broken Access Control**: 가장 빈번하고 영향도가 큰 취약점
- **Injection**: SQL 인젝션 등 직접적인 데이터 탈취 가능
- **비즈니스 영향도**: 금융, 의료 등 민감 데이터 처리 시스템 우선
- **공격 용이성**: 자동화된 도구로 쉽게 공격 가능한 취약점 우선

### Q2: "보안 테스트는 어떻게 자동화하나요?"
**답변 포인트:**
- **SAST**: SonarQube, Checkmarx 등으로 정적 분석
- **DAST**: OWASP ZAP, Burp Suite 등으로 동적 분석  
- **의존성 검사**: OWASP Dependency Check, Snyk 등
- **CI/CD 통합**: 파이프라인에 보안 검사 단계 포함

### Q3: "프로덕션 환경에서 보안 사고 발생 시 대응 절차는?"
**답변 포인트:**
- **즉시 격리**: 영향받은 시스템 즉시 차단
- **로그 수집**: 공격 흔적과 영향 범위 분석
- **패치 적용**: 취약점 즉시 수정 및 배포
- **사후 분석**: 근본 원인 분석과 재발 방지책 수립

## 실무 베스트 프랙티스

1. **보안 첫 번째 원칙**: 개발 초기부터 보안을 고려한 설계
2. **다층 방어**: 여러 보안 메커니즘을 조합하여 방어력 강화
3. **최소 권한 원칙**: 필요한 최소한의 권한만 부여
4. **지속적 모니터링**: 보안 이벤트 실시간 모니터링 및 대응
5. **보안 교육**: 개발팀 대상 정기적인 보안 교육 실시

OWASP Top 10을 기반으로 한 체계적인 보안 강화는 애플리케이션의 전반적인 보안 수준을 크게 향상시킵니다. 각 취약점의 특성을 이해하고 적절한 대응책을 적용하는 것이 중요합니다.