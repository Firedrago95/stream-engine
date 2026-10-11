package io.slice.stream.apiserver.admin.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<SystemConfigDto> getAllConfigs() {
        return systemConfigRepository.findAllByOrderByCategoryAscConfigKeyAsc().stream()
            .map(SystemConfigDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        return systemConfigRepository.findById(key)
            .map(entity -> {
                String val = entity.getConfigValue();
                if (val != null) {
                    cache.put(key, val);
                }
                return val;
            })
            .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        String val = get(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("[Config] 정수 변환 실패: key={}, value={}, 기본값 사용={}", key, val, defaultValue);
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public long getLong(String key, long defaultValue) {
        String val = get(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            log.warn("[Config] 긴정수 변환 실패: key={}, value={}, 기본값 사용={}", key, val, defaultValue);
            return defaultValue;
        }
    }

    @Transactional
    public void updateConfig(String key, String newValue) {
        SystemConfigEntity entity = systemConfigRepository.findById(key)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 설정 키입니다: " + key));

        entity.updateValue(newValue);
        systemConfigRepository.save(entity);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.remove(key);
                }
            });
        } else {
            cache.remove(key);
        }

        log.info("[Config] 시스템 파라미터 변경 완료: key={}, newValue={}", key, newValue);
    }

    public void clearCache() {
        cache.clear();
    }
}
