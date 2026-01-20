package io.slice.stream.engine.chat.infrastructure.chzzk.websocket;

import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.infrastructure.chzzk.ChzzkMessageConverter;
import io.slice.stream.engine.chat.infrastructure.chzzk.dto.request.ChzzkAuthRequest;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class ChzzkWebSocketListener implements Listener {

    private final ChatMessageListener messageListener;
    private final String chatChannelId;
    private final String accessToken;
    private final JsonMapper jsonMapper;
    private final ChzzkMessageConverter messageConverter;
    private final StringBuilder textBuffer = new StringBuilder();

    private WebSocket webSocket;
    private volatile boolean isRunning = true;

    public ChzzkWebSocketListener(
        ChatMessageListener messageListener,
        String chatChannelId,
        String accessToken,
        JsonMapper jsonMapper,
        ChzzkMessageConverter messageConverter
    ) {
        this.messageListener = messageListener;
        this.chatChannelId = chatChannelId;
        this.accessToken = accessToken;
        this.jsonMapper = jsonMapper;
        this.messageConverter = messageConverter;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        Listener.super.onOpen(webSocket);
        this.webSocket = webSocket;
        this.messageListener.onConnected();
        log.info("[{}] Websocket 연결 완료.", chatChannelId);

        try {
            String authPacket = createAuthPacket(chatChannelId, accessToken);
            webSocket.sendText(authPacket, true);

            String sendPacket = createSendPacket(chatChannelId);
            webSocket.sendText(sendPacket, true);
        } catch (Exception e) {
            log.error("[{}] 웹소켓 초기 패킷 전송 실패", chatChannelId, e);
            this.messageListener.onError(e);
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "초기 패킷 전송 실패");
            return;
        }

        Thread.ofVirtual().name("ping-thread-" + chatChannelId).start(this::runPingLoop);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBuffer.append(data);
        if (!last) {
            return Listener.super.onText(webSocket, data, last);
        }
        String message = textBuffer.toString();
        textBuffer.setLength(0);

        try {
            JsonNode rootNode = jsonMapper.readTree(message);
            int cmd = rootNode.path("cmd").asInt();

            switch (CmdType.fromInt(cmd)) {
                case CONNECT_ACK -> log.info("[{}] 웹소켓 연결 완료 ack 수신", chatChannelId);
                case PING -> webSocket.sendText(createPongPacket(), true);
                case PONG -> log.info("[{}] 서버로부터 pong 수신", chatChannelId);
                case CHAT, DONATION -> {
                    List<ChatMessage> messages = this.messageConverter.convert(rootNode);
                    if (!messages.isEmpty()) {
                        this.messageListener.onMessages(messages);
                    }
                }
                default -> log.warn("[{}] 알 수 없는 명령어 cmd 수신: {}", chatChannelId, cmd);
            }
        } catch (Exception e) {
            textBuffer.setLength(0);
            log.error("[{}] 메시지 파싱 중 오류가 발생했습니다.", chatChannelId, e);
        }

        return Listener.super.onText(webSocket, data, last);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.info("[{}] 웹소켓 연결 종료. 코드: {}, 이유: {}", chatChannelId, statusCode, reason);
        shutdownScheduler();
        this.messageListener.onDisconnected();
        return Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("[{}] 웹소켓 에러 발생", chatChannelId, error);
        shutdownScheduler();
        this.messageListener.onError(error);
        Listener.super.onError(webSocket, error);
    }

    public void runPingLoop() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(20_000);
                sendPing();
            } catch (InterruptedException e) {
                log.info("[{}] Ping 스레드 종료", chatChannelId);
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void shutdownScheduler() {
        this.isRunning = false;
    }

    private void sendPing() {
        try {
            String pingPacket = "{\"cmd\": 0, \"ver\": 2}";
            this.webSocket.sendText(pingPacket, true);
        } catch (Exception e) {
            log.error("[{}] ping 전송 실패", chatChannelId, e);
        }
    }

    private String createAuthPacket(String chatChannelId, String accessToken) {
        var body = new ChzzkAuthRequest.AuthRequestBody(null, 2, accessToken, "READ");
        var request = new ChzzkAuthRequest("2", 100, "game", chatChannelId, 1, body);
        return this.jsonMapper.writeValueAsString(request);
    }

    private String createSendPacket(String chatChannelId) {
        Map<String, Object> body = Map.of("recentMessageCount", 50);
        Map<String, Object> packet = Map.of(
            "cmd", 100,
            "ver", 2,
            "svcid", "game",
            "cid", chatChannelId,
            "bdy", body
        );
        return this.jsonMapper.writeValueAsString(packet);
    }

    private String createPongPacket() {
        return "{\"cmd\": 10000, \"ver\": 2}";
    }
}
