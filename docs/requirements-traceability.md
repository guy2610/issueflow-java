# Requirements Traceability

| Area | Requirement | Status | Implementation Notes |
|---|---|---|---|
| User Management | Register user | Implemented | POST /users |
| User Management | Fetch user by id | Implemented | GET /users/{userId} |
| User Management | Update user | Implemented | POST /users/update/{userId} |
| User Management | Delete user | Implemented | DELETE /users/{userId} |
| User Management | Fetch all users | Implemented | GET /users |
| Authentication | Login with JWT | Implemented | POST /auth/login |
| Authentication | Logout invalidates token | Implemented | In-memory deny-list |
| Authentication | Current user profile | Implemented | GET /auth/me |
| Project Management | CRUD projects | Implemented | Soft delete for DELETE |
| Ticket Management | CRUD tickets | Implemented | Includes lifecycle validation |
| Ticket Management | Prevent simultaneous updates | Implemented | @Version optimistic locking |
| Ticket Management | No update after DONE | Implemented | TicketService validation |
| Ticket Management | Forward-only status lifecycle | Implemented | TicketStatus ordinal check |
| Comment Management | CRUD comments | Implemented | Includes @Version |
| Audit Log | Record state changes | Implemented | AuditLogService |
| Audit Log | Retrieve/filter logs | Implemented | GET /audit-logs |
| Ticket Dependencies | Add/list/remove dependencies | Implemented | /tickets/{id}/dependencies |
| Ticket Dependencies | Block DONE if blockers unresolved | Implemented | TicketService validation |
| Attachments | Upload with size/type validation | Implemented | Filesystem storage + metadata in DB |
| Attachments | Allowed file types only | Implemented | png/jpeg/pdf/plain |
| Ticket Export | Export CSV | Implemented | GET /tickets/export |
| Ticket Import | Import CSV | Implemented | POST /tickets/import |
| Ticket Import | Handle commas/quotes | Implemented | Apache Commons CSV |
| Soft Delete | Tickets/projects soft-deleted | Implemented | deletedAt |
| Soft Delete | Admin deleted list/restore | Implemented | @PreAuthorize ADMIN |
| Mentions | Parse @username | Implemented | Case-insensitive |
| Mentions | Mentioned comments endpoint | Implemented | GET /users/{userId}/mentions |
| Escalation | dueDate escalation | Implemented | Scheduled service |
| Escalation | Idempotent CRITICAL behavior | Implemented | isOverdue handling |
| Auto Assignment | Least-loaded developer | Implemented with assumption | All DEVELOPER users are candidates |
| Workload | Project workload endpoint | Implemented | GET /projects/{projectId}/workload |
| Testing | Relevant tests | Pending | Integration tests next |
| Documentation | run.md | Pending | Final phase |
| Documentation | prompts.md | Pending | Final phase |