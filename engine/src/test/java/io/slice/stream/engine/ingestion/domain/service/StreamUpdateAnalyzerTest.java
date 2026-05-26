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
        StreamTarget streamTarget1 = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title1", 1200, "thumb1.jpg", "소통", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("channel2", "풍월량", "chat2", 200L, "title2", 1000, "thumb2.jpg", "게임", Instant.EPOCH);

        List<StreamTarget> currentTargets = List.of(streamTarget1, streamTarget2);
        Set<String> activeChannelIds = Set.of("channel1", "channel3");
        List<StreamTarget> oldTargets = List.of(streamTarget1);

        StreamUpdateResults results = analyzer.analyze(currentTargets, activeChannelIds, oldTargets);

        assertAll(
            () -> assertThat(results.newStreams()).hasSize(1),
            () -> assertThat(results.newStreams().iterator().next().channelId()).isEqualTo("channel2"),
            () -> assertThat(results.closedStreamIds()).containsExactly("channel3")
        );
    }

    @Test
    void 제목이나_카테고리가_변경되었을_경우_변경_내역을_추출한다() {
        StreamTarget oldTarget = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title1", 1200, "thumb1.jpg", "소통", Instant.EPOCH);
        StreamTarget newTarget = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title2", 1200, "thumb1.jpg", "게임", Instant.EPOCH);

        List<StreamTarget> currentTargets = List.of(newTarget);
        Set<String> activeChannelIds = Set.of("channel1");
        List<StreamTarget> oldTargets = List.of(oldTarget);

        StreamUpdateResults results = analyzer.analyze(currentTargets, activeChannelIds, oldTargets);

        assertThat(results.changedStreams()).hasSize(1);
        ChangedStream changed = results.changedStreams().iterator().next();

        assertAll(
            () -> assertThat(changed.streamId()).isEqualTo("channel1"),
            () -> assertThat(changed.oldTitle()).isEqualTo("title1"),
            () -> assertThat(changed.newTitle()).isEqualTo("title2"),
            () -> assertThat(changed.oldCategory()).isEqualTo("소통"),
            () -> assertThat(changed.newCategory()).isEqualTo("게임")
        );
    }
}
