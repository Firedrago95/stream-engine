package io.slice.stream.collector.infrastructure.file;

import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.core.model.Author;
import io.slice.stream.core.model.ChatMessage;
import io.slice.stream.core.model.StreamTarget;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class JsonlFileChatCollector implements ChatMessageListener {

    private static final long MAX_FILE_SIZE_BYTES = 30L * 1024L * 1024L;

    private final String sessionId;
    private final String channelId;
    private final String streamerName;
    private final File tmpFile;
    private final File finalFile;
    private final JsonMapper jsonMapper;
    private final AtomicLong messageCount = new AtomicLong(0);

    private BufferedWriter writer;
    private boolean closed = false;

    public JsonlFileChatCollector(StreamTarget streamTarget, String storageDir, JsonMapper jsonMapper) {
        this.channelId = streamTarget.channelId();
        this.sessionId = resolveSessionId(streamTarget);
        this.streamerName = resolveStreamerName(streamTarget);
        this.jsonMapper = jsonMapper;

        File dir = new File(storageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.tmpFile = new File(dir, sessionId + ".jsonl.gz.tmp");
        this.finalFile = new File(dir, sessionId + ".jsonl.gz");

        initializeWriter(streamTarget.startedAt());
    }

    private String resolveSessionId(StreamTarget target) {
        if (target.liveId() > 0) {
            return String.valueOf(target.liveId());
        }
        return target.channelId();
    }

    private String resolveStreamerName(StreamTarget target) {
        if (target.channelName() != null && !target.channelName().isBlank()) {
            return target.channelName();
        }
        return target.channelId();
    }

    private synchronized void initializeWriter(Instant startedAt) {
        try {
            if (finalFile.exists()) {
                Files.move(finalFile.toPath(), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            boolean isNewFile = !tmpFile.exists() || tmpFile.length() == 0;
            FileOutputStream fos = new FileOutputStream(tmpFile, true);
            GZIPOutputStream gzos = new GZIPOutputStream(fos, true);
            this.writer = new BufferedWriter(new OutputStreamWriter(gzos, StandardCharsets.UTF_8));

            if (isNewFile) {
                writeMetadataHeader(startedAt);
            }
            log.info("[채팅 파일 수집 시작] 세션 ID: {}, 채널 ID: {}, 스트리머: {}, 임시파일: {}",
                sessionId, channelId, streamerName, tmpFile.getName());
        } catch (IOException e) {
            log.error("[채팅 파일 초기화 실패] 세션 ID: {}, 채널 ID: {}, 사유: {}", sessionId, channelId, e.getMessage(), e);
        }
    }

    private void writeMetadataHeader(Instant startedAt) throws IOException {
        Instant now = Instant.now();
        Instant openDate = startedAt != null ? startedAt : now;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("type", "METADATA");
        header.put("sessionId", sessionId);
        header.put("channelId", channelId);
        header.put("streamerName", streamerName);
        header.put("openDate", openDate.toString());
        header.put("openDateEpoch", openDate.getEpochSecond());
        header.put("samplingStartedAt", now.toString());
        header.put("samplingStartedAtEpoch", now.getEpochSecond());

        writer.write(jsonMapper.writeValueAsString(header));
        writer.newLine();
        writer.flush();
    }

    @Override
    public synchronized void onMessages(List<ChatMessage> messages) {
        if (closed || writer == null || messages == null || messages.isEmpty()) {
            return;
        }
        if (tmpFile.exists() && tmpFile.length() >= MAX_FILE_SIZE_BYTES) {
            return;
        }

        try {
            for (ChatMessage msg : messages) {
                if (msg == null) {
                    continue;
                }
                Author author = msg.author();
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("timestamp", msg.time() != null ? msg.time().toString() : Instant.now().toString());
                record.put("authorId", author != null && author.id() != null ? author.id() : "");
                record.put("content", msg.message() != null ? msg.message() : "");
                record.put("isSubscriber", author != null && author.isSubscriber());

                writer.write(jsonMapper.writeValueAsString(record));
                writer.newLine();
                messageCount.incrementAndGet();
            }
            writer.flush();
        } catch (IOException e) {
            log.error("[채팅 파일 기록 실패] 세션 ID: {}, 채널 ID: {}, 사유: {}", sessionId, channelId, e.getMessage(), e);
        }
    }

    @Override
    public void onConnected() {
        log.info("[수집기 연결 성공] 세션 ID: {}, 채널 ID: {}", sessionId, channelId);
    }

    @Override
    public void onDisconnected() {
        log.info("[수집기 일시 연결 종료] 세션 ID: {}, 채널 ID: {}", sessionId, channelId);
    }

    @Override
    public void onError(Throwable error) {
        log.error("[수집기 오류 발생] 세션 ID: {}, 채널 ID: {}", sessionId, channelId, error);
    }

    @Override
    public synchronized void onClosed() {
        if (closed) {
            return;
        }
        closed = true;

        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
            if (tmpFile.exists()) {
                moveAtomically(tmpFile, finalFile);
                log.info("[채팅 파일 마감 완료] 세션 ID: {}, 채널 ID: {}, 파일: {}, 총 채팅: {}건",
                    sessionId, channelId, finalFile.getName(), messageCount.get());
            }
        } catch (IOException e) {
            log.error("[채팅 파일 마감 실패] 세션 ID: {}, 채널 ID: {}, 사유: {}", sessionId, channelId, e.getMessage(), e);
        }
    }

    private void moveAtomically(File source, File target) throws IOException {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
