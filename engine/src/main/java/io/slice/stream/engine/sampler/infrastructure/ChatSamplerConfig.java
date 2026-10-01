package io.slice.stream.engine.sampler.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slice.stream.engine.sampler.domain.ChatSampleUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatSamplerConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatSamplerConfig.class);

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper chatSamplerObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public ChatSampleUploader chatSampleUploader() {
        log.info("로컬 스토리지 채팅 샘플러 업로더를 활성화합니다.");
        return new LocalStorageChatSampleUploader();
    }
}

