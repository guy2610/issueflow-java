# IssueFlow Implementation Plan

## Goal

Build a RESTful backend API for IssueFlow, following the provided README API contract and the assignment requirements.

The implementation is based on Java 21, Spring Boot, PostgreSQL, JPA/Hibernate, Spring Security, and Maven.

## Phase 1: Environment and Repository Setup

Status: Completed

Tasks:

- Validate Java 21 installation.
- Validate Docker and Docker Compose.
- Start PostgreSQL using `compose.yml`.
- Run the Spring Boot skeleton.
- Run initial Maven tests.
- Initialize Git repository.
- Push the clean skeleton baseline to GitHub.

## Phase 2: Requirements and Architecture Planning

Status: Completed

Tasks:

- Review the PDF requirements.
- Review the README API table as the implementation contract.
- Identify ambiguous areas.
- Create requirements traceability documentation.
- Create architecture notes.
- Decide on Java/Spring Boot instead of TypeScript/NestJS.
- Define incremental implementation phases.

Key decisions:

- Use Spring Boot and JPA.
- Use service-layer business rules.
- Use DTOs for request/response payloads.
- Use PostgreSQL for persistence.
- Use JWT for authentication.
- Use optimistic locking for ticket and comment updates.
- Use soft delete only for projects and tickets.

## Phase 3: Core Domain Model

Status: Completed

Tasks:

- Add enums for user roles, ticket status, ticket priority, ticket type, audit action, actor type, and entity type.
- Add core JPA entities:
    - User
    - Project
    - Ticket
    - Comment
    - AuditLog
- Add repositories for core entities.
- Add centralized API error handling.

## Phase 4: User and Authentication APIs

Status: Completed

Tasks:

- Implement user creation, fetch, list, update, and delete.
- Add password hashing with BCrypt.
- Add JWT login.
- Add logout with in-memory deny-list.
- Add current-user endpoint.
- Protect all non-public endpoints with Spring Security.
- Externalize JWT configuration.

## Phase 5: Core Project, Ticket, and Comment APIs

Status: Completed

Tasks:

- Implement project CRUD.
- Implement ticket CRUD.
- Implement comment CRUD.
- Enforce ticket lifecycle rules.
- Enforce no updates after DONE.
- Add optimistic locking for tickets and comments.
- Add soft delete for projects and tickets.

## Phase 6: Extended Functional Requirements

Status: Completed

Tasks:

- Add audit logging for state-changing actions.
- Add ticket dependencies.
- Block DONE transition when unresolved blockers exist.
- Add admin-only soft delete restore/list APIs.
- Add mention parsing and retrieval.
- Add auto-assignment by developer workload.
- Add scheduled ticket escalation.
- Add attachment upload/list/download/delete.
- Add CSV export and import.

## Phase 7: Production-Oriented Refinements

Status: Completed

Tasks:

- Refactor attachments from database binary storage to filesystem storage with database metadata.
- Add CurrentUserService so audit logs use the authenticated actor.
- Align routes, status codes, and response payloads with the README API contract.
- Add audit log filter compatibility.
- Document architecture tradeoffs.

## Phase 8: Testing

Status: Completed

Tasks:

- Add integration tests for:
    - authentication
    - ticket lifecycle
    - dependency blocking
    - soft delete and restore
    - mentions
    - auto assignment
    - CSV import
    - attachment upload/download/delete
    - audit actor resolution
    - admin-only access
- Add domain tests for:
    - ticket status transitions
    - priority escalation

## Phase 9: Documentation and Final Verification

Status: Completed

Tasks:

- Add `run.md`.
- Add `prompts.md`.
- Update `README.md` with supporting documentation links.
- Update `docs/architecture-notes.md`.
- Update `docs/requirements-traceability.md`.
- Run full test suite.
- Verify GitHub repository state before submission.

## Final Verification Checklist

Before submission:

- Run `./mvnw clean test`.
- Confirm `git status` is clean.
- Confirm the repository is public.
- Confirm no local build output or uploaded files are tracked.
- Confirm `README.md`, `run.md`, `prompts.md`, and `docs/` are present.
- Submit the public GitHub repository link through the assignment flow.