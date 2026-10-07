package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.application.dto.FollowerRankingResponse;
import io.slice.stream.apiserver.streamer.application.dto.FollowerRankingType;
import io.slice.stream.apiserver.streamer.application.dto.FollowerTrendResponse;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerGrowthProjection;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreamerFollowerQueryService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_RANKING_LIMIT = 20;
    private static final int MAX_RANKING_LIMIT = 100;
    private static final int WEEKLY_GROWTH_DAYS = 7;

    private final StreamerFollowerSnapshotRepository snapshotRepository;
    private final StreamRepository streamRepository;

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

    public List<FollowerRankingResponse> getFollowerLeaderboard(FollowerRankingType type, int limit) {
        int validLimit = resolveValidLimit(limit);
        FollowerRankingType rankingType = (type != null) ? type : FollowerRankingType.GROWTH;

        if (rankingType == FollowerRankingType.TOTAL) {
            return getTotalFollowersRanking(validLimit);
        }
        return getWeeklyGrowthRanking(validLimit);
    }

    private List<FollowerRankingResponse> getTotalFollowersRanking(int limit) {
        log.info("[Streamer Follower] 누적 팔로워 순위 조회. limit: {}", limit);
        List<StreamEntity> streams = streamRepository.findTopFollowers(PageRequest.of(0, limit));

        return streams.stream()
            .map(this::toTotalFollowerResponse)
            .toList();
    }

    private List<FollowerRankingResponse> getWeeklyGrowthRanking(int limit) {
        log.info("[Streamer Follower] 주간 팔로워 급상승 순위 조회. limit: {}", limit);
        LocalDate sinceDate = LocalDate.now().minusDays(WEEKLY_GROWTH_DAYS);
        List<StreamerFollowerGrowthProjection> projections =
            streamRepository.findTopFollowerGrowth(sinceDate, limit);

        return projections.stream()
            .map(this::toGrowthResponse)
            .toList();
    }

    private FollowerRankingResponse toTotalFollowerResponse(StreamEntity stream) {
        StreamStatus status = stream.isLive() ? StreamStatus.LIVE : StreamStatus.OFFLINE;
        int followers = (stream.getFollowerCount() != null) ? stream.getFollowerCount() : 0;

        return new FollowerRankingResponse(
            stream.getStreamId(),
            stream.getStreamerName(),
            stream.getLiveTitle(),
            stream.getProfileImageUrl(),
            stream.getCategoryName(),
            status,
            stream.getConcurrentUserCount(),
            followers,
            0
        );
    }

    private FollowerRankingResponse toGrowthResponse(StreamerFollowerGrowthProjection projection) {
        StreamStatus status = projection.getIsLive() ? StreamStatus.LIVE : StreamStatus.OFFLINE;

        return new FollowerRankingResponse(
            projection.getStreamId(),
            projection.getStreamerName(),
            projection.getLiveTitle(),
            projection.getProfileImageUrl(),
            projection.getCategoryName(),
            status,
            projection.getConcurrentUserCount(),
            projection.getFollowerCount(),
            projection.getWeeklyGrowth()
        );
    }

    private int resolveValidDays(int days) {
        if (days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private int resolveValidLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_RANKING_LIMIT;
        }
        return Math.min(limit, MAX_RANKING_LIMIT);
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
                    currentDate,
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
