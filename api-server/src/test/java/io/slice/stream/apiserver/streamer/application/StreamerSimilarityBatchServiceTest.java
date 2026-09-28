package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.stream.targeting.TargetStreamerEntity;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerRepository;
import io.slice.stream.apiserver.stream.targeting.TargetType;
import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import io.slice.stream.apiserver.streamer.domain.service.StreamerSimilarityCalculator;
import io.slice.stream.apiserver.streamer.fake.FakeStreamerChatterProvider;
import io.slice.stream.apiserver.streamer.infrastructure.JpaStreamerSimilarityRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerSimilarityEntity;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerSimilarityBatchServiceTest {

    @Mock
    private TargetStreamerRepository targetStreamerRepository;

    @Mock
    private JpaStreamRepository streamRepository;

    @Mock
    private JpaStreamerSimilarityRepository similarityRepository;

    private FakeStreamerChatterProvider chatterProvider;
    private StreamerSimilarityCalculator similarityCalculator;
    private ConcurrentMapCacheManager cacheManager;
    private StreamerSimilarityBatchService batchService;

    @BeforeEach
    void setUp() {
        chatterProvider = new FakeStreamerChatterProvider();
        similarityCalculator = new StreamerSimilarityCalculator();
        cacheManager = new ConcurrentMapCacheManager("streamerSimilarities");
        batchService = new StreamerSimilarityBatchService(
            targetStreamerRepository,
            streamRepository,
            chatterProvider,
            similarityCalculator,
            similarityRepository,
            null,
            cacheManager
        );
    }

    private Set<Long> generateChatters(long startId, int count) {
        Set<Long> set = new HashSet<>(count);
        for (long i = startId; i < startId + count; i++) {
            set.add(i);
        }
        return set;
    }

    @Test
    void 배치_실행_시_타겟_스트리머들의_유사도를_계산하고_DB에_저장한다() {
        LocalDate targetDate = LocalDate.of(2026, 9, 28);
        TargetStreamerEntity streamerA = new TargetStreamerEntity("streamA", "스트리머A", TargetType.STATIC, true);
        TargetStreamerEntity streamerB = new TargetStreamerEntity("streamB", "스트리머B", TargetType.STATIC, true);

        when(targetStreamerRepository.findAllByIsActiveTrue()).thenReturn(List.of(streamerA, streamerB));

        // streamerA: 1,000명, streamerB: 1,000명 (500명 중복 -> 유사도 500/1500 = 33.3%)
        chatterProvider.setChatters("streamA", generateChatters(1L, 1000));
        chatterProvider.setChatters("streamB", generateChatters(501L, 1000));

        batchService.executeBatch(targetDate);

        verify(similarityRepository, times(1)).deleteByCalculatedDate(eq(targetDate));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StreamerSimilarityEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(similarityRepository, times(1)).saveAll(captor.capture());

        List<StreamerSimilarityEntity> savedEntities = captor.getValue();
        assertThat(savedEntities).isNotEmpty();

        StreamerSimilarityEntity matchForA = savedEntities.stream()
            .filter(e -> e.getStreamId().equals("streamA"))
            .findFirst()
            .orElseThrow();

        assertAll(
            () -> assertThat(matchForA.getStatus()).isEqualTo(SimilarityStatus.NORMAL),
            () -> assertThat(matchForA.getTargetStreamId()).isEqualTo("streamB"),
            () -> assertThat(matchForA.getRankOrder()).isEqualTo(1),
            () -> assertThat(matchForA.getSimilarityPercent()).isGreaterThan(30.0)
        );
    }

    @Test
    void 표본이_부족한_스트리머는_INSUFFICIENT_DATA_상태로_저장된다() {
        LocalDate targetDate = LocalDate.of(2026, 9, 28);
        TargetStreamerEntity smallStreamer = new TargetStreamerEntity("smallStream", "하꼬스트리머", TargetType.STATIC, true);

        when(targetStreamerRepository.findAllByIsActiveTrue()).thenReturn(List.of(smallStreamer));
        chatterProvider.setChatters("smallStream", generateChatters(1L, 50)); // 표본 50명 (< 100)

        batchService.executeBatch(targetDate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StreamerSimilarityEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(similarityRepository, times(1)).saveAll(captor.capture());

        List<StreamerSimilarityEntity> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getStatus()).isEqualTo(SimilarityStatus.INSUFFICIENT_DATA);
    }

    @Test
    void 독립_팬덤인_스트리머는_INDEPENDENT_FANDOM_상태로_저장된다() {
        LocalDate targetDate = LocalDate.of(2026, 9, 28);
        TargetStreamerEntity streamerA = new TargetStreamerEntity("streamA", "독립스트리머", TargetType.STATIC, true);
        TargetStreamerEntity streamerB = new TargetStreamerEntity("streamB", "다른스트리머", TargetType.STATIC, true);

        when(targetStreamerRepository.findAllByIsActiveTrue()).thenReturn(List.of(streamerA, streamerB));
        chatterProvider.setChatters("streamA", generateChatters(1L, 1000));
        chatterProvider.setChatters("streamB", generateChatters(1001L, 1000)); // 중복 0명

        batchService.executeBatch(targetDate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StreamerSimilarityEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(similarityRepository, times(1)).saveAll(captor.capture());

        List<StreamerSimilarityEntity> saved = captor.getValue();
        StreamerSimilarityEntity entityA = saved.stream()
            .filter(e -> e.getStreamId().equals("streamA"))
            .findFirst()
            .orElseThrow();

        assertThat(entityA.getStatus()).isEqualTo(SimilarityStatus.INDEPENDENT_FANDOM);
    }

    @Test
    void 배치가_정상_완료되면_기존_유사도_로컬_캐시가_초기화된다() {
        LocalDate targetDate = LocalDate.of(2026, 9, 28);
        TargetStreamerEntity streamerA = new TargetStreamerEntity("streamA", "테스트스트리머", TargetType.STATIC, true);

        when(targetStreamerRepository.findAllByIsActiveTrue()).thenReturn(List.of(streamerA));
        chatterProvider.setChatters("streamA", generateChatters(1L, 200));

        Cache cache = cacheManager.getCache("streamerSimilarities");
        assertThat(cache).isNotNull();
        cache.put("streamA", "old-cached-value");

        batchService.executeBatch(targetDate);

        assertThat(cache.get("streamA")).isNull();
    }
}
