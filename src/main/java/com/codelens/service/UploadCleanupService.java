package com.codelens.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class UploadCleanupService {

    @Value("${codelens.upload.path}")
    private String uploadBasePath;

    // ✅ Fix 3 — Configurable cron: reads from properties, defaults to 2AM
    @Scheduled(cron = "${codelens.cleanup.cron:0 0 2 * * *}")
    public void cleanOldUploads() {
        log.info("🧹 Running scheduled upload cleanup...");

        Path uploadsRoot = Paths.get(uploadBasePath);

        if (!Files.exists(uploadsRoot)) {
            log.info("📁 Uploads folder does not exist — nothing to clean.");
            return;
        }

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(uploadsRoot)) {

            for (Path projectFolder : stream) {
                if (!Files.isDirectory(projectFolder)) continue;

                BasicFileAttributes attrs =
                        Files.readAttributes(projectFolder,
                                BasicFileAttributes.class);

                // ✅ Fix 2 — Use lastModifiedTime() instead of creationTime()
                //    creationTime() is unreliable on Windows (shows copy time, not create time)
                //    lastModifiedTime() works consistently on all OS including Windows
                Instant lastModified = attrs.lastModifiedTime().toInstant();
                long hoursOld = ChronoUnit.HOURS.between(lastModified, Instant.now());

                // Delete any upload folder older than 24 hours
                if (hoursOld > 24) {
                    deleteDirectory(projectFolder);
                    log.info("🗑️ Deleted old upload folder: {} ({}h old)",
                            projectFolder.getFileName(), hoursOld);
                }
            }

        } catch (IOException e) {
            log.error("❌ Cleanup job failed: {}", e.getMessage());
        }

        log.info("✅ Upload cleanup job finished.");
    }

    // ── Recursively delete a folder and all its contents ──────
    private void deleteDirectory(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {

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
                    // ✅ If visiting failed — still try force-writable then delete
                    file.toFile().setWritable(true, false);
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        log.warn("⚠️ Could not delete file: {}", file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir,
                                                          IOException exc)
                        throws IOException {
                    // ✅ Delete folder AFTER all its contents are gone
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("🗑️ Upload folder fully deleted: {}", path);
        } catch (IOException e) {
            log.warn("⚠️ Could not delete upload folder: {}", e.getMessage());
        }
    }
}