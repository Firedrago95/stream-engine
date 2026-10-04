package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
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

    private final JpaHighlightEventRepository highlightRepository;
    private final JpaStreamSessionRepository sessionRepository;

    public List<HighlightResponse> getHighlightsBySessionId(String streamId, String sessionId) {
        if (sessionId == null || sessionId.equals("realtime")) {
            return sessionRepository.findActiveSession(streamId)
                .map(session -> highlightRepository.findAllByStreamIdAndSessionId(streamId, session.getSessionId()))
                .orElse(List.of())
                .stream()
                .sorted((a, b) -> b.getPeakFirepower().compareTo(a.getPeakFirepower()))
                .map(HighlightResponse::from)
                .toList();
        }

        return highlightRepository.findAllByStreamIdAndSessionId(streamId, sessionId)
            .stream()
            .sorted((a, b) -> b.getPeakFirepower().compareTo(a.getPeakFirepower()))
            .map(HighlightResponse::from)
            .toList();
    }
}

