package com.nagriksetu.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.nagriksetu.support.IntegrationTest;
import com.nagriksetu.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AdminIntegrationTest extends IntegrationTest {

    private ResultActions call(User who, String method, String path, String body) throws Exception {
        var req = switch (method) {
            case "POST" -> post(path);
            case "PUT" -> put(path);
            default -> get(path);
        };
        req.header("Authorization", bearer(who));
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private String official(String email, String role, String extra) {
        return """
                {"fullName":"New Official","email":"%s","phone":"9876543210","password":"Temp1234","role":"%s"%s}
                """.formatted(email, role, extra);
    }

    @Test
    void onlyAdministratorsCanUseTheAdminApi() throws Exception {
        for (User u : new User[] {citizen, wardHeadA, roadsHead, roadsOfficer}) {
            call(u, "GET", "/api/admin/accounts", null).andExpect(status().isForbidden());
            call(u, "POST", "/api/admin/accounts", official("x@test.in", "OFFICER", ",\"departmentId\":1")).andExpect(status().isForbidden());
            call(u, "GET", "/api/admin/audit", null).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsOfficialsAndOptionallyCitizens() throws Exception {
        call(admin, "GET", "/api/admin/accounts", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
        call(admin, "GET", "/api/admin/accounts?includeCitizens=true", null).andExpect(jsonPath("$.length()").value(8));
        call(admin, "GET", "/api/admin/accounts?role=OFFICER", null).andExpect(jsonPath("$.length()").value(2));
        call(admin, "GET", "/api/admin/accounts?q=wardhead.a", null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].wardName").value("Test Ward A"));
    }

    @Test
    void createsOfficialsWithTheAssignmentTheirRoleNeeds() throws Exception {
        Long roads = departmentId("ROADS");
        call(admin, "POST", "/api/admin/accounts", official("new.officer@test.in", "OFFICER", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ASSIGNMENT_REQUIRED"));
        call(admin, "POST", "/api/admin/accounts", official("new.ward@test.in", "WARD_HEAD", ",\"departmentId\":" + roads))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ASSIGNMENT_REQUIRED"));
        call(admin, "POST", "/api/admin/accounts", official("someone@test.in", "CITIZEN", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ROLE"));
        call(admin, "POST", "/api/admin/accounts", official("CITIZEN@test.in", "ADMIN", ""))
                .andExpect(status().isConflict());
        call(admin, "POST", "/api/admin/accounts", official("weak@test.in", "ADMIN", "").replace("Temp1234", "short"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());

        JsonNode created = read(call(admin, "POST", "/api/admin/accounts",
                        official("New.Officer@Test.in", "OFFICER", ",\"departmentId\":" + roads + ",\"wardId\":" + wardA.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.officer@test.in"))
                .andExpect(jsonPath("$.departmentName").value("Roads & Transport"))
                .andExpect(jsonPath("$.wardId").doesNotExist())
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn());

        // The new officer can sign in with the temporary password and is offered to the department head.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.officer@test.in\",\"password\":\"Temp1234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("OFFICER"));
        call(roadsHead, "GET", "/api/official/officers", null).andExpect(jsonPath("$.length()").value(2));
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action = 'OFFICIAL_CREATED' and entity_id = ?",
                Integer.class, created.get("id").asText())).isEqualTo(1);
    }

    @Test
    void updatesRoleAndAssignment() throws Exception {
        call(admin, "PUT", "/api/admin/accounts/" + roadsOfficer.getId(),
                        "{\"fullName\":\"Roads Officer\",\"phone\":\"9876543210\",\"role\":\"WARD_HEAD\",\"wardId\":" + wardB.getId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("WARD_HEAD"))
                .andExpect(jsonPath("$.wardName").value("Test Ward B"))
                .andExpect(jsonPath("$.departmentId").doesNotExist());
        call(admin, "PUT", "/api/admin/accounts/" + citizen.getId(),
                        "{\"fullName\":\"Asha\",\"phone\":\"9876543210\",\"role\":\"OFFICER\",\"departmentId\":1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ROLE"));
        call(admin, "PUT", "/api/admin/accounts/" + admin.getId(),
                        "{\"fullName\":\"Admin\",\"phone\":\"9876543210\",\"role\":\"OFFICER\",\"departmentId\":1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SELF_DEMOTION"));
    }

    @Test
    void deactivationBlocksSignInImmediatelyAndCanBeReversed() throws Exception {
        String officerToken = bearer(roadsOfficer);
        call(admin, "POST", "/api/admin/accounts/" + roadsOfficer.getId() + "/status", "{\"enabled\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/api/auth/me").header("Authorization", officerToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"roads.officer@test.in\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        call(roadsHead, "GET", "/api/official/officers", null).andExpect(jsonPath("$.length()").value(0));

        call(admin, "POST", "/api/admin/accounts/" + roadsOfficer.getId() + "/status", "{\"enabled\":true}")
                .andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(get("/api/auth/me").header("Authorization", officerToken)).andExpect(status().isOk());

        call(admin, "POST", "/api/admin/accounts/" + admin.getId() + "/status", "{\"enabled\":false}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SELF_DEACTIVATION"));
    }

    @Test
    void adminResetsAForgottenPassword() throws Exception {
        call(admin, "POST", "/api/admin/accounts/" + citizen.getId() + "/password", "{\"newPassword\":\"abc\"}")
                .andExpect(status().isBadRequest());
        call(admin, "POST", "/api/admin/accounts/" + citizen.getId() + "/password", "{\"newPassword\":\"NewPass2026\"}")
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"citizen@test.in\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"citizen@test.in\",\"password\":\"NewPass2026\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void auditLogShowsSensitiveActionsWithActors() throws Exception {
        createComplaint(citizen, true);
        call(admin, "POST", "/api/admin/accounts/" + otherCitizen.getId() + "/status", "{\"enabled\":false}").andExpect(status().isOk());
        call(admin, "GET", "/api/admin/audit", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("ACCOUNT_DEACTIVATED"))
                .andExpect(jsonPath("$.items[0].actorName").value("Admin"))
                .andExpect(jsonPath("$.items[1].action").value("COMPLAINT_CREATED"))
                .andExpect(jsonPath("$.items[1].actorEmail").value("citizen@test.in"));
        call(admin, "GET", "/api/admin/audit?action=COMPLAINT_CREATED", null)
                .andExpect(jsonPath("$.totalItems").value(1));
    }
}
