package io.slice.stream.collector.infrastructure.chzzk.websocket;

import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.collector.infrastructure.chzzk.ChzzkMessageConverter;
import io.slice.stream.collector.infrastructure.chzzk.dto.request.ChzzkAuthRequest;
import io.slice.stream.core.model.ChatMessage;
import java.net.SocketTimeoutException;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class ChzzkWebSocketListener implements Listener {

    private static final long PONG_TIMEOUT_MS = 60_000L;

    private final ChatMessageListener messageListener;
    private final String channelId;
    private final String chatChannelId;
    private final String accessToken;
    private final JsonMapper jsonMapper;
    private final ChzzkMessageConverter messageConverter;
    private final StringBuilder textBuffer = new StringBuilder();
    private final ScheduledExecutorService pingScheduler;

    private WebSocket webSocket;
    private ScheduledFuture<?> pingTask;
    private volatile long lastPongReceivedAt;

    public ChzzkWebSocketListener(
        ChatMessageListener messageListener,
        String channelId,
        String chatChannelId,
        String accessToken,
        JsonMapper jsonMapper,
        ChzzkMessageConverter messageConverter,
        ScheduledExecutorService pingScheduler
    ) {
        this.messageListener = messageListener;
        this.channelId = channelId;
        this.chatChannelId = chatChannelId;
        this.accessToken = accessToken;
        this.jsonMapper = jsonMapper;
        this.messageConverter = messageConverter;
        this.pingScheduler = pingScheduler;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        Listener.super.onOpen(webSocket);
        this.webSocket = webSocket;
        this.lastPongReceivedAt = System.currentTimeMillis();
        this.messageListener.onConnected();

        log.info("[{}] 웹소켓 연결 성공. 서버 신호 대기 중...", chatChannelId);

        requestAuthentication();
        schedulePing();
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBuffer.append(data);

        if (last) {
            String fullMessage = textBuffer.toString();
            textBuffer.setLength(0);
            processMessage(fullMessage);
        }

        return Listener.super.onText(webSocket, data, last);
    }

    private void processMessage(String message) {
        try {
            JsonNode rootNode = jsonMapper.readTree(message);
            int cmd = rootNode.path("cmd").asInt();
            CmdType cmdType = CmdType.fromInt(cmd);

            if (cmdType == CmdType.UNKNOWN) {
                String sanitized = message.replaceAll("[\\r\\n]", " ");
                String preview = sanitized.length() > 200 ? sanitized.substring(0, 200) + "..." : sanitized;
                log.warn("[{}] 정의되지 않은 명령어 수신: {}, 메시지: {}", chatChannelId, cmd, preview);
            }

            dispatchCommand(cmdType, rootNode);
        } catch (Exception e) {
            log.error("[{}] 웹소켓 메시지 파싱 중 오류 발생: {}", chatChannelId, e.getMessage());
        }
    }

    private void dispatchCommand(CmdType type, JsonNode rootNode) {
        switch (type) {
            case CONNECTED -> log.info("[{}] 서버 CONNECTED 수신. 인증 패킷 전송 시도", chatChannelId);
            case CHAT, DONATION -> handleChatMessage(rootNode);
            case PING -> handlePing();
            case CONNECT_ACK -> log.info("[{}] 웹소켓 인증 완료 확인", chatChannelId);
            case PONG -> {
                this.lastPongReceivedAt = System.currentTimeMillis();
                log.debug("[{}] 서버로부터 pong 응답 수신", chatChannelId);
            }
            case BLIND, PARTICIPATION -> {}
            default -> log.debug("[{}] 기타 명령어 수신: {}", chatChannelId, type);
        }
    }

    private void requestAuthentication() {
        try {
            String authPacket = createAuthPacket(chatChannelId, accessToken);
            webSocket.sendText(authPacket, true);
        } catch (Exception e) {
            log.error("[{}] 인증 패킷 전송 실패", chatChannelId, e);
        }
    }

    private void handleChatMessage(JsonNode rootNode) {
        List<ChatMessage> messages = messageConverter.convert(rootNode, channelId);
        if (!messages.isEmpty()) {
            messageListener.onMessages(messages);
        }
    }

    private void handlePing() {
        webSocket.sendText(createPongPacket(), true);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.info("[{}] 웹소켓 연결 종료. 코드: {}, 이유: {}", chatChannelId, statusCode, reason);
        cancelPing();
        this.messageListener.onDisconnected();
        return Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("[{}] 웹소켓 에러 발생: {}", chatChannelId, error.getMessage());
        cancelPing();
        this.messageListener.onError(error);
        Listener.super.onError(webSocket, error);
    }

    private void schedulePing() {
        this.pingTask = pingScheduler.scheduleAtFixedRate(
            this::sendPing,
            20,
            20,
            TimeUnit.SECONDS
        );
    }

    private void cancelPing() {
        if (pingTask != null) {
            pingTask.cancel(true);
        }
    }

    void sendPing() {
        if (webSocket == null) {
            return;
        }

        long elapsedSinceLastPong = System.currentTimeMillis() - lastPongReceivedAt;
        if (elapsedSinceLastPong > PONG_TIMEOUT_MS) {
            log.warn("[{}] 치지직 하트비트 PONG 타임아웃 감지 ({}ms간 무응답 좀비 소켓). 강제 종료 후 재연결을 시도합니다.",
                chatChannelId, elapsedSinceLastPong);
            cancelPing();
            webSocket.abort();
            messageListener.onError(new SocketTimeoutException("치지직 하트비트 PONG 응답 없음: " + elapsedSinceLastPong + "ms"));
            return;
        }

        try {
            String pingPacket = "{\"cmd\": 0, \"ver\": 2}";
            this.webSocket.sendText(pingPacket, true);
        } catch (Exception e) {
            log.error("[{}] ping 전송 실패: {}", chatChannelId, e.getMessage());
        }
    }

    void setLastPongReceivedAt(long time) {
        this.lastPongReceivedAt = time;
    }

    long getLastPongReceivedAt() {
        return this.lastPongReceivedAt;
    }

    private String createAuthPacket(String chatChannelId, String accessToken) {
        ChzzkAuthRequest.AuthRequestBody body = new ChzzkAuthRequest.AuthRequestBody(null, 2, accessToken, "READ");
        ChzzkAuthRequest request = new ChzzkAuthRequest("3", 100, "game", chatChannelId, 1, body);
        return this.jsonMapper.writeValueAsString(request);
    }

    private String createPongPacket() {
        return "{\"cmd\": 10000, \"ver\": 2}";
    }
}
