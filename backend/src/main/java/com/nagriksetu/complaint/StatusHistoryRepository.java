package com.nagriksetu.complaint;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    @Query("select h from StatusHistory h left join fetch h.actor a left join fetch a.role where h.complaint.id = :complaintId order by h.createdAt asc, h.id asc")
    List<StatusHistory> findTimeline(Long complaintId);
}
