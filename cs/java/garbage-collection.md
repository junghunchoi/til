# GC(가비지 컬렉션) 메커니즘과 튜닝 완전 가이드

> "GC 튜닝 경험이 있나요?"라는 질문에 직접 경험 없이도 이론적으로 답할 수 있도록.
> G1GC와 ZGC의 동작 원리, GC 로그 분석, OOM 진단 방법을 완전히 정리합니다.

---

## 1. GC의 기본 원리

### 1.1 GC 대상 판별: 도달 가능성(Reachability)

```
참조 카운팅 방식의 문제점:
A → B, B → A (순환 참조) 시 서로 참조하므로 카운트가 0이 되지 않음
→ Java는 참조 카운팅을 사용하지 않음

Java의 방식: GC Root에서 출발하여 도달 가능한 객체만 살아있다고 판단
```

**GC Root의 종류:**
```
1. Stack 지역 변수 (현재 실행 중인 모든 스레드의)
2. static 변수 (Method Area에 저장된)
3. JNI(Java Native Interface) 참조
4. 동기화(synchronized)를 위해 잠금된 객체

→ GC Root에서 참조 사슬을 따라 도달 가능한 객체: 살아있음 (Live)
→ 도달 불가능한 객체: 수집 대상 (Garbage)
```

### 1.2 Mark-Sweep-Compact 3단계

```
┌───────────────────────────────────────────────────────────┐
│  Phase 1: MARK                                             │
│  GC Root에서 출발하여 살아있는 객체에 마킹                  │
│                                                             │
│  [GC Root] → [A*] → [B*]   [C]  [D*] → [E*]              │
│  (* = 마킹됨, C는 도달 불가능)                             │
└───────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────┐
│  Phase 2: SWEEP                                            │
│  마킹되지 않은 객체(C) 제거 → 빈 공간 발생                 │
│                                                             │
│  [A] [빈공간] [B] [빈공간] [D] [E]                        │
│  → 단편화(Fragmentation) 발생!                             │
└───────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────┐
│  Phase 3: COMPACT                                          │
│  살아있는 객체를 한쪽으로 모아 단편화 해소                  │
│                                                             │
│  [A][B][D][E][빈공간]                                      │
└───────────────────────────────────────────────────────────┘
```

### 1.3 Stop-The-World (STW)

```
GC가 실행되는 동안 모든 애플리케이션 스레드를 멈추는 것

왜 필요한가?
- GC가 힙을 스캔하는 동안 애플리케이션이 객체를 계속 생성/이동하면
  GC의 분석 결과가 일관되지 않음
- GC의 정확성 보장을 위해 스냅샷이 필요

문제점:
- STW 동안 애플리케이션이 응답하지 않음 (Latency 증가)
- Full GC는 수 초 ~ 수십 초까지 STW 발생 가능

현대 GC의 목표: STW 최소화
- G1GC: STW < 200ms (기본 목표)
- ZGC: STW < 10ms (힙 크기 무관)
```

---

## 2. GC 알고리즘의 역사와 진화

### 2.1 Serial GC

```bash
# 사용 방법
-XX:+UseSerialGC

특징:
- 단일 스레드로 GC 실행
- GC 중 전체 STW
- 힙이 작은 환경, 단순 앱에 적합
- 클라이언트 애플리케이션용
```

### 2.2 Parallel GC (Java 8 이전 서버 기본값)

```bash
-XX:+UseParallelGC
-XX:ParallelGCThreads=4

특징:
- 여러 스레드로 GC 실행 (GC 시간 단축)
- 하지만 GC 중 여전히 전체 STW
- Throughput(처리량) 최적화
- 배치 처리처럼 latency보다 처리량이 중요한 경우
```

### 2.3 CMS (Concurrent Mark Sweep) - Java 9 Deprecated

```
STW를 줄이기 위한 첫 시도
Initial Mark (STW) → Concurrent Mark → Remark (STW) → Concurrent Sweep

문제점:
1. Floating Garbage: Concurrent Mark 중 새로 생성된 Garbage를 수집 못함
2. Fragmentation: Compact 단계 없어서 파편화 심화 → Free List 유지 오버헤드
3. Concurrent Mode Failure: Sweep 완료 전 Old Gen이 꽉 차면 Serial Full GC 실행
→ G1GC 등장으로 대체, Java 14에서 완전 제거
```

