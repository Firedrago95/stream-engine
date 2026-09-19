package io.slice.stream.collector.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.slice.stream.collector.fake.FakeChatCollector;
import io.slice.stream.collector.fake.FakeChatCollectorFactory;
import io.slice.stream.core.model.StreamTarget;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatManagerTest {

    private FakeChatCollectorFactory collectorFactory;
    private SimpleMeterRegistry meterRegistry;
    private ChatManager chatManager;

    @BeforeEach
    void setUp() {
        collectorFactory = new FakeChatCollectorFactory();
        meterRegistry = new SimpleMeterRegistry();
        chatManager = new ChatManager(collectorFactory, Runnable::run, meterRegistry);
    }

    @Test
    @DisplayName("새로운 스트림이 들어오면 해당 채널의 수집기를 시작한다")
    void startCollectorForNewStream() {
        StreamTarget target = new StreamTarget("ch1", "스트리머1", "chat1", 1L, "방송1", 100, "thumb1.jpg", "게임", Instant.now());

        chatManager.manageStreams(Set.of(target), Collections.emptySet());

        assertThat(chatManager.getActiveChannelIds()).contains("ch1");
        FakeChatCollector collector = collectorFactory.getCollector("ch1");
        assertThat(collector).isNotNull();
        assertThat(collector.isConnected()).isTrue();
    }

    @Test
    @DisplayName("종료된 스트림이 전달되면 해당 수집기를 disconnect하고 맵에서 제거한다")
    void disconnectCollectorForClosedStream() {
        StreamTarget target = new StreamTarget("ch1", "스트리머1", "chat1", 1L, "방송1", 100, "thumb1.jpg", "게임", Instant.now());
        chatManager.manageStreams(Set.of(target), Collections.emptySet());

        FakeChatCollector collector = collectorFactory.getCollector("ch1");
        assertThat(collector.isConnected()).isTrue();

        chatManager.manageStreams(Collections.emptySet(), Set.of(target));

        assertThat(chatManager.getActiveChannelIds()).doesNotContain("ch1");
        assertThat(collector.isConnected()).isFalse();
    }

    @Test
    @DisplayName("chatChannelId가 null이거나 빈 문자열이면 수집을 시작하지 않는다")
    void ignoreInvalidChatChannelId() {
        StreamTarget invalidTarget1 = new StreamTarget("ch_null", "채널1", null, 1L, "방송1", 10, "thumb.jpg", "소통", Instant.now());
        StreamTarget invalidTarget2 = new StreamTarget("ch_blank", "채널2", "   ", 2L, "방송2", 20, "thumb.jpg", "소통", Instant.now());

        chatManager.manageStreams(Set.of(invalidTarget1, invalidTarget2), Collections.emptySet());

        assertThat(chatManager.getActiveChannelIds()).isEmpty();
    }

    @Test
    @DisplayName("동일 채널이 종료와 신규로 동시 유입되면 기존 연결을 끊고 새로 연결한다")
    void replaceCollectorWhenStreamRecreated() {
        StreamTarget oldTarget = new StreamTarget("ch1", "침착맨", "chatOld", 100L, "어제방송", 100, "thumb.jpg", "소통", Instant.now());
        chatManager.manageStreams(Set.of(oldTarget), Collections.emptySet());
        FakeChatCollector oldCollector = collectorFactory.getCollector("ch1");

        StreamTarget newTarget = new StreamTarget("ch1", "침착맨", "chatNew", 200L, "오늘방송", 200, "thumb.jpg", "소통", Instant.now());
        chatManager.manageStreams(Set.of(newTarget), Set.of(oldTarget));

        assertThat(oldCollector.isConnected()).isFalse();
        assertThat(chatManager.getActiveChannelIds()).contains("ch1");
    }

    @Test
    @DisplayName("동일 채널에서 chatChannelId가 변경된 타겟이 들어오면 기존 수집기를 disconnect하고 새 수집기로 자동 교체한다")
    void replaceCollectorWhenChatChannelIdChanged() {
        StreamTarget oldTarget = new StreamTarget("ch1", "스트리머1", "chatOld", 1L, "방송1", 100, "thumb1.jpg", "게임", Instant.now());
        chatManager.manageStreams(Set.of(oldTarget), Collections.emptySet());
        FakeChatCollector oldCollector = collectorFactory.getCollector("ch1");
        assertThat(oldCollector.isConnected()).isTrue();
        assertThat(chatManager.isCollecting("ch1", "chatOld")).isTrue();
        assertThat(chatManager.isCollecting("ch1", "chatNew")).isFalse();

        StreamTarget newTarget = new StreamTarget("ch1", "스트리머1", "chatNew", 2L, "방송2", 200, "thumb2.jpg", "게임", Instant.now());
        chatManager.manageStreams(Set.of(newTarget), Collections.emptySet());

        assertThat(oldCollector.isConnected()).isFalse();
        FakeChatCollector newCollector = collectorFactory.getCollector("ch1");
        assertThat(newCollector.isConnected()).isTrue();
        assertThat(chatManager.isCollecting("ch1", "chatNew")).isTrue();
    }


    @Test
    @DisplayName("수집기 활성화 시 기존 engine 및 collector 규격의 웹소켓 연결 게이지가 모두 정확히 반영된다")
    void measureActiveWebSocketConnectionsMetrics() {
        StreamTarget target1 = new StreamTarget("ch1", "스트리머1", "chat1", 1L, "방송1", 100, "thumb1.jpg", "게임", Instant.now());
        StreamTarget target2 = new StreamTarget("ch2", "스트리머2", "chat2", 2L, "방송2", 200, "thumb2.jpg", "게임", Instant.now());

        chatManager.manageStreams(Set.of(target1, target2), Collections.emptySet());

        double collectorGauge = meterRegistry.get("collector.websocket.connections.active").gauge().value();
        double engineGauge = meterRegistry.get("engine.websocket.connections.active").gauge().value();

        assertThat(collectorGauge).isEqualTo(2.0);
        assertThat(engineGauge).isEqualTo(2.0);

        chatManager.manageStreams(Collections.emptySet(), Set.of(target1));

        assertThat(meterRegistry.get("collector.websocket.connections.active").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("engine.websocket.connections.active").gauge().value()).isEqualTo(1.0);
    }
}
