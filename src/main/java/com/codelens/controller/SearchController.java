package com.codelens.controller;

import com.codelens.dto.SearchRequest;
import com.codelens.dto.SearchResult;
import com.codelens.model.User;
import com.codelens.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    // POST /api/projects/{id}/search
    @PostMapping("/{id}/search")
    public ResponseEntity<List<SearchResult>> search(
            @PathVariable Long id,
            @Valid @RequestBody SearchRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        List<SearchResult> results = searchService.search(
                id,
                currentUser.getId(),
                request);

        return ResponseEntity.ok(results);
    }
}