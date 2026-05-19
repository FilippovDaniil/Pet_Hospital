package com.hospital.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AppointmentRequest {

    @NotNull(message = "Укажите врача")
    private Long doctorId;

    @NotNull(message = "Укажите желаемую дату приёма")
    @Future(message = "Дата должна быть в будущем")
    private LocalDate preferredDate;

    private String preferredTime;

    @Pattern(regexp = "\\+?[\\d\\-() ]{7,20}", message = "Некорректный номер телефона")
    private String contactPhone;

    private String notes;
}
