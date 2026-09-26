package io.slice.stream.engine.ingestion.application;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncPromotionInspector {

    private final StreamDiscoveryClient streamDiscoveryClient;
    private final StreamRepository streamRepository;
    private final ApiServerClient apiServerClient;
    private final ExecutorService virtualThreadExecutor;

    public void inspectChangedStreamsAsync(Collection<ChangedStream> changedStreams) {
        if (changedStreams == null || changedStreams.isEmpty()) {
            return;
        }

        for (ChangedStream changed : changedStreams) {
            virtualThreadExecutor.submit(() -> inspectSingleStream(changed));
        }
    }

    private void inspectSingleStream(ChangedStream changed) {
        try {
            List<StreamTarget> detailedTargets = streamDiscoveryClient.fetchLiveStreams(Set.of(changed.streamId()));
            if (detailedTargets == null || detailedTargets.isEmpty()) {
                return;
            }

            StreamTarget target = detailedTargets.get(0);
            Boolean latestPaidPromotion = target.paidPromotion();

            log.info("[비동기 광고 확인] 스트림: {}, 방제: '{}', 카테고리: '{}', 치지직 최신광고상태: {}, 이전감지상태: {}",
                changed.streamId(), changed.newTitle(), changed.newCategory(), latestPaidPromotion, changed.paidPromotion());

            if (!Objects.equals(latestPaidPromotion, changed.paidPromotion())) {
                log.info("[광고 상태 보정] 스트림: {}, 상태 변경: {} -> {}",
                    changed.streamId(), changed.paidPromotion(), latestPaidPromotion);

                streamRepository.updatePaidPromotion(changed.streamId(), Boolean.TRUE.equals(latestPaidPromotion));

                ChangedStream corrected = new ChangedStream(
                    changed.streamId(),
                    changed.liveId(),
                    changed.oldTitle(),
                    changed.newTitle(),
                    changed.oldCategory(),
                    changed.newCategory(),
                    changed.changedAt(),
                    changed.changeOffsetMs(),
                    latestPaidPromotion
                );

                apiServerClient.recordNewSegments(List.of(corrected));
            }
        } catch (Exception e) {
            log.warn("[비동기 광고 확인 실패] 스트림: {}, 사유: {}", changed.streamId(), e.getMessage());
        }
    }
}
