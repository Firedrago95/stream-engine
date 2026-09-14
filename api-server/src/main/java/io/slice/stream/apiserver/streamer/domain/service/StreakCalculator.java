package io.slice.stream.apiserver.streamer.domain.service;

import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class StreakCalculator {

    public int calculate(Set<LocalDate> activeDates, LocalDate today) {
        if (activeDates == null || activeDates.isEmpty() || today == null) {
            return 0;
        }

        LocalDate current = today;
        if (!activeDates.contains(current)) {
            current = today.minusDays(1);
            if (!activeDates.contains(current)) {
                return 0;
            }
        }

        int streak = 0;
        while (activeDates.contains(current)) {
            streak++;
            current = current.minusDays(1);
        }

        return streak;
    }
}
