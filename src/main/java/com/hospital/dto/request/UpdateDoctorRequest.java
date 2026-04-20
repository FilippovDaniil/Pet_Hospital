package com.hospital.dto.request;

import com.hospital.entity.Specialty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDoctorRequest {

    @Size(max = 255)
    private String fullName;

    private Specialty specialty;

    private String cabinetNumber;

    private String phone;

    private Long departmentId;
}
