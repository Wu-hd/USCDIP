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
@Table(name = "object_relation")
public class ObjectRelationEntity {

    @Id
    @Column(name = "relation_id", length = 64)
    private String relationId;

    @Column(name = "parent_object_type", nullable = false, length = 32)
    private String parentObjectType;

    @Column(name = "parent_object_id", nullable = false, length = 64)
    private String parentObjectId;

    @Column(name = "child_object_type", nullable = false, length = 32)
    private String childObjectType;

    @Column(name = "child_object_id", nullable = false, length = 64)
    private String childObjectId;

    @Column(name = "relation_type", nullable = false, length = 64)
    private String relationType;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
