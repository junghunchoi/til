# 학습정리

## 개요
노션이나 블로그 등 산발적으로 정리하여 머리속에 남는 내용이 없어서 이 프로젝트 내에서 학습한 내용을

정리하고자 합니다.

## 목차

1. CS
- 공통
- Spring
- Java
- database
- 인프라/운영

### 1.1 공통
## 디자인패턴
- [싱글톤 패턴](cs/common/designpattern/싱글턴-패턴.md)
- [팩토리 패턴](cs/common/designpattern/팩토리-패턴.md)
- [프록시 패턴]()
- [어댑터 패턴]()
- [옵저버 패턴]()
- [전략 패턴]()
- [템플릿 메소드 패턴]()
- [데코레이터 패턴]()


### 1.2 Spring
- [스프링 프레임워크의 핵심 특징과, 다른 프레임워크와 비교했을 때의 장점](cs/spring/spring-specification.md)
- [스프링 빈(Bean)의 생명주기와 실제 운영환경에서의 주의사항](cs/spring/spring-bean-lifecycle.md)
- [AOP(관점 지향 프로그래밍)의 개념과 실제 활용 사례](cs/spring/spring-aop.md)
- [@Transactional 애노테이션의 동작 원리와 속성(propagation, isolation 등)](cs/spring/spring-transactional.md)
- [스프링 부트 자동 구성(Auto Configuration)의 원리와 커스텀 구성](cs/spring/spring-boot-autoconfiguration.md)
- [스프링 WebFlux와 리액티브 프로그래밍 구현](cs/spring/spring-webflux.md)
- [Spring Security와 OAuth 2.0 구현 전략](cs/spring/spring-security-oauth.md)
- [스프링 애플리케이션 성능 최적화와 모니터링](cs/spring/spring-performance.md)
- [분산 캐싱 전략과 Redis 활용](cs/spring/spring-distributed-cache.md)
- [대용량 트래픽 처리를 위한 스프링 기반 아키텍처 설계](cs/spring/spring-scalable-architecture.md)
- [Spring Cloud를 활용한 마이크로서비스 구현](cs/spring/spring-cloud-microservices.md)
- [스프링 액추에이터를 통한 운영 모니터링](cs/spring/spring-actuator.md)
- [Spring IoC 컨테이너의 BeanFactory vs ApplicationContext 차이점과 사용 시점]()
- [@Profile과 @Conditional 어노테이션을 활용한 환경별 빈 관리 전략]()
- [Spring Boot의 ConfigurationProperties vs @Value 비교와 실무 활용법]()
- [Spring MVC의 DispatcherServlet 동작 과정과 커스터마이징 방법]()
- [Spring Boot Starter의 동작 원리와 커스텀 Starter 만들기]()
- [@Async의 내부 동작 원리와 ThreadPoolTaskExecutor 튜닝]()
- [Spring Event 기반 비동기 처리와 @EventListener 활용법]()
- [Spring Boot Test Slice 어노테이션들의 차이점과 적절한 사용법]()
- [Spring의 Validation 기능과 커스텀 Validator 구현]()
- [Spring Data JPA vs Spring Data JDBC 선택 기준]()

### 1.3 JPA/ORM
- [JPA와 Hibernate의 내부 동작 원리와 성능 튜닝](cs/jpa/jpa-hibernate-internals.md)
- [N+1 문제의 원인과 다양한 해결 방법](cs/jpa/jpa-n-plus-one.md)
- [영속성 컨텍스트와 1차 캐시의 동작 원리](cs/jpa/jpa-persistence-context.md)
- [JPA 쿼리 최적화 전략 (페치 조인, 배치 사이즈, 서브그래프)](cs/jpa/jpa-query-optimization.md)
- [JPA 2차 캐시와 분산 캐시 전략](cs/jpa/jpa-second-level-cache.md)
- [대용량 데이터 처리를 위한 JPA 배치 처리 기법](cs/jpa/jpa-batch-processing.md)
- [JPA OSIV(Open Session In View)와 성능 고려사항](cs/jpa/jpa-osiv.md)
- [복잡한 쿼리 처리: QueryDSL vs Criteria vs Native Query](cs/jpa/jpa-complex-queries.md)
- [JPA 상속 전략과 성능 비교](cs/jpa/jpa-inheritance-strategies.md)
- [JPA vs MyBatis vs Spring Data JDBC 비교와 선택 기준]()
- [@Entity vs @Embeddable vs @MappedSuperclass 사용법]()
- [JPA Auditing과 @CreatedDate, @LastModifiedDate 활용]()
- [JPA 벌크 연산(Bulk Operation)의 한계와 해결책]()
- [JPA Native Query vs JPQL vs Criteria API 성능 비교]()
- [Hibernate Envers를 활용한 엔티티 히스토리 관리]()
- [JPA 연관관계 매핑 시 주의사항 (양방향 매핑, 지연로딩)]()
- [Spring Data JPA의 Specification과 동적 쿼리 처리]()
- [JPA 멀티 데이터소스 설정과 트랜잭션 관리]()
- [JPA 성능 모니터링과 쿼리 분석 도구 활용법]()



