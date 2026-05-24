## Architecture Decisions and Tradeoffs

### Attachment Storage

Attachments are stored on the local filesystem under `uploads/attachments`, while PostgreSQL stores only metadata such as original file name, stored file name, content type, size, and storage path.

This avoids storing large binary payloads directly in the database and more closely resembles a production setup where files would usually be stored in object storage such as S3, with the database holding references and metadata.

For this assignment, local filesystem storage keeps the project simple to run without external infrastructure.

The `uploads/` directory is excluded from Git.

### Authentication and JWT Configuration

The API uses JWT-based stateless authentication through Spring Security.

JWT secret and expiration are configured externally through `application.yaml`, with environment variable fallback support. This avoids hardcoding secrets directly in application code and makes local configuration easier to override.

`POST /auth/login` and `POST /users` are intentionally left unauthenticated. `POST /users` allows bootstrapping the first user in a fresh local environment. In a production deployment, user creation would usually be restricted or handled through a provisioning flow.

### JWT Logout

Logout uses an in-memory token deny-list.

This satisfies the assignment requirement for token invalidation in a single-instance local setup.

In production, this would need Redis or another shared store if the service runs across multiple instances.

### Audit Actor Resolution

Audit logs use the authenticated user from Spring Security's `SecurityContext` for user-triggered actions.

A dedicated `CurrentUserService` reads the current principal and allows `AuditLogService` to record the real actor instead of guessing based on ticket assignee, project owner, or request body fields.

System-driven actions, such as auto-assignment and scheduled escalation, use `actorType = SYSTEM`.

The audit log is append-only from the API perspective.

### Optimistic Locking

Tickets and comments use JPA `@Version` fields.

This supports the requirement that two users should not update the same ticket or comment simultaneously. If concurrent updates conflict, the API returns a conflict response through the global exception handler.

### Soft Delete

Projects and tickets are soft-deleted using a `deletedAt` timestamp.

Standard GET/list endpoints hide soft-deleted records. Admin-only endpoints expose deleted records and allow restoring them.

Users, comments, attachments, mentions, dependencies, and audit records are not soft-deleted because the assignment only requires soft delete for projects and tickets.

### Auto Assignment Scope

The requirements mention assigning tickets to the least-loaded DEVELOPER in the project, but the provided API contract does not define project membership management.

Because of that, the implementation treats all users with role `DEVELOPER` as assignment candidates and calculates their workload within the target project.

Ties are resolved by user creation order, matching the requirement to prefer the oldest registrant.

### Scheduled Escalation

Ticket escalation is implemented as a scheduled service.

The core escalation logic is kept in a callable service method rather than only inside the scheduler, making it easier to test and reason about.

Escalation changes ticket priority only. It does not change ticket status.

When a ticket reaches `CRITICAL` and remains overdue, `isOverdue` is set to true. Repeated scheduler runs are idempotent for already-overdue critical tickets.

### CSV Import

CSV export includes the requested fields: `id`, `title`, `description`, `status`, `priority`, `type`, and `assigneeId`.

CSV import ignores the `id` column and creates new tickets with database-generated IDs. This is intentional because imported tickets are treated as new records in the target project.

Apache Commons CSV is used to correctly handle commas and quotes inside field values.

### Error Handling

The API uses a global `@ControllerAdvice` to return consistent error responses.

Validation errors, missing resources, conflicts, unauthorized requests, forbidden requests, and unexpected server errors are mapped to structured JSON responses.

### AI Usage

AI was used as an implementation assistant for planning, code generation, debugging, and documentation drafting.

The code was reviewed and tested incrementally, and each major feature was committed separately to keep the implementation traceable.