package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HighlightQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JpaHighlightEventRepository highlightRepository;
    private final JpaStreamSessionRepository sessionRepository;

    public List<HighlightResponse> getHighlightsBySessionId(String streamId, String sessionId) {
        // 프론트엔드가 실시간 탭에 있어서 sessionId를 안 보낸 경우
        if (sessionId == null || sessionId.equals("realtime")) {
            return sessionRepository.findActiveSession(streamId)
                .map(session -> highlightRepository.findAllByStreamIdAndSessionId(streamId, session.getSessionId()))
                .orElse(List.of()) // 만약 현재 방송 중이 아니면 빈 배열 반환
                .stream()
                .sorted((a, b) -> b.getPeakFirepower().compareTo(a.getPeakFirepower()))
                .limit(6)
                .map(HighlightResponse::from)
                .toList();
        }

        // 프론트엔드가 특정 과거 방송 탭을 누른 경우
        return highlightRepository.findAllByStreamIdAndSessionId(streamId, sessionId)
            .stream()
            .map(HighlightResponse::from)
            .toList();
    }
}
