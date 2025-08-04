# SQL 인젝션과 XSS 방어 기법

## SQL 인젝션 방어

### 1. 기본적인 SQL 인젝션 취약점
```java
// ❌ 매우 위험한 코드 - SQL 인젝션 취약점
@Repository
public class VulnerableUserDao {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public User findByUsername(String username) {
        // 사용자 입력을 직접 쿼리에 연결 - 매우 위험!
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        
        // 공격 예시: username = "admin'; DROP TABLE users; --"
        // 결과 쿼리: SELECT * FROM users WHERE username = 'admin'; DROP TABLE users; --'
        
        return jdbcTemplate.queryForObject(sql, User.class);
    }
    
    public List<User> searchUsers(String searchTerm, String orderBy) {
        // ORDER BY 절도 SQL 인젝션 취약점이 될 수 있음
        String sql = "SELECT * FROM users WHERE name LIKE '%" + searchTerm + "%' ORDER BY " + orderBy;
        
        // 공격 예시: orderBy = "name; DELETE FROM users WHERE '1'='1"
        
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(User.class));
    }
}
```

### 2. 안전한 SQL 쿼리 구현
```java
// ✅ 안전한 코드 - 파라미터 바인딩 사용
@Repository
public class SecureUserDao {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public User findByUsername(String username) {
        // PreparedStatement 파라미터 바인딩 사용
        String sql = "SELECT * FROM users WHERE username = ?";
        
        try {
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(User.class), username);
        } catch (EmptyResultDataAccessException e) {
            return null; // 사용자 없음
        }
    }
    
    public List<User> searchUsers(String searchTerm, String sortColumn, String sortDirection) {
        // 입력값 검증 및 화이트리스트 적용
        if (!isValidSortColumn(sortColumn) || !isValidSortDirection(sortDirection)) {
            throw new IllegalArgumentException("유효하지 않은 정렬 조건입니다");
        }
        
        // 동적 ORDER BY는 파라미터 바인딩이 불가능하므로 화이트리스트 검증 후 직접 삽입
        String sql = "SELECT * FROM users WHERE name LIKE ? ORDER BY " + sortColumn + " " + sortDirection;
        
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(User.class), "%" + searchTerm + "%");
    }
    
    private boolean isValidSortColumn(String column) {
        // 허용된 컬럼만 화이트리스트로 관리
        Set<String> allowedColumns = Set.of("id", "username", "email", "created_at", "updated_at");
        return allowedColumns.contains(column.toLowerCase());
    }
    
    private boolean isValidSortDirection(String direction) {
        return "ASC".equalsIgnoreCase(direction) || "DESC".equalsIgnoreCase(direction);
    }
    
    // 복잡한 동적 쿼리는 QueryDSL 또는 MyBatis 활용
    public List<User> findUsersWithDynamicQuery(UserSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (criteria.getUsername() != null) {
            sql.append(" AND username LIKE ?");
            params.add("%" + criteria.getUsername() + "%");
        }
        
        if (criteria.getEmail() != null) {
            sql.append(" AND email = ?");
            params.add(criteria.getEmail());
        }
        
        if (criteria.getCreatedAfter() != null) {
            sql.append(" AND created_at >= ?");
            params.add(criteria.getCreatedAfter());
        }
        
        return jdbcTemplate.query(sql.toString(), new BeanPropertyRowMapper<>(User.class), params.toArray());
    }
}
```

