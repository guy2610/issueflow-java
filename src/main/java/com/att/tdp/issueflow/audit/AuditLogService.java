package com.att.tdp.issueflow.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUserAction(
            AuditAction action,
            Long actorUserId,
            AuditEntityType entityType,
            Long entityId,
            String details
    ) {
        record(action, AuditActorType.USER, actorUserId, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystemAction(
            AuditAction action,
            AuditEntityType entityType,
            Long entityId,
            String details
    ) {
        record(action, AuditActorType.SYSTEM, null, entityType, entityId, details);
    }

    private void record(
            AuditAction action,
            AuditActorType actorType,
            Long actorUserId,
            AuditEntityType entityType,
            Long entityId,
            String details
    ) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setActorType(actorType);
        log.setActorUserId(actorUserId);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);

        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getLogs(
            AuditAction action,
            AuditEntityType entityType,
            Long actorUserId
    ) {
        if (action != null) {
            return auditLogRepository.findByAction(action)
                    .stream()
                    .map(AuditLogResponse::from)
                    .toList();
        }

        if (entityType != null) {
            return auditLogRepository.findByEntityType(entityType)
                    .stream()
                    .map(AuditLogResponse::from)
                    .toList();
        }

        if (actorUserId != null) {
            return auditLogRepository.findByActorUserId(actorUserId)
                    .stream()
                    .map(AuditLogResponse::from)
                    .toList();
        }

        return auditLogRepository.findAll()
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}