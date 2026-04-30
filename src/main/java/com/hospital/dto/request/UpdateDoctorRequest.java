package com.hospital.dto.request;

import com.hospital.entity.Specialty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO для частичного обновления данных врача (PUT /api/doctors/{id}).
 *
 * <p>Все поля необязательны — передаются только изменяемые.
 * Сервисный слой применяет изменения выборочно: если поле {@code null},
 * значение в Entity остаётся прежним (паттерн Partial Update).
 *
 * <p>Поле {@code active} намеренно отсутствует: деактивация врача —
 * это отдельная бизнес-операция (DELETE /api/doctors/{id}),
 * а не часть обычного редактирования профиля.
 */
@Data
public class UpdateDoctorRequest {

    /**
     * Новое полное имя врача (необязательное поле).
     *
     * <p><b>@Size(max = 255)</b> — если значение передано, ограничивает длину
     * строки размером колонки в БД. На {@code null} не срабатывает.
     */
    @Size(max = 255)
    private String fullName;

    /**
     * Новая специализация врача (необязательное поле).
     * Перечисление {@code Specialty}; если {@code null} — специализация
     * не меняется.
     */
    private Specialty specialty;

    /**
     * Новый номер кабинета врача (необязательное поле).
     */
    private String cabinetNumber;

    /**
     * Новый номер телефона врача (необязательное поле).
     */
    private String phone;

    /**
     * Новый идентификатор отделения (необязательное поле).
     *
     * <p>Если передан — врач переводится в указанное отделение.
     * Если {@code null} — привязка к отделению не меняется.
     * Сервис проверит существование отделения по {@code id} и
     * при необходимости выбросит {@code ResourceNotFoundException}.
     */
    private Long departmentId;
}
