package com.hospital.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateWardRequest {

    @NotBlank(message = "Ward number is required")
    private String wardNumber;

    @NotNull
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @NotNull(message = "Department ID is required")
    private Long departmentId;
}