### 1.4 Java
- [**알아두면 좋을 JVM Option**]()
- [**JVM 옵션에서 Xmx, Xms를 동일하게 설정하는 이유**]()
- [자료구조 및 Java 컬렉션. with 동적 크기 조정](cs/java/java_%EC%9E%90%EB%A3%8C%EA%B5%AC%EC%A1%B0_%EC%BB%AC%EB%A0%89%EC%85%98.md)
- [synchronized 와 ReentrantLock](cs/java/java_synchronized_ReentrantLock.md)
- [**Synchronized는 어떤 내부 원리로 락을 동작시키는지 설명해주세요.**(뮤텍스락)](cs/java/how_synchronized.md)
- [**ConcurrentHashMap과 Atomic 클래스들은 내부적으로 어떻게 동시성 이슈를 해결했는지 아시나요?**(CAS)](cs/java/how_concurrenthashmap.md)
- [ThreadLocal 이란?](cs/java/java_threadlocal.md)
- [GC 별 특징 및 구조, 동작 과정](cs/java/java_gc.md)
- [비동기 처리 및 스레드 관리](cs/java/java_async.md)
- [스레드 상태에 대해 설명해주세요. 혹시 TIMED_WATING이랑 WAITING의 차이를 아시나요?](cs/java/thread_status.md)
- [JDK 8과 JDK 17, JDK 21의 주요 차이](cs/java/java_8_17_21.md)
- [**Virtual Threads**](cs/java/java_virtual_thread.md)
- [**OutOfMemoryError의 원인을 분석할 수 있나요?**](cs/java/java_oom.md)
- [Record 클래스의 특징과 언제 사용해야 하는가?]()
- [Pattern Matching과 Switch Expression (Java 14+)]()
- [Text Blocks와 String 처리 최적화 (Java 15+)]()
- [Sealed Classes의 개념과 활용 사례 (Java 17+)]()
- [CompletableFuture와 비동기 프로그래밍 패턴]()
- [Method Reference와 Lambda 표현식의 성능 차이]()
- [Stream API 성능 최적화와 병렬 처리 주의사항]()
- [JVM 메모리 누수 탐지와 힙 덤프 분석 방법]()
- [JIT 컴파일러 최적화 이해와 성능 튜닝]()


### 1.5 Database
- [RDBMS vs NoSQL 특성과 하이브리드 아키텍처 설계](cs/database/db-comparison-architecture.md)
- [데이터베이스 인덱스 설계와 성능 최적화](cs/database/index최적화.md)
- [MySQL MVCC와 트랜잭션 격리 수준](cs/database/db-mysql-mvcc-isolation.md)
- [낙관적 락 vs 비관적 락 실제 구현과 성능 비교](cs/database/db-locking-strategies.md)
- [대용량 데이터 처리와 페이징 최적화](cs/database/대용량데이터처리와-페이징최적화.md)
- [데이터베이스 샤딩과 파티셔닝 전략](cs/database/db-sharding-partitioning.md)
- [복제(Replication)와 고가용성 설계](cs/database/db-replication-ha.md)
- [쿼리 실행 계획 분석과 성능 튜닝](cs/database/db-query-performance.md)
- [데드락 발생 원인과 해결 전략](cs/database/db-deadlock-solutions.md)
- [NoSQL 데이터베이스 선택과 설계 패턴](cs/database/db-nosql-patterns.md)
- [데이터베이스 정규화 1NF~5NF까지의 실무적 적용]()
- [트리거(Trigger) vs 애플리케이션 로직 처리 비교]()
- [저장 프로시저(Stored Procedure)의 장단점과 사용 시점]()
- [데이터베이스 백업 전략 (Full, Incremental, Differential)]()
- [MySQL vs PostgreSQL vs Oracle 특징과 선택 기준]()
- [Redis Cluster vs Redis Sentinel 고가용성 구성]()
- [데이터베이스 마이그레이션 전략 (Flyway, Liquibase)]()
- [데이터베이스 모니터링 지표와 성능 튜닝 포인트]()
- [ACID 속성의 실제 구현과 CAP 정리]()
- [분산 데이터베이스의 Eventually Consistency 구현]()

