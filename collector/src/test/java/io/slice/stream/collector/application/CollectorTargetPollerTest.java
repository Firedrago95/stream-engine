package io.slice.stream.collector.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.slice.stream.collector.fake.FakeChatCollector;
import io.slice.stream.collector.fake.FakeChatCollectorFactory;
import io.slice.stream.collector.fake.FakeLiveStatusClient;
import io.slice.stream.collector.fake.FakeTargetStreamReader;
import io.slice.stream.core.model.StreamTarget;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CollectorTargetPollerTest {

    private FakeTargetStreamReader fakeTargetStreamReader;
    private FakeLiveStatusClient fakeLiveStatusClient;
    private FakeChatCollectorFactory fakeCollectorFactory;
    private ChatManager chatManager;
    private CollectorTargetPoller poller;

    @BeforeEach
    void setUp() {
        fakeTargetStreamReader = new FakeTargetStreamReader();
        fakeLiveStatusClient = new FakeLiveStatusClient();
        fakeCollectorFactory = new FakeChatCollectorFactory();
        chatManager = new ChatManager(fakeCollectorFactory, Runnable::run, new SimpleMeterRegistry());
        poller = new CollectorTargetPoller(fakeTargetStreamReader, fakeLiveStatusClient, chatManager);
    }

    @Test
    @DisplayName("Redis 타겟 풀에 등록된 채널이 방송을 시작하면 수집기가 자동으로 시작된다")
    void startCollectorWhenTargetStreamGoesLive() {
        // given: 타겟 채널 등록 및 방송 시작 상태 주입
        fakeTargetStreamReader.addTarget("ch_target_1");
        StreamTarget liveTarget = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 100L, "제목1", 500, "thumb1.jpg", "소통", Instant.now());
        fakeLiveStatusClient.setOpenStream(liveTarget);

        // when: 폴링 수행
        poller.pollTargets();

        // then: 해당 채널 수집기가 활성화됨
        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_1");
        FakeChatCollector collector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(collector).isNotNull();
        assertThat(collector.isConnected()).isTrue();
    }

    @Test
    @DisplayName("진행 중이던 방송이 종료되면 수집기가 disconnect되고 관리 목록에서 제거된다")
    void stopCollectorWhenStreamCloses() {
        // given: 먼저 방송 수집 시작
        fakeTargetStreamReader.addTarget("ch_target_1");
        StreamTarget liveTarget = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 100L, "제목1", 500, "thumb1.jpg", "소통", Instant.now());
        fakeLiveStatusClient.setOpenStream(liveTarget);
        poller.pollTargets();
        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_1");

        // when: 방송 종료 (LiveStatus에서 제거) 후 폴링
        fakeLiveStatusClient.removeOpenStream("ch_target_1");
        poller.pollTargets();

        // then: 수집기 종료 및 목록 제거
        assertThat(chatManager.getActiveChannelIds()).doesNotContain("ch_target_1");
        FakeChatCollector collector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(collector.isConnected()).isFalse();
    }

    @Test
    @DisplayName("방송이 계속 켜져 있어도 타겟 풀에서 제외되면 수집을 중단한다")
    void stopCollectorWhenRemovedFromTargetPool() {
        // given: 타겟 등록 및 수집 시작
        fakeTargetStreamReader.addTarget("ch_target_1");
        StreamTarget liveTarget = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 100L, "제목1", 500, "thumb1.jpg", "소통", Instant.now());
        fakeLiveStatusClient.setOpenStream(liveTarget);
        poller.pollTargets();
        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_1");

        // when: 방송은 켜져 있으나 타겟 풀에서 제외됨
        fakeTargetStreamReader.removeTarget("ch_target_1");
        poller.pollTargets();

        // then: 수집 중단
        assertThat(chatManager.getActiveChannelIds()).doesNotContain("ch_target_1");
        FakeChatCollector collector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(collector.isConnected()).isFalse();
    }

    @Test
    @DisplayName("이미 수집 중인 스트림은 중복 연결하지 않고 유지한다")
    void maintainExistingCollectorWithoutDuplicateConnection() {
        fakeTargetStreamReader.addTarget("ch_target_1");
        StreamTarget liveTarget = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 100L, "제목1", 500, "thumb1.jpg", "소통", Instant.now());
        fakeLiveStatusClient.setOpenStream(liveTarget);

        poller.pollTargets();
        FakeChatCollector firstCollector = fakeCollectorFactory.getCollector("ch_target_1");

        poller.pollTargets();
        FakeChatCollector secondCollector = fakeCollectorFactory.getCollector("ch_target_1");

        assertThat(firstCollector).isSameAs(secondCollector);
        assertThat(chatManager.getActiveChannelIds()).hasSize(1);
    }

    @Test
    @DisplayName("타겟 풀이 비어있으면 아무 동작도 하지 않는다")
    void doNothingWhenTargetPoolIsEmpty() {
        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).isEmpty();
    }
}
