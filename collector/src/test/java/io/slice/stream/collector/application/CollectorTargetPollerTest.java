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
        assertThat(firstCollector.isConnected()).isTrue();
    }

    @Test
    @DisplayName("타겟 조회 중 일시적 Redis 장애(예외)가 발생해도 기존 활성 수집기는 종료되지 않고 안전하게 유지된다")
    void preserveActiveCollectorsWhenTargetReaderThrowsException() {
        // given: 정상적으로 1개 채널 수집 중
        fakeTargetStreamReader.addTarget("ch_target_1");
        StreamTarget liveTarget = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 100L, "제목1", 500, "thumb1.jpg", "소통", Instant.now());
        fakeLiveStatusClient.setOpenStream(liveTarget);
        poller.pollTargets();
        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_1");

        // when: Redis 장애 발생 (예외 발생) 후 폴링
        fakeTargetStreamReader.setThrowOnRead(true);
        poller.pollTargets();

        // then: 빈 타겟으로 오판하여 수집기를 종료하지 않고 그대로 유지됨
        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_1");
        FakeChatCollector collector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(collector.isConnected()).isTrue();
    }

    @Test
    @DisplayName("타겟 풀이 비어있으면 아무 동작도 하지 않는다")
    void doNothingWhenTargetPoolIsEmpty() {
        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).isEmpty();
    }

    @Test
    @DisplayName("동일 채널이 새 방송을 시작하여 chatChannelId가 변경되면 폴러가 이를 감지하여 새 채팅방으로 갱신한다")
    void refreshCollectorWhenStreamRestartedWithNewChatChannelId() {
        // given: 1차 방송 수집 중
        fakeTargetStreamReader.addTarget("ch_target_1");
        StreamTarget firstLive = new StreamTarget("ch_target_1", "침착맨", "chat_room_old", 100L, "1차 방송", 500, "thumb1.jpg", "소통", Instant.now());
        fakeLiveStatusClient.setOpenStream(firstLive);
        poller.pollTargets();
        FakeChatCollector firstCollector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(firstCollector.isConnected()).isTrue();

        // when: 새 방송 시작으로 chatChannelId 변경됨
        StreamTarget secondLive = new StreamTarget("ch_target_1", "침착맨", "chat_room_new", 200L, "2차 방송", 700, "thumb1.jpg", "소통", Instant.now());
        fakeLiveStatusClient.setOpenStream(secondLive);
        poller.pollTargets();

        // then: 기존 수집기는 종료되고 새 수집기가 연결됨
        assertThat(firstCollector.isConnected()).isFalse();
        FakeChatCollector newCollector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(newCollector.isConnected()).isTrue();
        assertThat(chatManager.isCollecting("ch_target_1", "chat_room_new")).isTrue();
    }

    @Test
    @DisplayName("여러 채널이 동시에 방송을 시작할 때 liveId가 0이어도 모든 채널의 수집기가 정상 시작된다")
    void startAllCollectorsWhenMultipleStreamsGoLiveConcurrently() {
        fakeTargetStreamReader.addTarget("ch_target_1");
        fakeTargetStreamReader.addTarget("ch_target_2");
        fakeTargetStreamReader.addTarget("ch_target_3");

        StreamTarget live1 = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 0L, "제목1", 500, null, "소통", Instant.now());
        StreamTarget live2 = new StreamTarget("ch_target_2", "풍월량", "chat_room_2", 0L, "제목2", 600, null, "게임", Instant.now());
        StreamTarget live3 = new StreamTarget("ch_target_3", "옥냥이", "chat_room_3", 0L, "제목3", 700, null, "게임", Instant.now());
        fakeLiveStatusClient.setOpenStream(live1);
        fakeLiveStatusClient.setOpenStream(live2);
        fakeLiveStatusClient.setOpenStream(live3);

        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).containsExactlyInAnyOrder("ch_target_1", "ch_target_2", "ch_target_3");
        assertThat(fakeCollectorFactory.getCollector("ch_target_1").isConnected()).isTrue();
        assertThat(fakeCollectorFactory.getCollector("ch_target_2").isConnected()).isTrue();
        assertThat(fakeCollectorFactory.getCollector("ch_target_3").isConnected()).isTrue();
    }

    @Test
    @DisplayName("여러 채널이 동시에 방송을 종료하거나 타겟 풀이 비었을 때 모든 수집기가 누락 없이 종료된다")
    void stopAllCollectorsWhenMultipleStreamsCloseConcurrently() {
        fakeTargetStreamReader.addTarget("ch_target_1");
        fakeTargetStreamReader.addTarget("ch_target_2");
        fakeTargetStreamReader.addTarget("ch_target_3");

        StreamTarget live1 = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 0L, "제목1", 500, null, "소통", Instant.now());
        StreamTarget live2 = new StreamTarget("ch_target_2", "풍월량", "chat_room_2", 0L, "제목2", 600, null, "게임", Instant.now());
        StreamTarget live3 = new StreamTarget("ch_target_3", "옥냥이", "chat_room_3", 0L, "제목3", 700, null, "게임", Instant.now());
        fakeLiveStatusClient.setOpenStream(live1);
        fakeLiveStatusClient.setOpenStream(live2);
        fakeLiveStatusClient.setOpenStream(live3);

        poller.pollTargets();
        assertThat(chatManager.getActiveChannelIds()).hasSize(3);

        fakeLiveStatusClient.removeOpenStream("ch_target_1");
        fakeLiveStatusClient.removeOpenStream("ch_target_2");
        fakeLiveStatusClient.removeOpenStream("ch_target_3");

        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).isEmpty();
        assertThat(fakeCollectorFactory.getCollector("ch_target_1").isConnected()).isFalse();
        assertThat(fakeCollectorFactory.getCollector("ch_target_2").isConnected()).isFalse();
        assertThat(fakeCollectorFactory.getCollector("ch_target_3").isConnected()).isFalse();
    }
}
