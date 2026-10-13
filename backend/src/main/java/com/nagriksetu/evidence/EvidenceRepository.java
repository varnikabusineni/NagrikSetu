package com.nagriksetu.evidence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    @Query("select e from Evidence e join fetch e.uploader u join fetch u.role where e.complaint.id = :complaintId order by e.createdAt asc")
    List<Evidence> findByComplaint(Long complaintId);

    @Query("""
            select e from Evidence e where e.complaint.id in :complaintIds and e.type = :type
            order by e.complaint.id, e.createdAt asc
            """)
    List<Evidence> findByComplaintsAndType(Collection<Long> complaintIds, EvidenceType type);

    @Query("select e from Evidence e join fetch e.complaint where e.id = :id")
    Optional<Evidence> findWithComplaint(UUID id);

    boolean existsByStorageKey(String storageKey);

    @Query("select e.storageKey from Evidence e where e.storageKey in :keys")
    List<String> findExistingKeys(Collection<String> keys);
}
