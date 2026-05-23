package com.att.tdp.issueflow.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByDeletedAtIsNull();
    List<Project> findByDeletedAtIsNotNull();
}