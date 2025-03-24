package com.emoney.til.async;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(25); // 대기 큐 용량이 넘어 갈 경우 새로운 스레드를 생성하여 작업을 처리하고 다시 반환한giut 다.
    executor.setThreadNamePrefix("Async-");

    // 거부 정책 설정 (큐가 가득 차고 모든 스레드가 사용 중일 때)
    executor.setRejectedExecutionHandler((r, e) -> {
      log.warn("Task rejected, thread pool is full and queue is full");
      // 여기서 알림 전송이나 추가 대응 로직 구현 가능
    });

    // 종료 시 실행 중인 작업이 완료될 때까지 대기
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60); // 최대 60초 대기

    executor.initialize();
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new CustomAsyncExceptionHandler();
  }

  // 비동기 메서드의 예외를 처리하는 핸들러
  public static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
      log.error("Async method '{}' threw exception: {}", method.getName(), ex.getMessage());
      log.error("Method parameters: {}", params);

      // 중요한 비동기 작업의 실패 시 추가 대응 예시
      // emailService.sendAlertEmail("Async method failed: " + method.getName());
      // retryService.scheduleRetry(method, params);
    }
  }
}
