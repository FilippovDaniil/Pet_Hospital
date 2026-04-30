package com.hospital.dto.response;

import com.hospital.entity.Specialty;
import lombok.Data;

/**
 * DTO ответа с данными врача (GET /api/doctors/{id} и другие эндпоинты).
 *
 * <p>Формируется MapStruct-маппером из Entity {@code Doctor}.
 *
 * <p><b>Поля из вложенного объекта (маппинг MapStruct):</b>
 * <ul>
 *   <li>{@code departmentId} ← {@code doctor.department.id}</li>
 *   <li>{@code departmentName} ← {@code doctor.department.name}</li>
 * </ul>
 * Благодаря этим «плоским» полям клиент получает полную информацию
 * об отделении в одном ответе, не делая отдельного запроса
 * к /api/departments/{id}.
 *
 * <p>Список пациентов врача намеренно исключён из этого DTO:
 * он доступен через отдельный эндпоинт (GET /api/patients?doctorId=...)
 * и может быть большим. Включение его сюда создало бы проблему N+1
 * и возвращало бы слишком большие ответы.
 */
@Data
public class DoctorResponse {

    /** Уникальный идентификатор врача в БД. */
    private Long id;

    /** Полное имя врача (ФИО). */
    private String fullName;

    /**
     * Специализация врача (например, THERAPIST, SURGEON).
     * Возвращается как строковое название значения enum.
     */
    private Specialty specialty;

    /** Номер кабинета приёма. */
    private String cabinetNumber;

    /** Контактный телефон врача. */
    private String phone;

    /**
     * ID отделения — берётся из {@code doctor.department.id}.
     * Может быть {@code null}, если врач не прикреплён к отделению.
     */
    private Long departmentId;

    /**
     * Название отделения — берётся из {@code doctor.department.name}.
     * Позволяет отображать название без дополнительного запроса к API.
     */
    private String departmentName;

    /**
     * Флаг активности (soft delete).
     * {@code false} — врач деактивирован и не отображается в стандартных списках.
     */
    private boolean active;
}