### 3. JPA/Hibernate에서의 SQL 인젝션 방지
```java
// ❌ JPA에서도 발생할 수 있는 SQL 인젝션
@Repository
public class VulnerableJpaRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<User> findByDynamicQuery(String condition) {
        // 동적 조건을 직접 쿼리에 삽입 - 위험!
        String jpql = "SELECT u FROM User u WHERE " + condition;
        return entityManager.createQuery(jpql, User.class).getResultList();
    }
    
    public List<User> findByNativeQuery(String tableName, String username) {
        // Native Query에서 테이블명을 동적으로 사용 - 위험!
        String sql = "SELECT * FROM " + tableName + " WHERE username = '" + username + "'";
        return entityManager.createNativeQuery(sql, User.class).getResultList();
    }
}

// ✅ JPA에서의 안전한 쿼리 작성
@Repository
public class SecureJpaRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // JPQL with Named Parameters
    public List<User> findByUsernameAndEmail(String username, String email) {
        String jpql = "SELECT u FROM User u WHERE u.username = :username AND u.email = :email";
        return entityManager.createQuery(jpql, User.class)
                           .setParameter("username", username)
                           .setParameter("email", email)
                           .getResultList();
    }
    
    // Criteria API 사용 - 타입 안전하고 SQL 인젝션 방지
    public List<User> findUsersByCriteria(String username, String email, LocalDateTime createdAfter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (username != null && !username.trim().isEmpty()) {
            predicates.add(cb.like(cb.lower(root.get("username")), 
                                 "%" + username.toLowerCase() + "%"));
        }
        
        if (email != null && !email.trim().isEmpty()) {
            predicates.add(cb.equal(root.get("email"), email));
        }
        
        if (createdAfter != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.desc(root.get("createdAt")));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // QueryDSL 사용 - 컴파일 타임 안전성과 가독성
    public List<User> findUsersWithQueryDSL(String username, String email) {
        QUser user = QUser.user;
        JPAQueryFactory queryFactory = new JPAQueryFactory(entityManager);
        
        BooleanBuilder builder = new BooleanBuilder();
        
        if (username != null && !username.trim().isEmpty()) {
            builder.and(user.username.containsIgnoreCase(username));
        }
        
        if (email != null && !email.trim().isEmpty()) {
            builder.and(user.email.eq(email));
        }
        
        return queryFactory.selectFrom(user)
                          .where(builder)
                          .orderBy(user.createdAt.desc())
                          .fetch();
    }
}
```

### 4. 저장 프로시저와 SQL 인젝션 방지
```sql
-- ✅ 안전한 저장 프로시저
DELIMITER //
CREATE PROCEDURE GetUsersByRole(
    IN p_role VARCHAR(50),
    IN p_active BOOLEAN,
    IN p_limit INT
)
BEGIN
    -- 입력 파라미터 검증
    IF p_limit > 1000 THEN
        SET p_limit = 1000; -- 최대 제한
    END IF;
    
    IF p_limit < 1 THEN
        SET p_limit = 10; -- 기본값
    END IF;
    
    -- 파라미터를 사용한 안전한 쿼리
    SELECT u.id, u.username, u.email, u.role, u.active, u.created_at
    FROM users u
    WHERE u.role = p_role 
      AND u.active = p_active
    ORDER BY u.created_at DESC
    LIMIT p_limit;
END //
DELIMITER ;
```

```java
// Java에서 저장 프로시저 호출
@Repository
public class UserStoredProcedureRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<User> getUsersByRole(String role, boolean active, int limit) {
        // 저장 프로시저 호출 - SQL 인젝션 안전
        String sql = "{CALL GetUsersByRole(?, ?, ?)}";
        
        return jdbcTemplate.query(sql, 
            new BeanPropertyRowMapper<>(User.class),
            role, active, limit);
    }
}
```

## XSS (Cross-Site Scripting) 방어

### 1. XSS 취약점 종류와 예시
```java
// ❌ XSS 취약점이 있는 코드
@Controller
public class VulnerableController {
    
    @GetMapping("/profile")
    public String showProfile(@RequestParam String name, Model model) {
        // 사용자 입력을 그대로 출력 - XSS 취약점
        model.addAttribute("userName", name);
        return "profile"; // profile.html에서 ${userName}을 그대로 출력
    }
    
    @PostMapping("/comment")
    public String saveComment(@RequestParam String content, Model model) {
        // HTML 태그가 포함된 댓글을 그대로 저장 및 출력
        Comment comment = new Comment(content);
        commentService.save(comment);
        
        model.addAttribute("message", "댓글이 저장되었습니다: " + content);
        return "comment-result";
    }
    
    @GetMapping("/search")
    public String search(@RequestParam String query, Model model) {
        List<Product> products = productService.search(query);
        
        // 검색어를 그대로 출력 - Reflected XSS 취약점
        model.addAttribute("searchQuery", query);
        model.addAttribute("products", products);
        return "search-results";
    }
}
```

