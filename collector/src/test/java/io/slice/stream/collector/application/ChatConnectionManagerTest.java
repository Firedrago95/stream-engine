package io.slice.stream.collector.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.collector.domain.BackoffPolicy;
import io.slice.stream.collector.fake.FakeChatClient;
import io.slice.stream.collector.fake.FakeChatMessageListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatConnectionManagerTest {

    private FakeChatClient fakeChatClient;
    private FakeChatMessageListener fakeDownstreamListener;
    private ChatConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        fakeChatClient = new FakeChatClient();
        fakeDownstreamListener = new FakeChatMessageListener();
        connectionManager = new ChatConnectionManager(
            fakeChatClient,
            fakeDownstreamListener,
            "chat_room_123",
            "ch_streamer_1",
            Runnable::run,
            BackoffPolicy.noDelay()
        );
    }

    @Test
    @DisplayName("start 호출 시 ChatClient가 올바른 채널 정보로 연결되고 onConnected가 전파된다")
    void connectSuccessfullyOnStart() {
        connectionManager.start();

        assertThat(fakeChatClient.isConnected()).isTrue();
        assertThat(fakeChatClient.getConnectedChannelId()).isEqualTo("ch_streamer_1");
        assertThat(fakeChatClient.getConnectedChatChannelId()).isEqualTo("chat_room_123");
        assertThat(fakeDownstreamListener.isConnected()).isTrue();
    }

    @Test
    @DisplayName("수동 disconnect 호출 시 ChatClient 연결이 종료된다")
    void disconnectManually() {
        connectionManager.start();
        assertThat(fakeChatClient.isConnected()).isTrue();

        connectionManager.disconnect();
        assertThat(fakeChatClient.isConnected()).isFalse();
    }

    @Test
    @DisplayName("원격에서 연결이 끊어지면 downstreamListener에 알리고 자동으로 재연결한다")
    void reconnectAutomaticallyWhenDisconnected() {
        connectionManager.start();
        assertThat(fakeChatClient.isConnected()).isTrue();

        fakeChatClient.triggerRemoteDisconnect();

        assertThat(fakeDownstreamListener.isDisconnected()).isTrue();
        assertThat(fakeChatClient.isConnected()).isTrue();
    }

    @Test
    @DisplayName("chatChannelId가 null이거나 공백이면 연결을 시도하지 않는다")
    void doNotConnectWhenChatChannelIdIsBlank() {
        ChatConnectionManager blankManager = new ChatConnectionManager(
            fakeChatClient,
            fakeDownstreamListener,
            "   ",
            "ch_streamer_1",
            Runnable::run,
            BackoffPolicy.noDelay()
        );

        blankManager.start();

        assertThat(fakeChatClient.isConnected()).isFalse();
    }
}
