package com.nagriksetu.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, String entityId);

    List<AuditEvent> findByAction(String action);

    Page<AuditEvent> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<AuditEvent> findByActionOrderByCreatedAtDescIdDesc(String action, Pageable pageable);
}
