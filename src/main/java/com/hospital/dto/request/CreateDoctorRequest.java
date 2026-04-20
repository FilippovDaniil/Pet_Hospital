package com.hospital.dto.request;

import com.hospital.entity.Specialty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDoctorRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255)
    private String fullName;

    @NotNull(message = "Specialty is required")
    private Specialty specialty;

    private String cabinetNumber;

    private String phone;

    private Long departmentId;
}
