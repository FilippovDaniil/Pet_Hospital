package com.hospital.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePatientRequest {

    @Size(max = 255)
    private String fullName;

    @Pattern(regexp = "\\+?[\\d\\-\\s()]{7,20}", message = "Invalid phone format")
    private String phone;

    private String address;
}
