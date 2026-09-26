package io.slice.stream.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreamTargetTest {

    @Test
    @DisplayName("서로 다른 채널 ID를 가진 스트림 타겟은 liveId가 0으로 같아도 동등하지 않다")
    void shouldNotBeEqualWhenChannelIdIsDifferentEvenIfLiveIdIsZero() {
        StreamTarget target1 = new StreamTarget("channel1", "스트리머1", "chat1", 0L, "제목1", 100, null, "소통", Instant.EPOCH);
        StreamTarget target2 = new StreamTarget("channel2", "스트리머2", "chat2", 0L, "제목2", 200, null, "게임", Instant.EPOCH);

        assertThat(target1).isNotEqualTo(target2);
        assertThat(target1.hashCode()).isNotEqualTo(target2.hashCode());

        Set<StreamTarget> targets = Set.of(target1, target2);
        assertThat(targets).hasSize(2);
    }

    @Test
    @DisplayName("동일한 채널 ID와 liveId를 가진 스트림 타겟은 시청자 수나 제목이 달라도 동등하다")
    void shouldBeEqualWhenChannelIdAndLiveIdAreSameEvenIfMetadataDiffers() {
        StreamTarget target1 = new StreamTarget("channel1", "스트리머1", "chat1", 12345L, "제목1", 100, null, "소통", Instant.EPOCH);
        StreamTarget target2 = new StreamTarget("channel1", "스트리머1", "chat1", 12345L, "제목2", 500, null, "게임", Instant.EPOCH);

        assertThat(target1).isEqualTo(target2);
        assertThat(target1.hashCode()).isEqualTo(target2.hashCode());
    }

    @Test
    @DisplayName("동일한 채널 ID여도 liveId가 다르면 동등하지 않다")
    void shouldNotBeEqualWhenLiveIdIsDifferentForSameChannel() {
        StreamTarget target1 = new StreamTarget("channel1", "스트리머1", "chat1", 100L, "1차 방송", 100, null, "소통", Instant.EPOCH);
        StreamTarget target2 = new StreamTarget("channel1", "스트리머1", "chat2", 200L, "2차 방송", 100, null, "소통", Instant.EPOCH);

        assertThat(target1).isNotEqualTo(target2);
    }

    @Test
    @DisplayName("adult와 paidPromotion에 null이 전달되어도 false 기본값으로 안전하게 초기화된다")
    void shouldInitializeWithDefaultFalseWhenAdultOrPaidPromotionIsNull() {
        StreamTarget target = new StreamTarget(
            "channel1", "스트리머1", "chat1", 100L, "방송", 100, null, "소통", Instant.EPOCH, null, null
        );

        assertThat(target.adult()).isFalse();
        assertThat(target.paidPromotion()).isFalse();
    }
}
