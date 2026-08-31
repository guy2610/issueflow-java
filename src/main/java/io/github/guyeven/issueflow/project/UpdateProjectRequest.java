package io.github.guyeven.issueflow.project;

public record UpdateProjectRequest(
        String name,
        String description
) {
}