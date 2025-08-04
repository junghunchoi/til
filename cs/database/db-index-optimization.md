# 데이터베이스 인덱스 설계와 성능 최적화

## 인덱스 기본 개념과 구조

### B-Tree 인덱스 구조
```sql
-- 인덱스가 없는 테이블 검색 (Full Table Scan)
SELECT * FROM orders WHERE customer_id = 12345;
-- 1백만 개 레코드를 모두 스캔해야 함

-- 인덱스 생성
CREATE INDEX idx_orders_customer_id ON orders(customer_id);

-- 인덱스를 사용한 검색 (Index Seek)
SELECT * FROM orders WHERE customer_id = 12345;
-- 로그 시간 복잡도 O(log n)으로 빠른 검색

/*
B-Tree 인덱스 구조:
                   [Root Node]
                  /     |     \
            [Branch]  [Branch]  [Branch]
           /    |    /    |    /    |    \
       [Leaf] [Leaf] [Leaf] [Leaf] [Leaf] [Leaf]
        |      |      |      |      |      |
     [Data]  [Data]  [Data]  [Data]  [Data]  [Data]

각 Leaf Node는 실제 데이터 페이지를 가리키는 포인터를 포함
*/
```

### 인덱스 타입별 특성
```sql
-- 1. 클러스터드 인덱스 (Clustered Index)
-- 데이터가 인덱스 순서대로 물리적으로 정렬됨
-- 테이블당 하나만 가능 (보통 Primary Key)
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,  -- 클러스터드 인덱스
    customer_id BIGINT,
    order_date DATETIME,
    total_amount DECIMAL(10,2)
);

-- 2. 비클러스터드 인덱스 (Non-Clustered Index)  
-- 데이터와 별도로 인덱스 구조 생성
-- 테이블당 여러 개 가능
CREATE INDEX idx_orders_customer_date ON orders(customer_id, order_date);
CREATE INDEX idx_orders_amount ON orders(total_amount);

-- 3. 유니크 인덱스 (Unique Index)
-- 중복값 허용하지 않음
CREATE UNIQUE INDEX idx_orders_order_number ON orders(order_number);

-- 4. 부분 인덱스 (Partial Index) - PostgreSQL
-- 조건을 만족하는 행만 인덱싱
CREATE INDEX idx_orders_active 
ON orders(customer_id) 
WHERE status = 'ACTIVE';

-- 5. 함수 기반 인덱스 (Function-based Index)
-- 함수나 표현식 결과에 대한 인덱스
CREATE INDEX idx_orders_year 
ON orders(EXTRACT(YEAR FROM order_date));
```

## 복합 인덱스 설계 원칙

### 컬럼 순서의 중요성
```sql
-- 잘못된 복합 인덱스 설계
CREATE INDEX idx_bad ON orders(order_date, customer_id, status);

-- 다음 쿼리들의 인덱스 활용도:
SELECT * FROM orders WHERE customer_id = 123;           -- 인덱스 사용 불가
SELECT * FROM orders WHERE status = 'ACTIVE';           -- 인덱스 사용 불가  
SELECT * FROM orders WHERE order_date > '2024-01-01' 
                      AND customer_id = 123;           -- 부분적 사용

-- 올바른 복합 인덱스 설계 (선택도가 높은 순서)
CREATE INDEX idx_good ON orders(customer_id, status, order_date);

-- 모든 쿼리에서 효율적인 인덱스 사용 가능:
SELECT * FROM orders WHERE customer_id = 123;                    -- ✓ 효율적
SELECT * FROM orders WHERE customer_id = 123 AND status = 'ACTIVE'; -- ✓ 효율적
SELECT * FROM orders WHERE customer_id = 123 
                      AND status = 'ACTIVE' 
                      AND order_date > '2024-01-01';            -- ✓ 효율적
```

