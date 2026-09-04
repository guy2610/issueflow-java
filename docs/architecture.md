# IssueFlow architecture

This document describes the design that is implemented today, including its deliberate boundaries and current limitations.

## Architecture choice

IssueFlow is a package-by-feature modular monolith. It runs as one Spring Boot application and uses one relational database. The source tree separates authentication, users, projects, tickets, comments, mentions, attachments, and auditing into feature packages; scheduled escalation remains part of the `ticket` feature.

This shape keeps domain operations local and transactional without introducing distributed-system concerns that the current product does not need. Package boundaries provide navigability and a practical route to stronger module enforcement later if the system grows.

Within a feature, the typical request path is:

1. a controller accepts and validates an HTTP request;
2. a service resolves identities and enforces domain rules inside a transaction;
3. a Spring Data repository reads or writes JPA entities; and
4. response DTOs expose the resulting API representation.

Cross-cutting packages provide authenticated-principal resolution, exception translation, security configuration, and password encoding.

## Domain boundaries

The user feature owns identities, the `ADMIN` and `DEVELOPER` roles, and first-admin bootstrap. Projects own their basic metadata and owner relationship; IssueFlow does not currently model project membership. Tickets belong to projects and own their workflow state, priority, type, assignee, due date, dependencies, CSV workflows, workload assignment, scheduled escalation, and soft-deletion state.

Comments belong to tickets, with mentions persisted as separate records after usernames are resolved from comment content. Attachments also belong to tickets, but their database records contain metadata and storage paths rather than binary content. Audit records refer to affected entities by type and identifier instead of becoming part of each domain aggregate.

These are logical boundaries inside one persistence model, not independently deployed services.

PostgreSQL 16 is the persistence target. Flyway owns the versioned schema, and Hibernate validates the entity mappings against that schema rather than creating or updating it. Integration tests use PostgreSQL Testcontainers so migrations, relational constraints, and locking behavior run against the production database engine; H2 is not used.

## Transaction boundaries

Mutation services use Spring transactions as the consistency boundary. Validation, entity changes, and normal domain audit records participate in the same transaction. If the business mutation fails, its successful domain audit entry is rolled back with it.

Authentication events are intentionally distinct. Successful login uses the security-event audit path, which starts an independent transaction so the event can survive a surrounding transaction failure. This separate behavior is limited to authentication/security recording rather than normal ticket, project, comment, or attachment mutations.

CSV import reports a result for each parsed row and continues after row-level parsing or validation failures. The import method currently has one surrounding service transaction; it should not be interpreted as independent commit isolation for every row.

Filesystem writes are not covered by the database transaction. PostgreSQL metadata and local file content therefore do not have a shared atomic commit.

## Security model

Spring Security authenticates requests with stateless JWT bearer tokens. Passwords are encoded with BCrypt, and logout deny-lists tokens in application memory until their expiry.

The service resolves the acting user from the authenticated principal. Comment authors and attachment uploaders therefore cannot be selected through request-supplied user identifiers.

Public registration is restricted to developers. Creating an administrator through the user API requires an authenticated administrator. Administrative user operations, audit-log access, and restoration of soft-deleted projects and tickets are role-protected. General project and ticket operations require authentication but do not currently implement project-membership ACLs.

For a new installation, an administrator can be bootstrapped only when all three `ISSUEFLOW_BOOTSTRAP_ADMIN_*` settings are explicitly provided and no administrator already exists. Incomplete bootstrap configuration is ignored, and the password is processed by the same validation and BCrypt infrastructure as API-created users.

## Ticket lifecycle

Tickets enter the system in `TODO`, whether created through the JSON API or CSV import. Status transitions are monotonic according to the enum order: a status may remain unchanged or move forward, but cannot move backward. `DONE` is terminal, so subsequent ticket mutations are rejected.

Explicit assignees must be developers. Automatic assignment chooses among developers by counting their active tickets in the target project and selecting the smallest workload, with deterministic tie-breaking.

JPA `@Version` fields on tickets and comments provide persistence-level optimistic-lock detection. The current HTTP representations do not expose those versions and do not require conditional requests, so this is concurrency groundwork rather than a complete client-visible optimistic-concurrency protocol.

## Dependency semantics

A dependency states that one ticket is blocked by another. Both tickets must belong to the same project; self-dependencies and duplicate edges are rejected. Before a ticket enters `DONE`, each direct blocker must already be complete.

The current implementation validates direct relationships only. It does not traverse the dependency structure to reject transitive cycles, so it should not be treated as a complete graph-consistency engine.

## Audit semantics

Audit records identify an action, entity type, entity identifier, timestamp, and actor. `USER` represents API-originated activity and includes the authenticated user ID when one exists. Public operations such as developer registration can therefore produce a `USER` record without an actor user ID. `SYSTEM` represents automated activity such as scheduled escalation. This distinction makes automated mutations visible without fabricating a human identity.

Audit history is append-oriented and readable only through the administrator-protected audit API. It is an application audit trail, not an immutable external compliance ledger.

## Workload assignment

Manual assignment validates that the selected user is a developer. Automatic assignment evaluates developers against active ticket counts for the project and uses creation order to resolve equal workloads. The algorithm is intentionally understandable and deterministic; it does not attempt skills matching, capacity calendars, or organization-wide scheduling.

## Scheduled escalation

A scheduled service queries overdue tickets that are not complete. Each run raises priority by one level. A ticket already at `CRITICAL` is marked overdue rather than repeatedly mutated, making subsequent scheduler runs stable for that state. Scheduler changes are recorded as system audit events.

## Attachment storage tradeoff

Attachment metadata is stored relationally while file content is written beneath a configurable local upload directory. Generated storage names avoid trusting client filenames as paths, while the original filename and declared content type remain metadata.

This design is suitable for a single application instance and keeps the database lean. It does not provide shared object storage, content scanning, or atomic coordination between database and filesystem commits.

## Current limitations

- Dependency validation does not prevent transitive cycles.
- Entity versions are not surfaced through an HTTP conditional-update contract.
- JWT logout state and attachment content are local to one application instance.
- Authorization is role-based and does not yet restrict resources by project membership.
