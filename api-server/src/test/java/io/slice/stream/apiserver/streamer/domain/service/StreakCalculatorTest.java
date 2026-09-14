package io.slice.stream.apiserver.streamer.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreakCalculatorTest {

    private StreakCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new StreakCalculator();
    }

    @Test
    void 오늘_방송을_진행한_경우_오늘부터_연속된_일수를_계산한다() {
        LocalDate today = LocalDate.of(2026, 9, 14);
        Set<LocalDate> activeDates = Set.of(
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 9, 13),
            LocalDate.of(2026, 9, 12),
            LocalDate.of(2026, 9, 11)
        );

        int streak = calculator.calculate(activeDates, today);

        assertThat(streak).isEqualTo(4);
    }

    @Test
    void 오늘_아직_방송하지_않았지만_어제까지_연속_방송한_경우_스트릭이_유지된다() {
        LocalDate today = LocalDate.of(2026, 9, 14);
        Set<LocalDate> activeDates = Set.of(
            LocalDate.of(2026, 9, 13),
            LocalDate.of(2026, 9, 12),
            LocalDate.of(2026, 9, 11)
        );

        int streak = calculator.calculate(activeDates, today);

        assertThat(streak).isEqualTo(3);
    }

    @Test
    void 어제_방송하지_않았으면_스트릭은_0이다() {
        LocalDate today = LocalDate.of(2026, 9, 14);
        Set<LocalDate> activeDates = Set.of(
            LocalDate.of(2026, 9, 12),
            LocalDate.of(2026, 9, 11)
        );

        int streak = calculator.calculate(activeDates, today);

        assertThat(streak).isZero();
    }

    @Test
    void 방송_이력이_전혀_없으면_스트릭은_0이다() {
        LocalDate today = LocalDate.of(2026, 9, 14);

        int streak = calculator.calculate(Collections.emptySet(), today);

        assertThat(streak).isZero();
    }

    @Test
    void 중간에_하루_빠진_날이_있으면_해당_날짜_직전까지만_스트릭으로_계산된다() {
        LocalDate today = LocalDate.of(2026, 9, 14);
        Set<LocalDate> activeDates = Set.of(
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 9, 13),
            LocalDate.of(2026, 9, 10),
            LocalDate.of(2026, 9, 9)
        );

        int streak = calculator.calculate(activeDates, today);

        assertThat(streak).isEqualTo(2);
    }
}
