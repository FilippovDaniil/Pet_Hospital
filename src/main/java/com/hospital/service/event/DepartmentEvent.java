package com.hospital.service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long departmentId;
    private String departmentName;
}
