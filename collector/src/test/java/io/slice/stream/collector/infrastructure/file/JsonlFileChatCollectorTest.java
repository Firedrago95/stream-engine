package io.slice.stream.collector.infrastructure.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.slice.stream.core.model.Author;
import io.slice.stream.core.model.ChatMessage;
import io.slice.stream.core.model.MessageType;
import io.slice.stream.core.model.StreamTarget;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class JsonlFileChatCollectorTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("절단된 .tmp GZIP 파일이 존재할 때 정상 라인만 복구한 뒤 새 메시지를 이어 써서 온전한 .jsonl.gz 파일을 생성한다")
    void recoverTruncatedTmpFileAndAppendNewMessages(@TempDir Path tempDir) throws IOException {
        StreamTarget target = createTarget(99001L);
        File tmpFile = tempDir.resolve("99001.jsonl.gz.tmp").toFile();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (
            GZIPOutputStream gzos = new GZIPOutputStream(baos, true);
            OutputStreamWriter writer = new OutputStreamWriter(gzos, StandardCharsets.UTF_8)
        ) {
            writer.write("{\"type\":\"METADATA\",\"sessionId\":\"99001\"}\n");
            writer.write("{\"content\":\"복구전 첫 메시지\"}\n");
            writer.flush();
        }
        byte[] fullBytes = baos.toByteArray();
        byte[] truncatedBytes = Arrays.copyOf(fullBytes, fullBytes.length - 4);
        Files.write(tmpFile.toPath(), truncatedBytes);

        JsonlFileChatCollector collector = new JsonlFileChatCollector(target, tempDir.toString(), jsonMapper);
        collector.onMessages(List.of(
            new ChatMessage(
                MessageType.TEXT,
                new Author("user-1", "테스터", null, true),
                "복구후 새 메시지",
                Instant.parse("2026-10-25T10:00:00Z"),
                "ch-1",
                System.currentTimeMillis(),
                Map.of()
            )
        ));
        collector.onClosed();

        File finalFile = tempDir.resolve("99001.jsonl.gz").toFile();
        assertThat(finalFile).exists();
        assertThat(tmpFile).doesNotExist();

        List<String> lines = readAllGzipLines(finalFile);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).contains("복구전 첫 메시지");
        assertThat(lines.get(2)).contains("복구후 새 메시지");
    }

    @Test
    @DisplayName("복구 출력 중 IOException이 발생하면 예외를 호출자에게 전파하고 원본 .tmp 파일을 보존한다")
    void preserveOriginalTmpFileWhenRecoveryOutputFails(@TempDir Path tempDir) throws IOException {
        StreamTarget target = createTarget(99002L);
        Path tmpPath = tempDir.resolve("99002.jsonl.gz.tmp");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (
            GZIPOutputStream gzos = new GZIPOutputStream(baos, true);
            OutputStreamWriter writer = new OutputStreamWriter(gzos, StandardCharsets.UTF_8)
        ) {
            writer.write("{\"type\":\"METADATA\",\"sessionId\":\"99002\"}\n");
            writer.write("{\"content\":\"보존되어야 하는 두 번째 원본 라인\"}\n");
            writer.flush();
        }
        byte[] originalBytes = baos.toByteArray();
        Files.write(tmpPath, originalBytes);

        assertThatThrownBy(() -> new JsonlFileChatCollector(target, tempDir.toString(), jsonMapper) {
            @Override
            BufferedWriter createRecoveryWriter(File recoveredFile) throws IOException {
                FileOutputStream fos = new FileOutputStream(recoveredFile, false);
                Writer failingWriter = new OutputStreamWriter(fos, StandardCharsets.UTF_8) {
                    private int flushCount = 0;

                    @Override
                    public void write(char[] cbuf, int off, int len) throws IOException {
                        flushCount++;
                        if (flushCount >= 2) {
                            throw new IOException("복구 파일 두 번째 줄 기록 중 디스크 풀 시뮬레이션");
                        }
                        super.write(cbuf, off, len);
                    }
                };
                return new BufferedWriter(failingWriter) {
                    @Override
                    public void newLine() throws IOException {
                        super.newLine();
                        flush();
                    }
                };
            }
        }).isInstanceOf(UncheckedIOException.class);

        assertThat(tmpPath).exists();
        assertThat(Files.readAllBytes(tmpPath)).isEqualTo(originalBytes);
        assertThat(tempDir.resolve("99002.jsonl.gz.recovered")).doesNotExist();
    }

    private StreamTarget createTarget(long liveId) {
        return new StreamTarget(
            "ch-1",
            "테스트스트리머",
            "chat-1",
            liveId,
            "테스트 방송",
            100,
            null,
            "Talk",
            Instant.parse("2026-10-25T09:00:00Z")
        );
    }

    private List<String> readAllGzipLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (
            FileInputStream fis = new FileInputStream(file);
            GZIPInputStream gzis = new GZIPInputStream(fis);
            BufferedReader reader = new BufferedReader(new InputStreamReader(gzis, StandardCharsets.UTF_8))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
