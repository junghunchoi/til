# JVM 내부 아키텍처 완전 가이드

> 면접에서 "JVM이 어떻게 동작하나요?"라는 질문은 단순히 구조를 외우는 것이 아닌,
> 실제 동작 원리를 이해하고 연결해서 설명할 수 있어야 합니다.

---

## 1. JVM 전체 구조도

```
┌─────────────────────────────────────────────────────────────────┐
│                        JVM Architecture                          │
│                                                                   │
│  ┌─────────────────────┐    ┌──────────────────────────────────┐ │
│  │   Class Loader       │    │      Runtime Data Area            │ │
│  │   Subsystem          │    │                                    │ │
│  │                      │    │  ┌────────┐  ┌─────────────────┐ │ │
│  │  Bootstrap ──┐       │    │  │Method  │  │   Heap           │ │ │
│  │  Extension  ─┤ Load  │───▶│  │ Area   │  │  ┌───────────┐  │ │ │
│  │  Application─┘       │    │  │(공유)  │  │  │Young Gen  │  │ │ │
│  └─────────────────────┘    │  └────────┘  │  │ Eden/S0/S1│  │ │ │
│                               │              │  └───────────┘  │ │ │
│  ┌─────────────────────┐    │  ┌────────┐  │  ┌───────────┐  │ │ │
│  │  Execution Engine    │    │  │ Stack  │  │  │ Old Gen   │  │ │ │
│  │                      │    │  │(Thread │  │  └───────────┘  │ │ │
│  │  ┌──────────────┐   │    │  │ 별 독립)│  └─────────────────┘ │ │
│  │  │ Interpreter  │   │    │  └────────┘                       │ │
│  │  ├──────────────┤   │    │  ┌────────┐  ┌─────────────────┐ │ │
│  │  │ JIT Compiler │   │◀───│  │  PC    │  │ Native Method   │ │ │
│  │  │  C1 / C2     │   │    │  │Register│  │   Stack          │ │ │
│  │  └──────────────┘   │    │  └────────┘  └─────────────────┘ │ │
│  │  ┌──────────────┐   │    └──────────────────────────────────┘ │
│  │  │  GC          │   │                                          │
│  │  └──────────────┘   │    ┌──────────────────────────────────┐ │
│  └─────────────────────┘    │    Native Interface (JNI)         │ │
│                               └──────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 클래스 로더 시스템 (Class Loader Subsystem)

### 2.1 클래스 로딩의 3단계

클래스 파일(.class)이 JVM에 로드되는 과정은 3단계로 구분됩니다.

```
Loading → Linking (Verification → Preparation → Resolution) → Initialization
```

#### 1단계: Loading (로딩)
- 클래스 파일의 바이트코드를 읽어서 `java.lang.Class` 객체를 생성
- 바이트코드를 Method Area에 저장
- 세 가지 클래스 로더 중 하나가 담당

#### 2단계: Linking (링킹)

**Verification (검증)**
```
- 바이트코드가 JVM 스펙에 맞는지 확인
- 악의적으로 조작된 클래스 파일 방어
- 예: 스택 오버플로우를 유발하는 코드 패턴 탐지
```

**Preparation (준비)**
```java
// 이 단계에서 static 변수에 기본값(0, null, false) 할당
static int count;      // 0으로 초기화
static String name;    // null로 초기화
// 실제 값 할당은 Initialization 단계에서
```

**Resolution (해결)**
```
- 심볼릭 참조(클래스명, 메서드명)를 실제 메모리 주소로 교체
- 예: OrderService 클래스의 createOrder()가 실제 어디에 있는지 연결
```

#### 3단계: Initialization (초기화)
```java
class Config {
    // Preparation: value = 0
    // Initialization: value = 100 (실제 값 할당)
    static int value = 100;

    // static 초기화 블록도 이 단계에서 실행
    static {
        System.out.println("Config 클래스 초기화");
    }
}
```

---

### 2.2 클래스 로더 계층 구조 (Parent Delegation Model)

```
Bootstrap ClassLoader
    ↑ (parent)
Extension ClassLoader
    ↑ (parent)
Application ClassLoader
    ↑ (parent)
