package com.hospital.dto.request;

import com.hospital.entity.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreatePatientRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255)
    private String fullName;

    @NotNull(message = "Birth date is required")
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotBlank(message = "SNILS is required")
    @Pattern(regexp = "\\d{3}-\\d{3}-\\d{3}\\s\\d{2}", message = "SNILS format: XXX-XXX-XXX XX")
    private String snils;

    @Pattern(regexp = "\\+?[\\d\\-\\s()]{7,20}", message = "Invalid phone format")
    private String phone;

    private String address;
}
