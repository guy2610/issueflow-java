package com.att.tdp.issueflow.audit;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        AuditAction action,
        AuditEntityType entityType,
        Long entityId,
        Long performedBy,
        AuditActorType actor,
        Instant timestamp,
        String details
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getActorUserId(),
                log.getActorType(),
                log.getCreatedAt(),
                log.getDetails()
        );
    }
}