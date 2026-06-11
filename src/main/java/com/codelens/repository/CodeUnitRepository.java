package com.codelens.repository;

import com.codelens.model.CodeUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;  // ✅ Correct import

import java.util.List;

@Repository
public interface CodeUnitRepository extends JpaRepository<CodeUnit, Long> {

    // Find all code units belonging to a specific project
    List<CodeUnit> findByProjectId(Long projectId);

    // Count how many code units a project has
    long countByProjectId(Long projectId);

    // Delete all code units for a project
    @Transactional                                               // ✅ Correct import
    @Modifying
    @Query("DELETE FROM CodeUnit c WHERE c.project.id = :projectId")
    void deleteAllByProjectId(@Param("projectId") Long projectId);  // ✅ Name fixed

    // Search classes by name within a project (case-insensitive)
    List<CodeUnit> findByProjectIdAndClassNameContainingIgnoreCase(
            Long projectId, String className
    );


    @Query(value = """
            SELECT id, class_name, class_type, package_name,
                   file_path, summary, methods_json,
                   fields_json, raw_source_code, parsed_at,
                   project_id, embedding,
                   1 - (embedding <=> CAST(:queryVector AS vector)) 
                       AS similarity
            FROM code_units
            WHERE project_id = :projectId
              AND embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """,
            nativeQuery = true)
    List<Object[]> findSimilarCodeUnits(
            @Param("projectId")   Long   projectId,
            @Param("queryVector") String queryVector,
            @Param("limit")       int    limit);



    @Query(value = "SELECT * FROM code_units WHERE project_id = :projectId " +
            "AND embedding IS NULL",
            nativeQuery = true)
    List<CodeUnit> findByProjectIdAndEmbeddingIsNull(
            @Param("projectId") Long projectId
    );

    @Query(value = "SELECT COUNT(*) FROM code_units WHERE project_id = :projectId " +
            "AND embedding IS NULL",
            nativeQuery = true)
    long countByProjectIdAndEmbeddingIsNull(
            @Param("projectId") Long projectId
    );

    @Query(value = """
    SELECT id, class_name, class_type, package_name,
           file_path, methods_json, fields_json, raw_source_code
    FROM code_units
    WHERE project_id = :projectId
    """, nativeQuery = true)
    List<Object[]> findByProjectIdForDiagram(
            @Param("projectId") Long projectId
    );


}