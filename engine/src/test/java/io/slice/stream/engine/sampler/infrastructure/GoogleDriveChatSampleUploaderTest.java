package io.slice.stream.engine.sampler.infrastructure;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import io.slice.stream.engine.sampler.domain.UploadResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class GoogleDriveChatSampleUploaderTest {

    @Mock
    private Drive drive;

    @Mock
    private Drive.Files files;

    @Mock
    private Drive.Files.Create createRequest;

    private static final String FOLDER_ID = "1EHrPLDWtyp7QknE81P1obF1dpJQS1GiD";

    @Test
    void 구글_드라이브_지정_폴더로_파일이_업로드되고_로컬_임시_파일은_삭제된다(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("sample.jsonl.gz");
        Files.writeString(tempFile, "dummy content");
        java.io.File localFile = tempFile.toFile();

        File responseFile = new File();
        responseFile.setId("file-12345");
        responseFile.setWebViewLink("https://drive.google.com/file/d/file-12345/view");

        given(drive.files()).willReturn(files);
        given(files.create(any(File.class), any(FileContent.class))).willReturn(createRequest);
        given(createRequest.setFields(anyString())).willReturn(createRequest);
        given(createRequest.execute()).willReturn(responseFile);

        GoogleDriveChatSampleUploader uploader = new GoogleDriveChatSampleUploader(drive, FOLDER_ID);

        UploadResult result = uploader.upload(localFile, "uploaded_sample.jsonl.gz");

        assertThat(result.success()).isTrue();
        assertThat(result.fileId()).isEqualTo("file-12345");
        assertThat(result.webViewLink()).isEqualTo("https://drive.google.com/file/d/file-12345/view");
        assertThat(localFile.exists()).isFalse();

        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(files).create(fileCaptor.capture(), any(FileContent.class));
        File capturedMetadata = fileCaptor.getValue();
        assertThat(capturedMetadata.getName()).isEqualTo("uploaded_sample.jsonl.gz");
        assertThat(capturedMetadata.getParents()).isEqualTo(Collections.singletonList(FOLDER_ID));
    }

    @Test
    void 구글_드라이브_업로드_실패_시_예외를_전파하지_않고_failure_결과를_반환한다(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("sample.jsonl.gz");
        Files.writeString(tempFile, "dummy content");
        java.io.File localFile = tempFile.toFile();

        given(drive.files()).willReturn(files);
        given(files.create(any(File.class), any(FileContent.class))).willReturn(createRequest);
        given(createRequest.setFields(anyString())).willReturn(createRequest);
        given(createRequest.execute()).willThrow(new IOException("Drive API Network Error"));

        GoogleDriveChatSampleUploader uploader = new GoogleDriveChatSampleUploader(drive, FOLDER_ID);

        UploadResult result = uploader.upload(localFile, "failed_sample.jsonl.gz");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Drive API Network Error");
    }
}
