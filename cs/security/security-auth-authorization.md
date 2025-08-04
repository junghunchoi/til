# 인증과 인가 시스템 설계 (OAuth 2.0, JWT)

## 인증 vs 인가 개념

### Authentication (인증) vs Authorization (인가)
```java
// 인증: "당신이 누구인지 확인"
@PostMapping("/login")
public ResponseEntity<AuthResponse> authenticate(@RequestBody LoginRequest request) {
    // 사용자 신원 확인
    User user = userService.validateCredentials(request.getUsername(), request.getPassword());
    if (user != null) {
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
}

// 인가: "당신이 이 리소스에 접근할 권한이 있는지 확인"
@GetMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")  // 인가 체크
public List<User> getUsers() {
    return userService.getAllUsers();
}
```

## JWT (JSON Web Token) 구현

### JWT 구조와 생성
```java
@Component
public class JwtService {
    
    @Value("${jwt.secret}")
    private String secretKey;
    
    @Value("${jwt.expiration}")
    private Long jwtExpiration;
    
    // JWT 토큰 생성
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("roles", user.getRoles().stream()
                               .map(Role::getName)
                               .collect(Collectors.toList()));
        
        return Jwts.builder()
                  .setClaims(claims)
                  .setSubject(user.getUsername())
                  .setIssuedAt(new Date(System.currentTimeMillis()))
                  .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                  .signWith(SignatureAlgorithm.HS512, secretKey)
                  .compact();
    }
    
    // JWT 토큰 검증
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    
    // 토큰에서 사용자명 추출
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    // 토큰에서 만료일 추출
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    // 토큰에서 특정 클레임 추출
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                  .setSigningKey(secretKey)
                  .parseClaimsJws(token)
                  .getBody();
    }
    
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
```

### JWT 필터 구현
```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        jwt = authHeader.substring(7);
        username = jwtService.extractUsername(jwt);
        
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            if (jwtService.validateToken(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

### Refresh Token 구현
```java
@Service
public class AuthService {
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    
    public AuthResponse authenticate(LoginRequest request) {
        // 사용자 인증
        User user = validateUser(request);
        
        // Access Token 생성 (짧은 만료 시간)
        String accessToken = jwtService.generateToken(user);
        
        // Refresh Token 생성 (긴 만료 시간)  
        RefreshToken refreshToken = createRefreshToken(user.getId());
        
        return AuthResponse.builder()
                          .accessToken(accessToken)
                          .refreshToken(refreshToken.getToken())
                          .tokenType("Bearer")
                          .expiresIn(jwtService.getJwtExpiration())
                          .build();
    }
    
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository
            .findByToken(request.getRefreshToken())
            .orElseThrow(() -> new TokenException("Refresh token not found"));
            
        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenException("Refresh token expired");
        }
        
        User user = refreshToken.getUser();
        String newAccessToken = jwtService.generateToken(user);
        
        return AuthResponse.builder()
                          .accessToken(newAccessToken)
                          .refreshToken(refreshToken.getToken())
                          .tokenType("Bearer")
                          .build();
    }
    
    private RefreshToken createRefreshToken(Long userId) {
        RefreshToken refreshToken = RefreshToken.builder()
            .user(userRepository.findById(userId).get())
            .token(UUID.randomUUID().toString())
            .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
            .build();
            
        return refreshTokenRepository.save(refreshToken);
    }
}
```

## OAuth 2.0 구현

### Authorization Code Flow
```java
// OAuth 2.0 설정
@Configuration
@EnableWebSecurity
public class OAuth2Config {
    
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(
            googleClientRegistration(),
            githubClientRegistration()
        );
    }
    
    private ClientRegistration googleClientRegistration() {
        return ClientRegistration.withRegistrationId("google")
            .clientId("your-google-client-id")
            .clientSecret("your-google-client-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/oauth2/callback/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://www.googleapis.com/oauth2/v4/token")
            .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .clientName("Google")
            .build();
    }
}

