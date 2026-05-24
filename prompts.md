# AI Usage and Representative Prompts

## Model Used

The main AI model used during this assignment was:

- GPT-5.5 Thinking

I used AI as a technical implementation assistant throughout the assignment. My role was to define the architecture, choose the implementation strategy, review the generated code, run and debug the application locally, and decide when a solution was good enough or needed refinement.

The project was implemented incrementally, with small commits after each stable phase.

---

## Development Approach

I approached the assignment as a production-oriented backend project rather than as a single large code generation task.

To keep the work controlled, I split the implementation into phases:

- Requirements analysis and traceability
- Environment setup and Spring Boot validation
- Domain model and persistence design
- Authentication and authorization
- Core CRUD APIs
- Business rules and consistency constraints
- Extended platform features
- Production-oriented refinements
- Integration and domain testing
- Documentation and final verification

AI helped accelerate boilerplate, implementation details, debugging, and documentation. The design direction, tradeoffs, validation process, and final decisions were handled by me.

---

## Representative Prompts and Usage

### 1. Requirements Decomposition and Execution Plan

#### Prompt

> Analyze the IssueFlow backend assignment and break it into implementation phases. Identify the dependencies between features, separate core requirements from extended requirements, and highlight ambiguous areas that require an explicit design decision before implementation.

Constraints:

- Use the README API table as the implementation contract
- Work in small Git commits
- Maintain a requirements traceability document
- Avoid jumping into code before the domain model and API contract are understood

#### Outcome

I used the output to create a staged implementation plan and a requirements traceability document. This helped keep the work organized and made it easier to verify what was implemented, pending, or implemented with assumptions.

---

### 2. Stack Selection

#### Prompt

> Evaluate the Java/Spring Boot and TypeScript/NestJS options for this assignment. Since I have stronger familiarity with Java and want to minimize delivery risk, compare the options in terms of persistence, validation, authentication, transactions, optimistic locking, and testing.

Recommend the lower-risk stack for building a complete backend within the assignment timeframe.

#### Outcome

I chose Java 21 with Spring Boot. This was a deliberate risk-reduction decision because Spring Boot provides strong support for REST APIs, JPA/Hibernate, Spring Security, validation, transactions, and integration testing.

---

### 3. Domain Model and Persistence Design

#### Prompt

> Design the core persistence model for users, projects, tickets, comments, audit logs, dependencies, attachments, and mentions.

Implementation constraints:

- Use JPA entities and repositories
- Use enums for constrained values
- Keep business rules out of controllers
- Add optimistic locking where concurrent updates are explicitly required
- Apply soft delete only to the entities required by the assignment
- Keep the design simple enough for the assignment but structured enough to explain in a technical interview

#### Outcome

The domain model was implemented with JPA entities, repositories, DTOs, and service-layer business logic. Tickets and comments use `@Version` for optimistic locking. Projects and tickets use `deletedAt` for soft delete.

---

### 4. Authentication and Security

#### Prompt

> Implement JWT authentication for a Spring Boot REST API.

Requirements:

- Add login, logout, and current-user endpoints
- Store passwords with BCrypt
- Use stateless Spring Security
- Implement a JWT filter that reads Bearer tokens and populates the SecurityContext
- Keep `/auth/login` and initial user creation public
- Protect the rest of the API
- Externalize JWT secret and expiration through configuration

#### Outcome

JWT authentication was implemented with Spring Security. Passwords are hashed with BCrypt. JWT settings are loaded from `application.yaml` with environment variable fallback. Logout uses an in-memory token deny-list, which is sufficient for this single-instance assignment setup.

---

### 5. Ticket Lifecycle and Optimistic Locking

#### Prompt

> Implement ticket update rules with explicit consistency guarantees.

Business constraints:

- Status can only move forward:
    - TODO → IN_PROGRESS → IN_REVIEW → DONE
- A DONE ticket cannot be updated
- Two users should not update the same ticket simultaneously
- Invalid transitions should return informative API errors
- The lifecycle logic should be centralized and testable

#### Outcome

