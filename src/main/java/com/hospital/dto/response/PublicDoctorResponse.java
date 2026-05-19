package com.hospital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicDoctorResponse {
    private Long id;
    private String fullName;
    private String specialty;
    private String cabinetNumber;
    private Long departmentId;
    private String departmentName;
    private String departmentLocation;
}
