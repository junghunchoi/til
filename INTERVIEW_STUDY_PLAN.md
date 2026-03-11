# 중견기업 이직 기술 면접 준비 플랜 (6년차 Java/Spring)

> 목표: 중견기업 백엔드 엔지니어 포지션 합격
> 기간: 8주 집중 학습
> 기준: 면접 출제 빈도 × 현재 약점 = 우선순위

---

## 📊 현재 역량 자가 진단 체크리스트

학습을 시작하기 전에 아래 항목에서 자신 있게 답할 수 있는지 체크해보세요.
체크가 안 되는 항목이 집중 학습 대상입니다.

### Java / JVM 영역
- [ ] JVM의 메모리 구조(Heap, Stack, Method Area)를 그림으로 그릴 수 있다
- [ ] OOM(OutOfMemoryError) 유형별 원인과 진단 방법을 알고 있다

### Java 동시성 영역[아예 나중에 학습하기]
- [ ] volatile이 가시성만 보장하고 원자성을 보장하지 않는 이유를 설명할 수 있다
- [ ] Double-Checked Locking에서 volatile이 왜 필요한지 코드로 설명할 수 있다
- [ ] ReentrantLock이 synchronized보다 추가로 제공하는 기능을 3가지 말할 수 있다
- [ ] ThreadLocal의 메모리 누수가 WAS 환경에서 발생하는 시나리오를 설명할 수 있다
- [ ] ThreadPoolExecutor에 작업이 추가될 때의 4단계 흐름을 설명할 수 있다
- [ ] Virtual Thread의 Pinning 문제와 해결 방법을 설명할 수 있다

### Spring 영역
- [ ] BeanFactory와 ApplicationContext의 차이를 설명할 수 있다
- [ ] Spring의 순환 의존성을 어떻게 해결하는지 개념 수준으로 설명할 수 있다
- [ ] JDK Dynamic Proxy와 CGLIB Proxy의 차이와 선택 기준을 설명할 수 있다
- [ ] @Transactional 셀프 호출이 왜 동작하지 않는지 근본 원인을 설명할 수 있다
- [ ] Spring Boot Auto-Configuration의 동작 순서를 설명할 수 있다
- [ ] @Transactional 전파 속성(REQUIRED, REQUIRES_NEW, NESTED)을 설명할 수 있다

### JPA / Database 영역
- [ ] 영속성 컨텍스트의 1차 캐시와 쓰기 지연이 어떻게 동작하는지 설명할 수 있다
- [ ] N+1 문제의 5가지 해결 방법을 설명할 수 있다
- [ ] MVCC 동작 원리와 각 격리 수준별 현상(Dirty Read, Non-Repeatable Read, Phantom Read)을 설명할 수 있다
- [ ] 낙관적 락과 비관적 락을 언제 선택해야 하는지 설명할 수 있다
- [ ] 커버링 인덱스가 무엇이고 어떤 경우에 성능 향상이 되는지 설명할 수 있다

### Redis / 캐싱 영역
- [ ] Cache-Aside 패턴이 무엇인지, 언제 사용하는지 설명할 수 있다
- [ ] Cache Stampede, Cache Penetration, Cache Avalanche 각각의 원인과 대응 방법을 설명할 수 있다
- [ ] Redis의 주요 자료구조(String, Hash, List, Set, Sorted Set)와 사용 사례를 설명할 수 있다

### 네트워크 / HTTP 영역
- [ ] HTTP 1.1과 HTTP 2.0의 핵심 차이(커넥션 재사용, 멀티플렉싱)를 설명할 수 있다
- [ ] HTTPS의 TLS 핸드쉐이크 과정을 간략히 설명할 수 있다
- [ ] RESTful API 설계 원칙(무상태, 자원 기반 URL, HTTP 메서드 활용)을 설명할 수 있다

### 시스템 설계 영역
- [ ] 대용량 트래픽에서 DB 병목을 해소하는 전략을 3가지 이상 설명할 수 있다

---

## 📅 8주 주차별 학습 로드맵

### 1주차: Java 동시성/멀티스레딩 완전 정복 ⭐ 최우선

> 이유: 6년차 면접 탈락 1위 영역. 대용량 트래픽 경험 없을 때 동시성 지식으로 상쇄 가능.

**학습 파일:**
- [ ] `cs/java/java-concurrency-threading.md` ← 핵심

