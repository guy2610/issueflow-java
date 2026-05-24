# IssueFlow Run Guide

## Prerequisites

Required tools:

- Java 21
- Docker Desktop / Docker Compose
- Git
- Maven Wrapper included in the project (`./mvnw`)

The project uses:

- Spring Boot 3.4.x
- PostgreSQL via Docker Compose
- Maven Wrapper
- JWT authentication
- Local filesystem storage for uploaded attachments

## 1. Clone the repository

```bash
git clone git@github.com:guy2610/issueflow-java.git
cd issueflow-java
```

## 2. Start PostgreSQL

Start the local PostgreSQL container:

```bash
docker compose up -d
```

Verify that the container is running:

```bash
docker compose ps
```

## 3. Configuration

The application can run with the default local configuration.

JWT configuration supports environment variable overrides:

```bash
export JWT_SECRET="replace-with-a-long-local-secret-at-least-32-bytes"
export JWT_EXPIRATION_SECONDS=14400
```

If these variables are not provided, the application uses local development fallback values from `application.yaml`.

Attachments are stored on the local filesystem under:

```text
uploads/attachments
```

The `uploads/` directory is excluded from Git.

## 4. Build and run tests

Run all tests:

```bash
./mvnw clean test
```

This runs:

- Spring context test
- Integration tests for the main API flows
- Domain tests for ticket status and priority logic

## 5. Run the application

Start the Spring Boot application:

```bash
./mvnw spring-boot:run
```

The API will be available at:

```text
http://localhost:8080
```

You can also run `IssueFlowApplication` directly from IntelliJ.

## 6. Bootstrap a user

Most endpoints require JWT authentication.

Create an initial admin user:

```bash
curl -i -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "username":"admin",
    "email":"admin@example.com",
    "fullName":"Admin User",
    "role":"ADMIN",
    "password":"secret"
  }'
```

`POST /users` is intentionally left unauthenticated to allow local bootstrap of the first user.

In production, user creation would usually be restricted or handled by an admin provisioning flow.
## 7. Login and store JWT token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"secret"}' | jq -r '.accessToken')
```

Verify the token:

```bash
curl -i http://localhost:8080/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

Expected result: `200 OK` with the current user profile.

If `jq` is not installed, call `/auth/login` manually and copy the `accessToken` value.

## 8. Create a project

```bash
curl -i -X POST http://localhost:8080/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name":"IssueFlow Core",
    "description":"Main project",
    "ownerId":1
  }'
```

## 9. Create a ticket

```bash
curl -i -X POST http://localhost:8080/tickets \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title":"First ticket",
    "description":"Implement basic flow",
    "status":"TODO",
    "priority":"MEDIUM",
    "type":"FEATURE",
    "projectId":1,
    "assigneeId":1
  }'
```

Fetch tickets by project:

```bash
curl -i "http://localhost:8080/tickets?projectId=1" \
  -H "Authorization: Bearer $TOKEN"
```
## 10. Add a comment

```bash
curl -i -X POST http://localhost:8080/tickets/1/comments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "content":"Initial investigation comment",
    "authorId":1
  }'
```

## 11. Upload an attachment

Create a small test file:

```bash
echo "hello attachment" > /tmp/issueflow-attachment.txt
```

Upload it:

```bash
curl -i -X POST "http://localhost:8080/tickets/1/attachments?uploadedByUserId=1" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/issueflow-attachment.txt;type=text/plain"
```

List attachments:

```bash
curl -i http://localhost:8080/tickets/1/attachments \
  -H "Authorization: Bearer $TOKEN"
```

Download an attachment:

```bash
curl -i http://localhost:8080/attachments/1 \
  -H "Authorization: Bearer $TOKEN"
```

Allowed attachment types:

- `image/png`
- `image/jpeg`
- `application/pdf`
- `text/plain`

Maximum file size: `10 MB`.
## 12. CSV export

```bash
curl -i "http://localhost:8080/tickets/export?projectId=1" \
  -H "Authorization: Bearer $TOKEN"
```

Save export to a file:

```bash
curl -s "http://localhost:8080/tickets/export?projectId=1" \
  -H "Authorization: Bearer $TOKEN" \
  -o /tmp/tickets-export.csv
```

## 13. CSV import

Create a CSV file:

```bash
cat > /tmp/tickets-import.csv <<'EOF'
id,title,description,status,priority,type,assigneeId
,"CSV ticket","Description with, comma and ""quote""",TODO,LOW,BUG,1
,"Another CSV ticket","Plain description",IN_PROGRESS,MEDIUM,FEATURE,
EOF
```

Import it:

```bash
curl -i -X POST "http://localhost:8080/tickets/import?projectId=1" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/tickets-import.csv;type=text/csv"
```

Expected response example:

```json
{
  "created": 2,
  "failed": 0,
  "errors": []
}
```

The import ignores the CSV `id` column and creates new tickets with database-generated IDs.
## 14. Audit logs

Fetch all audit logs:

```bash
curl -i http://localhost:8080/audit-logs \
  -H "Authorization: Bearer $TOKEN"
```

Filter by action:

```bash
curl -i "http://localhost:8080/audit-logs?action=CREATE" \
  -H "Authorization: Bearer $TOKEN"
```

Filter by entity type:

```bash
curl -i "http://localhost:8080/audit-logs?entityType=TICKET" \
  -H "Authorization: Bearer $TOKEN"
```

## 15. Soft delete and restore

Delete a ticket:

```bash
curl -i -X DELETE http://localhost:8080/tickets/1 \
  -H "Authorization: Bearer $TOKEN"
```

List deleted tickets, admin only:

```bash
curl -i "http://localhost:8080/tickets/deleted?projectId=1" \
  -H "Authorization: Bearer $TOKEN"
```

Restore a ticket, admin only:

```bash
curl -i -X POST http://localhost:8080/tickets/1/restore \
  -H "Authorization: Bearer $TOKEN"
```

Deleted projects can be listed and restored similarly:

```bash
curl -i http://localhost:8080/projects/deleted \
  -H "Authorization: Bearer $TOKEN"
```

```bash
curl -i -X POST http://localhost:8080/projects/1/restore \
  -H "Authorization: Bearer $TOKEN"
```

## 16. Stop the database

```bash
docker compose down
```

To remove database volumes as well:

```bash
docker compose down -v
```

Use `down -v` only when you want to reset local database state.

## Notes

- The API is stateless and protected by JWT.
- `POST /auth/login` and `POST /users` are public.
- All other endpoints require `Authorization: Bearer <token>`.
- Logout uses an in-memory deny-list, suitable for this single-instance assignment setup.
- Attachments are stored on the local filesystem and are not committed to Git.