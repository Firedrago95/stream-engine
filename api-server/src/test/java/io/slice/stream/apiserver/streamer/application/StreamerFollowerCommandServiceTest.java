package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.application.dto.FollowerSnapshotRecordDto;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamerFollowerCommandServiceTest {

    @Mock
    private StreamerFollowerSnapshotRepository snapshotRepository;

    @Mock
    private JpaStreamRepository streamRepository;

    @Mock
    private JpaStreamSessionRepository streamSessionRepository;

    private StreamerFollowerCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new StreamerFollowerCommandService(
            snapshotRepository,
            streamRepository,
            streamSessionRepository
        );
    }

    @Test
    @DisplayName("직전 스냅샷이 없으면 팔로워 증감량(growth)은 0으로 저장된다")
    void recordFollowerWithoutPrevSnapshot() {
        String streamId = "ch1";
        LocalDate date = LocalDate.of(2026, 9, 17);
        int followerCount = 10000;
        StreamEntity streamEntity = new StreamEntity(streamId, "스트리머1");

        when(streamRepository.findByStreamId(streamId)).thenReturn(Optional.of(streamEntity));
        when(snapshotRepository.findByStreamIdAndSnapshotDate(streamId, date)).thenReturn(Optional.empty());
        when(snapshotRepository.findFirstByStreamIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(streamId, date))
            .thenReturn(Optional.empty());

        commandService.recordFollower(streamId, followerCount, date);

        ArgumentCaptor<StreamerFollowerSnapshotEntity> captor = ArgumentCaptor.forClass(StreamerFollowerSnapshotEntity.class);
        verify(snapshotRepository).save(captor.capture());

        StreamerFollowerSnapshotEntity saved = captor.getValue();
        assertThat(saved.getStreamId()).isEqualTo(streamId);
        assertThat(saved.getSnapshotDate()).isEqualTo(date);
        assertThat(saved.getFollowerCount()).isEqualTo(10000);
        assertThat(saved.getFollowerGrowth()).isEqualTo(0);

        assertThat(streamEntity.getFollowerCount()).isEqualTo(10000);
        assertThat(streamEntity.getLastFollowerUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("직전 스냅샷이 있으면 증감량은 (현재 팔로워 - 직전 팔로워)로 계산된다")
    void recordFollowerWithPrevSnapshot() {
        String streamId = "ch1";
        LocalDate prevDate = LocalDate.of(2026, 9, 16);
        LocalDate date = LocalDate.of(2026, 9, 17);
        int followerCount = 10700;

        StreamEntity streamEntity = new StreamEntity(streamId, "스트리머1");
        StreamerFollowerSnapshotEntity prevSnapshot = new StreamerFollowerSnapshotEntity(streamId, prevDate, 10000, 100);

        when(streamRepository.findByStreamId(streamId)).thenReturn(Optional.of(streamEntity));
        when(snapshotRepository.findByStreamIdAndSnapshotDate(streamId, date)).thenReturn(Optional.empty());
        when(snapshotRepository.findFirstByStreamIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(streamId, date))
            .thenReturn(Optional.of(prevSnapshot));

        commandService.recordFollower(streamId, followerCount, date);

        ArgumentCaptor<StreamerFollowerSnapshotEntity> captor = ArgumentCaptor.forClass(StreamerFollowerSnapshotEntity.class);
        verify(snapshotRepository).save(captor.capture());

        StreamerFollowerSnapshotEntity saved = captor.getValue();
        assertThat(saved.getFollowerCount()).isEqualTo(10700);
        assertThat(saved.getFollowerGrowth()).isEqualTo(700);
    }

    @Test
    @DisplayName("직전 스냅샷이 하루 전(D-1)이 아닌 비연속 스냅샷(공백 발생)이면 왜곡 방지를 위해 증감량은 0으로 기록된다")
    void recordFollowerWithNonConsecutivePrevSnapshot() {
        String streamId = "ch1";
        LocalDate oldDate = LocalDate.of(2026, 9, 10);
        LocalDate date = LocalDate.of(2026, 9, 17);
        int followerCount = 12000;

        StreamEntity streamEntity = new StreamEntity(streamId, "스트리머1");
        StreamerFollowerSnapshotEntity oldSnapshot = new StreamerFollowerSnapshotEntity(streamId, oldDate, 10000, 100);

        when(streamRepository.findByStreamId(streamId)).thenReturn(Optional.of(streamEntity));
        when(snapshotRepository.findByStreamIdAndSnapshotDate(streamId, date)).thenReturn(Optional.empty());
        when(snapshotRepository.findFirstByStreamIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(streamId, date))
            .thenReturn(Optional.of(oldSnapshot));

        commandService.recordFollower(streamId, followerCount, date);

        ArgumentCaptor<StreamerFollowerSnapshotEntity> captor = ArgumentCaptor.forClass(StreamerFollowerSnapshotEntity.class);
        verify(snapshotRepository).save(captor.capture());

        StreamerFollowerSnapshotEntity saved = captor.getValue();
        assertThat(saved.getFollowerCount()).isEqualTo(12000);
        assertThat(saved.getFollowerGrowth()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 날짜 스냅샷이 이미 존재하면 새 수치와 재계산된 증감량으로 업데이트된다")
    void updateExistingSnapshot() {
        String streamId = "ch1";
        LocalDate date = LocalDate.of(2026, 9, 17);
        StreamEntity streamEntity = new StreamEntity(streamId, "스트리머1");
        StreamerFollowerSnapshotEntity existing = new StreamerFollowerSnapshotEntity(streamId, date, 10000, 0);

        StreamerFollowerSnapshotEntity prevSnapshot = new StreamerFollowerSnapshotEntity(streamId, date.minusDays(1), 9500, 100);

        when(streamRepository.findByStreamId(streamId)).thenReturn(Optional.of(streamEntity));
        when(snapshotRepository.findByStreamIdAndSnapshotDate(streamId, date)).thenReturn(Optional.of(existing));
        when(snapshotRepository.findFirstByStreamIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(streamId, date))
            .thenReturn(Optional.of(prevSnapshot));

        commandService.recordFollower(streamId, 10200, date);

        assertThat(existing.getFollowerCount()).isEqualTo(10200);
        assertThat(existing.getFollowerGrowth()).isEqualTo(700);
    }

    @Test
    @DisplayName("벌크 수집 목록을 받아 일괄 적재한다")
    void recordFollowersBulk() {
        FollowerSnapshotRecordDto dto1 = new FollowerSnapshotRecordDto("ch1", 5000, LocalDate.of(2026, 9, 17));
        FollowerSnapshotRecordDto dto2 = new FollowerSnapshotRecordDto("ch2", 3000, LocalDate.of(2026, 9, 17));

        when(streamRepository.findByStreamId("ch1")).thenReturn(Optional.of(new StreamEntity("ch1", "s1")));
        when(streamRepository.findByStreamId("ch2")).thenReturn(Optional.of(new StreamEntity("ch2", "s2")));
        when(snapshotRepository.findByStreamIdAndSnapshotDate(any(), any())).thenReturn(Optional.empty());
        when(snapshotRepository.findFirstByStreamIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(any(), any())).thenReturn(Optional.empty());

        commandService.recordFollowers(List.of(dto1, dto2));

        verify(snapshotRepository, org.mockito.Mockito.times(2)).save(any(StreamerFollowerSnapshotEntity.class));
    }

    @Test
    @DisplayName("최근 90일 이내 방송 세션 이력이 있는 스트리머 목록을 조회한다")
    void findFollowerTargetChannelIds() {
        when(streamSessionRepository.findDistinctStreamIdsByStartedAtAfter(any(Instant.class)))
            .thenReturn(List.of("ch1", "ch2", "ch3"));

        List<String> targets = commandService.findFollowerTargetChannelIds();

        assertThat(targets).containsExactly("ch1", "ch2", "ch3");
    }
}
