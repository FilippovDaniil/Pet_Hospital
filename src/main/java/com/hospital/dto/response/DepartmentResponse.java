package com.hospital.dto.response;

import lombok.Data;

@Data
public class DepartmentResponse {
    private Long id;
    private String name;
    private String description;
    private String location;
    private Long headDoctorId;
    private String headDoctorName;
}
