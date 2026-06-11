package com.codelens.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GithubImportRequest {

    @NotBlank(message = "GitHub URL is required")
    @Pattern(
            regexp = "^https://github\\.com/[\\w.-]+/[\\w.-]+(\\.git)?$",
            message = "Must be a valid GitHub repository URL"
    )
    private String repoUrl;


    @NotBlank(message = "Project name is required")
    private String projectName;
}
