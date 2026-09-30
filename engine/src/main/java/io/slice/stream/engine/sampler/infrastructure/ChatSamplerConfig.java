package io.slice.stream.engine.sampler.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slice.stream.engine.sampler.domain.ChatSampleUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GoogleDriveProperties.class)
public class ChatSamplerConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatSamplerConfig.class);

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper chatSamplerObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public ChatSampleUploader chatSampleUploader(GoogleDriveProperties properties) {
        if (properties.enabled()) {
            try {
                log.info("Google Drive 업로더를 활성화합니다. (타겟 폴더: {})", properties.folderId());
                return GoogleDriveChatSampleUploader.create(properties);
            } catch (Exception e) {
                log.error("Google Drive 업로더 초기화 실패. 로컬 스토리지 업로더로 대체합니다: {}", e.getMessage(), e);
                return new LocalStorageChatSampleUploader();
            }
        }
        log.info("Google Drive 비활성화 상태. 로컬 스토리지 업로더를 사용합니다.");
        return new LocalStorageChatSampleUploader();
    }
}
