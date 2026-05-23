package com.hospital.controller;

import com.hospital.search.DoctorDocument;
import com.hospital.search.PatientDocument;
import com.hospital.search.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Полнотекстовый поиск через OpenSearch")
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Поиск пациентов", description = "Full-text поиск по имени, диагнозу, палате, отделению")
    @GetMapping("/patients")
    public ResponseEntity<List<PatientDocument>> searchPatients(
            @RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(searchService.searchPatients(q));
    }

    @Operation(summary = "Поиск врачей", description = "Full-text поиск по имени, специализации, отделению")
    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorDocument>> searchDoctors(
            @RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(searchService.searchDoctors(q));
    }
}
