package com.nagriksetu.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from User u where u.role.code = :role and u.ward.id = :wardId and u.enabled = true")
    List<User> findActiveByRoleAndWard(RoleCode role, Long wardId);

    @Query("select u from User u where u.role.code = :role and u.department.id = :departmentId and u.enabled = true order by u.fullName")
    List<User> findActiveByRoleAndDepartment(RoleCode role, Long departmentId);

    @Query("select u from User u where u.role.code = :role and u.enabled = true")
    List<User> findActiveByRole(RoleCode role);
}
