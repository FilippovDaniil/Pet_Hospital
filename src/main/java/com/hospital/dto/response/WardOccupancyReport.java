package com.hospital.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WardOccupancyReport {
    private Long departmentId;
    private String departmentName;
    private List<WardOccupancyItem> wards;
    private int totalCapacity;
    private int totalOccupied;
    private int totalFree;

    @Data
    @Builder
    public static class WardOccupancyItem {
        private Long wardId;
        private String wardNumber;
        private int capacity;
        private int occupied;
        private int free;
    }
}
