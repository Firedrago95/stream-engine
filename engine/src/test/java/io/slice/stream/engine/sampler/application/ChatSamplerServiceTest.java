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
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatSamplerServiceTest {

    @Mock
    private ChatSampleUploader uploader;

    @Mock
    private StreamRepository streamRepository;

    private ChatSamplerService service;
    private ObjectMapper objectMapper;
    private Path tempDirPath;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        this.tempDirPath = tempDir;
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ChatSamplerService(uploader, objectMapper, streamRepository, tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        service.stopAll("TEST_CLEANUP");
    }

    @Test
    void 샘플링을_시작하면_해당_채널에_대해_isSampling이_true가_되고_상태_조회가_가능하다() {
        StartSamplingRequest request = new StartSamplingRequest(
                "test_tag",
                List.of("channel_1"),
                Map.of("channel_1", "스트리머_A"),
                null,
                null,
                30
        );

        service.startSampling(request);

        assertThat(service.isSampling("channel_1")).isTrue();
        assertThat(service.isSampling("other_channel")).isFalse();

        SamplingStatusResponse status = service.getStatus();
        assertThat(status.active()).isTrue();
        assertThat(status.activeSessionCount()).isEqualTo(1);
        assertThat(status.sessions().get(0).streamerName()).isEqualTo("스트리머_A");
    }

    @Test
    void StreamRepository에서_스트리머_이름을_자동으로_조회하여_세션을_생성한다() {
        StreamTarget mockTarget = new StreamTarget(
                "channel_2",
                "스트리머_B",
                "chat_ch_2",
                12345L,
                "생방송",
                5000,
                "profile.png",
                "게임",
                Instant.now()
        );
        given(streamRepository.getStreamTargets(List.of("channel_2"))).willReturn(List.of(mockTarget));

        StartSamplingRequest request = new StartSamplingRequest(
                "test_tag",
                List.of("channel_2"),
                null,
                null,
                null,
                null
        );

        service.startSampling(request);

        SamplingStatusResponse status = service.getStatus();
        assertThat(status.sessions().get(0).streamerName()).isEqualTo("스트리머_B");
    }

    @Test
    void StreamRepository의_openDate를_조회하여_세션과_파일_헤더에_기록한다() throws IOException {
        Instant expectedOpenDate = Instant.parse("2026-09-30T10:00:00Z");
        StreamTarget mockTarget = new StreamTarget(
                "channel_live",
                "스트리머_LIVE",
                "chat_ch_live",
                99999L,
                "생방송 타이틀",
                10000,
                "profile.png",
                "토크",
                expectedOpenDate
        );
        given(streamRepository.getStreamTargets(List.of("channel_live"))).willReturn(List.of(mockTarget));

        StartSamplingRequest request = new StartSamplingRequest(
                "tag_live",
                List.of("channel_live"),
                null,
                null,
                null,
                null
        );

        given(uploader.upload(any(File.class), anyString()))
                .willReturn(UploadResult.success("drive-file-id", "https://link"));

        service.startSampling(request);

        SamplingStatusResponse status = service.getStatus();
        assertThat(status.sessions().get(0).startedAt()).isEqualTo(expectedOpenDate);

        File[] files = tempDirPath.toFile().listFiles((dir, name) -> name.endsWith(".jsonl.gz"));
        assertThat(files).isNotNull();
        assertThat(files).hasSize(1);

        service.finishSession("channel_live", "TEST_FINISH");
        verify(uploader, timeout(3000)).upload(any(File.class), anyString());

        List<String> lines = readGzipLines(files[0]);
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).contains("\"type\":\"METADATA\"");
        assertThat(lines.get(0)).contains("\"openDate\":\"2026-09-30T10:00:00Z\"");
        assertThat(lines.get(0)).contains("\"channelId\":\"channel_live\"");
        assertThat(lines.get(0)).contains("\"streamerName\":\"스트리머_LIVE\"");
    }

    @Test
    void StreamRepository에_방송_정보가_없으면_fallback으로_현재_시각을_사용한다() {
        given(streamRepository.getStreamTargets(List.of("channel_unknown"))).willReturn(List.of());

        StartSamplingRequest request = new StartSamplingRequest(
                "tag_unknown",
                List.of("channel_unknown"),
                null,
                null,
                null,
                null
        );

        Instant before = Instant.now();
        service.startSampling(request);
        Instant after = Instant.now();

        SamplingStatusResponse status = service.getStatus();
        Instant sessionStartedAt = status.sessions().get(0).startedAt();
        assertThat(sessionStartedAt).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void 기록된_메시지는_백그라운드에서_파일에_기록되고_세션_종료_시_업로더가_호출된다() {
        given(uploader.upload(any(File.class), anyString()))
                .willReturn(UploadResult.success("drive-file-id", "https://link"));

        StartSamplingRequest request = new StartSamplingRequest(
                "test_tag",
                List.of("channel_1"),
                null,
                null,
                null,
                null
        );
        service.startSampling(request);

        Instant now = Instant.now();
        service.record(new ChatSampleMessage(now, "channel_1", "user_1", "ㅋㅋㅋㅋ 대박", true));
        service.record(new ChatSampleMessage(now.plusMillis(100), "channel_1", "user_2", "아니 저게", false));

        service.finishSession("channel_1", "STREAM_CLOSED");

        verify(uploader, timeout(3000)).upload(any(File.class), anyString());
        assertThat(service.isSampling("channel_1")).isFalse();
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

