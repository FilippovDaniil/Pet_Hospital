package com.hospital.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO ответа для назначения процедуры.
 *
 * Плоская структура: вместо вложенных объектов clientUser/nurseUser —
 * отдельные поля clientUserId/clientName и nurseUserId/nurseName.
 * Это избавляет фронтенд от лишней навигации по вложенным объектам.
 *
 * Два поля типа процедуры:
 *   - procedureType      ("INJECTION")  — для программной логики фронтенда.
 *   - procedureTypeLabel ("Укол")       — для отображения в таблице UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NurseAssignmentResponse {

    /** Первичный ключ назначения. */
    private Long id;

    /** ID пользователя-клиента (users.id). */
    private Long clientUserId;

    /** Полное имя клиента для отображения в таблице. */
    private String clientName;

    /** ID пользователя-медсестры (users.id). */
    private Long nurseUserId;

    /** Полное имя медсестры. */
    private String nurseName;

    /** Enum-ключ типа процедуры: "INJECTION" | "PILL" | "DRESSING" | "PROCEDURE" | "OTHER". */
    private String procedureType;

    /** Русский ярлык: «Укол» | «Приём таблеток» | «Перевязка» | «Процедура» | «Прочее». */
    private String procedureTypeLabel;

    /** Краткое название назначения. */
    private String title;

    /** Подробное описание. Может быть null. */
    private String description;

    /** Доза/способ приёма. Может быть null. */
    private String dosage;

    /** Запланированная дата. Может быть null. */
    private LocalDate scheduledDate;

    /** Запланированное время. Может быть null. */
    private LocalTime scheduledTime;

    /** Текущий статус: "ACTIVE" | "DONE" | "CANCELLED". */
    private String status;

    /** Дата и время создания назначения (устанавливается @PrePersist). */
    private LocalDateTime createdAt;
}
