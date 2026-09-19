package io.slice.stream.collector.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class CollectorConfig {

    @Value("${chzzk.api.base-url:https://api.chzzk.naver.com}")
    private String chzzkApiBaseUrl;

    @Value("${kafka.topic.chat:chat-messages}")
    private String chatTopic;

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
            .baseUrl(chzzkApiBaseUrl)
            .defaultHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
            .build();
    }

    @Bean
    public NewTopic chatTopic() {
        return TopicBuilder.name(chatTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
