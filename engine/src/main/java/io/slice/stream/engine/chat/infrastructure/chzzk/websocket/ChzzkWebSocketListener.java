package io.slice.stream.engine.chat.infrastructure.chzzk.websocket;

import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.infrastructure.chzzk.ChzzkMessageConverter;
import io.slice.stream.engine.chat.infrastructure.chzzk.dto.request.ChzzkAuthRequest;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class ChzzkWebSocketListener implements Listener {

    private final ChatMessageListener messageListener;
    private final String channelId;
    private final String chatChannelId;
    private final String accessToken;
    private final JsonMapper jsonMapper;
    private final ChzzkMessageConverter messageConverter;
    private final StringBuilder textBuffer = new StringBuilder();

    private WebSocket webSocket;
    private volatile boolean isRunning = true;

    public ChzzkWebSocketListener(
        ChatMessageListener messageListener,
        String channelId,
        String chatChannelId,
        String accessToken,
        JsonMapper jsonMapper,
        ChzzkMessageConverter messageConverter
    ) {
        this.messageListener = messageListener;
        this.channelId = channelId;
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

        log.info("[{}] Websocket 연결 성공. 서버의 CONNECTED(10100) 신호 대기 중...", chatChannelId);

        requestAuthentication();
        Thread.ofVirtual().name("ping-thread-" + chatChannelId).start(this::runPingLoop);
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
            CmdType cmdType = CmdType.fromInt(rootNode.path("cmd").asInt());

            if (cmdType == CmdType.UNKNOWN) {
                String sanitized = message.replaceAll("[\\r\\n]", " ");
                String preview = sanitized.length() > 200 ? sanitized.substring(0, 200) + "..." : sanitized;
                log.warn("[{}] 정의되지 않은 cmd 값 수신: {}, raw message: {}", chatChannelId, cmd, preview);
            }

            if (cmdType != CmdType.CHAT && cmdType != CmdType.PING && cmdType != CmdType.PONG) {
                log.debug("[{}] 시스템 메시지 수신: cmd={}", chatChannelId, cmdType);
            }

            dispatchCommand(cmdType, rootNode);
        } catch (Exception e) {
            log.error("[{}] 메시지 처리 중 오류 발생: {}", chatChannelId, e.getMessage());
        }
    }

    private void dispatchCommand(CmdType type, JsonNode rootNode) {
        switch (type) {
            case CONNECTED      -> log.info("[{}] 서버로부터 CONNECTED(10100) 수신. 인증 패킷 전송 시도...", chatChannelId);
            case CHAT, DONATION -> handleChatMessage(rootNode);
            case PING           -> handlePing();
            case CONNECT_ACK    -> log.info("[{}] 웹소켓 연결 완료 ack 수신", chatChannelId);
            case PONG           -> log.debug("[{}] 서버로부터 pong 수신", chatChannelId);
            default             -> log.warn("[{}] 처리되지 않은 명령어 수신 (cmd: {})", chatChannelId, type);
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
            if (log.isDebugEnabled()) {
                for (ChatMessage msg : messages) {
                    log.debug("[{}] {}: {}",
                        chatChannelId,
                        msg.author().nickname().replaceAll("[\r\n]", " "),
                        msg.message());
                }
            }
            messageListener.onMessages(messages);
        }
    }

    private void handlePing() {
        webSocket.sendText(createPongPacket(), true);
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
        var request = new ChzzkAuthRequest("3", 100, "game", chatChannelId, 1, body);
        return this.jsonMapper.writeValueAsString(request);
    }

    private String createPongPacket() {
        return "{\"cmd\": 10000, \"ver\": 2}";
    }
}
