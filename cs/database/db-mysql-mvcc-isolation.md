# MySQL MVCC와 트랜잭션 격리 수준

## MVCC (Multi-Version Concurrency Control) 개념

### MVCC란?
**Multi-Version Concurrency Control**은 동시성 제어를 위해 여러 버전의 데이터를 관리하는 메커니즘입니다. 읽기와 쓰기가 서로 블로킹하지 않도록 하여 높은 동시성을 제공합니다.

### InnoDB의 MVCC 구현
```sql
-- MVCC 동작 예시
-- 세션 1
START TRANSACTION;
SELECT * FROM accounts WHERE id = 1;  -- balance: 1000
-- 결과: id=1, balance=1000, version=1

-- 세션 2 (동시 실행)
START TRANSACTION;
UPDATE accounts SET balance = 1500 WHERE id = 1;
COMMIT;
-- 새로운 버전 생성: balance=1500, version=2

-- 세션 1 (계속)
SELECT * FROM accounts WHERE id = 1;  -- 여전히 balance: 1000
-- MVCC에 의해 트랜잭션 시작 시점의 버전을 읽음
COMMIT;

-- 이제 최신 버전 읽기
SELECT * FROM accounts WHERE id = 1;  -- balance: 1500
```

### Undo Log와 Read View
```sql
-- InnoDB의 MVCC 내부 구조 이해

-- 1. 언두 로그 체인 예시
/*
현재 데이터: (id=1, balance=1500, trx_id=102)
             ↓
언두 로그1:  (id=1, balance=1000, trx_id=101)
             ↓  
언두 로그2:  (id=1, balance=500, trx_id=100)
*/

-- 2. Read View 구조
/*
Read View 생성 시점의 정보:
- m_low_limit_id: 100 (가장 오래된 활성 트랜잭션)
- m_up_limit_id: 105 (다음에 할당될 트랜잭션 ID)
- m_creator_trx_id: 103 (현재 트랜잭션 ID)
- m_ids: [101, 102, 104] (활성 트랜잭션 목록)
*/

-- 3. 가시성 판단 알고리즘
-- 트랜잭션 ID = 101인 데이터를 읽을 때:
-- if (101 < 100) return VISIBLE;           -- 커밋된 오래된 트랜잭션
-- if (101 >= 105) return NOT_VISIBLE;      -- 미래 트랜잭션  
-- if (101 == 103) return VISIBLE;          -- 자신의 변경사항
-- if (101 in [101,102,104]) return NOT_VISIBLE; -- 활성 트랜잭션
```

## 트랜잭션 격리 수준

### 1. READ UNCOMMITTED (Level 0)
```sql
-- 격리 수준 설정
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;

-- 더티 리드 발생 예시
-- 세션 1
START TRANSACTION;
UPDATE accounts SET balance = 2000 WHERE id = 1;
-- 아직 COMMIT 안함

-- 세션 2
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;  -- 2000 (더티 리드)
-- 커밋되지 않은 데이터를 읽음

-- 세션 1에서 ROLLBACK 시
ROLLBACK;

-- 세션 2에서 다시 읽으면
SELECT balance FROM accounts WHERE id = 1;  -- 1000 (원래 값)
-- 데이터 불일치 발생
```

### 2. READ COMMITTED (Level 1)
```sql
-- 격리 수준 설정 (Oracle, PostgreSQL 기본값)
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- 논리퍼블릭 리드 방지, 논리퍼블릭 리드는 여전히 가능
-- 세션 1
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;  -- 1000

-- 세션 2
START TRANSACTION;
UPDATE accounts SET balance = 2000 WHERE id = 1;
COMMIT;

-- 세션 1에서 다시 읽으면
SELECT balance FROM accounts WHERE id = 1;  -- 2000 (논리퍼블릭 리드)
-- 같은 트랜잭션 내에서 다른 결과

-- MySQL에서는 각 SELECT마다 새로운 Read View 생성
SHOW ENGINE INNODB STATUS\G
/*
Read View가 매번 새로 생성되어 
가장 최근에 커밋된 데이터를 읽음
*/
```

