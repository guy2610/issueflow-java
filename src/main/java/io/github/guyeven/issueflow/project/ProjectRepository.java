package io.github.guyeven.issueflow.project;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByDeletedAtIsNull();
    List<Project> findByDeletedAtIsNotNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from Project project where project.id = :id")
    Optional<Project> findByIdForDependencyMutation(@Param("id") Long id);
}
