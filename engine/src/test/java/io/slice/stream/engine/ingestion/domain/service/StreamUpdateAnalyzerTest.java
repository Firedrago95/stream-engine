package io.slice.stream.engine.ingestion.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamUpdateAnalyzerTest {

    private final StreamUpdateAnalyzer analyzer = new StreamUpdateAnalyzer();

    @Test
    void 신규_방송과_종료된_방송을_판단한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title1", 1200, "thumb1.jpg", "소통", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("channel2", "풍월량", "chat2", 200L, "title2", 1000, "thumb2.jpg", "게임", Instant.EPOCH);

        List<StreamTarget> currentTargets = List.of(streamTarget1, streamTarget2);
        Set<String> activeChannelIds = Set.of("channel1", "channel3");
        StreamTarget streamTarget3 = new StreamTarget("channel3", "채널3", "chat3", 300L, "title3", 500, "thumb3.jpg", "먹방", Instant.EPOCH);
        List<StreamTarget> oldTargets = List.of(streamTarget1, streamTarget3);

        // when
        StreamUpdateResults results = analyzer.analyze(currentTargets, activeChannelIds, oldTargets, Instant.now());

        // then
        assertAll(
            () -> assertThat(results.newStreams()).hasSize(1),
            () -> assertThat(results.newStreams().iterator().next().channelId()).isEqualTo("channel2"),
            () -> assertThat(results.closedStreamIds()).containsExactly(streamTarget3)
        );
    }

    @Test
    void 제목이나_카테고리가_변경되었을_경우_변경_내역을_추출한다() {
        // given
        Instant startTime = Instant.now().minusSeconds(3600);
        Instant changedAt = Instant.now();
        StreamTarget oldTarget = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title1", 1200, "thumb1.jpg", "소통", startTime);
        StreamTarget newTarget = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title2", 1200, "thumb1.jpg", "게임", startTime);

        List<StreamTarget> currentTargets = List.of(newTarget);
        Set<String> activeChannelIds = Set.of("channel1");
        List<StreamTarget> oldTargets = List.of(oldTarget);

        // when
        StreamUpdateResults results = analyzer.analyze(currentTargets, activeChannelIds, oldTargets, changedAt);

        // then
        assertThat(results.changedStreams()).hasSize(1);
        ChangedStream changed = results.changedStreams().iterator().next();

        assertAll(
            () -> assertThat(changed.streamId()).isEqualTo("channel1"),
            () -> assertThat(changed.oldTitle()).isEqualTo("title1"),
            () -> assertThat(changed.newTitle()).isEqualTo("title2"),
            () -> assertThat(changed.oldCategory()).isEqualTo("소통"),
            () -> assertThat(changed.newCategory()).isEqualTo("게임"),
            () -> assertThat(changed.changedAt()).isEqualTo(changedAt),
            () -> assertThat(changed.changeOffsetMs()).isEqualTo(3600000L)
        );
    }

    @Test
    void 채널_ID가_이미_존재하더라도_liveId가_변경되면_이전_방송은_종료되고_새_방송은_신규로_판단한다() {
        Instant startTime = Instant.now().minusSeconds(3600);
        Instant now = Instant.now();

        StreamTarget oldTarget = new StreamTarget("channel1", "침착맨", "chat1", 100L, "어제 방송", 1200, "thumb1.jpg", "소통", startTime);
        StreamTarget newTarget = new StreamTarget("channel1", "침착맨", "chat2", 200L, "오늘 방송", 1500, "thumb1.jpg", "소통", now);

        List<StreamTarget> currentTargets = List.of(newTarget);
        Set<String> activeChannelIds = Set.of("channel1");
        List<StreamTarget> oldTargets = List.of(oldTarget);

        StreamUpdateResults results = analyzer.analyze(currentTargets, activeChannelIds, oldTargets, now);

        assertAll(
            () -> assertThat(results.closedStreamIds()).containsExactly(oldTarget),
            () -> assertThat(results.newStreams()).containsExactly(newTarget)
        );
    }
}
