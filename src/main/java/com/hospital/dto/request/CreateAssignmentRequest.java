package com.hospital.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO для создания назначения процедуры (POST /api/nurse/assignments).
 *
 * Медсестра не передаётся в теле запроса — она определяется из JWT токена
 * (Authentication.getName() в NurseController), что исключает подделку nurseId.
 *
 * scheduledDate и scheduledTime необязательны: медсестра может создать
 * назначение «на потом», без привязки к конкретному слоту.
 */
@Data
public class CreateAssignmentRequest {

    /**
     * ID пользователя-клиента (users.id, ROLE_CLIENT).
     * Сервис дополнительно проверяет роль через filter(u -> u.getRole() == ROLE_CLIENT),
     * чтобы нельзя было назначить процедуру врачу или администратору.
     */
    @NotNull
    private Long clientUserId;

    /**
     * Тип процедуры строкой: "INJECTION" | "PILL" | "DRESSING" | "PROCEDURE" | "OTHER".
     * Конвертируется в ProcedureType.valueOf() в NurseServiceImpl.
     */
    @NotNull
    private String procedureType;

    /** Название назначения: «Укол витамина C», «Перевязка». Обязательное. */
    @NotBlank
    private String title;

    /** Подробное описание: инструкция, противопоказания. Необязательное. */
    private String description;

    /** Доза или способ приёма: «1 мл», «1 таб. 2 р/д». Необязательное. */
    private String dosage;

    /** Запланированная дата. null — дата не определена. */
    private LocalDate scheduledDate;

    /** Запланированное время. null — время не определено. */
    private LocalTime scheduledTime;
}
