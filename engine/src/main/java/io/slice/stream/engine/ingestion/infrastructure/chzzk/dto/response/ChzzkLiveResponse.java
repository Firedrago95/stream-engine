package io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChzzkLiveResponse(
    Content content
) {
    public record Content(
        int size,
        Page page,
        List<ChzzkLive> data
    ) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record  Page (
            Next next
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Next(
            Long concurrentUserCount,
            Long liveId
        ) {}

        public record ChzzkLive(
            long liveId,
            String liveTitle,
            String liveImageUrl,
            String liveCategoryValue,
            String chatChannelId,
            int concurrentUserCount,
            boolean adult,
            boolean paidPromotion,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
            LocalDateTime openDate,
            Channel channel
        ) {
            public ChzzkLive(
                long liveId,
                String liveTitle,
                String liveImageUrl,
                String liveCategoryValue,
                String chatChannelId,
                int concurrentUserCount,
                boolean adult,
                LocalDateTime openDate,
                Channel channel
            ) {
                this(liveId, liveTitle, liveImageUrl, liveCategoryValue, chatChannelId, concurrentUserCount, adult, false, openDate, channel);
            }

            public ChzzkLive(
                long liveId,
                String liveTitle,
                String liveImageUrl,
                String liveCategoryValue,
                String chatChannelId,
                int concurrentUserCount,
                boolean adult,
                Channel channel
            ) {
                this(liveId, liveTitle, liveImageUrl, liveCategoryValue, chatChannelId, concurrentUserCount, adult, false, null, channel);
            }

            public record Channel(
                String channelId,
                String channelName,
                String channelImageUrl
            ) { }

            public String getFormattedThumbnailUrl() {
                if (liveImageUrl == null) return null;
                return liveImageUrl.replace("{type}", "1080");
            }
        }
    }
}
