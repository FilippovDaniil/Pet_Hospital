package com.hospital.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDocument {
    private String id;
    private String fullName;
    private String diagnosis;
    private String ward;
    private String department;
    private boolean active;
}