### 인덱스 설계 실전 예제
```sql
-- 전자상거래 주문 테이블
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    order_date DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount DECIMAL(10,2),
    payment_method VARCHAR(20),
    shipping_address_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 자주 사용되는 쿼리 패턴 분석
-- 1. 고객별 주문 조회 (가장 빈번)
SELECT * FROM orders WHERE customer_id = ? ORDER BY order_date DESC;

-- 2. 상태별 주문 관리
SELECT * FROM orders WHERE status = 'PENDING' ORDER BY created_at;

-- 3. 기간별 매출 분석
SELECT SUM(total_amount) FROM orders 
WHERE order_date BETWEEN '2024-01-01' AND '2024-12-31';

-- 4. 고객의 특정 기간 주문
SELECT * FROM orders 
WHERE customer_id = ? AND order_date BETWEEN ? AND ?;

-- 최적화된 인덱스 설계
-- 인덱스 1: 고객별 주문 조회 최적화
CREATE INDEX idx_orders_customer_date ON orders(customer_id, order_date DESC);

-- 인덱스 2: 상태별 관리 쿼리 최적화
CREATE INDEX idx_orders_status_created ON orders(status, created_at);

-- 인덱스 3: 기간별 분석 쿼리 최적화 (커버링 인덱스)
CREATE INDEX idx_orders_date_amount ON orders(order_date, total_amount);

-- 인덱스 4: 결제 방법별 분석
CREATE INDEX idx_orders_payment_date ON orders(payment_method, order_date);
```

## 커버링 인덱스 활용

### 커버링 인덱스의 이점
```sql
-- 일반적인 쿼리 (Non-Covering Index)
CREATE INDEX idx_orders_customer ON orders(customer_id);

SELECT customer_id, order_date, total_amount 
FROM orders 
WHERE customer_id = 123;

/*
실행 계획:
1. 인덱스에서 customer_id = 123인 행들의 위치 찾기
2. 각 행에 대해 실제 테이블에서 order_date, total_amount 읽기 (Random I/O)
*/

-- 커버링 인덱스 (Covering Index)
CREATE INDEX idx_orders_customer_covering 
ON orders(customer_id, order_date, total_amount);

SELECT customer_id, order_date, total_amount 
FROM orders 
WHERE customer_id = 123;

/*
실행 계획:
1. 인덱스에서 모든 필요한 데이터를 직접 읽기 (Sequential I/O)
2. 테이블 접근 불필요 (Key Lookup 없음)
*/
```

### MySQL InnoDB의 커버링 인덱스
```sql
-- InnoDB는 자동으로 PRIMARY KEY를 보조 인덱스에 포함
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    category_id INT,
    name VARCHAR(255),
    price DECIMAL(10,2),
    stock_quantity INT
);

CREATE INDEX idx_products_category ON products(category_id);

-- 다음 쿼리는 자동으로 커버링 인덱스 효과
SELECT id, category_id FROM products WHERE category_id = 1;
-- 인덱스: (category_id, id) - id는 자동 포함

-- 더 많은 컬럼이 필요한 경우 명시적 커버링 인덱스
CREATE INDEX idx_products_category_covering 
ON products(category_id, price, stock_quantity);

SELECT category_id, price, stock_quantity 
FROM products 
WHERE category_id = 1 AND price > 10000;
```

## 인덱스 성능 최적화 기법

### 인덱스 힌트 사용
```sql
-- MySQL 인덱스 힌트
SELECT * FROM orders 
USE INDEX (idx_orders_customer_date)
WHERE customer_id = 123;

-- 특정 인덱스 강제 사용
SELECT * FROM orders 
FORCE INDEX (idx_orders_status_created)
WHERE status = 'PENDING';

-- 인덱스 사용 금지
SELECT * FROM orders 
IGNORE INDEX (idx_orders_customer_date)
WHERE customer_id = 123;

-- PostgreSQL 힌트 (pg_hint_plan 확장)
/*+ IndexScan(orders idx_orders_customer_date) */
SELECT * FROM orders WHERE customer_id = 123;
```

### 인덱스 통계 관리
```sql
-- MySQL 인덱스 통계 업데이트
ANALYZE TABLE orders;

-- 특정 인덱스 통계 확인
SHOW INDEX FROM orders;

-- PostgreSQL 통계 업데이트
ANALYZE orders;

-- 통계 정보 확인
SELECT 
    schemaname,
    tablename,
    attname,
    n_distinct,
    correlation
FROM pg_stats 
WHERE tablename = 'orders';

-- SQL Server 통계 업데이트
UPDATE STATISTICS orders;

-- 통계 정보 확인
DBCC SHOW_STATISTICS('orders', 'idx_orders_customer_date');
```

## 인덱스 최적화 실전 사례

