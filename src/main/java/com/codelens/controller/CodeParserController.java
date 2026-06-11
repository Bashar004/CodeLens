package com.codelens.controller;


import com.codelens.dto.ParseResponse;
import com.codelens.model.User;
import com.codelens.service.CodeParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class CodeParserController {
    private final CodeParserService codeParserService;

    @PostMapping("/{id}/parse")
    public ResponseEntity<ParseResponse> parseProject(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) {
        ParseResponse response = codeParserService
                .parseProject(id, currentUser.getId());

        return ResponseEntity.ok(response);
    }
}
