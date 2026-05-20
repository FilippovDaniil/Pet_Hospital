package com.hospital.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateAssignmentRequest {

    @NotNull
    private Long clientUserId;

    @NotNull
    private String procedureType;

    @NotBlank
    private String title;

    private String description;

    private String dosage;

    private LocalDate scheduledDate;

    private LocalTime scheduledTime;
}
