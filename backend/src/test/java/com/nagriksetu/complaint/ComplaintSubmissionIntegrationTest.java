package com.nagriksetu.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.nagriksetu.evidence.Evidence;
import com.nagriksetu.evidence.EvidenceRepository;
import com.nagriksetu.evidence.EvidenceType;
import com.nagriksetu.support.IntegrationTest;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class ComplaintSubmissionIntegrationTest extends IntegrationTest {

    @Autowired EvidenceRepository evidenceRepository;
    @Autowired ComplaintRepository complaintRepository;

    @Test
    @Transactional
    void createsComplaintWithPhotosStoredAndLinked() throws Exception {
        byte[] png = pngBytes(Color.RED);
        byte[] jpg = jpegBytes(Color.BLUE);
        JsonNode body = read(mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A,
                        photo("../../etc/road photo.png", png, "image/png"),
                        photo("closeup.jpeg", jpg, "image/jpeg"))
                        .header("Authorization", bearer(citizen)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.wardMatch").value("AUTO_MATCHED"))
                .andExpect(jsonPath("$.wardName").value("Test Ward A"))
                .andExpect(jsonPath("$.access").value("OWNER"))
                .andExpect(jsonPath("$.citizenEvidence.length()").value(2))
                .andExpect(jsonPath("$.resolutionEvidence.length()").value(0))
                .andReturn());

        assertThat(body.get("referenceNo").asText()).matches("NS-\\d{6}-\\d{6}");
        Long id = body.get("id").asLong();

        List<Evidence> stored = evidenceRepository.findByComplaint(id);
        assertThat(stored).hasSize(2);
        for (Evidence e : stored) {
            assertThat(e.getComplaint().getId()).isEqualTo(id);
            assertThat(e.getUploader().getId()).isEqualTo(citizen.getId());
            assertThat(e.getType()).isEqualTo(EvidenceType.CITIZEN_REPORT);
            assertThat(e.getStorageProvider()).isEqualTo("local");
            // Generated key: never derived from the uploaded file name.
            assertThat(e.getStorageKey()).matches("complaints/" + id + "/citizen-report/[0-9a-f-]{36}\\.(png|jpg)");
            Path file = storage.root().resolve(e.getStorageKey());
            assertThat(file).exists();
            byte[] bytes = Files.readAllBytes(file);
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))).isEqualTo(e.getSha256());
            assertThat(e.getSizeBytes()).isEqualTo(bytes.length);
        }
        assertThat(stored).extracting(Evidence::getOriginalFilename).containsExactlyInAnyOrder("road photo.png", "closeup.jpg");
        assertThat(stored).extracting(Evidence::getContentType).containsExactlyInAnyOrder("image/png", "image/jpeg");
    }

    @Test
    void ignoresClientAttemptsToSetOfficialFields() throws Exception {
        JsonNode body = read(mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A, photo("p.png", pngBytes(Color.GRAY), "image/png"))
                        .param("status", "RESOLVED")
                        .param("priority", "URGENT")
                        .param("departmentId", departmentId("ROADS").toString())
                        .param("assignedOfficerId", roadsOfficer.getId().toString())
                        .param("reporterId", otherCitizen.getId().toString())
                        .header("Authorization", bearer(citizen)))
                .andExpect(status().isCreated()).andReturn());
        assertThat(body.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(body.get("priority").asText()).isEqualTo("NORMAL");
        assertThat(body.has("departmentName")).isFalse();
        assertThat(body.has("assignedOfficerName")).isFalse();
        Complaint c = complaintRepository.findById(body.get("id").asLong()).orElseThrow();
        assertThat(c.reporterId()).isEqualTo(citizen.getId());
    }

    @Test
    void unmatchedLocationNeedsManualWardVerificationAndAlertsAdmins() throws Exception {
        JsonNode body = read(mvc.perform(complaintForm(LAT_OUTSIDE, LNG_OUTSIDE).header("Authorization", bearer(citizen)))
                .andExpect(status().isCreated()).andReturn());
        assertThat(body.get("wardMatch").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(body.has("wardName")).isFalse();
        Integer adminNotes = jdbc.queryForObject(
                "select count(*) from notifications where user_id = ? and type = 'WARD_VERIFICATION_NEEDED'", Integer.class, admin.getId());
        assertThat(adminNotes).isEqualTo(1);
        Integer wardNotes = jdbc.queryForObject("select count(*) from notifications where user_id = ?", Integer.class, wardHeadA.getId());
        assertThat(wardNotes).isZero();
    }

    @Test
    void autoMatchedComplaintNotifiesWardHeadAndCitizen() throws Exception {
        createComplaint(citizen, true);
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'NEW_COMPLAINT'",
                Integer.class, wardHeadA.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'COMPLAINT_SUBMITTED'",
                Integer.class, citizen.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Integer.class, wardHeadB.getId())).isZero();
    }

    @Test
    void rejectsNonImageDisguisedAsJpegAndStoresNothing() throws Exception {
        mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A,
                        photo("ok.png", pngBytes(Color.GREEN), "image/png"),
                        photo("evil.jpg", "<script>alert(1)</script>".getBytes(), "image/jpeg"))
                        .header("Authorization", bearer(citizen)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        assertThat(complaintRepository.count()).isZero();
        assertThat(evidenceRepository.count()).isZero();
        assertThat(filesInStorage()).isZero();
    }

    @Test
    void rejectsUnsupportedDeclaredType() throws Exception {
        mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A, photo("doc.pdf", "%PDF-1.4".getBytes(), "application/pdf"))
                        .header("Authorization", bearer(citizen)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void rejectsTooManyFilesAndOversizedFiles() throws Exception {
        var req = complaintForm(LAT_IN_A, LNG_IN_A);
        for (int i = 0; i < 7; i++) {
            req.file(photo("p" + i + ".png", pngBytes(Color.GRAY), "image/png"));
        }
        mvc.perform(req.header("Authorization", bearer(citizen)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));

        byte[] big = new byte[8 * 1024 * 1024 + 10];
        byte[] header = pngBytes(Color.GRAY);
        System.arraycopy(header, 0, big, 0, header.length);
        mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A, photo("huge.png", big, "image/png")).header("Authorization", bearer(citizen)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        assertThat(complaintRepository.count()).isZero();
    }

    @Test
    void validatesRequiredFieldsAndCoordinates() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/complaints")
                        .param("latitude", "0").param("longitude", "0").header("Authorization", bearer(citizen)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.categoryCode").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists())
                .andExpect(jsonPath("$.fieldErrors.latitude").value("Location must be within India"))
                .andExpect(jsonPath("$.fieldErrors.longitude").value("Location must be within India"));

        mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A).param("categoryCode", "NOT_A_CATEGORY").header("Authorization", bearer(citizen)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));
    }

    @Test
    void requiresAuthenticationAndCitizenRole() throws Exception {
        mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A)).andExpect(status().isUnauthorized());
        mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A).header("Authorization", bearer(roadsOfficer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void retryWithSameClientRequestIdDoesNotDuplicate() throws Exception {
        String requestId = UUID.randomUUID().toString();
        JsonNode first = read(mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A, photo("p.png", pngBytes(Color.GRAY), "image/png"))
                        .param("clientRequestId", requestId).header("Authorization", bearer(citizen)))
                .andExpect(status().isCreated()).andReturn());
        JsonNode second = read(mvc.perform(complaintForm(LAT_IN_A, LNG_IN_A, photo("p.png", pngBytes(Color.GRAY), "image/png"))
                        .param("clientRequestId", requestId).header("Authorization", bearer(citizen)))
                .andExpect(status().isOk()).andReturn());
        assertThat(second.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(complaintRepository.count()).isEqualTo(1);
        assertThat(evidenceRepository.count()).isEqualTo(1);
    }

    @Test
    void citizenSeesOwnHistoryAndCanReopenComplaintWithPhotos() throws Exception {
        JsonNode created = createComplaint(citizen, false);
        mvc.perform(get("/api/complaints").param("mine", "true").header("Authorization", bearer(citizen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].referenceNo").value(created.get("referenceNo").asText()))
                .andExpect(jsonPath("$.items[0].photoCount").value(1))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").isNotEmpty());
        mvc.perform(get("/api/complaints/by-reference/" + created.get("referenceNo").asText()).header("Authorization", bearer(citizen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citizenEvidence[0].url").isNotEmpty())
                .andExpect(jsonPath("$.timeline[0].eventType").value("CREATED"));
    }

    private long filesInStorage() throws Exception {
        try (Stream<Path> s = Files.walk(storage.root())) {
            return s.filter(Files::isRegularFile).count();
        }
    }
}