### 2.4 G1GC (Java 9+ 기본값) ← 핵심

#### Region 기반 힙 구조

```
기존 GC: Young(Eden+S0+S1) | Old 의 고정 분리

G1GC: 힙 전체를 동일 크기 Region으로 분할
┌────┬────┬────┬────┬────┬────┬────┬────┐
│ E  │ O  │ S  │ E  │ H  │ O  │ E  │ O  │
├────┼────┼────┼────┼────┼────┼────┼────┤
│ O  │ E  │ O  │ S  │ H  │ E  │ O  │ E  │
└────┴────┴────┴────┴────┴────┴────┴────┘
E = Eden, S = Survivor, O = Old, H = Humongous

특징:
- 각 Region이 동적으로 역할 변경 (Eden이었다가 Old가 될 수 있음)
- Region 크기: 1MB ~ 32MB (2의 제곱수), 힙 크기 / 2048
- 가비지가 많은 Region부터 수집 (Garbage-First의 의미)
```

#### G1GC 동작 단계 상세

```
┌─────────────────────────────────────────────────────────────┐
│  1. Young GC (STW 발생)                                      │
│  트리거: Eden Region이 꽉 참                                  │
│  동작: Eden + Survivor의 살아있는 객체를 새 Survivor로 복사   │
│  일정 age 이상이면 Old Region으로 이동 (Promotion)           │
│  STW: 수십 ~ 수백 ms                                         │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  2. Concurrent Marking (STW 없음 - 병렬 실행)               │
│  트리거: 힙 점유율이 IHOP(기본 45%) 초과                    │
│  동작: 힙 전체에서 살아있는 객체 마킹 (앱과 동시 실행)      │
│  단계:                                                        │
│  a. Initial Mark (STW) - GC Root 직접 참조만 마킹            │
│  b. Root Region Scan - Survivor Region의 참조 스캔           │
│  c. Concurrent Mark - 전체 힙 병렬 마킹                      │
│  d. Remark (STW) - 놓친 객체 처리 (SATB 알고리즘)           │
│  e. Cleanup (STW 일부) - 빈 Region 식별 및 초기화           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  3. Mixed GC (STW 발생)                                      │
│  트리거: Concurrent Marking 완료 후 자동 실행                │
│  동작: Young Region + 가비지가 많은 Old Region 선별적 수집   │
│  특징: Old Region 전체를 한 번에 처리하지 않고 점진적 수집   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  4. Full GC (최후 수단, 매우 느림)                           │
│  트리거: Mixed GC로 메모리 회수 못할 때, Humongous Allocation│
│  동작: Serial 방식으로 전체 힙 수집                          │
│  STW: 수 초 이상 가능                                        │
│  → Full GC 발생 자체가 문제 신호!                            │
└─────────────────────────────────────────────────────────────┘
```

#### Humongous Object (Humongous Region)

```java
// Region 크기의 50% 이상인 객체 = Humongous Object
// 예: Region 크기 4MB이면, 2MB 이상 객체

// Humongous 객체의 특징:
// - Old Region에 직접 할당 (Young GC를 거치지 않음)
// - 연속된 Region을 차지
// - Full GC 시 수집 (G1GC 이전 버전)
// - Java 8u60+: Concurrent Marking 후 수집 가능

// 문제가 되는 경우:
// - 대용량 배열이나 List를 반복적으로 생성하면 Full GC 빈발

// 확인 방법 (GC 로그):
// [Humongous regions: X→Y]

// 해결:
// 1. G1HeapRegionSize 조정으로 Humongous 임계값 높이기
//    -XX:G1HeapRegionSize=8m  (임계값을 4MB로)
// 2. 대용량 객체를 재사용 (Pool)
```

#### G1GC 핵심 튜닝 파라미터

