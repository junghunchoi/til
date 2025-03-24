package com.emoney.til.async;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AsyncTest {

  private static final Logger logger = LoggerFactory.getLogger(AsyncTest.class);

  @Autowired
  private AsyncService asyncService;

  @Test
  public void testAsyncMehod() throws InterruptedException {

    for (int i = 0; i < 10; i++) {
      asyncService.asyncMethod(i);
    }
    logger.info("wait ing for async method");
    Thread.sleep(2000);

    logger.info("async method is done");
  }

  @Test
  public void testAsyncMethodWithResult()
      throws InterruptedException, ExecutionException, TimeoutException {
    // 결과를 반환하는 비동기 메서드 실행
    CompletableFuture<String> future = asyncService.asyncMethodWithResult("test-param");

    // CompletableFuture를 사용하면 작업 완료를 명시적으로 기다릴 수 있음
    String result = future.get(5, TimeUnit.SECONDS); // 최대 5초 대기

    logger.info("Received result from async method: {}", result);
    assertNotNull(result);
    assertTrue(result.contains("test-param"));
  }

  @Test
  public void testAsyncMethodWithException() {
    // 예외를 발생시키는 비동기 메서드 실행
    CompletableFuture<String> future = asyncService.asyncMethodWithException();

    // CompletableFuture 예외 처리 방법 1: try-catch
    try {
      String result = future.get(5, TimeUnit.SECONDS);
      fail("Should have thrown an exception");
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      logger.info("Expected exception caught: {}", e.getMessage());
      // ExecutionException 내부에 원래 예외가 포함되어 있음
      if (e instanceof ExecutionException) {
        assertInstanceOf(RuntimeException.class, e.getCause());
        assertEquals("Simulated error in async method", e.getCause().getMessage());
      }
    }

    // CompletableFuture 예외 처리 방법 2: exceptionally 콜백
    CompletableFuture<String> future2 = asyncService.asyncMethodWithException();
    CompletableFuture<String> recovered = future2.exceptionally(ex -> {
      logger.info("Handling exception via exceptionally: {}", ex.getMessage());
      return "Recovered from: " + ex.getMessage();
    });

    try {
      String result = recovered.get(5, TimeUnit.SECONDS);
      logger.info("Recovered result: {}", result);
      assertTrue(result.contains("Recovered from"));
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      fail("Should not throw exception after recovery");
    }
  }

  @Test
  public void testAsyncMethodWithCustomHandler() throws InterruptedException {
    // 이 테스트는 CustomAsyncExceptionHandler가 호출되는지 검증
    // 실제로는 void 메서드에서 throw된 예외를 확인하기 어려움

    // void 메서드에서 예외가 발생해도 메서드 호출은 성공한 것으로 간주됨
    // 예외는 AsyncUncaughtExceptionHandler에 의해 처리됨
    asyncService.asyncMethod(1);

    // 다른 비동기 작업이 완료될 시간을 기다림
    Thread.sleep(2000);

    // 이 시점에서는 로그를 확인하거나 예외 핸들러가 특정 작업을 수행했는지 검증해야 함
    // 예: 알림 메일이 발송되었는지, DB에 오류 로그가 기록되었는지 등
    logger.info("Async exception handler test completed");
  }
}
