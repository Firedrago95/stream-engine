package io.slice.stream.engine.sampler.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.sampler.application.dto.SamplingStatusResponse;
import io.slice.stream.engine.sampler.application.dto.StartSamplingRequest;
import io.slice.stream.engine.sampler.domain.ChatSampleMessage;
import io.slice.stream.engine.sampler.domain.ChatSampleUploader;
import io.slice.stream.engine.sampler.domain.UploadResult;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        objectMapper = new ObjectMapper();
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
                Map.of("channel_1", "한동숙"),
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
        assertThat(status.sessions().get(0).streamerName()).isEqualTo("한동숙");
    }

    @Test
    void StreamRepository에서_스트리머_이름을_자동으로_조회하여_세션을_생성한다() {
        StreamTarget mockTarget = new StreamTarget(
                "channel_2",
                "괴물쥐",
                "chat_ch_2",
                12345L,
                "생방송",
                5000,
                "profile.png",
                "롤",
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
        assertThat(status.sessions().get(0).streamerName()).isEqualTo("괴물쥐");
    }

    @Test
    void 기록된_메시지는_백그라운드에서_파일에_기록되고_세션_종료_시_업로더가_호출된다() {
        given(uploader.upload(any(File.class), anyString()))
                .willReturn(UploadResult.success("drive-file-id", "https://link"));

        StartSamplingRequest request = new StartSamplingRequest(
                "lol_test",
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
}