### 3. REPEATABLE READ (Level 2) - MySQL 기본값
```sql
-- MySQL InnoDB 기본 격리 수준
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- 논리퍼블릭 리드 방지
-- 세션 1
START TRANSACTION;
SELECT balance FROM accounts WHERE id = 1;  -- 1000

-- 세션 2
START TRANSACTION;
UPDATE accounts SET balance = 2000 WHERE id = 1;
COMMIT;

-- 세션 1에서 다시 읽어도
SELECT balance FROM accounts WHERE id = 1;  -- 1000 (일관된 읽기)
-- 트랜잭션 시작 시점의 스냅샷을 유지

-- 팬텀 리드 방지 (MySQL의 특별한 구현)
-- 세션 1
SELECT COUNT(*) FROM accounts WHERE balance > 500;  -- 3

-- 세션 2
INSERT INTO accounts (id, balance) VALUES (4, 1000);
COMMIT;

-- 세션 1에서 다시 실행해도
SELECT COUNT(*) FROM accounts WHERE balance > 500;  -- 3 (팬텀 리드 방지)
-- Next-Key Lock으로 팬텀 리드까지 방지
```

### 4. SERIALIZABLE (Level 3)
```sql
-- 가장 높은 격리 수준
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- 모든 SELECT에 자동으로 LOCK IN SHARE MODE 적용
-- 세션 1
START TRANSACTION;
SELECT * FROM accounts WHERE id = 1;  -- 공유 락 획득

-- 세션 2
START TRANSACTION;
UPDATE accounts SET balance = 2000 WHERE id = 1;  -- 대기 (블로킹)
-- 세션 1이 커밋될 때까지 대기

-- 성능 확인
SHOW PROCESSLIST;
-- State: Waiting for table metadata lock
```

## 락킹 메커니즘과 MVCC 상호작용

### Record Lock과 Gap Lock
```sql
-- Next-Key Lock = Record Lock + Gap Lock
CREATE TABLE test_locks (
    id INT PRIMARY KEY,
    value INT,
    INDEX idx_value (value)
);

INSERT INTO test_locks VALUES (1, 10), (3, 30), (5, 50);

-- 세션 1: Gap Lock 동작
START TRANSACTION;
SELECT * FROM test_locks WHERE value = 20 FOR UPDATE;
-- value=20인 레코드는 없지만 (10, 30) 사이에 Gap Lock

-- 세션 2: 블로킹되는 삽입
INSERT INTO test_locks VALUES (2, 15);  -- 블로킹됨
INSERT INTO test_locks VALUES (4, 35);  -- 성공 (다른 Gap)

-- 락 정보 확인
SELECT 
    r.trx_id,
    r.trx_mysql_thread_id,
    r.trx_query,
    l.lock_type,
    l.lock_mode,
    l.lock_status,
    l.lock_data
FROM information_schema.innodb_trx r
LEFT JOIN information_schema.innodb_locks l 
    ON r.trx_id = l.lock_trx_id;
```

### 데드락 예시와 해결
```sql
-- 데드락 발생 시나리오
-- 세션 1
START TRANSACTION;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;

-- 세션 2
START TRANSACTION;  
UPDATE accounts SET balance = balance - 50 WHERE id = 2;

-- 세션 1 (계속)
UPDATE accounts SET balance = balance + 100 WHERE id = 2;  -- 대기

-- 세션 2 (계속)
UPDATE accounts SET balance = balance + 50 WHERE id = 1;   -- 데드락!

-- MySQL이 자동으로 감지하고 한 트랜잭션을 롤백
-- ERROR 1213 (40001): Deadlock found when trying to get lock

-- 데드락 정보 확인
SHOW ENGINE INNODB STATUS\G
/*
LATEST DETECTED DEADLOCK 섹션에서 
데드락 발생 상황과 희생된 트랜잭션 정보 확인 가능
*/

-- 데드락 방지 전략
-- 1. 일관된 순서로 리소스 접근
START TRANSACTION;
UPDATE accounts SET balance = balance - 100 WHERE id = LEAST(1, 2);
UPDATE accounts SET balance = balance + 100 WHERE id = GREATEST(1, 2);
COMMIT;

-- 2. 짧은 트랜잭션 유지
-- 3. 적절한 인덱스 사용으로 락 범위 최소화
```

## 실무 최적화 전략

