package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaViewMetricTimelineEntity extends JpaRepository<ViewMetricTimelineEntity, Long> {

}
