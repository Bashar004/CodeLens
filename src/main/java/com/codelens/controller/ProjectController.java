package com.codelens.controller;


import com.codelens.dto.GithubImportRequest;
import com.codelens.dto.ProjectResponse;
import com.codelens.model.User;
import com.codelens.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;


    @PostMapping("/upload")
    public ResponseEntity<ProjectResponse> uploadProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String projectName,
            @AuthenticationPrincipal User currentUser
    ) throws IOException {
        return ResponseEntity.ok(projectService.uploadProject(file, projectName, currentUser));

    }

    @PostMapping("/import-github")
    public ResponseEntity<ProjectResponse> importFromGithub(
            @Valid @RequestBody GithubImportRequest request,
            @AuthenticationPrincipal User currentUser
    ) throws GitAPIException,IOException {
        return ResponseEntity.ok(projectService.importFromGithub(request,currentUser));
    }


    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getMyProjects(
            @AuthenticationPrincipal User currentUser
    ){
        return ResponseEntity.ok(
                projectService.getUserProjects(currentUser)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) throws IOException {
        projectService.deleteProject(id, currentUser);
        return ResponseEntity.noContent().build();
    }

}
