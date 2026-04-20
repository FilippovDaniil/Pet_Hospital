package com.hospital.dto.response;

import com.hospital.entity.Gender;
import com.hospital.entity.PatientStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientResponse {
    private Long id;
    private String fullName;
    private LocalDate birthDate;
    private Gender gender;
    private String snils;
    private String phone;
    private String address;
    private LocalDate registrationDate;
    private PatientStatus status;
    private Long currentDoctorId;
    private String currentDoctorName;
    private Long currentWardId;
    private String currentWardNumber;
    private boolean active;
}
