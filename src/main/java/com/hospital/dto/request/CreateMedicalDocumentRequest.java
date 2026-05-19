package com.hospital.dto.request;

import com.hospital.entity.MedicalDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateMedicalDocumentRequest {

    @NotNull(message = "Укажите пациента")
    private Long patientId;

    @NotNull(message = "Укажите тип документа")
    private MedicalDocumentType type;

    @NotBlank(message = "Заголовок обязателен")
    private String title;

    @NotBlank(message = "Содержание документа обязательно")
    private String content;

    /** Дата окончания действия (необязательна). */
    private LocalDate validUntil;
}
