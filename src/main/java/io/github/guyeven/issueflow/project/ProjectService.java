package io.github.guyeven.issueflow.project;

import io.github.guyeven.issueflow.common.error.BadRequestException;
import io.github.guyeven.issueflow.common.error.NotFoundException;
import io.github.guyeven.issueflow.user.User;
import io.github.guyeven.issueflow.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.guyeven.issueflow.audit.AuditLogService;
import io.github.guyeven.issueflow.audit.AuditAction;
import io.github.guyeven.issueflow.audit.AuditEntityType;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public ProjectService(ProjectRepository projectRepository, UserService userService,AuditLogService auditLogService) {
        this.projectRepository = projectRepository;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        User owner = userService.findUserEntity(request.ownerId());

        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwner(owner);
        Project saved = projectRepository.save(project);

        auditLogService.recordCurrentUserAction(
                AuditAction.CREATE,
                AuditEntityType.PROJECT,
                saved.getId(),
                "Project created"
        );

        return ProjectResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long id) {
        return ProjectResponse.from(findActiveProjectEntity(id));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findByDeletedAtIsNull()
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional
    public ProjectResponse updateProject(Long id, UpdateProjectRequest request) {
        Project project = findActiveProjectEntity(id);

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("Project name cannot be blank");
            }
            project.setName(request.name());
        }

        if (request.description() != null) {
            project.setDescription(request.description());
        }

        Project saved = projectRepository.saveAndFlush(project);
        auditLogService.recordCurrentUserAction(
                AuditAction.UPDATE,
                AuditEntityType.PROJECT,
                saved.getId(),
                "Project updated"
        );
        return ProjectResponse.from(saved);
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = findActiveProjectEntity(id);
        project.softDelete();
        auditLogService.recordCurrentUserAction(
                AuditAction.DELETE,
                AuditEntityType.PROJECT,
                project.getId(),
                "Project soft-deleted"
        );
    }

    public Project findActiveProjectEntity(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));

        if (project.isDeleted()) {
            throw new NotFoundException("Project not found: " + id);
        }

        return project;
    }
    @Transactional(readOnly = true)
    public List<ProjectResponse> getDeletedProjects() {
        return projectRepository.findByDeletedAtIsNotNull()
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional
    public ProjectResponse restoreProject(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));

        if (!project.isDeleted()) {
            return ProjectResponse.from(project);
        }

        project.restore();
        Project saved = projectRepository.saveAndFlush(project);

        auditLogService.recordCurrentUserAction(
                AuditAction.RESTORE,
                AuditEntityType.PROJECT,
                saved.getId(),
                "Project restored"
        );

        return ProjectResponse.from(saved);
    }
}