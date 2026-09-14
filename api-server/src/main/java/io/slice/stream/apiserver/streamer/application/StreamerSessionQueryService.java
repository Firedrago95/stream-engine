package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse.StreamerSessionItemDto;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreamerSessionQueryService {

    private final JpaStreamSessionRepository sessionRepository;

    public StreamerSessionHistoryResponse getSessionHistory(String channelId, int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size > 0 && size <= 50) ? size : 10;
        Pageable pageable = PageRequest.of(validPage, validSize);

        Page<StreamSessionEntity> sessionPage = sessionRepository.findByStreamIdOrderByStartedAtDesc(
            channelId, pageable
        );

        Instant now = Instant.now();

        List<StreamerSessionItemDto> items = sessionPage.getContent().stream()
            .map(session -> {
                Instant start = session.getStartedAt();
                Instant end = session.getEndedAt() != null ? session.getEndedAt() : now;
                long durationSeconds = Math.max(0L, Duration.between(start, end).getSeconds());

                int peak = session.getPeakViewers() != null ? session.getPeakViewers() : 0;
                int avg = session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0;

                return new StreamerSessionItemDto(
                    session.getSessionId(),
                    session.getTitle(),
                    session.getCategoryName(),
                    start,
                    session.getEndedAt(),
                    durationSeconds,
                    peak,
                    avg,
                    session.getSessionFollowerGrowth(),
                    session.getSubscriberChatRatio(),
                    null
                );
            })
            .toList();

        log.debug("스트리머 세션 전적 히스토리 조회 완료: channelId={}, page={}, items={}",
            channelId, validPage, items.size());

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
