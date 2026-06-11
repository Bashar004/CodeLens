package com.codelens.repository;

import com.codelens.model.Project;
import com.codelens.model.User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository  extends JpaRepository<Project, Long> {

    List<Project> findByOwner(User owner);

    Optional<Project> findByIdAndOwner(Long id, User owner);

    boolean existsByNameAndOwner(String name, User owner);
    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Project p SET p.processedUnits = :processed, " +
            "p.failedUnits = :failed WHERE p.id = :projectId")
    void updateProgress(
            @Param("projectId") Long projectId,
            @Param("processed") int processed,
            @Param("failed") int failed
    );
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Project p SET p.status = :status WHERE p.id = :projectId")
    void updateStatus(
            @Param("projectId") Long projectId,
            @Param("status") String status
    );

    @Query(value = "SELECT processed_units FROM projects WHERE id = :projectId",
            nativeQuery = true)
    Integer findProcessedUnits(@Param("projectId") Long projectId);

    @Query(value = "SELECT failed_units FROM projects WHERE id = :projectId",
            nativeQuery = true)
    Integer findFailedUnits(@Param("projectId") Long projectId);

    @Query(value = "SELECT status FROM projects WHERE id = :projectId",
            nativeQuery = true)
    String findStatusById(@Param("projectId") Long projectId);

}
