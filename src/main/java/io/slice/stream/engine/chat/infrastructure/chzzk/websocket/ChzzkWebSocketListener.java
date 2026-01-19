package io.slice.stream.engine.chat.infrastructure.chzzk.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.infrastructure.chzzk.dto.request.ChzzkAuthRequest;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChzzkWebSocketListener implements Listener {

    private final ChatMessageListener messageListener;
    private final String chatChannelId;
    private final String accessToken;
    private final ObjectMapper objectMapper;

    private WebSocket webSocket;
    private ScheduledExecutorService pingScheduler;

    public ChzzkWebSocketListener(
        ChatMessageListener messageListener,
        String chatChannelId,
        String accessToken
    ) {
        this.messageListener = messageListener;
        this.chatChannelId = chatChannelId;
        this.accessToken = accessToken;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        Listener.super.onOpen(webSocket);
        this.webSocket = webSocket;
        this.messageListener.onConnected();
        log.info("[{}] Websocket 연결 완료.", chatChannelId);

        String authPacket = createAuthPacket(chatChannelId, accessToken);
        webSocket.sendText(authPacket, true);

        String sendPacket = createSendPacket(chatChannelId);
        webSocket.sendText(sendPacket, true);

        this.pingScheduler = Executors.newSingleThreadScheduledExecutor();
        pingScheduler.scheduleAtFixedRate(this::sendPing, 20, 20, TimeUnit.SECONDS);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String message = data.toString();
        JsonNode rootNode = objectMapper.readTree(message);
        int cmd = rootNode.path("cmd").asInt();

        switch (CmdType.fromInt(cmd)) {
            case CONNECT_ACK -> log.info("[{}] 웹소켓 연결 완료 ack 수신", chatChannelId);
            case PING -> webSocket.sendText(createPongPacket(), true);
            case PONG -> log.info("[{}] 서버로부터 pong 수신", chatChannelId);
            case CHAT, DONATION -> this.messageListener.onRawMessage(message);
            default -> log.warn("[{}] 알 수 없는 명령어 cmd 수신: {}", chatChannelId, cmd);
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

    private void shutdownScheduler() {
        if (pingScheduler != null && !pingScheduler.isShutdown()) {
            pingScheduler.shutdown();
        }
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
        return this.objectMapper.writeValueAsString(request);
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
        return this.objectMapper.writeValueAsString(packet);
    }

    private String createPongPacket() {
        return "{\"cmd\": 10000, \"ver\": 2}";
    }
}
