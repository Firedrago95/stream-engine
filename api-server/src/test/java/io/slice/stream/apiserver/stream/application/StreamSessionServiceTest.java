package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamSessionServiceTest {

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    @Mock
    private JpaStreamRepository streamRepository;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private StreamSessionService streamSessionService;

    @Test
    void 현재_진행중인_세션이_있으면_새로_생성하지_않고_기존_세션ID를_반환한다() {
        // given
        String streamId = "stream-1";
        Instant now = Instant.now();
        StreamSessionEntity existingSession = new StreamSessionEntity(streamId, "existing-session-id", "방제", "카테고리", now);

        when(sessionRepository.findActiveSession(streamId))
            .thenReturn(Optional.of(existingSession));

        // when
        String sessionId = streamSessionService.getOrCreateActiveSession(streamId, now);

        // then
        assertThat(sessionId).isEqualTo("existing-session-id");
        verify(sessionRepository, times(0)).save(any()); // save가 호출되지 않았음을 검증
    }

    @Test
    void 진행중인_세션이_없으면_스트리머_정보를_조회하여_새로운_세션을_생성한다() {
        // given
        String streamId = "stream-2";
        Instant now = Instant.now();

        // DB에 열려있는 세션이 없음
        when(sessionRepository.findActiveSession(streamId))
            .thenReturn(Optional.empty());

        // 방제와 카테고리를 가져오기 위한 스트림 마스터 정보 Mocking
        StreamEntity mockStreamInfo = mock(StreamEntity.class);
        when(mockStreamInfo.getLiveTitle()).thenReturn("새로운 꿀잼 방송");
        when(mockStreamInfo.getCategoryName()).thenReturn("Just Chatting");
        when(streamRepository.findByStreamId(streamId))
            .thenReturn(Optional.of(mockStreamInfo));

        // when
        String sessionId = streamSessionService.getOrCreateActiveSession(streamId, now);

        // then
        assertThat(sessionId).isNotNull(); // UUID가 정상 발급되었는지 확인
        verify(sessionRepository, times(1)).save(any(StreamSessionEntity.class)); // DB에 저장되었는지 검증
    }

    @Test
    void 오프라인_임계치를_초과한_방종_세션을_찾아_종료하고_캐시를_명시적으로_제거한다() {
        // given
        String streamId = "stream-3";
        StreamSessionEntity zombieSession = new StreamSessionEntity(streamId, "zombie-session-id", "방제", "카테고리", Instant.now().minusSeconds(3600));

        when(sessionRepository.findSessionsToClose(any(Instant.class)))
            .thenReturn(List.of(zombieSession));

        // 캐시 Evict 로직을 검증하기 위한 Mocking
        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCache("activeSessions")).thenReturn(mockCache);

        // when
        streamSessionService.closeOfflineSessions();

        // then
        assertThat(zombieSession.getEndedAt()).isNotNull(); // 엔티티에 종료 시간이 잘 찍혔는지 검증
        verify(mockCache, times(1)).evict(streamId); // 💡 핵심: 글로벌 캐시에서 스트림ID가 잘 삭제되었는지 검증
    }
}
