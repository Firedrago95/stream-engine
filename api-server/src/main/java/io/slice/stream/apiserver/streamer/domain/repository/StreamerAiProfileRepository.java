package io.slice.stream.apiserver.streamer.domain.repository;

import io.slice.stream.apiserver.streamer.presentation.dto.StreamerAiProfileDto;
import java.util.Optional;

public interface StreamerAiProfileRepository {

    Optional<StreamerAiProfileDto> findByStreamId(String streamId);
}