### 1.6 마이크로서비스 & 분산 시스템
- [마이크로서비스 아키텍처 설계와 도메인 분리](cs/microservices/microservices-architecture.md)
- [분산 트랜잭션 패턴 (Saga, 2PC, 이벤트 소싱)](cs/microservices/distributed-transactions.md)
- [서킷 브레이커와 장애 격리 패턴](cs/microservices/circuit-breaker-patterns.md)
- [이벤트 드리븐 아키텍처와 메시지 큐](cs/microservices/event-driven-architecture.md)
- [API 게이트웨이와 서비스 메시(Service Mesh)](cs/microservices/api-gateway-service-mesh.md)
- [분산 시스템에서의 데이터 일관성 패턴](cs/microservices/distributed-data-consistency.md)

### 1.7 인프라/운영
- [애플리케이션 모니터링과 성능 튜닝](cs/infra/infra-monitoring-performance.md)
- [부하 테스트와 용량 계획](cs/infra/infra-load-testing.md)
- [무중단 배포 전략과 실제 구현](cs/infra/infra-zero-downtime-deployment.md)
- [컨테이너 오케스트레이션과 Kubernetes](cs/infra/infra-kubernetes.md)
- [CI/CD 파이프라인 설계와 자동화](cs/infra/infra-cicd-pipeline.md)
- [장애 대응과 복구 전략](cs/infra/infra-disaster-recovery.md)
- [대용량 트래픽 처리를 위한 시스템 아키텍처 설계]()
- [캐시 전략 (로컬 캐시, 분산 캐시, CDN) 설계와 구현]()
- [메시지 큐 시스템 (RabbitMQ, Kafka, Redis Pub/Sub) 비교와 활용]()
- [검색 엔진 (Elasticsearch, Solr) 설계와 최적화]()
- [실시간 데이터 처리 아키텍처 (Lambda, Kappa Architecture)]()
- [로드밸런싱 전략과 Health Check 구현]()
- [분산 락과 세마포어 구현 패턴]()
- [비동기 처리와 Job Queue 시스템 설계]()
- [Docker 컨테이너 최적화와 멀티스테이지 빌드]()
- [로그 수집과 중앙 집중식 로깅 시스템]()
- [메트릭 수집과 대시보드 구성 (Prometheus, Grafana)]()


### 1.8 보안
- [애플리케이션 보안과 OWASP Top 10](cs/security/security-owasp.md)
- [인증과 인가 시스템 설계 (OAuth 2.0, JWT)](cs/security/security-auth-authorization.md)
- [SQL 인젝션과 XSS 방어 기법](cs/security/security-injection-prevention.md)
- [HTTPS와 TLS 구현](cs/security/security-https-tls.md)

### 1.9 테스팅
- [테스트 전략과 테스트 피라미드](cs/testing/testing-strategy-pyramid.md)
- [통합 테스트와 컨트랙트 테스트](cs/testing/testing-integration-contract.md)
- [성능 테스트 자동화](cs/testing/testing-performance-automation.md)

### 1.10 개발문화
- [도메인 주도 설계(DDD)와 헥사고날 아키텍처]()
- [클린 아키텍처와 SOLID 원칙 실무 적용]()
- [리팩토링 기법과 레거시 코드 개선 전략]()
- [코드 리뷰 문화와 품질 관리]()
- [기술 부채 관리와 우선순위 결정]()
- [문서화 전략과 Knowledge Management]()
- [개발 생산성 향상을 위한 도구와 자동화]()