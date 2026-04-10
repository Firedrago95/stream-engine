package io.slice.stream.engine.global.config;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ThreadPoolConfig {

    @Value("${app.thread.mode:virtual}")
    private String threadMode;

    @Value("${app.thread.pool-size:200}")
    private int poolSize;

    @Bean
    public ExecutorService virtualThreadExecutor() {
        if ("platform".equalsIgnoreCase(threadMode)) {
            log.info("[플랫폼 스레드 모드] Thread Pool Size: {}", poolSize);
            return Executors.newFixedThreadPool(poolSize);
        } else {
            log.info("[가상 스레드 모드] 실행");
            return Executors.newVirtualThreadPerTaskExecutor();
        }
    }

    @Bean
    public HttpClient httpClient(ExecutorService executorService) {
        return HttpClient.newBuilder()
            .executor(executorService)
            .build();
    }
}
