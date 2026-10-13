package com.nagriksetu.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nagriksetu.support.IntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends IntegrationTest {

    @Value("${nagrik.security.jwt-secret}")
    String secret;

    private static final String REGISTER = """
            {"fullName":"Meena Rao","email":"Meena.Rao@Example.in","phone":"98765 43210",
             "password":"secure123","locality":"Ameerpet","address":"Flat 4, Lake View","role":"ADMIN"}
            """;

    @Test
    void registersCitizenWithHashedPasswordAndIgnoresRequestedRole() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("CITIZEN"))
                .andExpect(jsonPath("$.user.email").value("meena.rao@example.in"))
                .andExpect(jsonPath("$.user.phone").value("+919876543210"));

        User saved = users.findByEmailIgnoreCase("meena.rao@example.in").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo("secure123");
        assertThat(saved.roleCode()).isEqualTo(RoleCode.CITIZEN);
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER.replace("Meena.Rao@Example.in", "MEENA.RAO@example.in")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
    }

    @Test
    void validatesRegistrationFields() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"M","email":"not-an-email","phone":"12345","password":"short","locality":""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.locality").exists());
    }

    @Test
    void logsInAndRejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"citizen@test.in\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.fullName").value("Asha Citizen"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"citizen@test.in\",\"password\":\"wrong-pass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.in\",\"password\":\"wrong-pass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        String expired = Jwts.builder().issuer("nagrik-setu").subject(citizen.getId().toString())
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(citizen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("citizen@test.in"));
    }

    @Test
    void disabledAccountsLoseAccessImmediately() throws Exception {
        String token = bearer(citizen);
        citizen.setEnabled(false);
        users.save(citizen);
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void updatesProfile() throws Exception {
        mvc.perform(put("/api/auth/me").header("Authorization", bearer(citizen)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Asha K\",\"phone\":\"+91 9123456780\",\"locality\":\"Kondapur\",\"address\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha K"))
                .andExpect(jsonPath("$.locality").value("Kondapur"))
                .andExpect(jsonPath("$.phone").value("+919123456780"));
    }
}
