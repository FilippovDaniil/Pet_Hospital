package com.hospital.controller;

import com.hospital.dto.request.CreateWardRequest;
import com.hospital.dto.response.WardResponse;
import com.hospital.service.WardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wards")
@RequiredArgsConstructor
@Tag(name = "Wards", description = "Ward management API")
public class WardController {

    private final WardService wardService;

    @PostMapping
    @Operation(summary = "Create a new ward")
    public ResponseEntity<WardResponse> create(@Valid @RequestBody CreateWardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(wardService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ward by ID")
    public ResponseEntity<WardResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(wardService.getById(id));
    }

    @GetMapping
    @Operation(summary = "Get all wards")
    public ResponseEntity<List<WardResponse>> getAll() {
        return ResponseEntity.ok(wardService.getAll());
    }

    @PostMapping("/{wardId}/admit/{patientId}")
    @Operation(summary = "Admit patient to ward")
    public ResponseEntity<WardResponse> admitPatient(
            @PathVariable Long wardId,
            @PathVariable Long patientId) {
        return ResponseEntity.ok(wardService.admitPatient(wardId, patientId));
    }

    @PostMapping("/{wardId}/discharge/{patientId}")
    @Operation(summary = "Discharge patient from ward")
    public ResponseEntity<WardResponse> dischargePatient(
            @PathVariable Long wardId,
            @PathVariable Long patientId) {
        return ResponseEntity.ok(wardService.dischargePatientFromWard(wardId, patientId));
    }
}