### 1. 적절한 격리 수준 선택
```sql
-- 애플리케이션별 격리 수준 선택 가이드

-- 1. 실시간 대시보드 (약간의 데이터 불일치 허용)
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- 2. 금융 거래 (데이터 일관성 중요)
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- 3. 배치 처리 (성능 우선)
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;

-- 4. 회계 시스템 (완벽한 일관성 필요)
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- 트랜잭션별 격리 수준 설정
START TRANSACTION WITH CONSISTENT SNAPSHOT;  -- REPEATABLE READ
-- 또는
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
```

### 2. 트랜잭션 크기 최적화
```sql
-- 잘못된 예: 큰 트랜잭션
START TRANSACTION;
-- 1만 건의 데이터 처리
FOR i = 1 TO 10000 DO
    UPDATE large_table SET processed = 1 WHERE id = i;
END FOR;
COMMIT;  -- 오랜 시간 락 유지

-- 개선된 예: 배치 단위 처리
DELIMITER $$
CREATE PROCEDURE process_in_batches()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE batch_size INT DEFAULT 1000;
    DECLARE current_id INT DEFAULT 1;
    
    WHILE current_id <= 10000 DO
        START TRANSACTION;
        
        UPDATE large_table 
        SET processed = 1 
        WHERE id BETWEEN current_id AND (current_id + batch_size - 1);
        
        COMMIT;
        
        SET current_id = current_id + batch_size;
    END WHILE;
END$$
DELIMITER ;
```

### 3. 락 대기 최적화
```sql
-- 락 대기 시간 설정
SET SESSION innodb_lock_wait_timeout = 5;  -- 5초 후 타임아웃

-- 락 대기 없이 즉시 실패
SELECT * FROM accounts WHERE id = 1 FOR UPDATE NOWAIT;

-- 지정된 시간만 대기 (MySQL 8.0+)
SELECT * FROM accounts WHERE id = 1 FOR UPDATE SKIP LOCKED;

-- 락이 걸린 행은 건너뛰기
SELECT * FROM queue_table 
WHERE status = 'PENDING' 
FOR UPDATE SKIP LOCKED
LIMIT 10;
```

### 4. MVCC 성능 모니터링
```sql
-- Undo Log 모니터링
SELECT 
    COUNT(*) as active_transactions,
    MAX(TIME_TO_SEC(TIMEDIFF(NOW(), trx_started))) as longest_trx_sec
FROM information_schema.innodb_trx;

-- 히스토리 리스트 길이 확인
SHOW ENGINE INNODB STATUS\G
/*
History list length: 1000  -- 이 값이 크면 언두 로그가 많이 쌓임
*/

-- 정리되지 않은 언두 로그로 인한 성능 영향
SELECT 
    table_schema,
    table_name,
    ROUND(data_length / 1024 / 1024, 2) AS data_mb,
    ROUND(index_length / 1024 / 1024, 2) AS index_mb
FROM information_schema.tables 
WHERE engine = 'InnoDB'
ORDER BY (data_length + index_length) DESC;

-- 긴 트랜잭션 찾기
SELECT 
    trx_id,
    trx_state,
    trx_started,
    TIME_TO_SEC(TIMEDIFF(NOW(), trx_started)) as duration_sec,
    trx_mysql_thread_id,
    trx_query
FROM information_schema.innodb_trx
WHERE TIME_TO_SEC(TIMEDIFF(NOW(), trx_started)) > 30
ORDER BY trx_started;
```

## 실무 문제 해결 사례

### 케이스 1: 읽기 성능 최적화
```sql
-- 문제: 대량의 읽기 쿼리로 인한 성능 저하
-- 읽기 전용 복제본 활용
-- Master-Slave 구성에서 읽기는 Slave에서 처리

-- 읽기 전용 트랜잭션 명시
START TRANSACTION READ ONLY;
SELECT * FROM large_report_table WHERE date_range = '2024-01';
COMMIT;

-- 또는 autocommit으로 가벼운 읽기
SET autocommit = 1;
SELECT * FROM product_catalog WHERE category = 'electronics';
```

