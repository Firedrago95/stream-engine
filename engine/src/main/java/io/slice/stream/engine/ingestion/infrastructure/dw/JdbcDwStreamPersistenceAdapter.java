package io.slice.stream.engine.ingestion.infrastructure.dw;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.DwStreamPersistencePort;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcDwStreamPersistenceAdapter implements DwStreamPersistencePort {

    private static final Duration OFFLINE_THRESHOLD = Duration.ofMinutes(6);

    private static final String SELECT_OPEN_SESSIONS_SQL = """
        SELECT session_id, stream_id, started_at
        FROM stream_sessions
        WHERE ended_at IS NULL
        """;

    private static final String SELECT_LAST_RECORDED_AT_SQL = """
        SELECT session_id, MAX(recorded_at) AS last_recorded_at
        FROM view_metric_timelines
        WHERE recorded_at >= ? AND session_id = ANY(?)
        GROUP BY session_id
        """;

    private static final String CLOSE_SESSION_SQL = """
        UPDATE stream_sessions
        SET ended_at = ?
        WHERE session_id = ? AND ended_at IS NULL
        """;

    private static final String CLOSE_ACTIVE_SEGMENT_BY_SESSION_SQL = """
        UPDATE stream_session_segments
        SET ended_at = ?,
            end_offset_ms = ?
        WHERE session_id = ? AND ended_at IS NULL
        """;

    private static final String UPSERT_SESSION_SQL = """
        INSERT INTO stream_sessions (
            session_id, stream_id, streamer_name, title, category_name,
            started_at, peak_viewers, paid_promotion, is_adult
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (session_id) DO UPDATE SET
            streamer_name = EXCLUDED.streamer_name,
            title = EXCLUDED.title,
            category_name = EXCLUDED.category_name,
            peak_viewers = GREATEST(stream_sessions.peak_viewers, EXCLUDED.peak_viewers),
            paid_promotion = stream_sessions.paid_promotion OR EXCLUDED.paid_promotion,
            is_adult = EXCLUDED.is_adult,
            ended_at = NULL
        WHERE stream_sessions.streamer_name IS DISTINCT FROM EXCLUDED.streamer_name
           OR stream_sessions.title IS DISTINCT FROM EXCLUDED.title
           OR stream_sessions.category_name IS DISTINCT FROM EXCLUDED.category_name
           OR EXCLUDED.peak_viewers > stream_sessions.peak_viewers
           OR (EXCLUDED.paid_promotion = TRUE AND stream_sessions.paid_promotion = FALSE)
           OR stream_sessions.is_adult IS DISTINCT FROM EXCLUDED.is_adult
           OR stream_sessions.ended_at IS NOT NULL
        """;

    private static final String SELECT_OPEN_SEGMENTS_SQL = """
        SELECT id, session_id, title, category_name, started_at
        FROM stream_session_segments
        WHERE session_id = ANY(?) AND ended_at IS NULL
        """;

    private static final String SELECT_LATEST_SEGMENTS_SQL = """
        SELECT DISTINCT ON (session_id)
               id, session_id, title, category_name, started_at
        FROM stream_session_segments
        WHERE session_id = ANY(?)
        ORDER BY session_id, start_offset_ms DESC, id DESC
        """;

    private static final String REOPEN_SEGMENT_BY_ID_SQL = """
        UPDATE stream_session_segments
        SET ended_at = NULL,
            end_offset_ms = NULL
        WHERE id = ?
        """;

    private static final String CLOSE_SEGMENT_BY_ID_SQL = """
        UPDATE stream_session_segments
        SET ended_at = ?,
            end_offset_ms = ?
        WHERE id = ?
        """;

    private static final String INSERT_SEGMENT_SQL = """
        INSERT INTO stream_session_segments (
            session_id, stream_id, title, category_name,
            started_at, start_offset_ms, paid_promotion, is_adult
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (session_id) WHERE ended_at IS NULL DO NOTHING
        """;

    private static final String UPSERT_TIMELINE_SQL = """
        INSERT INTO view_metric_timelines (
            session_id, stream_id, recorded_at, viewer_count
        ) VALUES (?, ?, ?, ?)
        ON CONFLICT (session_id, recorded_at) DO UPDATE SET
            viewer_count = EXCLUDED.viewer_count
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void persistSnapshot(
        List<StreamTarget> liveTargets,
        StreamUpdateResults updateResults,
        Instant recordedAt
    ) {
        Map<String, StreamTarget> uniqueTargets = deduplicateTargets(liveTargets);

        closeEndedSessions(uniqueTargets, updateResults, recordedAt);

        if (uniqueTargets.isEmpty()) {
            return;
        }

        List<StreamTarget> activeList = new ArrayList<>(uniqueTargets.values());
        upsertActiveSessions(activeList, recordedAt);
        syncActiveSegments(activeList, recordedAt);
        insertViewMetricTimelines(activeList, recordedAt);
    }

    private Map<String, StreamTarget> deduplicateTargets(List<StreamTarget> liveTargets) {
        if (liveTargets == null || liveTargets.isEmpty()) {
            return Map.of();
        }
        Map<String, StreamTarget> uniqueMap = new LinkedHashMap<>();
        for (StreamTarget target : liveTargets) {
            if (target != null && target.channelId() != null && target.liveId() > 0) {
                uniqueMap.putIfAbsent(target.channelId(), target);
            }
        }
        return uniqueMap;
    }

    private void closeEndedSessions(
        Map<String, StreamTarget> uniqueTargets,
        StreamUpdateResults updateResults,
        Instant recordedAt
    ) {
        List<OpenSession> openSessions = fetchOpenSessions();
        if (openSessions.isEmpty()) {
            return;
        }

        Instant minStartedAt = openSessions.stream()
            .map(OpenSession::startedAt)
            .min(Instant::compareTo)
            .orElse(recordedAt);
        List<String> openSessionIds = openSessions.stream()
            .map(OpenSession::sessionId)
            .toList();
        Map<String, Instant> lastRecordedMap = fetchLastRecordedAtBySessions(openSessionIds, minStartedAt);

        Set<String> explicitClosedIds = extractClosedChannelIds(updateResults);
        Instant offlineCutoff = recordedAt.minus(OFFLINE_THRESHOLD);

        List<SessionClosure> closures = new ArrayList<>();
        for (OpenSession open : openSessions) {
            Instant lastActiveAt = lastRecordedMap.getOrDefault(open.sessionId(), open.startedAt());
            StreamTarget currentLive = uniqueTargets.get(open.streamId());

            if (currentLive != null) {
                String currentSessionId = String.valueOf(currentLive.liveId());
                if (!open.sessionId().equals(currentSessionId)) {
                    Instant closedAt = resolveSwitchedEndedAt(lastActiveAt, open.startedAt(), currentLive.startedAt());
                    closures.add(new SessionClosure(open, closedAt));
                }
            } else if (explicitClosedIds.contains(open.streamId()) || !lastActiveAt.isAfter(offlineCutoff)) {
                closures.add(new SessionClosure(open, normalizeEndedAt(lastActiveAt, open.startedAt())));
            }
        }

        if (!closures.isEmpty()) {
            executeSessionClosures(closures);
        }
    }

    private void executeSessionClosures(List<SessionClosure> closures) {
        jdbcTemplate.batchUpdate(CLOSE_SESSION_SQL, closures, closures.size(), (ps, closure) -> {
            ps.setObject(1, toOffsetDateTime(closure.endedAt()));
            ps.setString(2, closure.session().sessionId());
        });

        jdbcTemplate.batchUpdate(CLOSE_ACTIVE_SEGMENT_BY_SESSION_SQL, closures, closures.size(), (ps, closure) -> {
            OpenSession session = closure.session();
            long endOffsetMs = Math.max(0L, Duration.between(session.startedAt(), closure.endedAt()).toMillis());
            ps.setObject(1, toOffsetDateTime(closure.endedAt()));
            ps.setLong(2, endOffsetMs);
            ps.setString(3, session.sessionId());
        });

        log.info("[DW-Sync] 방송 종료 세션 마감 완료 - 대상: {}건", closures.size());
    }

    private void upsertActiveSessions(List<StreamTarget> activeTargets, Instant recordedAt) {
        jdbcTemplate.batchUpdate(UPSERT_SESSION_SQL, activeTargets, activeTargets.size(), (ps, target) -> {
            Instant startedAt = target.startedAt() != null ? target.startedAt() : recordedAt;
            ps.setString(1, String.valueOf(target.liveId()));
            ps.setString(2, target.channelId());
            ps.setString(3, target.channelName() != null ? target.channelName() : target.channelId());
            ps.setString(4, target.liveTitle());
            ps.setString(5, target.categoryName());
            ps.setObject(6, toOffsetDateTime(startedAt));
            ps.setInt(7, target.concurrentUserCount());
            ps.setBoolean(8, Boolean.TRUE.equals(target.paidPromotion()));
            ps.setBoolean(9, Boolean.TRUE.equals(target.adult()));
        });
    }

    private void syncActiveSegments(List<StreamTarget> activeTargets, Instant recordedAt) {
        List<String> sessionIds = activeTargets.stream()
            .map(t -> String.valueOf(t.liveId()))
            .toList();

        Map<String, SegmentRow> activeSegmentMap = fetchSegmentsBySql(SELECT_OPEN_SEGMENTS_SQL, sessionIds);
        Map<String, SegmentRow> latestSegmentMap = fetchSegmentsBySql(SELECT_LATEST_SEGMENTS_SQL, sessionIds);

        List<Long> segmentIdsToReopen = new ArrayList<>();
        List<SegmentClosure> segmentClosures = new ArrayList<>();
        List<NewSegmentParam> newSegments = new ArrayList<>();

        for (StreamTarget target : activeTargets) {
            String sessionId = String.valueOf(target.liveId());
            Instant sessionStartedAt = target.startedAt() != null ? target.startedAt() : recordedAt;
            SegmentRow activeSegment = activeSegmentMap.get(sessionId);
            boolean paid = Boolean.TRUE.equals(target.paidPromotion());
            boolean adult = Boolean.TRUE.equals(target.adult());

            if (activeSegment == null) {
                SegmentRow lastSegment = latestSegmentMap.get(sessionId);
                if (lastSegment != null && !isTitleOrCategoryChanged(lastSegment, target)) {
                    segmentIdsToReopen.add(lastSegment.id());
                } else {
                    long startOffsetMs = lastSegment != null
                        ? Math.max(0L, Duration.between(sessionStartedAt, recordedAt).toMillis())
                        : 0L;
                    Instant segmentStartedAt = lastSegment != null ? recordedAt : sessionStartedAt;
                    newSegments.add(new NewSegmentParam(
                        sessionId,
                        target.channelId(),
                        target.liveTitle(),
                        target.categoryName(),
                        segmentStartedAt,
                        startOffsetMs,
                        paid,
                        adult
                    ));
                }
                continue;
            }

            if (isTitleOrCategoryChanged(activeSegment, target)) {
                long offsetMs = Math.max(0L, Duration.between(sessionStartedAt, recordedAt).toMillis());
                segmentClosures.add(new SegmentClosure(activeSegment.id(), recordedAt, offsetMs));
                newSegments.add(new NewSegmentParam(
                    sessionId,
                    target.channelId(),
                    target.liveTitle(),
                    target.categoryName(),
                    recordedAt,
                    offsetMs,
                    paid,
                    adult
                ));
            }
        }

        if (!segmentIdsToReopen.isEmpty()) {
            jdbcTemplate.batchUpdate(REOPEN_SEGMENT_BY_ID_SQL, segmentIdsToReopen, segmentIdsToReopen.size(), (ps, id) -> {
                ps.setLong(1, id);
            });
        }

        if (!segmentClosures.isEmpty()) {
            jdbcTemplate.batchUpdate(CLOSE_SEGMENT_BY_ID_SQL, segmentClosures, segmentClosures.size(), (ps, closure) -> {
                ps.setObject(1, toOffsetDateTime(closure.endedAt()));
                ps.setLong(2, closure.endOffsetMs());
                ps.setLong(3, closure.segmentId());
            });
        }

        if (!newSegments.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_SEGMENT_SQL, newSegments, newSegments.size(), (ps, seg) -> {
                ps.setString(1, seg.sessionId());
                ps.setString(2, seg.streamId());
                ps.setString(3, seg.title());
                ps.setString(4, seg.categoryName());
                ps.setObject(5, toOffsetDateTime(seg.startedAt()));
                ps.setLong(6, seg.startOffsetMs());
                ps.setBoolean(7, seg.paidPromotion());
                ps.setBoolean(8, seg.isAdult());
            });
        }
    }

    private boolean isTitleOrCategoryChanged(SegmentRow segment, StreamTarget target) {
        return !Objects.equals(segment.title(), target.liveTitle())
            || !Objects.equals(segment.categoryName(), target.categoryName());
    }

    private void insertViewMetricTimelines(List<StreamTarget> activeTargets, Instant recordedAt) {
        OffsetDateTime recordedOffset = toOffsetDateTime(recordedAt);
        jdbcTemplate.batchUpdate(UPSERT_TIMELINE_SQL, activeTargets, activeTargets.size(), (ps, target) -> {
            ps.setString(1, String.valueOf(target.liveId()));
            ps.setString(2, target.channelId());
            ps.setObject(3, recordedOffset);
            ps.setInt(4, target.concurrentUserCount());
        });
    }

    private List<OpenSession> fetchOpenSessions() {
        return jdbcTemplate.query(SELECT_OPEN_SESSIONS_SQL, (rs, rowNum) -> new OpenSession(
            rs.getString("session_id"),
            rs.getString("stream_id"),
            extractInstant(rs, "started_at")
        ));
    }

    private Map<String, Instant> fetchLastRecordedAtBySessions(List<String> sessionIds, Instant minStartedAt) {
        return jdbcTemplate.execute((Connection con) -> {
            Array sqlArray = con.createArrayOf("varchar", sessionIds.toArray(String[]::new));
            try (PreparedStatement ps = con.prepareStatement(SELECT_LAST_RECORDED_AT_SQL)) {
                ps.setObject(1, toOffsetDateTime(minStartedAt));
                ps.setArray(2, sqlArray);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Instant> result = new HashMap<>();
                    while (rs.next()) {
                        Instant lastRecorded = extractInstant(rs, "last_recorded_at");
                        if (lastRecorded != null) {
                            result.put(rs.getString("session_id"), lastRecorded);
                        }
                    }
                    return result;
                }
            } finally {
                sqlArray.free();
            }
        });
    }

    private Map<String, SegmentRow> fetchSegmentsBySql(String sql, List<String> sessionIds) {
        return jdbcTemplate.execute((Connection con) -> {
            Array sqlArray = con.createArrayOf("varchar", sessionIds.toArray(String[]::new));
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setArray(1, sqlArray);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, SegmentRow> result = new HashMap<>();
                    while (rs.next()) {
                        SegmentRow segment = new SegmentRow(
                            rs.getLong("id"),
                            rs.getString("session_id"),
                            rs.getString("title"),
                            rs.getString("category_name"),
                            extractInstant(rs, "started_at")
                        );
                        result.putIfAbsent(segment.sessionId(), segment);
                    }
                    return result;
                }
            } finally {
                sqlArray.free();
            }
        });
    }

    private Set<String> extractClosedChannelIds(StreamUpdateResults updateResults) {
        if (updateResults == null || updateResults.closedStreamIds() == null || updateResults.closedStreamIds().isEmpty()) {
            return Set.of();
        }
        Set<String> closedIds = new HashSet<>();
        for (StreamTarget closed : updateResults.closedStreamIds()) {
            if (closed != null && closed.channelId() != null) {
                closedIds.add(closed.channelId());
            }
        }
        return closedIds;
    }

    private Instant resolveSwitchedEndedAt(Instant lastActiveAt, Instant oldStartedAt, Instant newStartedAt) {
        Instant candidate = lastActiveAt;
        if (newStartedAt != null && candidate.isAfter(newStartedAt)) {
            candidate = newStartedAt;
        }
        return normalizeEndedAt(candidate, oldStartedAt);
    }

    private Instant normalizeEndedAt(Instant candidateEndedAt, Instant sessionStartedAt) {
        if (candidateEndedAt == null || candidateEndedAt.isBefore(sessionStartedAt)) {
            return sessionStartedAt;
        }
        return candidateEndedAt;
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private Instant extractInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
        return odt != null ? odt.toInstant() : null;
    }

    private record OpenSession(
        String sessionId,
        String streamId,
        Instant startedAt
    ) {
    }

    private record SessionClosure(
        OpenSession session,
        Instant endedAt
    ) {
    }

    private record SegmentRow(
        long id,
        String sessionId,
        String title,
        String categoryName,
        Instant startedAt
    ) {
    }

    private record SegmentClosure(
        long segmentId,
        Instant endedAt,
        long endOffsetMs
    ) {
    }

    private record NewSegmentParam(
        String sessionId,
        String streamId,
        String title,
        String categoryName,
        Instant startedAt,
        long startOffsetMs,
        boolean paidPromotion,
        boolean isAdult
    ) {
    }
}
