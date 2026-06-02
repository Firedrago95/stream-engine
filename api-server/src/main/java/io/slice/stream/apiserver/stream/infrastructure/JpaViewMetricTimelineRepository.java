package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaViewMetricTimelineRepository extends JpaRepository<ViewMetricTimelineEntity, Long> {

}
