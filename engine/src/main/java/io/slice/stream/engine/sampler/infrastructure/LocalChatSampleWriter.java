package io.slice.stream.engine.sampler.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slice.stream.engine.sampler.domain.ChatSampleHeader;
import io.slice.stream.engine.sampler.domain.ChatSampleMessage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalChatSampleWriter {

    private static final Logger log = LoggerFactory.getLogger(LocalChatSampleWriter.class);

    private final File targetFile;
    private final ObjectMapper objectMapper;
    private final BufferedWriter writer;
    private final AtomicLong messageCount;
    private volatile boolean finished;

    public LocalChatSampleWriter(File targetFile, ObjectMapper objectMapper) throws IOException {
        this.targetFile = targetFile;
        this.objectMapper = objectMapper;
        this.messageCount = new AtomicLong(0);
        this.finished = false;

        ensureParentDirectory(targetFile);
        FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(fileOutputStream);
        this.writer = new BufferedWriter(new OutputStreamWriter(gzipOutputStream, StandardCharsets.UTF_8));
    }

    private void ensureParentDirectory(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    public synchronized void writeHeader(ChatSampleHeader header) throws IOException {
        if (finished || header == null) {
            return;
        }
        String jsonLine = objectMapper.writeValueAsString(header);
        writer.write(jsonLine);
        writer.newLine();
    }

    public synchronized void write(ChatSampleMessage message) throws IOException {
        if (finished || message == null) {
            return;
        }
        String jsonLine = objectMapper.writeValueAsString(message);
        writer.write(jsonLine);
        writer.newLine();
        messageCount.incrementAndGet();
    }

    public synchronized File finish() throws IOException {
        if (!finished) {
            finished = true;
            writer.flush();
            writer.close();
            log.info("채팅 샘플 압축 파일 마감 완료: {} (총 {}건, {} bytes)",
                    targetFile.getName(), messageCount.get(), targetFile.length());
        }
        return targetFile;
    }

    public long getMessageCount() {
        return messageCount.get();
    }

    public long getCurrentFileSize() {
        return targetFile.exists() ? targetFile.length() : 0;
    }

    public File getTargetFile() {
        return targetFile;
    }
}
