package io.slice.stream.engine.sampler.infrastructure;

import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import io.slice.stream.engine.sampler.domain.ChatSampleUploader;
import io.slice.stream.engine.sampler.domain.UploadResult;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveChatSampleUploader implements ChatSampleUploader {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveChatSampleUploader.class);
    private static final String APPLICATION_NAME = "CheesePick";
    private static final String GZIP_MIME_TYPE = "application/gzip";

    private final Drive drive;
    private final String folderId;

    public GoogleDriveChatSampleUploader(Drive drive, String folderId) {
        this.drive = drive;
        this.folderId = folderId;
    }

    public static GoogleDriveChatSampleUploader create(GoogleDriveProperties properties) throws IOException {
        GoogleCredentials credentials = loadCredentials(properties);
        Drive drive = new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName(APPLICATION_NAME).build();

        return new GoogleDriveChatSampleUploader(drive, properties.folderId());
    }

    private static GoogleCredentials loadCredentials(GoogleDriveProperties properties) throws IOException {
        InputStream credentialsStream = resolveCredentialsStream(properties);
        return GoogleCredentials.fromStream(credentialsStream)
                .createScoped(Collections.singletonList(DriveScopes.DRIVE_FILE));
    }

    private static InputStream resolveCredentialsStream(GoogleDriveProperties properties) throws IOException {
        if (properties.credentialsJson() != null && !properties.credentialsJson().isBlank()) {
            return new ByteArrayInputStream(properties.credentialsJson().getBytes(StandardCharsets.UTF_8));
        }
        if (properties.credentialsPath() != null && !properties.credentialsPath().isBlank()) {
            return new FileInputStream(properties.credentialsPath());
        }
        throw new IllegalArgumentException("구글 드라이브 인증 정보(JSON 또는 파일 경로)가 설정되지 않았습니다.");
    }

    @Override
    public UploadResult upload(java.io.File file, String remoteFileName) {
        if (file == null || !file.exists()) {
            return UploadResult.failure("업로드할 로컬 파일이 존재하지 않습니다.");
        }

        try {
            File fileMetadata = createMetadata(remoteFileName);
            FileContent mediaContent = new FileContent(GZIP_MIME_TYPE, file);

            File uploadedFile = drive.files().create(fileMetadata, mediaContent)
                    .setFields("id, name, webViewLink")
                    .execute();

            log.info("구글 드라이브 파일 업로드 성공: {} (ID: {})", remoteFileName, uploadedFile.getId());
            deleteLocalFile(file);
            return UploadResult.success(uploadedFile.getId(), uploadedFile.getWebViewLink());
        } catch (Exception e) {
            log.error("구글 드라이브 파일 업로드 실패: {} - {}", remoteFileName, e.getMessage(), e);
            return UploadResult.failure(e.getMessage());
        }
    }

    private File createMetadata(String remoteFileName) {
        File fileMetadata = new File();
        fileMetadata.setName(remoteFileName);
        if (folderId != null && !folderId.isBlank()) {
            fileMetadata.setParents(List.of(folderId));
        }
        return fileMetadata;
    }

    private void deleteLocalFile(java.io.File file) {
        boolean deleted = file.delete();
        if (deleted) {
            log.info("업로드 완료에 따른 로컬 임시 파일 삭제 완료: {}", file.getAbsolutePath());
        } else {
            log.warn("로컬 임시 파일 삭제 실패: {}", file.getAbsolutePath());
        }
    }
}