### 케이스 1: 페이징 성능 최적화
```sql
-- 문제가 되는 페이징 쿼리
SELECT * FROM orders 
ORDER BY created_at DESC 
LIMIT 1000000, 20;  -- 매우 느림

-- 해결책 1: 커서 기반 페이징
SELECT * FROM orders 
WHERE created_at < '2024-01-01 12:00:00'  -- 마지막 조회 시점
ORDER BY created_at DESC 
LIMIT 20;

-- 해결책 2: 커버링 인덱스 + 조인
SELECT o.* FROM orders o
INNER JOIN (
    SELECT id FROM orders 
    ORDER BY created_at DESC 
    LIMIT 1000000, 20
) t ON o.id = t.id;

-- 최적화를 위한 인덱스
CREATE INDEX idx_orders_created_id ON orders(created_at DESC, id);
```

### 케이스 2: 검색 성능 최적화
```sql
-- 문제: LIKE 검색이 느림
SELECT * FROM products WHERE name LIKE '%smartphone%';  -- Full Table Scan

-- 해결책 1: 전문 검색 인덱스 (MySQL)
CREATE FULLTEXT INDEX ft_products_name ON products(name);
SELECT * FROM products WHERE MATCH(name) AGAINST('smartphone');

-- 해결책 2: 역방향 인덱스 + LIKE 최적화
CREATE INDEX idx_products_name_reverse ON products(REVERSE(name));

-- 앞쪽 매칭은 일반 인덱스
SELECT * FROM products WHERE name LIKE 'smartphone%';  -- 인덱스 사용 가능

-- 뒤쪽 매칭은 역방향 인덱스
SELECT * FROM products 
WHERE REVERSE(name) LIKE REVERSE('%phone');

-- 해결책 3: 복합 검색 조건 최적화
CREATE INDEX idx_products_category_name ON products(category_id, name);
SELECT * FROM products 
WHERE category_id = 1 AND name LIKE 'smart%';  -- 효율적
```

### 케이스 3: 집계 쿼리 최적화
```sql
-- 문제: 느린 집계 쿼리
SELECT 
    customer_id,
    COUNT(*) as order_count,
    SUM(total_amount) as total_spent
FROM orders 
WHERE order_date >= '2024-01-01'
GROUP BY customer_id;

-- 해결책: 집계용 커버링 인덱스
CREATE INDEX idx_orders_date_customer_amount 
ON orders(order_date, customer_id, total_amount);

-- 더 나은 성능을 위한 부분 인덱스 (PostgreSQL)
CREATE INDEX idx_orders_recent_customer_amount 
ON orders(customer_id, total_amount)
WHERE order_date >= '2024-01-01';
```

## 인덱스 모니터링과 분석

### 실행 계획 분석
```sql
-- MySQL 실행 계획
EXPLAIN FORMAT=JSON
SELECT * FROM orders 
WHERE customer_id = 123 
  AND order_date > '2024-01-01';

-- PostgreSQL 실행 계획  
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT * FROM orders 
WHERE customer_id = 123 
  AND order_date > '2024-01-01';

-- SQL Server 실행 계획
SET STATISTICS IO ON;
SET STATISTICS TIME ON;
SELECT * FROM orders 
WHERE customer_id = 123 
  AND order_date > '2024-01-01';
```

### 인덱스 사용률 모니터링
```sql
-- MySQL 인덱스 사용률 확인
SELECT 
    object_schema,
    object_name,
    index_name,
    count_read,
    count_write,
    sum_timer_read,
    sum_timer_write
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE object_schema = 'your_database'
ORDER BY sum_timer_read DESC;

-- 사용되지 않는 인덱스 찾기
SELECT 
    object_schema,
    object_name,
    index_name
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE count_read = 0 AND count_write = 0
  AND object_schema = 'your_database';

-- PostgreSQL 인덱스 사용률
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_tup_read DESC;
```

### 인덱스 크기와 유지 비용 분석
```sql
-- MySQL 인덱스 크기 확인
SELECT 
    table_name,
    index_name,
    ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS size_mb
FROM mysql.innodb_index_stats 
WHERE stat_name = 'size' 
  AND database_name = 'your_database'
ORDER BY stat_value DESC;

-- PostgreSQL 인덱스 크기
SELECT 
    indexrelname AS index_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC;
```

## 인덱스 안티패턴과 해결책

