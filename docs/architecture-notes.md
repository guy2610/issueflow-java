## Architecture Decisions and Tradeoffs

### Attachment Storage

Attachments are stored on the local filesystem under `uploads/attachments`, while PostgreSQL stores only metadata such as original file name, stored file name, content type, size, and storage path.

This approach avoids storing large binary payloads directly in the database and more closely resembles a production setup where files would usually be stored in object storage such as S3, with the database holding references and metadata.

For this assignment, local filesystem storage keeps the project simple to run without external infrastructure.

The `uploads/` directory is excluded from Git.

### Auto Assignment Scope

The requirements mention assigning tickets to the least-loaded DEVELOPER in the project, but the provided API contract does not define project membership management.

Because of that, the implementation treats all users with role `DEVELOPER` as assignment candidates and calculates their workload within the target project.

### CSV Import

CSV import ignores the `id` column and creates new tickets with database-generated IDs. This is intentional because imported tickets are treated as new records in the target project.

### JWT Logout

Logout uses an in-memory token deny-list. This satisfies the assignment requirement for token invalidation in a single-instance local setup.

In production, this would need Redis or another shared store if the service runs across multiple instances.

### Audit Actor

Most user-facing actions are recorded in the audit log. Some system-driven actions, such as auto-assignment and scheduled escalation, use `actorType = SYSTEM`.

The audit log is append-only from the API perspective.