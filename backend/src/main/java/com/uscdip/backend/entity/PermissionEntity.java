package com.uscdip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rbac_permission")
public class PermissionEntity {

    @Id
    @Column(name = "permission_code", length = 128)
    private String permissionCode;

    @Column(name = "permission_type", nullable = false, length = 32)
    private String permissionType;

    @Column(name = "resource_code", nullable = false, length = 128)
    private String resourceCode;

    @Column(name = "description", length = 512)
    private String description;
}
