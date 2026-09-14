package io.slice.stream.apiserver.streamer.domain.service;

import io.slice.stream.apiserver.streamer.domain.model.DailySplitSegment;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SessionMidnightSplitter {

    private final ZoneId zoneId;

    public SessionMidnightSplitter() {
        this(ZoneId.of("Asia/Seoul"));
    }

    public SessionMidnightSplitter(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    public List<DailySplitSegment> split(
        Instant startedAt,
        Instant endedAt,
        int peakViewers,
        int avgViewers,
        String title,
        String category
    ) {
        if (startedAt == null || endedAt == null || !endedAt.isAfter(startedAt)) {
            return Collections.emptyList();
        }

        List<DailySplitSegment> segments = new ArrayList<>();
        ZonedDateTime currentStart = startedAt.atZone(zoneId);
        ZonedDateTime finalEnd = endedAt.atZone(zoneId);

        while (currentStart.isBefore(finalEnd)) {
            LocalDate currentDate = currentStart.toLocalDate();
            ZonedDateTime nextMidnight = currentDate.plusDays(1).atStartOfDay(zoneId);
            ZonedDateTime segmentEnd = finalEnd.isBefore(nextMidnight) ? finalEnd : nextMidnight;

            long durationSeconds = Duration.between(currentStart, segmentEnd).getSeconds();
            if (durationSeconds > 0) {
                segments.add(new DailySplitSegment(
                    currentDate,
                    durationSeconds,
                    peakViewers,
                    avgViewers,
                    title,
                    category
                ));
            }

            currentStart = segmentEnd;
        }

        return Collections.unmodifiableList(segments);
    }
}
