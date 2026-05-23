package com.att.tdp.issueflow.audit;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        AuditAction action,
        AuditActorType actorType,
        Long actorUserId,
        AuditEntityType entityType,
        Long entityId,
        String details,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getActorType(),
                log.getActorUserId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}