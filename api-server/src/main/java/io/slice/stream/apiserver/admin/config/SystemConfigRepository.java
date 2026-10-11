package io.slice.stream.apiserver.admin.config;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, String> {

    List<SystemConfigEntity> findAllByOrderByCategoryAscConfigKeyAsc();
}
