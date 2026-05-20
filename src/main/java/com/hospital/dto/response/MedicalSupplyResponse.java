package com.hospital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalSupplyResponse {
    private Long id;
    private String name;
    private String category;
    private String categoryLabel;
    private int quantity;
    private String unit;
    private String description;
    private int minQuantity;
    private boolean lowStock;
}
