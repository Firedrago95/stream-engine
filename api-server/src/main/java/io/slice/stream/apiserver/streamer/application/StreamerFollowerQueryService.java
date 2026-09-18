package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.streamer.application.dto.FollowerTrendResponse;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreamerFollowerQueryService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 90;

    private final StreamerFollowerSnapshotRepository snapshotRepository;

    public List<FollowerTrendResponse> getFollowerTrend(String channelId, int days) {
        int validDays = resolveValidDays(days);
        LocalDate startDate = LocalDate.now().minusDays(validDays);

        List<StreamerFollowerSnapshotEntity> snapshots =
            snapshotRepository.findAllByStreamIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(channelId, startDate);

        if (snapshots.isEmpty()) {
            return List.of();
        }

        return fillMissingDates(snapshots);
    }

    private int resolveValidDays(int days) {
        if (days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private List<FollowerTrendResponse> fillMissingDates(List<StreamerFollowerSnapshotEntity> snapshots) {
        Map<LocalDate, StreamerFollowerSnapshotEntity> snapshotMap = snapshots.stream()
            .collect(Collectors.toMap(StreamerFollowerSnapshotEntity::getSnapshotDate, s -> s));

        LocalDate currentDate = snapshots.get(0).getSnapshotDate();
        LocalDate lastDate = snapshots.get(snapshots.size() - 1).getSnapshotDate();

        List<FollowerTrendResponse> result = new ArrayList<>();
        int lastFollowerCount = snapshots.get(0).getFollowerCount();

        while (!currentDate.isAfter(lastDate)) {
            StreamerFollowerSnapshotEntity snapshot = snapshotMap.get(currentDate);
            if (snapshot != null) {
                lastFollowerCount = snapshot.getFollowerCount();
                result.add(new FollowerTrendResponse(
                    snapshot.getSnapshotDate(),
                    snapshot.getFollowerCount(),
                    snapshot.getFollowerGrowth()
                ));
            } else {
                result.add(new FollowerTrendResponse(
                    currentDate,
                    lastFollowerCount,
                    0
                ));
            }
            currentDate = currentDate.plusDays(1);
        }

        return result;
    }
}
