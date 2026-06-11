package com.codelens.controller;

import com.codelens.dto.DiagramResponse;
import com.codelens.model.User;
import com.codelens.service.DiagramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class DiagramController {

    private final DiagramService diagramService;

    @PostMapping("/{id}/diagram")
    public ResponseEntity<DiagramResponse> generateDiagram(
            @PathVariable Long id,
            @RequestParam(name = "package", required = false,
                    defaultValue = "all") String packageFilter,
            @AuthenticationPrincipal User currentUser) {

        log.info("📊 Diagram for project {} — package: {}",
                id, packageFilter);

        DiagramResponse response = diagramService
                .generateDiagram(id, currentUser.getId(), packageFilter);

        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{id}/diagram/render", produces = "text/plain")
    public ResponseEntity<String> getDiagramRaw(
            @PathVariable Long id,
            @RequestParam(name = "package", required = false,
                    defaultValue = "all") String packageFilter,
            @AuthenticationPrincipal User currentUser) {

        DiagramResponse response = diagramService
                .generateDiagram(id, currentUser.getId(), packageFilter);

        return ResponseEntity.ok(response.getDiagram());
    }
}