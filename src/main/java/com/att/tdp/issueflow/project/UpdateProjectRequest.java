package com.att.tdp.issueflow.project;

public record UpdateProjectRequest(
        String name,
        String description
) {
}