package com.hospital.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaidServiceResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private String description;
    private boolean active;
}
