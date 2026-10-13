package com.nagriksetu.community;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DuplicateFlagRepository extends JpaRepository<DuplicateFlag, Long> {

    @Query("""
            select f from DuplicateFlag f join fetch f.complaint c join fetch f.duplicateOf d
            where f.status = :status order by f.createdAt asc
            """)
    List<DuplicateFlag> findByStatusWithComplaints(DuplicateFlag.Status status);

    @Query("select count(f) > 0 from DuplicateFlag f where f.complaint.id = :complaintId and f.duplicateOf.id = :duplicateOfId and f.flaggedBy.id = :userId")
    boolean existsFlag(Long complaintId, Long duplicateOfId, Long userId);

    @Query("select f from DuplicateFlag f where f.complaint.id = :complaintId and f.status = :status")
    List<DuplicateFlag> findByComplaintAndStatus(Long complaintId, DuplicateFlag.Status status);
}
