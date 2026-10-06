package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.analysis.application.service.ReLiveSessionMerger;
import io.slice.stream.apiserver.global.config.SessionProperties;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse.StreamerSessionItemDto;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StreamerSessionQueryService {

    private final JpaStreamSessionRepository sessionRepository;
    private final ReLiveSessionMerger reLiveSessionMerger;
    private final SessionProperties sessionProperties;

    public StreamerSessionQueryService(JpaStreamSessionRepository sessionRepository) {
        this(sessionRepository, new ReLiveSessionMerger(), new SessionProperties(null, null, 50));
    }

    public StreamerSessionQueryService(
        JpaStreamSessionRepository sessionRepository,
        ReLiveSessionMerger reLiveSessionMerger,
        SessionProperties sessionProperties
    ) {
        this.sessionRepository = sessionRepository;
        this.reLiveSessionMerger = reLiveSessionMerger;
        this.sessionProperties = sessionProperties;
    }

    public StreamerSessionHistoryResponse getSessionHistory(String channelId, int page, int size) {
        return getSessionHistory(channelId, page, size, false);
    }

    public StreamerSessionHistoryResponse getSessionHistory(String channelId, int page, int size, boolean paidPromotionOnly) {
        int validPage = Math.max(0, page);
        int validSize = (size > 0 && size <= 50) ? size : 10;
        Pageable pageable = PageRequest.of(validPage, validSize);

        long noiseThresholdSeconds = sessionProperties.noiseThreshold().getSeconds();
        Page<StreamSessionEntity> sessionPage = paidPromotionOnly
            ? sessionRepository.findValidSessionsByStreamIdAndPaidPromotionTrue(channelId, noiseThresholdSeconds, pageable)
            : sessionRepository.findValidSessionsByStreamId(channelId, noiseThresholdSeconds, pageable);

        Instant now = Instant.now();

        List<List<StreamSessionEntity>> groups = reLiveSessionMerger.groupSessions(sessionPage.getContent());
        List<StreamerSessionItemDto> items = groups.stream()
            .map(group -> reLiveSessionMerger.mergeToStreamerSessionItemDto(group, now))
            .sorted(Comparator.comparing(StreamerSessionItemDto::startedAt).reversed())
            .toList();

        log.debug("스트리머 세션 전적 히스토리 조회 완료: channelId={}, page={}, items={}, paidPromotionOnly={}",
            channelId, validPage, items.size(), paidPromotionOnly);

        return new StreamerSessionHistoryResponse(
            items,
            sessionPage.getNumber(),
            sessionPage.getSize(),
            sessionPage.getTotalElements(),
            sessionPage.getTotalPages(),
            sessionPage.hasNext()
        );
    }
}
