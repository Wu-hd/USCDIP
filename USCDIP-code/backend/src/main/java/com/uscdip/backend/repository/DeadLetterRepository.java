package com.uscdip.backend.repository;

import com.uscdip.backend.entity.DeadLetterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, String> {

    List<DeadLetterEntity> findByIdempotentKeyOrderByCreatedAtAsc(String idempotentKey);

    List<DeadLetterEntity> findByStatusOrderByCreatedAtAsc(String status);
}