```bash
# 목표 STW 시간 (G1이 이 값을 맞추려고 Region 수 자동 조절)
-XX:MaxGCPauseMillis=200   # 기본값 200ms

# Region 크기 (1MB~32MB, 2의 제곱수)
# 힙 크기 / 2048 로 자동 결정되지만 수동 설정 가능
-XX:G1HeapRegionSize=4m

# Concurrent Marking 시작 힙 점유율 (기본 45%)
# 너무 높으면 Concurrent Marking이 늦어 Full GC 위험
# 너무 낮으면 불필요한 Marking으로 CPU 낭비
-XX:InitiatingHeapOccupancyPercent=45

# Survivor Region 예비 공간 (기본 10%)
# Evacuation Failure 방지용
-XX:G1ReservePercent=10

# Mixed GC에서 처리할 Old Region의 최대 비율 (기본 65%)
-XX:G1OldCSetRegionThresholdPercent=65
```

---

### 2.5 ZGC (Java 15+ Production Ready) ← 심화

#### ZGC의 목표와 핵심 기술

```
목표: 힙 크기(1GB ~ 수 TB)에 관계없이 STW < 10ms

핵심 기술:
1. Colored Pointers (컬러 포인터)
2. Load Barriers (로드 배리어)
3. Concurrent Relocation (동시 재배치)
```

#### Colored Pointers (컬러 포인터)

```
일반 참조 포인터: 64비트 중 일부를 상태 정보로 사용

┌─────────────────────────────────────────────────────┐
│ 64-bit 포인터                                        │
│ [unused(18bit)][Finalizable(1)][Remapped(1)]        │
│                [Marked1(1)][Marked0(1)][address(42)]│
└─────────────────────────────────────────────────────┘

각 비트의 의미:
- Marked0/Marked1: GC가 살아있는 객체로 마킹 (교대로 사용)
- Remapped: 객체가 새 위치로 이동 완료
- Finalizable: finalize() 필요

장점: 포인터를 읽는 것만으로 GC 상태 확인 가능
      → 별도 비트맵 없이도 마킹/재배치 상태 추적
```

#### Load Barriers (로드 배리어)

```java
// 객체 참조를 읽을 때마다 JIT가 삽입하는 소량의 코드
Object obj = someObject.field;  // 읽기

// JIT가 변환:
Object obj = someObject.field;
if (needsBarrier(obj)) {       // 포인터 상태 확인
    obj = fixPointer(obj);      // 필요 시 업데이트 (재배치된 주소로)
}

// Load Barrier가 하는 일:
// 1. GC가 진행 중이면 마킹에 협력
// 2. 객체가 이동되었으면 새 주소로 포인터 업데이트
// → GC 작업을 애플리케이션 스레드와 분산
```

#### Concurrent Relocation (동시 재배치)

```
기존 GC: 객체 이동은 반드시 STW 중에 (모든 참조를 한 번에 업데이트)
ZGC: 객체를 이동하면서 동시에 앱 실행

방법:
1. 이동할 객체를 새 위치에 복사
2. 이전 위치에 Forwarding Pointer 남김 (새 위치 가리킴)
3. 이후 이전 위치에 접근 시 Load Barrier가 새 위치로 리다이렉트
4. 나중에 모든 참조가 업데이트되면 이전 위치 해제

결과: 객체 이동 중에도 애플리케이션 실행 가능 → STW 없음
```

#### Generational ZGC (Java 21+)

```bash
# Java 21부터 Young/Old 세대 분리로 성능 향상
-XX:+UseZGC -XX:+ZGenerational

# Young 세대에서 자주 GC → 단명 객체 빠르게 제거
# Old 세대는 드물게 GC → 장수 객체 효율적 관리
# Throughput과 Latency 모두 개선
```

### 2.6 GC 선택 기준

```
의사결정 트리:

latency < 10ms가 최우선 → ZGC (Java 15+)
대용량 힙 (> 8GB), latency < 200ms → G1GC (기본값)
배치/ETL처럼 처리량이 최우선 → Parallel GC
힙 < 1GB, 단순 앱, 빠른 시작 → Serial GC

Java 21+, Spring Boot 3.x 신규 프로젝트 → ZGC + Generational 고려

추가 고려사항:
- CPU 코어 수: 단일 코어 환경 → Serial GC
- 응답시간 SLA가 엄격한 경우 → ZGC 우선
- 기존 G1GC 환경을 ZGC로 전환 시 부하 테스트 필수
```

---

## 3. GC 로그 읽기와 문제 진단

