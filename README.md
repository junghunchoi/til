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


### 1.2 Spring
- [스프링 프레임워크의 핵심 특징과, 다른 프레임워크와 비교했을 때의 장점](cs/Spring/spring-specification.md)
- 스프링 빈(Bean)의 생명주기와 스코프 종류 설명
- 스프링 컨테이너의 종류와 차이점(ApplicationContext vs BeanFactory)
- AOP(관점 지향 프로그래밍)의 개념과 실제 활용 사례
- @Component, @Service, @Repository, @Controller 등 스테레오타입 애노테이션의 차이점
- @Transactional 애노테이션의 동작 원리와 속성(propagation, isolation 등)
- 스프링 트랜잭션 관리 방식과 선언적/프로그래밍적 트랜잭션의 차이
- 스프링 부트의 주요 특징과 스프링 프레임워크와의 차이점
- 자동 구성(Auto Configuration)의 원리와 작동 방식
- 스프링 부트 프로파일 관리 방법과 환경별 설정 분리 방법
- 스프링 애플리케이션 성능 최적화 경험과 방법
- 캐싱 전략과 스프링의 캐시 추상화 사용법
- 대용량 트래픽 처리를 위한 스프링 기반 아키텍처 설계 방법


### 1.3 Jpa
- 스프링 JDBC와 JPA/Hibernate의 차이점과 각각의 장단점
- N+1 문제와 해결 방법
- [Transaction이란 무엇인가? @Transaction 애너테이션의 주요 옵션에 대해 설명](cs/spring/spring_transaction.md)
- [영속성 컨텍스트란 무엇인가?](cs/spring/spring_context.md)
- [N+1 문제와 해결책](cs/spring/spring_jpa_n1.md)
    - 패치조인과 조인의 차이점을 알고 계신가요?
- [JPA OSIV(Open Session In View) 설정](cs/spring/spring_jpa_osiv.md)


### 1.4 Java
- [**알아두면 좋을 JVM Option**](cs/java/jvm_options.md)
- [**JVM 옵션에서 Xmx, Xms를 동일하게 설정하는 이유**](cs/java/java_jvm_xmx_xms.md)
- [**Java의 Object 클래스의 Equals() 메서드와 HashCode() 메서드가 무슨 목적인지 설명해주세요.**](cs/java/java_equals_hascode.md)
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


### 1.5 database
- [SQL vs NoSQL , 하이브리드 아키텍처 설계](cs/database/db_sql_nosql.md)
    - [**RDBMS는 어떤 이유로 NoSQL보다 읽기 성능이 좋을까요? 반대로, NoSQL은 어떤 이유로 쓰기 성능이 좋을까요?
      **](cs/database/why_rdb_better_than_nosql_and_why_nosql_better_than_rdb..md)
    - [**RDBMS에서 데이터가 많이 적재되면 무슨 문제가 발생하나요?**](cs/database/db_rdb_too_many_data.md)
- [인메모리 DB가 더 빠른 이유](cs/database/db_inmemory.md)
- [정규화/반정규화](cs/database/db_normalization.md)
- [**인덱스란?**](cs/database/db_index.md)
    - [**인덱스가 있음에도 성능이 더 느린 경우가 있다. 이에 대해 설명해보시오.**](cs/database/db_index_invalid.md)
    - [**LIKE 연산자가 인덱스를 사용하지 않는 이유**](cs/database/like_not_using_index.md)
    - [DB 인덱스가 어떤 자료구조로 이루어져 있어서, 성능을 향상시키나요?](cs/database/db_index_data_structure.md)
- [MySQL InnoDB의 기본 격리 수준이 어떻게 될까요?](cs/database/db_default_isolation_level.md)
- [Dirty Read, Non-Repeatable Read, Phantom Read, Gap Lock](cs/database/db_mysql_gaplock.md)
- [**MySQL MVCC 방식과 Undo Log에 대해 설명해주세요.**](cs/database/db_mysql_mvcc.md)
- [비관적 락과 낙관적 락에 대해 설명해주세요.](cs/database/db_lock.md)

### 1.6 인프라/운영
- [**모니터링 및 Alert 구축 시 고려사항**](cs/infra/infra_monitoring_alert.md)
- [**쿼리 성능 개선: 인덱싱 및 실행 계획 분석**](cs/database/db_performence.md)
- [부하 테스트 및 TPS 분석 시 고려사항](cs/infra/infra_load_test_consider.md)
- [APM 활용 방안](cs/infra/infra_apm.md)
- [배포 전략: 카나리, 블루-그린, 롤링 배포 비교](cs/infra/infra_deploy.md)