package io.slice.stream.engine.sampler.infrastructure;

import io.slice.stream.engine.sampler.domain.UploadResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(ReplaceUnderscores.class)
class LocalStorageChatSampleUploaderTest {

    private final LocalStorageChatSampleUploader uploader = new LocalStorageChatSampleUploader();

    @Test
    void 정상_파일을_전달하면_completed_하위_디렉토리로_이동하여_보관한다(@TempDir Path tempDir) throws IOException {
        File tempFile = tempDir.resolve("sample.jsonl.gz").toFile();
        tempFile.createNewFile();

        UploadResult result = uploader.upload(tempFile, "sample_한동숙_20261001.jsonl.gz");

        assertThat(result.success()).isTrue();
        assertThat(result.fileId()).isEqualTo("sample_한동숙_20261001.jsonl.gz");

        File destFile = new File(tempDir.resolve("completed").toFile(), "sample_한동숙_20261001.jsonl.gz");
        assertThat(destFile.exists()).isTrue();
        assertThat(tempFile.exists()).isFalse();
    }

    @Test
    void 파일이_존재하지_않으면_실패_결과를_반환한다() {
        File nonExistent = new File("/non/existent/path/sample.jsonl.gz");

        UploadResult result = uploader.upload(nonExistent, "remote.jsonl.gz");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("존재하지 않습니다");
    }
}
