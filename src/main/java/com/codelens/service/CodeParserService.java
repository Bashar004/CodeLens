package com.codelens.service;

import com.codelens.dto.ParseResponse;
import com.codelens.model.CodeUnit;
import com.codelens.model.Project;
import com.codelens.repository.CodeUnitRepository;
import com.codelens.repository.ProjectRepository;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.JavadocComment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeParserService {

    private final ProjectRepository projectRepository;
    private final CodeUnitRepository codeUnitRepository;
    private final JavaParser javaParser = new JavaParser();


    @Transactional
    public ParseResponse parseProject(Long projectId, Long userId) {

        // 1. Load project and verify it exists
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));


        if (!project.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Access denied: project does not belong to this user");
        }


        if (!"UPLOADED".equals(project.getStatus())) {
            throw new RuntimeException(
                    "Project status is '" + project.getStatus() +
                            "'. Only UPLOADED projects can be parsed."
            );
        }


        Path storagePath = Paths.get(project.getStoragePath());


        List<Path> javaFiles = collectJavaFiles(storagePath);
        log.info("📂 Found {} Java files in project '{}'", javaFiles.size(), project.getName());


        int totalMethods = 0;
        int totalFields  = 0;
        List<CodeUnit> codeUnits = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            try {
                CodeUnit unit = parseJavaFile(javaFile, storagePath, project);
                if (unit != null) {
                    codeUnits.add(unit);
                    totalMethods += countOccurrences(unit.getMethodsJson(), "\"name\":");
                    totalFields  += countOccurrences(unit.getFieldsJson(),  "\"name\":");
                }
            } catch (Exception e) {

                log.warn("⚠️ Skipping file: {} → {}", javaFile.getFileName(), e.getMessage());
            }
        }


        codeUnitRepository.saveAll(codeUnits);
        log.info("💾 Saved {} code units to database", codeUnits.size());


        deleteDirectory(storagePath);


        project.setStatus("PARSED");
        projectRepository.save(project);
        log.info("✅ Project '{}' status updated to PARSED", project.getName());


        return ParseResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .totalFilesParsed(codeUnits.size())
                .totalMethodsExtracted(totalMethods)
                .totalFieldsExtracted(totalFields)
                .status("PARSED")
                .message("Parsing completed successfully. Uploads folder deleted.")
                .build();
    }


    private List<Path> collectJavaFiles(Path rootPath) {
        List<Path> javaFiles = new ArrayList<>();
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("⚠️ Cannot access file: {}", file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk directory: " + rootPath, e);
        }
        return javaFiles;
    }


    private CodeUnit parseJavaFile(Path javaFile,
                                   Path rootPath,
                                   Project project) throws IOException {


        String sourceCode = Files.readString(javaFile);


        ParseResult<CompilationUnit> result = javaParser.parse(sourceCode);


        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            log.warn("⚠️ JavaParser could not parse: {}", javaFile.getFileName());
            return null;
        }

        CompilationUnit cu = result.getResult().get();


        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("(default package)");


        if (cu.getTypes().isEmpty()) {
            log.warn("⚠️ No type declarations found in: {}", javaFile.getFileName());
            return null;
        }

        TypeDeclaration<?> primaryType = cu.getTypes().get(0);
        String className  = primaryType.getNameAsString();
        String classType  = resolveClassType(primaryType);
        String methodsJson = extractMethods(primaryType);
        String fieldsJson  = extractFields(primaryType);


        String relativePath = rootPath.relativize(javaFile).toString();


        return CodeUnit.builder()
                .project(project)
                .filePath(relativePath)
                .packageName(packageName)
                .className(className)
                .classType(classType)
                .rawSourceCode(sourceCode)
                .methodsJson(methodsJson)
                .fieldsJson(fieldsJson)
                .summary(null)                  // ← Filled in Step 6 by Gemini AI
                .parsedAt(LocalDateTime.now())
                .build();
    }


    private String resolveClassType(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration c) {
            return c.isInterface() ? "INTERFACE" : "CLASS";
        } else if (type instanceof EnumDeclaration) {
            return "ENUM";
        } else if (type instanceof AnnotationDeclaration) {
            return "ANNOTATION";
        }
        return "CLASS"; // default fallback
    }


    private String extractMethods(TypeDeclaration<?> type) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (MethodDeclaration method : type.getMethods()) {
            if (!first) sb.append(",");
            first = false;

            // Build parameters array
            StringBuilder params = new StringBuilder("[");
            boolean firstParam = true;
            for (var param : method.getParameters()) {
                if (!firstParam) params.append(",");
                firstParam = false;
                params.append("{")
                        .append("\"name\":\"")
                        .append(escapeJson(param.getNameAsString()))
                        .append("\",")
                        .append("\"type\":\"")
                        .append(escapeJson(param.getTypeAsString()))
                        .append("\"}");
            }
            params.append("]");

            // Get method body (empty string if abstract/interface method)
            String body = method.getBody()
                    .map(b -> escapeJson(b.toString()))
                    .orElse("");

            // Get JavaDoc comment if present
            String javadoc = method.getComment()
                    .filter(c -> c instanceof JavadocComment)
                    .map(c -> escapeJson(c.getContent()))
                    .orElse("");

            sb.append("{")
                    .append("\"name\":\"")
                    .append(escapeJson(method.getNameAsString())).append("\",")
                    .append("\"returnType\":\"")
                    .append(escapeJson(method.getTypeAsString())).append("\",")
                    .append("\"parameters\":").append(params).append(",")
                    .append("\"body\":\"").append(body).append("\",")
                    .append("\"javadoc\":\"").append(javadoc).append("\"")
                    .append("}");
        }

        sb.append("]");
        return sb.toString();
    }


    private String extractFields(TypeDeclaration<?> type) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (FieldDeclaration field : type.getFields()) {
            for (var variable : field.getVariables()) {
                if (!first) sb.append(",");
                first = false;

                sb.append("{")
                        .append("\"name\":\"")
                        .append(escapeJson(variable.getNameAsString())).append("\",")
                        .append("\"type\":\"")
                        .append(escapeJson(field.getElementType().asString())).append("\"")
                        .append("}");
            }
        }

        sb.append("]");
        return sb.toString();
    }


    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")   // backslash must be first
                .replace("\"", "\\\"")   // double quote
                .replace("\n", "\\n")    // newline
                .replace("\r", "\\r")    // carriage return
                .replace("\t", "\\t");   // tab
    }


    private int countOccurrences(String json, String target) {
        if (json == null || json.equals("[]")) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = json.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }


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