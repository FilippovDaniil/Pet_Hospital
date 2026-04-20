package com.hospital.dto.response;

import com.hospital.entity.Specialty;
import lombok.Data;

@Data
public class DoctorResponse {
    private Long id;
    private String fullName;
    private Specialty specialty;
    private String cabinetNumber;
    private String phone;
    private Long departmentId;
    private String departmentName;
    private boolean active;
}
