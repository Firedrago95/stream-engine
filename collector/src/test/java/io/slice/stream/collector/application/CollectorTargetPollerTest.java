package io.slice.stream.collector.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.slice.stream.collector.fake.FakeChatCollector;
import io.slice.stream.collector.fake.FakeChatCollectorFactory;
import io.slice.stream.collector.fake.FakeTargetStreamReader;
import io.slice.stream.core.model.StreamTarget;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CollectorTargetPollerTest {

    private FakeTargetStreamReader fakeTargetStreamReader;
    private FakeChatCollectorFactory fakeCollectorFactory;
    private ChatManager chatManager;
    private CollectorTargetPoller poller;

    @BeforeEach
    void setUp() {
        fakeTargetStreamReader = new FakeTargetStreamReader();
        fakeCollectorFactory = new FakeChatCollectorFactory();
        chatManager = new ChatManager(fakeCollectorFactory, Runnable::run, new SimpleMeterRegistry());
        poller = new CollectorTargetPoller(fakeTargetStreamReader, chatManager);
    }

    @Test
    @DisplayName("Redis에 새로운 활성 방송 스트림이 등록되면 수집기가 자동으로 시작된다")
    void startCollectorWhenTargetStreamGoesLive() {
        StreamTarget liveTarget = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 100L, "제목1", 500, "thumb1.jpg", "소통", Instant.now());
        fakeTargetStreamReader.addTarget(liveTarget);

        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_1");
        FakeChatCollector collector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(collector).isNotNull();
        assertThat(collector.isConnected()).isTrue();
    }

    @Test
    @DisplayName("진행 중이던 방송이 종료되어 Redis 목록에서 사라지면 수집기가 disconnect되고 관리 목록에서 제거된다")
    void stopCollectorWhenStreamCloses() {
        StreamTarget liveTarget = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 100L, "제목1", 500, "thumb1.jpg", "소통", Instant.now());
        fakeTargetStreamReader.addTarget(liveTarget);
        poller.pollTargets();
        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_1");

        fakeTargetStreamReader.removeTarget("ch_target_1");
        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).doesNotContain("ch_target_1");
        FakeChatCollector collector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(collector.isConnected()).isFalse();
    }

    @Test
    @DisplayName("이미 수집 중인 스트림은 중복 연결하지 않고 기존 수집기를 유지한다")
    void maintainExistingCollectorWithoutDuplicateConnection() {
        StreamTarget liveTarget = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 100L, "제목1", 500, "thumb1.jpg", "소통", Instant.now());
        fakeTargetStreamReader.addTarget(liveTarget);

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
        StreamTarget liveTarget = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 100L, "제목1", 500, "thumb1.jpg", "소통", Instant.now());
        fakeTargetStreamReader.addTarget(liveTarget);
        poller.pollTargets();
        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_1");

        fakeTargetStreamReader.setThrowOnRead(true);
        poller.pollTargets();

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
        StreamTarget firstLive = new StreamTarget("ch_target_1", "침착맨", "chat_room_old", 100L, "1차 방송", 500, "thumb1.jpg", "소통", Instant.now());
        fakeTargetStreamReader.addTarget(firstLive);
        poller.pollTargets();
        FakeChatCollector firstCollector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(firstCollector.isConnected()).isTrue();

        StreamTarget secondLive = new StreamTarget("ch_target_1", "침착맨", "chat_room_new", 200L, "2차 방송", 700, "thumb1.jpg", "소통", Instant.now());
        fakeTargetStreamReader.addTarget(secondLive);
        poller.pollTargets();

        assertThat(firstCollector.isConnected()).isFalse();
        FakeChatCollector newCollector = fakeCollectorFactory.getCollector("ch_target_1");
        assertThat(newCollector.isConnected()).isTrue();
        assertThat(chatManager.isCollecting("ch_target_1", "chat_room_new")).isTrue();
    }

    @Test
    @DisplayName("여러 채널이 동시에 방송을 시작할 때 모든 채널의 수집기가 정상 시작된다")
    void startAllCollectorsWhenMultipleStreamsGoLiveConcurrently() {
        StreamTarget live1 = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 0L, "제목1", 500, null, "소통", Instant.now());
        StreamTarget live2 = new StreamTarget("ch_target_2", "풍월량", "chat_room_2", 0L, "제목2", 600, null, "게임", Instant.now());
        StreamTarget live3 = new StreamTarget("ch_target_3", "옥냥이", "chat_room_3", 0L, "제목3", 700, null, "게임", Instant.now());
        fakeTargetStreamReader.addTarget(live1);
        fakeTargetStreamReader.addTarget(live2);
        fakeTargetStreamReader.addTarget(live3);

        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).containsExactlyInAnyOrder("ch_target_1", "ch_target_2", "ch_target_3");
        assertThat(fakeCollectorFactory.getCollector("ch_target_1").isConnected()).isTrue();
        assertThat(fakeCollectorFactory.getCollector("ch_target_2").isConnected()).isTrue();
        assertThat(fakeCollectorFactory.getCollector("ch_target_3").isConnected()).isTrue();
    }

    @Test
    @DisplayName("여러 채널이 동시에 방송을 종료하거나 타겟 풀이 비었을 때 모든 수집기가 누락 없이 종료된다")
    void stopAllCollectorsWhenMultipleStreamsCloseConcurrently() {
        StreamTarget live1 = new StreamTarget("ch_target_1", "침착맨", "chat_room_1", 0L, "제목1", 500, null, "소통", Instant.now());
        StreamTarget live2 = new StreamTarget("ch_target_2", "풍월량", "chat_room_2", 0L, "제목2", 600, null, "게임", Instant.now());
        StreamTarget live3 = new StreamTarget("ch_target_3", "옥냥이", "chat_room_3", 0L, "제목3", 700, null, "게임", Instant.now());
        fakeTargetStreamReader.addTarget(live1);
        fakeTargetStreamReader.addTarget(live2);
        fakeTargetStreamReader.addTarget(live3);

        poller.pollTargets();
        assertThat(chatManager.getActiveChannelIds()).hasSize(3);

        fakeTargetStreamReader.removeTarget("ch_target_1");
        fakeTargetStreamReader.removeTarget("ch_target_2");
        fakeTargetStreamReader.removeTarget("ch_target_3");

        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).isEmpty();
        assertThat(fakeCollectorFactory.getCollector("ch_target_1").isConnected()).isFalse();
        assertThat(fakeCollectorFactory.getCollector("ch_target_2").isConnected()).isFalse();
        assertThat(fakeCollectorFactory.getCollector("ch_target_3").isConnected()).isFalse();
    }

    @Test
    @DisplayName("19금 연령제한 방송은 수집 대상에서 제외되어 수집기가 시작되지 않는다")
    void doNotStartCollectorWhenStreamIsAdult() {
        StreamTarget adultStream = new StreamTarget(
            "ch_target_adult", "고수달", "chat_room_adult", 100L, "19금 음주 토크", 500, null, "talk", Instant.now(), true
        );
        fakeTargetStreamReader.addTarget(adultStream);

        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).doesNotContain("ch_target_adult");
    }

    @Test
    @DisplayName("방송 중 19금이 설정되면 수집기가 즉시 disconnect되고 해제 시 자동으로 재연결된다")
    void disconnectWhenStreamChangesToAdultAndReconnectWhenLifted() {
        StreamTarget normalStream = new StreamTarget(
            "ch_target_dynamic", "고수달", "chat_room_dynamic", 100L, "일반 토크", 500, null, "talk", Instant.now(), false
        );
        fakeTargetStreamReader.addTarget(normalStream);

        // 1. 일반 방송 시작 시 수집기 연결
        poller.pollTargets();
        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_dynamic");
        FakeChatCollector collector = fakeCollectorFactory.getCollector("ch_target_dynamic");
        assertThat(collector.isConnected()).isTrue();

        // 2. 19금으로 전환 시 수집기 정상 종료(disconnect)
        StreamTarget adultStream = new StreamTarget(
            "ch_target_dynamic", "고수달", "chat_room_dynamic", 100L, "19금 음주 토크", 500, null, "talk", Instant.now(), true
        );
        fakeTargetStreamReader.addTarget(adultStream);
        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).doesNotContain("ch_target_dynamic");
        assertThat(collector.isConnected()).isFalse();

        // 3. 19금 해제 시 자동으로 수집기 재연결
        fakeTargetStreamReader.addTarget(normalStream);
        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).contains("ch_target_dynamic");
        FakeChatCollector reconnected = fakeCollectorFactory.getCollector("ch_target_dynamic");
        assertThat(reconnected.isConnected()).isTrue();
    }

    @Test
    @DisplayName("chatChannelId가 누락되었거나 빈 문자열인 스트림은 수집 대상에서 안전하게 제외된다")
    void doNotStartCollectorWhenChatChannelIdIsMissing() {
        StreamTarget invalidStream = new StreamTarget(
            "ch_invalid", "스트리머", null, 100L, "채팅방 없는 방송", 500, null, "talk", Instant.now(), false
        );
        fakeTargetStreamReader.addTarget(invalidStream);

        poller.pollTargets();

        assertThat(chatManager.getActiveChannelIds()).doesNotContain("ch_invalid");
    }
}
