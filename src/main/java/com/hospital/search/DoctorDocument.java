package com.hospital.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDocument {
    private String id;
    private String fullName;
    private String specialization;
    private String department;
    private boolean active;
}
