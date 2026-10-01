package io.slice.stream.engine.sampler.infrastructure;

import io.slice.stream.engine.sampler.domain.ChatSampleUploader;
import io.slice.stream.engine.sampler.domain.UploadResult;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalStorageChatSampleUploader implements ChatSampleUploader {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageChatSampleUploader.class);
    private static final String COMPLETED_DIR_NAME = "completed";

    @Override
    public UploadResult upload(File file, String remoteFileName) {
        if (file == null || !file.exists()) {
            return UploadResult.failure("업로드할 파일이 존재하지 않습니다.");
        }

        File parentDir = file.getParentFile();
        File completedDir = (parentDir != null) ? new File(parentDir, COMPLETED_DIR_NAME) : new File(COMPLETED_DIR_NAME);
        if (!completedDir.exists() && !completedDir.mkdirs()) {
            log.error("완료 디렉토리 생성 실패: {}", completedDir.getAbsolutePath());
            return UploadResult.failure("완료 디렉토리 생성 실패: " + completedDir.getAbsolutePath());
        }

        String targetFileName = (remoteFileName != null && !remoteFileName.isBlank()) ? remoteFileName : file.getName();
        File targetFile = new File(completedDir, targetFileName);

        boolean moved = file.renameTo(targetFile);
        if (!moved) {
            log.warn("파일 이동 실패 (원본 유지): {} -> {}", file.getAbsolutePath(), targetFile.getAbsolutePath());
            return UploadResult.success(file.getName(), file.getAbsolutePath());
        }

        log.info("로컬 스토리지에 샘플 파일 보관 완료 (완료 디렉토리 이동): {}", targetFile.getAbsolutePath());
        return UploadResult.success(targetFile.getName(), targetFile.getAbsolutePath());
    }
}