### 안티패턴 1: 과도한 인덱스
```sql
-- 문제: 너무 많은 인덱스
CREATE INDEX idx1 ON orders(customer_id);
CREATE INDEX idx2 ON orders(order_date);  
CREATE INDEX idx3 ON orders(status);
CREATE INDEX idx4 ON orders(customer_id, order_date);
CREATE INDEX idx5 ON orders(customer_id, status);
CREATE INDEX idx6 ON orders(order_date, status);
CREATE INDEX idx7 ON orders(customer_id, order_date, status);  -- 이것만으로 충분

-- 해결책: 복합 인덱스 하나로 통합
DROP INDEX idx1, idx2, idx3, idx4, idx5, idx6;
-- idx7만 유지하면 대부분의 쿼리 패턴을 커버
```

### 안티패턴 2: 저선택도 컬럼 인덱스
```sql
-- 문제: 성별처럼 선택도가 낮은 컬럼에 단독 인덱스
CREATE INDEX idx_users_gender ON users(gender);  -- 'M', 'F' 두 값만 존재

-- 해결책: 복합 인덱스의 후순위로 배치
CREATE INDEX idx_users_age_gender ON users(age, gender);
-- age로 먼저 필터링하고 gender로 추가 필터링
```

### 안티패턴 3: 함수 사용으로 인한 인덱스 무효화
```sql
-- 문제: 함수 사용으로 인덱스 사용 불가
SELECT * FROM orders WHERE YEAR(order_date) = 2024;        -- 인덱스 사용 안됨
SELECT * FROM orders WHERE DATE(created_at) = '2024-01-01'; -- 인덱스 사용 안됨

-- 해결책 1: 범위 조건으로 변경
SELECT * FROM orders 
WHERE order_date >= '2024-01-01' 
  AND order_date < '2025-01-01';

SELECT * FROM orders 
WHERE created_at >= '2024-01-01 00:00:00' 
  AND created_at < '2024-01-02 00:00:00';

-- 해결책 2: 함수 기반 인덱스 생성
CREATE INDEX idx_orders_year ON orders((YEAR(order_date)));  -- MySQL 8.0+
CREATE INDEX idx_orders_date ON orders((DATE(created_at)));
```

## 인터뷰 꼬리질문 대비

### Q1: "인덱스가 있는데도 성능이 느린 경우는?"
**답변 포인트:**
- **통계 정보 오래됨**: ANALYZE TABLE로 통계 업데이트 필요
- **인덱스 선택도 낮음**: 카디널리티가 낮은 컬럼의 단독 인덱스
- **복합 인덱스 순서 잘못**: 선택도 높은 컬럼을 앞에 배치
- **함수 사용**: WHERE절에서 컬럼에 함수 적용 시 인덱스 무효화

### Q2: "복합 인덱스에서 컬럼 순서는 어떻게 정하나요?"
**답변 포인트:**
- **등호 조건 우선**: = 조건인 컬럼을 앞에 배치
- **선택도 순서**: 카디널리티가 높은 컬럼부터
- **쿼리 패턴 고려**: 가장 자주 사용되는 조건 순서
- **범위 조건 마지막**: BETWEEN, >, < 조건은 뒤쪽에 배치

### Q3: "클러스터드 인덱스와 논클러스터드 인덱스의 차이는?"
**답변 포인트:**
- **데이터 정렬**: 클러스터드는 데이터가 인덱스 순으로 물리적 정렬
- **개수 제한**: 클러스터드는 테이블당 하나, 논클러스터드는 여러 개 가능
- **검색 성능**: 클러스터드가 일반적으로 더 빠름 (데이터 직접 포함)
- **삽입 성능**: 클러스터드는 삽입 시 정렬 유지로 오버헤드 있음

## 실무 베스트 프랙티스

1. **쿼리 패턴 분석**: 실제 사용되는 쿼리를 기반으로 인덱스 설계
2. **선택적 인덱스 생성**: 모든 컬럼에 인덱스 생성 금지
3. **정기적 모니터링**: 인덱스 사용률과 성능 지속 모니터링
4. **통계 관리**: 정기적인 통계 정보 업데이트
5. **점진적 최적화**: 성능 문제가 있는 쿼리부터 우선 최적화

올바른 인덱스 설계는 데이터베이스 성능의 핵심입니다. 비즈니스 요구사항과 쿼리 패턴을 정확히 분석하여 최적의 인덱스 전략을 수립하는 것이 중요합니다.