package com.att.tdp.issueflow.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityType(AuditEntityType entityType);
    List<AuditLog> findByActorUserId(Long actorUserId);
    List<AuditLog> findByAction(AuditAction action);
    List<AuditLog> findByEntityId(Long entityId);
    List<AuditLog> findByActorType(AuditActorType actorType);
}