### 3.1 GC 로그 활성화 (Java 9+)

```bash
# 기본 GC 로그
-Xlog:gc*:file=gc.log:time,uptime,level,tags

# 힙 상세 포함
-Xlog:gc*,gc+heap=debug:file=gc.log:time,uptime

# G1GC 특화 로그
-Xlog:gc+ergo*=debug:file=gc.log:time,uptime

# 콘솔 출력
-Xlog:gc*::time,uptime
```

### 3.2 G1GC 로그 해석

```
[2024-01-15T10:30:00.100+0900][5.123s][info][gc,start] GC(1) Pause Young (Normal) (G1 Evacuation Pause)
[2024-01-15T10:30:00.100+0900][5.123s][info][gc,heap] GC(1) Heap before GC invocations=1: 512M(512M)/2048M(2048M)
[2024-01-15T10:30:00.145+0900][5.168s][info][gc      ] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 512M->128M(2048M) 45.123ms

해석:
- Pause Young (Normal): 일반 Young GC
- G1 Evacuation Pause: Eden/Survivor 객체를 대피(Evacuate)
- 512M→128M: GC 전 512MB, GC 후 128MB (힙 사용량)
- (2048M): 전체 힙 크기
- 45.123ms: STW 시간

주의 신호:
- Pause Young (Concurrent Start): Concurrent Marking 시작
- Pause Mixed: Mixed GC
- Pause Full: Full GC (문제!) → 즉시 조사
- to-space exhausted: Survivor/Old 공간 부족 (Evacuation Failure!)
```

### 3.3 GC 분석 도구

```
1. GCViewer (오픈소스)
   - GC 로그 파일을 시각화
   - Pause Time 분포, Throughput 계산

2. GCEasy (웹 기반, https://gceasy.io)
   - GC 로그 업로드 후 자동 분석
   - 문제 유형 자동 진단

3. Java Mission Control (JMC)
   - JVM 런타임 모니터링
   - GC, CPU, 메모리 종합 분석

4. jstat (JDK 내장)
jstat -gcutil <pid> 1000  # 1초마다 GC 통계
# S0  S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT
# 0.00 12.50 76.00  45.00  98.55  90.00    150    3.200     2    1.000    4.200
# YGC: Young GC 횟수, YGCT: Young GC 총 시간
# FGC: Full GC 횟수 (0이 이상적), FGCT: Full GC 총 시간
```

---

## 4. OOM 유형별 진단과 해결

### 4.1 유형 1: Java heap space

```
에러 메시지: java.lang.OutOfMemoryError: Java heap space

원인:
1. 진짜 메모리 부족 (-Xmx가 너무 작음)
2. 메모리 누수 (참조가 해제되지 않는 객체 축적)

진단 방법:
```

```bash
# OOM 발생 시 자동으로 Heap Dump 생성
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/logs/heapdump.hprof

# 수동으로 Heap Dump 생성 (생산 서버에서 주의)
jmap -dump:live,format=b,file=heapdump.hprof <pid>
```

```
Heap Dump 분석 (Eclipse MAT - Memory Analyzer Tool):
1. heapdump.hprof 파일을 MAT로 열기
2. "Leak Suspects Report" 실행
3. 가장 큰 객체/클래스 확인
4. Retained Heap(해당 객체를 통해 살아있는 전체 메모리) 분석
5. 참조 사슬(Reference Chain) 추적 → 어디서 참조가 유지되는지 확인

일반적인 메모리 누수 패턴:
- static Map/List에 계속 추가만 하고 제거하지 않는 경우
- ThreadLocal.remove() 누락 (앞 파일 참조)
- 이벤트 리스너 등록 후 해제하지 않는 경우
- 캐시가 무한정 증가하는 경우
```

### 4.2 유형 2: GC overhead limit exceeded

```
에러 메시지: java.lang.OutOfMemoryError: GC overhead limit exceeded

발생 조건: 전체 실행 시간의 98% 이상을 GC에 사용했지만 힙을 2% 미만 회수

원인:
1. 힙이 너무 작아서 GC가 계속 실행됨
2. 메모리 누수로 힙이 점점 채워짐

해결:
1. -Xmx로 힙 크기 증가
2. 메모리 누수 찾아서 수정
3. (임시방편) -XX:-UseGCOverheadLimit 비활성화 (권장하지 않음)
```

