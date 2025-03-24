package com.emoney.til.async;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AsyncService {
  /**
   * 기본 비동기 메서드 (void 반환)
   */
  @Async("taskExecutor")
  public void asyncMethod(int i) {
    try {
      log.info("Execute method asynchronously. Thread: {} - Time: {} - Index :{}",
          Thread.currentThread().getName(), LocalDateTime.now(), i);

      // 작업 시뮬레이션
      Thread.sleep(1000);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Async method was interrupted", e);
    } catch (Exception e) {
      // 비동기 메서드의 예외는 호출자에게 전파되지 않으므로 로깅 필수
      log.error("Error in async method", e);
    }
  }

  /**
   * 결과를 반환하는 비동기 메서드
   */
  @Async("taskExecutor")
  public CompletableFuture<String> asyncMethodWithResult(String param) {
    try {
      log.info("Execute method with result asynchronously. Thread: {} - Param: {}",
          Thread.currentThread().getName(), param);

      // 작업 시뮬레이션
      Thread.sleep(2000);

      return CompletableFuture.completedFuture("Result for: " + param);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Async method with result was interrupted", e);
      return CompletableFuture.failedFuture(e);
    } catch (Exception e) {
      log.error("Error in async method with result", e);
      return CompletableFuture.failedFuture(e);
    }
  }

  /**
   * 의도적으로 예외를 발생시키는 비동기 메서드
   */
  @Async("taskExecutor")
  public CompletableFuture<String> asyncMethodWithException() {
    log.info("Execute method that will throw exception. Thread: {}",
        Thread.currentThread().getName());

    try {
      Thread.sleep(500);
      throw new RuntimeException("Simulated error in async method");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return CompletableFuture.failedFuture(e);
    }
  }
}
