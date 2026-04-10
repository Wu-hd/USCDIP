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
@Table(name = "rbac_role")
public class RoleEntity {

    @Id
    @Column(name = "role_code", length = 64)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 128)
    private String roleName;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "read_only", nullable = false)
    private Boolean readOnly;
}
