package com.codelens.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private String status;

    @Column
    private Long fileSize;

    @Column
    private Integer totalFiles;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Builder.Default
    @Column(name = "processed_units", nullable = false)
    private Integer processedUnits = 0;

    @Builder.Default
    @Column(name = "failed_units", nullable = false)
    private Integer failedUnits = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;
}