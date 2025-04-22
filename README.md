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
- 스프링 프레임워크의 핵심 특징과, 다른 프레임워크와 비교했을 때의 장점
- IoC(제어의 역전)와 DI(의존성 주입)의 개념과 차이점 설명
- 스프링 빈(Bean)의 생명주기와 스코프 종류 설명
- 스프링 컨테이너의 종류와 차이점(ApplicationContext vs BeanFactory)
- AOP(관점 지향 프로그래밍)의 개념과 실제 활용 사례
- @Component, @Service, @Repository, @Controller 등 스테레오타입 애노테이션의 차이점
- @Autowired, @Qualifier, @Resource, @Inject 등 의존성 주입 관련 애노테이션 비교
- @Transactional 애노테이션의 동작 원리와 속성(propagation, isolation 등)
- 스프링 JDBC와 JPA/Hibernate의 차이점과 각각의 장단점
- 스프링 트랜잭션 관리 방식과 선언적/프로그래밍적 트랜잭션의 차이
- N+1 문제와 해결 방법
- 스프링 부트의 주요 특징과 스프링 프레임워크와의 차이점
- 자동 구성(Auto Configuration)의 원리와 작동 방식
- 스프링 부트 프로파일 관리 방법과 환경별 설정 분리 방법
- 스프링 애플리케이션 성능 최적화 경험과 방법
- 캐싱 전략과 스프링의 캐시 추상화 사용법
- 대용량 트래픽 처리를 위한 스프링 기반 아키텍처 설계 방법



### 1.3 Java


### 1.4 database


### 1.5 인프라/운영



<details>
<summary>@Async 학습 [2025.03.24]</summary>

CompletableFuture 를 반환하자
- 작업의 완료여부 체크가 가능하다.
- 비동기의 예외는 호출자에게 반환되지 않을 수 있기때문
- 체이닝 메소드(thenApply, thenAccept, thenRun)를 통해 비동기 작업을 순차적으로 처리할 수 있다.
- 명시적으로 작업 완료를 할 수 있다. (get(), join())
- config는 기본적으로 작성한 클래스를 참고하고 필요에 따라 적절히 수정하는 방식으로

</details>