package com.nagriksetu.complaint;

import static com.nagriksetu.complaint.ComplaintStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nagriksetu.common.ApiException;
import org.junit.jupiter.api.Test;

class ComplaintWorkflowTest {

    @Test
    void followsTheDocumentedLifecycle() {
        assertThat(ComplaintWorkflow.canTransition(SUBMITTED, UNDER_REVIEW)).isTrue();
        assertThat(ComplaintWorkflow.canTransition(UNDER_REVIEW, ASSIGNED)).isTrue();
        assertThat(ComplaintWorkflow.canTransition(ASSIGNED, IN_PROGRESS)).isTrue();
        assertThat(ComplaintWorkflow.canTransition(IN_PROGRESS, RESOLVED)).isTrue();
        assertThat(ComplaintWorkflow.canTransition(RESOLVED, REOPENED)).isTrue();
        assertThat(ComplaintWorkflow.canTransition(REJECTED, REOPENED)).isTrue();
        assertThat(ComplaintWorkflow.canTransition(REOPENED, ASSIGNED)).isTrue();
    }

    @Test
    void forbidsShortcutsAndChangesToClosedComplaints() {
        assertThat(ComplaintWorkflow.canTransition(SUBMITTED, RESOLVED)).isFalse();
        assertThat(ComplaintWorkflow.canTransition(SUBMITTED, IN_PROGRESS)).isFalse();
        assertThat(ComplaintWorkflow.canTransition(UNDER_REVIEW, RESOLVED)).isFalse();
        assertThat(ComplaintWorkflow.canTransition(RESOLVED, IN_PROGRESS)).isFalse();
        assertThat(ComplaintWorkflow.canTransition(REJECTED, RESOLVED)).isFalse();
        assertThat(ComplaintWorkflow.canTransition(RESOLVED, REJECTED)).isFalse();
        assertThatThrownBy(() -> ComplaintWorkflow.requireTransition(SUBMITTED, RESOLVED))
                .isInstanceOf(ApiException.class).hasMessageContaining("cannot move to Resolved");
    }
}