The ticket lifecycle rules were implemented in the service layer. `TicketStatus` owns the forward-transition check, and the service enforces the DONE lock rule. JPA `@Version` provides optimistic locking for concurrent ticket updates.

---

### 6. Audit Log and Actor Resolution

#### Prompt

> Design an audit log mechanism for state-changing actions.

Requirements:

- Persist audit records
- Support both user actions and system actions
- Record automatic actions such as auto-assignment and scheduled escalation
- Avoid guessing the actor from unrelated fields such as assignee, owner, or author
- Use the authenticated principal from Spring Security wherever possible

#### Outcome

I implemented an `AuditLogService` and later refined the design with `CurrentUserService`. User-triggered actions now use the authenticated user from the `SecurityContext`. System-triggered actions use `actorType = SYSTEM`.

This made the audit trail more accurate and closer to a real backend design.

---

### 7. Ticket Dependencies

#### Prompt

> Implement ticket dependencies with validation and lifecycle enforcement.

Requirements:

- Add, list, and remove dependencies
- Prevent a ticket from depending on itself
- Ensure both tickets belong to the same project
- Prevent transition to DONE while unresolved blockers exist
- Avoid circular service dependencies in Spring

#### Outcome

Ticket dependencies were implemented with a dedicated entity, repository, service, and controller. The ticket update flow checks unresolved blockers before allowing a transition to DONE.

---

### 8. Mentions in Comments

#### Prompt

> Implement @username mentions in comments.

Requirements:

- Parse mentions from comment content
- Match usernames case-insensitively
- Persist mention associations
- Include mentioned users in comment responses
- Re-evaluate mentions when comments are updated
- Provide an endpoint for retrieving comments where a user was mentioned

#### Outcome

Mentions were implemented as persisted associations between comments and users. Comment responses include `mentionedUsers`. Mention parsing was later adjusted to support common username characters such as dashes.

---

### 9. Auto Assignment by Workload

#### Prompt

> Implement automatic assignment when a ticket is created without an assignee.

Business rules:

- Only DEVELOPER users are eligible
- Workload is the count of non-DONE tickets assigned to a developer in the target project
- Choose the developer with the lowest workload
- Break ties by oldest registration
- Record the action in the audit log as a system action
- Do not introduce project membership APIs unless the contract defines them

#### Outcome

Auto-assignment was implemented using the available user and ticket model. Since the API contract does not define project membership, all DEVELOPER users are considered candidates, and their workload is calculated within the target project. This assumption is documented in the architecture notes.

---

### 10. Scheduled Escalation

#### Prompt

> Implement scheduled escalation for overdue tickets.

Requirements:

- Tickets may have an optional due date
- Overdue unresolved tickets should be promoted one priority level at a time
- CRITICAL tickets should not escalate further
- If a CRITICAL ticket remains overdue, set `isOverdue = true`
- The scheduler should be idempotent
- The escalation logic should be testable outside the scheduler callback
- Record system audit logs

#### Outcome

A scheduled escalation service was implemented. The core escalation logic is exposed through a callable service method, which keeps it easier to test and reason about. Escalation changes priority only and does not modify ticket status.

---

### 11. Attachment Storage Refactor

#### Prompt

> Review the attachment storage design. The first implementation stores binary file data in the database. Refactor it toward a cleaner storage design while keeping the project easy to run locally.

Constraints:

- Validate max file size and allowed content types
- Store files under a configured local upload directory
- Store metadata and file paths in PostgreSQL
- Exclude uploaded files from Git
- Keep upload, list, download, and delete flows working

#### Outcome

I changed the attachment design from database binary storage to filesystem storage with PostgreSQL metadata. This keeps the assignment simple to run locally while better matching how production systems usually store files using object storage and metadata references.

---

### 12. CSV Import and Export

#### Prompt

> Implement ticket CSV export and import.

Requirements:

- Export project tickets with:
    - id
    - title
    - description
    - status
    - priority
    - type
    - assigneeId
- Import tickets from a multipart CSV file into a target project
- Return a summary with created count, failed count, and row-level errors
- Correctly handle commas and quotes inside field values
- Use Apache Commons CSV rather than manual parsing
- Treat imported rows as new tickets with generated database IDs