**1주차 완료 기준:**
- [ ] volatile과 synchronized의 차이를 happens-before로 설명할 수 있다
- [ ] DCL 패턴 코드를 직접 작성하고 volatile이 필요한 이유를 설명할 수 있다
- [ ] ThreadLocal 메모리 누수 시나리오를 WAS 스레드 풀 재사용 관점으로 설명할 수 있다
- [ ] ThreadPoolExecutor에 작업이 추가되는 4단계 흐름을 그릴 수 있다
- [ ] ConcurrentHashMap이 HashMap보다 안전한 이유를 설명할 수 있다 (CAS + 버킷 단위 동기화)
- [ ] Virtual Thread의 Pinning 문제를 코드 예시와 해결책으로 설명할 수 있다
- [ ] 데드락의 4가지 발생 조건(Coffman Conditions)을 나열할 수 있다

---

### 2주차: JVM 내부 아키텍처 ⭐⭐

> 이유: 다른 모든 Java 주제의 기반. 꼬리질문에서 JVM을 모르면 막힘.

**학습 파일:**
- [ ] `cs/java/jvm-architecture.md` ← 핵심
- [ ] `cs/java/jvm-options.md` (복습)
- [ ] `cs/java/jvm-xmx-xms-same.md` (복습)

**2주차 완료 기준:**
- [ ] JVM 전체 구조도를 그림으로 그릴 수 있다 (ClassLoader → Runtime Data Area → Execution Engine)
- [ ] 클래스 로딩 3단계(Loading/Linking/Initialization)를 순서대로 설명할 수 있다
- [ ] Bootstrap/Extension/Application ClassLoader가 각각 무엇을 로드하는지 말할 수 있다
- [ ] PermGen과 Metaspace의 차이를 말할 수 있다
- [ ] `String s = "hello"`와 `new String("hello")`의 메모리 차이를 설명할 수 있다
- [ ] JIT 컴파일러가 무엇인지, 인터프리터와의 차이를 설명할 수 있다 (C1/C2 세부 불필요)

---

### 3주차: Spring 내부 동작 원리 ⭐⭐

> 이유: "Spring을 6년 써왔다면 내부를 설명할 수 있어야 한다"는 시니어 필수 질문.

**학습 파일:**
- [ ] `cs/spring/spring-ioc-proxy-internal.md` ← 핵심
- [ ] `cs/spring/spring-bean-lifecycle.md` (복습)
- [ ] `cs/spring/spring-aop.md` (복습)
- [ ] `cs/spring/spring-transactional.md` (복습)

**3주차 완료 기준:**
- [ ] BeanDefinition이 등록되고 Bean Instance가 생성되는 전체 흐름을 설명할 수 있다
- [ ] Spring이 순환 의존성을 해결하는 원리를 개념 수준으로 설명할 수 있다 (3단계 캐시 이름 암기보다 원리 이해)
- [ ] JDK Proxy와 CGLIB Proxy의 생성 조건과 제약을 설명할 수 있다
- [ ] @Transactional 셀프 호출 문제의 근본 원인과 3가지 해결책을 설명할 수 있다
- [ ] Auto-Configuration의 @ConditionalOnMissingBean 동작을 설명할 수 있다
- [ ] CGLIB 프록시에서 final 클래스/메서드가 문제 되는 이유를 설명할 수 있다

---

### 4주차: GC 알고리즘과 기본 튜닝 ⭐⭐

> 이유: 운영 경험을 묻는 질문에서 GC 이론으로 대응 가능.

**학습 파일:**
- [ ] `cs/java/garbage-collection.md` ← 핵심

**4주차 완료 기준:**
- [ ] GC의 Major/Minor/Full GC 차이를 설명할 수 있다
- [ ] G1GC의 Heap Region 구조와 Young GC/Mixed GC가 트리거되는 조건을 설명할 수 있다
- [ ] GC 로그를 보고 Pause Time을 읽고 분석할 수 있다
- [ ] OOM 발생 시 Heap Dump를 생성하고 분석하는 절차를 설명할 수 있다
- [ ] G1GC를 언제 선택하는지 latency/throughput 관점으로 설명할 수 있다
- [ ] ZGC/Shenandoah는 저레이턴시를 위한 GC라는 것을 알고 특징을 간단히 설명할 수 있다 (내부 메커니즘 불필요)

---

### 5주차: JPA / Hibernate 심화 복습

**학습 파일 (기존):**
- [ ] `cs/jpa/jpa-persistence-context.md` (복습)
- [ ] `cs/jpa/N+1 문제의 원인과 다양한 해결 방법.md` (복습)
- [ ] `cs/database/db-locking-strategies.md` (복습)
- [ ] `cs/database/db-mysql-mvcc-isolation.md` (복습)

**5주차 완료 기준:**
- [ ] EntityManager의 생명주기를 4가지 상태로 설명할 수 있다
- [ ] @BatchSize, EntityGraph, Fetch Join, Subselect의 차이를 설명할 수 있다
- [ ] 낙관적 락과 비관적 락의 선택 기준을 실제 시나리오로 설명할 수 있다
- [ ] REPEATABLE READ 격리 수준에서 MySQL InnoDB가 Phantom Read를 막는 방법을 설명할 수 있다