Custom ClassLoader
```

#### 각 클래스 로더의 역할

| 클래스 로더 | 로드 대상 | 구현 |
|-----------|---------|------|
| Bootstrap | rt.jar (java.lang.*, java.util.* 등 핵심 클래스) | JVM 내장 (Native) |
| Extension | lib/ext/*.jar (보안, XML 등) | sun.misc.Launcher$ExtClassLoader |
| Application | CLASSPATH의 클래스, 우리가 작성한 코드 | sun.misc.Launcher$AppClassLoader |

#### Parent Delegation의 동작 원리

```java
// ClassLoader.loadClass() 내부 동작 (의사코드)
protected Class<?> loadClass(String name, boolean resolve) {
    // 1. 이미 로드된 클래스인지 확인 (캐시)
    Class<?> c = findLoadedClass(name);

    if (c == null) {
        // 2. 부모 클래스 로더에게 먼저 위임 (핵심!)
        if (parent != null) {
            c = parent.loadClass(name, false);
        } else {
            // Bootstrap ClassLoader에게 위임
            c = findBootstrapClassOrNull(name);
        }
    }

    if (c == null) {
        // 3. 부모도 못 찾으면 직접 찾기
        c = findClass(name);
    }

    return c;
}
```

#### 왜 Parent Delegation Model이 필요한가?

**이유 1: 보안 (핵심 클래스 변조 방지)**
```
java.lang.String을 해커가 임의로 수정한 클래스로 교체하려 해도,
Bootstrap ClassLoader가 항상 먼저 처리하므로 교체 불가능
```

**이유 2: 중복 로딩 방지**
```
같은 클래스를 여러 클래스 로더가 중복으로 로드하면 ClassCastException 발생.
캐시 확인 후 위임하므로 한 번만 로드됨.
```

#### 실무에서 Parent Delegation을 깨는 경우

```
Tomcat의 WebappClassLoader:
- 서로 다른 웹앱이 같은 라이브러리의 다른 버전을 각각 사용 가능
- WebApp ClassLoader가 Application ClassLoader보다 먼저 로드 시도 (위임 역전)
- 각 웹앱이 독립적인 ClassLoader를 가짐

Spring Boot DevTools:
- 코드 변경 감지 후 해당 클래스만 재로드하기 위해 커스텀 ClassLoader 사용
```

---

## 3. Runtime Data Area (메모리 구조)

### 3.1 Method Area (메서드 영역) - 스레드 공유

```
Method Area에 저장되는 것:
├── 클래스 메타데이터 (클래스명, 접근제어자, 필드/메서드 정보)
├── static 변수
├── 상수 풀 (Constant Pool - 문자열 리터럴, 숫자 상수)
└── 메서드 바이트코드
```

#### Java 7 PermGen → Java 8 Metaspace 변경

```
Java 7 이전: PermGen (Permanent Generation)
- JVM 힙 내부에 고정 크기 영역
- -XX:MaxPermSize=256m 로 크기 제한
- OutOfMemoryError: PermGen space 오류 빈번
- String Pool이 PermGen에 위치 (GC 수집 어려움)

Java 8 이후: Metaspace
- JVM 힙 외부 Native Memory 사용
- OS가 허용하는 한 동적으로 확장
- -XX:MaxMetaspaceSize=256m 으로 제한 (기본값: 제한 없음 → 주의!)
- String Pool이 힙으로 이동 (GC 수집 가능)
```

```java
// Metaspace 누수가 발생하는 경우
// 동적으로 클래스를 생성하면서 ClassLoader를 해제하지 않을 때

// 예: CGLIB, Javassist, Byte Buddy 등으로 프록시를 계속 생성
// Spring에서 @Scope("prototype")으로 CGLIB 프록시를 대량 생성 시 주의
```

### 3.2 Heap (힙) - 스레드 공유

```
┌─────────────────────────────────────────────────┐
│                    Heap                           │
│                                                   │
│  ┌──────────────────────────────────────────┐   │
│  │           Young Generation                │   │
│  │  ┌──────────────┐  ┌───────┐  ┌───────┐ │   │
│  │  │     Eden     │  │  S0   │  │  S1   │ │   │
│  │  │ (새 객체 생성)│  │(From) │  │ (To)  │ │   │
│  │  └──────────────┘  └───────┘  └───────┘ │   │
│  └──────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────┐   │
│  │           Old Generation                   │   │
│  │  (Young에서 오래 살아남은 객체)             │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

**객체 생성과 이동 흐름:**
```
새 객체 → Eden 할당
Eden 꽉 참 → Young GC (Minor GC) 발생
  └─ 살아남은 객체 → Survivor (S0 또는 S1)
  └─ 오래 살아남은 객체 (age 임계값 초과) → Old Gen
Old Gen 꽉 참 → Full GC (Major GC) 발생
```

#### String Pool (Java 7+ 힙에 위치)

