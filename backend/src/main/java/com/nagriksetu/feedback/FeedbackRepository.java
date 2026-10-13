package com.nagriksetu.feedback;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @Query("select f from Feedback f where f.complaint.id = :complaintId and f.user.id = :userId")
    Optional<Feedback> findByComplaintAndUser(Long complaintId, Long userId);

    @Query("select f from Feedback f where f.complaint.id = :complaintId and f.user.id = f.complaint.reporter.id")
    Optional<Feedback> findReporterFeedback(Long complaintId);
}