### 4.3 유형 3: Metaspace

```
에러 메시지: java.lang.OutOfMemoryError: Metaspace

원인: 클래스 메타데이터가 Metaspace를 가득 채움

일반적 원인: ClassLoader 누수
- 동적으로 클래스를 생성하면서 ClassLoader를 GC되지 않게 잡아둠
- CGLIB, Javassist, ByteBuddy 과도 사용
- Tomcat 웹앱 재로드 시 이전 ClassLoader 누수

진단:
```

```bash
# Metaspace 크기 설정
-XX:MaxMetaspaceSize=256m

# ClassLoader 통계 확인
jmap -clstats <pid>
```

```java
// ClassLoader 누수 패턴
// 잘못된 코드: 매 요청마다 새 ClassLoader 생성 (GC되지 않음)
Map<String, ClassLoader> classLoaderCache = new HashMap<>();
classLoaderCache.put(name, new URLClassLoader(urls)); // 계속 누적
```

### 4.4 유형 4: Direct Buffer Memory

```
에러 메시지: java.lang.OutOfMemoryError: Direct buffer memory

원인: ByteBuffer.allocateDirect()로 할당한 Native Memory 부족
- Netty, NIO, WebFlux에서 자주 사용

해결:
-XX:MaxDirectMemorySize=512m  (기본값: -Xmx와 동일)
```

---

## 5. 실무 GC 튜닝 프로세스

### 5.1 튜닝 원칙

```
1. 측정 먼저, 최적화 나중
2. 한 번에 하나씩 변경하고 측정
3. 부하 테스트 환경에서 검증 (프로덕션과 동일 조건)
4. GC가 아닌 코드 문제일 수 있음을 항상 고려
```

### 5.2 단계별 튜닝 프로세스

```
Step 1: 현재 상태 측정
├── jstat -gcutil <pid> 1000으로 GC 빈도/시간 모니터링
├── GC 로그 활성화 후 수집
└── OOM 상황이면 HeapDump 생성

Step 2: 문제 유형 파악
├── Throughput이 낮음?
│   → Young GC가 너무 자주 → 힙 크기 증가 또는 코드 개선
├── Latency가 높음 (긴 Pause)?
│   → Full GC 발생 → InitiatingHeapOccupancyPercent 낮추기
│   → Mixed GC STW가 큼 → MaxGCPauseMillis 낮추기
└── OOM 발생?
    → Heap Dump 분석으로 메모리 누수 찾기

Step 3: G1GC 주요 조정
├── 힙 크기: -Xms == -Xmx (힙 크기 변동으로 인한 GC 방지)
├── 목표 Pause: -XX:MaxGCPauseMillis=200
├── IHOP 조정: -XX:InitiatingHeapOccupancyPercent=35 (낮추면 GC 더 자주)
└── Region 크기: Humongous 객체가 많으면 -XX:G1HeapRegionSize 증가

Step 4: 검증
└── 부하 테스트 (Artillery, k6, JMeter) + GC 로그 분석
```

### 5.3 I/O 바운드 서비스의 GC 특성

```
Spring Boot 웹 애플리케이션의 일반적 GC 패턴:

많은 요청 처리 중:
- 요청당 임시 객체 대량 생성 (DTO, 중간 처리 객체)
- Young GC 자주 발생 (정상, 빠름)
- 대부분 Young GC에서 수집됨 (단명 객체)
- Old Gen에는 세션, 캐시, 싱글톤 등 장수 객체

문제 신호:
- Old Gen 사용량이 계속 증가 → 메모리 누수 의심
- Full GC 발생 → 즉시 조사
- Young GC 시간이 길어짐 → Survivor 공간 부족 또는 Promotion 과다
```

---

## 6. 면접 예상 질문 & 모범 답변

### Q1. GC가 왜 Stop-The-World를 해야 하나요? 완전히 없앨 수 없나요?