### 케이스 2: 핫스팟 테이블 최적화
```sql
-- 문제: 자주 업데이트되는 테이블로 인한 락 경합
-- 해결책 1: 파티셔닝
CREATE TABLE user_activities (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    activity_date DATE,
    activity_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) 
PARTITION BY RANGE (TO_DAYS(activity_date)) (
    PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
    PARTITION p202402 VALUES LESS THAN (TO_DAYS('2024-03-01')),
    PARTITION p202403 VALUES LESS THAN (TO_DAYS('2024-04-01'))
);

-- 해결책 2: 카운터 테이블 샤딩
CREATE TABLE page_view_counters (
    page_id INT,
    counter_id TINYINT,  -- 0~9 (10개 샤드)
    view_count BIGINT DEFAULT 0,
    PRIMARY KEY (page_id, counter_id)
);

-- 업데이트 시 랜덤 샤드 선택
UPDATE page_view_counters 
SET view_count = view_count + 1 
WHERE page_id = ? AND counter_id = FLOOR(RAND() * 10);

-- 조회 시 합계 계산
SELECT page_id, SUM(view_count) as total_views
FROM page_view_counters 
WHERE page_id = ?
GROUP BY page_id;
```

### 케이스 3: 배치 처리 최적화
```sql
-- 문제: 야간 배치 작업이 온라인 트랜잭션에 영향
-- 해결책: 온라인 DDL과 pt-online-schema-change 활용

-- MySQL 8.0 온라인 DDL
ALTER TABLE large_table 
ADD COLUMN new_status VARCHAR(20) DEFAULT 'ACTIVE',
ALGORITHM=INPLACE, LOCK=NONE;

-- 배치 업데이트를 작은 청크로 분할
DELIMITER $$
CREATE PROCEDURE update_in_chunks()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE rows_affected INT;
    
    REPEAT
        UPDATE large_table 
        SET processed = 1 
        WHERE processed = 0 
        LIMIT 1000;
        
        SET rows_affected = ROW_COUNT();
        
        -- 다른 트랜잭션에게 기회 제공
        SELECT SLEEP(0.1);
        
    UNTIL rows_affected = 0 END REPEAT;
END$$
DELIMITER ;
```

## 인터뷰 꼬리질문 대비

### Q1: "MySQL의 REPEATABLE READ에서 팬텀 리드가 방지되는 이유는?"
**답변 포인트:**
- **Next-Key Lock**: Record Lock + Gap Lock으로 범위 락킹
- **MVCC와 조합**: 일관된 스냅샷 읽기로 팬텀 리드 방지
- **표준과 차이**: SQL 표준에서는 SERIALIZABLE에서만 팬텀 리드 방지
- **성능 고려사항**: 락 대기 시간 증가 가능성

### Q2: "긴 트랜잭션이 시스템에 미치는 영향은?"
**답변 포인트:**
- **Undo Log 증가**: 메모리 사용량 증가와 정리 지연
- **MVCC 오버헤드**: 오래된 버전 유지로 인한 성능 저하
- **락 홀딩**: 다른 트랜잭션의 대기 시간 증가
- **복제 지연**: Master-Slave 복제에서 지연 발생

### Q3: "READ COMMITTED와 REPEATABLE READ의 실무적 선택 기준은?"
**답변 포인트:**
- **데이터 일관성 요구**: 금융권은 REPEATABLE READ, 일반 웹은 READ COMMITTED
- **동시성 vs 일관성**: READ COMMITTED가 더 높은 동시성 제공
- **애플리케이션 특성**: 짧은 트랜잭션은 READ COMMITTED 적합
- **성능 모니터링**: 실제 워크로드 기반 성능 테스트 필요

## 실무 적용 팁

1. **트랜잭션 설계**: 가능한 짧고 작은 단위로 설계
2. **격리 수준 선택**: 비즈니스 요구사항에 맞는 최소 격리 수준 사용
3. **모니터링**: 긴 트랜잭션과 락 대기 상황 지속 모니터링
4. **인덱스 최적화**: 적절한 인덱스로 락 범위 최소화
5. **배치 처리**: 대량 작업은 청크 단위로 분할 처리

MySQL의 MVCC와 트랜잭션 격리 수준을 올바르게 이해하고 활용하면, 높은 동시성과 데이터 일관성을 동시에 달성할 수 있습니다. 실무에서는 성능과 일관성 사이의 균형을 찾는 것이 핵심입니다.