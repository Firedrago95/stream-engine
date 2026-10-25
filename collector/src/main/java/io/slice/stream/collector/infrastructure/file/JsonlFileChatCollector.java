package io.slice.stream.collector.infrastructure.file;

import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.core.model.Author;
import io.slice.stream.core.model.ChatMessage;
import io.slice.stream.core.model.StreamTarget;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class JsonlFileChatCollector implements ChatMessageListener {

    private static final long MAX_FILE_SIZE_BYTES = 30L * 1024L * 1024L;

    private final String sessionId;
    private final String channelId;
    private final String streamerName;
    private final Instant sessionStartedAt;
    private final File storageDirFile;
    private final File tmpFile;
    private final File finalFile;
    private final JsonMapper jsonMapper;
    private final AtomicLong messageCount = new AtomicLong(0);
    private final AtomicInteger partIndex = new AtomicInteger(1);

    private BufferedWriter writer;
    private boolean closed = false;

    public JsonlFileChatCollector(StreamTarget streamTarget, String storageDir, JsonMapper jsonMapper) {
        this.channelId = streamTarget.channelId();
        this.sessionId = resolveSessionId(streamTarget);
        this.streamerName = resolveStreamerName(streamTarget);
        this.sessionStartedAt = streamTarget.startedAt();
        this.jsonMapper = jsonMapper;

        this.storageDirFile = new File(storageDir);
        if (!this.storageDirFile.exists() && !this.storageDirFile.mkdirs() && !this.storageDirFile.exists()) {
            throw new UncheckedIOException(new IOException("채팅 저장 디렉터리 생성 실패: " + storageDir));
        }
        this.tmpFile = new File(this.storageDirFile, sessionId + ".jsonl.gz.tmp");
        this.finalFile = new File(this.storageDirFile, sessionId + ".jsonl.gz");

        initializeExistingPartIndex();
        initializeWriter(this.sessionStartedAt);
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

    private void initializeExistingPartIndex() {
        int nextIdx = 1;
        while (new File(storageDirFile, String.format("%s_part%03d.jsonl.gz", sessionId, nextIdx)).exists()) {
            nextIdx++;
        }
        partIndex.set(nextIdx);
    }

    private synchronized void initializeWriter(Instant startedAt) {
        FileOutputStream fos = null;
        GZIPOutputStream gzos = null;
        try {
            if (finalFile.exists() && !tmpFile.exists()) {
                moveAtomically(finalFile, tmpFile);
            } else if (tmpFile.exists() && tmpFile.length() > 0) {
                recoverTmpFileIfNeeded();
            }

            boolean isNewFile = !tmpFile.exists() || tmpFile.length() == 0;
            fos = new FileOutputStream(tmpFile, true);
            gzos = new GZIPOutputStream(fos, true);
            this.writer = new BufferedWriter(new OutputStreamWriter(gzos, StandardCharsets.UTF_8));

            if (isNewFile) {
                writeMetadataHeader(startedAt);
            }
            log.info("[채팅 파일 수집 시작] 세션 ID: {}, 채널 ID: {}, 스트리머: {}, 임시파일: {}",
                sessionId, channelId, streamerName, tmpFile.getName());
        } catch (IOException e) {
            closeQuietly(this.writer);
            closeQuietly(gzos);
            closeQuietly(fos);
            this.writer = null;
            log.error("[채팅 파일 초기화 실패] 세션 ID: {}, 채널 ID: {}, 사유: {}", sessionId, channelId, e.getMessage(), e);
            throw new UncheckedIOException("채팅 파일 초기화 실패: " + tmpFile.getName(), e);
        }
    }

    private void recoverTmpFileIfNeeded() throws IOException {
        File recoveredFile = new File(storageDirFile, sessionId + ".jsonl.gz.recovered");
        long validLines = 0;

        try (
            FileOutputStream fos = new FileOutputStream(recoveredFile, false);
            GZIPOutputStream gzos = new GZIPOutputStream(fos, true);
            BufferedWriter recoveryWriter = new BufferedWriter(new OutputStreamWriter(gzos, StandardCharsets.UTF_8))
        ) {
            try (
                FileInputStream fis = new FileInputStream(tmpFile);
                GZIPInputStream gzis = new GZIPInputStream(fis);
                BufferedReader reader = new BufferedReader(new InputStreamReader(gzis, StandardCharsets.UTF_8))
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    recoveryWriter.write(line);
                    recoveryWriter.newLine();
                    validLines++;
                }
            } catch (IOException truncatedEx) {
                log.warn("[임시 채팅 파일 복구] 비정상 종료된 GZIP 스트림 감지, 정상 라인({}줄)까지만 복구합니다: 파일={}",
                    validLines, tmpFile.getName());
            }
            recoveryWriter.flush();
        }

        if (validLines > 0) {
            moveAtomically(recoveredFile, tmpFile);
        } else {
            Files.deleteIfExists(recoveredFile.toPath());
            Files.deleteIfExists(tmpFile.toPath());
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

        try {
            if (tmpFile.exists() && tmpFile.length() >= MAX_FILE_SIZE_BYTES) {
                rolloverPartFile();
            }

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

            if (tmpFile.exists() && tmpFile.length() >= MAX_FILE_SIZE_BYTES) {
                rolloverPartFile();
            }
        } catch (IOException e) {
            log.error("[채팅 파일 기록 실패] 세션 ID: {}, 채널 ID: {}, 사유: {}", sessionId, channelId, e.getMessage(), e);
        }
    }

    private void rolloverPartFile() throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
        int currentPart = partIndex.getAndIncrement();
        File partFile = new File(storageDirFile, String.format("%s_part%03d.jsonl.gz", sessionId, currentPart));
        if (tmpFile.exists()) {
            moveAtomically(tmpFile, partFile);
            log.info("[채팅 파일 청크 롤오버] 30MB 상한 도달로 파트 파일 마감 후 기록 지속: 세션 ID={}, 파트파일={}",
                sessionId, partFile.getName());
        }

        FileOutputStream fos = null;
        GZIPOutputStream gzos = null;
        try {
            fos = new FileOutputStream(tmpFile, false);
            gzos = new GZIPOutputStream(fos, true);
            this.writer = new BufferedWriter(new OutputStreamWriter(gzos, StandardCharsets.UTF_8));
            writeMetadataHeader(sessionStartedAt);
        } catch (IOException e) {
            closeQuietly(this.writer);
            closeQuietly(gzos);
            closeQuietly(fos);
            this.writer = null;
            throw e;
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
                writer = null;
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

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
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
