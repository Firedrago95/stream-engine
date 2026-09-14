package io.slice.stream.apiserver.streamer.infrastructure;

import io.slice.stream.apiserver.streamer.domain.model.StreamerDailyStat;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerDailyStatRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerDailyStatEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StreamerDailyStatRepositoryImpl implements StreamerDailyStatRepository {

    private final JpaStreamerDailyStatRepository jpaRepository;

    @Override
    public List<StreamerDailyStat> findByChannelIdAndDateRange(String channelId, LocalDate startDate, LocalDate endDate) {
        return jpaRepository.findByChannelIdAndDateRange(channelId, startDate, endDate).stream()
            .map(StreamerDailyStatEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<StreamerDailyStat> findByChannelIdAndStatDate(String channelId, LocalDate statDate) {
        return jpaRepository.findByChannelIdAndStatDate(channelId, statDate)
            .map(StreamerDailyStatEntity::toDomain);
    }

    @Override
    public List<LocalDate> findRecentActiveDates(String channelId, LocalDate today, int limit) {
        int validLimit = Math.max(1, limit);
        return jpaRepository.findActiveDatesUpTo(channelId, today, PageRequest.of(0, validLimit));
    }

    @Override
    public void save(StreamerDailyStat stat) {
        jpaRepository.save(StreamerDailyStatEntity.fromDomain(stat));
    }

    @Override
    public void saveAll(List<StreamerDailyStat> stats) {
        List<StreamerDailyStatEntity> entities = stats.stream()
            .map(StreamerDailyStatEntity::fromDomain)
            .toList();
        jpaRepository.saveAll(entities);
    }
}
