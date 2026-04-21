package com.uscdip.backend.repository;

import com.uscdip.backend.entity.OidcLoginStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OidcLoginStateRepository extends JpaRepository<OidcLoginStateEntity, String> {

    List<OidcLoginStateEntity> findByStatus(String status);
}
