package io.slice.stream.apiserver.category.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "weekly_category_rankings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyCategoryRankingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "accumulated_view_hours", nullable = false, length = 50)
    private String accumulatedViewHours;

    @Column(name = "exact_hours", nullable = false)
    private long exactHours;

    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;

    @Column(name = "change_value")
    private Integer changeValue;

    @Column(length = 20)
    private String icon;

    @Column(name = "created_at")
    private Instant createdAt;

    public WeeklyCategoryRankingEntity(
        LocalDate weekStartDate,
        String categoryName,
        int rank,
        String accumulatedViewHours,
        long exactHours,
        String changeType,
        Integer changeValue,
        String icon
    ) {
        this.weekStartDate = weekStartDate;
        this.categoryName = categoryName;
        this.rank = rank;
        this.accumulatedViewHours = accumulatedViewHours;
        this.exactHours = exactHours;
        this.changeType = changeType;
        this.changeValue = changeValue;
        this.icon = icon;
        this.createdAt = Instant.now();
    }
}
