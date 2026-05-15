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
@Table(name = "object_scope_binding")
public class ObjectScopeBindingEntity {

    @Id
    @Column(name = "binding_id", length = 64)
    private String bindingId;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 64)
    private String objectId;

    @Column(name = "region_id", length = 64)
    private String regionId;

    @Column(name = "owner_user_id", length = 64)
    private String ownerUserId;

    @Column(name = "owner_username", length = 64)
    private String ownerUsername;

    @Column(name = "scope_level", nullable = false, length = 32)
    private String scopeLevel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
