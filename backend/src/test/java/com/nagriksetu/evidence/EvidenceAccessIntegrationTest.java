package com.nagriksetu.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.nagriksetu.support.IntegrationTest;
import com.nagriksetu.user.User;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class EvidenceAccessIntegrationTest extends IntegrationTest {

    @Autowired EvidenceUrlSigner signer;
    @Autowired EvidenceRepository evidenceRepository;

    private String content(UUID id) {
        return "/api/evidence/" + id + "/content";
    }

    private UUID firstEvidence(JsonNode complaint) {
        return UUID.fromString(complaint.get("citizenEvidence").get(0).get("id").asText());
    }

    @Test
    void ownerCanDownloadWithBearerTokenAndBytesMatchStorage() throws Exception {
        JsonNode c = createComplaint(citizen, false);
        UUID id = firstEvidence(c);
        byte[] expected = Files.readAllBytes(storage.root().resolve(evidenceRepository.findById(id).orElseThrow().getStorageKey()));
        byte[] body = mvc.perform(get(content(id)).header("Authorization", bearer(citizen)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(expected);
    }

    @Test
    void privateEvidenceIsHiddenFromOtherCitizensAndOutOfScopeOfficials() throws Exception {
        JsonNode c = createComplaint(citizen, false);
        UUID id = firstEvidence(c);
        mvc.perform(get(content(id)).header("Authorization", bearer(otherCitizen))).andExpect(status().isNotFound());
        mvc.perform(get(content(id)).header("Authorization", bearer(wardHeadB))).andExpect(status().isNotFound());
        mvc.perform(get(content(id)).header("Authorization", bearer(roadsHead))).andExpect(status().isNotFound());
        mvc.perform(get(content(id)).header("Authorization", bearer(roadsOfficer))).andExpect(status().isNotFound());
        mvc.perform(get(content(id))).andExpect(status().isUnauthorized());
        // The complaint itself is hidden too (404, not 403, so references cannot be probed).
        mvc.perform(get("/api/complaints/" + c.get("id").asLong()).header("Authorization", bearer(otherCitizen)))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicEvidenceIsVisibleToOtherCitizensWithoutPersonalDetails() throws Exception {
        JsonNode c = createComplaint(citizen, true);
        UUID id = firstEvidence(c);
        mvc.perform(get(content(id)).header("Authorization", bearer(otherCitizen)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("photo.png")));
        mvc.perform(get("/api/complaints/" + c.get("id").asLong()).header("Authorization", bearer(otherCitizen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("PUBLIC"))
                .andExpect(jsonPath("$.reporter").doesNotExist())
                .andExpect(jsonPath("$.address").value("Madhapur"))
                .andExpect(jsonPath("$.citizenEvidence[0].filename").doesNotExist());
    }

    @Test
    void sameEvidenceFollowsTheComplaintThroughWardDepartmentAndOfficer() throws Exception {
        JsonNode c = createComplaint(citizen, false);
        long complaintId = c.get("id").asLong();
        UUID id = firstEvidence(c);

        mvc.perform(get(content(id)).header("Authorization", bearer(wardHeadA))).andExpect(status().isOk());
        mvc.perform(get(content(id)).header("Authorization", bearer(roadsHead))).andExpect(status().isNotFound());

        mvc.perform(post("/api/official/complaints/" + complaintId + "/route").header("Authorization", bearer(wardHeadA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"departmentId\":" + departmentId("ROADS") + "}"))
                .andExpect(status().isOk());
        mvc.perform(get(content(id)).header("Authorization", bearer(roadsHead))).andExpect(status().isOk());
        mvc.perform(get(content(id)).header("Authorization", bearer(roadsOfficer))).andExpect(status().isNotFound());

        mvc.perform(post("/api/official/complaints/" + complaintId + "/assign").header("Authorization", bearer(roadsHead))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"officerId\":" + roadsOfficer.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citizenEvidence[0].id").value(id.toString()));
        mvc.perform(get(content(id)).header("Authorization", bearer(roadsOfficer))).andExpect(status().isOk());
        mvc.perform(get(content(id)).header("Authorization", bearer(sanitationOfficer))).andExpect(status().isNotFound());

        // One evidence record and one stored file, shared by every official: no copies were made.
        assertThat(evidenceRepository.count()).isEqualTo(1);
        try (var files = Files.walk(storage.root())) {
            assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1);
        }
    }

    @Test
    void signedUrlsAreUserBoundExpiringAndRecheckPermissions() throws Exception {
        JsonNode c = createComplaint(citizen, false);
        UUID id = firstEvidence(c);
        String url = c.get("citizenEvidence").get(0).get("url").asText();
        assertThat(url).startsWith(content(id) + "?u=" + citizen.getId());

        mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(get(url.replace("sig=", "sig=x"))).andExpect(status().isUnauthorized());
        mvc.perform(get(url.replace("u=" + citizen.getId(), "u=" + otherCitizen.getId()))).andExpect(status().isUnauthorized());

        long past = Instant.now().getEpochSecond() - 10;
        String expired = content(id) + "?u=" + citizen.getId() + "&exp=" + past + "&sig="
                + signer.signedUrl(id, citizen.getId()).replaceAll(".*sig=", "");
        mvc.perform(get(expired)).andExpect(status().isUnauthorized());

        // A validly signed URL for a user without permission is still refused.
        String forOther = signer.signedUrl(id, otherCitizen.getId());
        mvc.perform(get(forOther)).andExpect(status().isNotFound());

        // Permission is re-evaluated on every request: disabling the owner revokes their URL.
        User owner = users.findById(citizen.getId()).orElseThrow();
        owner.setEnabled(false);
        users.save(owner);
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEvidenceIsNotFound() throws Exception {
        mvc.perform(get(content(UUID.randomUUID())).header("Authorization", bearer(citizen))).andExpect(status().isNotFound());
    }
}
