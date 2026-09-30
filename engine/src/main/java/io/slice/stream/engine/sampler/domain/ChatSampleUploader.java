package io.slice.stream.engine.sampler.domain;

import java.io.File;

public interface ChatSampleUploader {
    UploadResult upload(File file, String remoteFileName);
}
