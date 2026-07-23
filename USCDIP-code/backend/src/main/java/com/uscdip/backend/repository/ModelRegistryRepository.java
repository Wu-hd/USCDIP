package com.uscdip.backend.repository;

import com.uscdip.backend.entity.ModelRegistryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelRegistryRepository extends JpaRepository<ModelRegistryEntity, String> {

    List<ModelRegistryEntity> findAllByOrderByUpdatedAtDescModelCodeAsc();
}
