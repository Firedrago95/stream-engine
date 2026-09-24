package io.slice.stream.engine.analyzer.domain.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatSummaryTest {

    @Test
    void 총_채팅수가_0이면_구독자_비율은_0을_반환한다() {
        ChatSummary summary = new ChatSummary(0, 0);

        double ratio = summary.calculateSubscriberRatio();

        assertThat(ratio).isEqualTo(0.0);
    }

    @Test
    void 구독자_비율을_소수점_첫째자리까지_반올림하여_계산한다() {
        ChatSummary summary = new ChatSummary(10, 3);

        double ratio = summary.calculateSubscriberRatio();

        assertThat(ratio).isEqualTo(30.0);
    }

    @Test
    void 소수점_둘째자리에서_반올림이_정상_동작한다() {
        ChatSummary summary = new ChatSummary(3, 1);

        double ratio = summary.calculateSubscriberRatio();

        assertThat(ratio).isEqualTo(33.3);
    }
}