**답변 포인트:**
> "GC가 힙을 스캔하는 동안 애플리케이션이 계속 객체를 생성하고 참조를 변경하면, GC의 분석 결과가 일관되지 않습니다. 예를 들어 GC가 객체 A를 수집 대상으로 표시했는데, 그 사이 다른 스레드가 A에 대한 새 참조를 만들면 살아있는 객체가 수집되는 오류가 발생합니다. STW를 완전히 없앨 수는 없지만 ZGC는 Load Barrier와 Colored Pointer를 활용하여 STW를 10ms 이하로 줄였습니다. Initial Mark와 Remark 단계에만 극히 짧은 STW가 있고, 나머지는 애플리케이션과 동시에 실행됩니다."

---

### Q2. G1GC의 Region이란 무엇이고, 기존 GC 방식과 어떻게 다른가요?

**답변 포인트:**
> "기존 GC는 Young(Eden+Survivor)과 Old로 힙을 고정 분리했습니다. G1GC는 힙 전체를 동일 크기(1MB~32MB)의 Region으로 분할합니다. 각 Region이 동적으로 Eden, Survivor, Old 역할을 맡고 바꿀 수 있습니다. 가비지가 가장 많은 Region부터 수집하여(Garbage-First) 수집 효율을 높입니다. 또한 목표 STW 시간(-XX:MaxGCPauseMillis)에 맞추어 한 번에 처리할 Region 수를 자동 조절합니다. 이를 통해 예측 가능한 STW 시간을 제공하는 것이 G1GC의 핵심 특징입니다."

---

### Q3. G1GC에서 Full GC가 발생하는 원인과 대응 방법은?

**답변 포인트:**
> "G1GC의 Full GC는 Mixed GC로 메모리를 충분히 회수하지 못할 때 마지막 수단으로 발생합니다. 주요 원인은 세 가지입니다. 첫째, Concurrent Marking이 너무 늦게 시작되어(IHOP이 너무 높음) Old Gen이 꽉 찰 때입니다. IHOP을 낮추어 Marking을 더 일찍 시작합니다. 둘째, Humongous Object 할당이 과다할 때입니다. 대용량 객체를 줄이거나 G1HeapRegionSize를 늘립니다. 셋째, 실제 메모리 누수로 힙이 계속 채워질 때입니다. Heap Dump 분석으로 누수를 찾아야 합니다. Full GC 발생 자체가 문제 신호이므로 로그에서 Pause Full을 발견하면 즉시 원인을 조사합니다."

---

### Q4. ZGC는 어떻게 STW를 10ms 이하로 줄이나요?

**답변 포인트:**
> "ZGC는 세 가지 핵심 기술을 사용합니다. 첫째, Colored Pointer입니다. 64비트 포인터의 상위 비트를 GC 상태 정보로 활용합니다. 포인터를 읽는 것만으로 해당 객체의 GC 상태를 알 수 있어 별도 비트맵이 필요 없습니다. 둘째, Load Barrier입니다. 객체 참조를 읽을 때마다 JIT가 삽입한 소량의 코드가 실행됩니다. GC 진행 중이면 마킹에 협력하고, 객체가 이동됐으면 새 주소로 자동 업데이트합니다. GC 작업을 애플리케이션 스레드에 분산하는 효과가 있습니다. 셋째, Concurrent Relocation입니다. 객체를 새 위치에 복사하면서 애플리케이션도 함께 실행합니다. 이전 위치에 Forwarding Pointer를 남겨 접근 시 Load Barrier가 새 주소로 리다이렉트합니다."

---

### Q5. OOM이 발생했을 때 어떤 순서로 대응하나요?

**답변 포인트:**
> "먼저 Heap Dump를 확보합니다. 운영 환경이면 -XX:+HeapDumpOnOutOfMemoryError로 자동 생성되도록 미리 설정합니다. 이미 OOM이 났다면 jmap -dump로 현재 상태를 덤프합니다. 다음으로 GC 로그를 확인하여 OOM 직전 GC 패턴을 분석합니다. OOM 유형을 에러 메시지로 구분합니다: 'Java heap space'면 힙 누수나 크기 부족, 'Metaspace'면 ClassLoader 누수, 'GC overhead'면 GC가 대부분 시간을 차지하는 것입니다. Eclipse MAT으로 Heap Dump를 분석하여 Leak Suspects 리포트를 확인하고 어떤 객체가 메모리를 차지하는지, 어디서 참조가 유지되는지 추적합니다. 메모리 누수 원인 코드를 수정하고 부하 테스트로 검증합니다."

