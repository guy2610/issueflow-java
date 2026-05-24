package com.att.tdp.issueflow.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.att.tdp.issueflow.common.security.CurrentUserService;
import com.att.tdp.issueflow.audit.AuditActorType;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            CurrentUserService currentUserService
    ) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
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
            Long entityId,
            String actor,
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

        if (entityId != null) {
            return auditLogRepository.findByEntityId(entityId)
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

        if (actor != null && !actor.isBlank()) {
            if ("SYSTEM".equalsIgnoreCase(actor)) {
                return auditLogRepository.findByActorType(AuditActorType.SYSTEM)
                        .stream()
                        .map(AuditLogResponse::from)
                        .toList();
            }

            try {
                Long parsedActorUserId = Long.parseLong(actor);
                return auditLogRepository.findByActorUserId(parsedActorUserId)
                        .stream()
                        .map(AuditLogResponse::from)
                        .toList();
            } catch (NumberFormatException ex) {
                return List.of();
            }
        }

        return auditLogRepository.findAll()
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCurrentUserAction(
            AuditAction action,
            AuditEntityType entityType,
            Long entityId,
            String details
    ) {
        Long actorUserId = currentUserService.getCurrentUserId().orElse(null);

        if (actorUserId == null) {
            record(action, AuditActorType.SYSTEM, null, entityType, entityId, details);
            return;
        }

        record(action, AuditActorType.USER, actorUserId, entityType, entityId, details);
    }
}