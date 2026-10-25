package io.slice.stream.engine.ingestion.infrastructure.dw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.testcontainer.postgres.DwPostgresTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@Import(JdbcDwStreamPersistenceAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JdbcDwStreamPersistenceAdapterTest implements DwPostgresTestSupport {

    @Autowired
    private JdbcDwStreamPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE stream_sessions, stream_session_segments, view_metric_timelines RESTART IDENTITY CASCADE");
    }

    @Test
    void 방송_시작_시_세션과_초기_세그먼트와_시청자_타임라인이_저장된다() {
        Instant startedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant recordedAt = Instant.parse("2026-10-24T03:00:30Z");
        StreamTarget target = new StreamTarget(
            "ch_1", "침착맨", "chat_1", 1001L, "소통 방송", 3200, "thumb.jpg", "Talk", startedAt, false, true
        );

        adapter.persistSnapshot(List.of(target), emptyResults(recordedAt), recordedAt);

        Map<String, Object> session = jdbcTemplate.queryForMap(
            "SELECT * FROM stream_sessions WHERE session_id = '1001'"
        );
        assertThat(session.get("stream_id")).isEqualTo("ch_1");
        assertThat(session.get("title")).isEqualTo("소통 방송");
        assertThat(session.get("category_name")).isEqualTo("Talk");
        assertThat(session.get("peak_viewers")).isEqualTo(3200);
        assertThat(session.get("ended_at")).isNull();

        List<Map<String, Object>> segments = jdbcTemplate.queryForList(
            "SELECT * FROM stream_session_segments WHERE session_id = '1001'"
        );
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).get("start_offset_ms")).isEqualTo(0L);
        assertThat(segments.get(0).get("ended_at")).isNull();

        List<Map<String, Object>> timelines = jdbcTemplate.queryForList(
            "SELECT * FROM view_metric_timelines WHERE session_id = '1001'"
        );
        assertThat(timelines).hasSize(1);
        assertThat(timelines.get(0).get("viewer_count")).isEqualTo(3200);
    }

    @Test
    void 동일_세션_재적재_시_피크_시청자수가_갱신되고_세그먼트는_중복_생성되지_않는다() {
        Instant startedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant t1 = Instant.parse("2026-10-24T03:00:30Z");
        Instant t2 = Instant.parse("2026-10-24T03:01:00Z");

        StreamTarget first = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "소통 방송", 2000, "thumb.jpg", "Talk", startedAt);
        StreamTarget second = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "소통 방송", 4500, "thumb.jpg", "Talk", startedAt);

        adapter.persistSnapshot(List.of(first), emptyResults(t1), t1);
        adapter.persistSnapshot(List.of(second), emptyResults(t2), t2);

        Map<String, Object> session = jdbcTemplate.queryForMap(
            "SELECT * FROM stream_sessions WHERE session_id = '1001'"
        );
        assertThat(session.get("peak_viewers")).isEqualTo(4500);

        Integer segmentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stream_session_segments WHERE session_id = '1001'", Integer.class
        );
        assertThat(segmentCount).isEqualTo(1);

        Integer timelineCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM view_metric_timelines WHERE session_id = '1001'", Integer.class
        );
        assertThat(timelineCount).isEqualTo(2);
    }

    @Test
    void 동일한_메타데이터와_피크_이하_시청자수로_재적재_시_불필요한_세션_튜플_갱신이_발생하지_않는다() {
        Instant startedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant t1 = Instant.parse("2026-10-24T03:00:30Z");
        Instant t2 = Instant.parse("2026-10-24T03:01:00Z");

        StreamTarget first = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "소통 방송", 3000, "thumb.jpg", "Talk", startedAt);
        StreamTarget second = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "소통 방송", 2800, "thumb.jpg", "Talk", startedAt);

        adapter.persistSnapshot(List.of(first), emptyResults(t1), t1);
        String ctidBefore = jdbcTemplate.queryForObject(
            "SELECT ctid::text FROM stream_sessions WHERE session_id = '1001'", String.class
        );

        adapter.persistSnapshot(List.of(second), emptyResults(t2), t2);
        String ctidAfter = jdbcTemplate.queryForObject(
            "SELECT ctid::text FROM stream_sessions WHERE session_id = '1001'", String.class
        );

        assertThat(ctidAfter).isEqualTo(ctidBefore);
    }

    @Test
    void 방제나_카테고리가_변경되면_이전_세그먼트를_마감하고_새_세그먼트를_생성한다() {
        Instant startedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant t1 = Instant.parse("2026-10-24T03:00:30Z");
        Instant t2 = Instant.parse("2026-10-24T03:10:00Z");

        StreamTarget initial = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "1부 토크", 2000, "thumb.jpg", "Talk", startedAt);
        StreamTarget changed = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "2부 롤", 3500, "thumb.jpg", "리그 오브 레전드", startedAt);

        adapter.persistSnapshot(List.of(initial), emptyResults(t1), t1);
        adapter.persistSnapshot(List.of(changed), emptyResults(t2), t2);

        List<Map<String, Object>> segments = jdbcTemplate.queryForList(
            "SELECT * FROM stream_session_segments WHERE session_id = '1001' ORDER BY start_offset_ms ASC"
        );
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).get("title")).isEqualTo("1부 토크");
        assertThat(segments.get(0).get("end_offset_ms")).isEqualTo(600_000L);
        assertThat(segments.get(0).get("ended_at")).isNotNull();

        assertThat(segments.get(1).get("title")).isEqualTo("2부 롤");
        assertThat(segments.get(1).get("start_offset_ms")).isEqualTo(600_000L);
        assertThat(segments.get(1).get("ended_at")).isNull();
    }

    @Test
    void 방송이_종료되면_마지막_관측_시각을_기준으로_세션과_활성_세그먼트를_마감한다() {
        Instant startedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant lastRecordedAt = Instant.parse("2026-10-24T03:01:00Z");
        Instant closeDetectedAt = Instant.parse("2026-10-24T03:01:30Z");

        StreamTarget live = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "방제", 3000, "thumb.jpg", "Talk", startedAt);
        adapter.persistSnapshot(List.of(live), emptyResults(lastRecordedAt), lastRecordedAt);

        StreamUpdateResults closeResults = new StreamUpdateResults(
            Set.of(), Set.of(live), Set.of(), closeDetectedAt
        );
        adapter.persistSnapshot(List.of(), closeResults, closeDetectedAt);

        Map<String, Object> session = jdbcTemplate.queryForMap(
            "SELECT * FROM stream_sessions WHERE session_id = '1001'"
        );
        Timestamp endedAt = (Timestamp) session.get("ended_at");
        assertThat(endedAt.toInstant()).isEqualTo(lastRecordedAt);

        Map<String, Object> segment = jdbcTemplate.queryForMap(
            "SELECT * FROM stream_session_segments WHERE session_id = '1001'"
        );
        Timestamp segEndedAt = (Timestamp) segment.get("ended_at");
        assertThat(segEndedAt.toInstant()).isEqualTo(lastRecordedAt);
        assertThat(segment.get("end_offset_ms")).isEqualTo(60_000L);
    }

    @Test
    void 리방_감지_시_이전_방송의_마지막_관측시각이_새_방송_시작시각보다_미래이면_새_방송_시작시각으로_맞춰_시간_겹침을_방지한다() {
        Instant oldStartedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant lastRecordedAt = Instant.parse("2026-10-24T03:00:30Z");
        Instant newStartedAt = Instant.parse("2026-10-24T03:00:15Z");
        Instant t2 = Instant.parse("2026-10-24T03:01:00Z");

        StreamTarget oldLive = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "이전 방송", 1500, "thumb.jpg", "Talk", oldStartedAt);
        StreamTarget newLive = new StreamTarget("ch_1", "침착맨", "chat_2", 1002L, "리방 후 방송", 2500, "thumb.jpg", "Talk", newStartedAt);

        adapter.persistSnapshot(List.of(oldLive), emptyResults(lastRecordedAt), lastRecordedAt);
        adapter.persistSnapshot(List.of(newLive), emptyResults(t2), t2);

        Map<String, Object> oldSession = jdbcTemplate.queryForMap(
            "SELECT * FROM stream_sessions WHERE session_id = '1001'"
        );
        Timestamp oldEndedAt = (Timestamp) oldSession.get("ended_at");
        assertThat(oldEndedAt.toInstant()).isEqualTo(newStartedAt);

        Map<String, Object> oldSegment = jdbcTemplate.queryForMap(
            "SELECT * FROM stream_session_segments WHERE session_id = '1001'"
        );
        assertThat(oldSegment.get("end_offset_ms")).isEqualTo(15_000L);
    }

    @Test
    void 오판_종료된_세션이_동일한_방제와_카테고리로_다시_감지되면_0L_세그먼트를_중복_생성하지_않고_닫혔던_세그먼트를_다시_연다() {
        Instant startedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant t1 = Instant.parse("2026-10-24T03:00:30Z");
        Instant falseClosedAt = Instant.parse("2026-10-24T03:01:00Z");
        Instant tReopened = Instant.parse("2026-10-24T03:01:30Z");

        StreamTarget target = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "동일 방제", 2000, "thumb.jpg", "Talk", startedAt);

        adapter.persistSnapshot(List.of(target), emptyResults(t1), t1);
        adapter.persistSnapshot(List.of(), new StreamUpdateResults(Set.of(), Set.of(target), Set.of(), falseClosedAt), falseClosedAt);
        adapter.persistSnapshot(List.of(target), emptyResults(tReopened), tReopened);

        List<Map<String, Object>> segments = jdbcTemplate.queryForList(
            "SELECT * FROM stream_session_segments WHERE session_id = '1001'"
        );
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).get("start_offset_ms")).isEqualTo(0L);
        assertThat(segments.get(0).get("ended_at")).isNull();
        assertThat(segments.get(0).get("end_offset_ms")).isNull();
    }

    @Test
    void 오판_종료된_사이에_방제가_바뀌어_다시_감지되면_상대_오프셋으로_새_세그먼트를_생성한다() {
        Instant startedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant t1 = Instant.parse("2026-10-24T03:00:30Z");
        Instant falseClosedAt = Instant.parse("2026-10-24T03:01:00Z");
        Instant tReopened = Instant.parse("2026-10-24T03:02:00Z");

        StreamTarget initial = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "1부 토크", 2000, "thumb.jpg", "Talk", startedAt);
        StreamTarget reopenedChanged = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "2부 게임", 2500, "thumb.jpg", "게임", startedAt);

        adapter.persistSnapshot(List.of(initial), emptyResults(t1), t1);
        adapter.persistSnapshot(List.of(), new StreamUpdateResults(Set.of(), Set.of(initial), Set.of(), falseClosedAt), falseClosedAt);
        adapter.persistSnapshot(List.of(reopenedChanged), emptyResults(tReopened), tReopened);

        List<Map<String, Object>> segments = jdbcTemplate.queryForList(
            "SELECT * FROM stream_session_segments WHERE session_id = '1001' ORDER BY start_offset_ms ASC"
        );
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).get("start_offset_ms")).isEqualTo(0L);
        assertThat(segments.get(0).get("end_offset_ms")).isEqualTo(30_000L);

        assertThat(segments.get(1).get("title")).isEqualTo("2부 게임");
        assertThat(segments.get(1).get("start_offset_ms")).isEqualTo(120_000L);
        assertThat(((Timestamp) segments.get(1).get("started_at")).toInstant()).isEqualTo(tReopened);
        assertThat(segments.get(1).get("ended_at")).isNull();
    }

    @Test
    void 부분_유니크_인덱스에_의해_동일_세션에_열린_세그먼트가_중복_INSERT_되어도_1개만_유지된다() {
        Instant startedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant recordedAt = Instant.parse("2026-10-24T03:00:30Z");
        StreamTarget target = new StreamTarget("ch_1", "침착맨", "chat_1", 1001L, "방제", 2000, "thumb.jpg", "Talk", startedAt);

        adapter.persistSnapshot(List.of(target), emptyResults(recordedAt), recordedAt);

        jdbcTemplate.update("""
            INSERT INTO stream_session_segments (
                session_id, stream_id, title, category_name, started_at, start_offset_ms, paid_promotion, is_adult
            ) VALUES ('1001', 'ch_1', '중복방제', 'Talk', NOW(), 0, false, false)
            ON CONFLICT (session_id) WHERE ended_at IS NULL DO NOTHING
            """);

        Integer openSegmentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stream_session_segments WHERE session_id = '1001' AND ended_at IS NULL",
            Integer.class
        );
        assertThat(openSegmentCount).isEqualTo(1);
    }

    @Test
    void 스냅샷_적재_도중_DB_제약조건_위반_발생_시_트랜잭션_전체가_원자적으로_롤백된다() {
        Instant startedAt = Instant.parse("2026-10-24T03:00:00Z");
        Instant outOfPartitionRecordedAt = Instant.parse("2025-01-01T00:00:00Z");
        StreamTarget target = new StreamTarget("ch_1", "침착맨", "chat_1", 9999L, "방제", 1000, "thumb.jpg", "Talk", startedAt);

        assertThatThrownBy(() -> adapter.persistSnapshot(List.of(target), emptyResults(outOfPartitionRecordedAt), outOfPartitionRecordedAt))
            .isInstanceOf(DataIntegrityViolationException.class);

        Integer sessionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stream_sessions WHERE session_id = '9999'", Integer.class
        );
        Integer segmentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM stream_session_segments WHERE session_id = '9999'", Integer.class
        );
        assertThat(sessionCount).isZero();
        assertThat(segmentCount).isZero();
    }

    private StreamUpdateResults emptyResults(Instant changedAt) {
        return new StreamUpdateResults(Set.of(), Set.of(), Set.of(), changedAt);
    }
}
