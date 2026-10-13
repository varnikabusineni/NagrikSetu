package com.nagriksetu.complaint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ComplaintRepository extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {

    Optional<Complaint> findByReferenceNoIgnoreCase(String referenceNo);

    @Query("select c from Complaint c where c.reporter.id = :reporterId and c.clientRequestId = :clientRequestId")
    Optional<Complaint> findByReporterAndClientRequestId(Long reporterId, UUID clientRequestId);

    @Query(value = "select nextval('complaint_reference_seq')", nativeQuery = true)
    long nextReferenceSequence();

    @Query("select c.status, count(c) from Complaint c where c.reporter.id = :reporterId group by c.status")
    List<Object[]> countByStatusForReporter(Long reporterId);

    @Query("select c from Complaint c join fetch c.category where c.reporter.id = :reporterId order by c.updatedAt desc")
    List<Complaint> findRecentlyUpdatedForReporter(Long reporterId, Pageable pageable);

    @Query("""
            select c from Complaint c join fetch c.category
            where c.publicVisible = true and c.duplicateOf is null and c.status not in :excluded and c.createdAt >= :since
            order by (c.upvoteCount + c.followerCount) desc, c.updatedAt desc
            """)
    List<Complaint> findCommunityAttention(Collection<ComplaintStatus> excluded, Instant since, Pageable pageable);

    @Query("""
            select c from Complaint c join fetch c.category
            where c.latitude between :minLat and :maxLat and c.longitude between :minLng and :maxLng
              and c.publicVisible = true and c.duplicateOf is null and c.status not in :excluded and c.createdAt >= :since
            """)
    List<Complaint> findCandidatesInBox(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng,
                                        Collection<ComplaintStatus> excluded, Instant since);

    /** Analytics: duplicates are excluded so they are never counted as independent incidents. */
    @Query("""
            select c.status, count(c) from Complaint c
            where c.publicVisible = true and c.duplicateOf is null group by c.status
            """)
    List<Object[]> communityCountsByStatus();

    @Query("""
            select c.category.name, count(c) from Complaint c
            where c.publicVisible = true and c.duplicateOf is null group by c.category.name order by count(c) desc
            """)
    List<Object[]> communityCountsByCategory();

    @Query("""
            select count(c) from Complaint c
            where c.duplicateOf is null and c.status = com.nagriksetu.complaint.ComplaintStatus.RESOLVED and c.resolvedAt >= :since
            """)
    long countResolvedIndependentSince(Instant since);

    @Modifying
    @Query("update Complaint c set c.upvoteCount = c.upvoteCount + :delta where c.id = :id")
    int adjustUpvotes(Long id, int delta);

    @Modifying
    @Query("update Complaint c set c.followerCount = c.followerCount + :delta where c.id = :id")
    int adjustFollowers(Long id, int delta);
}
