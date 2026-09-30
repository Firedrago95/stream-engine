package io.slice.stream.engine.sampler.infrastructure;

import io.slice.stream.engine.sampler.domain.ChatSampleUploader;
import io.slice.stream.engine.sampler.domain.UploadResult;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalStorageChatSampleUploader implements ChatSampleUploader {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageChatSampleUploader.class);

    @Override
    public UploadResult upload(File file, String remoteFileName) {
        if (file == null || !file.exists()) {
            return UploadResult.failure("업로드할 파일이 존재하지 않습니다.");
        }
        log.info("로컬 스토리지에 샘플 파일 보관 완료: {}", file.getAbsolutePath());
        return UploadResult.success(file.getName(), file.getAbsolutePath());
    }
}