### 2. 안전한 XSS 방어 구현
```java
// ✅ XSS 방어가 적용된 코드
@Controller
public class SecureController {
    
    @Autowired
    private InputSanitizer inputSanitizer;
    
    @GetMapping("/profile")
    public String showProfile(@RequestParam String name, Model model) {
        // 입력값 검증 및 이스케이프
        String safeName = inputSanitizer.sanitizeForHtml(name);
        model.addAttribute("userName", safeName);
        return "profile";
    }
    
    @PostMapping("/comment")
    public String saveComment(@Valid @ModelAttribute CommentForm form, 
                            BindingResult bindingResult, Model model) {
        
        if (bindingResult.hasErrors()) {
            return "comment-form";
        }
        
        // HTML 태그 제거 또는 안전한 태그만 허용
        String sanitizedContent = inputSanitizer.sanitizeForHtml(form.getContent());
        
        Comment comment = new Comment(sanitizedContent);
        commentService.save(comment);
        
        model.addAttribute("message", "댓글이 성공적으로 저장되었습니다.");
        return "comment-result";
    }
    
    @GetMapping("/search")
    public String search(@RequestParam String query, Model model) {
        // 입력값 길이 제한
        if (query.length() > 100) {
            query = query.substring(0, 100);
        }
        
        List<Product> products = productService.search(query);
        
        // 검색어 이스케이프
        String safeQuery = inputSanitizer.escapeHtml(query);
        model.addAttribute("searchQuery", safeQuery);
        model.addAttribute("products", products);
        return "search-results";
    }
}

// 입력값 검증 및 정제 유틸리티
@Component
public class InputSanitizer {
    
    private final PolicyFactory htmlPolicy;
    
    public InputSanitizer() {
        // OWASP Java HTML Sanitizer 정책 설정
        this.htmlPolicy = Sanitizers.FORMATTING
                                   .and(Sanitizers.LINKS)
                                   .and(Sanitizers.BLOCKS)
                                   .and(Sanitizers.IMAGES);
    }
    
    public String sanitizeForHtml(String input) {
        if (input == null) return "";
        
        // 허용된 HTML 태그만 유지, 나머지는 제거
        return htmlPolicy.sanitize(input);
    }
    
    public String escapeHtml(String input) {
        if (input == null) return "";
        
        // 모든 HTML 특수 문자를 이스케이프
        return StringEscapeUtils.escapeHtml4(input);
    }
    
    public String sanitizeForJavaScript(String input) {
        if (input == null) return "";
        
        // JavaScript에서 안전하게 사용할 수 있도록 이스케이프
        return StringEscapeUtils.escapeEcmaScript(input);
    }
    
    public String sanitizeForUrl(String input) {
        if (input == null) return "";
        
        try {
            // URL 인코딩
            return URLEncoder.encode(input, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("URL 인코딩 실패", e);
        }
    }
}

// 커스텀 검증 어노테이션
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeHtmlValidator.class)
public @interface SafeHtml {
    String message() default "잠재적으로 위험한 HTML 콘텐츠가 감지되었습니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class SafeHtmlValidator implements ConstraintValidator<SafeHtml, String> {
    
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "<script[^>]*>.*?</script>|javascript:|on\\w+\\s*=", 
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        
        return !SCRIPT_PATTERN.matcher(value).find();
    }
}
```

### 3. Thymeleaf 템플릿에서의 XSS 방어
```html
<!-- ❌ 취약한 Thymeleaf 템플릿 -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>사용자 프로필</title>
</head>
<body>
    <!-- 이스케이프 없이 출력 - XSS 취약점 -->
    <h1 th:utext="${userName}">사용자명</h1>
    
    <!-- JavaScript에 직접 변수 삽입 - XSS 취약점 -->
    <script>
        var userName = /*[[${userName}]]*/ '';
        alert('환영합니다, ' + userName);
    </script>
    
    <!-- 댓글 내용을 그대로 출력 -->
    <div th:each="comment : ${comments}">
        <p th:utext="${comment.content}"></p>
    </div>
</body>
</html>
```

