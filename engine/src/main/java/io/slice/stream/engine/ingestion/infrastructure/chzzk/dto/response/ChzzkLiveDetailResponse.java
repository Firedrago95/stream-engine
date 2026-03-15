package io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChzzkLiveDetailResponse(
    Content content
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
        String status,
        String chatChannelId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
        LocalDateTime openDate
    ) {
    }
}
