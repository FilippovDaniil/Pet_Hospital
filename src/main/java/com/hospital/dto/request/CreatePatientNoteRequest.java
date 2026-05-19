package com.hospital.dto.request;

import com.hospital.entity.PatientNoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePatientNoteRequest {

    @NotNull(message = "Укажите пациента")
    private Long patientId;

    @NotNull(message = "Укажите тип записи")
    private PatientNoteType type;

    @NotBlank(message = "Содержание обязательно")
    private String content;

    /** Если true — запись отображается пациенту в личном кабинете. */
    private boolean visibleToClient = false;
}