```html
<!-- ✅ 안전한 Thymeleaf 템플릿 -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>사용자 프로필</title>
</head>
<body>
    <!-- 자동 HTML 이스케이프 (th:text 사용) -->
    <h1 th:text="${userName}">사용자명</h1>
    
    <!-- JavaScript에서 안전한 변수 사용 -->
    <script th:inline="javascript">
        /*<![CDATA[*/
        var userName = /*[[${#strings.escapeJavaScript(userName)}]]*/ '';
        alert('환영합니다, ' + userName);
        /*]]>*/
    </script>
    
    <!-- 안전한 HTML 렌더링 -->
    <div th:each="comment : ${comments}">
        <!-- 기본적으로 HTML 이스케이프됨 -->
        <p th:text="${comment.content}"></p>
        
        <!-- 허용된 HTML만 렌더링 (사전에 sanitize된 경우) -->
        <div th:if="${comment.isHtmlAllowed}" th:utext="${comment.sanitizedContent}"></div>
    </div>
    
    <!-- URL에서 안전한 파라미터 사용 -->
    <a th:href="@{/user/profile(id=${#httpServletRequest.getParameter('userId')})}">프로필 보기</a>
</body>
</html>
```

### 4. Content Security Policy (CSP) 구현
```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
            .contentSecurityPolicy(
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                "font-src 'self' https://fonts.gstatic.com; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'"
            )
        );
        
        return http.build();
    }
}

// 동적 CSP 헤더 설정
@Component
public class CSPHeaderInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 페이지별로 다른 CSP 정책 적용
        String requestURI = request.getRequestURI();
        
        if (requestURI.startsWith("/admin")) {
            // 관리자 페이지는 더 엄격한 정책
            response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'");
        } else if (requestURI.startsWith("/public")) {
            // 공개 페이지는 외부 리소스 허용
            response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; " +
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com");
        }
        
        return true;
    }
}
```

### 5. API에서의 XSS 방어
```java
@RestController
@RequestMapping("/api")
public class SecureApiController {
    
    @Autowired
    private InputSanitizer inputSanitizer;
    
    @PostMapping("/comments")
    public ResponseEntity<ApiResponse<CommentDto>> createComment(@Valid @RequestBody CreateCommentRequest request) {
        
        // API 요청도 XSS 공격 대상이 될 수 있으므로 검증 필요
        String sanitizedContent = inputSanitizer.sanitizeForHtml(request.getContent());
        
        Comment comment = new Comment();
        comment.setContent(sanitizedContent);
        comment.setAuthor(request.getAuthor());
        
        Comment savedComment = commentService.save(comment);
        CommentDto responseDto = CommentDto.from(savedComment);
        
        return ResponseEntity.ok(ApiResponse.success(responseDto));
    }
    
    @GetMapping("/users/search")
    public ResponseEntity<ApiResponse<List<UserDto>>> searchUsers(@RequestParam String query) {
        
        // 검색어 길이 제한
        if (query.length() > 50) {
            return ResponseEntity.badRequest()
                                .body(ApiResponse.error("검색어가 너무 깁니다"));
        }
        
        // 특수 문자 검증
        if (!isValidSearchQuery(query)) {
            return ResponseEntity.badRequest()
                                .body(ApiResponse.error("유효하지 않은 검색어입니다"));
        }
        
        List<User> users = userService.searchByName(query);
        List<UserDto> userDtos = users.stream()
                                     .map(UserDto::from)
                                     .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(userDtos));
    }
    
    private boolean isValidSearchQuery(String query) {
        // 허용된 문자만 포함하는지 검증
        return query.matches("^[a-zA-Z0-9가-힣\\s\\-_\\.]+$");
    }
}

// DTO에서의 XSS 방어
public class CommentDto {
    private Long id;
    
    @JsonSerialize(using = HtmlEscapeSerializer.class)
    private String content; // JSON 직렬화 시 HTML 이스케이프
    
    private String author;
    private LocalDateTime createdAt;
    
    // getters, setters...
}

// 커스텀 JSON 직렬화기
public class HtmlEscapeSerializer extends JsonSerializer<String> {
    
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) 
            throws IOException {
        if (value != null) {
            gen.writeString(StringEscapeUtils.escapeHtml4(value));
        } else {
            gen.writeNull();
        }
    }
}
```

