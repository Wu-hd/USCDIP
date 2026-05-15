package com.uscdip.backend.repository;

import com.uscdip.backend.entity.ModelVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ModelVersionRepository extends JpaRepository<ModelVersionEntity, String> {

    List<ModelVersionEntity> findByModelCodeOrderByUpdatedAtDescVersionNoDesc(String modelCode);

    List<ModelVersionEntity> findByModelCodeIn(Collection<String> modelCodes);

    Optional<ModelVersionEntity> findByModelCodeAndVersionNo(String modelCode, String versionNo);

    boolean existsByModelCodeAndVersionNo(String modelCode, String versionNo);
}