// OAuth2 성공 핸들러
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    
    @Autowired
    private JwtService jwtService;
    
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                      HttpServletResponse response,
                                      Authentication authentication) throws IOException {
        
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        
        // OAuth2 사용자 정보로 내부 사용자 생성/업데이트
        User user = processOAuth2User(oAuth2User);
        
        // JWT 토큰 생성
        String token = jwtService.generateToken(user);
        
        // 프론트엔드로 리다이렉트 (토큰 포함)
        String targetUrl = "http://localhost:3000/oauth2/redirect?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
    
    private User processOAuth2User(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        
        Optional<User> userOptional = userRepository.findByEmail(email);
        
        if (userOptional.isPresent()) {
            // 기존 사용자 업데이트
            User existingUser = userOptional.get();
            existingUser.setName(name);
            return userRepository.save(existingUser);
        } else {
            // 새 사용자 생성
            User newUser = User.builder()
                              .email(email)
                              .name(name)
                              .provider(AuthProvider.GOOGLE)
                              .providerId(oAuth2User.getAttribute("sub"))
                              .build();
            return userRepository.save(newUser);
        }
    }
}
```

### Resource Server 구현
```java
// OAuth2 Resource Server 설정
@Configuration
@EnableResourceServer
public class ResourceServerConfig extends ResourceServerConfigurerAdapter {
    
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
            .antMatchers("/api/public/**").permitAll()
            .antMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
            .antMatchers(HttpMethod.POST, "/api/posts/**").hasRole("USER")
            .antMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
            .and()
            .oauth2ResourceServer()
            .jwt();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        // JWT 검증을 위한 디코더 설정
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
            .withJwkSetUri("https://your-auth-server/.well-known/jwks.json")
            .build();
            
        // 추가 검증 로직
        jwtDecoder.setJwtValidator(jwtValidator());
        return jwtDecoder;
    }
    
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // JWT에서 권한 정보 추출
            List<String> authorities = jwt.getClaimAsStringList("authorities");
            return authorities.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
        });
        return converter;
    }
}
```

## Spring Security 통합

### 세큐리티 설정
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Autowired
    private AuthenticationEntryPoint authenticationEntryPoint;
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/posts/**").hasRole("USER")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint)
            .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .oauth2Login()
                .successHandler(oAuth2AuthenticationSuccessHandler)
                .failureHandler(oAuth2AuthenticationFailureHandler);
            
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

### 커스텀 UserDetailsService
```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                                 .orElseThrow(() -> 
                                     new UsernameNotFoundException("User not found: " + username));
        
        return UserPrincipal.create(user);
    }
    
    @Transactional
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                                 .orElseThrow(() -> 
                                     new UsernameNotFoundException("User not found: " + id));
        
        return UserPrincipal.create(user);
    }
}

// 커스텀 UserDetails 구현
public class UserPrincipal implements UserDetails {
    private Long id;
    private String email;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;
    
    public UserPrincipal(Long id, String email, String password, 
                        Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }
    
    public static UserPrincipal create(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
            .collect(Collectors.toList());
            
        return new UserPrincipal(
            user.getId(),
            user.getEmail(),
            user.getPassword(),
            authorities
        );
    }
    
    @Override
    public String getUsername() {
        return email;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    // getter methods...
}
```

## 고급 인가 패턴

### 메서드 레벨 보안
```java
@Service
public class PostService {
    
    // 역할 기반 접근 제어
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAllPosts() {
        postRepository.deleteAll();
    }
    
    // 표현식 기반 접근 제어
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id")
    public List<Post> getUserPosts(Long userId) {
        return postRepository.findByUserId(userId);
    }
    
    // 객체 레벨 보안
    @PreAuthorize("@postSecurityService.canEdit(authentication.principal.id, #postId)")
    public Post updatePost(Long postId, PostUpdateRequest request) {
        Post post = postRepository.findById(postId).orElseThrow();
        post.update(request);
        return postRepository.save(post);
    }
    
    // 필터링
    @PostFilter("hasRole('ADMIN') or filterObject.author.id == authentication.principal.id")
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }
}

@Component
public class PostSecurityService {
    
    public boolean canEdit(Long userId, Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        return post != null && post.getAuthor().getId().equals(userId);
    }
    
    public boolean canView(Long userId, Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        return post != null && (post.isPublic() || post.getAuthor().getId().equals(userId));
    }
}
```

### 동적 권한 관리
```java
@Entity
public class Permission {
    @Id
    private Long id;
    private String name;
    private String resource;
    private String action;
}

