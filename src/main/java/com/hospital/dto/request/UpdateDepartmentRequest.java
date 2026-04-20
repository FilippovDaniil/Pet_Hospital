package com.hospital.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDepartmentRequest {

    @Size(max = 255)
    private String name;

    private String description;

    private String location;

    private Long headDoctorId;
}