## 고급 방어 기법

### 1. 입력값 검증 필터
```java
@Component
@Order(1)
public class XSSPreventionFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        XSSRequestWrapper wrappedRequest = new XSSRequestWrapper((HttpServletRequest) request);
        chain.doFilter(wrappedRequest, response);
    }
}

public class XSSRequestWrapper extends HttpServletRequestWrapper {
    
    private static final Pattern[] XSS_PATTERNS = {
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("onerror(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("onclick(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    };
    
    public XSSRequestWrapper(HttpServletRequest servletRequest) {
        super(servletRequest);
    }
    
    @Override
    public String[] getParameterValues(String parameter) {
        String[] values = super.getParameterValues(parameter);
        
        if (values == null) {
            return null;
        }
        
        int count = values.length;
        String[] encodedValues = new String[count];
        for (int i = 0; i < count; i++) {
            encodedValues[i] = stripXSS(values[i]);
        }
        
        return encodedValues;
    }
    
    @Override
    public String getParameter(String parameter) {
        String value = super.getParameter(parameter);
        return stripXSS(value);
    }
    
    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return stripXSS(value);
    }
    
    private String stripXSS(String value) {
        if (value != null) {
            value = StringEscapeUtils.escapeHtml4(value);
            
            // XSS 패턴 제거
            for (Pattern pattern : XSS_PATTERNS) {
                value = pattern.matcher(value).replaceAll("");
            }
        }
        return value;
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "PreparedStatement가 SQL 인젝션을 완전히 방지하나요?"
**답변 포인트:**
- **대부분 방지**: 파라미터 바인딩으로 대부분의 SQL 인젝션 방지
- **예외 상황**: ORDER BY, 테이블명 등 동적 쿼리 부분은 별도 검증 필요
- **2차 SQL 인젝션**: 저장된 데이터가 다른 쿼리에서 사용될 때 주의 필요
- **NoSQL 인젝션**: MongoDB 등에서는 다른 방어 기법 필요

### Q2: "CSP 헤더만으로 XSS를 완전히 방지할 수 있나요?"
**답변 포인트:**
- **보조적 역할**: CSP는 방어의 한 층이지, 완전한 해결책 아님
- **입력 검증 필수**: 서버 사이드 입력 검증과 출력 인코딩이 기본
- **브라우저 지원**: 구형 브라우저에서는 CSP 지원 제한적
- **우회 가능성**: CSP 정책 설정 오류 시 우회 가능

### Q3: "DOM 기반 XSS는 어떻게 방어하나요?"
**답변 포인트:**
- **클라이언트 사이드 검증**: JavaScript에서 DOM 조작 시 입력값 검증
- **안전한 API 사용**: innerHTML 대신 textContent, innerText 사용
- **라이브러리 활용**: DOMPurify 등 클라이언트 사이드 정제 라이브러리
- **CSP 적용**: strict-dynamic 등 고급 CSP 정책 활용

## 실무 베스트 프랙티스

1. **다층 방어**: 입력 검증, 출력 인코딩, CSP 등 여러 방어 기법 조합
2. **화이트리스트 방식**: 허용된 입력만 받는 방식이 더 안전
3. **자동화된 검사**: SAST, DAST 도구로 정기적인 취약점 검사
4. **개발자 교육**: 보안 코딩 가이드라인 교육 및 코드 리뷰
5. **지속적 모니터링**: 공격 시도에 대한 실시간 모니터링 및 대응

SQL 인젝션과 XSS는 가장 일반적이면서도 위험한 웹 애플리케이션 취약점입니다. 체계적인 방어 기법을 적용하여 안전한 애플리케이션을 구축하는 것이 중요합니다.