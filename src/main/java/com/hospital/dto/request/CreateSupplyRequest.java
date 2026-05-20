package com.hospital.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSupplyRequest {

    @NotBlank
    private String name;

    @NotNull
    private String category;

    @Min(0)
    private int quantity;

    @NotBlank
    private String unit;

    private String description;

    @Min(0)
    private int minQuantity = 5;
}
