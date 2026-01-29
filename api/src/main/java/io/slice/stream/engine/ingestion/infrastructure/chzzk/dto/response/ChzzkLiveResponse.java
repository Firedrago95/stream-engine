package io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChzzkLiveResponse(
    Content content
) {
    public record Content(
        List<ChzzkLive> data
    ) {
        public record ChzzkLive(
            long liveId,
            String liveTitle,
            String chatChannelId,
            int concurrentUserCount,
            Channel channel
        ) {
            public record Channel(
                String channelId,
                String channelName
            ) { }
        }
    }
}
