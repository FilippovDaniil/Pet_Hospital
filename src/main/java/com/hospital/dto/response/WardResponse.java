package com.hospital.dto.response;

import lombok.Data;

@Data
public class WardResponse {
    private Long id;
    private String wardNumber;
    private int capacity;
    private int currentOccupancy;
    private int freeSlots;
    private Long departmentId;
    private String departmentName;
}