---

### 6주차: 데이터베이스 / 인덱스 심화 복습

**학습 파일 (기존):**
- [ ] `cs/database/index최적화.md` (복습)
- [ ] `cs/database/대용량데이터처리와-페이징최적화.md` (복습)

**6주차 완료 기준:**
- [ ] B-Tree 인덱스의 구조와 범위 스캔이 가능한 이유를 설명할 수 있다
- [ ] 커버링 인덱스가 성능을 향상시키는 원리를 설명할 수 있다
- [ ] OFFSET 기반 페이징의 성능 문제와 Cursor 기반 페이징 해결책을 설명할 수 있다
- [ ] 인덱스가 있어도 사용 안 되는 케이스 3가지를 설명할 수 있다

---

### 7주차: Redis 캐싱 + 시스템 설계 복습 ⭐ (중견기업 빈출)

> 이유: Redis는 현재 사실상 표준 캐싱 솔루션. 실무 경험과 이론 모두 필수.

**학습 파일:**
- [ ] `cs/microservices/large-scale-traffic-handling.md` (복습)
- [ ] `cs/microservices/circuit-breaker-patterns.md` (복습)
- [ ] `cs/microservices/event-driven-architecture.md` (복습)
- [ ] `cs/java/modern-java-multithreading.md` (복습)
- [ ] `cs/database/db-redis-caching.md` (신규 작성 필요) ← 핵심 추가

**7주차 완료 기준:**
- [ ] Cache-Aside, Write-Through, Write-Behind 패턴의 차이를 설명할 수 있다
- [ ] Cache Stampede/Penetration/Avalanche 각 문제와 해결 방법을 설명할 수 있다
- [ ] Redis 주요 자료구조(String, Hash, List, Set, Sorted Set)별 적합한 사용 사례를 설명할 수 있다
- [ ] 대용량 트래픽 대응 전략을 Scale-Out/캐싱/DB 분리/비동기로 설명할 수 있다
- [ ] Circuit Breaker의 3가지 상태와 전환 조건을 설명할 수 있다
- [ ] 이벤트 메시지의 중복 처리(Idempotency)를 어떻게 보장하는지 설명할 수 있다

---

### 8주차: 모의 면접 + 선택 주제 정리

**활동:**
- [ ] 각 파일의 면접 Q&A를 보지 않고 답변해보기 (셀프 모의 면접)
- [ ] 약점으로 남은 항목 집중 복습
- [ ] 면접 직전 점검 리스트 (아래) 완료

**여유 있을 경우 추가 학습 (출제 빈도 중간):**
- [ ] `cs/security/security-auth-authorization.md` → JWT vs Session 차이, Spring Security Filter Chain
- [ ] HTTP/네트워크 기초: HTTP 1.1 vs 2.0, HTTPS TLS 핸드쉐이크, TCP 3-way handshake
- [ ] Docker 기초: 이미지/컨테이너 차이, Dockerfile 작성, 기본 명령어
- [ ] `cs/testing/testing-strategy-pyramid.md` → 테스트 전략과 테스트 피라미드

---

## 🎯 면접 직전 1시간 최종 점검 리스트

### Java/JVM 핵심 (20분)
```
□ JVM 메모리 구조 (힙/스택/메서드 영역 구분)
□ GC: STW → G1GC Region → Mixed GC 트리거 조건
□ volatile: 가시성 보장, 원자성 미보장
□ synchronized: 모니터 락, 재진입 가능
□ ThreadLocal: remove() 필수, WAS 환경 누수 위험
□ Virtual Thread: Pinning 문제 (synchronized 블로킹)
□ ThreadPoolExecutor: core → queue → max → reject 4단계
```

### Spring 핵심 (20분)
```
□ BeanDefinition = 빈의 설계도
□ 순환 의존성: 스프링이 3단계 캐시로 미완성 빈을 먼저 주입해서 해결
□ JDK Proxy: 인터페이스 필요 / CGLIB: 상속 기반, final 불가
□ @Transactional 셀프 호출: 프록시를 거치지 않음
□ Auto-Configuration: @ConditionalOnMissingBean으로 커스터마이징 허용
□ @Transactional 전파 속성: REQUIRED / REQUIRES_NEW / NESTED
```

### JPA/Database 핵심 (10분)
```
□ N+1: 즉시/지연 로딩과 무관, 해결: Fetch Join/EntityGraph/@BatchSize
□ 영속성 컨텍스트: 1차 캐시, 변경 감지, 쓰기 지연
□ 낙관적 락: @Version, 충돌 시 OptimisticLockException
□ MVCC: Undo Log로 스냅샷 읽기, Phantom Read 처리
□ 인덱스: B-Tree, 커버링 인덱스, 인덱스 미사용 케이스
```

