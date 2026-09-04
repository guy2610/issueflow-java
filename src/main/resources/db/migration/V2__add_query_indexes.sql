CREATE INDEX idx_tickets_active_project_assignee_status
    ON tickets (project_id, assignee_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tickets_overdue_due_date
    ON tickets (due_date)
    WHERE deleted_at IS NULL AND due_date IS NOT NULL;

CREATE INDEX idx_comments_ticket_created_at
    ON comments (ticket_id, created_at);

CREATE INDEX idx_mentions_mentioned_user_comment
    ON mentions (mentioned_user_id, comment_id);

CREATE INDEX idx_attachments_ticket_created_at
    ON attachments (ticket_id, created_at);
