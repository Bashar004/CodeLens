package com.codelens.service;

import com.codelens.dto.GithubImportRequest;
import com.codelens.dto.ProjectResponse;
import com.codelens.model.Project;
import com.codelens.model.User;
import com.codelens.repository.CodeUnitRepository;
import com.codelens.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final CodeUnitRepository codeUnitRepository;  // ✅ Added

    @Value("${codelens.upload.path}")
    private String uploadBasePath;

    // ── Upload ZIP ────────────────────────────────────────────
    public ProjectResponse uploadProject(
            MultipartFile file,
            String projectName,
            User currentUser
    ) throws IOException {

        validateZipFile(file);

        if (projectRepository.existsByNameAndOwner(projectName, currentUser)) {
            throw new RuntimeException(
                    "Project name '" + projectName + "' already exists");
        }

        Path projectPath = createProjectDirectory(currentUser);

        AtomicInteger fileCount = new AtomicInteger(0);
        extractZip(file, projectPath, fileCount);

        return saveAndReturn(
                projectName,
                projectPath,
                file.getSize(),
                fileCount.get(),
                currentUser,
                "ZIP"
        );
    }

    // ── Import from GitHub ────────────────────────────────────
    public ProjectResponse importFromGithub(
            GithubImportRequest request,
            User currentUser
    ) throws GitAPIException, IOException {

        if (projectRepository.existsByNameAndOwner(
                request.getProjectName(), currentUser)) {
            throw new RuntimeException(
                    "Project name '" + request.getProjectName() + "' already exists");
        }

        Path projectPath = createProjectDirectory(currentUser);

        log.info("Cloning GitHub repo: {}", request.getRepoUrl());

        // ✅ Use try-with-resources → auto-closes JGit and releases ALL file locks
        try (Git git = Git.cloneRepository()
                .setURI(request.getRepoUrl())
                .setDirectory(projectPath.toFile())
                .call()) {

            // Git object is closed here automatically → file locks released
            log.info("Clone complete for: {}", request.getRepoUrl());

        } catch (GitAPIException e) {
            deleteDirectory(projectPath.toFile());
            throw new RuntimeException(
                    "Failed to clone repository: " + e.getMessage());
        }

        int javaFileCount = countJavaFiles(projectPath);
        long totalSize    = getFolderSize(projectPath);

        return saveAndReturn(
                request.getProjectName(),
                projectPath,
                totalSize,
                javaFileCount,
                currentUser,
                "GITHUB"
        );
    }

    // ── Get All Projects for Current User ─────────────────────
    public List<ProjectResponse> getUserProjects(User currentUser) {
        return projectRepository.findByOwner(currentUser)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ── Delete Project ────────────────────────────────────────
    @Transactional                                         // ✅ Added
    public void deleteProject(Long projectId, User currentUser) throws IOException {

        // Step 1: Find project and verify ownership
        Project project = projectRepository
                .findByIdAndOwner(projectId, currentUser)
                .orElseThrow(() ->
                        new RuntimeException("Project not found or access denied"));

        // Step 2: Delete files from disk (only if uploads not yet deleted by parser)
        Path projectPath = Paths.get(project.getStoragePath());
        if (Files.exists(projectPath)) {
            deleteDirectory(projectPath.toFile());
            log.info("🗑️ Deleted upload folder for project '{}'", project.getName());
        }

        // Step 3: Delete code_units FIRST (child rows — FK constraint)  ✅ Added
        codeUnitRepository.deleteAllByProjectId(projectId);
        log.info("🗑️ Deleted code units for project '{}'", project.getName());

        // Step 4: Now safe to delete the project (parent row)
        projectRepository.delete(project);

        log.info("✅ Project '{}' deleted by '{}'",
                project.getName(), currentUser.getEmail());
    }

    // ── Private Helpers ───────────────────────────────────────

    private Path createProjectDirectory(User user) throws IOException {
        String uniqueFolder = user.getId() + "_" + UUID.randomUUID();
        Path path = Paths.get(uploadBasePath, uniqueFolder);
        Files.createDirectories(path);
        return path;
    }

    private ProjectResponse saveAndReturn(
            String name,
            Path path,
            long size,
            int fileCount,
            User owner,
            String source
    ) {
        Project project = Project.builder()
                .name(name)
                .storagePath(path.toString())
                .status("UPLOADED")
                .fileSize(size)
                .totalFiles(fileCount)
                .uploadedAt(LocalDateTime.now())
                .owner(owner)
                .build();

        Project saved = projectRepository.save(project);

        log.info("Project '{}' saved from {} — {} Java files found",
                name, source, fileCount);

        return mapToResponse(saved);
    }

    private ProjectResponse mapToResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .status(p.getStatus())
                .fileSize(p.getFileSize())
                .totalFiles(p.getTotalFiles())
                .uploadedAt(p.getUploadedAt())
                .message("Success")
                .build();
    }

    private void validateZipFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new RuntimeException("Only ZIP files are allowed");
        }

        long maxSize = 50L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new RuntimeException("File size exceeds 50MB limit");
        }
    }

    private void extractZip(
            MultipartFile zipFile,
            Path targetDir,
            AtomicInteger javaFileCount
    ) throws IOException {

        try (ZipInputStream zis =
                     new ZipInputStream(zipFile.getInputStream())) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                Path entryPath = targetDir
                        .resolve(entry.getName())
                        .normalize();

                if (!entryPath.startsWith(targetDir)) {
                    throw new RuntimeException(
                            "Security violation: invalid ZIP entry — "
                                    + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath,
                            StandardCopyOption.REPLACE_EXISTING);

                    if (entry.getName().endsWith(".java")) {
                        javaFileCount.incrementAndGet();
                    }
                }

                zis.closeEntry();
            }
        }
    }

    private int countJavaFiles(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            return (int) stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();
        }
    }

    private long getFolderSize(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        }
    }

    // ✅ Fixed — NIO API instead of old File API
    private void deleteDirectory(File dir) {
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs)
                        throws IOException {
                    // ✅ Force writable BEFORE delete — fixes Windows .git lock
                    file.toFile().setWritable(true, false);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file,
                                                       IOException exc)
                        throws IOException {
                    // ✅ If visiting failed — force-writable then retry delete
                    file.toFile().setWritable(true, false);
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        log.warn("⚠️ Could not delete file: {}", file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory,
                                                          IOException exc)
                        throws IOException {
                    // ✅ Delete folder AFTER all its contents are gone
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("🗑️ Upload folder fully deleted: {}", dir.getPath());
        } catch (IOException e) {
            log.warn("⚠️ Could not fully delete directory: {} → {}",
                    dir.getPath(), e.getMessage());
        }
    }
}