```java
String s1 = "hello";           // String Pool에 저장 (힙)
String s2 = "hello";           // Pool에서 s1과 같은 객체 반환
String s3 = new String("hello"); // 힙에 새 객체 생성 (Pool 미사용)
String s4 = s3.intern();       // Pool에 등록 후 Pool의 참조 반환

System.out.println(s1 == s2);  // true  (같은 Pool 객체)
System.out.println(s1 == s3);  // false (다른 객체)
System.out.println(s1 == s4);  // true  (intern()으로 Pool 참조)
```

### 3.3 Stack (스택) - 스레드별 독립

```
각 스레드마다 독립적인 Stack 생성
└── 각 메서드 호출마다 Stack Frame 생성
    ├── Local Variables (지역 변수, 파라미터)
    ├── Operand Stack (연산 중간 값)
    └── Frame Data (상수 풀 참조, 메서드 반환 정보)

메서드 반환 시 Stack Frame 제거
재귀 호출이 너무 깊으면 → StackOverflowError
-Xss 옵션으로 스택 크기 조정 (기본: 512k~1MB)
```

```java
// 지역 변수는 Stack에, 객체 본체는 Heap에
void process() {
    int count = 0;              // Stack에 값 저장
    String name = "hello";      // Stack에 참조값, Heap에 실제 String 객체
    List<String> list = new ArrayList<>();  // Stack에 참조값, Heap에 ArrayList
}
```

### 3.4 PC Register와 Native Method Stack - 스레드별 독립

```
PC Register:
- 현재 실행 중인 JVM 명령어 주소를 저장
- 멀티스레딩에서 컨텍스트 스위칭 시 복원 용도

Native Method Stack:
- Java가 아닌 Native 메서드(C, C++) 실행 시 사용
- JNI(Java Native Interface) 관련
```

---

## 4. Execution Engine (실행 엔진)

### 4.1 인터프리터 vs JIT 컴파일러

```
처음 실행: 인터프리터 (바이트코드를 한 줄씩 해석)
           ↓ 느리지만 즉시 실행 가능
Hot Method 감지: 특정 메서드가 자주 호출되면 JIT 컴파일
           ↓ 처음 컴파일 비용이 있지만 이후 빠름
컴파일된 네이티브 코드 실행: 인터프리터 대비 10~100배 빠름
```

**Hotspot VM의 이름 유래:**
> 프로그램 실행 시간의 90%가 전체 코드의 10%에 집중된다는 법칙.
> 그 "Hot한 Spot"을 찾아서 최적화하기 때문에 Hotspot VM이라고 명명.

### 4.2 JIT 컴파일러의 Tiered Compilation

```
Level 0: 인터프리터
Level 1: C1 (단순 최적화, 빠른 컴파일)
Level 2: C1 (제한된 프로파일링 포함)
Level 3: C1 (완전한 프로파일링)
Level 4: C2 (공격적인 최적화, 느린 컴파일)
```

| | C1 컴파일러 | C2 컴파일러 |
|--|-----------|-----------|
| 목적 | 빠른 시작 | 최고 성능 |
| 최적화 수준 | 기본적인 최적화 | 공격적인 최적화 |
| 컴파일 속도 | 빠름 | 느림 |
| 트리거 조건 | 1,500회 호출 | 10,000회 호출 (기본값) |

```bash
# JIT 컴파일 동작 확인
-XX:+PrintCompilation

# 출력 예시
    124    1       3       java.lang.String::hashCode (55 bytes)
    125    2       4       java.lang.String::equals (81 bytes)
    # 숫자: 시간(ms), 레벨(1~4), 클래스/메서드
```

### 4.3 JIT 주요 최적화 기법

#### 메서드 인라이닝 (가장 중요한 최적화)

```java
// 원본 코드
int add(int a, int b) {
    return a + b;
}

void process() {
    int result = add(1, 2);  // 메서드 호출 비용 발생
}

// JIT 인라이닝 후
void process() {
    int result = 1 + 2;      // 메서드 호출 없이 직접 삽입
}
```

```bash
# 인라이닝 동작 확인
-XX:+UnlockDiagnosticVMOptions
-XX:+PrintInlining

# 인라이닝 최대 크기 조정 (기본 35 bytes)
-XX:MaxInlineSize=35
```

#### Escape Analysis (스택 할당 최적화)

