package com.hospital.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ServiceOrderRequest {

    @NotNull(message = "Укажите услугу")
    private Long serviceId;

    @Pattern(regexp = "\\+?[\\d\\-() ]{7,20}", message = "Некорректный номер телефона")
    private String contactPhone;

    private String notes;

    private LocalDate preferredDate;

    private LocalTime preferredTime;
}
