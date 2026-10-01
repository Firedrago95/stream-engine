package io.slice.stream.engine.sampler.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.sampler.application.dto.SamplingStatusResponse;
import io.slice.stream.engine.sampler.application.dto.StartSamplingRequest;
import io.slice.stream.engine.sampler.domain.ChatSampleMessage;
import io.slice.stream.engine.sampler.domain.ChatSampleUploader;
import io.slice.stream.engine.sampler.domain.UploadResult;
import io.slice.stream.engine.sampler.infrastructure.LocalChatSampleWriter;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatSamplerService {

    private static final Logger log = LoggerFactory.getLogger(ChatSamplerService.class);
    private static final int QUEUE_CAPACITY = 10_000;
    private static final long MAX_FILE_SIZE_BYTES = 30 * 1024 * 1024; // 30MB
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final ChatSampleUploader uploader;
    private final ObjectMapper objectMapper;
    private final String tempDir;
    private final StreamRepository streamRepository;
    private final Map<String, SamplerSession> activeSessions;
    private final BlockingQueue<ChatSampleMessage> messageQueue;
    private final AtomicLong droppedMessages;
    private final AtomicBoolean running;
    private final Thread workerThread;

    @Autowired
    public ChatSamplerService(
            ChatSampleUploader uploader,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) StreamRepository streamRepository,
            @Value("${chat.sampler.temp-dir:data/chat-samples}") String tempDir
    ) {
        this.uploader = uploader;
        this.objectMapper = (objectMapper != null) ? objectMapper : createDefaultObjectMapper();
        this.streamRepository = streamRepository;
        this.tempDir = tempDir;
        this.activeSessions = new ConcurrentHashMap<>();
        this.messageQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.droppedMessages = new AtomicLong(0);
        this.running = new AtomicBoolean(true);

        this.workerThread = Thread.ofVirtual()
                .name("chat-sampler-worker")
                .start(this::consumeQueue);
    }

    public boolean isSampling(String channelId) {
        return channelId != null && activeSessions.containsKey(channelId);
    }

    public void record(ChatSampleMessage message) {
        if (activeSessions.isEmpty() || message == null || !activeSessions.containsKey(message.channelId())) {
            return;
        }

        boolean accepted = messageQueue.offer(message);
        if (!accepted) {
            droppedMessages.incrementAndGet();
        }
    }

    public synchronized void startSampling(StartSamplingRequest request) {
        if (request == null || request.channelIds() == null || request.channelIds().isEmpty()) {
            log.warn("샘플링 시작 요청에 대상 채널 ID가 없습니다.");
            return;
        }

        Instant now = Instant.now();
        Instant expiresAt = request.durationMinutes() != null && request.durationMinutes() > 0
                ? now.plus(Duration.ofMinutes(request.durationMinutes()))
                : null;

        for (String channelId : request.channelIds()) {
            if (activeSessions.containsKey(channelId)) {
                log.info("이미 샘플링 중인 채널입니다: {}", channelId);
                continue;
            }
            String streamerName = resolveStreamerName(channelId, request.streamerNames());
            createSession(channelId, streamerName, request.tag(), now, expiresAt);
        }
    }

    private String resolveStreamerName(String channelId, Map<String, String> customNames) {
        if (customNames != null && customNames.containsKey(channelId) && !customNames.get(channelId).isBlank()) {
            return customNames.get(channelId);
        }
        if (streamRepository != null) {
            try {
                List<StreamTarget> targets = streamRepository.getStreamTargets(List.of(channelId));
                if (targets != null && !targets.isEmpty() && targets.get(0).channelName() != null && !targets.get(0).channelName().isBlank()) {
                    return targets.get(0).channelName();
                }
            } catch (Exception e) {
                log.warn("스트리머 이름 조회 실패 (채널: {}): {}", channelId, e.getMessage());
            }
        }
        return channelId;
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    private void createSession(String channelId, String streamerName, String tag, Instant startedAt, Instant expiresAt) {
        String safeTag = (tag != null && !tag.isBlank()) ? tag : "sample";
        String safeStreamerName = sanitizeFileName(streamerName);
        String fileName = String.format("%s_%s_%s.jsonl.gz",
                safeTag, safeStreamerName, FILE_DATE_FORMAT.format(startedAt));
        File targetFile = new File(tempDir, fileName);

        try {
            LocalChatSampleWriter writer = new LocalChatSampleWriter(targetFile, objectMapper);
            SamplerSession session = new SamplerSession(channelId, streamerName, safeTag, startedAt, expiresAt, writer, fileName);
            activeSessions.put(channelId, session);
            log.info("채팅 샘플링 세션 시작: 채널={}, 스트리머={}, 파일={}, 만료={}",
                    channelId, streamerName, fileName, expiresAt);
        } catch (IOException e) {
            log.error("채팅 샘플링 세션 생성 실패 (채널: {}): {}", channelId, e.getMessage(), e);
        }
    }

    public synchronized void finishSession(String channelId, String reason) {
        SamplerSession session = activeSessions.remove(channelId);
        if (session == null) {
            return;
        }

        log.info("채팅 샘플링 세션 종료 (채널: {}, 사유: {})", channelId, reason);
        closeAndUploadAsync(session);
    }

    public synchronized void stopAll(String reason) {
        List<String> channelIds = new ArrayList<>(activeSessions.keySet());
        for (String channelId : channelIds) {
            finishSession(channelId, reason);
        }
    }

    private void closeAndUploadAsync(SamplerSession session) {
        CompletableFuture.runAsync(() -> {
            try {
                File file = session.writer().finish();
                UploadResult result = uploader.upload(file, session.remoteFileName());
                if (result.success()) {
                    log.info("채팅 샘플 업로드 완료: 채널={}, 링크={}", session.channelId(), result.webViewLink());
                } else {
                    log.error("채팅 샘플 업로드 실패: 채널={}, 에러={}", session.channelId(), result.errorMessage());
                }
            } catch (Exception e) {
                log.error("채팅 샘플 마감 및 업로드 처리 중 오류 발생 (채널: {}): {}", session.channelId(), e.getMessage(), e);
            }
        });
    }

    private void consumeQueue() {
        while (running.get()) {
            try {
                ChatSampleMessage message = messageQueue.poll(200, TimeUnit.MILLISECONDS);
                if (message != null) {
                    processMessage(message);
                }
                checkExpiredSessions();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("샘플러 큐 처리 중 오류 발생: {}", e.getMessage(), e);
            }
        }
    }

    private void processMessage(ChatSampleMessage message) {
        SamplerSession session = activeSessions.get(message.channelId());
        if (session == null) {
            return;
        }

        try {
            session.writer().write(message);
            if (session.writer().getCurrentFileSize() >= MAX_FILE_SIZE_BYTES) {
                log.warn("채팅 샘플 파일 크기 상한 도달(30MB)로 세션을 조기 마감합니다 (채널: {})", message.channelId());
                finishSession(message.channelId(), "FILE_SIZE_LIMIT_REACHED");
            }
        } catch (IOException e) {
            log.error("채팅 메시지 파일 쓰기 실패 (채널: {}): {}", message.channelId(), e.getMessage(), e);
        }
    }

    private void checkExpiredSessions() {
        if (activeSessions.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (Map.Entry<String, SamplerSession> entry : activeSessions.entrySet()) {
            SamplerSession session = entry.getValue();
            if (session.expiresAt() != null && now.isAfter(session.expiresAt())) {
                finishSession(entry.getKey(), "DURATION_EXPIRED");
            }
        }
    }

    public SamplingStatusResponse getStatus() {
        List<SamplingStatusResponse.SessionDetail> sessionDetails = activeSessions.values().stream()
                .map(s -> new SamplingStatusResponse.SessionDetail(
                        s.channelId(),
                        s.streamerName(),
                        s.tag(),
                        s.startedAt(),
                        s.expiresAt(),
                        s.writer().getMessageCount(),
                        s.writer().getCurrentFileSize()
                ))
                .toList();

        return new SamplingStatusResponse(
                !activeSessions.isEmpty(),
                activeSessions.size(),
                droppedMessages.get(),
                sessionDetails
        );
    }

    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private record SamplerSession(
            String channelId,
            String streamerName,
            String tag,
            Instant startedAt,
            Instant expiresAt,
            LocalChatSampleWriter writer,
            String remoteFileName
    ) {
    }
}
