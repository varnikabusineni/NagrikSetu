package com.nagriksetu.official;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.nagriksetu.support.IntegrationTest;
import com.nagriksetu.user.User;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class WorkflowIntegrationTest extends IntegrationTest {

    private ResultActions action(User who, long complaintId, String action, String body) throws Exception {
        return mvc.perform(post("/api/official/complaints/" + complaintId + "/" + action)
                .header("Authorization", bearer(who)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<String> notificationTypes(User u) {
        return jdbc.queryForList("select type from notifications where user_id = ? order by id", String.class, u.getId());
    }

    @Test
    void fullLifecycleFromSubmissionToFeedback() throws Exception {
        long id = createComplaint(citizen, true).get("id").asLong();

        action(wardHeadA, id, "review", "{}").andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        action(wardHeadA, id, "route", "{\"departmentId\":" + departmentId("ROADS") + ",\"note\":\"Main road\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.departmentName").value("Roads & Transport"));
        action(roadsHead, id, "assign", "{\"officerId\":" + roadsOfficer.getId() + "}")
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedOfficerName").value("Roads Officer"));
        action(roadsOfficer, id, "start", "{\"note\":\"On site\"}").andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mvc.perform(multipart("/api/official/complaints/" + id + "/resolve")
                        .file(photo("after.jpg", jpegBytes(Color.GREEN), "image/jpeg"))
                        .param("summary", "Pothole filled and road surface levelled.")
                        .header("Authorization", bearer(roadsOfficer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionEvidence.length()").value(1))
                .andExpect(jsonPath("$.resolutionEvidence[0].type").value("RESOLUTION"))
                .andExpect(jsonPath("$.citizenEvidence.length()").value(1))
                .andExpect(jsonPath("$.citizenEvidence[0].type").value("CITIZEN_REPORT"));

        JsonNode view = read(mvc.perform(get("/api/complaints/" + id).header("Authorization", bearer(citizen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution.summary").value("Pothole filled and road surface levelled."))
                .andExpect(jsonPath("$.resolution.resolvedByName").doesNotExist())
                .andExpect(jsonPath("$.canGiveFeedback").value(true))
                .andReturn());
        List<String> statuses = new ArrayList<>();
        view.get("timeline").forEach(t -> {
            if (t.get("eventType").asText().equals("STATUS_CHANGE")) {
                statuses.add(t.get("toStatus").asText());
            }
        });
        assertThat(statuses).containsExactly("UNDER_REVIEW", "ASSIGNED", "IN_PROGRESS", "RESOLVED");

        assertThat(notificationTypes(citizen)).contains("COMPLAINT_SUBMITTED", "COMPLAINT_VERIFIED", "DEPARTMENT_ASSIGNED",
                "OFFICER_ASSIGNED", "STATUS_CHANGED", "COMPLAINT_RESOLVED");
        assertThat(notificationTypes(roadsHead)).contains("NEW_ASSIGNMENT");
        assertThat(notificationTypes(roadsOfficer)).contains("NEW_ASSIGNMENT");

        mvc.perform(post("/api/complaints/" + id + "/feedback").header("Authorization", bearer(citizen))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Great work\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/complaints/" + id + "/feedback").header("Authorization", bearer(citizen))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FEEDBACK_EXISTS"));
        mvc.perform(put("/api/complaints/" + id + "/feedback").header("Authorization", bearer(citizen))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"comment\":\"Edited\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(4));
        mvc.perform(post("/api/complaints/" + id + "/feedback").header("Authorization", bearer(otherCitizen))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":1}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from feedback where complaint_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where entity_id = ? and action = 'COMPLAINT_RESOLVED'",
                Integer.class, Long.toString(id))).isEqualTo(1);
    }

    @Test
    void citizensCannotChangeOfficialState() throws Exception {
        long id = createComplaint(citizen, true).get("id").asLong();
        for (String a : List.of("review", "start", "reject", "reopen")) {
            action(citizen, id, a, "{\"reason\":\"I fixed it myself already\"}").andExpect(status().isForbidden());
        }
        action(citizen, id, "route", "{\"departmentId\":1}").andExpect(status().isForbidden());
        mvc.perform(multipart("/api/official/complaints/" + id + "/resolve").param("summary", "Resolved by me, the citizen")
                        .header("Authorization", bearer(citizen)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/official/queue").header("Authorization", bearer(citizen))).andExpect(status().isForbidden());
        mvc.perform(get("/api/complaints/" + id).header("Authorization", bearer(citizen)))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.allowedActions.length()").value(0));
    }

    @Test
    void officialsAreLimitedToTheirScopeAndValidStates() throws Exception {
        long id = createComplaint(citizen, true).get("id").asLong();
        action(wardHeadB, id, "route", "{\"departmentId\":" + departmentId("ROADS") + "}").andExpect(status().isForbidden());
        action(roadsHead, id, "assign", "{\"officerId\":" + roadsOfficer.getId() + "}").andExpect(status().isForbidden());
        action(roadsOfficer, id, "start", "{}").andExpect(status().isForbidden());

        action(wardHeadA, id, "route", "{\"departmentId\":" + departmentId("ROADS") + "}").andExpect(status().isOk());
        // Officer from another department cannot be assigned.
        action(roadsHead, id, "assign", "{\"officerId\":" + sanitationOfficer.getId() + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_OFFICER"));
        action(roadsHead, id, "assign", "{\"officerId\":" + roadsOfficer.getId() + "}").andExpect(status().isOk());
        // Resolving before work starts is not a valid transition.
        mvc.perform(multipart("/api/official/complaints/" + id + "/resolve").param("summary", "Done without starting work")
                        .header("Authorization", bearer(roadsOfficer)))
                .andExpect(status().isForbidden());
        action(sanitationOfficer, id, "start", "{}").andExpect(status().isForbidden());
    }

    @Test
    void rejectionNeedsReasonAndReopenPreservesHistory() throws Exception {
        long id = createComplaint(citizen, true).get("id").asLong();
        action(wardHeadA, id, "reject", "{\"reason\":\"short\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        action(wardHeadA, id, "reject", "{\"reason\":\"Private property: outside municipal jurisdiction.\"}")
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Private property: outside municipal jurisdiction."));
        assertThat(notificationTypes(citizen)).contains("COMPLAINT_REJECTED");

        // Rejected complaints disappear from the public feed but remain visible to the reporter.
        mvc.perform(get("/api/complaints/" + id).header("Authorization", bearer(otherCitizen))).andExpect(status().isNotFound());
        mvc.perform(get("/api/complaints/" + id).header("Authorization", bearer(citizen))).andExpect(status().isOk());

        action(wardHeadA, id, "reopen", "{\"reason\":\"Re-inspection shows it is a public lane.\"}")
                .andExpect(jsonPath("$.status").value("REOPENED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.citizenEvidence.length()").value(1));
        action(wardHeadA, id, "route", "{\"departmentId\":" + departmentId("ROADS") + "}").andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        JsonNode detail = read(mvc.perform(get("/api/complaints/" + id).header("Authorization", bearer(citizen))).andReturn());
        assertThat(detail.get("timeline").size()).isGreaterThanOrEqualTo(5);
        assertThat(notificationTypes(citizen)).contains("COMPLAINT_REOPENED");
    }

    @Test
    void reroutingKeepsComplaintIdentityAndEvidence() throws Exception {
        JsonNode created = createComplaint(citizen, true);
        long id = created.get("id").asLong();
        String evidenceId = created.get("citizenEvidence").get(0).get("id").asText();
        action(wardHeadA, id, "route", "{\"departmentId\":" + departmentId("SANITATION") + "}").andExpect(status().isOk());
        action(wardHeadA, id, "route", "{\"departmentId\":" + departmentId("ROADS") + "}")
                .andExpect(jsonPath("$.referenceNo").value(created.get("referenceNo").asText()))
                .andExpect(jsonPath("$.departmentName").value("Roads & Transport"))
                .andExpect(jsonPath("$.citizenEvidence[0].id").value(evidenceId));
        assertThat(notificationTypes(citizen)).contains("REASSIGNED");
        assertThat(jdbc.queryForObject("select count(*) from assignments where complaint_id = ? and assignment_type = 'DEPARTMENT'",
                Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from assignments where complaint_id = ? and active",
                Integer.class, id)).isEqualTo(2); // ward + current department
    }

    @Test
    void infoRequestAndCitizenResponseWithPhoto() throws Exception {
        long id = createComplaint(citizen, false).get("id").asLong();
        action(wardHeadA, id, "request-info", "{\"message\":\"Please share a wider photo\"}").andExpect(status().isOk());
        assertThat(notificationTypes(citizen)).contains("INFO_REQUESTED");

        mvc.perform(multipart("/api/complaints/" + id + "/responses")
                        .file(photo("wide.png", pngBytes(Color.ORANGE), "image/png"))
                        .param("message", "Wider photo attached")
                        .header("Authorization", bearer(citizen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citizenEvidence.length()").value(2));
        assertThat(notificationTypes(wardHeadA)).contains("CITIZEN_RESPONSE");

        mvc.perform(multipart("/api/complaints/" + id + "/responses").param("message", "Not my complaint")
                        .header("Authorization", bearer(otherCitizen)))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalNotesAreHiddenFromCitizens() throws Exception {
        long id = createComplaint(citizen, true).get("id").asLong();
        action(wardHeadA, id, "updates", "{\"message\":\"Contractor is unreliable\",\"visibleToCitizen\":false}").andExpect(status().isOk());
        action(wardHeadA, id, "updates", "{\"message\":\"Inspection scheduled for Monday\"}").andExpect(status().isOk());
        String citizenView = mvc.perform(get("/api/complaints/" + id).header("Authorization", bearer(citizen)))
                .andReturn().getResponse().getContentAsString();
        assertThat(citizenView).doesNotContain("Contractor is unreliable").contains("Inspection scheduled for Monday");
        String publicView = mvc.perform(get("/api/complaints/" + id).header("Authorization", bearer(otherCitizen)))
                .andReturn().getResponse().getContentAsString();
        assertThat(publicView).doesNotContain("Contractor is unreliable").doesNotContain("Asha Citizen").doesNotContain("citizen@test.in");
        String officialView = mvc.perform(get("/api/complaints/" + id).header("Authorization", bearer(wardHeadA)))
                .andReturn().getResponse().getContentAsString();
        assertThat(officialView).contains("Contractor is unreliable").contains("Asha Citizen");
    }

    @Test
    void upvotesAreUniquePerCitizenAndFollowersGetUpdates() throws Exception {
        long id = createComplaint(citizen, true).get("id").asLong();
        mvc.perform(post("/api/complaints/" + id + "/upvote").header("Authorization", bearer(otherCitizen)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.upvoteCount").value(1));
        mvc.perform(post("/api/complaints/" + id + "/upvote").header("Authorization", bearer(otherCitizen)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_UPVOTED"));
        mvc.perform(post("/api/complaints/" + id + "/upvote").header("Authorization", bearer(citizen)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/complaints/" + id + "/upvote").header("Authorization", bearer(roadsOfficer)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select upvote_count from complaints where id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from upvotes where complaint_id = ?", Integer.class, id)).isEqualTo(1);

        mvc.perform(delete("/api/complaints/" + id + "/upvote").header("Authorization", bearer(otherCitizen)))
                .andExpect(jsonPath("$.upvoteCount").value(0));

        mvc.perform(post("/api/complaints/" + id + "/follow").header("Authorization", bearer(otherCitizen)))
                .andExpect(jsonPath("$.followed").value(true));
        action(wardHeadA, id, "review", "{}").andExpect(status().isOk());
        assertThat(notificationTypes(otherCitizen)).contains("FOLLOWED_UPDATE");
    }

    @Test
    void duplicateFlagsNeedOfficialConfirmationAndAreExcludedFromAnalytics() throws Exception {
        JsonNode original = createComplaint(citizen, true);
        JsonNode dup = createComplaint(otherCitizen, true);
        long dupId = dup.get("id").asLong();

        mvc.perform(get("/api/complaints/nearby").param("lat", LAT_IN_A).param("lng", LNG_IN_A).param("category", "POTHOLE")
                        .header("Authorization", bearer(otherCitizen)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(post("/api/complaints/" + dupId + "/duplicate-flags").header("Authorization", bearer(otherCitizen))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duplicateOfReference\":\"" + original.get("referenceNo").asText() + "\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select duplicate_of_id from complaints where id = ?", Long.class, dupId)).isNull();

        JsonNode flags = read(mvc.perform(get("/api/official/duplicate-flags").header("Authorization", bearer(wardHeadA)))
                .andExpect(jsonPath("$.length()").value(1)).andReturn());
        mvc.perform(get("/api/official/duplicate-flags").header("Authorization", bearer(wardHeadB)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/official/duplicate-flags/" + flags.get(0).get("id").asLong() + "/confirm")
                        .header("Authorization", bearer(wardHeadA)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select duplicate_of_id from complaints where id = ?", Long.class, dupId))
                .isEqualTo(original.get("id").asLong());
        assertThat(notificationTypes(otherCitizen)).contains("DUPLICATE_CONFIRMED");

        mvc.perform(get("/api/dashboard/community").header("Authorization", bearer(citizen)))
                .andExpect(jsonPath("$.byStatus.SUBMITTED").value(1))
                .andExpect(jsonPath("$.openIssues").value(1));
    }

    @Test
    void notificationsCanBeReadOnlyByTheirOwner() throws Exception {
        createComplaint(citizen, true);
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(citizen)))
                .andExpect(jsonPath("$.unread").value(1));
        Long noteId = jdbc.queryForObject("select id from notifications where user_id = ?", Long.class, citizen.getId());
        mvc.perform(post("/api/notifications/" + noteId + "/read").header("Authorization", bearer(otherCitizen)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/" + noteId + "/read").header("Authorization", bearer(citizen)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(citizen)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void dashboardCountsComeFromDatabase() throws Exception {
        long a = createComplaint(citizen, true).get("id").asLong();
        createComplaint(citizen, true);
        long c = createComplaint(citizen, false).get("id").asLong();
        action(wardHeadA, a, "route", "{\"departmentId\":" + departmentId("ROADS") + "}");
        action(roadsHead, a, "assign", "{\"officerId\":" + roadsOfficer.getId() + "}");
        action(wardHeadA, c, "reject", "{\"reason\":\"Duplicate of an existing works order.\"}");
        mvc.perform(get("/api/dashboard/citizen").header("Authorization", bearer(citizen)))
                .andExpect(jsonPath("$.totals.total").value(3))
                .andExpect(jsonPath("$.totals.awaitingReview").value(1))
                .andExpect(jsonPath("$.totals.inProgress").value(1))
                .andExpect(jsonPath("$.totals.resolved").value(0))
                .andExpect(jsonPath("$.totals.rejected").value(1))
                .andExpect(jsonPath("$.recentlyUpdated.length()").value(3));
        mvc.perform(get("/api/dashboard/citizen").header("Authorization", bearer(otherCitizen)))
                .andExpect(jsonPath("$.totals.total").value(0))
                .andExpect(jsonPath("$.communityAttention.length()").value(2));
    }

    @Test
    void adminVerifiesWardForUnmatchedComplaints() throws Exception {
        JsonNode body = read(mvc.perform(complaintForm(LAT_OUTSIDE, LNG_OUTSIDE).header("Authorization", bearer(citizen))).andReturn());
        long id = body.get("id").asLong();
        mvc.perform(get("/api/official/queue").param("view", "unverified").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/official/queue").header("Authorization", bearer(wardHeadA)))
                .andExpect(jsonPath("$.totalItems").value(0));
        action(wardHeadA, id, "verify-ward", "{\"wardId\":" + wardA.getId() + "}").andExpect(status().isForbidden());
        action(admin, id, "verify-ward", "{\"wardId\":" + wardA.getId() + "}")
                .andExpect(jsonPath("$.wardMatch").value("VERIFIED")).andExpect(jsonPath("$.wardName").value("Test Ward A"));
        mvc.perform(get("/api/official/queue").header("Authorization", bearer(wardHeadA)))
                .andExpect(jsonPath("$.totalItems").value(1));
        assertThat(notificationTypes(wardHeadA)).contains("NEW_COMPLAINT");
    }
}
