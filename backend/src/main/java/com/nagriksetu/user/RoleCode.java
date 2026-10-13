package com.nagriksetu.user;

public enum RoleCode {
    CITIZEN, WARD_HEAD, DEPARTMENT_HEAD, OFFICER, ADMIN;

    public boolean isOfficial() {
        return this != CITIZEN;
    }
}
