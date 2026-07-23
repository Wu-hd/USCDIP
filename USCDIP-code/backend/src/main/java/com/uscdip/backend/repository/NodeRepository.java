package com.uscdip.backend.repository;

import com.uscdip.backend.entity.NodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeRepository extends JpaRepository<NodeEntity, String> {
}