---

### Q6. Humongous Object가 무엇이고 왜 문제가 되나요?

**답변 포인트:**
> "G1GC에서 Region 크기의 50% 이상인 객체를 Humongous Object라고 합니다. 예를 들어 Region 크기가 4MB이면 2MB 이상의 객체입니다. Humongous Object는 Old Region에 직접 할당되어 Young GC로 수집되지 않습니다. 연속된 여러 Region을 차지하므로 메모리 단편화가 심해집니다. 이로 인해 Mixed GC로 수집하기 어려워 Full GC를 유발할 수 있습니다. 해결책은 대용량 객체 생성을 줄이거나, 필요하면 G1HeapRegionSize를 늘려 Humongous 임계값을 높이거나, 객체 풀(Pool)을 사용하여 재사용합니다."

---

### Q7. GC Roots는 무엇인가요?

**답변 포인트:**
> "GC Root는 GC가 살아있는 객체를 탐색할 때 시작점이 되는 참조들입니다. 대표적으로 현재 실행 중인 모든 스레드의 스택 지역 변수, Method Area의 static 변수, JNI(Java Native Interface)에서의 참조, 동기화를 위해 잠금된 객체가 있습니다. GC Root에서 참조 사슬을 따라 도달할 수 있는 모든 객체는 살아있는 것으로 간주됩니다. 도달 불가능한 객체는 수집 대상입니다. 메모리 누수는 대부분 의도치 않게 GC Root가 유지되어 객체가 수집되지 않는 경우에 발생합니다."

---

### Q8. Young GC와 Full GC의 차이와 발생 조건은?

**답변 포인트:**
> "Young GC(Minor GC)는 Young Generation(Eden + Survivor)이 꽉 찼을 때 발생합니다. Young Gen만 대상으로 빠르게 수집됩니다. G1GC에서는 STW가 수십~수백ms 수준입니다. 살아남은 객체는 Survivor로 복사되고, 오래된 객체는 Old Gen으로 승격됩니다. Full GC(Major GC)는 Old Gen도 포함한 전체 힙 수집입니다. G1GC에서는 최후 수단으로만 발생하며 Serial 방식으로 실행되어 STW가 초 단위로 길어질 수 있습니다. 발생 조건은 Old Gen이 꽉 차거나, Mixed GC로 메모리 회수가 불충분하거나, Humongous Object 할당 실패 시입니다. Full GC가 빈발하면 반드시 원인을 찾아야 합니다."

---

### Q9. -Xms와 -Xmx를 같게 설정하는 이유는?

**답변 포인트:**
> "Xms는 초기 힙 크기, Xmx는 최대 힙 크기입니다. 기본적으로 Xms가 작으면 애플리케이션 시작 후 힙이 점점 커지면서 GC가 자주 발생합니다. 힙이 늘어날 때마다 OS에서 메모리를 새로 할당받는 비용도 있습니다. 서버 환경에서는 Xms와 Xmx를 같게 설정하여 시작 시점에 최대 힙을 미리 확보합니다. 이렇게 하면 힙 크기 변동으로 인한 GC를 방지하고, 처음부터 충분한 메모리로 안정적으로 실행됩니다. 단, 컨테이너(Docker/K8s) 환경에서는 메모리 제한에 맞게 설정하여 OOM Kill이 발생하지 않도록 주의합니다."

---

### Q10. GC 튜닝 경험을 설명해주세요. (경험이 없는 경우 대응)

**답변 포인트:**
> "직접 GC 튜닝을 경험한 프로젝트는 없지만, 이론과 분석 방법을 준비해왔습니다. 만약 제가 GC 문제를 만난다면 이렇게 접근하겠습니다. 먼저 -Xlog:gc*로 GC 로그를 수집하고 jstat으로 실시간 GC 통계를 모니터링합니다. Young GC 빈도가 높다면 단명 객체가 많은 것이므로 Eden 크기를 늘리거나 코드를 개선합니다. Full GC가 발생한다면 HeapDump로 메모리 누수를 조사하거나 IHOP을 낮추어 Marking을 더 일찍 시작합니다. ZGC 도입을 검토한다면 latency SLA와 기존 G1GC의 STW 시간을 비교하고 부하 테스트로 검증하겠습니다."

