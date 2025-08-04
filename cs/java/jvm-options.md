# 알아두면 좋을 JVM Option

## 핵심 개념

### JVM 옵션의 분류
1. **표준 옵션** (-server, -client)
2. **비표준 옵션** (-X로 시작, -Xmx, -Xms)
3. **실험적 옵션** (-XX로 시작)

### 메모리 관련 옵션

#### 힙 메모리 설정
```bash
# 힙 최대 크기 설정
-Xmx4g  # 4GB로 설정

# 힙 초기 크기 설정
-Xms2g  # 2GB로 설정

# Young Generation 크기 설정
-Xmn1g  # 1GB로 설정

# 스택 크기 설정
-Xss1m  # 1MB로 설정
```

#### 메타스페이스/PermGen 설정
```bash
# Java 8 이후 - 메타스페이스
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m

# Java 7 이하 - PermGen
-XX:PermSize=256m
-XX:MaxPermSize=512m
```

### GC 관련 옵션

#### GC 알고리즘 선택
```bash
# G1GC 사용 (Java 9부터 기본)
-XX:+UseG1GC

# ZGC 사용 (Java 11+)
-XX:+UseZGC

# Parallel GC 사용
-XX:+UseParallelGC

# CMS GC 사용 (Deprecated)
-XX:+UseConcMarkSweepGC
```

#### GC 튜닝 옵션
```bash
# G1GC 일시정지 목표 시간 설정
-XX:MaxGCPauseMillis=200

# Parallel GC 스레드 수 설정
-XX:ParallelGCThreads=4

# Old Generation GC 스레드 수
-XX:ConcGCThreads=2
```

### 성능 관련 옵션

#### JIT 컴파일러 설정
```bash
# 컴파일 임계값 설정
-XX:CompileThreshold=10000

# 티어드 컴파일 활성화
-XX:+TieredCompilation

# C1/C2 컴파일러 스레드 수
-XX:CICompilerCount=4
```

#### 클래스 로딩 최적화
```bash
# 클래스 데이터 공유
-XX:+UseSharedSpaces

# 압축된 OOP 사용
-XX:+UseCompressedOops
```

## 실제 활용 사례

### 운영 환경 권장 설정
```bash
# 8GB 서버 권장 설정
java -server \
  -Xms4g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/logs/heapdump.hprof \
  -XX:+PrintGCDetails \
  -XX:+PrintGCTimeStamps \
  -Xloggc:/logs/gc.log \
  -XX:+UseGCLogFileRotation \
  -XX:NumberOfGCLogFiles=5 \
  -XX:GCLogFileSize=100M \
  MyApplication
```

### 개발 환경 설정
```bash
# 개발용 설정 (빠른 시작, 디버깅 편의)
java -server \
  -Xms1g -Xmx2g \
  -XX:+UseG1GC \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:+PrintGCDetails \
  -Dspring.profiles.active=dev \
  MyApplication
```

### 마이크로서비스 환경 설정
```bash
# 컨테이너 환경 최적화
java -XX:+UseContainerSupport \
  -XX:InitialRAMPercentage=50.0 \
  -XX:MaxRAMPercentage=80.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  MyApplication
```

## 모니터링 및 디버깅 옵션

#### GC 로깅
```bash
# Java 9+ GC 로깅
-Xlog:gc*:gc.log:time,tags

# Java 8 GC 로깅
-XX:+PrintGC
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-Xloggc:gc.log
```

#### 메모리 덤프
```bash
# OOM 발생시 힙 덤프 생성
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/path/to/dumps/

# JVM 종료시 힙 덤프 생성
-XX:+HeapDumpOnCtrlBreak
```

#### 성능 분석
```bash
# JIT 컴파일 로깅
-XX:+PrintCompilation
-XX:+PrintInlining

# 클래스 로딩 로깅
-XX:+TraceClassLoading
-XX:+TraceClassUnloading
```

## 면접 핵심 포인트

### 자주 묻는 질문들

**Q: Xmx와 Xms를 동일하게 설정하는 이유는?**
- 힙 크기 조정으로 인한 오버헤드 방지
- 안정적인 메모리 사용량 예측
- GC 성능 최적화

**Q: 프로덕션 환경에서 반드시 설정해야 할 옵션은?**
- `-XX:+HeapDumpOnOutOfMemoryError`: OOM 분석을 위한 덤프
- GC 로깅: 성능 모니터링
- 적절한 힙 크기 설정
- 컨테이너 환경에서는 `-XX:+UseContainerSupport`

**Q: G1GC vs Parallel GC 선택 기준은?**
- **G1GC**: 짧은 일시정지가 중요한 웹 애플리케이션
- **Parallel GC**: 배치 처리 등 처리량이 중요한 경우
- **ZGC**: 매우 큰 힙(100GB+)을 사용하는 경우

## 실무 주의사항

### 성능 튜닝 시 고려사항
1. **측정 우선**: 추측하지 말고 성능을 측정
2. **점진적 변경**: 한 번에 하나씩 옵션 변경
3. **모니터링**: GC 로그와 APM 도구 활용
4. **환경별 차이**: 개발/운영 환경 분리

### 흔한 실수
```bash
# 잘못된 설정 예시
-Xms512m -Xmx4g    # 초기값과 최대값 차이가 큰 경우
-XX:NewRatio=1      # Young:Old 비율을 1:1로 설정 (비효율적)
```

## 핵심 요약

### 필수 암기 사항
- **메모리 옵션**: -Xmx(최대힙), -Xms(초기힙), -Xss(스택)
- **GC 옵션**: -XX:+UseG1GC, -XX:MaxGCPauseMillis
- **모니터링**: -XX:+HeapDumpOnOutOfMemoryError
- **컨테이너**: -XX:+UseContainerSupport

### 실무 팁
- 힙 크기는 시스템 메모리의 70% 이하로 설정
- GC 일시정지 시간은 200ms 이하 목표
- 운영 환경에서는 반드시 GC 로깅 활성화
- 컨테이너에서는 RAM 비율 기반 설정 사용