```java
// Escape Analysis가 적용되는 경우
void process() {
    // Point 객체가 메서드 밖으로 나가지 않음 (탈출하지 않음)
    Point p = new Point(1, 2);
    int sum = p.x + p.y;
    // p는 이 메서드 안에서만 사용됨
}

// JIT 최적화 후
void process() {
    // 힙 할당 없이 스택에 직접 저장 (GC 부담 없음)
    int x = 1;
    int y = 2;
    int sum = x + y;
}
```

```java
// Escape Analysis가 안 되는 경우
Point createPoint() {
    return new Point(1, 2);  // 메서드 밖으로 탈출 (반환값)
}

void storePoint() {
    this.point = new Point(1, 2);  // 필드에 저장 (탈출)
}
```

#### 기타 최적화
- **Loop Unrolling**: 반복문을 펼쳐서 분기 비용 감소
- **Dead Code Elimination**: 실행되지 않는 코드 제거
- **Branch Prediction**: CPU 분기 예측 힌트 활용

### 4.4 GraalVM과 Native Image (AOT 컴파일)

```
JIT (Just-In-Time) 컴파일:
- 실행 시간에 컴파일 (런타임 프로파일링 가능)
- 시작 시간이 느림
- 장기 실행 시 C2의 공격적 최적화로 최고 성능

AOT (Ahead-Of-Time) 컴파일 (GraalVM Native Image):
- 빌드 시간에 네이티브 실행 파일 생성
- 즉각적인 시작 시간 (몇 밀리초)
- 메모리 사용량 적음
- 런타임 프로파일링 불가 → JIT보다 피크 성능 낮을 수 있음
- Spring Boot 3.x Native Image 공식 지원

적합한 시나리오:
- JIT: 장기 실행 서버 (WebApp, 마이크로서비스)
- AOT: 짧은 실행 (CLI 도구, FaaS/Lambda 함수, 컨테이너 빠른 시작)
```

---

## 5. Virtual Thread와 JVM (Java 21)

### 5.1 Platform Thread vs Virtual Thread

```
Platform Thread (기존):
- OS Thread 1:1 매핑
- 생성 비용 높음 (약 1MB 스택, OS 자원)
- I/O 블로킹 시 OS Thread도 블로킹
- 수천 개가 한계

Virtual Thread (Java 21):
- JVM이 직접 스케줄링
- 생성 비용 낮음 (수백 bytes, 동적 스택)
- I/O 블로킹 시 carrier thread에서 분리 (unmount)
- 수백만 개 생성 가능
```

```java
// Virtual Thread 생성
Thread vThread = Thread.ofVirtual()
    .name("virtual-thread")
    .start(() -> {
        // I/O 블로킹 코드를 동기 스타일로 작성
        // 블로킹되면 carrier thread에서 분리되어 다른 Virtual Thread가 실행
        String result = httpClient.send(request, bodyHandler);
    });

// ExecutorService로 사용 (추천)
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> processRequest());
}
```

### 5.2 JVM 메모리 관점에서의 Virtual Thread

```
Platform Thread:
- 고정된 Stack 크기 (기본 ~1MB)
- OS가 관리

Virtual Thread:
- 동적으로 성장/축소하는 Stack
- Heap에 저장 (JVM이 관리)
- I/O 대기 시 Stack을 Heap에 저장(Heap dump), carrier thread 해방
- I/O 완료 시 다른 carrier thread에서 Stack 복원하여 재개
```

---

## 6. 면접 예상 질문 & 모범 답변

### Q1. JVM의 클래스 로더 위임 모델(Parent Delegation Model)이 왜 필요한가요?

**답변 포인트:**
> "두 가지 이유가 있습니다. 첫째, 보안입니다. java.lang.String 같은 핵심 클래스를 악의적으로 교체하려 해도 Bootstrap ClassLoader가 항상 먼저 처리하므로 불가능합니다. 둘째, 중복 로딩 방지입니다. 같은 클래스를 여러 ClassLoader가 중복으로 로드하면 ClassCastException이 발생할 수 있는데, 위임 모델을 통해 한 번만 로드되도록 보장합니다."

---

### Q2. Java 8에서 PermGen이 Metaspace로 바뀐 이유가 무엇인가요?

**답변 포인트:**
> "PermGen은 JVM 힙 내부에 고정 크기 영역이었습니다. -XX:MaxPermSize를 잘못 설정하면 'OutOfMemoryError: PermGen space'가 자주 발생했고, 적절한 크기 예측이 어려웠습니다. Metaspace는 JVM 힙 외부의 Native Memory를 사용하므로 OS가 허용하는 한 동적으로 확장됩니다. 또한 String Pool이 힙으로 이동하면서 GC가 불필요한 문자열을 수집할 수 있게 되었습니다. 다만 -XX:MaxMetaspaceSize를 설정하지 않으면 무제한 증가할 수 있어 주의가 필요합니다."

