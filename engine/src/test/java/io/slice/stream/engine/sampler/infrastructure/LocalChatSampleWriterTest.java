package io.slice.stream.engine.sampler.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slice.stream.engine.sampler.domain.ChatSampleHeader;
import io.slice.stream.engine.sampler.domain.ChatSampleMessage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(ReplaceUnderscores.class)
class LocalChatSampleWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void 헤더를_기록하면_파일_첫_줄에_메타데이터가_포함되고_메시지_카운트에는_영향을_주지_않는다(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test_header_sample.jsonl.gz");
        LocalChatSampleWriter writer = new LocalChatSampleWriter(tempFile.toFile(), objectMapper);

        Instant openDate = Instant.parse("2026-09-30T10:00:00Z");
        Instant samplingStart = Instant.parse("2026-09-30T10:05:00Z");
        ChatSampleHeader header = ChatSampleHeader.of(openDate, samplingStart, "channel_1", "스트리머A");

        writer.writeHeader(header);

        ChatSampleMessage msg = new ChatSampleMessage(samplingStart.plusSeconds(1), "channel_1", "user_1", "채팅 내용", false);
        writer.write(msg);

        File resultFile = writer.finish();

        assertThat(resultFile.exists()).isTrue();
        assertThat(writer.getMessageCount()).isEqualTo(1);

        List<String> lines = readGzipLines(resultFile);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("\"type\":\"METADATA\"");
        assertThat(lines.get(0)).contains("\"openDate\":\"2026-09-30T10:00:00Z\"");
        assertThat(lines.get(0)).contains("\"openDateEpoch\":1790762400");
        assertThat(lines.get(0)).contains("\"samplingStartedAt\":\"2026-09-30T10:05:00Z\"");
        assertThat(lines.get(0)).contains("\"samplingStartedAtEpoch\":1790762700");
        assertThat(lines.get(0)).contains("\"channelId\":\"channel_1\"");
        assertThat(lines.get(0)).contains("\"streamerName\":\"스트리머A\"");
        assertThat(lines.get(1)).contains("채팅 내용");
    }

    @Test
    void 메시지를_기록하고_finish_호출_시_정상적인_GZIP_JSON_Lines_압축_파일이_생성된다(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test_sample.jsonl.gz");
        LocalChatSampleWriter writer = new LocalChatSampleWriter(tempFile.toFile(), objectMapper);

        Instant now = Instant.parse("2026-09-30T12:00:00Z");
        ChatSampleMessage msg1 = new ChatSampleMessage(now, "ch1", "author_1", "ㅋㅋㅋㅋ 대박이네", true);
        ChatSampleMessage msg2 = new ChatSampleMessage(now.plusSeconds(1), "ch1", "author_2", "아니 저게 왜 죽어", false);

        writer.write(msg1);
        writer.write(msg2);
        File resultFile = writer.finish();

        assertThat(resultFile.exists()).isTrue();
        assertThat(writer.getMessageCount()).isEqualTo(2);

        List<String> lines = readGzipLines(resultFile);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("ㅋㅋㅋㅋ 대박이네");
        assertThat(lines.get(0)).contains("author_1");
        assertThat(lines.get(0)).doesNotContain("channelId");
        assertThat(lines.get(0)).doesNotContain("streamerName");
        assertThat(lines.get(1)).contains("아니 저게 왜 죽어");
    }

    private List<String> readGzipLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (GZIPInputStream gzipStream = new GZIPInputStream(new FileInputStream(file));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzipStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
