package com.nagriksetu.complaint;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolutionRepository extends JpaRepository<Resolution, Long> {

    Optional<Resolution> findFirstByComplaintIdOrderByCreatedAtDesc(Long complaintId);
}
