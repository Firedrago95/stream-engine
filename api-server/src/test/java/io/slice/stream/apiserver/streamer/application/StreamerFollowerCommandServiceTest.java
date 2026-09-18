package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.streamer.application.dto.FollowerSnapshotRecordDto;
import io.slice.stream.apiserver.streamer.application.dto.StreamFollowerUpdateDto;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.StreamerFollowerJdbcRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

    @Mock
    private StreamerFollowerJdbcRepository followerJdbcRepository;

    @Captor
    private ArgumentCaptor<List<StreamerFollowerSnapshotEntity>> snapshotCaptor;

    @Captor
    private ArgumentCaptor<List<StreamFollowerUpdateDto>> streamUpdateCaptor;

    private StreamerFollowerCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new StreamerFollowerCommandService(
            snapshotRepository,
            streamRepository,
            streamSessionRepository,
            followerJdbcRepository
        );
    }

    @Test
    @DisplayName("직전일(D-1) 스냅샷이 없으면 팔로워 증감량(growth)은 0으로 계산되어 벌크 저장된다")
    void recordFollowerWithoutPrevSnapshot() {
        String streamId = "ch1";
        LocalDate date = LocalDate.of(2026, 9, 17);
        int followerCount = 10000;

        when(snapshotRepository.findAllBySnapshotDateAndStreamIdIn(date.minusDays(1), List.of(streamId)))
            .thenReturn(Collections.emptyList());

        commandService.recordFollower(streamId, followerCount, date);

        verify(followerJdbcRepository).batchUpsertSnapshots(snapshotCaptor.capture());
        verify(followerJdbcRepository).batchUpdateStreamFollowers(streamUpdateCaptor.capture());

        List<StreamerFollowerSnapshotEntity> savedSnapshots = snapshotCaptor.getValue();
        assertThat(savedSnapshots).hasSize(1);
        StreamerFollowerSnapshotEntity saved = savedSnapshots.get(0);
        assertThat(saved.getStreamId()).isEqualTo(streamId);
        assertThat(saved.getSnapshotDate()).isEqualTo(date);
        assertThat(saved.getFollowerCount()).isEqualTo(10000);
        assertThat(saved.getFollowerGrowth()).isEqualTo(0);

        List<StreamFollowerUpdateDto> savedUpdates = streamUpdateCaptor.getValue();
        assertThat(savedUpdates).hasSize(1);
        StreamFollowerUpdateDto update = savedUpdates.get(0);
        assertThat(update.streamId()).isEqualTo(streamId);
        assertThat(update.followerCount()).isEqualTo(10000);
        assertThat(update.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("직전일(D-1) 스냅샷이 있으면 증감량은 (현재 팔로워 - 직전 팔로워)로 계산된다")
    void recordFollowerWithPrevSnapshot() {
        String streamId = "ch1";
        LocalDate date = LocalDate.of(2026, 9, 17);
        LocalDate yesterday = date.minusDays(1);
        int followerCount = 10700;

        StreamerFollowerSnapshotEntity prevSnapshot = new StreamerFollowerSnapshotEntity(streamId, yesterday, 10000, 100);

        when(snapshotRepository.findAllBySnapshotDateAndStreamIdIn(yesterday, List.of(streamId)))
            .thenReturn(List.of(prevSnapshot));

        commandService.recordFollower(streamId, followerCount, date);

        verify(followerJdbcRepository).batchUpsertSnapshots(snapshotCaptor.capture());
        StreamerFollowerSnapshotEntity saved = snapshotCaptor.getValue().get(0);
        assertThat(saved.getFollowerCount()).isEqualTo(10700);
        assertThat(saved.getFollowerGrowth()).isEqualTo(700);
    }

    @Test
    @DisplayName("다건의 팔로워 스냅샷 목록을 받아 전일 데이터 일괄 조회 후 벌크 업서트 및 업데이트를 실행한다")
    void recordFollowersBulk() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        LocalDate yesterday = date.minusDays(1);

        FollowerSnapshotRecordDto dto1 = new FollowerSnapshotRecordDto("ch1", 5000, date);
        FollowerSnapshotRecordDto dto2 = new FollowerSnapshotRecordDto("ch2", 3000, date);

        StreamerFollowerSnapshotEntity prev1 = new StreamerFollowerSnapshotEntity("ch1", yesterday, 4800, 50);

        when(snapshotRepository.findAllBySnapshotDateAndStreamIdIn(yesterday, List.of("ch1", "ch2")))
            .thenReturn(List.of(prev1));

        commandService.recordFollowers(List.of(dto1, dto2));

        verify(followerJdbcRepository).batchUpsertSnapshots(snapshotCaptor.capture());
        verify(followerJdbcRepository).batchUpdateStreamFollowers(streamUpdateCaptor.capture());

        List<StreamerFollowerSnapshotEntity> snapshots = snapshotCaptor.getValue();
        assertThat(snapshots).hasSize(2);

        StreamerFollowerSnapshotEntity s1 = snapshots.get(0);
        assertThat(s1.getStreamId()).isEqualTo("ch1");
        assertThat(s1.getFollowerCount()).isEqualTo(5000);
        assertThat(s1.getFollowerGrowth()).isEqualTo(200);

        StreamerFollowerSnapshotEntity s2 = snapshots.get(1);
        assertThat(s2.getStreamId()).isEqualTo("ch2");
        assertThat(s2.getFollowerCount()).isEqualTo(3000);
        assertThat(s2.getFollowerGrowth()).isEqualTo(0);

        List<StreamFollowerUpdateDto> updates = streamUpdateCaptor.getValue();
        assertThat(updates).hasSize(2);
        assertThat(updates.get(0).streamId()).isEqualTo("ch1");
        assertThat(updates.get(0).followerCount()).isEqualTo(5000);
        assertThat(updates.get(1).streamId()).isEqualTo("ch2");
        assertThat(updates.get(1).followerCount()).isEqualTo(3000);
    }

    @Test
    @DisplayName("빈 목록이나 null이 전달되면 아무런 DB 작업도 수행하지 않는다")
    void recordFollowersWithEmptyList() {
        commandService.recordFollowers(null);
        commandService.recordFollowers(Collections.emptyList());

        verify(followerJdbcRepository, never()).batchUpsertSnapshots(any());
        verify(followerJdbcRepository, never()).batchUpdateStreamFollowers(any());
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