@Entity
public class Role {
    @Id
    private Long id;
    private String name;
    
    @ManyToMany(fetch = FetchType.EAGER)
    private Set<Permission> permissions = new HashSet<>();
}

// 동적 권한 평가
@Component
public class PermissionEvaluator implements org.springframework.security.access.PermissionEvaluator {
    
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || targetDomainObject == null || !(permission instanceof String)) {
            return false;
        }
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return hasPrivilege(userPrincipal, targetDomainObject.getClass().getSimpleName(), permission.toString());
    }
    
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetType == null || !(permission instanceof String)) {
            return false;
        }
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return hasPrivilege(userPrincipal, targetType, permission.toString());
    }
    
    private boolean hasPrivilege(UserPrincipal user, String resource, String action) {
        return user.getAuthorities().stream()
                  .anyMatch(authority -> hasPermission(authority.getAuthority(), resource, action));
    }
    
    private boolean hasPermission(String roleName, String resource, String action) {
        // 데이터베이스에서 역할의 권한 확인
        Role role = roleRepository.findByName(roleName.replace("ROLE_", ""));
        if (role == null) return false;
        
        return role.getPermissions().stream()
                  .anyMatch(permission -> 
                      permission.getResource().equals(resource) && 
                      permission.getAction().equals(action));
    }
}
```

## 보안 테스팅

### JWT 테스트
```java
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {
    
    @Mock
    private User user;
    
    @InjectMocks
    private JwtService jwtService;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secretKey", "testSecretKey");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
    }
    
    @Test
    void generateToken_Success() {
        // Given
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getUsername()).thenReturn("testuser");
        when(user.getRoles()).thenReturn(Collections.emptyList());
        
        // When
        String token = jwtService.generateToken(user);
        
        // Then
        assertThat(token).isNotNull();
        assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }
}
```

### Spring Security 테스트
```java
@SpringBootTest
@AutoConfigureTestDatabase
@TestMethodOrder(OrderAnnotation.class)
class AuthControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    @WithMockUser(roles = "USER")
    void getProfile_WithValidToken_Success() {
        ResponseEntity<UserProfile> response = restTemplate.getForEntity(
            "/api/auth/profile", UserProfile.class);
            
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    @Test
    void accessProtectedResource_WithoutToken_Unauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/protected", String.class);
            
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    
    @Test
    void accessAdminResource_WithUserRole_Forbidden() {
        // JWT 토큰 생성
        String token = createTokenForUser("USER");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/admin/users", HttpMethod.GET, entity, String.class);
            
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "JWT의 단점과 해결 방법은?"
**답변 포인트:**
- **토큰 무효화 어려움**: Blacklist, 짧은 만료 시간 + Refresh Token
- **토큰 크기**: 필요한 정보만 포함, 압축 고려
- **보안 이슈**: HTTPS 필수, XSS 방지를 위한 HttpOnly 쿠키 사용

### Q2: "OAuth 2.0의 다양한 Grant Type을 설명해보세요"
**답변 포인트:**
- **Authorization Code**: 웹 애플리케이션용 (가장 안전)
- **Implicit**: SPA용 (보안상 권장하지 않음)
- **Resource Owner Password**: 신뢰할 수 있는 클라이언트용
- **Client Credentials**: 서버 간 통신용

### Q3: "Session vs JWT, 언제 어떤 것을 사용해야 하나요?"
**답변 포인트:**
- **Session**: 전통적인 웹 애플리케이션, 서버 상태 관리 필요
- **JWT**: 마이크로서비스, SPA, 모바일 앱, 확장성 중요한 경우
- **하이브리드**: Refresh Token은 서버에 저장, Access Token은 JWT

## 실무 보안 고려사항

1. **토큰 저장**: XSS 방지를 위해 HttpOnly 쿠키 사용 권장
2. **CSRF 방지**: SameSite 쿠키 속성, CSRF 토큰 사용
3. **Rate Limiting**: 로그인 시도 횟수 제한
4. **로그인 기록**: 의심스러운 활동 모니터링
5. **비밀번호 정책**: 강력한 비밀번호 요구사항

인증과 인가는 모든 애플리케이션의 핵심 보안 요소입니다. 올바른 구현과 지속적인 보안 업데이트가 중요합니다.