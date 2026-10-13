package com.nagriksetu.complaint;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query("select a from Assignment a where a.complaint.id = :complaintId and a.type = :type and a.active = true")
    Optional<Assignment> findActive(Long complaintId, Assignment.Type type);

    @Query("select a from Assignment a where a.complaint.id = :complaintId order by a.createdAt asc, a.id asc")
    List<Assignment> findHistory(Long complaintId);
}
