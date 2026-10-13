package com.nagriksetu.user;

import java.time.Instant;
import java.util.Set;

public record UserDto(Long id, String fullName, String email, String phone, String locality, String address,
                      RoleCode role, String roleName, Long wardId, String wardName, Long departmentId,
                      String departmentName, Set<String> permissions, Instant createdAt) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getLocality(), u.getAddress(),
                u.roleCode(), u.getRole().getName(),
                u.getWard() == null ? null : u.getWard().getId(),
                u.getWard() == null ? null : u.getWard().getName(),
                u.getDepartment() == null ? null : u.getDepartment().getId(),
                u.getDepartment() == null ? null : u.getDepartment().getName(),
                u.getRole().getPermissions().stream().map(Permission::getCode).collect(java.util.stream.Collectors.toSet()),
                u.getCreatedAt());
    }
}
