package com.nagriksetu.security;

import com.nagriksetu.user.RoleCode;
import com.nagriksetu.user.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** The authenticated caller. Built from the database on every request, never from client input. */
public record UserPrincipal(Long id, String email, String fullName, RoleCode role, Long wardId, Long departmentId,
                            Set<String> permissions) {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.roleCode(),
                user.getWard() == null ? null : user.getWard().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getRole().getPermissions().stream().map(p -> p.getCode()).collect(Collectors.toUnmodifiableSet()));
    }

    public Collection<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        return authorities;
    }

    public boolean is(RoleCode code) {
        return role == code;
    }

    public boolean isOfficial() {
        return role.isOfficial();
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
