package com.nagriksetu.complaint;

import com.nagriksetu.user.RoleCode;
import com.nagriksetu.user.User;

final class RoleLabels {

    private RoleLabels() {
    }

    static String of(User user) {
        if (user == null) {
            return "System";
        }
        return of(user.roleCode());
    }

    static String of(RoleCode role) {
        return switch (role) {
            case CITIZEN -> "Citizen";
            case WARD_HEAD -> "Ward Office";
            case DEPARTMENT_HEAD -> "Department Head";
            case OFFICER -> "Field Officer";
            case ADMIN -> "Municipal Control Room";
        };
    }
}
