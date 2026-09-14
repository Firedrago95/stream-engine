package io.slice.stream.apiserver.stream.targeting;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TargetStreamerRepository extends JpaRepository<TargetStreamerEntity, Long> {

    List<TargetStreamerEntity> findAllByIsActiveTrue();

    Optional<TargetStreamerEntity> findByChannelId(String channelId);

    boolean existsByChannelId(String channelId);
}
