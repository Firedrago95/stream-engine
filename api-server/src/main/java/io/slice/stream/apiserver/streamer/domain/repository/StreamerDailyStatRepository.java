package io.slice.stream.apiserver.streamer.domain.repository;

import io.slice.stream.apiserver.streamer.domain.model.StreamerDailyStat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StreamerDailyStatRepository {

    List<StreamerDailyStat> findByChannelIdAndDateRange(String channelId, LocalDate startDate, LocalDate endDate);

    Optional<StreamerDailyStat> findByChannelIdAndStatDate(String channelId, LocalDate statDate);

    void save(StreamerDailyStat stat);

    void saveAll(List<StreamerDailyStat> stats);
}
