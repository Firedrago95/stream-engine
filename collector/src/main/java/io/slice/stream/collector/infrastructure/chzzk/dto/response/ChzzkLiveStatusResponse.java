package io.slice.stream.collector.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChzzkLiveStatusResponse(
    Content content
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
        String liveTitle,
        String status,
        int concurrentUserCount,
        int accumulateCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
        LocalDateTime openDate,
        String chatChannelId,
        String liveCategoryValue,
        String channelId
    ) {
    }
}
