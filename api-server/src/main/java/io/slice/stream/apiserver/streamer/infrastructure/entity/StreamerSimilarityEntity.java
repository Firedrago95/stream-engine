package io.slice.stream.apiserver.streamer.infrastructure.entity;

import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "streamer_similarities",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_streamer_similarities_stream_date_rank",
            columnNames = {"stream_id", "calculated_date", "rank_order"}
        )
    },
    indexes = {
        @Index(
            name = "idx_streamer_similarities_lookup",
            columnList = "stream_id, calculated_date DESC"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamerSimilarityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false)
    private String streamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SimilarityStatus status;

    @Column(name = "target_stream_id")
    private String targetStreamId;

    @Column(name = "target_streamer_name", length = 100)
    private String targetStreamerName;

    @Column(name = "target_profile_image")
    private String targetProfileImage;

    @Column(name = "target_primary_category", length = 100)
    private String targetPrimaryCategory;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @Column(name = "similarity_percent")
    private Double similarityPercent;

    @Column(name = "common_chatter_count")
    private Integer commonChatterCount;

    @Column(name = "total_chatter_count")
    private Integer totalChatterCount;

    @Column(name = "calculated_date", nullable = false)
    private LocalDate calculatedDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StreamerSimilarityEntity(
        String streamId,
        SimilarityStatus status,
        String targetStreamId,
        String targetStreamerName,
        String targetProfileImage,
        String targetPrimaryCategory,
        int rankOrder,
        Double similarityPercent,
        Integer commonChatterCount,
        Integer totalChatterCount,
        LocalDate calculatedDate
    ) {
        this.streamId = streamId;
        this.status = status;
        this.targetStreamId = targetStreamId;
        this.targetStreamerName = targetStreamerName;
        this.targetProfileImage = targetProfileImage;
        this.targetPrimaryCategory = targetPrimaryCategory;
        this.rankOrder = rankOrder;
        this.similarityPercent = similarityPercent;
        this.commonChatterCount = commonChatterCount;
        this.totalChatterCount = totalChatterCount;
        this.calculatedDate = calculatedDate;
        this.createdAt = Instant.now();
    }

    public static StreamerSimilarityEntity emptyState(
        String streamId,
        SimilarityStatus status,
        LocalDate calculatedDate
    ) {
        return new StreamerSimilarityEntity(
            streamId,
            status,
            null,
            null,
            null,
            null,
            0,
            null,
            null,
            null,
            calculatedDate
        );
    }
}