---

### Q3. `String s = "hello"`와 `new String("hello")`의 차이가 무엇인가요?

**답변 포인트:**
> "리터럴 방식(String s = 'hello')은 String Pool(Java 7 이후 힙에 위치)을 사용합니다. 같은 문자열이 이미 Pool에 있으면 새 객체를 만들지 않고 기존 객체의 참조를 반환합니다. new String()은 Pool을 사용하지 않고 항상 힙에 새 객체를 생성합니다. 그래서 'hello' == 'hello'는 true이지만, new String('hello') == new String('hello')는 false입니다. intern() 메서드를 호출하면 Pool에 등록하거나 이미 있는 Pool의 참조를 반환합니다."

---

### Q4. Escape Analysis가 무엇이고 GC에 어떤 영향을 주나요?

**답변 포인트:**
> "Escape Analysis는 JIT 컴파일러가 객체의 생존 범위를 분석하여, 메서드 내부에서만 사용되는 객체(탈출하지 않는 객체)를 힙이 아닌 스택에 할당하는 최적화입니다. 스택에 할당되면 메서드가 반환될 때 자동으로 해제되므로 GC의 대상이 되지 않습니다. 결과적으로 Young GC 빈도가 줄어들고 GC Pause Time도 감소합니다. 단, 객체가 반환값이거나 필드에 저장되면 Escape Analysis 대상에서 제외됩니다."

---

### Q5. JIT 컴파일러의 C1과 C2의 차이가 무엇인가요?

**답변 포인트:**
> "C1은 Client 컴파일러로 빠른 컴파일 속도가 목적입니다. 기본적인 최적화만 수행하지만 빠르게 네이티브 코드를 생성합니다. C2는 Server 컴파일러로 최고 성능이 목적입니다. 공격적인 최적화(메서드 인라이닝, 루프 언롤링 등)를 수행하지만 컴파일 시간이 깁니다. Java 7부터는 Tiered Compilation으로 처음엔 C1을 사용하다가 메서드가 충분히 호출되면 C2로 전환합니다. 이를 통해 빠른 시작과 장기 실행 성능을 모두 달성합니다."

---

### Q6. Tomcat은 왜 여러 웹앱이 같은 라이브러리를 독립적으로 가질 수 있나요?

**답변 포인트:**
> "Tomcat은 각 웹앱마다 독립적인 WebappClassLoader를 생성하고, 이 ClassLoader는 Parent Delegation Model을 부분적으로 위반합니다. 일반적인 위임 모델과 달리 WebappClassLoader가 먼저 WEB-INF/lib의 클래스를 로드하려 시도하고, 없는 경우에만 부모에게 위임합니다. 이로 인해 App1은 jackson 2.13을, App2는 jackson 2.12를 독립적으로 사용할 수 있습니다."

---

### Q7. StackOverflowError와 OutOfMemoryError의 차이가 무엇인가요?

**답변 포인트:**
> "StackOverflowError는 재귀 호출이 너무 깊어서 스레드의 Stack 공간이 부족할 때 발생합니다. -Xss 옵션으로 스택 크기를 조정하거나 재귀를 반복문으로 바꿔 해결합니다. OutOfMemoryError는 힙, Metaspace, Direct Memory 등 다양한 영역에서 메모리가 부족할 때 발생합니다. 'Java heap space'는 힙 부족, 'GC overhead limit exceeded'는 GC가 시간의 98% 이상을 소요, 'Metaspace'는 클래스 로더 누수가 주요 원인입니다."

---

### Q8. Virtual Thread와 기존 Platform Thread의 I/O 처리 방식 차이가 무엇인가요?

**답변 포인트:**
> "Platform Thread는 OS Thread와 1:1 매핑되어 있어서 I/O 블로킹 시 OS Thread도 함께 블로킹됩니다. 스레드 1000개를 만들면 OS Thread 1000개가 블로킹 상태로 대기하며 메모리와 컨텍스트 스위칭 비용이 큽니다. Virtual Thread는 I/O 블로킹 시 JVM이 해당 Virtual Thread를 carrier thread에서 분리(unmount)합니다. Stack을 힙에 저장하고 carrier thread는 다른 Virtual Thread를 실행합니다. I/O가 완료되면 어느 carrier thread에서든 재개될 수 있습니다. 결과적으로 적은 수의 carrier thread로 수백만 개의 Virtual Thread를 처리할 수 있습니다."

