package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.SegmentResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import io.slice.stream.apiserver.stream.fake.FakeAnalysisRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.fake.FakeViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AnalysisQueryServiceTest {

    private FakeAnalysisRepository analysisRepository;
    private FakeStreamSessionRepository sessionRepository;
    private FakeStreamSessionSegmentRepository segmentRepository;
    private FakeViewMetricTimelineRepository timelineRepository;
    private ReLiveSessionMerger reLiveSessionMerger;
    private AnalysisQueryService analysisQueryService;

    @BeforeEach
    void setUp() {
        analysisRepository = new FakeAnalysisRepository();
        sessionRepository = new FakeStreamSessionRepository();
        segmentRepository = new FakeStreamSessionSegmentRepository();
        timelineRepository = new FakeViewMetricTimelineRepository();
        reLiveSessionMerger = new ReLiveSessionMerger();
        analysisQueryService = new AnalysisQueryService(
            analysisRepository,
            sessionRepository,
            segmentRepository,
            timelineRepository,
            reLiveSessionMerger
        );
    }

    @Test
    void 과거_데이터_조회_시_요약_데이터와_원본_데이터를_시간순으로_병합하여_반환한다() {
        String streamId = "test-stream";
        String sessionId = "target-session";
        Instant sessionStart = Instant.ofEpochMilli(0L);

        StreamSessionEntity session = new StreamSessionEntity(streamId, sessionId, "방제", "게임", sessionStart);
        session.finishSession(sessionStart.plusSeconds(3600), 200, 150.0);
        sessionRepository.save(session);

        analysisRepository.setSummaryHistory(streamId, sessionId, List.of(
            new AnalysisDataPoint(0L, 150L, "NORMAL", 0L)
        ));
        analysisRepository.setRawHistory(streamId, sessionId, List.of(
            new AnalysisDataPoint(60000L, 200L, "PEAK", 60000L)
        ));

        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, sessionId);

        assertThat(response.dataPoints()).hasSize(2);
        assertThat(response.dataPoints().get(0).timestamp()).isEqualTo(0L);
        assertThat(response.dataPoints().get(0).value()).isEqualTo(150L);
        assertThat(response.dataPoints().get(1).timestamp()).isEqualTo(60000L);
        assertThat(response.dataPoints().get(1).value()).isEqualTo(200L);
        assertThat(response.segments()).isEmpty();
    }

    @Test
    void 과거_데이터_조회_시_요약_데이터가_비어있으면_원본_데이터를_1분_단위로_압축하여_반환한다() {
        String streamId = "test-stream";
        String sessionId = "target-session";
        Instant sessionStart = Instant.ofEpochMilli(0L);

        StreamSessionEntity session = new StreamSessionEntity(streamId, sessionId, "방제", "게임", sessionStart);
        session.finishSession(sessionStart.plusSeconds(3600), 300, 150.0);
        sessionRepository.save(session);

        List<AnalysisDataPoint> rawPoints = List.of(
            new AnalysisDataPoint(1000L, 200L, "NORMAL", 1000L),
            new AnalysisDataPoint(1500L, 300L, "PEAK", 1500L),
            new AnalysisDataPoint(65000L, 50L, "NORMAL", 65000L)
        );
        analysisRepository.setRawHistory(streamId, sessionId, rawPoints);

        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, sessionId);

        List<AnalysisDataPoint> points = response.dataPoints();
        assertThat(points).hasSize(2);

        assertThat(points.get(0).timestamp()).isEqualTo(0L);
        assertThat(points.get(0).value()).isEqualTo(250L);
        assertThat(points.get(0).status()).isEqualTo("PEAK");

        assertThat(points.get(1).timestamp()).isEqualTo(60000L);
        assertThat(points.get(1).value()).isEqualTo(50L);
        assertThat(points.get(1).status()).isEqualTo("NORMAL");
        assertThat(response.segments()).isEmpty();
    }

    @Test
    void 세션_목록_조회_시_상세_지표와_함께_반환한다() {
        String streamId = "test-stream";
        int limit = 10;
        Instant startedAt = Instant.parse("2026-03-17T11:30:00Z");

        StreamSessionEntity sessionEntity = new StreamSessionEntity(streamId, "session-123", "방제", "게임", startedAt);
        sessionEntity.updatePeakViewers(3500);
        sessionEntity.finishSession(startedAt.plusSeconds(3600), 3500, 2100.0);
        sessionEntity.updateSubscriberChatRatio(35.5);
        sessionRepository.save(sessionEntity);

        List<SessionResponse> sessions = analysisQueryService.getAvailableSessions(streamId, limit);

        assertThat(sessions).hasSize(1);
        SessionResponse session = sessions.get(0);
        assertThat(session.sessionId()).isEqualTo("session-123");
        assertThat(session.title()).isEqualTo("방제");
        assertThat(session.categoryName()).isEqualTo("게임");
        assertThat(session.startedAt()).isEqualTo(startedAt);
        assertThat(session.peakViewers()).isEqualTo(3500);
        assertThat(session.averageViewerCount()).isEqualTo(2100);
        assertThat(session.subscriberChatRatio()).isEqualTo(35.5);
    }

    @Test
    void 과거_데이터_조회_시_세그먼트와_시계열_타임라인_및_세션_요약이_함께_반환된다() {
        String streamId = "test-stream";
        String sessionId = "target-session";
        Instant segmentStart = Instant.now();

        StreamSessionEntity sessionEntity = new StreamSessionEntity(streamId, sessionId, "테스트 방제", "테스트 카테고리", segmentStart);
        sessionEntity.finishSession(segmentStart.plusSeconds(3600), 2500, 1800.0);
        sessionEntity.updateSubscriberChatRatio(50.0);
        sessionRepository.save(sessionEntity);

        StreamSessionSegmentEntity segmentEntity = new StreamSessionSegmentEntity(
            streamId, sessionId, "테스트 방제", "테스트 카테고리", segmentStart, 0L
        );
        segmentEntity.endSegment(segmentStart.plusSeconds(30), 30000L);
        segmentRepository.addSegment(segmentEntity);

        ViewMetricTimelineEntity timelineEntity = new ViewMetricTimelineEntity(streamId, sessionId, segmentStart, 2000);
        timelineRepository.save(timelineEntity);

        analysisRepository.setSummaryHistory(streamId, sessionId, List.of(
            new AnalysisDataPoint(1000L, 100L, "NORMAL", 0L)
        ));

        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, sessionId);

        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).title()).isEqualTo("테스트 방제");
        assertThat(response.segments().get(0).categoryName()).isEqualTo("테스트 카테고리");
        assertThat(response.segments().get(0).startedAt()).isEqualTo(segmentStart);
        assertThat(response.segments().get(0).endedAt()).isEqualTo(segmentStart.plusSeconds(30));
        assertThat(response.segments().get(0).startOffsetMs()).isEqualTo(0L);
        assertThat(response.segments().get(0).endOffsetMs()).isEqualTo(30000L);

        assertThat(response.timeline()).hasSize(1);
        assertThat(response.timeline().get(0).viewerCount()).isEqualTo(2000);

        assertThat(response.summary()).isNotNull();
        assertThat(response.summary().peakViewers()).isEqualTo(2500);
        assertThat(response.summary().averageViewerCount()).isEqualTo(1800);
        assertThat(response.summary().subscriberChatRatio()).isEqualTo(50.0);
    }

    @Test
    void 방송시간이_6시간인_경우_화력과_시청자수가_3분_단위_평균으로_다운샘플링된다() {
        String streamId = "test-stream";
        String sessionId = "6h-session";
        Instant sessionStart = Instant.parse("2026-03-17T12:00:00Z");
        Instant sessionEnd = sessionStart.plusSeconds(6 * 3600);

        StreamSessionEntity session = new StreamSessionEntity(streamId, sessionId, "6시간 방송", "롤", sessionStart);
        session.finishSession(sessionEnd, 3000, 2000.0);
        sessionRepository.save(session);

        long startMs = sessionStart.toEpochMilli();
        List<AnalysisDataPoint> summaryPoints = List.of(
            new AnalysisDataPoint(startMs, 100L, "NORMAL", 0L),
            new AnalysisDataPoint(startMs + 60_000L, 200L, "PEAK", 60_000L),
            new AnalysisDataPoint(startMs + 120_000L, 300L, "NORMAL", 120_000L)
        );
        analysisRepository.setSummaryHistory(streamId, sessionId, summaryPoints);

        List<ViewMetricTimelineEntity> timelines = List.of(
            new ViewMetricTimelineEntity(streamId, sessionId, sessionStart, 1000),
            new ViewMetricTimelineEntity(streamId, sessionId, sessionStart.plusSeconds(60), 2000),
            new ViewMetricTimelineEntity(streamId, sessionId, sessionStart.plusSeconds(120), 3000)
        );
        timelineRepository.saveAll(timelines);

        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, sessionId);

        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).value()).isEqualTo(200L);
        assertThat(response.dataPoints().get(0).status()).isEqualTo("PEAK");

        assertThat(response.timeline()).hasSize(1);
        assertThat(response.timeline().get(0).viewerCount()).isEqualTo(2000);
    }

    @Test
    void 방송시간이_92시간인_경우_화력과_시청자수가_1시간_단위_평균으로_다운샘플링된다() {
        String streamId = "test-stream";
        String sessionId = "92h-session";
        Instant sessionStart = Instant.parse("2026-03-12T10:00:00Z");
        Instant sessionEnd = sessionStart.plusSeconds(92 * 3600);

        StreamSessionEntity session = new StreamSessionEntity(streamId, sessionId, "켠왕 92시간", "롤", sessionStart);
        session.finishSession(sessionEnd, 7000, 4500.0);
        sessionRepository.save(session);

        long startMs = sessionStart.toEpochMilli();
        List<AnalysisDataPoint> summaryPoints = List.of(
            new AnalysisDataPoint(startMs, 50L, "NORMAL", 0L),
            new AnalysisDataPoint(startMs + 1_800_000L, 150L, "PEAK", 1_800_000L)
        );
        analysisRepository.setSummaryHistory(streamId, sessionId, summaryPoints);

        List<ViewMetricTimelineEntity> timelines = List.of(
            new ViewMetricTimelineEntity(streamId, sessionId, sessionStart, 4000),
            new ViewMetricTimelineEntity(streamId, sessionId, sessionStart.plusSeconds(1800), 6000)
        );
        timelineRepository.saveAll(timelines);

        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, sessionId);

        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).value()).isEqualTo(100L);
        assertThat(response.dataPoints().get(0).status()).isEqualTo("PEAK");

        assertThat(response.timeline()).hasSize(1);
        assertThat(response.timeline().get(0).viewerCount()).isEqualTo(5000);
    }

    @Test
    void 리방이_발생한_경우_세션_목록_조회_시_단일_세션으로_병합되어_반환된다() {
        String streamId = "stream-runner";
        Instant t1 = Instant.parse("2026-03-24T11:29:00Z");
        Instant t1End = t1.plusSeconds(16); // 16초 뒤 종료
        Instant t2 = t1End.plusSeconds(100); // 약 1분 40초 뒤 리방 (6분 이내)

        StreamSessionEntity s1 = new StreamSessionEntity(streamId, "sess-1", "1차 방제", "카테고리1", t1);
        s1.finishSession(t1End, 1500, 1200.0);

        StreamSessionEntity s2 = new StreamSessionEntity(streamId, "sess-2", "2차 리방 방제", "카테고리2", t2);
        s2.finishSession(t2.plusSeconds(7200), 5000, 4000.0);

        sessionRepository.save(s1);
        sessionRepository.save(s2);

        List<SessionResponse> sessions = analysisQueryService.getAvailableSessions(streamId, 10);

        assertThat(sessions).hasSize(1);
        SessionResponse merged = sessions.get(0);
        assertThat(merged.sessionId()).isEqualTo("sess-2"); // 최신 세션 ID가 마스터
        assertThat(merged.title()).isEqualTo("2차 리방 방제");
        assertThat(merged.categoryName()).isEqualTo("카테고리2");
        assertThat(merged.startedAt()).isEqualTo(t1); // 시작시각은 1차 세션 시작시각
        assertThat(merged.peakViewers()).isEqualTo(5000); // 두 세션 중 최고 시청자
    }

    @Test
    void 리방이_발생한_경우_과거_데이터_조회_시_세그먼트_오프셋과_타임라인_화력_데이터가_통합_시계열로_병합된다() {
        String streamId = "stream-runner";
        Instant t1 = Instant.parse("2026-03-24T11:29:00Z");
        Instant t1End = t1.plusSeconds(60);
        Instant t2 = t1End.plusSeconds(120); // 2분 뒤 리방

        StreamSessionEntity s1 = new StreamSessionEntity(streamId, "sess-1", "1차 방제", "롤", t1);
        s1.finishSession(t1End, 2000, 1800.0);

        StreamSessionEntity s2 = new StreamSessionEntity(streamId, "sess-2", "2차 방제", "종합게임", t2);
        s2.finishSession(t2.plusSeconds(1800), 4000, 3500.0);

        sessionRepository.save(s1);
        sessionRepository.save(s2);

        // s1 세그먼트 (t1 기준 0 ~ 60초)
        StreamSessionSegmentEntity seg1 = new StreamSessionSegmentEntity(streamId, "sess-1", "1차 세그먼트", "롤", t1, 0L);
        seg1.endSegment(t1End, 60000L);
        segmentRepository.addSegment(seg1);

        // s2 세그먼트 (t2 ~ t2+600초) -> t1 기준으로는 180초 ~ 780초
        StreamSessionSegmentEntity seg2 = new StreamSessionSegmentEntity(streamId, "sess-2", "2차 세그먼트", "종합게임", t2, 0L);
        seg2.endSegment(t2.plusSeconds(600), 600000L);
        segmentRepository.addSegment(seg2);

        // 시청자수 타임라인 (s1 1개, s2 1개)
        timelineRepository.save(new ViewMetricTimelineEntity(streamId, "sess-1", t1.plusSeconds(30), 1800));
        timelineRepository.save(new ViewMetricTimelineEntity(streamId, "sess-2", t2.plusSeconds(30), 3600));

        // 화력 데이터 (s1 1개, s2 1개)
        analysisRepository.setSummaryHistory(streamId, "sess-1", List.of(
            new AnalysisDataPoint(t1.toEpochMilli(), 100L, "NORMAL", 0L)
        ));
        analysisRepository.setSummaryHistory(streamId, "sess-2", List.of(
            new AnalysisDataPoint(t2.toEpochMilli(), 250L, "PEAK", 0L)
        ));

        // s2를 조회하든 s1을 조회하든 동일하게 단일 통합 데이터가 반환되어야 함
        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, "sess-2");

        assertThat(response.segments()).hasSize(2);
        SegmentResponse resSeg1 = response.segments().get(0);
        SegmentResponse resSeg2 = response.segments().get(1);
        assertThat(resSeg1.startOffsetMs()).isEqualTo(0L);
        assertThat(resSeg1.endOffsetMs()).isEqualTo(60000L);
        assertThat(resSeg2.startOffsetMs()).isEqualTo(180000L); // t1 기준 180초 (3분) 오프셋으로 보정됨!
        assertThat(resSeg2.endOffsetMs()).isEqualTo(780000L);

        // 타임라인 병합 확인
        assertThat(response.timeline()).hasSize(2);
        assertThat(response.timeline().get(0).viewerCount()).isEqualTo(1800);
        assertThat(response.timeline().get(1).viewerCount()).isEqualTo(3600);

        // 화력 병합 및 오프셋 보정 확인
        assertThat(response.dataPoints()).hasSize(2);
        assertThat(response.dataPoints().get(0).offsetMs()).isEqualTo(0L);
        assertThat(response.dataPoints().get(1).offsetMs()).isEqualTo(180000L); // t1 기준 180초로 보정!

        // 세션 요약 병합 확인
        assertThat(response.summary()).isNotNull();
        assertThat(response.summary().startedAt()).isEqualTo(t1);
        assertThat(response.summary().peakViewers()).isEqualTo(4000);
    }
}
