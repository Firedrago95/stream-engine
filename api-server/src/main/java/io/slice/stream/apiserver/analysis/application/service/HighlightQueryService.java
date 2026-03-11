package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    private final JpaHighlightEventRepository repository;

    public List<HighlightResponse> getHighlightsByDate(String streamId, LocalDate date) {
        Instant startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS);

        return repository.findAllByStreamIdAndDateRange(streamId, startOfDay, endOfDay).stream()
            .map(HighlightResponse::from)
            .toList();
    }
}
