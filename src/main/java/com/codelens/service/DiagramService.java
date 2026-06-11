package com.codelens.service;

import com.codelens.dto.DiagramResponse;
import com.codelens.model.Project;
import com.codelens.repository.CodeUnitRepository;
import com.codelens.repository.ProjectRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagramService {

    private final ProjectRepository projectRepository;
    private final CodeUnitRepository codeUnitRepository;

    // Jackson for parsing methodsJson and fieldsJson
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Exclusion rules ─────────────────────────────────────
    // WHY: Test classes, config, utils add noise with no
    // architectural value in a class diagram
    private static final Set<String> EXCLUDED_SUFFIXES = Set.of(
            "Tests", "Test", "IntegrationTests",
            "Configuration", "Application",
            "Formatter", "Validator", "Utils", "Hints",
            "Controller"
    );

    private static final Set<String> EXCLUDED_EXACT = Set.of(
            "WebConfiguration", "WelcomeController",
            "EntityUtils", "PetClinicRuntimeHints",
            "CacheConfiguration", "MysqlTestApplication",
            "I18nPropertiesSyncTest"
    );

    public DiagramResponse generateDiagram(Long projectId,
                                           Long userId,
                                           String packageFilter) {

        // ── 1. Validate project ──────────────────────────────
        Project project = projectRepository
                .findByIdAndOwnerId(projectId, userId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found or access denied"));

        if (!"INDEXED".equals(project.getStatus()) &&
                !"PARSED".equals(project.getStatus())) {
            throw new RuntimeException(
                    "Project must be PARSED or INDEXED first.");
        }

        // ── 2. Load rows from DB ─────────────────────────────
        // Uses diagram-specific query — skips embedding column
        List<Object[]> rows = codeUnitRepository
                .findByProjectIdForDiagram(projectId);

        if (rows.isEmpty()) {
            throw new RuntimeException(
                    "No code units found. Run /parse first.");
        }

        // ── 3. Build full class map ──────────────────────────
        // Map: className → ClassInfo (all data we need)
        Map<String, ClassInfo> classMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String className   = (String) row[1];
            String classType   = (String) row[2];
            String packageName = (String) row[3];
            String methodsJson = (String) row[5];
            String fieldsJson  = (String) row[6];
            String rawSource   = (String) row[7];

            if (className == null || className.isBlank()) continue;
            if (shouldExclude(className)) continue;

            classMap.put(className, new ClassInfo(
                    className, classType, packageName,
                    methodsJson, fieldsJson, rawSource
            ));
        }

        // ── 4. Get available packages ────────────────────────
        // Extracts last segment of package name for readability
        // e.g. "org.springframework.samples.petclinic.owner" → "owner"
        List<String> availablePackages = classMap.values().stream()
                .map(c -> getLastPackageSegment(c.packageName))
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        log.info("📦 Available packages: {}", availablePackages);

        // ── 5. Filter by package if requested ───────────────
        Map<String, ClassInfo> filteredMap;
        if ("all".equalsIgnoreCase(packageFilter)) {
            filteredMap = classMap;
        } else {
            // Filter: keep only classes whose package ends with
            // the requested segment
            // e.g. packageFilter="owner" keeps
            //      "org.springframework.samples.petclinic.owner"
            filteredMap = classMap.entrySet().stream()
                    .filter(e -> getLastPackageSegment(
                            e.getValue().packageName)
                            .equalsIgnoreCase(packageFilter))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a,
                            LinkedHashMap::new));

            if (filteredMap.isEmpty()) {
                throw new RuntimeException(
                        "No classes found for package: " + packageFilter +
                                ". Available: " + availablePackages);
            }
        }

        // ── 6. Build diagram ─────────────────────────────────
        Set<String> knownClassNames = classMap.keySet();
        StringBuilder diagram = new StringBuilder();
        diagram.append("classDiagram\n");
        diagram.append("    direction TB\n");

        Set<String> relationships = new LinkedHashSet<>();

        for (ClassInfo info : filteredMap.values()) {
            String sanitized = sanitize(info.className);

            // Class declaration
            diagram.append("    class ").append(sanitized);

            String stereotype = getStereotype(info.classType);
            if (!stereotype.isEmpty()) {
                diagram.append(" {\n")
                        .append("        <<").append(stereotype)
                        .append(">>\n")
                        .append("    }\n");
            } else {
                diagram.append("\n");
            }

            // ── Detect relationships ─────────────────────────

            // A: From fieldsJson — most reliable source
            // WHY: JavaParser already extracted all fields with
            // their exact types in Step 5 — no regex needed
            detectFromFields(
                    info.fieldsJson, sanitized,
                    knownClassNames, relationships);

            // B: From rawSourceCode — only for inheritance
            // WHY: extends/implements are in class declaration,
            // not in fields — still need regex for these two
            detectInheritance(
                    info.rawSource, sanitized,
                    knownClassNames, relationships);
        }

        // ── 7. Build final diagram string ────────────────────
        diagram.append("\n");
        for (String rel : relationships) {
            diagram.append("    ").append(rel).append("\n");
        }

        log.info("✅ Diagram: {} classes, {} relationships, package={}",
                filteredMap.size(), relationships.size(), packageFilter);

        return DiagramResponse.builder()
                .projectId(projectId)
                .projectName(project.getName())
                .diagram(diagram.toString())
                .classCount(filteredMap.size())
                .relationshipCount(relationships.size())
                .packageFilter(packageFilter)
                .availablePackages(availablePackages)
                .message(String.format(
                        "Diagram: %d classes, %d relationships [package=%s]",
                        filteredMap.size(), relationships.size(), packageFilter))
                .build();
    }

    // ────────────────────────────────────────────────────────
    // Detect relationships from fieldsJson
    // This is the V2 core improvement over V1
    // ────────────────────────────────────────────────────────
    private void detectFromFields(String fieldsJson,
                                  String className,
                                  Set<String> knownClasses,
                                  Set<String> relationships) {
        if (fieldsJson == null || fieldsJson.isBlank() ||
                fieldsJson.equals("[]")) return;

        try {
            // Parse the fields JSON array
            // Format: [{"name":"pets","type":"List<Pet>"},...]
            List<Map<String, String>> fields = objectMapper.readValue(
                    fieldsJson,
                    new TypeReference<List<Map<String, String>>>() {}
            );

            for (Map<String, String> field : fields) {
                String rawType = field.get("type");
                if (rawType == null || rawType.isBlank()) continue;

                // ── Case 1: Simple type — "private Owner owner" ──
                // rawType = "Owner"
                if (knownClasses.contains(rawType) &&
                        !rawType.equals(className)) {
                    relationships.add(
                            className + " --> " + sanitize(rawType));
                    continue;
                }

                // ── Case 2: Generic type — "List<Pet>", "Set<Specialty>" ──
                // rawType = "List<Pet>" → extract "Pet"
                String inner = extractGenericType(rawType);
                if (inner != null &&
                        knownClasses.contains(inner) &&
                        !inner.equals(className)) {
                    relationships.add(
                            className + " --> \"*\" " + sanitize(inner));
                }
            }

        } catch (Exception e) {
            log.warn("⚠️ Failed to parse fieldsJson for {}: {}",
                    className, e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────
    // Detect inheritance from rawSourceCode
    // Only used for extends and implements — 2 patterns only
    // ────────────────────────────────────────────────────────
    private void detectInheritance(String source,
                                   String className,
                                   Set<String> knownClasses,
                                   Set<String> relationships) {
        if (source == null) return;

        // extends — inheritance arrow --|>
        Pattern extendsPattern = Pattern.compile(
                "class\\s+\\w+\\s+extends\\s+(\\w+)");
        Matcher extendsMatcher = extendsPattern.matcher(source);
        while (extendsMatcher.find()) {
            String parent = extendsMatcher.group(1);
            if (knownClasses.contains(parent) &&
                    !parent.equals(className)) {
                relationships.add(
                        className + " --|> " + sanitize(parent));
            }
        }

        // implements — realization arrow ..|>
        Pattern implementsPattern = Pattern.compile(
                "implements\\s+([\\w,\\s]+?)(?:extends|\\{)");
        Matcher implementsMatcher = implementsPattern.matcher(source);
        while (implementsMatcher.find()) {
            String[] interfaces = implementsMatcher
                    .group(1).split(",");
            for (String iface : interfaces) {
                String trimmed = iface.trim();
                if (knownClasses.contains(trimmed) &&
                        !trimmed.equals(className)) {
                    relationships.add(
                            className + " ..|> " + sanitize(trimmed));
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────
    // Extract inner type from generic — "List<Pet>" → "Pet"
    // ────────────────────────────────────────────────────────
    private String extractGenericType(String type) {
        if (type == null) return null;
        // Matches: List<X>, Set<X>, Collection<X>, Optional<X>
        Pattern p = Pattern.compile(
                "(?:List|Set|Collection|Optional)<(\\w+)>");
        Matcher m = p.matcher(type);
        return m.find() ? m.group(1) : null;
    }

    // ────────────────────────────────────────────────────────
    // Get last segment of package name
    // "org.springframework.samples.petclinic.owner" → "owner"
    // ────────────────────────────────────────────────────────
    private String getLastPackageSegment(String packageName) {
        if (packageName == null || packageName.isBlank()) return "default";
        String[] parts = packageName.split("\\.");
        return parts[parts.length - 1];
    }

    private boolean shouldExclude(String className) {
        if (EXCLUDED_EXACT.contains(className)) return true;
        for (String suffix : EXCLUDED_SUFFIXES) {
            if (className.endsWith(suffix)) return true;
        }
        return false;
    }

    private String getStereotype(String classType) {
        if (classType == null) return "";
        return switch (classType.toUpperCase()) {
            case "INTERFACE"  -> "interface";
            case "ENUM"       -> "enumeration";
            case "ANNOTATION" -> "annotation";
            default           -> "";
        };
    }

    private String sanitize(String name) {
        if (name == null) return "Unknown";
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    // ────────────────────────────────────────────────────────
    // Inner record to hold class data during processing
    // WHY record: Immutable, compact, no boilerplate
    // ────────────────────────────────────────────────────────
    private record ClassInfo(
            String className,
            String classType,
            String packageName,
            String methodsJson,
            String fieldsJson,
            String rawSource
    ) {}


}