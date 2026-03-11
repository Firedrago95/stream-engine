package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.infrastructure.JpaAnalysisSignalRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisDataCleanupService {

    private final JpaAnalysisSignalRepository jpaRepository;

    @Transactional
    public void cleanupOldData(Instant cutoffTime) {
        int summaryCount = jpaRepository.rollupOldSignals(cutoffTime);
        log.info("[Cleanup] {} 건의 데이터가 요약 테이블에 반영되었습니다.", summaryCount);

        int deleteCount = jpaRepository.deleteOlderThan(cutoffTime);
        log.info("[Cleanup] 3일이 지난 원본 데이터 {} 건이 삭제되었습니다.", deleteCount);
    }
}
