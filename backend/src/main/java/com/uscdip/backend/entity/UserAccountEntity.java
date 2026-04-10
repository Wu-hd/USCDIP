package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_account")
public class UserAccountEntity {

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "primary_region_id", length = 64)
    private String primaryRegionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
