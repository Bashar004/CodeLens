package com.codelens.controller;

import com.codelens.dto.QARequest;
import com.codelens.dto.QAResponse;
import com.codelens.model.User;
import com.codelens.service.QAService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class QAController {

    private final QAService qaService;

    // POST /api/projects/{id}/ask
    @PostMapping("/{id}/ask")
    public ResponseEntity<QAResponse> ask(
            @PathVariable Long id,
            @Valid @RequestBody QARequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        QAResponse response = qaService.answer(
                id,
                currentUser.getId(),
                request);

        return ResponseEntity.ok(response);
    }
}