---

### Q11. Weak Reference, Soft Reference는 GC에서 어떻게 처리되나요?

**답변 포인트:**
> "Java는 일반 Strong Reference 외에 세 가지 약한 참조 타입을 제공합니다. Soft Reference는 메모리가 부족할 때 GC 수집 대상이 됩니다. -Xmx에 근접하면 수집되어 캐시 구현에 적합합니다. Weak Reference는 다음 GC 때 무조건 수집됩니다. Strong Reference가 없는 객체를 다음 GC까지만 유지하고 싶을 때 사용합니다. WeakHashMap의 키가 Weak Reference입니다. Phantom Reference는 GC 수집 후 ReferenceQueue에 등록되어 파이널라이제이션 대체나 리소스 정리에 사용됩니다. ThreadLocalMap의 키도 WeakReference입니다. 이 때문에 ThreadLocal 객체가 GC되면 키는 null이 되지만 값은 Map에 남아 메모리 누수가 발생할 수 있습니다."

---

### Q12. G1GC에서 Evacuation Failure란 무엇인가요?

**답변 포인트:**
> "Young GC(G1 Evacuation Pause) 중에 Eden과 Survivor의 살아있는 객체를 새 Survivor나 Old Region으로 이동(Evacuate)해야 하는데, 이동할 공간이 충분하지 않을 때 Evacuation Failure가 발생합니다. G1GC는 이 경우 이동하지 못한 객체를 원래 위치에 그대로 두고 Old Gen으로 처리합니다. 이후 Full GC로 이어질 수 있습니다. GC 로그에서 'to-space exhausted'로 나타납니다. 해결책은 G1ReservePercent를 높여 예비 공간을 늘리거나, 힙 크기를 증가시키거나, MaxGCPauseMillis를 높여 더 많은 Region을 한 번에 처리하게 합니다."

---

## 7. 학습 체크리스트

- [ ] GC Root의 종류를 나열하고 Reachability 기반 GC의 원리를 설명할 수 있다
- [ ] Mark-Sweep-Compact 3단계를 순서대로 설명할 수 있다
- [ ] Stop-The-World가 왜 필요한지 일관성 관점으로 설명할 수 있다
- [ ] G1GC의 Heap Region 구조를 기존 Young/Old 분리 방식과 비교하여 설명할 수 있다
- [ ] G1GC의 Young GC, Concurrent Marking, Mixed GC, Full GC의 순서와 트리거 조건을 설명할 수 있다
- [ ] Humongous Object가 문제가 되는 이유와 해결 방법을 설명할 수 있다
- [ ] ZGC의 Colored Pointer와 Load Barrier가 STW를 줄이는 원리를 설명할 수 있다
- [ ] G1GC와 ZGC의 선택 기준을 latency/throughput 관점으로 설명할 수 있다
- [ ] GC 로그를 보고 Pause Time과 GC 유형을 읽을 수 있다
- [ ] OOM의 3가지 주요 유형(heap space, GC overhead, Metaspace)의 원인을 설명할 수 있다
- [ ] Heap Dump를 생성하고 MAT으로 분석하는 절차를 설명할 수 있다
- [ ] -Xms와 -Xmx를 같게 설정하는 이유를 설명할 수 있다
- [ ] GC 튜닝 접근 방법을 측정 → 분석 → 변경 → 검증 순서로 설명할 수 있다

---

## 8. 연관 학습 파일

- [`jvm-architecture.md`](./jvm-architecture.md) - JVM Heap 구조와 Region의 연결 (선행 학습 권장)
- [`jvm-options.md`](./jvm-options.md) - JVM 실행 옵션 (-XX: 파라미터 상세)
- [`jvm-xmx-xms-same.md`](./jvm-xmx-xms-same.md) - 힙 크기 설정 (-Xms == -Xmx 이유)
- [`java-concurrency-threading.md`](./java-concurrency-threading.md) - STW와 스레드 중단의 관계
- [`../microservices/large-scale-traffic-handling.md`](../microservices/large-scale-traffic-handling.md) - 대용량 트래픽에서 GC 튜닝 필요성