#### Outcome

CSV import/export was implemented with Apache Commons CSV. Import ignores the CSV `id` column and creates new tickets in the target project. Malformed CSV input is handled as a client error instead of becoming a generic server failure.

---

### 13. Error Handling

#### Prompt

> Implement consistent API error handling.

Requirements:

- Use a global exception handler
- Return structured JSON errors
- Map validation errors, not-found errors, conflicts, forbidden access, unauthorized access, and unexpected failures
- Do not expose internal exception details to API clients
- Log unexpected server errors for debugging

#### Outcome

A global `@ControllerAdvice` was added. Domain and framework exceptions are mapped to structured API errors. Access-denied failures from method security return `403` instead of `500`.

---

### 14. Testing Strategy

#### Prompt

> Design a test strategy for this Spring Boot assignment.

Priorities:

- Focus on high-value integration tests because the main risks are API wiring, security, JPA, transactions, multipart upload, CSV parsing, and business rules
- Add small domain tests only where the logic is pure and valuable to isolate
- Avoid excessive mocking that would not prove the application works end-to-end
- Keep tests readable and close to real API behavior

#### Outcome

Integration tests were added for authentication, ticket lifecycle rules, dependency blocking, soft delete and restore, mentions, auto-assignment, CSV import, attachment upload/download/delete, audit actor resolution, and admin-only access. Domain tests cover ticket status transitions and priority escalation.

---

### 15. Debugging and Refinement

#### Prompt

> Analyze failing tests and identify whether each issue is caused by the test, the implementation, or the API behavior. Do not weaken assertions to hide real bugs. Fix root causes and keep the final test suite clean.

Issues investigated:

- Mentions returned no mentioned users
- CSV import returned 500
- Admin-only endpoints returned 500 instead of 403
- A domain test was accidentally placed under `src/main/java` instead of `src/test/java`

#### Outcome

The mention regex was updated. CSV input construction in the test was corrected. CSV parsing errors were mapped more cleanly. `AccessDeniedException` is now handled as `403`. Test files were moved to the correct source tree.

---

## Key Engineering Decisions I Made

These were the main design and tradeoff decisions I made while using AI as an implementation assistant:

- Stack strategy:
    - I chose Java/Spring Boot over NestJS to reduce delivery risk and use a stack that fits the assignment’s persistence, validation, authentication, and testing needs.

- Incremental delivery:
    - I worked in small Git commits so each feature could be verified independently.

- Requirements tracking:
    - I created a requirements traceability document to track coverage and assumptions.

- Concurrency:
    - I used optimistic locking with JPA `@Version` for tickets and comments because the assignment explicitly mentions simultaneous updates.

- Soft delete scope:
    - I limited soft delete to projects and tickets because those are the entities required by the assignment.

- Auto-assignment assumption:
    - I treated all DEVELOPER users as candidates because the provided API does not define project membership.

- Attachment storage:
    - I changed the initial attachment design from database binary storage to filesystem storage with PostgreSQL metadata.

- Audit accuracy:
    - I added `CurrentUserService` so audit records use the authenticated user instead of inferred actors.

- Testing strategy:
    - I prioritized integration tests over broad mock-based unit tests because most risk was in the full Spring/JPA/Security/API behavior.

- Documentation:
    - I documented assumptions and tradeoffs rather than hiding ambiguous parts of the requirements.

---

## Verification Performed

I verified the project through:

- Maven builds with `./mvnw clean test`
- Local PostgreSQL through Docker Compose
- Manual curl smoke tests for:
    - authentication
    - users
    - projects
    - tickets
    - comments
    - attachments
    - CSV import/export
    - dependencies
    - escalation
    - audit logs
- Integration tests for key API flows
- Domain tests for pure ticket logic
- Git status checks before commits
- Incremental Git commits and pushes after stable phases

---

## Accountability

I reviewed, ran, tested, and committed the final implementation myself. AI helped accelerate the work, but the submitted code and design decisions are my responsibility.