---

### Q9. 클래스 로딩은 언제 트리거되나요?

**답변 포인트:**
> "클래스 로딩은 지연 로딩(Lazy Loading)이 원칙입니다. 즉 클래스가 처음 실제로 사용될 때 로드됩니다. 트리거 조건은: 1) new 키워드로 인스턴스 생성, 2) static 변수/메서드 접근, 3) 서브클래스가 로드될 때 부모 클래스 로드, 4) 애플리케이션 시작점(main() 메서드 포함 클래스)이 있습니다. 단순히 클래스 참조 타입 선언만으로는 로딩이 트리거되지 않습니다."

---

### Q10. JIT 컴파일러의 메서드 인라이닝이 왜 중요한 최적화인가요?

**답변 포인트:**
> "메서드 인라이닝은 호출되는 메서드의 코드를 호출 지점에 직접 삽입하는 최적화입니다. 이것이 중요한 이유는 두 가지입니다. 첫째, 메서드 호출 자체의 오버헤드(스택 프레임 생성/해제, 파라미터 전달)를 제거합니다. 둘째, 인라이닝된 코드에 다른 최적화(Escape Analysis, 상수 폴딩 등)를 연쇄적으로 적용할 수 있습니다. 특히 Java에서 getter/setter 같은 소규모 메서드가 많은데, 이런 메서드들이 인라이닝되면 실질적으로 직접 필드 접근과 동일해집니다. -XX:MaxInlineSize(기본 35 bytes)를 초과하는 메서드는 인라이닝되지 않습니다."

---

### Q11. Metaspace 메모리 누수는 어떤 상황에서 발생하나요?

**답변 포인트:**
> "Metaspace 누수는 ClassLoader 누수와 동일합니다. 클래스 메타데이터는 해당 ClassLoader가 GC되어야 수집됩니다. ClassLoader가 GC되지 않으면 메타데이터도 Metaspace에 계속 남습니다. 주요 발생 케이스: 1) 동적으로 클래스를 생성하는 라이브러리(CGLIB, Javassist)를 과도하게 사용할 때, 2) Tomcat 같은 WAS에서 웹앱을 반복 reload할 때 이전 WebappClassLoader가 누수될 때, 3) Spring의 @Scope('prototype')에서 CGLIB 프록시를 과도하게 생성할 때입니다. 진단은 jmap -clstats 또는 JProfiler로 ClassLoader 수를 모니터링합니다."

---

## 7. 학습 체크리스트

학습을 마친 후 아래 항목을 구두로 설명해보세요.

- [ ] JVM 전체 구조도를 화이트보드에 그릴 수 있다 (ClassLoader → Runtime Data Area → Execution Engine)
- [ ] 클래스 로딩 3단계를 순서대로 설명할 수 있다 (Loading → Linking → Initialization)
- [ ] Bootstrap/Extension/Application ClassLoader가 각각 무엇을 로드하는지 말할 수 있다
- [ ] Parent Delegation Model의 필요성을 보안과 중복 로딩 방지 관점으로 설명할 수 있다
- [ ] PermGen과 Metaspace의 차이를 말할 수 있다 (위치, 크기 결정 방식)
- [ ] `String s = "hello"`와 `new String("hello")`의 메모리 차이를 설명할 수 있다
- [ ] 지역 변수와 인스턴스 변수가 각각 어디에 저장되는지 설명할 수 있다
- [ ] JIT 컴파일러의 C1/C2 역할 차이와 Tiered Compilation을 설명할 수 있다
- [ ] Escape Analysis가 GC에 미치는 영향을 설명할 수 있다
- [ ] Virtual Thread의 carrier thread 개념과 I/O 처리 방식을 설명할 수 있다
- [ ] StackOverflowError와 OutOfMemoryError의 차이와 원인을 설명할 수 있다
- [ ] Metaspace 누수가 발생하는 시나리오를 설명할 수 있다

---

## 8. 연관 학습 파일

- [`garbage-collection.md`](./garbage-collection.md) - 힙 구조와 GC 알고리즘 상세
- [`java-concurrency-threading.md`](./java-concurrency-threading.md) - JMM과 happens-before (메모리 모델)
- [`jvm-options.md`](./jvm-options.md) - JVM 실행 옵션 정리
- [`jvm-xmx-xms-same.md`](./jvm-xmx-xms-same.md) - 힙 메모리 설정 최적화