### Redis/시스템 설계 핵심 (10분)
```
□ Cache-Aside: DB 읽기 전에 캐시 확인, Miss 시 DB → 캐시 저장
□ Cache Stampede: 동시 만료 → 뮤텍스 또는 TTL 랜덤화
□ Cache Penetration: 없는 키 반복 조회 → Null 캐싱 or Bloom Filter
□ 트래픽 급증 대응: 캐싱 → 비동기 → Scale-Out → DB 분리
□ Circuit Breaker: Closed → Open → Half-Open
```

---

## 📁 학습 파일 전체 맵

```
cs/
├── java/
│   ├── java-concurrency-threading.md  ← 1주차 (NEW) ⭐⭐⭐
│   ├── jvm-architecture.md             ← 2주차 (NEW) ⭐⭐
│   ├── garbage-collection.md           ← 4주차 (NEW) ⭐⭐
│   ├── modern-java-multithreading.md   ← 7주차 복습
│   ├── java_8_17_21.md
│   ├── java-record.md
│   ├── sealed-classes.md
│   ├── pattern-matching-switch.md
│   ├── text-blocks-string-optimization.md
│   ├── jvm-options.md                  ← 2주차 복습
│   └── jvm-xmx-xms-same.md            ← 2주차 복습
│
├── spring/
│   ├── spring-ioc-proxy-internal.md   ← 3주차 (NEW) ⭐⭐
│   ├── spring-bean-lifecycle.md        ← 3주차 복습
│   ├── spring-aop.md                   ← 3주차 복습
│   ├── spring-transactional.md         ← 3주차 복습
│   ├── spring-webflux.md
│   └── spring-event.md
│
├── jpa/
│   ├── jpa-persistence-context.md     ← 5주차 복습
│   ├── N+1 문제의 원인과 다양한 해결 방법.md ← 5주차 복습
│   ├── 왜 엔티티는 표현계층에 전달하면 안될까.md
│   └── jpa에서 record를 사용하지 않는 이유.md
│
├── database/
│   ├── db-locking-strategies.md       ← 5주차 복습
│   ├── db-mysql-mvcc-isolation.md     ← 5주차 복습
│   ├── db-redis-caching.md            ← 7주차 (NEW 작성 필요) ⭐⭐
│   ├── index최적화.md                  ← 6주차 복습
│   └── 대용량데이터처리와-페이징최적화.md ← 6주차 복습
│
├── microservices/
│   ├── large-scale-traffic-handling.md  ← 7주차 복습
│   ├── circuit-breaker-patterns.md      ← 7주차 복습
│   ├── event-driven-architecture.md     ← 7주차 복습
│   ├── distributed-transactions.md      (선택)
│   └── microservices-architecture.md
│
├── security/
│   ├── security-auth-authorization.md   ← 8주차 선택
│   ├── security-injection-prevention.md
│   └── security-owasp.md
│
├── testing/
│   ├── testing-strategy-pyramid.md      ← 8주차 선택
│   └── testing-integration-contract.md
│
├── common/
│   └── designpattern/ (9개 파일)
│
└── dev-culture/
    ├── clean-architecture-solid.md
    ├── ddd-hexagonal-architecture.md
    └── code-review-culture.md
```

---

## 💡 면접 답변 전략

### 기술 깊이 부족 시 대응법
> "대용량 트래픽을 직접 경험하지 않았지만, 해당 문제를 해결하기 위한 기술적 접근 방법을 이론적으로 공부했습니다. 예를 들어 G1GC 튜닝에서 `-XX:MaxGCPauseMillis` 조정과 GC 로그 분석 방법을 숙지하고 있습니다."

### 모르는 질문이 나왔을 때
> 모른다고 바로 포기하지 말고 "정확히 구현 세부 사항은 확인이 필요하지만, 제가 이해하는 원리는 이러합니다..."로 접근

### 꼬리질문 대응
> 꼬리질문은 내가 말한 키워드에서 나온다. 자신 없는 키워드는 설명에서 꺼내지 말 것.

---

## 🔗 학습 리소스

| 주제 | 추천 자료 |
|------|---------|
| JVM 내부 | "JVM Internals" by James D Bloom |
| GC 튜닝 | Oracle G1GC 공식 문서, GC 로그 분석 도구 (GCViewer, GCEasy) |
| 동시성 | "Java Concurrency in Practice" (Brian Goetz) |
| Spring 내부 | Spring Framework 공식 소스코드 (GitHub) |
| 시스템 설계 | "Designing Data-Intensive Applications" (Martin Kleppmann) |
| Redis | Redis 공식 문서, "Redis in Action" |
