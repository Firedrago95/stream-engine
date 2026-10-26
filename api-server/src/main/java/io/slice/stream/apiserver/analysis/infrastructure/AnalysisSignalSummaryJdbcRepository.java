package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.infrastructure.dto.AnalysisSignalSummaryDto;
import io.slice.stream.apiserver.analysis.infrastructure.dto.LiveViewerSummaryDto;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AnalysisSignalSummaryJdbcRepository {

    private static final String BATCH_UPSERT_SUMMARY_SQL = """
        INSERT INTO analysis_signals_summary (stream_id, session_id, status, firepower_avg, firepower_max, timestamp_minute, offset_ms)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (session_id, status, offset_ms)
        DO UPDATE SET
            firepower_avg = EXCLUDED.firepower_avg,
            firepower_max = EXCLUDED.firepower_max,
            timestamp_minute = LEAST(analysis_signals_summary.timestamp_minute, EXCLUDED.timestamp_minute)
        """;

    private static final String BATCH_UPSERT_LIVE_VIEWER_SQL = """
        INSERT INTO analysis_signals_summary (
            stream_id, session_id, status, firepower_avg, firepower_max,
            timestamp_minute, offset_ms, viewer_count
        ) VALUES (?, ?, 'NORMAL', 0, 0, ?, ?, ?)
        ON CONFLICT (session_id, status, offset_ms)
        DO UPDATE SET
            viewer_count = EXCLUDED.viewer_count
        """;

    private final JdbcTemplate jdbcTemplate;

    public void batchUpsertSummaries(List<AnalysisSignalSummaryDto> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            BATCH_UPSERT_SUMMARY_SQL,
            summaries,
            summaries.size(),
            (PreparedStatement ps, AnalysisSignalSummaryDto dto) -> {
                ps.setString(1, dto.streamId());
                ps.setString(2, dto.sessionId());
                ps.setString(3, dto.status());
                ps.setLong(4, dto.firepowerAvg());
                ps.setLong(5, dto.firepowerMax());
                ps.setTimestamp(6, Timestamp.from(dto.timestampMinute()));
                ps.setLong(7, dto.offsetMs());
            }
        );

        log.info("[Analysis-Summary-JDBC] 분석 신호 요약 {}건 벌크 업서트 완료", summaries.size());
    }

    public void batchUpsertLiveViewers(List<LiveViewerSummaryDto> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            BATCH_UPSERT_LIVE_VIEWER_SQL,
            summaries,
            summaries.size(),
            (PreparedStatement ps, LiveViewerSummaryDto dto) -> {
                ps.setString(1, dto.streamId());
                ps.setString(2, dto.sessionId());
                ps.setTimestamp(3, Timestamp.from(dto.timestampMinute()));
                ps.setLong(4, dto.offsetMs());
                ps.setInt(5, dto.viewerCount());
            }
        );

        log.info("[Analysis-Summary-JDBC] 라이브 시청자 요약 {}건 벌크 업서트 완료", summaries.size());
    }
}
