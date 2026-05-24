# Project Instructions

This file documents the working instructions followed during the implementation of the IssueFlow assignment.

## Implementation Principles

- Use the README API table as the implementation contract.
- Prefer Java 21 with Spring Boot and PostgreSQL, based on the provided skeleton.
- Implement the assignment incrementally, with small Git commits after each stable phase.
- Keep controllers thin and place business rules in service classes.
- Use DTOs for request and response payloads.
- Use JPA repositories for persistence.
- Use validation annotations and centralized error handling.
- Protect API endpoints with JWT authentication.
- Use optimistic locking for entities with concurrent update requirements.
- Keep assignment-specific assumptions documented in `docs/architecture-notes.md`.
- Keep requirement coverage documented in `docs/requirements-traceability.md`.
- Run tests before final submission.

## AI Usage Instructions

AI was used as a technical implementation assistant under developer direction, not as an unchecked code generator.

The AI workflow followed these rules:

- First analyze requirements and risks before implementing.
- Break work into small phases.
- Generate implementation suggestions only for the current phase.
- Review and run the generated code locally.
- Fix compile, runtime, and test failures before moving forward.
- Commit only stable changes.
- Document meaningful design decisions and tradeoffs.

## Verification Requirements

Before submission:

- `./mvnw clean test` must pass.
- `git status` must be clean.
- The repository must not include generated files, uploaded attachments, local build outputs, or secrets.
- `run.md`, `prompts.md`, `docs/architecture-notes.md`, and `docs/requirements-traceability.md` must be present and up to date.
